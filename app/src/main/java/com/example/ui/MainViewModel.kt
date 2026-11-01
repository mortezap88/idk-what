package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.FlashDataBackupManager
import com.example.backup.FlashDataSummary
import com.example.data.AppDatabase
import com.example.data.ExamQuestionParser
import com.example.data.Flashcard
import com.example.data.FlashcardStatus
import com.example.data.FlashcardTypes
import com.example.data.QuestionBankExporter
import com.example.data.SpacedRepetitionUtils
import com.example.data.SrsStage
import com.example.data.Subject
import com.example.data.SubjectImageAttachment
import com.example.data.SubjectImagesCodec
import com.example.network.ChatMessage
import com.example.network.CorrectionJsonCodec
import com.example.network.GeminiRepository
import com.example.network.TutorMessageFormatter
import com.example.network.TutorResponseMode
import com.example.notepad.data.repository.NotesRepository
import com.example.settings.ApiKeyProvider
import com.example.settings.AppSettings
import com.example.settings.AppSettingsRepository
import com.example.settings.AppThemePreference
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SubjectMaterialKind {
    TEXTBOOK,
    EXTRA_KNOWLEDGE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val flashcardDao = database.flashcardDao()
    private val settingsRepository = AppSettingsRepository(application)
    private val notesRepository = NotesRepository.getInstance(application)
    private val backupManager = FlashDataBackupManager(
        context = application,
        flashcardDao = flashcardDao,
        settingsRepository = settingsRepository,
        notesRepository = notesRepository
    )
    private val geminiRepo = GeminiRepository(settingsRepository)
    val ttsHelper = TtsHelper(application)
    val ttsVoices: StateFlow<List<TtsVoiceOption>> = ttsHelper.voices

    private val _appSettings = MutableStateFlow(settingsRepository.loadSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    val allCards: StateFlow<List<Flashcard>> = flashcardDao.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjects: StateFlow<List<Subject>> = flashcardDao.getAllSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Creation Chat State
    private val _creationChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val creationChatMessages: StateFlow<List<ChatMessage>> = _creationChatMessages.asStateFlow()

    private val _isCreationThinking = MutableStateFlow(false)
    val isCreationThinking: StateFlow<Boolean> = _isCreationThinking.asStateFlow()

    // Subject "Add a Question" Conversation State
    private val _subjectQuestionChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val subjectQuestionChatMessages: StateFlow<List<ChatMessage>> = _subjectQuestionChatMessages.asStateFlow()

    private val _isSubjectQuestionThinking = MutableStateFlow(false)
    val isSubjectQuestionThinking: StateFlow<Boolean> = _isSubjectQuestionThinking.asStateFlow()

    private val _subjectQuestionDraftState =
        MutableStateFlow<SubjectQuestionDraftState>(SubjectQuestionDraftState.Idle)
    val subjectQuestionDraftState: StateFlow<SubjectQuestionDraftState> =
        _subjectQuestionDraftState.asStateFlow()

    private var subjectDraftGenerationJob: Job? = null

    // Subject AI Question Generation State
    private val _generatingQuestionsSubjectIds = MutableStateFlow<Set<Long>>(emptySet())
    val generatingQuestionsSubjectIds: StateFlow<Set<Long>> = _generatingQuestionsSubjectIds.asStateFlow()

    private val _generatingQuestionProgress = MutableStateFlow<Map<Long, SubjectGenerationProgress>>(emptyMap())
    val generatingQuestionProgress: StateFlow<Map<Long, SubjectGenerationProgress>> =
        _generatingQuestionProgress.asStateFlow()

    private val _subjectStatusMessages = MutableStateFlow<Map<Long, SubjectStatusNotice>>(emptyMap())
    val subjectStatusMessages: StateFlow<Map<Long, SubjectStatusNotice>> = _subjectStatusMessages.asStateFlow()

    private val subjectImagesMutex = Mutex()
    private val subjectOcrJobs = mutableMapOf<String, Job>()

    // Active Review Session State
    private val _activeReviewList = MutableStateFlow<List<Flashcard>>(emptyList())
    val activeReviewList: StateFlow<List<Flashcard>> = _activeReviewList.asStateFlow()

    private val _currentReviewIndex = MutableStateFlow(0)
    val currentReviewIndex: StateFlow<Int> = _currentReviewIndex.asStateFlow()

    private val _reviewScenarioLoading = MutableStateFlow(false)
    val reviewScenarioLoading: StateFlow<Boolean> = _reviewScenarioLoading.asStateFlow()

    private val _currentScenarioOrQuestion = MutableStateFlow<String?>(null)
    val currentScenarioOrQuestion: StateFlow<String?> = _currentScenarioOrQuestion.asStateFlow()

    private val _reviewChatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val reviewChatHistory: StateFlow<List<ChatMessage>> = _reviewChatHistory.asStateFlow()

    private val _reviewContextHistory = MutableStateFlow<List<ChatMessage>>(emptyList())

    private var lastReviewReplyText: String? = null
    private var lastReviewReplyAudioBase64: String? = null

    private val _isReviewReplying = MutableStateFlow(false)
    val isReviewReplying: StateFlow<Boolean> = _isReviewReplying.asStateFlow()

    // Review "Extract Question" Conversation & Draft State
    private val _isReviewExtractOpen = MutableStateFlow(false)
    val isReviewExtractOpen: StateFlow<Boolean> = _isReviewExtractOpen.asStateFlow()

    private val _reviewExtractChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val reviewExtractChatMessages: StateFlow<List<ChatMessage>> = _reviewExtractChatMessages.asStateFlow()

    private val _isReviewExtractThinking = MutableStateFlow(false)
    val isReviewExtractThinking: StateFlow<Boolean> = _isReviewExtractThinking.asStateFlow()

    private val _reviewExtractDraftState =
        MutableStateFlow<SubjectQuestionDraftState>(SubjectQuestionDraftState.Idle)
    val reviewExtractDraftState: StateFlow<SubjectQuestionDraftState> =
        _reviewExtractDraftState.asStateFlow()

    private var reviewExtractDraftJob: Job? = null

    init {
        seedInitialCardsIfEmpty()
    }

    fun setThemePreference(theme: AppThemePreference) {
        settingsRepository.saveTheme(theme)
        _appSettings.value = _appSettings.value.copy(theme = theme, errorMessage = null)
    }

    fun refreshSettings() {
        _appSettings.value = settingsRepository.loadSettings()
    }

    fun setTokenStreaming(enabled: Boolean) {
        settingsRepository.saveTokenStreaming(enabled)
        _appSettings.value = _appSettings.value.copy(tokenStreaming = enabled, errorMessage = null)
    }

    fun setMaxFontSize(sizeSp: Int) {
        settingsRepository.saveMaxFontSize(sizeSp)
        _appSettings.value = _appSettings.value.copy(maxFontSizeSp = sizeSp, errorMessage = null)
    }

    fun setTtsVoice(voiceName: String?) {
        settingsRepository.saveTtsVoice(voiceName)
        _appSettings.value = _appSettings.value.copy(ttsVoiceName = voiceName, errorMessage = null)
    }

    fun setTtsRate(rate: Float) {
        settingsRepository.saveTtsRate(rate)
        _appSettings.value = _appSettings.value.copy(
            ttsRate = rate.coerceIn(AppSettingsRepository.MIN_TTS_RATE, AppSettingsRepository.MAX_TTS_RATE),
            errorMessage = null
        )
    }

    /** Reads a short sample with the currently selected voice and rate. */
    fun testTts() {        val settings = _appSettings.value
        ttsHelper.speak(
            text = "FlashRecall will read your cards like this.",
            voiceName = settings.ttsVoiceName,
            rate = settings.ttsRate
        )
    }

    /** Reads [text] with the configured voice, stripping any hidden markup tags first. */
    fun speak(text: String) {
        val settings = _appSettings.value
        val speakable = TutorMessageFormatter.toPlainSpeakableText(text).ifBlank { text }
        ttsHelper.speak(speakable, settings.ttsVoiceName, settings.ttsRate)
    }

    /** Asks the TTS engine for its voice list again (engines publish it lazily). */
    fun refreshTtsVoices() {
        ttsHelper.refreshVoices()
    }

    fun speakText(text: String) {
        speak(text)
    }

    fun endReviewSession() {
        _activeReviewList.value = emptyList()
        _currentReviewIndex.value = 0
        _currentScenarioOrQuestion.value = null
        _reviewChatHistory.value = emptyList()
        _reviewContextHistory.value = emptyList()
        closeReviewExtractQuestion()
    }

    fun updateCardStage(card: Flashcard, stageIndex: Int) {
        scheduleCardAtStage(card, stageIndex)
    }

    fun deleteQuestion(card: Flashcard) {
        deleteCard(card)
    }

    fun restoreQuestion(card: Flashcard) {
        restoreCardActive(card)
    }

    fun saveTheme(theme: AppThemePreference) {
        setThemePreference(theme)
    }

    fun selectTttProvider(provider: ApiKeyProvider) {
        settingsRepository.saveTttProvider(provider)
        refreshSettings(null)
    }

    fun selectTttModel(modelId: String) {
        settingsRepository.saveTttModel(modelId)
        refreshSettings(null)
    }

    fun selectSttModel(modelId: String) {
        settingsRepository.saveSttModel(modelId)
        refreshSettings(null)
    }

    fun saveTokenStreaming(enabled: Boolean) {
        setTokenStreaming(enabled)
    }

    fun saveMaxFontSize(sizeSp: Int) {
        setMaxFontSize(sizeSp)
    }

    fun saveTtsVoice(voiceName: String?) {
        setTtsVoice(voiceName)
    }

    fun saveTtsRate(rate: Float) {
        setTtsRate(rate)
    }

    fun applyCreationCorrection(messageId: String, correctionId: String) {
        _creationChatMessages.value = applyCorrection(
            _creationChatMessages.value,
            messageId,
            correctionId
        )
    }

    fun applySubjectQuestionCorrection(messageId: String, correctionId: String) {
        _subjectQuestionChatMessages.value = applyCorrection(
            _subjectQuestionChatMessages.value,
            messageId,
            correctionId
        )
    }

    fun applyReviewExtractCorrection(messageId: String, correctionId: String) {
        _reviewExtractChatMessages.value = applyCorrection(
            _reviewExtractChatMessages.value,
            messageId,
            correctionId
        )
    }

    fun applyReviewCorrection(messageId: String, correctionId: String) {
        val updatedVisibleHistory = applyCorrection(
            _reviewChatHistory.value,
            messageId,
            correctionId
        )
        _reviewChatHistory.value = updatedVisibleHistory
        _reviewContextHistory.value = applyCorrection(
            _reviewContextHistory.value,
            messageId,
            correctionId
        )
        val currentCard = _activeReviewList.value.getOrNull(_currentReviewIndex.value) ?: return
        val serializedHistory = geminiRepo.serializeChat(_reviewContextHistory.value)
        viewModelScope.launch {
            flashcardDao.getCardById(currentCard.id)?.let { storedCard ->
                val updatedCard = storedCard.copy(conversationHistoryJson = serializedHistory)
                flashcardDao.updateCard(updatedCard)
                _activeReviewList.value = _activeReviewList.value.map { card ->
                    if (card.id == updatedCard.id) updatedCard else card
                }
            }
        }
    }

    private fun applyCorrection(
        messages: List<ChatMessage>,
        messageId: String,
        correctionId: String
    ): List<ChatMessage> = messages.map { message ->
        if (message.id != messageId) return@map message
        val (updatedText, remainingCorrections) = CorrectionJsonCodec.applyCorrectionToText(
            text = message.text,
            corrections = message.corrections,
            correctionId = correctionId
        )
        message.copy(
            text = updatedText,
            corrections = remainingCorrections
        )
    }

    fun addApiKey(provider: ApiKeyProvider, apiKey: String): Boolean {
        return try {
            settingsRepository.addApiKey(provider, apiKey)
            refreshSettings(null)
            true
        } catch (error: Exception) {
            _appSettings.value = _appSettings.value.copy(
                errorMessage = error.localizedMessage ?: "Could not save the ${provider.displayName} API key."
            )
            false
        }
    }

    fun removeApiKey(provider: ApiKeyProvider, index: Int) {
        settingsRepository.removeApiKeyAt(provider, index)
        refreshSettings(null)
    }

    fun clearApiKeys(provider: ApiKeyProvider) {
        settingsRepository.clearApiKeys(provider)
        refreshSettings(null)
    }

    private fun refreshSettings(errorMessage: String?) {
        _appSettings.value = settingsRepository.loadSettings().copy(errorMessage = errorMessage)
    }

    fun exportFlashData(uri: Uri) {
        if (_backupState.value is BackupUiState.Working) return
        _backupState.value = BackupUiState.Working("Exporting .flashdata backup…")
        viewModelScope.launch {
            backupManager.exportToUri(uri)
                .onSuccess { summary ->
                    _backupState.value = BackupUiState.Success(
                        formatBackupMessage("Exported .flashdata backup", summary)
                    )
                }
                .onFailure { error ->
                    _backupState.value = BackupUiState.Error(
                        error.localizedMessage ?: "Failed to export .flashdata backup."
                    )
                }
        }
    }

    fun importFlashData(uri: Uri) {
        if (_backupState.value is BackupUiState.Working) return
        _backupState.value = BackupUiState.Working("Importing .flashdata backup…")
        viewModelScope.launch {
            backupManager.importFromUri(uri)
                .onSuccess { summary ->
                    refreshSettings(null)
                    _creationChatMessages.value = emptyList()
                    _subjectQuestionChatMessages.value = emptyList()
                    _generatingQuestionProgress.value = emptyMap()
                    _subjectStatusMessages.value = emptyMap()
                    _activeReviewList.value = emptyList()
                    _currentReviewIndex.value = 0
                    _currentScenarioOrQuestion.value = null
                    _reviewChatHistory.value = emptyList()
                    _reviewContextHistory.value = emptyList()
                    lastReviewReplyText = null
                    lastReviewReplyAudioBase64 = null
                    _backupState.value = BackupUiState.Success(
                        formatBackupMessage("Restored .flashdata backup", summary)
                    )
                }
                .onFailure { error ->
                    _backupState.value = BackupUiState.Error(
                        error.localizedMessage ?: "Failed to import .flashdata backup."
                    )
                }
        }
    }

    private fun formatBackupMessage(prefix: String, summary: FlashDataSummary): String {
        val parts = buildList {
            add("${summary.apiKeyCount} API ${if (summary.apiKeyCount == 1) "key" else "keys"}")
            add("${summary.cardCount} review ${if (summary.cardCount == 1) "item" else "items"}")
            if (summary.subjectCount > 0) {
                add("${summary.subjectCount} ${if (summary.subjectCount == 1) "subject" else "subjects"}")
            }
            if (summary.notepadTabCount > 0) {
                add("${summary.notepadTabCount} note ${if (summary.notepadTabCount == 1) "tab" else "tabs"}")
            }
            if (summary.mediaFileCount > 0) {
                add("${summary.mediaFileCount} media ${if (summary.mediaFileCount == 1) "file" else "files"}")
            }
        }
        return "$prefix (${parts.joinToString(", ")})."
    }

    private fun seedInitialCardsIfEmpty() {
        viewModelScope.launch {
            if (flashcardDao.getCardCount() == 0) {
                val now = System.currentTimeMillis()
                val sampleChat = listOf(
                    ChatMessage(sender = "user", text = "I want to practice using 'Were it not for' in formal negotiation contexts."),
                    ChatMessage(sender = "model", text = "Excellent choice. 'Were it not for' is an inverted conditional expressing hypothetical necessity (e.g. 'Were it not for your team's dedication, we couldn't have closed the round'). It conveys polite yet firm emphasis.")
                )

                val initialCards = listOf(
                    Flashcard(
                        type = FlashcardTypes.SPELLING,
                        word = "Conscientious",
                        stageIndex = 0,
                        nextReviewTimeMillis = now - 1000L,
                        createdAtMillis = now
                    ),
                    Flashcard(
                        type = FlashcardTypes.PRONUNCIATION,
                        word = "Ubiquitous",
                        stageIndex = 0,
                        nextReviewTimeMillis = now - 500L,
                        createdAtMillis = now
                    ),
                    Flashcard(
                        type = FlashcardTypes.CONVERSATION_LINGUISTIC,
                        word = "Conditional Inversion: 'Were it not for...'",
                        stageIndex = 0,
                        nextReviewTimeMillis = now - 100L,
                        createdAtMillis = now,
                        conversationHistoryJson = geminiRepo.serializeChat(sampleChat),
                        focusSummary = "Grammatical structure for polite emphasis in formal discussions"
                    )
                )
                flashcardDao.insertAll(initialCards)
            }
        }
    }

    fun addFlashcard(
        type: String,
        word: String,
        backText: String? = null,
        imageUrl: String? = null,
        conversationHistory: List<ChatMessage>? = null,
        focusSummary: String? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val initialDue = now + SrsStage.TWO_MINUTES.durationMillis
            val card = Flashcard(
                type = type,
                word = word.trim(),
                backText = backText?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = imageUrl,
                stageIndex = 0,
                nextReviewTimeMillis = initialDue,
                createdAtMillis = now,
                conversationHistoryJson = conversationHistory?.let { geminiRepo.serializeChat(it) },
                focusSummary = focusSummary
            )
            flashcardDao.insertCard(card)
            clearCreationChat()
        }
    }

    // --- Subject centre ---

    /**
     * Creates a new empty subject from just its [name].
     *
     * @return true if the name was valid and queued for saving.
     */
    fun createSubject(name: String): Boolean {
        val subjectName = name.trim()
        if (subjectName.isEmpty()) return false

        viewModelScope.launch {
            flashcardDao.insertSubject(Subject(name = subjectName))
        }
        return true
    }

    fun updateSubjectTextbook(subjectId: Long, textbook: String) {
        viewModelScope.launch {
            flashcardDao.updateSubjectTextbook(subjectId, textbook.trim())
            dismissSubjectStatus(subjectId)
        }
    }

    fun updateSubjectExtraKnowledge(subjectId: Long, extraKnowledge: String) {
        viewModelScope.launch {
            flashcardDao.updateSubjectExtraKnowledge(subjectId, extraKnowledge.trim())
        }
    }

    fun addSubjectMaterialBitmap(
        subjectId: Long,
        kind: SubjectMaterialKind,
        bitmap: Bitmap
    ) {
        viewModelScope.launch {
            val savedFile = withContext(Dispatchers.IO) {
                try {
                    val dir = File(getApplication<Application>().filesDir, "subject_images")
                    if (!dir.exists()) dir.mkdirs()
                    val uniqueTag = UUID.randomUUID().toString().take(8)
                    val file = File(dir, "subj_${subjectId}_${System.currentTimeMillis()}_$uniqueTag.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    file
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch

            val attachment = SubjectImageAttachment(
                filePath = savedFile.absolutePath,
                ocrText = "",
                isProcessingOcr = true,
                ocrFailed = false,
                timestamp = System.currentTimeMillis()
            )
            appendSubjectMaterialAttachments(subjectId, kind, listOf(attachment))
            if (kind == SubjectMaterialKind.TEXTBOOK) {
                dismissSubjectStatus(subjectId)
            }
            launchSubjectImageOcr(subjectId, kind, attachment.id, savedFile)
        }
    }

    fun addSubjectMaterialUris(
        subjectId: Long,
        kind: SubjectMaterialKind,
        uris: List<Uri>
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val baseNow = System.currentTimeMillis()
            val savedAttachments = withContext(Dispatchers.IO) {
                val dir = File(app.filesDir, "subject_images")
                if (!dir.exists()) dir.mkdirs()
                uris.mapIndexedNotNull { index, uri ->
                    try {
                        val inputStream = app.contentResolver.openInputStream(uri) ?: return@mapIndexedNotNull null
                        val uniqueTag = UUID.randomUUID().toString().take(8)
                        val file = File(dir, "subj_${subjectId}_${baseNow}_${index}_$uniqueTag.jpg")
                        inputStream.use { input ->
                            FileOutputStream(file).use { out ->
                                input.copyTo(out)
                            }
                        }
                        SubjectImageAttachment(
                            filePath = file.absolutePath,
                            ocrText = "",
                            isProcessingOcr = true,
                            ocrFailed = false,
                            timestamp = baseNow + index
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            if (savedAttachments.isEmpty()) return@launch

            appendSubjectMaterialAttachments(subjectId, kind, savedAttachments)
            if (kind == SubjectMaterialKind.TEXTBOOK) {
                dismissSubjectStatus(subjectId)
            }
            for (attachment in savedAttachments) {
                launchSubjectImageOcr(subjectId, kind, attachment.id, File(attachment.filePath))
            }
        }
    }

    fun deleteSubjectMaterialImage(
        subjectId: Long,
        kind: SubjectMaterialKind,
        imageId: String
    ) {
        subjectOcrJobs.remove(imageId)?.cancel()
        viewModelScope.launch {
            var removedPath: String? = null
            subjectImagesMutex.withLock {
                val subject = flashcardDao.getSubjectById(subjectId) ?: return@withLock
                val current = when (kind) {
                    SubjectMaterialKind.TEXTBOOK -> subject.textbookImages()
                    SubjectMaterialKind.EXTRA_KNOWLEDGE -> subject.extraKnowledgeImages()
                }
                removedPath = current.firstOrNull { it.id == imageId }?.filePath
                val updatedJson = SubjectImagesCodec.encode(current.filterNot { it.id == imageId })
                when (kind) {
                    SubjectMaterialKind.TEXTBOOK ->
                        flashcardDao.updateSubjectTextbookImages(subjectId, updatedJson)
                    SubjectMaterialKind.EXTRA_KNOWLEDGE ->
                        flashcardDao.updateSubjectExtraKnowledgeImages(subjectId, updatedJson)
                }
            }
            removedPath?.let { path ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                }
            }
        }
    }

    fun retrySubjectMaterialImageOcr(
        subjectId: Long,
        kind: SubjectMaterialKind,
        imageId: String
    ) {
        subjectOcrJobs.remove(imageId)?.cancel()
        viewModelScope.launch {
            var targetFile: File? = null
            subjectImagesMutex.withLock {
                val subject = flashcardDao.getSubjectById(subjectId) ?: return@withLock
                val current = when (kind) {
                    SubjectMaterialKind.TEXTBOOK -> subject.textbookImages()
                    SubjectMaterialKind.EXTRA_KNOWLEDGE -> subject.extraKnowledgeImages()
                }
                val updated = current.map { item ->
                    if (item.id == imageId) {
                        targetFile = File(item.filePath)
                        item.copy(isProcessingOcr = true, ocrFailed = false)
                    } else {
                        item
                    }
                }
                val updatedJson = SubjectImagesCodec.encode(updated)
                when (kind) {
                    SubjectMaterialKind.TEXTBOOK ->
                        flashcardDao.updateSubjectTextbookImages(subjectId, updatedJson)
                    SubjectMaterialKind.EXTRA_KNOWLEDGE ->
                        flashcardDao.updateSubjectExtraKnowledgeImages(subjectId, updatedJson)
                }
            }
            val file = targetFile ?: return@launch
            launchSubjectImageOcr(subjectId, kind, imageId, file)
        }
    }

    private suspend fun appendSubjectMaterialAttachments(
        subjectId: Long,
        kind: SubjectMaterialKind,
        additions: List<SubjectImageAttachment>
    ) {
        subjectImagesMutex.withLock {
            val subject = flashcardDao.getSubjectById(subjectId) ?: return
            val current = when (kind) {
                SubjectMaterialKind.TEXTBOOK -> subject.textbookImages()
                SubjectMaterialKind.EXTRA_KNOWLEDGE -> subject.extraKnowledgeImages()
            }
            val updatedJson = SubjectImagesCodec.encode(current + additions)
            when (kind) {
                SubjectMaterialKind.TEXTBOOK ->
                    flashcardDao.updateSubjectTextbookImages(subjectId, updatedJson)
                SubjectMaterialKind.EXTRA_KNOWLEDGE ->
                    flashcardDao.updateSubjectExtraKnowledgeImages(subjectId, updatedJson)
            }
        }
    }

    private fun launchSubjectImageOcr(
        subjectId: Long,
        kind: SubjectMaterialKind,
        imageId: String,
        imageFile: File
    ) {
        subjectOcrJobs.remove(imageId)?.cancel()
        val job = viewModelScope.launch {
            runSubjectImageOcrInternal(subjectId, kind, imageId, imageFile)
        }
        subjectOcrJobs[imageId] = job
        job.invokeOnCompletion {
            if (subjectOcrJobs[imageId] === job) {
                subjectOcrJobs.remove(imageId)
            }
        }
    }

    private suspend fun runSubjectImageOcrInternal(
        subjectId: Long,
        kind: SubjectMaterialKind,
        imageId: String,
        imageFile: File
    ) {
        val result = geminiRepo.extractTextFromImage(imageFile)
        val extracted = result.getOrNull()?.trim().orEmpty()
        val failed = result.isFailure
        subjectImagesMutex.withLock {
            val subject = flashcardDao.getSubjectById(subjectId) ?: return
            val current = when (kind) {
                SubjectMaterialKind.TEXTBOOK -> subject.textbookImages()
                SubjectMaterialKind.EXTRA_KNOWLEDGE -> subject.extraKnowledgeImages()
            }
            if (current.none { it.id == imageId }) return
            val updated = current.map { item ->
                if (item.id == imageId) {
                    item.copy(
                        ocrText = if (extracted.isNotEmpty()) extracted else item.ocrText,
                        isProcessingOcr = false,
                        ocrFailed = failed && item.ocrText.isBlank()
                    )
                } else {
                    item
                }
            }
            val updatedJson = SubjectImagesCodec.encode(updated)
            when (kind) {
                SubjectMaterialKind.TEXTBOOK ->
                    flashcardDao.updateSubjectTextbookImages(subjectId, updatedJson)
                SubjectMaterialKind.EXTRA_KNOWLEDGE ->
                    flashcardDao.updateSubjectExtraKnowledgeImages(subjectId, updatedJson)
            }
        }
    }

    private suspend fun ensureSubjectImagesOcrCompleted(subjectId: Long) {
        val snapshot = flashcardDao.getSubjectById(subjectId) ?: return
        for (item in snapshot.textbookImages()) {
            subjectOcrJobs[item.id]?.join()
        }
        for (item in snapshot.extraKnowledgeImages()) {
            subjectOcrJobs[item.id]?.join()
        }
        val refreshed = flashcardDao.getSubjectById(subjectId) ?: return
        for (item in refreshed.textbookImages()) {
            if (item.ocrText.isBlank()) {
                val file = File(item.filePath)
                if (file.exists()) {
                    runSubjectImageOcrInternal(subjectId, SubjectMaterialKind.TEXTBOOK, item.id, file)
                }
            }
        }
        for (item in refreshed.extraKnowledgeImages()) {
            if (item.ocrText.isBlank()) {
                val file = File(item.filePath)
                if (file.exists()) {
                    runSubjectImageOcrInternal(subjectId, SubjectMaterialKind.EXTRA_KNOWLEDGE, item.id, file)
                }
            }
        }
    }

    fun dismissSubjectStatus(subjectId: Long) {
        _subjectStatusMessages.value = _subjectStatusMessages.value - subjectId
    }

    private fun postSubjectStatus(
        subjectId: Long,
        text: String,
        isError: Boolean,
        isWarning: Boolean = false
    ) {
        _subjectStatusMessages.value = _subjectStatusMessages.value + (
            subjectId to SubjectStatusNotice(
                text = text,
                isError = isError,
                isWarning = isWarning
            )
        )
    }

    /**
     * Always reads the newest version of [subjectId]'s question bank from Room (with fallback to
     * the live [allCards] state) so additions, edits, status changes, and deletions are reflected immediately.
     */
    suspend fun getLatestSubjectQuestionBank(subjectId: Long): List<Flashcard> = withContext(Dispatchers.IO) {
        val persisted = flashcardDao.getCardsBySubjectSnapshot(subjectId)
        val source = persisted.ifEmpty {
            QuestionBankExporter.getSubjectQuestionBank(allCards.value, subjectId)
        }
        source.sortedWith(compareBy<Flashcard> { it.createdAtMillis }.thenBy { it.id })
    }

    fun copySubjectQuestionBank(subjectId: Long) {
        viewModelScope.launch {
            val subject = withContext(Dispatchers.IO) {
                flashcardDao.getSubjectById(subjectId)
            } ?: subjects.value.firstOrNull { it.id == subjectId }

            val latestQuestions = getLatestSubjectQuestionBank(subjectId)
            if (latestQuestions.isEmpty()) {
                postSubjectStatus(
                    subjectId = subjectId,
                    text = "Question bank is empty — generate or add questions first.",
                    isError = true
                )
                return@launch
            }

            val formattedBank = QuestionBankExporter.formatSubjectQuestionBank(
                subject = subject,
                questions = latestQuestions
            )
            val clipboard = getApplication<Application>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipLabel = "Question Bank - ${subject?.name ?: "Subject"}"
            clipboard?.setPrimaryClip(ClipData.newPlainText(clipLabel, formattedBank))

            postSubjectStatus(
                subjectId = subjectId,
                text = "Copied all ${latestQuestions.size} question${if (latestQuestions.size == 1) "" else "s"} from Question Bank to clipboard.",
                isError = false
            )
        }
    }

    fun deleteSubjectQuestionBank(subjectId: Long) {
        if (subjectId in _generatingQuestionsSubjectIds.value) return
        viewModelScope.launch {
            val latestQuestions = getLatestSubjectQuestionBank(subjectId)
            if (latestQuestions.isEmpty()) {
                postSubjectStatus(
                    subjectId = subjectId,
                    text = "Question bank is already empty.",
                    isError = true
                )
                return@launch
            }

            val deletedCount = latestQuestions.size
            withContext(Dispatchers.IO) {
                flashcardDao.deleteCardsBySubject(subjectId)
            }

            val remainingReview = _activeReviewList.value.filterNot { it.subjectId == subjectId }
            if (remainingReview.size != _activeReviewList.value.size) {
                _activeReviewList.value = remainingReview
                if (_currentReviewIndex.value >= remainingReview.size) {
                    _currentReviewIndex.value = (remainingReview.size - 1).coerceAtLeast(0)
                }
                loadActiveCardDetails()
            }

            postSubjectStatus(
                subjectId = subjectId,
                text = "Deleted all $deletedCount question${if (deletedCount == 1) "" else "s"} from Question Bank.",
                isError = false
            )
        }
    }

    fun generateQuestionsForSubject(subjectId: Long) {
        if (subjectId in _generatingQuestionsSubjectIds.value) return

        viewModelScope.launch {
            val initialSubject = flashcardDao.getSubjectById(subjectId)
                ?: subjects.value.firstOrNull { it.id == subjectId }
                ?: return@launch

            if (!initialSubject.hasTextbookContent()) {
                postSubjectStatus(
                    subjectId = subjectId,
                    text = "Add Textbook (answer reference) text or images first before generating questions.",
                    isError = true
                )
                return@launch
            }

            dismissSubjectStatus(subjectId)
            _generatingQuestionsSubjectIds.value = _generatingQuestionsSubjectIds.value + subjectId
            _generatingQuestionProgress.value = _generatingQuestionProgress.value + (
                subjectId to SubjectGenerationProgress(isThinking = true, reasoningChars = 0, addedCount = 0)
            )

            val existingQuestions = flashcardDao.getCardsBySubjectSnapshot(subjectId)
                .map { card ->
                    GeminiRepository.ExistingSubjectQuestion(
                        question = card.word,
                        status = card.status
                    )
                }

            val baseTimestamp = System.currentTimeMillis()
            val streamedChannel = Channel<ExamQuestionParser.ParsedQuestion>(Channel.UNLIMITED)
            var insertedCount = 0
            val insertedQuestions = HashSet<String>()

            val writerJob = launch {
                for (q in streamedChannel) {
                    if (insertedQuestions.add(q.question)) {
                        flashcardDao.insertCard(
                            q.toExamCard(
                                subjectId = subjectId,
                                createdAtMillis = baseTimestamp + insertedCount
                            )
                        )
                        insertedCount++
                        _generatingQuestionProgress.value = _generatingQuestionProgress.value + (
                            subjectId to SubjectGenerationProgress(
                                isThinking = false,
                                reasoningChars = _generatingQuestionProgress.value[subjectId]?.reasoningChars ?: 0,
                                addedCount = insertedCount
                            )
                        )
                    }
                }
            }

            try {
                ensureSubjectImagesOcrCompleted(subjectId)
                val subject = flashcardDao.getSubjectById(subjectId) ?: initialSubject
                val effectiveTextbook = subject.effectiveTextbook()
                val effectiveExtraKnowledge = subject.effectiveExtraKnowledge()

                if (effectiveTextbook.isBlank()) {
                    streamedChannel.close()
                    writerJob.join()
                    postSubjectStatus(
                        subjectId = subjectId,
                        text = "Could not extract readable text from the Textbook image(s). Check your API key or tap an image in Textbook to retry OCR.",
                        isError = true
                    )
                    return@launch
                }

                val result = geminiRepo.generateSubjectQuestions(
                    subjectName = subject.name,
                    textbook = effectiveTextbook,
                    extraKnowledge = effectiveExtraKnowledge,
                    existingQuestions = existingQuestions,
                    onReasoningProgress = { chars ->
                        val current = _generatingQuestionProgress.value[subjectId]
                        if (current == null || current.addedCount == 0) {
                            _generatingQuestionProgress.value = _generatingQuestionProgress.value + (
                                subjectId to SubjectGenerationProgress(
                                    isThinking = true,
                                    reasoningChars = chars,
                                    addedCount = current?.addedCount ?: 0
                                )
                            )
                        }
                    },
                    onQuestionStreamed = { parsedQuestion ->
                        streamedChannel.trySend(parsedQuestion)
                    }
                )

                streamedChannel.close()
                writerJob.join()

                result.onSuccess { outcome ->
                    for (q in outcome.questions) {
                        if (insertedQuestions.add(q.question)) {
                            flashcardDao.insertCard(
                                q.toExamCard(
                                    subjectId = subjectId,
                                    createdAtMillis = baseTimestamp + insertedCount
                                )
                            )
                            insertedCount++
                        }
                    }

                    when {
                        outcome.wasInterruptedMidStream && insertedCount > 0 -> {
                            postSubjectStatus(
                                subjectId = subjectId,
                                text = "Saved $insertedCount new question${if (insertedCount == 1) "" else "s"} before the stream paused — tap Generating questions again to continue from where it left off.",
                                isError = false,
                                isWarning = true
                            )
                        }

                        insertedCount == 0 -> {
                            postSubjectStatus(
                                subjectId = subjectId,
                                text = "Nothing new to generate — all Textbook concepts are already covered by your active, learned, and dumb questions.",
                                isError = false
                            )
                        }

                        else -> {
                            postSubjectStatus(
                                subjectId = subjectId,
                                text = "Generated $insertedCount new question${if (insertedCount == 1) "" else "s"} from Textbook.",
                                isError = false
                            )
                        }
                    }
                }.onFailure { error ->
                    if (insertedCount > 0) {
                        postSubjectStatus(
                            subjectId = subjectId,
                            text = "Saved $insertedCount new question${if (insertedCount == 1) "" else "s"} before the stream paused — tap Generating questions again to continue from where it left off.",
                            isError = false,
                            isWarning = true
                        )
                    } else {
                        postSubjectStatus(
                            subjectId = subjectId,
                            text = error.localizedMessage ?: "Failed to generate questions.",
                            isError = true
                        )
                    }
                }
            } finally {
                streamedChannel.close()
                _generatingQuestionsSubjectIds.value = _generatingQuestionsSubjectIds.value - subjectId
                _generatingQuestionProgress.value = _generatingQuestionProgress.value - subjectId
            }
        }
    }

    fun sendSubjectQuestionMessage(
        subjectId: Long,
        text: String,
        imageBitmaps: List<Bitmap> = emptyList(),
        audioBase64: String? = null
    ) {
        if (text.isBlank() && imageBitmaps.isEmpty() && audioBase64.isNullOrBlank()) return

        val encodedImages = imageBitmaps.map { GeminiRepository.bitmapToBase64(it) }
        val userMsg = ChatMessage(
            sender = "user",
            text = text,
            imageBase64 = encodedImages.firstOrNull(),
            imageBase64List = encodedImages,
            audioBase64 = audioBase64?.takeIf { it.isNotBlank() }
        )
        val updated = _subjectQuestionChatMessages.value + userMsg
        val pendingReply = ChatMessage(sender = "model", text = "")
        _subjectQuestionChatMessages.value = updated + pendingReply

        _isSubjectQuestionThinking.value = true
        viewModelScope.launch {
            val subject = flashcardDao.getSubjectById(subjectId)
                ?: subjects.value.firstOrNull { it.id == subjectId }
            val existingQuestions = flashcardDao.getCardsBySubjectSnapshot(subjectId)
                .map { card ->
                    GeminiRepository.ExistingSubjectQuestion(
                        question = card.word,
                        status = card.status
                    )
                }
            val systemInstruction = GeminiRepository.buildSubjectAddQuestionSystemInstruction(
                subject = subject,
                existingQuestions = existingQuestions
            )

            val result = geminiRepo.chatTurn(
                history = updated,
                systemInstruction = systemInstruction,
                responseMode = TutorResponseMode.FREESTYLE,
                streamResponses = _appSettings.value.tokenStreaming,
                onUserTranscript = { transcript ->
                    _subjectQuestionChatMessages.value = _subjectQuestionChatMessages.value.map { message ->
                        if (message.id == userMsg.id) {
                            message.copy(text = transcript)
                        } else {
                            message
                        }
                    }
                },
                onPartialResponse = { partial ->
                    _subjectQuestionChatMessages.value = _subjectQuestionChatMessages.value.map { message ->
                        when (message.id) {
                            pendingReply.id -> message.copy(text = partial.answer)
                            userMsg.id -> message.copy(
                                corrections = partial.corrections.ifEmpty { message.corrections }
                            )
                            else -> message
                        }
                    }
                }
            )
            _isSubjectQuestionThinking.value = false
            result.onSuccess { turnResult ->
                _subjectQuestionChatMessages.value = _subjectQuestionChatMessages.value.map { message ->
                    when (message.id) {
                        pendingReply.id -> message.copy(text = turnResult.answer)
                        userMsg.id -> message.copy(
                            text = turnResult.userTranscript ?: message.text,
                            audioBase64 = null,
                            corrections = turnResult.corrections
                        )
                        else -> message
                    }
                }
            }.onFailure { error ->
                _subjectQuestionChatMessages.value = _subjectQuestionChatMessages.value.map { message ->
                    if (message.id == pendingReply.id) {
                        message.copy(
                            text = "Response failed: ${error.localizedMessage ?: "Could not reach AI service."}",
                            isError = true
                        )
                    } else {
                        message
                    }
                }
            }
        }
    }

    fun sendSubjectQuestionAudioMessage(
        subjectId: Long,
        audioBase64: String,
        imageBitmaps: List<Bitmap> = emptyList()
    ) {
        if (audioBase64.isBlank() && imageBitmaps.isEmpty()) return
        sendSubjectQuestionMessage(
            subjectId = subjectId,
            text = "",
            imageBitmaps = imageBitmaps,
            audioBase64 = audioBase64
        )
    }

    fun clearSubjectQuestionChat() {
        subjectDraftGenerationJob?.cancel()
        subjectDraftGenerationJob = null
        _subjectQuestionDraftState.value = SubjectQuestionDraftState.Idle
        _subjectQuestionChatMessages.value = emptyList()
        _isSubjectQuestionThinking.value = false
    }

    fun dismissSubjectQuestionDraft() {
        subjectDraftGenerationJob?.cancel()
        subjectDraftGenerationJob = null
        _subjectQuestionDraftState.value = SubjectQuestionDraftState.Idle
    }

    fun generateDraftQuestionFromConversation(subjectId: Long) {
        val conversationSnapshot = _subjectQuestionChatMessages.value.filterNot { it.isError }
        if (conversationSnapshot.none { it.sender == "user" }) return

        subjectDraftGenerationJob?.cancel()
        _subjectQuestionDraftState.value = SubjectQuestionDraftState.Generating(reasoningChars = 0)

        subjectDraftGenerationJob = viewModelScope.launch {
            try {
                ensureSubjectImagesOcrCompleted(subjectId)
                val subject = flashcardDao.getSubjectById(subjectId)
                    ?: subjects.value.firstOrNull { it.id == subjectId }
                val existingQuestions = flashcardDao.getCardsBySubjectSnapshot(subjectId)
                    .map { card ->
                        GeminiRepository.ExistingSubjectQuestion(
                            question = card.word,
                            status = card.status,
                            answer = card.backText
                        )
                    }

                val result = geminiRepo.generateSingleSubjectQuestionFromConversation(
                    subjectName = subject?.name.orEmpty(),
                    textbook = subject?.effectiveTextbook().orEmpty(),
                    extraKnowledge = subject?.effectiveExtraKnowledge().orEmpty(),
                    existingQuestions = existingQuestions,
                    history = conversationSnapshot,
                    onReasoningProgress = { chars ->
                        if (_subjectQuestionDraftState.value is SubjectQuestionDraftState.Generating) {
                            _subjectQuestionDraftState.value =
                                SubjectQuestionDraftState.Generating(reasoningChars = chars)
                        }
                    }
                )

                result.onSuccess { outcome ->
                    when (outcome) {
                        is GeminiRepository.SingleQuestionGenerationOutcome.Generated -> {
                            _subjectQuestionDraftState.value = SubjectQuestionDraftState.Ready(
                                subjectId = subjectId,
                                question = outcome.parsed.question,
                                answer = outcome.parsed.answer
                            )
                        }
                        is GeminiRepository.SingleQuestionGenerationOutcome.AlreadyExists -> {
                            _subjectQuestionDraftState.value = SubjectQuestionDraftState.AlreadyExists(
                                subjectId = subjectId,
                                matchedQuestion = outcome.matchedQuestion,
                                matchedStatus = outcome.matchedStatus
                            )
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _subjectQuestionDraftState.value = SubjectQuestionDraftState.Error(
                        subjectId = subjectId,
                        message = error.localizedMessage ?: "Failed to generate the question."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _subjectQuestionDraftState.value = SubjectQuestionDraftState.Error(
                    subjectId = subjectId,
                    message = error.localizedMessage ?: "Failed to generate the question."
                )
            }
        }
    }

    fun saveGeneratedSubjectQuestion(subjectId: Long): Boolean {
        val ready = _subjectQuestionDraftState.value as? SubjectQuestionDraftState.Ready
            ?: return false
        if (ready.subjectId != subjectId) return false

        val questionText = ready.question.trim()
        val answerText = ready.answer.trim()
        if (questionText.isEmpty() || answerText.isEmpty()) return false

        val conversationSnapshot = _subjectQuestionChatMessages.value.filterNot { it.isError }
        clearSubjectQuestionChat()

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val card = Flashcard(
                type = FlashcardTypes.EXAM,
                word = questionText,
                backText = answerText,
                subjectId = subjectId,
                stageIndex = 0,
                nextReviewTimeMillis = now + SrsStage.TWO_MINUTES.durationMillis,
                createdAtMillis = now,
                conversationHistoryJson = conversationSnapshot
                    .takeIf { it.isNotEmpty() }
                    ?.let { geminiRepo.serializeChat(it) },
                focusSummary = "Subject Question"
            )
            flashcardDao.insertCard(card)
        }
        return true
    }

    private fun ExamQuestionParser.ParsedQuestion.toExamCard(
        subjectId: Long,
        createdAtMillis: Long = System.currentTimeMillis()
    ): Flashcard {
        val now = System.currentTimeMillis()
        return Flashcard(
            type = FlashcardTypes.EXAM,
            word = question,
            backText = answer,
            subjectId = subjectId,
            stageIndex = 0,
            nextReviewTimeMillis = now + SrsStage.TWO_MINUTES.durationMillis,
            createdAtMillis = createdAtMillis
        )
    }

    fun deleteSubject(subject: Subject) {
        val attachmentsToDelete = subject.textbookImages() + subject.extraKnowledgeImages()
        attachmentsToDelete.forEach { subjectOcrJobs.remove(it.id)?.cancel() }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                attachmentsToDelete.forEach { item ->
                    runCatching {
                        val file = File(item.filePath)
                        if (file.exists()) file.delete()
                    }
                }
            }
            flashcardDao.deleteCardsBySubject(subject.id)
            flashcardDao.deleteSubjectById(subject.id)
            dismissSubjectStatus(subject.id)
            _generatingQuestionProgress.value = _generatingQuestionProgress.value - subject.id
            _activeReviewList.value = _activeReviewList.value.filterNot { it.subjectId == subject.id }
            if (_currentReviewIndex.value >= _activeReviewList.value.size) {
                _currentReviewIndex.value = (_activeReviewList.value.size - 1).coerceAtLeast(0)
            }
            loadActiveCardDetails()
        }
    }

    /** Reviews only due questions of one subject, earliest stage first. Returns true if any are due. */
    fun startSubjectReview(subjectId: Long): Boolean {
        val now = System.currentTimeMillis()
        val dueCards = allCards.value
            .filter { it.subjectId == subjectId && SpacedRepetitionUtils.isCardDue(it, now) }
            .sortedWith(compareBy({ it.stageIndex }, { it.nextReviewTimeMillis }, { it.createdAtMillis }))
        _activeReviewList.value = dueCards
        _currentReviewIndex.value = 0
        loadActiveCardDetails()
        return dueCards.isNotEmpty()
    }

    fun deleteCard(card: Flashcard) {
        viewModelScope.launch {
            flashcardDao.deleteCard(card)
            // If deleting during review, remove from active due list without revisiting completed cards
            val updated = _activeReviewList.value.filter { it.id != card.id }
            _activeReviewList.value = updated
            if (_currentReviewIndex.value >= updated.size) {
                _currentReviewIndex.value = (updated.size - 1).coerceAtLeast(0)
            }
            loadActiveCardDetails()
        }
    }

    fun markCardDumb(card: Flashcard) {
        markCardStatus(card, FlashcardStatus.DUMB)
    }

    fun markCardLearned(card: Flashcard) {
        markCardStatus(card, FlashcardStatus.LEARNED)
    }

    fun restoreCardActive(card: Flashcard) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val stored = flashcardDao.getCardById(card.id) ?: card
            flashcardDao.updateCard(
                stored.copy(
                    status = FlashcardStatus.ACTIVE,
                    nextReviewTimeMillis = now
                )
            )
        }
    }

    private fun markCardStatus(card: Flashcard, status: String) {
        val normalized = FlashcardStatus.normalize(status)
        viewModelScope.launch {
            val stored = flashcardDao.getCardById(card.id) ?: card
            flashcardDao.updateCard(stored.copy(status = normalized))

            if (normalized != FlashcardStatus.ACTIVE) {
                val remaining = _activeReviewList.value.filterNot { it.id == card.id }
                _activeReviewList.value = remaining
                if (_currentReviewIndex.value >= remaining.size) {
                    _currentReviewIndex.value = 0
                }
                loadActiveCardDetails()
            }
        }
    }

    fun scheduleCardAtStage(card: Flashcard, stageIndex: Int) {
        viewModelScope.launch {
            val nextStageIndex = stageIndex.coerceIn(SrsStage.entries.indices)
            val nextTime = SpacedRepetitionUtils.calculateNextReviewTime(nextStageIndex)
            val updatedCard = card.copy(
                status = FlashcardStatus.ACTIVE,
                stageIndex = nextStageIndex,
                nextReviewTimeMillis = nextTime
            )
            flashcardDao.updateCard(updatedCard)

            // Remove the rescheduled card immediately since it is no longer due
            val remaining = _activeReviewList.value.filterNot { it.id == card.id }
            _activeReviewList.value = remaining
            if (_currentReviewIndex.value >= remaining.size) {
                _currentReviewIndex.value = 0
            }
            loadActiveCardDetails()
        }
    }

    // --- Creation Chat Methods ---
    fun sendCreationMessage(
        text: String,
        imageBitmaps: List<Bitmap> = emptyList(),
        audioBase64: String? = null
    ) {
        if (text.isBlank() && imageBitmaps.isEmpty() && audioBase64.isNullOrBlank()) return

        val encodedImages = imageBitmaps.map { GeminiRepository.bitmapToBase64(it) }
        val userMsg = ChatMessage(
            sender = "user",
            text = text,
            imageBase64 = encodedImages.firstOrNull(),
            imageBase64List = encodedImages,
            audioBase64 = audioBase64?.takeIf { it.isNotBlank() }
        )
        val updated = _creationChatMessages.value + userMsg
        val pendingReply = ChatMessage(sender = "model", text = "")
        _creationChatMessages.value = updated + pendingReply

        _isCreationThinking.value = true
        viewModelScope.launch {
            val result = geminiRepo.chatTurn(
                history = updated,
                systemInstruction = "You are a direct, expert tutor helping a learner pinpoint what they want to remember. They may focus on linguistic elements (grammar, vocabulary in context) or technical topics (anatomy, engineering mechanisms, processes), and may share one or more images (such as textbook excerpts, diagrams, slides, or notes). Carefully read any attached images and refine/explain their target concept crisply with zero praise or filler.",
                responseMode = TutorResponseMode.FREESTYLE,
                streamResponses = _appSettings.value.tokenStreaming,
                onUserTranscript = { transcript ->
                    _creationChatMessages.value = _creationChatMessages.value.map { message ->
                        if (message.id == userMsg.id) {
                            message.copy(text = transcript)
                        } else {
                            message
                        }
                    }
                },
                onPartialResponse = { partial ->
                    _creationChatMessages.value = _creationChatMessages.value.map { message ->
                        when (message.id) {
                            pendingReply.id -> message.copy(text = partial.answer)
                            userMsg.id -> message.copy(
                                corrections = partial.corrections.ifEmpty { message.corrections }
                            )
                            else -> message
                        }
                    }
                }
            )
            _isCreationThinking.value = false
            result.onSuccess { turnResult ->
                _creationChatMessages.value = _creationChatMessages.value.map { message ->
                    when (message.id) {
                        pendingReply.id -> message.copy(text = turnResult.answer)
                        userMsg.id -> message.copy(
                            text = turnResult.userTranscript ?: message.text,
                            audioBase64 = null,
                            corrections = turnResult.corrections
                        )
                        else -> message
                    }
                }
            }.onFailure { error ->
                _creationChatMessages.value = _creationChatMessages.value.map { message ->
                    if (message.id == pendingReply.id) {
                        message.copy(
                            text = "Response failed: ${error.localizedMessage ?: "Could not reach AI service."}",
                            isError = true
                        )
                    } else {
                        message
                    }
                }
            }
        }
    }

    fun sendCreationAudioMessage(
        audioBase64: String,
        imageBitmaps: List<Bitmap> = emptyList()
    ) {
        if (audioBase64.isBlank() && imageBitmaps.isEmpty()) return
        sendCreationMessage(text = "", imageBitmaps = imageBitmaps, audioBase64 = audioBase64)
    }

    fun clearCreationChat() {
        _creationChatMessages.value = emptyList()
        _isCreationThinking.value = false
    }

    // --- Review Session Methods ---
    /** Starts a review session with due cards only. Returns true if at least one card is due. */
    fun startReviewSession(stageIndex: Int? = null): Boolean {
        val now = System.currentTimeMillis()
        val dueCards = allCards.value
            .filter { card ->
                SpacedRepetitionUtils.isCardDue(card, now) &&
                    (stageIndex == null || card.stageIndex == stageIndex)
            }
            .sortedBy { it.nextReviewTimeMillis }

        _activeReviewList.value = dueCards
        _currentReviewIndex.value = 0
        loadActiveCardDetails()
        return dueCards.isNotEmpty()
    }

    private fun loadActiveCardDetails() {
        val now = System.currentTimeMillis()
        val dueList = _activeReviewList.value.filter { SpacedRepetitionUtils.isCardDue(it, now) }
        if (dueList.size != _activeReviewList.value.size) {
            _activeReviewList.value = dueList
        }
        if (dueList.isEmpty()) {
            _currentReviewIndex.value = 0
            _currentScenarioOrQuestion.value = null
            _reviewChatHistory.value = emptyList()
            _reviewContextHistory.value = emptyList()
            return
        }
        val index = _currentReviewIndex.value.coerceIn(dueList.indices)
        if (index != _currentReviewIndex.value) {
            _currentReviewIndex.value = index
        }

        val card = dueList[index]
        closeReviewExtractQuestion()
        _reviewChatHistory.value = emptyList()

        if (card.type == FlashcardTypes.EXAM) {
            // The question is already known, so it opens straight away with no model call.
            _reviewScenarioLoading.value = false
            _currentScenarioOrQuestion.value = card.word
            _reviewContextHistory.value = emptyList()
            _reviewChatHistory.value = listOf(ChatMessage(sender = "model", text = card.word))
        } else if (card.type == FlashcardTypes.CONVERSATION_LINGUISTIC || card.type == FlashcardTypes.CONVERSATION_TECHNICAL) {
            _reviewScenarioLoading.value = true
            _currentScenarioOrQuestion.value = null
            val history = geminiRepo.deserializeChat(card.conversationHistoryJson)
            _reviewContextHistory.value = history

            viewModelScope.launch {
                val subject = card.subjectId?.let { id ->
                    subjects.value.firstOrNull { it.id == id } ?: flashcardDao.getSubjectById(id)
                }
                val result = if (card.type == FlashcardTypes.CONVERSATION_LINGUISTIC) {
                    geminiRepo.generateLinguisticScenario(history)
                } else {
                    geminiRepo.generateTechnicalQuestion(
                        history = history,
                        subjectName = subject?.name,
                        textbook = subject?.effectiveTextbook()
                    )
                }

                _reviewScenarioLoading.value = false
                result.onSuccess { promptText ->
                    _currentScenarioOrQuestion.value = promptText
                    _reviewChatHistory.value = listOf(ChatMessage(sender = "model", text = promptText))
                }.onFailure {
                    _currentScenarioOrQuestion.value = null
                    _reviewChatHistory.value = listOf(
                        ChatMessage(sender = "model", text = "", isError = true)
                    )
                }
            }
        } else {
            _reviewScenarioLoading.value = false
            _currentScenarioOrQuestion.value = null
            _reviewContextHistory.value = emptyList()
        }
    }

    fun sendReviewReply(userReply: String) {
        val trimmed = userReply.trim()
        if (trimmed.isBlank()) return

        lastReviewReplyText = trimmed
        lastReviewReplyAudioBase64 = null
        performReviewTurn(userText = trimmed, audioBase64 = null)
    }

    fun sendReviewAudioReply(audioBase64: String) {
        if (audioBase64.isBlank()) return

        lastReviewReplyAudioBase64 = audioBase64
        lastReviewReplyText = null
        performReviewTurn(userText = "", audioBase64 = audioBase64)
    }

    private fun performReviewTurn(userText: String, audioBase64: String?) {
        val currentCard = _activeReviewList.value.getOrNull(_currentReviewIndex.value) ?: return
        if (!SpacedRepetitionUtils.isCardDue(currentCard)) {
            loadActiveCardDetails()
            return
        }
        val userMsg = ChatMessage(
            sender = "user",
            text = userText,
            audioBase64 = audioBase64
        )
        val updated = _reviewChatHistory.value + userMsg
        val pendingReply = ChatMessage(sender = "model", text = "")
        _reviewChatHistory.value = updated + pendingReply

        _isReviewReplying.value = true
        val isInitialReviewAnswer = updated.count { it.sender == "user" } == 1
        val responseMode = if (isInitialReviewAnswer) {
            TutorResponseMode.INITIAL_REVIEW_EVALUATION
        } else {
            TutorResponseMode.FREESTYLE
        }

        viewModelScope.launch {
            val combinedChat = _reviewContextHistory.value + updated
            val subject = currentCard.subjectId?.let { id ->
                subjects.value.firstOrNull { it.id == id } ?: flashcardDao.getSubjectById(id)
            }
            val subjectGuidance = GeminiRepository.buildSubjectReferenceInstructions(
                subject = subject,
                forReviewGrading = true
            )

            val systemPrompt = when (currentCard.type) {
                FlashcardTypes.CONVERSATION_LINGUISTIC -> if (isInitialReviewAnswer) {
                    "You are a direct language tutor evaluating the student's initial response to your scenario. Assess whether they retrieved and applied the target expression or grammatical structure naturally, state directly any part that should be phrased better or was missed, and add a [blue] note ONLY if broader pragmatic nuance or register context outside the literal answer genuinely helps."
                } else {
                    "You are a direct language tutor answering the student's follow-up question or continuing the review discussion clearly and concisely."
                }

                FlashcardTypes.EXAM -> buildString {
                    append("You are a direct exam tutor.\n")
                    append("Question: ${currentCard.word}\n")
                    append("Card reference answer: ")
                    append(currentCard.backText?.takeIf { it.isNotBlank() } ?: "(see Subject Textbook if provided)")
                    append("\n")
                    if (subjectGuidance.isNotBlank()) {
                        append("\n")
                        append(subjectGuidance)
                        append("\n\n")
                    }
                    if (isInitialReviewAnswer) {
                        append("Evaluate the student's initial answer directly against the reference answer / Textbook with zero meta labels, zero memorization formulas, and zero complimentary filler. Omit [blue] unless broader foundational mechanism/context (or a helpful quote/insight from Extra Knowledge) genuinely deepens conceptual understanding.\n")
                    } else {
                        append("Answer the student's follow-up question or clarification directly and concisely (you may draw on or quote Extra Knowledge when relevant).\n")
                    }
                    append("Write your response in the language of the question itself (for example Persian when the question is Persian), even though these instructions are in English.")
                }

                else -> buildString {
                    if (isInitialReviewAnswer) {
                        append("You are a direct technical/subject mentor evaluating the student's initial answer. State directly what mechanism or concept they got right, state what needs refinement or was missed, and omit [blue] unless broader foundational context (or a relevant quote/insight from Extra Knowledge) genuinely deepens understanding.")
                    } else {
                        append("You are a direct technical/subject mentor answering the student's follow-up question clearly and concisely.")
                    }
                    if (subjectGuidance.isNotBlank()) {
                        append("\n\n")
                        append(subjectGuidance)
                    }
                }
            }

            var capturedTranscript: String? = null
            val result = geminiRepo.chatTurn(
                history = combinedChat,
                systemInstruction = systemPrompt,
                responseMode = responseMode,
                streamResponses = _appSettings.value.tokenStreaming,
                onUserTranscript = { transcript ->
                    capturedTranscript = transcript
                    lastReviewReplyText = transcript
                    _reviewChatHistory.value = _reviewChatHistory.value.map { message ->
                        if (message.id == userMsg.id) {
                            message.copy(text = transcript, audioBase64 = null)
                        } else {
                            message
                        }
                    }
                },
                onPartialResponse = { partial ->
                    _reviewChatHistory.value = _reviewChatHistory.value.map { message ->
                        when (message.id) {
                            pendingReply.id -> message.copy(text = partial.answer)
                            userMsg.id -> message.copy(
                                corrections = partial.corrections.ifEmpty { message.corrections }
                            )
                            else -> message
                        }
                    }
                }
            )
            _isReviewReplying.value = false
            result.onSuccess { turnResult ->
                lastReviewReplyText = null
                lastReviewReplyAudioBase64 = null
                val correctedTranscript = userMsg.copy(
                    text = turnResult.userTranscript ?: capturedTranscript ?: userMsg.text,
                    audioBase64 = null,
                    corrections = turnResult.corrections
                )
                val replyMessage = pendingReply.copy(text = turnResult.answer)
                _reviewChatHistory.value = _reviewChatHistory.value.map { message ->
                    when (message.id) {
                        userMsg.id -> correctedTranscript
                        pendingReply.id -> replyMessage
                        else -> message
                    }
                }
                val persistedTurn = updated.map { message ->
                    if (message.id == userMsg.id) correctedTranscript else message
                } + replyMessage
                val persistedHistory = _reviewContextHistory.value + persistedTurn
                _reviewContextHistory.value = persistedHistory
                val historyJson = geminiRepo.serializeChat(persistedHistory)
                val storedCard = flashcardDao.getCardById(currentCard.id)
                if (storedCard != null) {
                    val updatedCard = storedCard.copy(conversationHistoryJson = historyJson)
                    flashcardDao.updateCard(updatedCard)
                    _activeReviewList.value = _activeReviewList.value.map { card ->
                        if (card.id == updatedCard.id) updatedCard else card
                    }
                }
            }.onFailure { error ->
                _reviewChatHistory.value = _reviewChatHistory.value.map { message ->
                    when (message.id) {
                        pendingReply.id -> message.copy(
                            text = "Response failed: ${error.localizedMessage ?: "Could not reach AI service."}",
                            isError = true
                        )
                        userMsg.id -> if (!capturedTranscript.isNullOrBlank()) {
                            message.copy(text = capturedTranscript!!, audioBase64 = null)
                        } else {
                            message
                        }
                        else -> message
                    }
                }
            }
        }
    }

    fun retryLastReviewReply() {
        val retryText = lastReviewReplyText
        val retryAudio = lastReviewReplyAudioBase64
        when {
            !retryText.isNullOrBlank() -> {
                val cleaned = _reviewChatHistory.value.filterNot { it.isError }
                _reviewChatHistory.value = if (
                    cleaned.lastOrNull()?.sender == "user" &&
                    cleaned.lastOrNull()?.text == retryText
                ) {
                    cleaned.dropLast(1)
                } else {
                    cleaned
                }
                sendReviewReply(retryText)
            }
            !retryAudio.isNullOrBlank() -> {
                val cleaned = _reviewChatHistory.value.filterNot { it.isError }
                _reviewChatHistory.value = if (
                    cleaned.lastOrNull()?.sender == "user" &&
                    cleaned.lastOrNull()?.audioBase64 != null
                ) {
                    cleaned.dropLast(1)
                } else {
                    cleaned
                }
                sendReviewAudioReply(retryAudio)
            }
            _currentScenarioOrQuestion.value == null -> {
                loadActiveCardDetails()
            }
        }
    }

    // --- Review "Extract Question" Methods ---
    fun openReviewExtractQuestion() {
        val currentCard = _activeReviewList.value.getOrNull(_currentReviewIndex.value) ?: return
        reviewExtractDraftJob?.cancel()
        reviewExtractDraftJob = null
        _reviewExtractDraftState.value = SubjectQuestionDraftState.Idle
        _isReviewExtractThinking.value = false

        val subject = currentCard.subjectId?.let { id ->
            subjects.value.firstOrNull { it.id == id }
        }
        val combinedReviewConversation = (_reviewContextHistory.value + _reviewChatHistory.value)
            .filterNot { it.isError }
        val opener = GeminiRepository.buildExtractQuestionOpeningMessage(
            sourceCard = currentCard,
            reviewConversation = combinedReviewConversation,
            subject = subject
        )
        _reviewExtractChatMessages.value = listOf(
            ChatMessage(sender = "model", text = opener)
        )
        _isReviewExtractOpen.value = true
    }

    fun closeReviewExtractQuestion() {
        reviewExtractDraftJob?.cancel()
        reviewExtractDraftJob = null
        _reviewExtractDraftState.value = SubjectQuestionDraftState.Idle
        _reviewExtractChatMessages.value = emptyList()
        _isReviewExtractThinking.value = false
        _isReviewExtractOpen.value = false
    }

    fun dismissReviewExtractDraft() {
        reviewExtractDraftJob?.cancel()
        reviewExtractDraftJob = null
        _reviewExtractDraftState.value = SubjectQuestionDraftState.Idle
    }

    fun sendReviewExtractMessage(
        text: String,
        imageBitmaps: List<Bitmap> = emptyList(),
        audioBase64: String? = null
    ) {
        if (text.isBlank() && imageBitmaps.isEmpty() && audioBase64.isNullOrBlank()) return
        val currentCard = _activeReviewList.value.getOrNull(_currentReviewIndex.value) ?: return

        val encodedImages = imageBitmaps.map { GeminiRepository.bitmapToBase64(it) }
        val userMsg = ChatMessage(
            sender = "user",
            text = text,
            imageBase64 = encodedImages.firstOrNull(),
            imageBase64List = encodedImages,
            audioBase64 = audioBase64?.takeIf { it.isNotBlank() }
        )
        val updated = _reviewExtractChatMessages.value + userMsg
        val pendingReply = ChatMessage(sender = "model", text = "")
        _reviewExtractChatMessages.value = updated + pendingReply

        _isReviewExtractThinking.value = true
        val reviewConversationSnapshot = (_reviewContextHistory.value + _reviewChatHistory.value)
            .filterNot { it.isError }

        viewModelScope.launch {
            val subjectId = currentCard.subjectId
            val subject = subjectId?.let { id ->
                flashcardDao.getSubjectById(id) ?: subjects.value.firstOrNull { it.id == id }
            }
            val existingQuestions = loadExistingQuestionsForCardScope(currentCard.subjectId)

            val systemInstruction = GeminiRepository.buildExtractQuestionFromReviewSystemInstruction(
                sourceCard = currentCard,
                reviewConversation = reviewConversationSnapshot,
                subject = subject,
                existingQuestions = existingQuestions
            )

            val result = geminiRepo.chatTurn(
                history = updated,
                systemInstruction = systemInstruction,
                responseMode = TutorResponseMode.FREESTYLE,
                streamResponses = _appSettings.value.tokenStreaming,
                onUserTranscript = { transcript ->
                    _reviewExtractChatMessages.value = _reviewExtractChatMessages.value.map { message ->
                        if (message.id == userMsg.id) {
                            message.copy(text = transcript)
                        } else {
                            message
                        }
                    }
                },
                onPartialResponse = { partial ->
                    _reviewExtractChatMessages.value = _reviewExtractChatMessages.value.map { message ->
                        when (message.id) {
                            pendingReply.id -> message.copy(text = partial.answer)
                            userMsg.id -> message.copy(
                                corrections = partial.corrections.ifEmpty { message.corrections }
                            )
                            else -> message
                        }
                    }
                }
            )
            _isReviewExtractThinking.value = false
            result.onSuccess { turnResult ->
                _reviewExtractChatMessages.value = _reviewExtractChatMessages.value.map { message ->
                    when (message.id) {
                        pendingReply.id -> message.copy(text = turnResult.answer)
                        userMsg.id -> message.copy(
                            text = turnResult.userTranscript ?: message.text,
                            audioBase64 = null,
                            corrections = turnResult.corrections
                        )
                        else -> message
                    }
                }
            }.onFailure { error ->
                _reviewExtractChatMessages.value = _reviewExtractChatMessages.value.map { message ->
                    if (message.id == pendingReply.id) {
                        message.copy(
                            text = "Response failed: ${error.localizedMessage ?: "Could not reach AI service."}",
                            isError = true
                        )
                    } else {
                        message
                    }
                }
            }
        }
    }

    fun sendReviewExtractAudioMessage(
        audioBase64: String,
        imageBitmaps: List<Bitmap> = emptyList()
    ) {
        if (audioBase64.isBlank() && imageBitmaps.isEmpty()) return
        sendReviewExtractMessage(
            text = "",
            imageBitmaps = imageBitmaps,
            audioBase64 = audioBase64
        )
    }

    fun generateDraftQuestionFromReviewExtraction() {
        val currentCard = _activeReviewList.value.getOrNull(_currentReviewIndex.value) ?: return
        val extractionSnapshot = _reviewExtractChatMessages.value.filterNot { it.isError }
        if (extractionSnapshot.none { it.sender == "user" }) return

        val reviewConversationSnapshot = (_reviewContextHistory.value + _reviewChatHistory.value)
            .filterNot { it.isError }
        val targetSubjectId = currentCard.subjectId ?: 0L

        reviewExtractDraftJob?.cancel()
        _reviewExtractDraftState.value = SubjectQuestionDraftState.Generating(reasoningChars = 0)

        reviewExtractDraftJob = viewModelScope.launch {
            try {
                if (currentCard.subjectId != null) {
                    ensureSubjectImagesOcrCompleted(currentCard.subjectId)
                }
                val subject = currentCard.subjectId?.let { id ->
                    flashcardDao.getSubjectById(id) ?: subjects.value.firstOrNull { it.id == id }
                }
                val existingQuestions = loadExistingQuestionsForCardScope(currentCard.subjectId)

                val result = geminiRepo.generateExtractedQuestionFromReview(
                    sourceCard = currentCard,
                    reviewConversation = reviewConversationSnapshot,
                    subject = subject,
                    existingQuestions = existingQuestions,
                    extractionConversation = extractionSnapshot,
                    onReasoningProgress = { chars ->
                        if (_reviewExtractDraftState.value is SubjectQuestionDraftState.Generating) {
                            _reviewExtractDraftState.value =
                                SubjectQuestionDraftState.Generating(reasoningChars = chars)
                        }
                    }
                )

                result.onSuccess { outcome ->
                    when (outcome) {
                        is GeminiRepository.SingleQuestionGenerationOutcome.Generated -> {
                            _reviewExtractDraftState.value = SubjectQuestionDraftState.Ready(
                                subjectId = targetSubjectId,
                                question = outcome.parsed.question,
                                answer = outcome.parsed.answer
                            )
                        }
                        is GeminiRepository.SingleQuestionGenerationOutcome.AlreadyExists -> {
                            _reviewExtractDraftState.value = SubjectQuestionDraftState.AlreadyExists(
                                subjectId = targetSubjectId,
                                matchedQuestion = outcome.matchedQuestion,
                                matchedStatus = outcome.matchedStatus
                            )
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _reviewExtractDraftState.value = SubjectQuestionDraftState.Error(
                        subjectId = targetSubjectId,
                        message = error.localizedMessage ?: "Failed to generate the question."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _reviewExtractDraftState.value = SubjectQuestionDraftState.Error(
                    subjectId = targetSubjectId,
                    message = error.localizedMessage ?: "Failed to generate the question."
                )
            }
        }
    }

    private suspend fun loadExistingQuestionsForCardScope(
        subjectId: Long?
    ): List<GeminiRepository.ExistingSubjectQuestion> {
        val cardsInScope = if (subjectId != null) {
            flashcardDao.getCardsBySubjectSnapshot(subjectId)
        } else {
            flashcardDao.getAllCardsSnapshot().filter { it.subjectId == null }
        }
        return cardsInScope.map { card ->
            GeminiRepository.ExistingSubjectQuestion(
                question = card.word,
                status = card.status,
                answer = card.backText
            )
        }
    }

    fun saveExtractedReviewQuestion(): Boolean {
        val ready = _reviewExtractDraftState.value as? SubjectQuestionDraftState.Ready
            ?: return false
        val currentCard = _activeReviewList.value.getOrNull(_currentReviewIndex.value)
        val questionText = ready.question.trim()
        val answerText = ready.answer.trim()
        if (questionText.isEmpty() || answerText.isEmpty()) return false

        val extractionSnapshot = _reviewExtractChatMessages.value.filterNot { it.isError }
        val resolvedSubjectId = currentCard?.subjectId ?: ready.subjectId.takeIf { it > 0L }
        closeReviewExtractQuestion()

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val card = Flashcard(
                type = FlashcardTypes.EXAM,
                word = questionText,
                backText = answerText,
                subjectId = resolvedSubjectId,
                stageIndex = 0,
                nextReviewTimeMillis = now + SrsStage.TWO_MINUTES.durationMillis,
                createdAtMillis = now,
                conversationHistoryJson = extractionSnapshot
                    .takeIf { it.isNotEmpty() }
                    ?.let { geminiRepo.serializeChat(it) },
                focusSummary = if (resolvedSubjectId != null) "Extracted Question" else "Extracted Review Question"
            )
            flashcardDao.insertCard(card)
        }
        return true
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }
}

data class SubjectStatusNotice(
    val text: String,
    val isError: Boolean,
    val isWarning: Boolean = false
)

data class SubjectGenerationProgress(
    val isThinking: Boolean = true,
    val reasoningChars: Int = 0,
    val addedCount: Int = 0
)

sealed interface SubjectQuestionDraftState {
    data object Idle : SubjectQuestionDraftState

    data class Generating(
        val reasoningChars: Int = 0
    ) : SubjectQuestionDraftState

    data class Ready(
        val subjectId: Long,
        val question: String,
        val answer: String
    ) : SubjectQuestionDraftState

    data class AlreadyExists(
        val subjectId: Long,
        val matchedQuestion: String? = null,
        val matchedStatus: String? = null
    ) : SubjectQuestionDraftState

    data class Error(
        val subjectId: Long,
        val message: String
    ) : SubjectQuestionDraftState
}

