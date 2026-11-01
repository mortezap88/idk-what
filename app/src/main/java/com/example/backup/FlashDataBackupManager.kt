package com.example.backup

import android.content.Context
import android.net.Uri
import com.example.data.Flashcard
import com.example.data.FlashcardDao
import com.example.data.FlashcardStatus
import com.example.data.FlashcardTypes
import com.example.data.Subject
import com.example.data.SubjectImageAttachment
import com.example.data.SubjectImagesCodec
import com.example.network.CorrectionJsonCodec
import com.example.notepad.data.model.MediaItem
import com.example.notepad.data.model.MediaType
import com.example.notepad.data.model.NoteLine
import com.example.notepad.data.model.NotepadBlock
import com.example.notepad.data.model.NotepadTab
import com.example.notepad.data.repository.NotesRepository
import com.example.settings.ApiKeyProvider
import com.example.settings.AppSettingsRepository
import com.example.settings.AppThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class FlashDataSummary(
    val apiKeyCount: Int,
    val cardCount: Int,
    val subjectCount: Int,
    val notepadTabCount: Int,
    val mediaFileCount: Int
)

/**
 * Exports and imports the entire FlashRecall app memory as a portable `.flashdata` archive:
 * - AvalAI API key pool, active key index, theme, chat & TTS settings
 * - All review items (flashcards across all stages, conversation histories, SRS schedules, images)
 * - Subject centre papers and exam questions
 * - Notepad tabs, blocks, text lines, voice memos, and image notes
 */
