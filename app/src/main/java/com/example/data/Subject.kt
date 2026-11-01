package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A single attached page/photo inside a Subject's Textbook or Extra Knowledge,
 * paired with its extracted OCR text so both typed text and dropped images work seamlessly.
 */
data class SubjectImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val ocrText: String = "",
    val isProcessingOcr: Boolean = false,
    val ocrFailed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object SubjectImagesCodec {
    fun encode(items: List<SubjectImageAttachment>): String {
        if (items.isEmpty()) return "[]"
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("filePath", item.filePath)
                    put("ocrText", item.ocrText)
                    put("isProcessingOcr", item.isProcessingOcr)
                    put("ocrFailed", item.ocrFailed)
                    put("timestamp", item.timestamp)
                }
            )
        }
        return arr.toString()
    }

    fun decode(rawJson: String?): List<SubjectImageAttachment> {
        val trimmed = rawJson?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(trimmed)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val filePath = obj.optString("filePath", "").trim()
                    add(
                        SubjectImageAttachment(
                            id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                            filePath = filePath,
                            ocrText = obj.optString("ocrText", ""),
                            isProcessingOcr = obj.optBoolean("isProcessingOcr", false),
                            ocrFailed = obj.optBoolean("ocrFailed", false),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun combineTextAndImageOcr(
        typedText: String,
        images: List<SubjectImageAttachment>
    ): String {
        val cleanTyped = typedText.trim()
        val ocrSections = images.mapIndexedNotNull { idx, item ->
            val extracted = item.ocrText.trim()
            if (extracted.isEmpty()) {
                null
            } else if (images.size == 1 && cleanTyped.isEmpty()) {
                extracted
            } else {
                "[Image ${idx + 1}]\n$extracted"
            }
        }
        return buildList {
            if (cleanTyped.isNotEmpty()) add(cleanTyped)
            addAll(ocrSections)
        }.joinToString("\n\n").trim()
    }
}

/**
 * A named study subject containing questions and optional reference materials:
 * - [textbook] + [textbookImagesJson]: Authoritative answer reference used when grading answers and generating questions.
 * - [extraKnowledge] + [extraKnowledgeImagesJson]: Backing context that is NOT required in the user's answer,
 *   though the AI tutor may draw on or quote it for extra context.
 */
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val textbook: String = "",
    val extraKnowledge: String = "",
    val textbookImagesJson: String = "[]",
    val extraKnowledgeImagesJson: String = "[]",
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun textbookImages(): List<SubjectImageAttachment> =
        SubjectImagesCodec.decode(textbookImagesJson)

    fun extraKnowledgeImages(): List<SubjectImageAttachment> =
        SubjectImagesCodec.decode(extraKnowledgeImagesJson)

    fun effectiveTextbook(): String =
        SubjectImagesCodec.combineTextAndImageOcr(textbook, textbookImages())

    fun effectiveExtraKnowledge(): String =
        SubjectImagesCodec.combineTextAndImageOcr(extraKnowledge, extraKnowledgeImages())

    fun hasTextbookContent(): Boolean =
        textbook.isNotBlank() || textbookImages().isNotEmpty()

    fun hasExtraKnowledgeContent(): Boolean =
        extraKnowledge.isNotBlank() || extraKnowledgeImages().isNotEmpty()
}
