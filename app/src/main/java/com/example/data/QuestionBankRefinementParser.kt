package com.example.data

/**
 * Parses and applies Step 2 (Complement, Audit & Refinement) operations for a Subject's
 * question bank.
 *
 * Step 2 runs immediately after Step 1 question generation in a separate AI request and answers:
 *  1. What other questions can be added to make the question bank even more complete? (`ADD`)
 *  2. What active questions should be fixed and edited for logic, clarity, and accuracy? (`EDIT`)
 *  3. What active questions are genuinely better combined into a cohesive question? (`COMBINE`)
 *
 * Frame syntax (supports live SSE streaming block-by-block as well as end-of-stream salvaging):
 * ```
 * [op_item]
 * [action] ADD | EDIT | COMBINE [/action]
 * [ids] 42 [/ids]                 // omitted for ADD; 1 ID for EDIT; 2+ comma-separated IDs for COMBINE
 * [q] ...question text... [/q]
 * [a] ...authoritative answer... [/a]
 * [/op_item]
 * ```
 */
object QuestionBankRefinementParser {

    const val NO_CHANGES_TAG = "[no_refinements_needed]"

    sealed interface RefinementOperation {
        data class Add(
            val question: String,
            val answer: String
        ) : RefinementOperation

        data class Edit(
            val targetId: Long,
            val question: String,
            val answer: String
        ) : RefinementOperation

        data class Combine(
            val sourceIds: List<Long>,
            val question: String,
            val answer: String
        ) : RefinementOperation
    }

    data class RefinementExecutionSummary(
        val addedCount: Int = 0,
        val editedCount: Int = 0,
        val combinedCount: Int = 0,
        val mergedAwayCardIds: Set<Long> = emptySet(),
        val updatedCardsById: Map<Long, Flashcard> = emptyMap()
    ) {
        val hasAnyChange: Boolean
            get() = addedCount > 0 || editedCount > 0 || combinedCount > 0
    }

    private val CODE_FENCE_REGEX = Regex("(?is)```(?:json|text|xml)?\\s*(.*?)\\s*```")
    private val OP_ITEM_BLOCK_REGEX = Regex("(?is)\\[op_item\\]\\s*(.*?)\\s*\\[/op_item\\]")
    private val TRAILING_UNCLOSED_OP_ITEM_REGEX = Regex("(?is)\\[op_item\\]\\s*((?:(?!\\[op_item\\]).)*)$")
    private val LEGACY_Q_ITEM_REGEX =
        Regex("(?is)\\[q_item\\]\\s*\\[q\\]\\s*(.*?)\\s*\\[/q\\]\\s*\\[a\\]\\s*(.*?)\\s*\\[/a\\]\\s*\\[/q_item\\]")

    private val ACTION_TAG_REGEX = Regex("(?is)\\[action\\]\\s*(.*?)\\s*\\[/action\\]")
    private val IDS_TAG_REGEX = Regex("(?is)\\[(?:ids?|target_ids?|source_ids?)\\]\\s*(.*?)\\s*\\[/(?:ids?|target_ids?|source_ids?)\\]")
    private val QUESTION_TAG_REGEX = Regex("(?is)\\[q\\]\\s*(.*?)\\s*\\[/q\\]")
    private val ANSWER_TAG_REGEX = Regex("(?is)\\[a\\]\\s*(.*?)\\s*\\[/a\\]")
    private val NUMBER_TOKEN_REGEX = Regex("\\d+")

