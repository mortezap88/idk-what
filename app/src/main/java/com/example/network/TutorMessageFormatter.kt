package com.example.network

/**
 * Parses and formats the AI tutor's hidden structured evaluation markup (`[lang:fa]` / `[lang:en]`,
 * `[green]`, `[red]`, `[blue]`) and inline rich emphasis (`**bold**`, `==highlight==`, `*italic*`,
 * `` `code` ``) so raw control tags are never exposed to the user — even mid-stream.
 */
object TutorMessageFormatter {

    enum class FeedbackTone {
        GREEN,
        RED,
        BLUE
    }

    enum class ReplyOrientation {
        PERSIAN_RTL,
        ENGLISH_LTR
    }

    data class ParsedTutorMessage(
        val orientation: ReplyOrientation,
        val segments: List<MessageSegment>
    )

    sealed interface MessageSegment {
        data class EvaluationBlock(
            val tone: FeedbackTone,
            val blocks: List<RichBlock>
        ) : MessageSegment

        data class FreestyleBlock(
            val block: RichBlock
        ) : MessageSegment
    }

    sealed interface RichBlock {
        data class Paragraph(val spans: List<InlineSpan>) : RichBlock
        data class Heading(val level: Int, val spans: List<InlineSpan>) : RichBlock
        data class BulletItem(val marker: String, val spans: List<InlineSpan>) : RichBlock
    }

    enum class InlineStyle {
        PLAIN,
        BOLD,
        HIGHLIGHT,
        ITALIC,
        CODE
    }

    data class InlineSpan(
        val text: String,
        val style: InlineStyle = InlineStyle.PLAIN
    )

    private val KNOWN_BRACKET_PREFIXES = listOf(
        "[answer]",
        "[/answer]",
        "[lang:fa]",
        "[lang:en]",
        "[lang:persian]",
        "[lang:english]",
        "[lang:rtl]",
        "[lang:ltr]",
        "[linguistic_correction]",
        "[correction]",
        "[transcription]",
        "[green]",
        "[/green]",
        "[red]",
        "[/red]",
        "[blue]",
        "[/blue]",
        "[[hl]]",
        "[[/hl]]"
    )

    private val KNOWN_ANGLE_TAGS = listOf(
        "<green>",
        "</green>",
        "<red>",
        "</red>",
        "<blue>",
        "</blue>"
    )

    private val ANSWER_WRAPPER_REGEX = Regex("(?is)^\\s*(?:\\[answer\\]|<<<ANSWER>>>)\\s*")
    private val CLOSING_ANSWER_REGEX = Regex("(?is)\\[/answer\\]")
    private val LANG_TAG_REGEX = Regex("(?is)\\[lang\\s*:\\s*([a-z_-]+)\\]\\s*")

    private val SECTION_TOKEN_REGEX = Regex(
        "(?is)(\\[/?(?:green|red|blue)\\]|</?(?:green|red|blue)>|(?m)^\\s*(?:🟢|🔴|🔵)\\s*)"
    )

    private val HEADING_LINE_REGEX = Regex("^\\s*(#{1,3})\\s+(.+)$")
    private val BULLET_LINE_REGEX = Regex("^\\s*(?:[-*•]|(\\d+[.)]))\\s+(.+)$")
    private val LEADING_TONE_EMOJI_REGEX = Regex("^\\s*(?:🟢|🔴|🔵)\\s*[:：\\-–—]?\\s*")

    private val BOILERPLATE_PREFIX_REGEX = Regex(
        "(?iu)^\\s*(?:[\"«“']?\\s*)?(?:پاسخ\\s*کامل|جواب\\s*کامل|پاسخ\\s*صحیح|پاسخ\\s*درست|برای\\s*حفظ(?:\\s*کردن)?|نکته\\s*حفظی|نکته\\s*تکمیلی|نکته|بخش\\s*درست|بخش\\s*نادرست|اصلاح|جمع[‌\\s]*بندی|the\\s+complete\\s+answer(?:\\s+is)?|complete\\s+answer|full\\s+answer|correct\\s+answer|for\\s+memorization|memorization\\s+(?:tip|note)|to\\s+memorize|extra\\s+(?:info|information|note|tip)|context|pro\\s*tip|what\\s+you\\s+got\\s+right|what\\s+to\\s+improve)\\s*(?:[\"»”']?\\s*)?[:：\\-–—]\\s*"
    )

