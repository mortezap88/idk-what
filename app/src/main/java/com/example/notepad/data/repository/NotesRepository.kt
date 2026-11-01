package com.example.notepad.data.repository

import android.content.Context
import android.util.Log
import com.example.network.CorrectionJsonCodec
import com.example.notepad.data.model.MediaItem
import com.example.notepad.data.model.MediaType
import com.example.notepad.data.model.NoteLine
import com.example.notepad.data.model.NotepadBlock
import com.example.notepad.data.model.NotepadTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class NotesRepository(context: Context) {

    private val appContext = context.applicationContext

    private val dataFile: File by lazy {
        File(appContext.filesDir, "notepads_data.json")
    }

    private val _tabs = MutableStateFlow<List<NotepadTab>>(emptyList())
    val tabs: StateFlow<List<NotepadTab>> = _tabs.asStateFlow()
    private val updatesMutex = Mutex()

    suspend fun loadData() = updatesMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!dataFile.exists()) {
                val initial = createDefaultData()
                _tabs.value = initial
                saveData(initial)
                return@withContext
            }

            try {
                val content = dataFile.readText()
                val parsed = parseJson(content)
                if (parsed.isEmpty()) {
                    val initial = createDefaultData()
                    _tabs.value = initial
                    saveData(initial)
                } else {
                    _tabs.value = parsed
                }
            } catch (e: Exception) {
                Log.e("NotesRepository", "Failed to parse notepads data, resetting to default", e)
                val initial = createDefaultData()
                _tabs.value = initial
                saveData(initial)
            }
        }
    }

    suspend fun updateTabs(transform: (List<NotepadTab>) -> List<NotepadTab>) =
        updatesMutex.withLock {
            val updatedTabs = transform(_tabs.value)
            _tabs.value = updatedTabs
            withContext(Dispatchers.IO) {
                saveData(updatedTabs)
            }
        }

    private fun saveData(tabs: List<NotepadTab>) {
        try {
            val jsonArray = JSONArray()
            for (tab in tabs) {
                val tabObj = JSONObject().apply {
                    put("id", tab.id)
                    put("title", tab.title)
                    val blocksArray = JSONArray()
                    for (block in tab.blocks) {
                        val blockObj = JSONObject().apply {
                            put("id", block.id)
                            put("title", block.title)
                            put("maxCapacity", block.maxCapacity)
                            val linesArray = JSONArray()
                            for (line in block.lines) {
                                linesArray.put(JSONObject().apply {
                                    put("id", line.id)
                                    put("text", line.text)
                                })
                            }
                            put("lines", linesArray)
                            val mediaArray = JSONArray()
                            for (item in block.mediaItems) {
                                mediaArray.put(JSONObject().apply {
                                    put("id", item.id)
                                    put("type", item.type.name)
                                    put("filePath", item.filePath)
                                    put("transcription", item.transcription ?: "")
                                    put("englishTranslation", item.englishTranslation ?: "")
                                    put("corrections", CorrectionJsonCodec.toJsonArray(item.corrections))
                                    put("isTranscribed", item.isTranscribed)
                                    put("isProcessing", item.isProcessing)
                                    put("transcriptionError", item.transcriptionError ?: "")
                                    put("durationSec", item.durationSec)
                                    put("timestamp", item.timestamp)
                                })
                            }
                            put("mediaItems", mediaArray)
                        }
                        blocksArray.put(blockObj)
                    }
                    put("blocks", blocksArray)
                }
                jsonArray.put(tabObj)
            }
            dataFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e("NotesRepository", "Failed to save notepads data", e)
        }
    }

    private fun parseJson(content: String): List<NotepadTab> {
        val list = mutableListOf<NotepadTab>()
        val array = JSONArray(content)
        for (i in 0 until array.length()) {
            val tabObj = array.getJSONObject(i)
            val tabId = tabObj.optString("id", UUID.randomUUID().toString())
            val title = tabObj.optString("title", "words")
            val blocksArray = tabObj.optJSONArray("blocks") ?: JSONArray()
            val blocks = mutableListOf<NotepadBlock>()

            for (j in 0 until blocksArray.length()) {
                val blockObj = blocksArray.getJSONObject(j)
                val blockId = blockObj.optString("id", UUID.randomUUID().toString())
                val blockTitle = blockObj.optString("title", "")
                val maxCapacity = blockObj.optInt("maxCapacity", 3)

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
                if (lines.size == 6 && lines.all { it.text.isBlank() }) {
                    repeat(3) { lines.removeAt(lines.lastIndex) }
                }

                val mediaArray = blockObj.optJSONArray("mediaItems") ?: JSONArray()
                val mediaItems = mutableListOf<MediaItem>()
                for (k in 0 until mediaArray.length()) {
                    val mObj = mediaArray.getJSONObject(k)
                    val typeStr = mObj.optString("type", "AUDIO")
                    val type = if (typeStr == "IMAGE") MediaType.IMAGE else MediaType.AUDIO
                    val storedIsTranscribed = mObj.optBoolean("isTranscribed", false)
                    val wasProcessingOnDisk = mObj.optBoolean("isProcessing", false)
                    val storedError = if (mObj.isNull("transcriptionError")) {
                        null
                    } else {
                        mObj.optString("transcriptionError", "").ifEmpty { null }
                    }
                    val recoveredError = if (wasProcessingOnDisk && !storedIsTranscribed && storedError == null) {
                        "Transcription was interrupted before completion. Tap Retry to finish."
                    } else {
                        storedError
                    }
                    mediaItems.add(
                        MediaItem(
                            id = mObj.optString("id", UUID.randomUUID().toString()),
                            type = type,
                            filePath = mObj.optString("filePath", ""),
                            transcription = if (mObj.isNull("transcription")) null else mObj.optString("transcription", "").ifEmpty { null },
                            englishTranslation = if (mObj.isNull("englishTranslation")) null else mObj.optString("englishTranslation", "").ifEmpty { null },
                            corrections = CorrectionJsonCodec.fromStoredJsonArray(
                                mObj.optJSONArray("corrections")
                            ),
                            isTranscribed = storedIsTranscribed,
                            isProcessing = false,
                            transcriptionError = recoveredError,
                            durationSec = mObj.optInt("durationSec", 0),
                            timestamp = mObj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }

                blocks.add(
                    NotepadBlock(
                        id = blockId,
                        title = blockTitle,
                        maxCapacity = maxCapacity,
                        lines = lines,
                        mediaItems = mediaItems
                    )
                )
            }
            list.add(NotepadTab(id = tabId, title = title, blocks = blocks))
        }
        return list
    }

    private fun createDefaultData(): List<NotepadTab> {
        // Tab 1: "words" matching Screenshot 2
        val starterLines = listOf(
            "life preserver",
            "trinkets",
            "he had this on him",
            "okay, here's what's gonna happen",
            "mind your business",
            "that kind of this happen without tavener knowing"
        ).map { NoteLine(text = it) }

        val starterMediaItems = listOf(
            MediaItem(transcription = "life preserver and radio beacon", isTranscribed = true),
            MediaItem(transcription = "gold trinkets found near dock", isTranscribed = true),
            MediaItem(transcription = "he had this on him all evening", isTranscribed = true),
            MediaItem(transcription = "plan of action for tomorrow", isTranscribed = true),
            MediaItem(transcription = "mind your own business notes", isTranscribed = true)
        )

        val wordsTab = NotepadTab(
            title = "words",
            blocks = listOf(
                NotepadBlock(
                    title = "Primary Notepad",
                    maxCapacity = 6,
                    lines = starterLines,
                    mediaItems = starterMediaItems
                )
            )
        )

        // Tab 2: "notes" matching Screenshot 1 (full audio files grid)
        val gridItems = mutableListOf<MediaItem>()
        val defaultTranscriptions = listOf(
            "better than the rest of us",
            "clean yourself up",
            "the map says we are nearly there",
            "heading in a wrong direction",
            "let's see if this work",
            "no .... in sight",
            "an inordnte amount of",
            "i don't get it",
            "you owe me, you owe him",
            "did you ... in the first place",
            "are you listening to yourself",
            "for a period of time",
            "isnt that something?",
            "you can't go back and undo what's done",
            "something i can help you with",
            "figure out the answer",
            "i'll call you when it's done"
        )

        for (i in 0 until 48) {
            val text = defaultTranscriptions[i % defaultTranscriptions.size]
            gridItems.add(
                MediaItem(
                    transcription = text,
                    isTranscribed = true
                )
            )
        }

        val notesTab = NotepadTab(
            title = "notes",
            blocks = listOf(
                NotepadBlock(
                    title = "",
                    lines = emptyList(),
                    mediaItems = gridItems
                )
            )
        )

        // Tab 3: "..." matching Screenshot
        val dotsTab = NotepadTab(
            title = "...",
            blocks = listOf(
                NotepadBlock(
                    title = "",
                    lines = List(3) { NoteLine(text = "") },
                    mediaItems = emptyList()
                )
            )
        )

        return listOf(wordsTab, notesTab, dotsTab)
    }

    suspend fun getSnapshotForBackup(): List<NotepadTab> = updatesMutex.withLock {
        withContext(Dispatchers.IO) {
            if (_tabs.value.isNotEmpty()) {
                return@withContext _tabs.value
            }
            if (!dataFile.exists()) {
                val initial = createDefaultData()
                _tabs.value = initial
                saveData(initial)
                return@withContext initial
            }
            val parsed = runCatching { parseJson(dataFile.readText()) }.getOrNull().orEmpty()
            if (parsed.isNotEmpty()) {
                _tabs.value = parsed
                parsed
            } else {
                val initial = createDefaultData()
                _tabs.value = initial
                saveData(initial)
                initial
            }
        }
    }

    suspend fun replaceAllTabs(importedTabs: List<NotepadTab>) = updatesMutex.withLock {
        withContext(Dispatchers.IO) {
            val resolved = importedTabs.ifEmpty { createDefaultData() }
            _tabs.value = resolved
            saveData(resolved)
        }
    }

    suspend fun resetToDefault() = updatesMutex.withLock {
        withContext(Dispatchers.IO) {
            val initial = createDefaultData()
            _tabs.value = initial
            saveData(initial)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: NotesRepository? = null

        fun getInstance(context: Context): NotesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