    /**
     * Stateful incremental collector that emits each completed `[op_item]` (or fallback `[q_item]`)
     * once during streaming while deduplicating invalid/redundant mutations.
     */
    class IncrementalRefinementCollector(
        activeCardIds: Set<Long>,
        existingQuestions: List<String>,
        private val onOperation: (RefinementOperation) -> Unit = {}
    ) {
        private val availableActiveIds = activeCardIds.toMutableSet()
        private val seenQuestionKeys = existingQuestions
            .map { normalizeQuestionKey(it) }
            .filter { it.isNotEmpty() }
            .toHashSet()

        private val _emitted = mutableListOf<RefinementOperation>()
        val emittedOperations: List<RefinementOperation>
            get() = _emitted.toList()

        private var scanCursor = 0

        @Synchronized
        fun onPartialStream(accumulated: String) {
            if (accumulated.length <= scanCursor) return
            val window = accumulated.substring(scanCursor)

            val useOpItems = accumulated.contains("[op_item]", ignoreCase = true)
            val regex = if (useOpItems) OP_ITEM_BLOCK_REGEX else LEGACY_Q_ITEM_REGEX

            var lastConsumedEnd = 0
            for (match in regex.findAll(window)) {
                val op = if (useOpItems) {
                    parseOpBody(match.groupValues.getOrNull(1).orEmpty())
                } else {
                    val q = cleanQuestionText(match.groupValues.getOrNull(1).orEmpty())
                    val a = match.groupValues.getOrNull(2)?.trim().orEmpty()
                    if (q.isNotEmpty() && a.isNotEmpty()) RefinementOperation.Add(q, a) else null
                }
                if (op != null) {
                    acceptIfValid(op)
                }
                lastConsumedEnd = match.range.last + 1
            }

            if (lastConsumedEnd > 0) {
                scanCursor += lastConsumedEnd
            }
        }

        @Synchronized
        fun finish(finalRaw: String) {
            onPartialStream(finalRaw)
            val allParsed = parseOperations(finalRaw)
            for (op in allParsed) {
                acceptIfValid(op)
            }
        }

        private fun acceptIfValid(op: RefinementOperation) {
            when (op) {
                is RefinementOperation.Add -> {
                    val q = cleanQuestionText(op.question)
                    val a = op.answer.trim()
                    if (q.isEmpty() || a.isEmpty()) return
                    val key = normalizeQuestionKey(q)
                    if (key.isNotEmpty() && seenQuestionKeys.add(key)) {
                        val normalized = RefinementOperation.Add(question = q, answer = a)
                        _emitted.add(normalized)
                        onOperation(normalized)
                    }
                }

                is RefinementOperation.Edit -> {
                    val q = cleanQuestionText(op.question)
                    val a = op.answer.trim()
                    if (q.isEmpty() || a.isEmpty()) return
                    if (!availableActiveIds.remove(op.targetId)) return
                    val key = normalizeQuestionKey(q)
                    if (key.isNotEmpty()) {
                        seenQuestionKeys.add(key)
                    }
                    val normalized = RefinementOperation.Edit(
                        targetId = op.targetId,
                        question = q,
                        answer = a
                    )
                    _emitted.add(normalized)
                    onOperation(normalized)
                }

                is RefinementOperation.Combine -> {
                    val q = cleanQuestionText(op.question)
                    val a = op.answer.trim()
                    if (q.isEmpty() || a.isEmpty()) return
                    val validIds = op.sourceIds.distinct().filter { it in availableActiveIds }
                    if (validIds.size < 2) {
                        // If only 1 valid active ID remains unconsumed, treat as an Edit of that card;
                        // if 0 valid active IDs match, ignore rather than corrupting cards.
                        val singleId = validIds.firstOrNull() ?: return
                        availableActiveIds.remove(singleId)
                        val key = normalizeQuestionKey(q)
                        if (key.isNotEmpty()) seenQuestionKeys.add(key)
                        val fallbackEdit = RefinementOperation.Edit(
                            targetId = singleId,
                            question = q,
                            answer = a
                        )
                        _emitted.add(fallbackEdit)
                        onOperation(fallbackEdit)
                        return
                    }
                    availableActiveIds.removeAll(validIds.toSet())
                    val key = normalizeQuestionKey(q)
                    if (key.isNotEmpty()) {
                        seenQuestionKeys.add(key)
                    }
                    val normalized = RefinementOperation.Combine(
                        sourceIds = validIds,
                        question = q,
                        answer = a
                    )
                    _emitted.add(normalized)
                    onOperation(normalized)
                }
            }
        }
    }

    /**
     * Parses all `[op_item]` blocks from [raw], salvaging a trailing `[op_item]` whose `[q]` and
     * `[a]` closed cleanly before `[/op_item]` if the stream paused at the very end, and falling
     * back to `[q_item]` `ADD` operations if a model emitted plain `[q_item]` blocks.
     */
    fun parseOperations(raw: String): List<RefinementOperation> {
        val stripped = CODE_FENCE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.trim()
            ?: raw.trim()

        if (stripped.isEmpty() ||
            stripped.equals(NO_CHANGES_TAG, ignoreCase = true) ||
            (stripped.contains(NO_CHANGES_TAG, ignoreCase = true) &&
                !stripped.contains("[q]", ignoreCase = true))
        ) {
            return emptyList()
        }

        if (stripped.contains("[op_item]", ignoreCase = true)) {
            val closedMatches = OP_ITEM_BLOCK_REGEX.findAll(stripped).toList()
            val ops = mutableListOf<RefinementOperation>()
            var lastEnd = 0
            for (match in closedMatches) {
                parseOpBody(match.groupValues.getOrNull(1).orEmpty())?.let { ops.add(it) }
                lastEnd = match.range.last + 1
            }

            val tail = stripped.substring(lastEnd)
            val trailingMatch = TRAILING_UNCLOSED_OP_ITEM_REGEX.find(tail)
            if (trailingMatch != null) {
                parseOpBody(trailingMatch.groupValues.getOrNull(1).orEmpty())?.let { ops.add(it) }
            }
            return ops
        }

        // Fallback if a model returned `[q_item]` blocks directly in Step 2 -> treat as ADD.
        return LEGACY_Q_ITEM_REGEX.findAll(stripped).mapNotNull { match ->
            val q = cleanQuestionText(match.groupValues.getOrNull(1).orEmpty())
            val a = match.groupValues.getOrNull(2)?.trim().orEmpty()
            if (q.isNotEmpty() && a.isNotEmpty()) {
                RefinementOperation.Add(question = q, answer = a)
            } else {
                null
            }
        }.toList()
    }