    /**
     * Parses a model message (complete or mid-stream) into its majority language orientation
     * (`PERSIAN_RTL` vs `ENGLISH_LTR`) and structured/freestyle segments, stripping all secret tags.
     */
    fun parseMessage(raw: String): ParsedTutorMessage {
        val (explicitOrientation, cleaned) = extractOrientationAndBody(raw)
        if (cleaned.isBlank()) {
            return ParsedTutorMessage(
                orientation = explicitOrientation ?: ReplyOrientation.ENGLISH_LTR,
                segments = emptyList()
            )
        }

        val segments = parseSegmentsFromCleanBody(cleaned)
        val plainText = segmentsToPlainText(segments)
        val resolvedOrientation = explicitOrientation ?: detectMajorityOrientation(plainText)

        return ParsedTutorMessage(
            orientation = resolvedOrientation,
            segments = segments
        )
    }

    /**
     * Convenience wrapper returning parsed segments directly.
     */
    fun parseSegments(raw: String): List<MessageSegment> = parseMessage(raw).segments

    private fun parseSegmentsFromCleanBody(cleaned: String): List<MessageSegment> {
        val matches = SECTION_TOKEN_REGEX.findAll(cleaned).toList()
        if (matches.isEmpty()) {
            return parseRichBlocks(cleaned, stripBoilerplatePrefix = false).map {
                MessageSegment.FreestyleBlock(it)
            }
        }

        val segments = mutableListOf<MessageSegment>()
        var cursor = 0
        var activeTone: FeedbackTone? = null
        val activeBuffer = StringBuilder()

        fun flushActiveTone() {
            val tone = activeTone ?: return
            val content = activeBuffer.toString().trim()
            activeBuffer.setLength(0)
            activeTone = null
            if (content.isNotEmpty()) {
                val blocks = parseRichBlocks(content, stripBoilerplatePrefix = true)
                if (blocks.isNotEmpty()) {
                    segments += MessageSegment.EvaluationBlock(tone = tone, blocks = blocks)
                }
            }
        }

        fun flushFreestyle(text: String) {
            val trimmed = text.trim()
            if (trimmed.isNotEmpty()) {
                parseRichBlocks(trimmed, stripBoilerplatePrefix = false).forEach { block ->
                    segments += MessageSegment.FreestyleBlock(block)
                }
            }
        }

        for (match in matches) {
            val chunkBefore = cleaned.substring(cursor, match.range.first)
            if (activeTone != null) {
                activeBuffer.append(chunkBefore)
            } else {
                flushFreestyle(chunkBefore)
            }

            val token = match.value.trim().lowercase()
            when {
                token == "[green]" || token == "<green>" || token == "🟢" -> {
                    flushActiveTone()
                    activeTone = FeedbackTone.GREEN
                }
                token == "[red]" || token == "<red>" || token == "🔴" -> {
                    flushActiveTone()
                    activeTone = FeedbackTone.RED
                }
                token == "[blue]" || token == "<blue>" || token == "🔵" -> {
                    flushActiveTone()
                    activeTone = FeedbackTone.BLUE
                }
                token == "[/green]" || token == "</green>" ||
                    token == "[/red]" || token == "</red>" ||
                    token == "[/blue]" || token == "</blue>" -> {
                    flushActiveTone()
                }
            }
            cursor = match.range.last + 1
        }

        val remainder = cleaned.substring(cursor)
        if (activeTone != null) {
            activeBuffer.append(remainder)
            flushActiveTone()
        } else {
            flushFreestyle(remainder)
        }

        return mergeAdjacentSameToneBlocks(segments)
    }

