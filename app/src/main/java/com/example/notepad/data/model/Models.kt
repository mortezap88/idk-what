package com.example.notepad.data.model

import com.example.network.LinguisticCorrection
import java.util.UUID

enum class MediaType {
    AUDIO,
    IMAGE
}

data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    val type: MediaType = MediaType.AUDIO,
    val filePath: String = "",
    val transcription: String? = null,
    val englishTranslation: String? = null,
    val corrections: List<LinguisticCorrection> = emptyList(),
    val isTranscribed: Boolean = false,
    val isProcessing: Boolean = false,
    val transcriptionError: String? = null,
    val durationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class NoteLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String = ""
)

data class NotepadBlock(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val maxCapacity: Int = 3,
    val lines: List<NoteLine> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList()
)

data class NotepadTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val blocks: List<NotepadBlock> = emptyList()
)

const val MIN_NOTEPAD_LINE_COUNT = 3

fun NotepadBlock.hasContent(): Boolean =
    title.isNotBlank() || lines.any { it.text.isNotBlank() } || mediaItems.isNotEmpty()

fun NotepadBlock.removeLine(lineId: String): NotepadBlock {
    if (lines.none { it.id == lineId }) return this

    return if (lines.size <= MIN_NOTEPAD_LINE_COUNT) {
        copy(lines = lines.map { line ->
            if (line.id == lineId) line.copy(text = "") else line
        })
    } else {
        copy(lines = lines.filterNot { it.id == lineId })
    }
}

fun NotepadTab.removeEmptySecondaryBlock(blockId: String): NotepadTab {
    if (blocks.size <= 1) return this

    val blockIndex = blocks.indexOfFirst { it.id == blockId }
    if (blockIndex <= 0 || blocks[blockIndex].hasContent()) return this

    return copy(blocks = blocks.filterNot { it.id == blockId })
}
