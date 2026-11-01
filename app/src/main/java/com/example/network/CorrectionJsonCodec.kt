package com.example.network

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object CorrectionJsonCodec {

    private val PUNCT_SYMBOL_DIACRITIC_REGEX = Regex(
        "[\\p{P}\\p{S}\\u200B-\\u200D\\uFEFF\\u064B-\\u065F\\u0670\\u06C0]"
    )
    private val EDGE_MARKS_REGEX = Regex(
        "^[\\p{P}\\p{S}\\s]+|[\\p{P}\\p{S}\\s]+$"
    )
    private val CLAUSE_MARKS_REGEX = Regex("[.,;:?!،؛؟—–]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val PUNCTUATION_ONLY_REASON_REGEX = Regex(
        "(?i)\\b(punctuation|semicolon|comma|period|full stop|question mark|exclamation|" +
            "apostrophe|hyphen|capitali[sz]|uppercase|lowercase|half-space|zwnj|" +
            "clause boundary|needs a boundary|run-on)\\b"
    )
    private val SUBSTANTIVE_GRAMMAR_REASON_REGEX = Regex(
        "(?i)\\b(tense|verb|preposition|plural|singular|agreement|article|pronoun|idiom|unnatural|awkward|wrong word)\\b"
    )

    private val PERSIAN_COLLOQUIAL_EQUIVALENTS: Set<Pair<String, String>> = setOf(
        "یه" to "یک",
        "خونه" to "خانه",
        "اگه" to "اگر",
        "واسه" to "برای",
        "بذار" to "بگذار",
        "میذارم" to "میگذارم",
        "میام" to "میایم",
        "میرم" to "میروم",
        "میشه" to "میشود",
        "نمیشه" to "نمیشود",
        "داره" to "دارد",
        "نداره" to "ندارد",
        "میگه" to "میگوید",
        "میخوام" to "میخواهم",
        "نمیخوام" to "نمیخواهم"
    )

    fun parseFromModelOutput(
        raw: String,
        transcript: String,
        isSpokenStt: Boolean = false
    ): List<LinguisticCorrection> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start || transcript.isBlank()) return emptyList()

        return try {
            val array = JSONArray(raw.substring(start, end + 1))
            val occupiedRanges = mutableListOf<IntRange>()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val original = item.optSafeString("original").trim()
                    val rawSuggested = item.optSafeString("suggested").trim()
                    val reason = item.optSafeString("reason").trim()
                    if (original.isBlank() || rawSuggested.isBlank()) continue

                    val suggested = sanitizeSuggestedMarks(
                        original = original,
                        suggested = rawSuggested,
                        isSpokenStt = isSpokenStt
                    )
                    if (suggested.isBlank() || original == suggested) continue

                    if (isTrivialOrSttArtifact(original, suggested, reason, isSpokenStt)) {
                        continue
                    }

                    val wordCount = original.split(WHITESPACE_REGEX).size
                    val maxAllowedChars = if (transcript.length <= 60) {
                        transcript.length
                    } else {
                        maxOf(48, (transcript.length * 0.75f).toInt())
                    }
                    if (wordCount > 10 || original.length > maxAllowedChars) continue

                    val rawType = item.optSafeString("type")
                    val type = if (
                        rawType.contains("WORD", ignoreCase = true) ||
                        rawType.contains("IMPROVE", ignoreCase = true) ||
                        rawType.contains("STYLE", ignoreCase = true) ||
                        rawType.contains("NATURAL", ignoreCase = true)
                    ) {
                        CorrectionType.WORD_CHOICE
                    } else {
                        CorrectionType.GRAMMAR
                    }

                    val matchedRanges = findOccurrences(transcript, original)
                    for (range in matchedRanges) {
                        val overlapsExisting = occupiedRanges.any { existing ->
                            range.first <= existing.last && range.last >= existing.first
                        }
                        if (overlapsExisting) continue
                        occupiedRanges.add(range)
                        add(
                            LinguisticCorrection(
                                type = type,
                                original = transcript.substring(range),
                                suggested = suggested,
                                reason = reason
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Strips unrequested leading/trailing punctuation (and injected clause marks in STT) when
     * [original] itself had no punctuation marks.
     */
    private fun sanitizeSuggestedMarks(
        original: String,
        suggested: String,
        isSpokenStt: Boolean
    ): String {
        var result = suggested
        val originalHasEdgeMark = EDGE_MARKS_REGEX.containsMatchIn(original)
        if (!originalHasEdgeMark) {
            result = result.replace(EDGE_MARKS_REGEX, "").trim()
        }
        if (isSpokenStt && !CLAUSE_MARKS_REGEX.containsMatchIn(original)) {
            result = result
                .replace(CLAUSE_MARKS_REGEX, " ")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
        }
        return result
    }

    internal fun isTrivialOrSttArtifact(
        original: String,
        suggested: String,
        reason: String,
        isSpokenStt: Boolean
    ): Boolean {
        // 1. Ignore any difference that is purely marks/punctuation, apostrophes, hyphens,
        //    capitalization, diacritics, Arabic/Persian character variants, or ZWNJ/spacing.
        val origSkeleton = lexicalSkeleton(original)
        val suggSkeleton = lexicalSkeleton(suggested)
        if (origSkeleton.isEmpty() || origSkeleton == suggSkeleton) {
            return true
        }

        val origWords = cleanWords(original)
        val suggWords = cleanWords(suggested)
        if (origWords.isEmpty() || suggWords.isEmpty()) return true

        // 2. Spoken contraction / colloquial equivalences (e.g. "im"/"I'm" <-> "I am", "gonna" <-> "going to")
        if (expandEnglishContractions(origWords) == expandEnglishContractions(suggWords)) {
            return true
        }

        // 3. Spoken Persian colloquial vs formal equivalences & STT dropped final '-ه' copula
        if (isPersianColloquialOrSttCopulaVariation(origWords, suggWords, isSpokenStt)) {
            return true
        }

        // 4. Guard against nitpicky reason strings focused solely on punctuation/boundaries/caps
        if (
            PUNCTUATION_ONLY_REASON_REGEX.containsMatchIn(reason) &&
            !SUBSTANTIVE_GRAMMAR_REASON_REGEX.containsMatchIn(reason)
        ) {
            val sharedCount = origWords.intersect(suggWords.toSet()).size
            val maxSize = maxOf(origWords.size, suggWords.size)
            if (maxSize > 0 && sharedCount.toDouble() / maxSize >= 0.75) {
                return true
            }
        }

        return false
    }

    private fun normalizePersianArabicChars(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                'ي', 'ى' -> append('ی')
                'ك' -> append('ک')
                'ة', 'ۀ' -> append('ه')
                'ؤ' -> append('و')
                'إ', 'أ' -> append('ا')
                else -> append(ch)
            }
        }
    }

    private fun lexicalSkeleton(text: String): String {
        val normalized = normalizePersianArabicChars(text).lowercase()
        return normalized
            .replace(PUNCT_SYMBOL_DIACRITIC_REGEX, "")
            .replace(WHITESPACE_REGEX, "")
    }

    private fun cleanWords(text: String): List<String> {
        val normalized = normalizePersianArabicChars(text).lowercase()
        return normalized
            .replace(PUNCT_SYMBOL_DIACRITIC_REGEX, " ")
            .trim()
            .split(WHITESPACE_REGEX)
            .filter { it.isNotEmpty() }
    }

    private fun expandEnglishContractions(words: List<String>): List<String> = buildList {
        for (word in words) {
            when (word) {
                "im" -> { add("i"); add("am") }
                "ive" -> { add("i"); add("have") }
                "id" -> { add("i"); add("would") }
                "ill" -> { add("i"); add("will") }
                "youre" -> { add("you"); add("are") }
                "youve" -> { add("you"); add("have") }
                "youd" -> { add("you"); add("would") }
                "youll" -> { add("you"); add("will") }
                "hes" -> { add("he"); add("is") }
                "shes" -> { add("she"); add("is") }
                "its" -> { add("it"); add("is") }
                "were" -> add("were")
                "weve" -> { add("we"); add("have") }
                "theyre" -> { add("they"); add("are") }
                "theyve" -> { add("they"); add("have") }
                "dont" -> { add("do"); add("not") }
                "doesnt" -> { add("does"); add("not") }
                "didnt" -> { add("did"); add("not") }
                "cant" -> add("cannot")
                "couldnt" -> { add("could"); add("not") }
                "wouldnt" -> { add("would"); add("not") }
                "shouldnt" -> { add("should"); add("not") }
                "wont" -> { add("will"); add("not") }
                "isnt" -> { add("is"); add("not") }
                "arent" -> { add("are"); add("not") }
                "wasnt" -> { add("was"); add("not") }
                "werent" -> { add("were"); add("not") }
                "havent" -> { add("have"); add("not") }
                "hasnt" -> { add("has"); add("not") }
                "hadnt" -> { add("had"); add("not") }
                "gonna" -> { add("going"); add("to") }
                "wanna" -> { add("want"); add("to") }
                "kinda" -> { add("kind"); add("of") }
                "sorta" -> { add("sort"); add("of") }
                else -> add(word)
            }
        }
    }

    private fun isPersianColloquialOrSttCopulaVariation(
        origWords: List<String>,
        suggWords: List<String>,
        isSpokenStt: Boolean
    ): Boolean {
        if (origWords.size == suggWords.size) {
            var diffCount = 0
            for (i in origWords.indices) {
                val o = origWords[i]
                val s = suggWords[i]
                if (o == s) continue
                diffCount++
                if (diffCount > 1) return false

                val isColloquialPair = PERSIAN_COLLOQUIAL_EQUIVALENTS.contains(o to s) ||
                    PERSIAN_COLLOQUIAL_EQUIVALENTS.contains(s to o)
                // In STT, unstressed colloquial Persian '-ه' copula/ezafe (e.g. "تست" -> "تسته")
                // is routinely dropped by speech recognizers.
                val isSttCopulaDrop = isSpokenStt &&
                    o.any { ch -> ch in '\u0600'..'\u06FF' } &&
                    (s == "${o}ه" || o == "${s}ه")

                if (!isColloquialPair && !isSttCopulaDrop) {
                    return false
                }
            }
            return diffCount == 1
        }

        // In STT, also ignore inserting a standalone Persian copula ("است" / "هست") into a spoken clause
        if (isSpokenStt && suggWords.size == origWords.size + 1) {
            for (i in suggWords.indices) {
                if (suggWords[i] == "است" || suggWords[i] == "هست") {
                    val withoutCopula = suggWords.filterIndexed { idx, _ -> idx != i }
                    if (withoutCopula == origWords) return true
                }
            }
        }

        return false
    }

    private fun findOccurrences(transcript: String, original: String): List<IntRange> {
        val boundaryMatches = runCatching {
            Regex(
                "(?<![\\p{L}\\p{N}_])${Regex.escape(original)}(?![\\p{L}\\p{N}_])",
                RegexOption.IGNORE_CASE
            ).findAll(transcript).map { it.range }.toList()
        }.getOrDefault(emptyList())

        if (boundaryMatches.isNotEmpty()) return boundaryMatches

        return buildList {
            var searchFrom = 0
            while (searchFrom < transcript.length) {
                val found = transcript.indexOf(original, startIndex = searchFrom, ignoreCase = true)
                if (found < 0) break
                val endExclusive = found + original.length
                add(found until endExclusive)
                searchFrom = endExclusive
            }
        }
    }

    fun toJsonArray(corrections: List<LinguisticCorrection>): JSONArray {
        val array = JSONArray()
        for (correction in corrections) {
            array.put(
                JSONObject().apply {
                    put("id", correction.id)
                    put("type", correction.type.name)
                    put("original", correction.original)
                    put("suggested", correction.suggested)
                    put("reason", correction.reason)
                }
            )
        }
        return array
    }

    fun fromStoredJsonArray(array: JSONArray?): List<LinguisticCorrection> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val original = obj.optSafeString("original").trim()
                if (original.isEmpty()) continue
                val typeStr = obj.optSafeString("type").ifBlank { CorrectionType.GRAMMAR.name }
                val type = if (typeStr.equals(CorrectionType.WORD_CHOICE.name, ignoreCase = true)) {
                    CorrectionType.WORD_CHOICE
                } else {
                    CorrectionType.GRAMMAR
                }
                add(
                    LinguisticCorrection(
                        id = obj.optSafeString("id").ifBlank { UUID.randomUUID().toString() },
                        type = type,
                        original = original,
                        suggested = obj.optSafeString("suggested"),
                        reason = obj.optSafeString("reason")
                    )
                )
            }
        }
    }

    private fun JSONObject.optSafeString(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    fun applyCorrectionToText(
        text: String,
        corrections: List<LinguisticCorrection>,
        correctionId: String
    ): Pair<String, List<LinguisticCorrection>> {
        val correction = corrections.firstOrNull { it.id == correctionId }
            ?: return text to corrections
        val start = text.indexOf(correction.original).takeIf { it >= 0 }
            ?: text.indexOf(correction.original, ignoreCase = true)
        if (start < 0) {
            return text to corrections.filterNot { it.id == correction.id }
        }
        val updatedText = buildString {
            append(text, 0, start)
            append(correction.suggested)
            append(text, start + correction.original.length, text.length)
        }
        return updatedText to corrections.filterNot { it.id == correction.id }
    }
}