    /**
     * Converts any model text (including hidden evaluation/orientation tags and inline emphasis)
     * into clean, human-readable text suitable for TTS or history context.
     */
    fun toPlainSpeakableText(raw: String): String {
        val segments = parseSegments(raw)
        return segmentsToPlainText(segments)
    }

    private fun segmentsToPlainText(segments: List<MessageSegment>): String {
        if (segments.isEmpty()) return ""
        return segments.joinToString("\n\n") { segment ->
            when (segment) {
                is MessageSegment.EvaluationBlock ->
                    segment.blocks.joinToString("\n") { richBlockToPlainText(it) }
                is MessageSegment.FreestyleBlock ->
                    richBlockToPlainText(segment.block)
            }
        }.trim()
    }

    private fun richBlockToPlainText(block: RichBlock): String = when (block) {
        is RichBlock.Paragraph -> block.spans.joinToString("") { it.text }
        is RichBlock.Heading -> block.spans.joinToString("") { it.text }
        is RichBlock.BulletItem -> "${block.marker} ${block.spans.joinToString("") { it.text }}"
    }

    /**
     * Determines majority script orientation across the whole reply when `[lang:...]` is absent
     * (e.g., older persisted history or initial review prompt). Even if a Persian reply starts
     * with an English acronym like `ATP`, majority script counting recognizes it as Persian!
     */
    fun detectMajorityOrientation(text: String): ReplyOrientation {
        var rtlCount = 0
        var ltrCount = 0
        for (ch in text) {
            when {
                ch in '\u0590'..'\u08FF' ||
                    ch in '\uFB1D'..'\uFDFF' ||
                    ch in '\uFE70'..'\uFEFF' -> rtlCount++
                ch in 'A'..'Z' || ch in 'a'..'z' -> ltrCount++
            }
        }
        return if (rtlCount > ltrCount) {
            ReplyOrientation.PERSIAN_RTL
        } else {
            ReplyOrientation.ENGLISH_LTR
        }
    }

    private fun mergeAdjacentSameToneBlocks(segments: List<MessageSegment>): List<MessageSegment> {
        if (segments.size <= 1) return segments
        val merged = mutableListOf<MessageSegment>()
        for (segment in segments) {
            val prev = merged.lastOrNull()
            if (prev is MessageSegment.EvaluationBlock &&
                segment is MessageSegment.EvaluationBlock &&
                prev.tone == segment.tone
            ) {
                merged[merged.lastIndex] = prev.copy(blocks = prev.blocks + segment.blocks)
            } else {
                merged += segment
            }
        }
        return merged
    }

    /**
     * Extracts `[answer]` body while preserving any `[lang:fa]`/`[lang:en]` tag at the front so
     * `ChatMessage.text` retains explicit language orientation across streaming & persistence,
     * while holding back any incomplete trailing secret tags.
     */
    fun extractAnswerBody(raw: String): String {
        val trimmedStart = raw.trimStart()
        if (trimmedStart.isEmpty()) return ""

        if (isIncompleteLeadingTag(trimmedStart)) {
            return ""
        }

        var text = trimmedStart.replaceFirst(ANSWER_WRAPPER_REGEX, "")
        text = text.replace(CLOSING_ANSWER_REGEX, "")

        if (isIncompleteLeadingTag(text.trimStart())) {
            return ""
        }

        val cutMarkers = listOf(
            "[linguistic_correction]",
            "[correction]",
            "[transcription]",
            "<<<CORRECTIONS>>>"
        )
        var earliestCut = text.length
        for (marker in cutMarkers) {
            val idx = text.indexOf(marker, ignoreCase = true)
            if (idx in 0 until earliestCut) {
                earliestCut = idx
            }
        }
        text = text.substring(0, earliestCut)
        text = stripTrailingPartialMarkup(text)
        return text.trim()
    }

