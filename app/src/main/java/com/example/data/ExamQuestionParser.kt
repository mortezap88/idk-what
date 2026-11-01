package com.example.data

/**
 * Splits pasted study material into question/answer pairs.
 *
 * Supported shapes, tried in this order:
 *  1. Lettered or labelled markers — `Q: ...` / `A: ...`, `Question 2: ...` / `Answer: ...`,
 *     and numbered questions (`1. ...`) when answers use an `Answer:` style marker.
 *  2. One pair per line, split by a tab, ` | `, ` -> ` or an em dash.
 *  3. Blank-line separated blocks, where the first line is the question and the rest is the answer.
 */
object ExamQuestionParser {
    data class ParsedQuestion(
        val question: String,
        val answer: String
    )

    private val questionMarker = Regex(
        """^\s*(?:Q\s*\d*\s*[.:)\-]|Question\s*\d*\s*[.:)\-])\s*""",
        RegexOption.IGNORE_CASE
    )
    private val answerMarker = Regex(
        """^\s*(?:A\s*\d*\s*[.:)\-]|Answer\s*\d*\s*[.:)\-])\s*""",
        RegexOption.IGNORE_CASE
    )
    private val numberMarker = Regex("""^\s*\d+\s*[.)]\s+""")
    private val inlineSplitter = Regex("""\t|\s+\|\s+|\s+->\s+|\s+—\s+|\s+-{2,}\s+""", RegexOption.MULTILINE)
    private val blankLineSplitter = Regex("""\n\s*\n""")

    fun parse(raw: String): List<ParsedQuestion> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return emptyList()

        markerPairs(text)?.let { if (it.isNotEmpty()) return it }
        inlinePairs(text).let { if (it.isNotEmpty()) return it }
        return blockPairs(text)
    }

    private fun markerPairs(text: String): List<ParsedQuestion>? {
        val lines = text.lines()
        val hasQuestionMarker = lines.any { questionMarker.containsMatchIn(it) }
        val hasAnswerMarker = lines.any { answerMarker.containsMatchIn(it) }
        if (!hasQuestionMarker && !hasAnswerMarker) return null

        // "1. What is ...?" with "Answer: ..." below it is a very common paste shape.
        val questionMatcher = if (hasQuestionMarker) questionMarker else numberMarker

        val pairs = mutableListOf<ParsedQuestion>()
        var question: StringBuilder? = null
        var answer: StringBuilder? = null

        fun flush() {
            val currentQuestion = question?.toString()?.trim().orEmpty()
            val currentAnswer = answer?.toString()?.trim().orEmpty()
            if (currentQuestion.isNotEmpty()) {
                pairs.add(ParsedQuestion(currentQuestion, currentAnswer))
            }
            question = null
            answer = null
        }

        for (line in lines) {
            when {
                questionMatcher.containsMatchIn(line) -> {
                    flush()
                    question = StringBuilder(line.replaceFirst(questionMatcher, "").trim())
                }
                answerMarker.containsMatchIn(line) -> {
                    if (question == null) continue
                    answer = StringBuilder(line.replaceFirst(answerMarker, "").trim())
                }
                else -> {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    when {
                        question == null -> Unit
                        answer == null -> question = question?.append(' ')?.append(trimmed)
                        else -> answer = answer?.append('\n')?.append(trimmed)
                    }
                }
            }
        }
        flush()
        return pairs
    }

    private fun inlinePairs(text: String): List<ParsedQuestion> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val pairs = lines.mapNotNull { line ->
            val parts = line.split(inlineSplitter, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val question = parts[0].trim()
            val answer = parts[1].trim()
            if (question.isEmpty() || answer.isEmpty()) null else ParsedQuestion(question, answer)
        }
        // Only trust this shape when every line matched, otherwise fall back to blocks.
        return if (pairs.size == lines.size) pairs else emptyList()
    }

    private fun blockPairs(text: String): List<ParsedQuestion> {
        val blocks = text.split(blankLineSplitter)
            .map { block -> block.lines().map { it.trim() }.filter { it.isNotEmpty() } }
            .filter { it.isNotEmpty() }
        if (blocks.isEmpty()) return emptyList()

        // Every block carries its own answer: "question\nanswer".
        if (blocks.all { it.size >= 2 }) {
            return blocks.map { block ->
                ParsedQuestion(
                    question = block.first(),
                    answer = block.drop(1).joinToString(" ")
                )
            }
        }

        // Alternating single-line blocks: "question", blank line, "answer".
        if (blocks.all { it.size == 1 } && blocks.size >= 2 && blocks.size % 2 == 0) {
            return blocks.chunked(2).map { (questionBlock, answerBlock) ->
                ParsedQuestion(
                    question = questionBlock.first(),
                    answer = answerBlock.first()
                )
            }
        }

        // Mixed paste: keep whatever blocks contain an answer.
        return blocks.filter { it.size >= 2 }.map { block ->
            ParsedQuestion(
                question = block.first(),
                answer = block.drop(1).joinToString(" ")
            )
        }
    }
}
