package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object FlashcardTypes {
    const val TEXT = "TEXT"
    const val SPELLING = "SPELLING"
    const val PRONUNCIATION = "PRONUNCIATION"
    const val IMAGE = "IMAGE"
    const val CONVERSATION_LINGUISTIC = "CONVERSATION_LINGUISTIC"
    const val CONVERSATION_TECHNICAL = "CONVERSATION_TECHNICAL"
    const val EXAM = "EXAM"
}

object FlashcardStatus {
    const val ACTIVE = "ACTIVE"
    const val DUMB = "DUMB"
    const val LEARNED = "LEARNED"

    fun normalize(raw: String?): String = when (raw?.trim()?.uppercase()) {
        DUMB -> DUMB
        LEARNED -> LEARNED
        else -> ACTIVE
    }
}

@Entity(tableName = "flashcards")
data class Flashcard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String, // from FlashcardTypes
    val word: String, // Word, term, topic, front text, or exam question
    val backText: String? = null, // Back face for TEXT cards, reference answer for EXAM cards
    val imageUrl: String? = null,
    val subjectId: Long? = null, // Set for cards that belong to a Subject
    val stageIndex: Int = 0, // 0 to 7
    val nextReviewTimeMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val conversationHistoryJson: String? = null, // JSON representation of creation chat
    val focusSummary: String? = null, // Grammar rule, context, or technical concept
    val lastScenarioOrQuestion: String? = null, // Generated scenario or question for review
    val status: String = FlashcardStatus.ACTIVE // ACTIVE, DUMB, or LEARNED
) {
    val isActive: Boolean
        get() = status == FlashcardStatus.ACTIVE

    val isDumb: Boolean
        get() = status == FlashcardStatus.DUMB

    val isLearned: Boolean
        get() = status == FlashcardStatus.LEARNED
}