    private fun extractOrientationAndBody(raw: String): Pair<ReplyOrientation?, String> {
        var body = extractAnswerBody(raw)
        if (body.isEmpty()) return null to ""

        var detected: ReplyOrientation? = null
        val match = LANG_TAG_REGEX.find(body)
        if (match != null) {
            val code = match.groupValues[1].lowercase()
            detected = when {
                code.startsWith("fa") || code == "persian" || code == "farsi" || code == "rtl" || code.startsWith("ar") ->
                    ReplyOrientation.PERSIAN_RTL
                code.startsWith("en") || code == "english" || code == "ltr" ->
                    ReplyOrientation.ENGLISH_LTR
                else -> null
            }
            body = body.replace(LANG_TAG_REGEX, "").trim()
        }
        return detected to body
    }

    private fun isIncompleteLeadingTag(text: String): Boolean {
        if (text.startsWith("[") && !text.contains("]")) {
            val lower = text.lowercase()
            if (KNOWN_BRACKET_PREFIXES.any { it.startsWith(lower) } || lower.startsWith("[lang:")) {
                return true
            }
        }
        if (text.startsWith("<") && !text.contains(">")) {
            val lower = text.lowercase()
            if (KNOWN_ANGLE_TAGS.any { it.startsWith(lower) } || "<<<answer>>>".startsWith(lower)) {
                return true
            }
        }
        return false
    }

    private fun stripTrailingPartialMarkup(input: String): String {
        var text = input

        val lastDoubleBracket = text.lastIndexOf("[[")
        if (lastDoubleBracket >= 0 && text.indexOf("]]", startIndex = lastDoubleBracket) < 0) {
            val tail = text.substring(lastDoubleBracket).lowercase()
            if (KNOWN_BRACKET_PREFIXES.any { it.startsWith(tail) }) {
                text = text.substring(0, lastDoubleBracket)
            }
        }

        val lastBracket = text.lastIndexOf('[')
        if (lastBracket >= 0 && text.indexOf(']', startIndex = lastBracket) < 0) {
            val tail = text.substring(lastBracket).lowercase()
            if (KNOWN_BRACKET_PREFIXES.any { it.startsWith(tail) } || tail.startsWith("[lang:")) {
                text = text.substring(0, lastBracket)
            }
        }

        val lastAngle = text.lastIndexOf('<')
        if (lastAngle >= 0 && text.indexOf('>', startIndex = lastAngle) < 0) {
            val tail = text.substring(lastAngle).lowercase()
            if (KNOWN_ANGLE_TAGS.any { it.startsWith(tail) } || "<<<corrections>>>".startsWith(tail)) {
                text = text.substring(0, lastAngle)
            }
        }

        text = text.trimEnd { ch -> ch == '\u200B' }
        while (text.endsWith("**") || text.endsWith("==")) {
            text = text.dropLast(2)
        }
        if (text.endsWith("*") || text.endsWith("=") || text.endsWith("`")) {
            val lastChar = text.last()
            val before = text.dropLast(1)
            val shouldStrip = when (lastChar) {
                '*' -> before.count { it == '*' } % 2 == 0
                '`' -> before.count { it == '`' } % 2 == 0
                '=' -> before.endsWith("=") || countDoubleChar(before, '=') % 2 == 1 || before.lastOrNull()?.isWhitespace() == true
                else -> false
            }
            if (shouldStrip) {
                text = before
            }
        }
        return text
    }

    private fun countDoubleChar(s: String, ch: Char): Int {
        var count = 0
        var i = 0
        while (i < s.length - 1) {
            if (s[i] == ch && s[i + 1] == ch) {
                count++
                i += 2
            } else {
                i++
            }
        }
        return count
    }

