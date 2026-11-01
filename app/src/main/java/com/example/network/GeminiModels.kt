package com.example.network

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "model"
    val text: String,
    val imageBase64: String? = null,
    val imageBase64List: List<String> = emptyList(),
    val audioBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val corrections: List<LinguisticCorrection> = emptyList()
) {
    fun allImagesBase64(): List<String> {
        val fromList = imageBase64List.filter { it.isNotBlank() }
        if (fromList.isNotEmpty()) return fromList
        return listOfNotNull(imageBase64?.takeIf { it.isNotBlank() })
    }
}

enum class CorrectionType {
    GRAMMAR,
    WORD_CHOICE
}

data class LinguisticCorrection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: CorrectionType,
    val original: String,
    val suggested: String,
    val reason: String
)

enum class TutorResponseMode {
    INITIAL_REVIEW_EVALUATION,
    FREESTYLE
}

data class TutoringResponse(
    val answer: String,
    val corrections: List<LinguisticCorrection>
)

data class TutoringTurnResult(
    val answer: String,
    val corrections: List<LinguisticCorrection>,
    val userTranscript: String? = null
)

data class SpokenAudioAnalysisResult(
    val transcript: String,
    val corrections: List<LinguisticCorrection> = emptyList()
)