    private fun parseOpBody(body: String): RefinementOperation? {
        if (body.isBlank()) return null

        val rawAction = ACTION_TAG_REGEX.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.uppercase()
            .orEmpty()

        val rawIds = IDS_TAG_REGEX.find(body)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val parsedIds = NUMBER_TOKEN_REGEX.findAll(rawIds)
            .mapNotNull { it.value.toLongOrNull() }
            .distinct()
            .toList()

        val q = cleanQuestionText(QUESTION_TAG_REGEX.find(body)?.groupValues?.getOrNull(1).orEmpty())
        val a = ANSWER_TAG_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (q.isEmpty() || a.isEmpty()) return null

        return when {
            rawAction.startsWith("COMBINE") || rawAction.startsWith("MERGE") -> {
                if (parsedIds.size >= 2) {
                    RefinementOperation.Combine(sourceIds = parsedIds, question = q, answer = a)
                } else if (parsedIds.size == 1) {
                    RefinementOperation.Edit(targetId = parsedIds.first(), question = q, answer = a)
                } else {
                    null
                }
            }

            rawAction.startsWith("EDIT") || rawAction.startsWith("FIX") || rawAction.startsWith("UPDATE") -> {
                val targetId = parsedIds.firstOrNull() ?: return null
                RefinementOperation.Edit(targetId = targetId, question = q, answer = a)
            }

            rawAction.startsWith("ADD") || rawAction.isEmpty() && parsedIds.isEmpty() -> {
                RefinementOperation.Add(question = q, answer = a)
            }

            else -> null
        }
    }

    /**
     * Applies a single validated [RefinementOperation] directly to [flashcardDao] for [subjectId],
     * strictly protecting `LEARNED`, `DUMB`, and other subjects' cards from accidental mutation.
     */
    suspend fun applyOperationToDatabase(
        subjectId: Long,
        operation: RefinementOperation,
        flashcardDao: FlashcardDao,
        createdAtMillis: Long = System.currentTimeMillis(),
        nowMillis: Long = System.currentTimeMillis()
    ): RefinementExecutionSummary {
        return when (operation) {
            is RefinementOperation.Add -> {
                val newCard = Flashcard(
                    type = FlashcardTypes.EXAM,
                    word = operation.question.trim(),
                    backText = operation.answer.trim(),
                    subjectId = subjectId,
                    stageIndex = 0,
                    nextReviewTimeMillis = nowMillis + SrsStage.TWO_MINUTES.durationMillis,
                    createdAtMillis = createdAtMillis,
                    status = FlashcardStatus.ACTIVE
                )
                flashcardDao.insertCard(newCard)
                RefinementExecutionSummary(addedCount = 1)
            }

            is RefinementOperation.Edit -> {
                val existing = flashcardDao.getCardById(operation.targetId)
                    ?: return RefinementExecutionSummary()
                if (existing.subjectId != subjectId || !existing.isActive) {
                    return RefinementExecutionSummary()
                }
                val updated = existing.copy(
                    word = operation.question.trim(),
                    backText = operation.answer.trim()
                )
                flashcardDao.updateCard(updated)
                RefinementExecutionSummary(
                    editedCount = 1,
                    updatedCardsById = mapOf(updated.id to updated)
                )
            }

            is RefinementOperation.Combine -> {
                val validCards = operation.sourceIds.distinct().mapNotNull { id ->
                    flashcardDao.getCardById(id)?.takeIf { it.subjectId == subjectId && it.isActive }
                }
                if (validCards.isEmpty()) return RefinementExecutionSummary()
                if (validCards.size == 1) {
                    val onlyCard = validCards.first()
                    val updated = onlyCard.copy(
                        word = operation.question.trim(),
                        backText = operation.answer.trim()
                    )
                    flashcardDao.updateCard(updated)
                    return RefinementExecutionSummary(
                        editedCount = 1,
                        updatedCardsById = mapOf(updated.id to updated)
                    )
                }

                val primary = validCards.first()
                val redundant = validCards.drop(1)
                val earliestStage = validCards.minOf { it.stageIndex }
                val earliestNextReview = validCards.minOf { it.nextReviewTimeMillis }
                val updatedPrimary = primary.copy(
                    word = operation.question.trim(),
                    backText = operation.answer.trim(),
                    stageIndex = earliestStage,
                    nextReviewTimeMillis = earliestNextReview
                )
                flashcardDao.updateCard(updatedPrimary)
                val removedIds = LinkedHashSet<Long>()
                for (extra in redundant) {
                    flashcardDao.deleteCardById(extra.id)
                    removedIds.add(extra.id)
                }
                RefinementExecutionSummary(
                    combinedCount = 1,
                    mergedAwayCardIds = removedIds,
                    updatedCardsById = mapOf(updatedPrimary.id to updatedPrimary)
                )
            }
        }
    }

    internal fun cleanQuestionText(rawQuestion: String): String =
        rawQuestion.trim()
            .replace(Regex("^(?i)(?:\\[(?:q_item|op_item)\\]\\s*)?(?:q|question|سوال)\\s*\\d*\\s*[:：.-]\\s*"), "")
            .trim()

    internal fun normalizeQuestionKey(text: String): String =
        text.lowercase()
            .replace(Regex("[\\s\\p{Punct}؟،؛«»]+"), " ")
            .trim()
}