class FlashDataBackupManager(
    context: Context,
    private val flashcardDao: FlashcardDao,
    private val settingsRepository: AppSettingsRepository,
    private val notesRepository: NotesRepository
) {
    private val appContext = context.applicationContext

    suspend fun exportToUri(uri: Uri): Result<FlashDataSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val out = appContext.contentResolver.openOutputStream(uri, "wt")
                ?: appContext.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open destination file for writing.")
            out.use { stream ->
                exportToStream(stream)
            }
        }
    }

    suspend fun importFromUri(uri: Uri): Result<FlashDataSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open selected .flashdata file.")
            input.use { stream ->
                importFromStream(stream)
            }
        }
    }

    suspend fun exportToStream(outputStream: OutputStream): FlashDataSummary =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.loadSettings()
            val storedOnexaiKeys = settingsRepository.exportStoredApiKeys(ApiKeyProvider.ONEXAI)
            val storedHoushyarKeys = settingsRepository.exportStoredApiKeys(ApiKeyProvider.HOUSHYAR24)
            val storedHormouzKeys = settingsRepository.exportStoredApiKeys(ApiKeyProvider.HORMOUZ)
            val subjects = flashcardDao.getAllSubjectsSnapshot()
            val cards = flashcardDao.getAllCardsSnapshot()
            val notepadTabs = notesRepository.getSnapshotForBackup()

            val mediaQueue = LinkedHashMap<String, File>()
            val sourcePathToArchiveEntry = HashMap<String, String>()

            fun stageLocalMedia(subfolder: String, rawRef: String?): String? {
                val localFile = resolveLocalFile(rawRef) ?: return null
                if (!localFile.exists() || !localFile.isFile) return null
                val canonical = runCatching { localFile.canonicalPath }.getOrNull() ?: return null
                sourcePathToArchiveEntry[canonical]?.let { return it }

                val safeName = localFile.name
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "media_${mediaQueue.size}.bin" }
                val entryPath = "$MEDIA_DIR/$subfolder/${mediaQueue.size}_$safeName"
                mediaQueue[entryPath] = localFile
                sourcePathToArchiveEntry[canonical] = entryPath
                return entryPath
            }

            val settingsJson = JSONObject().apply {
                put("theme", settings.theme.name)
                put("deepseekModel", settings.deepseekModel)
                put("transcribeModel", settings.transcribeModel)
                put("tttProvider", settings.tttProvider.name)
                put("sttProvider", settings.sttProvider.name)
                put("tokenStreaming", settings.tokenStreaming)
                put("maxFontSizeSp", settings.maxFontSizeSp)
                put("ttsVoiceName", settings.ttsVoiceName ?: JSONObject.NULL)
                put("ttsRate", settings.ttsRate.toDouble())
                put("activeOnexaiKeyIndex", settings.activeOnexaiKeyIndex)
                put("onexaiApiKeys", JSONArray(storedOnexaiKeys))
                put("activeHoushyarKeyIndex", settings.activeHoushyarKeyIndex)
                put("houshyarApiKeys", JSONArray(storedHoushyarKeys))
                put("activeHormouzKeyIndex", settings.activeHormouzKeyIndex)
                put("hormouzApiKeys", JSONArray(storedHormouzKeys))
            }

            fun encodeSubjectImagesForArchive(images: List<SubjectImageAttachment>): JSONArray {
                val arr = JSONArray()
                for (img in images) {
                    val archiveEntry = stageLocalMedia(SUBFOLDER_SUBJECT_IMAGES, img.filePath)
                    arr.put(
                        JSONObject().apply {
                            put("id", img.id)
                            put("filePath", img.filePath)
                            put("archiveMediaEntry", archiveEntry ?: JSONObject.NULL)
                            put("ocrText", img.ocrText)
                            put("isProcessingOcr", false)
                            put("ocrFailed", img.ocrFailed)
                            put("timestamp", img.timestamp)
                        }
                    )
                }
                return arr
            }

            val subjectsJson = JSONArray()
            for (subject in subjects) {
                subjectsJson.put(
                    JSONObject().apply {
                        put("id", subject.id)
                        put("name", subject.name)
                        put("textbook", subject.textbook)
                        put("extraKnowledge", subject.extraKnowledge)
                        put("textbookImages", encodeSubjectImagesForArchive(subject.textbookImages()))
                        put("extraKnowledgeImages", encodeSubjectImagesForArchive(subject.extraKnowledgeImages()))
                        put("createdAtMillis", subject.createdAtMillis)
                    }
                )
            }

            val flashcardsJson = JSONArray()
            for (card in cards) {
                val archiveImageEntry = stageLocalMedia(SUBFOLDER_FLASHCARD_IMAGES, card.imageUrl)
                flashcardsJson.put(
                    JSONObject().apply {
                        put("id", card.id)
                        put("type", card.type)
                        put("word", card.word)
                        put("backText", card.backText ?: JSONObject.NULL)
                        put("imageUrl", card.imageUrl ?: JSONObject.NULL)
                        put("archiveImageEntry", archiveImageEntry ?: JSONObject.NULL)
                        put("subjectId", card.subjectId ?: JSONObject.NULL)
                        put("stageIndex", card.stageIndex)
                        put("nextReviewTimeMillis", card.nextReviewTimeMillis)
                        put("createdAtMillis", card.createdAtMillis)
                        put("conversationHistoryJson", card.conversationHistoryJson ?: JSONObject.NULL)
                        put("focusSummary", card.focusSummary ?: JSONObject.NULL)
                        put("lastScenarioOrQuestion", card.lastScenarioOrQuestion ?: JSONObject.NULL)
                        put("status", card.status)
                    }
                )
            }

            val tabsJson = JSONArray()
            for (tab in notepadTabs) {
                val blocksJson = JSONArray()
                for (block in tab.blocks) {
                    val linesJson = JSONArray()
                    for (line in block.lines) {
                        linesJson.put(
                            JSONObject().apply {
                                put("id", line.id)
                                put("text", line.text)
                            }
                        )
                    }
                    val mediaJson = JSONArray()
                    for (item in block.mediaItems) {
                        val subfolder = if (item.type == MediaType.IMAGE) {
                            SUBFOLDER_IMAGE_NOTES
                        } else {
                            SUBFOLDER_AUDIO_NOTES
                        }
                        val archiveMediaEntry = stageLocalMedia(subfolder, item.filePath)
                        mediaJson.put(
                            JSONObject().apply {
                                put("id", item.id)
                                put("type", item.type.name)
                                put("filePath", item.filePath)
                                put("archiveMediaEntry", archiveMediaEntry ?: JSONObject.NULL)
                                put("transcription", item.transcription ?: JSONObject.NULL)
                                put("englishTranslation", item.englishTranslation ?: JSONObject.NULL)
                                put("corrections", CorrectionJsonCodec.toJsonArray(item.corrections))
                                put("isTranscribed", item.isTranscribed)
                                put("isProcessing", false)
                                put("durationSec", item.durationSec)
                                put("timestamp", item.timestamp)
                            }
                        )
                    }
                    blocksJson.put(
                        JSONObject().apply {
                            put("id", block.id)
                            put("title", block.title)
                            put("maxCapacity", block.maxCapacity)
                            put("lines", linesJson)
                            put("mediaItems", mediaJson)
                        }
                    )
                }
                tabsJson.put(
                    JSONObject().apply {
                        put("id", tab.id)
                        put("title", tab.title)
                        put("blocks", blocksJson)
                    }
                )
            }

            val manifestJson = JSONObject().apply {
                put("format", FORMAT_ID)
                put("schemaVersion", SCHEMA_VERSION)
                put("exportedAtMillis", System.currentTimeMillis())
                put("settings", settingsJson)
                put("subjects", subjectsJson)
                put("flashcards", flashcardsJson)
                put("notepadTabs", tabsJson)
            }

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                val manifestBytes = manifestJson.toString(2).toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                zip.write(manifestBytes)
                zip.closeEntry()

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                for ((entryPath, sourceFile) in mediaQueue) {
                    if (!sourceFile.exists()) continue
                    zip.putNextEntry(ZipEntry(entryPath))
                    FileInputStream(sourceFile).use { fileIn ->
                        var read = fileIn.read(buffer)
                        while (read >= 0) {
                            if (read > 0) zip.write(buffer, 0, read)
                            read = fileIn.read(buffer)
                        }
                    }
                    zip.closeEntry()
                }
                zip.finish()
            }

            FlashDataSummary(
                apiKeyCount = (storedOnexaiKeys + storedHoushyarKeys + storedHormouzKeys).distinct().size,
                cardCount = cards.size,
                subjectCount = subjects.size,
                notepadTabCount = notepadTabs.size,
                mediaFileCount = mediaQueue.size
            )
        }

    suspend fun importFromStream(inputStream: InputStream): FlashDataSummary =
        withContext(Dispatchers.IO) {
            val stagingDir = File(appContext.cacheDir, "flashdata_import_${UUID.randomUUID()}")
            stagingDir.mkdirs()

            try {
                val stagingCanonical = stagingDir.canonicalPath
                var entryCount = 0
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        entryCount++
                        require(entryCount <= MAX_ARCHIVE_ENTRIES) {
                            "Invalid .flashdata archive: too many entries."
                        }
                        if (!entry.isDirectory) {
                            val targetFile = File(stagingDir, entry.name)
                            val targetCanonical = targetFile.canonicalPath
                            require(targetCanonical.startsWith(stagingCanonical + File.separator)) {
                                "Invalid .flashdata archive: unsafe file path."
                            }
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { fileOut ->
                                var read = zip.read(buffer)
                                while (read >= 0) {
                                    if (read > 0) fileOut.write(buffer, 0, read)
                                    read = zip.read(buffer)
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                val manifestFile = File(stagingDir, MANIFEST_ENTRY_NAME)
                require(manifestFile.exists() && manifestFile.isFile) {
                    "Invalid .flashdata file. Choose a backup exported from FlashRecall."
                }

                val manifestJson = runCatching {
                    JSONObject(manifestFile.readText(Charsets.UTF_8))
                }.getOrElse {
                    throw IllegalArgumentException(
                        "Corrupted .flashdata file: backup manifest could not be parsed."
                    )
                }

                require(manifestJson.optString("format") == FORMAT_ID) {
                    "Invalid .flashdata file: unrecognized backup format."
                }

                // Parse everything in memory before mutating live database or files.
                val settingsObj = manifestJson.optJSONObject("settings") ?: JSONObject()
                val importedTheme = AppThemePreference.fromStoredValue(
                    settingsObj.optNullableString("theme")
                )
                val importedTokenStreaming = settingsObj.optBoolean("tokenStreaming", true)
                val importedMaxFontSize = settingsObj.optInt(
                    "maxFontSizeSp",
                    AppSettingsRepository.DEFAULT_MAX_FONT_SIZE
                )
                val importedTtsVoice = settingsObj.optNullableString("ttsVoiceName")
                val importedTtsRate = settingsObj.optDouble(
                    "ttsRate",
                    AppSettingsRepository.DEFAULT_TTS_RATE.toDouble()
                ).toFloat()
                val importedActiveKeyIndex = when {
                    settingsObj.has("activeOnexaiKeyIndex") -> settingsObj.optInt("activeOnexaiKeyIndex", 0)
                    settingsObj.has("activeAvalaiKeyIndex") -> settingsObj.optInt("activeAvalaiKeyIndex", 0)
                    else -> settingsObj.optInt("activeDeepseekKeyIndex", 0)
                }
                val importedTttProvider = runCatching {
                    ApiKeyProvider.valueOf(settingsObj.optString("tttProvider", ApiKeyProvider.HOUSHYAR24.name))
                }.getOrDefault(ApiKeyProvider.HOUSHYAR24)
                val importedTttModelId = settingsObj.optNullableString("deepseekModel")
                val importedSttModelId = settingsObj.optNullableString("transcribeModel")
                val importedActiveHoushyarKeyIndex = settingsObj.optInt("activeHoushyarKeyIndex", 0)
                val importedHoushyarKeys = mutableListOf<String>()
                settingsObj.optJSONArray("houshyarApiKeys")?.let { keysArray ->
                    for (i in 0 until keysArray.length()) {
                        keysArray.optNullableString(i)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let(importedHoushyarKeys::add)
                    }
                }
                val importedActiveHormouzKeyIndex = settingsObj.optInt("activeHormouzKeyIndex", 0)
                val importedHormouzKeys = mutableListOf<String>()
                settingsObj.optJSONArray("hormouzApiKeys")?.let { keysArray ->
                    for (i in 0 until keysArray.length()) {
                        keysArray.optNullableString(i)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let(importedHormouzKeys::add)
                    }
                }
                val importedKeys = mutableListOf<String>()
                val rawKeysArray = settingsObj.optJSONArray("onexaiApiKeys")
                    ?: settingsObj.optJSONArray("avalaiApiKeys")
                    ?: settingsObj.optJSONArray("deepseekApiKeys")
                rawKeysArray?.let { keysArray ->
                    for (i in 0 until keysArray.length()) {
                        keysArray.optNullableString(i)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let(importedKeys::add)
                    }
                }

                data class StagedSubjectImage(
                    val attachment: SubjectImageAttachment,
                    val archiveMediaEntry: String?
                )

                data class StagedSubject(
                    val subject: Subject,
                    val textbookImages: List<StagedSubjectImage>,
                    val extraKnowledgeImages: List<StagedSubjectImage>
                )

                fun parseStagedSubjectImages(arr: JSONArray?): List<StagedSubjectImage> {
                    if (arr == null) return emptyList()
                    return buildList {
                        for (idx in 0 until arr.length()) {
                            val imgObj = arr.optJSONObject(idx) ?: continue
                            add(
                                StagedSubjectImage(
                                    attachment = SubjectImageAttachment(
                                        id = imgObj.optString("id", UUID.randomUUID().toString()),
                                        filePath = imgObj.optString("filePath", ""),
                                        ocrText = imgObj.optString("ocrText", ""),
                                        isProcessingOcr = false,
                                        ocrFailed = imgObj.optBoolean("ocrFailed", false),
                                        timestamp = imgObj.optLong("timestamp", System.currentTimeMillis())
                                    ),
                                    archiveMediaEntry = imgObj.optNullableString("archiveMediaEntry")
                                )
                            )
                        }
                    }
                }

                val stagedSubjects = mutableListOf<StagedSubject>()
                manifestJson.optJSONArray("subjects")?.let { subjectsArray ->
                    for (i in 0 until subjectsArray.length()) {
                        val obj = subjectsArray.getJSONObject(i)
                        val name = obj.optString("name", "").trim()
                        if (name.isNotEmpty()) {
                            stagedSubjects.add(
                                StagedSubject(
                                    subject = Subject(
                                        id = obj.optLong("id", 0L),
                                        name = name,
                                        textbook = obj.optString("textbook", ""),
                                        extraKnowledge = obj.optString("extraKnowledge", ""),
                                        createdAtMillis = obj.optLong(
                                            "createdAtMillis",
                                            System.currentTimeMillis()
                                        )
                                    ),
                                    textbookImages = parseStagedSubjectImages(obj.optJSONArray("textbookImages")),
                                    extraKnowledgeImages = parseStagedSubjectImages(obj.optJSONArray("extraKnowledgeImages"))
                                )
                            )
                        }
                    }
                }

                data class StagedFlashcard(
                    val card: Flashcard,
                    val archiveImageEntry: String?
                )

                val stagedCards = mutableListOf<StagedFlashcard>()
                manifestJson.optJSONArray("flashcards")?.let { cardsArray ->
                    for (i in 0 until cardsArray.length()) {
                        val obj = cardsArray.getJSONObject(i)
                        val word = obj.optString("word", "")
                        if (word.isBlank()) continue
                        val subjectId = if (obj.isNull("subjectId") || !obj.has("subjectId")) {
                            null
                        } else {
                            obj.optLong("subjectId")
                        }
                        stagedCards.add(
                            StagedFlashcard(
                                card = Flashcard(
                                    id = obj.optLong("id", 0L),
                                    type = obj.optString("type", FlashcardTypes.TEXT),
                                    word = word,
                                    backText = obj.optNullableString("backText"),
                                    imageUrl = obj.optNullableString("imageUrl"),
                                    subjectId = subjectId,
                                    stageIndex = obj.optInt("stageIndex", 0).coerceIn(0, 7),
                                    nextReviewTimeMillis = obj.optLong(
                                        "nextReviewTimeMillis",
                                        System.currentTimeMillis()
                                    ),
                                    createdAtMillis = obj.optLong(
                                        "createdAtMillis",
                                        System.currentTimeMillis()
                                    ),
                                    conversationHistoryJson = obj.optNullableString("conversationHistoryJson"),
                                    focusSummary = obj.optNullableString("focusSummary"),
                                    lastScenarioOrQuestion = obj.optNullableString("lastScenarioOrQuestion"),
                                    status = FlashcardStatus.normalize(obj.optNullableString("status"))
                                ),
                                archiveImageEntry = obj.optNullableString("archiveImageEntry")
                            )
                        )
                    }
                }

                data class StagedMediaItem(
                    val item: MediaItem,
                    val archiveMediaEntry: String?
                )

                data class StagedBlock(
                    val id: String,
                    val title: String,
                    val maxCapacity: Int,
                    val lines: List<NoteLine>,
                    val mediaItems: List<StagedMediaItem>
                )

                data class StagedTab(
                    val id: String,
                    val title: String,
                    val blocks: List<StagedBlock>
                )

                val hasNotepadSection = manifestJson.has("notepadTabs")
                val stagedTabs = mutableListOf<StagedTab>()
                manifestJson.optJSONArray("notepadTabs")?.let { tabsArray ->
                    for (i in 0 until tabsArray.length()) {
                        val tabObj = tabsArray.getJSONObject(i)
                        val blocksArray = tabObj.optJSONArray("blocks") ?: JSONArray()
                        val blocks = mutableListOf<StagedBlock>()
                        for (j in 0 until blocksArray.length()) {
                            val blockObj = blocksArray.getJSONObject(j)
                            val linesArray = blockObj.optJSONArray("lines") ?: JSONArray()
                            val lines = mutableListOf<NoteLine>()
                            for (k in 0 until linesArray.length()) {
                                val lineObj = linesArray.getJSONObject(k)
                                lines.add(
                                    NoteLine(
                                        id = lineObj.optString("id", UUID.randomUUID().toString()),
                                        text = lineObj.optString("text", "")
                                    )
                                )
                            }
                            val mediaArray = blockObj.optJSONArray("mediaItems") ?: JSONArray()
                            val mediaItems = mutableListOf<StagedMediaItem>()
                            for (k in 0 until mediaArray.length()) {
                                val mObj = mediaArray.getJSONObject(k)
                                val type = if (mObj.optString("type") == MediaType.IMAGE.name) {
                                    MediaType.IMAGE
                                } else {
                                    MediaType.AUDIO
                                }
                                mediaItems.add(
                                    StagedMediaItem(
                                        item = MediaItem(
                                            id = mObj.optString("id", UUID.randomUUID().toString()),
                                            type = type,
                                            filePath = mObj.optString("filePath", ""),
                                            transcription = mObj.optNullableString("transcription")
                                                ?.ifEmpty { null },
                                            englishTranslation = mObj.optNullableString("englishTranslation")
                                                ?.ifEmpty { null },
                                            corrections = CorrectionJsonCodec.fromStoredJsonArray(
                                                mObj.optJSONArray("corrections")
                                            ),
                                            isTranscribed = mObj.optBoolean("isTranscribed", false),
                                            isProcessing = false,
                                            durationSec = mObj.optInt("durationSec", 0),
                                            timestamp = mObj.optLong(
                                                "timestamp",
                                                System.currentTimeMillis()
                                            )
                                        ),
                                        archiveMediaEntry = mObj.optNullableString("archiveMediaEntry")
                                    )
                                )
                            }
                            blocks.add(
                                StagedBlock(
                                    id = blockObj.optString("id", UUID.randomUUID().toString()),
                                    title = blockObj.optString("title", ""),
                                    maxCapacity = blockObj.optInt("maxCapacity", 3),
                                    lines = lines,
                                    mediaItems = mediaItems
                                )
                            )
                        }
                        stagedTabs.add(
                            StagedTab(
                                id = tabObj.optString("id", UUID.randomUUID().toString()),
                                title = tabObj.optString("title", "notes"),
                                blocks = blocks
                            )
                        )
                    }
                }

                // Validation succeeded. Now restore media files and atomically swap app memory.
                val restoredMediaByEntry = restoreStagedMediaFiles(stagingDir, hasNotepadSection)

                val restoredSubjects = stagedSubjects.map { staged ->
                    val restoredTextbookImgs = staged.textbookImages.map { sImg ->
                        val restoredFile = sImg.archiveMediaEntry?.let(restoredMediaByEntry::get)
                        sImg.attachment.copy(filePath = restoredFile?.absolutePath ?: sImg.attachment.filePath)
                    }
                    val restoredExtraImgs = staged.extraKnowledgeImages.map { sImg ->
                        val restoredFile = sImg.archiveMediaEntry?.let(restoredMediaByEntry::get)
                        sImg.attachment.copy(filePath = restoredFile?.absolutePath ?: sImg.attachment.filePath)
                    }
                    staged.subject.copy(
                        textbookImagesJson = SubjectImagesCodec.encode(restoredTextbookImgs),
                        extraKnowledgeImagesJson = SubjectImagesCodec.encode(restoredExtraImgs)
                    )
                }

                val restoredCards = stagedCards.map { staged ->
                    val restoredFile = staged.archiveImageEntry?.let(restoredMediaByEntry::get)
                    val resolvedImageUrl = when {
                        restoredFile != null -> Uri.fromFile(restoredFile).toString()
                        isRemoteUrl(staged.card.imageUrl) -> staged.card.imageUrl
                        else -> null
                    }
                    staged.card.copy(imageUrl = resolvedImageUrl)
                }

                val restoredTabs = stagedTabs.map { tab ->
                    NotepadTab(
                        id = tab.id,
                        title = tab.title,
                        blocks = tab.blocks.map { block ->
                            NotepadBlock(
                                id = block.id,
                                title = block.title,
                                maxCapacity = block.maxCapacity,
                                lines = block.lines,
                                mediaItems = block.mediaItems.map { stagedMedia ->
                                    val restoredFile = stagedMedia.archiveMediaEntry
                                        ?.let(restoredMediaByEntry::get)
                                    stagedMedia.item.copy(
                                        filePath = restoredFile?.absolutePath.orEmpty()
                                    )
                                }
                            )
                        }
                    )
                }

                flashcardDao.replaceAllMemory(
                    subjects = restoredSubjects,
                    cards = restoredCards
                )

                if (hasNotepadSection) {
                    notesRepository.replaceAllTabs(restoredTabs)
                }

                settingsRepository.restoreFromBackup(
                    theme = importedTheme,
                    tokenStreaming = importedTokenStreaming,
                    maxFontSizeSp = importedMaxFontSize,
                    ttsVoiceName = importedTtsVoice,
                    ttsRate = importedTtsRate,
                    onexaiApiKeys = importedKeys,
                    activeOnexaiKeyIndex = importedActiveKeyIndex,
                    houshyarApiKeys = importedHoushyarKeys,
                    activeHoushyarKeyIndex = importedActiveHoushyarKeyIndex,
                    hormouzApiKeys = importedHormouzKeys,
                    activeHormouzKeyIndex = importedActiveHormouzKeyIndex,
                    tttProvider = importedTttProvider,
                    tttModelId = importedTttModelId,
                    sttModelId = importedSttModelId
                )

                FlashDataSummary(
                    apiKeyCount = (importedKeys + importedHoushyarKeys + importedHormouzKeys).distinct().size,
                    cardCount = restoredCards.size,
                    subjectCount = restoredSubjects.size,
                    notepadTabCount = if (hasNotepadSection) restoredTabs.size else 0,
                    mediaFileCount = restoredMediaByEntry.size
                )
            } finally {
                stagingDir.deleteRecursively()
            }
        }

    private fun restoreStagedMediaFiles(
        stagingDir: File,
        replaceNotepadMedia: Boolean
    ): Map<String, File> {
        val restored = HashMap<String, File>()
        val subfolders = buildList {
            add(SUBFOLDER_FLASHCARD_IMAGES)
            add(SUBFOLDER_SUBJECT_IMAGES)
            if (replaceNotepadMedia) {
                add(SUBFOLDER_AUDIO_NOTES)
                add(SUBFOLDER_IMAGE_NOTES)
            }
        }

        for (subfolder in subfolders) {
            val destDir = File(appContext.filesDir, subfolder)
            if (destDir.exists()) {
                destDir.listFiles()?.forEach { it.deleteRecursively() }
            } else {
                destDir.mkdirs()
            }

            val stagedSubDir = File(stagingDir, "$MEDIA_DIR/$subfolder")
            if (!stagedSubDir.exists() || !stagedSubDir.isDirectory) continue

            stagedSubDir.listFiles()?.forEach { stagedFile ->
                if (!stagedFile.isFile) return@forEach
                val safeName = stagedFile.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val destFile = File(destDir, safeName)
                stagedFile.copyTo(destFile, overwrite = true)
                val archiveEntry = "$MEDIA_DIR/$subfolder/${stagedFile.name}"
                restored[archiveEntry] = destFile
            }
        }
        return restored
    }

    private fun resolveLocalFile(rawRef: String?): File? {
        val trimmed = rawRef?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            trimmed.startsWith("file:", ignoreCase = true) -> {
                runCatching { Uri.parse(trimmed).path }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
            }
            trimmed.startsWith("/") -> File(trimmed)
            else -> null
        }
    }

    private fun isRemoteUrl(url: String?): Boolean {
        val trimmed = url?.trim() ?: return false
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    companion object {
        const val FILE_EXTENSION = "flashdata"
        const val FORMAT_ID = "flashrecall.flashdata"
        private const val SCHEMA_VERSION = 1
        private const val MANIFEST_ENTRY_NAME = "manifest.json"
        private const val MEDIA_DIR = "media"
        private const val SUBFOLDER_FLASHCARD_IMAGES = "flashcard_images"
        private const val SUBFOLDER_SUBJECT_IMAGES = "subject_images"
        private const val SUBFOLDER_AUDIO_NOTES = "audio_notes"
        private const val SUBFOLDER_IMAGE_NOTES = "image_notes"
        private const val MAX_ARCHIVE_ENTRIES = 10_000

        fun defaultExportFileName(nowMillis: Long = System.currentTimeMillis()): String {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(nowMillis))
            return "flashrecall_backup_$stamp.$FILE_EXTENSION"
        }
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONArray.optNullableString(index: Int): String? =
    if (index < 0 || index >= length() || isNull(index)) null else getString(index)
