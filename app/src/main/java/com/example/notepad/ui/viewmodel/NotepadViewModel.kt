package com.example.notepad.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.AvalAiClient
import com.example.network.CorrectionJsonCodec
import com.example.notepad.audio.AudioPlayerHelper
import com.example.notepad.audio.AudioRecorderHelper
import com.example.notepad.data.gemini.GeminiPreferences
import com.example.notepad.data.gemini.GeminiService
import com.example.notepad.data.model.MediaItem
import com.example.notepad.data.model.MediaType
import com.example.notepad.data.model.NoteLine
import com.example.notepad.data.model.NotepadBlock
import com.example.notepad.data.model.NotepadTab
import com.example.notepad.data.model.hasContent
import com.example.notepad.data.model.removeEmptySecondaryBlock
import com.example.notepad.data.model.removeLine
import com.example.notepad.data.repository.NotesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class NotepadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NotesRepository.getInstance(application)
    private val geminiService = GeminiService()
    val geminiPreferences = GeminiPreferences(application)
    val recorderHelper = AudioRecorderHelper(application)
    val playerHelper = AudioPlayerHelper(application)

    val tabs: StateFlow<List<NotepadTab>> = repository.tabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private var recordingTimerJob: Job? = null
    private var activeRecordingBlockId: String? = null
    private val emptyBlockCleanupJobs = mutableMapOf<String, Job>()
    private val transcriptionRetryJobs = mutableMapOf<String, Job>()
    private val focusedLineIdsByBlock = mutableMapOf<String, MutableSet<String>>()

    // For image preview modal (smooth expand)
    private val _previewImageItem = MutableStateFlow<MediaItem?>(null)
    val previewImageItem: StateFlow<MediaItem?> = _previewImageItem.asStateFlow()

    // For audio review dialog (matching Screenshot 1)
    private val _selectedAudioItem = MutableStateFlow<MediaItem?>(null)
    val selectedAudioItem: StateFlow<MediaItem?> = _selectedAudioItem.asStateFlow()

    // For item deletion popup (blockId to MediaItem)
    private val _itemToDelete = MutableStateFlow<Pair<String, MediaItem>?>(null)
    val itemToDelete: StateFlow<Pair<String, MediaItem>?> = _itemToDelete.asStateFlow()

    // Dialog state for new general notepad tab
    private val _showNewTabDialog = MutableStateFlow(false)
    val showNewTabDialog: StateFlow<Boolean> = _showNewTabDialog.asStateFlow()

    // Dialog state for tab long-press action (Rename or Delete)
    private val _tabActionTarget = MutableStateFlow<Pair<Int, NotepadTab>?>(null)
    val tabActionTarget: StateFlow<Pair<Int, NotepadTab>?> = _tabActionTarget.asStateFlow()

    val currentlyPlayingId = playerHelper.currentlyPlayingId
    val currentPositionMs = playerHelper.currentPositionMs
    val durationMs = playerHelper.durationMs

    val avalaiApiKey = geminiPreferences.avalaiApiKey

    init {
        viewModelScope.launch {
            repository.loadData()
            repository.tabs.value.forEach { tab ->
                tab.blocks.drop(1)
                    .filterNot(NotepadBlock::hasContent)
                    .forEach { block -> scheduleEmptyBlockRemoval(tab.id, block.id) }
            }
        }
        viewModelScope.launch {
            repository.tabs.collect { updatedTabs ->
                if (updatedTabs.isNotEmpty() && _selectedTabIndex.value !in updatedTabs.indices) {
                    _selectedTabIndex.value = updatedTabs.lastIndex.coerceAtLeast(0)
                }
                val selectedAudioId = _selectedAudioItem.value?.id
                if (selectedAudioId != null) {
                    val refreshed = updatedTabs
                        .asSequence()
                        .flatMap { it.blocks.asSequence() }
                        .flatMap { it.mediaItems.asSequence() }
                        .firstOrNull { it.id == selectedAudioId }
                    if (refreshed != null) {
                        _selectedAudioItem.value = refreshed
                    }
                }
            }
        }
    }

    fun selectTab(index: Int) {
        val currentTabs = tabs.value
        if (index in currentTabs.indices) {
            _selectedTabIndex.value = index
        }
    }

    fun openTabActionMenu(index: Int, tab: NotepadTab) {
        _tabActionTarget.value = Pair(index, tab)
    }

    fun closeTabActionMenu() {
        _tabActionTarget.value = null
    }

    fun renameTab(index: Int, newTitle: String) {
        if (index !in tabs.value.indices) return
        val cleanTitle = newTitle.trim()
        viewModelScope.launch {
            repository.updateTabs { currentTabs ->
                if (index !in currentTabs.indices) {
                    currentTabs
                } else {
                    currentTabs.toMutableList().apply {
                        val title = cleanTitle.ifEmpty { this[index].title }
                        set(index, this[index].copy(title = title))
                    }
                }
            }
            closeTabActionMenu()
        }
    }

    fun deleteTab(index: Int) {
        val currentTabs = tabs.value
        if (currentTabs.size <= 1 || index !in currentTabs.indices) return
        viewModelScope.launch {
            repository.updateTabs { latestTabs ->
                if (latestTabs.size <= 1 || index !in latestTabs.indices) {
                    latestTabs
                } else {
                    latestTabs.toMutableList().apply { removeAt(index) }
                }
            }
            _selectedTabIndex.value = (index - 1).coerceAtLeast(0)
            closeTabActionMenu()
        }
    }

    fun openNewTabDialog() {
        _showNewTabDialog.value = true
    }

    fun closeNewTabDialog() {
        _showNewTabDialog.value = false
    }

    fun refreshAiSettings() {
        geminiPreferences.refreshFromSharedSettings()
    }

    fun updateAiSettings(avalaiKey: String) {
        geminiPreferences.updateSettings(avalaiKey)
    }

    fun resetToDefaultData() {
        viewModelScope.launch {
            repository.resetToDefault()
            _selectedTabIndex.value = 0
        }
    }

    fun createNewTab(title: String) {
        val cleanTitle = title.trim().ifEmpty { "notepad ${tabs.value.size + 1}" }
        val newTab = NotepadTab(
            title = cleanTitle.lowercase(),
            blocks = listOf(
                NotepadBlock(
                    title = "",
                    lines = List(INITIAL_LINE_COUNT) { NoteLine(text = "") },
                    mediaItems = emptyList()
                )
            )
        )
        viewModelScope.launch {
            repository.updateTabs { currentTabs -> currentTabs + newTab }
            _selectedTabIndex.value = repository.tabs.value.lastIndex
            _showNewTabDialog.value = false
        }
    }

    fun addSubNotepad() {
        val currentTab = tabs.value.getOrNull(_selectedTabIndex.value) ?: return
        val newBlock = NotepadBlock(
            title = "",
            lines = List(INITIAL_LINE_COUNT) { NoteLine(text = "") },
            mediaItems = emptyList()
        )

        viewModelScope.launch {
            repository.updateTabs { currentTabs ->
                currentTabs.map { tab ->
                    if (tab.id == currentTab.id) tab.copy(blocks = tab.blocks + newBlock) else tab
                }
            }
            scheduleEmptyBlockRemoval(currentTab.id, newBlock.id)
        }
    }

    fun keepBlock(blockId: String) {
        emptyBlockCleanupJobs.remove(blockId)?.cancel()
    }

    fun setLineTyping(blockId: String, lineId: String, isTyping: Boolean) {
        if (isTyping) {
            focusedLineIdsByBlock.getOrPut(blockId) { mutableSetOf() }.add(lineId)
            keepBlock(blockId)
        } else {
            val focusedLineIds = focusedLineIdsByBlock[blockId] ?: return
            focusedLineIds.remove(lineId)
            if (focusedLineIds.isEmpty()) {
                focusedLineIdsByBlock.remove(blockId)
                resumeEmptyBlockCleanup(blockId)
            }
        }
    }

    fun resumeEmptyBlockCleanup(blockId: String) {
        val tab = repository.tabs.value.firstOrNull { candidate ->
            candidate.blocks.any { it.id == blockId }
        } ?: return
        scheduleEmptyBlockRemoval(tab.id, blockId)
    }

    fun removeLine(blockId: String, lineId: String) {
        updateBlock(blockId) { block ->
            block.removeLine(lineId)
        }
    }

    fun insertLineAfter(
        blockId: String,
        currentLineId: String,
        text: String = "",
        newLineId: String = UUID.randomUUID().toString()
    ): String {
        updateBlock(blockId) { block ->
            val index = block.lines.indexOfFirst { it.id == currentLineId }
            val newLines = block.lines.toMutableList()
            val newLine = NoteLine(id = newLineId, text = text)
            if (index >= 0) {
                newLines.add(index + 1, newLine)
            } else {
                newLines.add(newLine)
            }
            block.copy(lines = newLines)
        }
        return newLineId
    }

    fun splitLineOnEnter(
        blockId: String,
        currentLineId: String,
        beforeCursor: String,
        afterCursor: String,
        newLineId: String = UUID.randomUUID().toString()
    ): String {
        updateBlock(blockId) { block ->
            val index = block.lines.indexOfFirst { it.id == currentLineId }
            if (index < 0) return@updateBlock block
            val updatedCurrent = block.lines[index].copy(text = beforeCursor)
            val newLine = NoteLine(id = newLineId, text = afterCursor)
            val newLines = block.lines.toMutableList().apply {
                set(index, updatedCurrent)
                add(index + 1, newLine)
            }
            block.copy(lines = newLines)
        }
        return newLineId
    }

    fun updateLine(blockId: String, lineId: String, newText: String) {
        updateBlock(blockId) { block ->
            val updatedLines = block.lines.map {
                if (it.id == lineId) it.copy(text = newText) else it
            }
            block.copy(lines = updatedLines)
        }
    }

    /**
     * Tracks the currently active hardware recording before the user taps Stop.
     * No mid-recording patches are sent; once stopped, the single complete WAV file is processed
     * in two clean requests (1: STT transcription, 2: Persian->English translation OR English linguistic analysis).
     */
    private data class ActiveVoiceCapture(
        val itemId: String,
        val blockId: String,
        val audioFile: File
    )

    private var activeVoiceCapture: ActiveVoiceCapture? = null

    // Voice recording management (single whole voice recording)
    fun startVoiceRecording(blockId: String) {
        keepBlock(blockId)
        activeRecordingBlockId = blockId

        val itemId = UUID.randomUUID().toString()
        val file = recorderHelper.startRecording()

        if (file != null) {
            activeVoiceCapture = ActiveVoiceCapture(
                itemId = itemId,
                blockId = blockId,
                audioFile = file
            )

            AvalAiClient.warmUpConnectionAsync()
            _isRecording.value = true
            _recordingDurationSec.value = 0
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                while (_isRecording.value) {
                    delay(1000)
                    _recordingDurationSec.value += 1
                }
            }
        } else {
            activeRecordingBlockId = null
            resumeEmptyBlockCleanup(blockId)
        }
    }

    fun stopVoiceRecordingAndTranscribe(blockId: String) {
        if (!_isRecording.value) return
        _isRecording.value = false
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val capture = activeVoiceCapture
        val targetBlockId = capture?.blockId ?: activeRecordingBlockId ?: blockId
        activeVoiceCapture = null
        activeRecordingBlockId = null

        val result = recorderHelper.stopRecording()
        if (result == null) {
            resumeEmptyBlockCleanup(targetBlockId)
            return
        }

        val itemId = capture?.itemId ?: UUID.randomUUID().toString()

        // If the entire recording had zero audible speech, mark it immediately without wasting network calls
        if (!result.hasSpeech) {
            val silentItem = MediaItem(
                id = itemId,
                type = MediaType.AUDIO,
                filePath = result.file.absolutePath,
                isProcessing = false,
                isTranscribed = true,
                transcriptionError = null,
                durationSec = result.durationSec,
                transcription = GeminiService.SILENCE_TRANSCRIPTION,
                englishTranslation = null,
                corrections = emptyList()
            )
            keepBlock(targetBlockId)
            viewModelScope.launch {
                repository.updateTabs { currentTabs ->
                    currentTabs.map { tab ->
                        if (tab.blocks.none { it.id == targetBlockId }) {
                            tab
                        } else {
                            tab.copy(blocks = tab.blocks.map { block ->
                                if (block.id == targetBlockId) {
                                    block.copy(mediaItems = block.mediaItems + silentItem)
                                } else {
                                    block
                                }
                            })
                        }
                    }
                }
                resumeEmptyBlockCleanup(targetBlockId)
            }
            return
        }

        val voiceMediaItem = MediaItem(
            id = itemId,
            type = MediaType.AUDIO,
            filePath = result.file.absolutePath,
            isProcessing = true,
            isTranscribed = false,
            transcriptionError = null,
            durationSec = result.durationSec,
            transcription = null,
            englishTranslation = null,
            corrections = emptyList()
        )

        keepBlock(targetBlockId)
        viewModelScope.launch {
            repository.updateTabs { currentTabs ->
                currentTabs.map { tab ->
                    if (tab.blocks.none { it.id == targetBlockId }) {
                        tab
                    } else {
                        tab.copy(blocks = tab.blocks.map { block ->
                            if (block.id == targetBlockId) {
                                block.copy(mediaItems = block.mediaItems + voiceMediaItem)
                            } else {
                                block
                            }
                        })
                    }
                }
            }
            startTranscriptionWithRetry(targetBlockId, voiceMediaItem)
        }
    }

    fun retryAudioTranscription(blockId: String, itemId: String) {
        val currentBlock = tabs.value
            .flatMap { it.blocks }
            .firstOrNull { it.id == blockId }
            ?: return

        val item = currentBlock.mediaItems.firstOrNull { it.id == itemId } ?: return
        if (transcriptionRetryJobs[itemId]?.isActive == true) return

        val resettingItem = item.copy(
            isProcessing = true,
            isTranscribed = false,
            transcriptionError = null,
            transcription = null,
            englishTranslation = null,
            corrections = emptyList()
        )
        updateBlock(blockId) { block ->
            block.copy(mediaItems = block.mediaItems.map { media ->
                if (media.id == itemId) resettingItem else media
            })
        }
        startTranscriptionWithRetry(blockId, resettingItem)
    }

    fun applyAudioCorrection(blockId: String, itemId: String, correctionId: String) {
        updateBlock(blockId) { block ->
            block.copy(mediaItems = block.mediaItems.map { media ->
                if (media.id != itemId) return@map media
                val currentText = media.transcription ?: return@map media
                val (updatedText, remainingCorrections) = CorrectionJsonCodec.applyCorrectionToText(
                    text = currentText,
                    corrections = media.corrections,
                    correctionId = correctionId
                )
                media.copy(
                    transcription = updatedText,
                    corrections = remainingCorrections
                )
            })
        }
    }

    /**
     * Retries full-recording lossless WAV transcription + post-analysis on demand.
     */
    private fun startTranscriptionWithRetry(
        blockId: String,
        item: MediaItem,
        retriesRemaining: Int = TRANSCRIPTION_MAX_ATTEMPTS
    ) {
        transcriptionRetryJobs.remove(item.id)?.cancel()
        transcriptionRetryJobs[item.id] = viewModelScope.launch {
            try {
                var attemptsLeft = retriesRemaining.coerceAtLeast(1)
                val audioFile = File(item.filePath)
                var lastError: String? = null

                while (isActive && attemptsLeft > 0) {
                    attemptsLeft -= 1
                    geminiPreferences.refreshFromSharedSettings()
                    val sttOption = geminiPreferences.activeSttOption()
                    val analysisTarget = geminiPreferences.resolvePostAnalysisTarget()
                    val result = try {
                        withTimeout(PER_REQUEST_TIMEOUT_MS) {
                            geminiService.analyzeVoiceNote(
                                audioFile = audioFile,
                                avalaiKeyCandidates = geminiPreferences.getOrderedKeyCandidates(sttOption.provider),
                                model = sttOption.id,
                                provider = sttOption.provider,
                                analysisModel = analysisTarget.model,
                                analysisProvider = analysisTarget.provider,
                                analysisKeyCandidates = analysisTarget.keyCandidates,
                                onVerbatimTranscriptReady = { verbatimTranscript ->
                                    updateMediaItemInBlock(blockId, item.id) { media ->
                                        media.copy(
                                            transcription = verbatimTranscript,
                                            transcriptionError = null
                                        )
                                    }
                                },
                                onAvalaiKeySucceeded = { poolIdx ->
                                    geminiPreferences.markKeySucceeded(poolIdx, sttOption.provider)
                                },
                                onAnalysisKeySucceeded = { poolIdx ->
                                    geminiPreferences.markKeySucceeded(poolIdx, analysisTarget.provider)
                                }
                            )
                        }
                    } catch (te: TimeoutCancellationException) {
                        Result.failure(Exception("Voice note transcription timed out.", te))
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Result.failure(e)
                    }

                    if (result.isSuccess) {
                        val analysis = result.getOrThrow()
                        updateMediaItemInBlock(blockId, item.id) { media ->
                            media.copy(
                                isProcessing = false,
                                isTranscribed = true,
                                transcriptionError = null,
                                transcription = analysis.transcription,
                                englishTranslation = analysis.englishTranslation,
                                corrections = analysis.corrections
                            )
                        }
                        return@launch
                    }

                    lastError = result.exceptionOrNull()?.localizedMessage
                    if (lastError == AvalAiClient.MISSING_AVALAI_KEY_MESSAGE) {
                        break
                    }
                    if (attemptsLeft > 0) {
                        delay(TRANSCRIPTION_RETRY_DELAY_MS)
                    }
                }

                updateMediaItemInBlock(blockId, item.id) { media ->
                    media.copy(
                        isProcessing = false,
                        isTranscribed = false,
                        transcriptionError = lastError
                            ?: "Could not transcribe this recording. Check your connection or API key and tap Retry.",
                        transcription = null,
                        englishTranslation = null,
                        corrections = emptyList()
                    )
                }
            } finally {
                transcriptionRetryJobs.remove(item.id)
            }
        }
    }

    private fun updateMediaItemInBlock(
        blockId: String,
        itemId: String,
        transform: (MediaItem) -> MediaItem
    ) {
        updateBlock(blockId) { block ->
            if (block.mediaItems.none { it.id == itemId }) {
                block
            } else {
                block.copy(mediaItems = block.mediaItems.map { media ->
                    if (media.id == itemId) transform(media) else media
                })
            }
        }
    }

    fun cancelVoiceRecording() {
        _isRecording.value = false
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val capture = activeVoiceCapture
        val blockId = capture?.blockId ?: activeRecordingBlockId
        activeVoiceCapture = null
        activeRecordingBlockId = null

        recorderHelper.cancelRecording()
        if (capture != null && blockId != null) {
            updateBlock(blockId) { block ->
                block.copy(mediaItems = block.mediaItems.filterNot { it.id == capture.itemId })
            }
        }
        blockId?.let(::resumeEmptyBlockCleanup)
    }

    // Image capture management
    fun addImageBitmap(blockId: String, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val imagesDir = File(getApplication<Application>().filesDir, "image_notes")
                if (!imagesDir.exists()) imagesDir.mkdirs()

                val uniqueTag = UUID.randomUUID().toString().take(8)
                val file = File(imagesDir, "img_${System.currentTimeMillis()}_$uniqueTag.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val item = MediaItem(
                    type = MediaType.IMAGE,
                    filePath = file.absolutePath,
                    isTranscribed = false
                )

                updateBlock(blockId) { block ->
                    block.copy(mediaItems = block.mediaItems + item)
                }
            } catch (e: Exception) {
                Log.e("NotepadViewModel", "Failed to save image", e)
                resumeEmptyBlockCleanup(blockId)
            }
        }
    }

    fun addImageUri(blockId: String, uri: Uri) {
        addImageUris(blockId, listOf(uri))
    }

    fun addImageUris(blockId: String, uris: List<Uri>) {
        if (uris.isEmpty()) {
            resumeEmptyBlockCleanup(blockId)
            return
        }
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val addedItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val imagesDir = File(context.filesDir, "image_notes")
                    if (!imagesDir.exists()) imagesDir.mkdirs()

                    val baseNow = System.currentTimeMillis()
                    buildList {
                        for ((index, uri) in uris.withIndex()) {
                            val inputStream = if (uri.scheme == "file" && !uri.path.isNullOrEmpty()) {
                                runCatching { File(uri.path!!).inputStream() }.getOrNull()
                            } else {
                                context.contentResolver.openInputStream(uri)
                            } ?: continue
                            val uniqueTag = UUID.randomUUID().toString().take(8)
                            val file = File(imagesDir, "img_${baseNow}_${index}_$uniqueTag.jpg")
                            inputStream.use { stream ->
                                FileOutputStream(file).use { out ->
                                    stream.copyTo(out)
                                }
                            }
                            add(
                                MediaItem(
                                    type = MediaType.IMAGE,
                                    filePath = file.absolutePath,
                                    isTranscribed = false,
                                    timestamp = baseNow + index
                                )
                            )
                        }
                    }
                }

                if (addedItems.isEmpty()) {
                    resumeEmptyBlockCleanup(blockId)
                    return@launch
                }

                updateBlock(blockId) { block ->
                    block.copy(mediaItems = block.mediaItems + addedItems)
                }
            } catch (e: Exception) {
                Log.e("NotepadViewModel", "Failed to copy image URIs", e)
                resumeEmptyBlockCleanup(blockId)
            }
        }
    }

    // Delete popup
    fun requestDeleteItem(blockId: String, item: MediaItem) {
        _itemToDelete.value = Pair(blockId, item)
    }

    fun confirmDeleteItem() {
        val target = _itemToDelete.value ?: return
        val blockId = target.first
        val item = target.second

        transcriptionRetryJobs.remove(item.id)?.cancel()

        updateBlock(blockId) { block ->
            block.copy(mediaItems = block.mediaItems.filterNot { it.id == item.id })
        }
        _itemToDelete.value = null
    }

    fun dismissDeletePopup() {
        _itemToDelete.value = null
    }

    // Previews & Details
    fun showImagePreview(item: MediaItem) {
        _previewImageItem.value = item
    }

    fun dismissImagePreview() {
        _previewImageItem.value = null
    }

    fun showAudioDetails(item: MediaItem) {
        _selectedAudioItem.value = item
        if (item.filePath.isNotEmpty()) {
            playerHelper.playAudio(item.id, item.filePath)
        }
    }

    fun dismissAudioDetails() {
        _selectedAudioItem.value = null
        playerHelper.stopAudio()
    }

    fun toggleActiveAudioPlayback(item: MediaItem) {
        if (playerHelper.isPlaying(item.id)) {
            playerHelper.stopAudio()
        } else {
            if (item.filePath.isNotEmpty()) {
                playerHelper.playAudio(item.id, item.filePath)
            }
        }
    }

    private fun updateBlock(blockId: String, transform: (NotepadBlock) -> NotepadBlock) {
        keepBlock(blockId)
        viewModelScope.launch {
            repository.updateTabs { currentTabs ->
                currentTabs.map { tab ->
                    if (tab.blocks.none { it.id == blockId }) {
                        tab
                    } else {
                        tab.copy(blocks = tab.blocks.map { block ->
                            if (block.id == blockId) transform(block) else block
                        })
                    }
                }
            }
            resumeEmptyBlockCleanup(blockId)
        }
    }

    private fun scheduleEmptyBlockRemoval(tabId: String, blockId: String) {
        emptyBlockCleanupJobs.remove(blockId)?.cancel()

        val tab = repository.tabs.value.firstOrNull { it.id == tabId } ?: return
        val blockIndex = tab.blocks.indexOfFirst { it.id == blockId }
        if (blockIndex <= 0 || tab.blocks.size <= 1 || tab.blocks[blockIndex].hasContent()) return

        emptyBlockCleanupJobs[blockId] = viewModelScope.launch {
            delay(EMPTY_BLOCK_REMOVAL_DELAY_MS)
            repository.updateTabs { currentTabs ->
                currentTabs.map { currentTab ->
                    if (currentTab.id == tabId) {
                        currentTab.removeEmptySecondaryBlock(blockId)
                    } else {
                        currentTab
                    }
                }
            }
            emptyBlockCleanupJobs.remove(blockId)
        }
    }

    private companion object {
        const val TAG = "NotepadViewModel"
        const val INITIAL_LINE_COUNT = 3
        const val EMPTY_BLOCK_REMOVAL_DELAY_MS = 10_000L
        const val TRANSCRIPTION_RETRY_DELAY_MS = 3_000L
        const val TRANSCRIPTION_MAX_ATTEMPTS = 3
        const val PER_REQUEST_TIMEOUT_MS = 90_000L
    }

    override fun onCleared() {
        super.onCleared()
        emptyBlockCleanupJobs.values.forEach(Job::cancel)
        emptyBlockCleanupJobs.clear()
        transcriptionRetryJobs.values.forEach(Job::cancel)
        transcriptionRetryJobs.clear()
        activeVoiceCapture = null
        playerHelper.stopAudio()
        recorderHelper.cancelRecording()
    }
}