    private fun cleanEvaluationBoilerplate(rawSection: String): String {
        var cleaned = rawSection
            .replace("\r\n", "\n")
            .replaceFirst(LEADING_TONE_EMOJI_REGEX, "")
            .trim()
        // Strip boilerplate lead-ins like "پاسخ کامل:", "برای حفظ:", "The complete answer:"
        cleaned = cleaned.replaceFirst(BOILERPLATE_PREFIX_REGEX, "").trim()
        // Unwrap if the entire block was wrapped in outer quotation marks ("...", «...», “...”)
        cleaned = unwrapOuterQuotes(cleaned)
        return cleaned.trim()
    }

    private fun unwrapOuterQuotes(text: String): String {
        if (text.length < 3) return text
        val pairs = listOf(
            '"' to '"',
            '“' to '”',
            '«' to '»'
        )
        for ((open, close) in pairs) {
            if (text.first() == open && text.last() == close) {
                val inner = text.substring(1, text.length - 1)
                // Only unwrap if the closing quote wasn't closed earlier mid-sentence
                if (!inner.contains(close)) {
                    return inner.trim()
                }
            }
        }
        return text
    }

    fun parseRichBlocks(
        sectionText: String,
        stripBoilerplatePrefix: Boolean = false
    ): List<RichBlock> {
        val cleaned = if (stripBoilerplatePrefix) {
            cleanEvaluationBoilerplate(sectionText)
        } else {
            sectionText
                .replace("\r\n", "\n")
                .replaceFirst(LEADING_TONE_EMOJI_REGEX, "")
                .trim()
        }
        if (cleaned.isEmpty()) return emptyList()

        val result = mutableListOf<RichBlock>()
        val paragraphLines = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphLines.isEmpty()) return
            val joined = paragraphLines.joinToString("\n").trim()
            paragraphLines.clear()
            if (joined.isNotEmpty()) {
                val spans = parseInlineSpans(joined)
                if (spans.any { it.text.isNotEmpty() }) {
                    result += RichBlock.Paragraph(spans)
                }
            }
        }

        for (rawLine in cleaned.split('\n')) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                flushParagraph()
                continue
            }

            val headingMatch = HEADING_LINE_REGEX.matchEntire(line)
            if (headingMatch != null) {
                flushParagraph()
                val level = headingMatch.groupValues[1].length.coerceIn(1, 3)
                val body = headingMatch.groupValues[2].trim()
                val spans = parseInlineSpans(body)
                if (spans.any { it.text.isNotEmpty() }) {
                    result += RichBlock.Heading(level = level, spans = spans)
                }
                continue
            }

            val bulletMatch = BULLET_LINE_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                flushParagraph()
                val numbered = bulletMatch.groupValues[1]
                val marker = if (numbered.isNotEmpty()) numbered else "•"
                val body = bulletMatch.groupValues[2].trim()
                val spans = parseInlineSpans(body)
                if (spans.any { it.text.isNotEmpty() }) {
                    result += RichBlock.BulletItem(marker = marker, spans = spans)
                }
                continue
            }

            paragraphLines += line.trimStart()
        }

        flushParagraph()
        return result
    }

    /**
     * Parses inline emphasis markers without leaking raw delimiters (`**`, `==`, `[[hl]]`, `` ` ``, `*`).
     */
    fun parseInlineSpans(input: String): List<InlineSpan> {
        if (input.isEmpty()) return emptyList()
        val spans = mutableListOf<InlineSpan>()
        val plainBuffer = StringBuilder()

        fun flushPlain() {
            if (plainBuffer.isNotEmpty()) {
                spans += InlineSpan(plainBuffer.toString(), InlineStyle.PLAIN)
                plainBuffer.setLength(0)
            }
        }

        var i = 0
        val n = input.length
        while (i < n) {
            when {
                input.startsWith("[[hl]]", startIndex = i, ignoreCase = true) -> {
                    flushPlain()
                    val contentStart = i + 6
                    val closeIdx = input.indexOf("[[/hl]]", startIndex = contentStart, ignoreCase = true)
                    if (closeIdx >= 0) {
                        val inner = stripNestedMarkers(input.substring(contentStart, closeIdx))
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.HIGHLIGHT)
                        }
                        i = closeIdx + 7
                    } else {
                        val inner = stripNestedMarkers(input.substring(contentStart))
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.HIGHLIGHT)
                        }
                        i = n
                    }
                }

                input.startsWith("[[/hl]]", startIndex = i, ignoreCase = true) -> {
                    i += 7
                }

                input.startsWith("==", startIndex = i) &&
                    i + 2 < n &&
                    !input[i + 2].isWhitespace() -> {
                    flushPlain()
                    val contentStart = i + 2
                    val closeIdx = input.indexOf("==", startIndex = contentStart)
                    if (closeIdx >= 0) {
                        val inner = stripNestedMarkers(input.substring(contentStart, closeIdx))
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.HIGHLIGHT)
                        }
                        i = closeIdx + 2
                    } else {
                        val inner = stripNestedMarkers(input.substring(contentStart))
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.HIGHLIGHT)
                        }
                        i = n
                    }
                }

                input.startsWith("**", startIndex = i) &&
                    i + 2 < n &&
                    !input[i + 2].isWhitespace() -> {
                    flushPlain()
                    val contentStart = i + 2
                    val closeIdx = input.indexOf("**", startIndex = contentStart)
                    if (closeIdx >= 0) {
                        val inner = stripNestedMarkers(input.substring(contentStart, closeIdx))
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.BOLD)
                        }
                        i = closeIdx + 2
                    } else {
                        val inner = stripNestedMarkers(input.substring(contentStart))
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.BOLD)
                        }
                        i = n
                    }
                }

                input[i] == '`' && i + 1 < n && input[i + 1] != '`' -> {
                    flushPlain()
                    val contentStart = i + 1
                    val closeIdx = input.indexOf('`', startIndex = contentStart)
                    if (closeIdx >= 0) {
                        val inner = input.substring(contentStart, closeIdx)
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.CODE)
                        }
                        i = closeIdx + 1
                    } else {
                        val inner = input.substring(contentStart)
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.CODE)
                        }
                        i = n
                    }
                }

                input[i] == '*' &&
                    i + 1 < n &&
                    input[i + 1] != '*' &&
                    !input[i + 1].isWhitespace() -> {
                    val contentStart = i + 1
                    val closeIdx = findSingleStarClose(input, contentStart)
                    if (closeIdx > contentStart) {
                        flushPlain()
                        val inner = stripNestedMarkers(input.substring(contentStart, closeIdx))
                        if (inner.isNotEmpty()) {
                            spans += InlineSpan(inner, InlineStyle.ITALIC)
                        }
                        i = closeIdx + 1
                    } else {
                        plainBuffer.append(input[i])
                        i++
                    }
                }

                else -> {
                    plainBuffer.append(input[i])
                    i++
                }
            }
        }

        flushPlain()
        return mergeAdjacentSpans(spans)
    }

    private fun findSingleStarClose(input: String, start: Int): Int {
        var idx = start
        while (idx < input.length) {
            if (input[idx] == '*') {
                val prevNotSpace = idx > start && !input[idx - 1].isWhitespace()
                val notDouble = (idx + 1 >= input.length || input[idx + 1] != '*')
                if (prevNotSpace && notDouble) {
                    return idx
                }
                return -1
            }
            if (input[idx] == '\n') return -1
            idx++
        }
        return -1
    }

    private fun stripNestedMarkers(text: String): String {
        return text
            .replace("[[hl]]", "", ignoreCase = true)
            .replace("[[/hl]]", "", ignoreCase = true)
            .replace("**", "")
            .replace("==", "")
    }

    private fun mergeAdjacentSpans(spans: List<InlineSpan>): List<InlineSpan> {
        if (spans.size <= 1) return spans
        val merged = mutableListOf<InlineSpan>()
        for (span in spans) {
            if (span.text.isEmpty()) continue
            val last = merged.lastOrNull()
            if (last != null && last.style == span.style) {
                merged[merged.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                merged += span
            }
        }
        return merged
    }
}
