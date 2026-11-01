package com.example.data

object QuestionBankExporter {

    /**
     * Extracts the latest ordered question bank for [subjectId] from a live [allCards] snapshot.
     */
    fun getSubjectQuestionBank(allCards: List<Flashcard>, subjectId: Long): List<Flashcard> {
        return allCards
            .asSequence()
            .filter { it.subjectId == subjectId }
            .sortedWith(compareBy<Flashcard> { it.createdAtMillis }.thenBy { it.id })
            .toList()
    }

    /**
     * Formats all questions in [questions] into a clean, structured document suitable for
     * pasting directly into an LLM to inspect question quality, answers, and status patterns.
     */
    fun formatSubjectQuestionBank(
        subject: Subject?,
        questions: List<Flashcard>
    ): String {
        val ordered = questions.sortedWith(compareBy<Flashcard> { it.createdAtMillis }.thenBy { it.id })
        val activeCount = ordered.count { it.isActive }
        val learnedCount = ordered.count { it.isLearned }
        val dumbCount = ordered.count { it.isDumb }
        val subjectTitle = subject?.name?.trim().takeUnless { it.isNullOrEmpty() } ?: "Subject"

        return buildString {
            appendLine("# Question Bank: $subjectTitle")
            appendLine(
                "Total Questions: ${ordered.size} (Active: $activeCount, Learned: $learnedCount, Dumb: $dumbCount)"
            )
            if (ordered.isEmpty()) {
                appendLine()
                append("(No questions in question bank)")
                return@buildString
            }

            ordered.forEachIndexed { index, card ->
                appendLine()
                val stageTitle = SrsStage.entries.getOrNull(card.stageIndex)?.title ?: "Stage ${card.stageIndex}"
                val normalizedStatus = FlashcardStatus.normalize(card.status)
                appendLine("${index + 1}. [$normalizedStatus | Stage: $stageTitle | Type: ${card.type}]")
                appendLine("Question: ${card.word.trim()}")
                val answer = card.backText?.trim().orEmpty()
                if (answer.isNotEmpty()) {
                    appendLine("Answer: $answer")
                }
                val focus = card.focusSummary?.trim().orEmpty()
                if (focus.isNotEmpty() && !focus.equals("Subject Question", ignoreCase = true)) {
                    appendLine("Focus: $focus")
                }
            }
        }.trimEnd()
    }
}
