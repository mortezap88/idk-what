package com.example.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.ExamQuestionParser
import com.example.data.Flashcard
import com.example.data.FlashcardStatus
import com.example.data.QuestionBankRefinementParser
import com.example.data.Subject
import com.example.settings.AppSettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Coordinates AI tutoring conversations, scenario/question generation, and speech-to-text:
 * - Main TTT model (chat, review evaluation, and grammar/word-choice highlights) -> DeepSeek (`deepseek-chat` via AvalAI)
 *   using a strict two-part response format: `[answer]` and `[linguistic_correction]`.
 * - Voice input transcription ONLY (English & Persian) -> ElevenLabs Scribe v2 (`scribe_v2` via AvalAI `/v1/audio/transcriptions`).
 */
class GeminiRepository(private val settingsRepository: AppSettingsRepository) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val chatListType = Types.newParameterizedType(List::class.java, ChatMessage::class.java)
    private val chatListAdapter = moshi.adapter<List<ChatMessage>>(chatListType)

    private val avalAiClient: AvalAiClient by lazy { AvalAiClient() }

    fun serializeChat(messages: List<ChatMessage>): String {
        val normalized = messages.filterNot { it.isError }.map { msg ->
            val allImgs = msg.allImagesBase64()
            msg.copy(
                audioBase64 = null,
                imageBase64 = null,
                imageBase64List = allImgs
            )
        }
        val fullJson = chatListAdapter.toJson(normalized)
        if (fullJson.length <= MAX_SERIALIZED_CHAT_CHARS) return fullJson

        val trimmed = normalized.toMutableList()
        for (i in trimmed.indices) {
            if (trimmed[i].imageBase64List.isNotEmpty()) {
                val count = trimmed[i].imageBase64List.size
                val note = "[Attached $count image${if (count == 1) "" else "s"}]"
                trimmed[i] = trimmed[i].copy(
                    text = if (trimmed[i].text.isBlank()) note else "${trimmed[i].text}\n$note",
                    imageBase64List = emptyList()
                )
                val candidate = chatListAdapter.toJson(trimmed)
                if (candidate.length <= MAX_SERIALIZED_CHAT_CHARS) return candidate
            }
        }
        return chatListAdapter.toJson(trimmed)
    }

    fun deserializeChat(json: String?): List<ChatMessage> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val loaded = chatListAdapter.fromJson(json) ?: emptyList()
            loaded.map { msg ->
                if (msg.sender == "model") {
                    msg.copy(text = sanitizeLegacyArtifacts(msg.text))
                } else {
                    msg
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses DeepSeek's two-part response (`[answer]` and `[linguistic_correction]`),
     * holding back incomplete hidden section/inline tags during streaming.
     */
    fun parseTutoringResponse(
        raw: String,
        userText: String,
        isSpokenStt: Boolean = false,
        sttCorrections: List<LinguisticCorrection> = emptyList()
    ): TutoringResponse {
        val cleaned = sanitizeLegacyArtifacts(raw).trimStart()
        if (cleaned.isBlank()) {
            return TutoringResponse(answer = "", corrections = sttCorrections)
        }

        // Hold back incomplete opening section tags at the very start of a token stream.
        if (cleaned.startsWith("[") && !cleaned.contains("]")) {
            val lower = cleaned.lowercase()
            if (KNOWN_TAGS.any { it.startsWith(lower) }) {
                return TutoringResponse(answer = "", corrections = sttCorrections)
            }
        }

        val correctionMatch = CORRECTION_SECTION_REGEX.find(cleaned)
        val beforeCorrection = if (correctionMatch != null) {
            cleaned.substring(0, correctionMatch.range.first)
        } else {
            cleaned
        }

        val afterAnswer = ANSWER_TAG_ANYWHERE_REGEX.find(beforeCorrection)
            ?.let { beforeCorrection.substring(it.range.last + 1) }
            ?: beforeCorrection
        val answer = TutorMessageFormatter.extractAnswerBody(afterAnswer)

        val correctionRaw = correctionMatch?.groupValues?.getOrNull(1)?.trim()
        val corrections = if (sttCorrections.isNotEmpty()) {
            sttCorrections
        } else if (!correctionRaw.isNullOrBlank()) {
            CorrectionJsonCodec.parseFromModelOutput(
                raw = correctionRaw,
                transcript = userText,
                isSpokenStt = isSpokenStt
            )
        } else {
            emptyList()
        }

        return TutoringResponse(
            answer = answer,
            corrections = corrections
        )
    }

    private fun sanitizeLegacyArtifacts(text: String): String {
        return text.replaceFirst(LEGACY_NULL_PREFIX_REGEX, "")
    }

    suspend fun chatTurn(
        history: List<ChatMessage>,
        systemInstruction: String? = null,
        responseMode: TutorResponseMode = TutorResponseMode.FREESTYLE,
        streamResponses: Boolean = false,
        onUserTranscript: (String) -> Unit = {},
        onPartialResponse: (TutoringResponse) -> Unit = {}
    ): Result<TutoringTurnResult> = withContext(Dispatchers.IO) {
        val cleanHistory = history.filterNot { it.isError }
        val latestUserMessage = cleanHistory.lastOrNull { it.sender == "user" }
        val latestAudioBase64 = latestUserMessage?.audioBase64?.takeIf { it.isNotBlank() }

        val spokenTranscript: String? = if (latestAudioBase64 != null) {
            val sttResult = avalAiClient.transcribeBase64Audio(
                audioBase64 = latestAudioBase64,
                mimeType = null,
                settingsRepository = settingsRepository
            )
            if (sttResult.isFailure) {
                return@withContext Result.failure(
                    sttResult.exceptionOrNull()
                        ?: IOException("Voice transcription failed.")
                )
            }
            val transcript = sttResult.getOrNull()?.trim().orEmpty()
            if (transcript.isBlank() ||
                transcript.contains(AvalAiClient.SILENCE_SENTINEL, ignoreCase = true)
            ) {
                return@withContext Result.failure(
                    IOException("No speech was detected in the recording. Please try speaking again.")
                )
            }
            // Immediately surface the verbatim transcript in the chat UI after Request 1.
            onUserTranscript(transcript)
            transcript
        } else {
            null
        }

        val effectiveUserText = spokenTranscript ?: latestUserMessage?.text.orEmpty()
        val isSpokenInput = spokenTranscript != null

        val messages = buildList {
            add(
                AvalAiClient.Message(
                    role = "system",
                    content = tutoringFormattingInstruction(
                        systemInstruction = systemInstruction,
                        isSpokenInput = isSpokenInput,
                        responseMode = responseMode
                    )
                )
            )
            for (msg in cleanHistory) {
                val role = if (msg.sender == "user") "user" else "assistant"
                val attachedImages = if (msg.sender == "user") msg.allImagesBase64() else emptyList()
                val contentText = when {
                    msg.id == latestUserMessage?.id && spokenTranscript != null -> spokenTranscript
                    msg.sender == "model" && msg.text.isNotBlank() ->
                        TutorMessageFormatter.toPlainSpeakableText(sanitizeLegacyArtifacts(msg.text))
                            .ifBlank { sanitizeLegacyArtifacts(msg.text) }
                    msg.text.isNotBlank() -> sanitizeLegacyArtifacts(msg.text)
                    attachedImages.isNotEmpty() ->
                        "Examine the attached image(s) carefully (extracting all visible text via OCR and analyzing any diagrams) and help me with this material."
                    else -> continue
                }
                add(
                    AvalAiClient.Message(
                        role = role,
                        content = contentText,
                        imageBase64List = attachedImages
                    )
                )
            }
        }

        val completionResult = avalAiClient.completeWithSettings(
            messages = messages,
            settingsRepository = settingsRepository,
            temperature = 0.7,
            streamResponses = streamResponses,
            onPartialText = { partialModelText ->
                onPartialResponse(
                    parseTutoringResponse(
                        raw = partialModelText,
                        userText = effectiveUserText,
                        isSpokenStt = isSpokenInput
                    )
                )
            }
        )

        completionResult.map { finalModelText ->
            val parsed = parseTutoringResponse(
                raw = finalModelText,
                userText = effectiveUserText,
                isSpokenStt = isSpokenInput
            )
            TutoringTurnResult(
                answer = parsed.answer,
                corrections = parsed.corrections,
                userTranscript = spokenTranscript
            )
        }
    }

    internal fun tutoringFormattingInstruction(
        systemInstruction: String?,
        isSpokenInput: Boolean,
        responseMode: TutorResponseMode
    ): String = buildString {
        if (!systemInstruction.isNullOrBlank()) {
            append(systemInstruction.trim())
            append("\n\n")
        }

        val answerFormattingRules = when (responseMode) {
            TutorResponseMode.INITIAL_REVIEW_EVALUATION -> """
                HIDDEN UI EVALUATION PROTOCOL FOR [answer] (INITIAL REVIEW RESPONSE):
                The app parses hidden control tags inside [answer] to draw a colored vertical line on the screen edge (Green, Red, Blue) and align the reply. The user never sees the raw tags.
                1. FIRST LINE OF [answer] — MAJORITY LANGUAGE ORIENTATION TAG (MANDATORY):
                   - Output `[lang:fa]` as the very first token inside `[answer]` if the majority of your reply is in Persian (even if it contains English technical terms/acronyms), OR `[lang:en]` if the majority of your reply is in English (even if it quotes a Persian word).
                2. EVALUATION BLOCKS — Immediately after `[lang:fa]` or `[lang:en]`, output ONLY the applicable blocks from `[green]...[/green]`, `[red]...[/red]`, and `[blue]...[/blue]` (in that order), with NO text outside those tags:
                   - `[green]...[/green]` — State directly which specific part, concept, or phrasing the student got right.
                     * OMIT `[green]...[/green]` COMPLETELY if the student completely missed the answer or got nothing right.
                   - `[red]...[/red]` — State directly what needs to be phrased better, refined, or what was missed/wrong (if they missed the answer completely, omit `[green]` and state the core answer directly inside `[red]`).
                     * OMIT `[red]...[/red]` COMPLETELY if the student's answer was accurate and natural with nothing meaningful to fix.
                   - `[blue]...[/blue]` — STRICTLY OPTIONAL foundational context, underlying "why"/mechanism, or broader mental model slightly outside the literal answer that deepens understanding of the whole concept.
                     * OMIT `[blue]...[/blue]` by default unless you have genuine, non-repetitive insight (especially valuable when `[red]` is omitted because the student was completely right).
                     * NEVER use `[blue]` for memorization tips, mnemonics, or summaries/formulas repeating what was already covered in `[green]` or `[red]` (NEVER write things like "برای حفظ: ..." or "For memorization: ...").
            """.trimIndent()

            TutorResponseMode.FREESTYLE -> """
                FREESTYLE RESPONSE PROTOCOL FOR [answer]:
                - Output `[lang:fa]` as the very first token inside `[answer]` if the majority of your reply is in Persian, OR `[lang:en]` if the majority of your reply is in English.
                - Answer directly in clean, natural prose (and `- ` bullet points only when listing distinct items helps clarity).
                - Do NOT use `[green]`, `[red]`, `[blue]`, or `🟢`/`🔴`/`🔵` blocks in freestyle mode.
            """.trimIndent()
        }

        val inputSourceRule = if (isSpokenInput) {
            "The user's latest message comes from automatic Speech-to-Text (STT) of a single spoken voice recording: NEVER flag missing punctuation, capitalization, apostrophes, contractions, spoken fillers (\"uh\", \"um\", \"you know\"), or unseparated clauses."
        } else {
            "Ignore casual capitalization, minor punctuation omissions, and normal contractions."
        }

        append(
            """
            Respond in EXACTLY TWO parts — [answer] followed by [linguistic_correction] — with no extra text before or after:
            [answer]
            [lang:fa] or [lang:en]
            [Your main response following the protocol below]
            [linguistic_correction]
            [JSON array of linguistic corrections for the user's latest message, or []]

            $answerFormattingRules

            LINGUISTIC ANALYSIS PROTOCOL FOR [linguistic_correction]:
            - If the user's latest message is in English and contains clear grammar mistakes (wrong verb tense, wrong subject-verb agreement, wrong preposition, broken word order) or noticeably unnatural word choices, output a JSON array:
              [{"type":"GRAMMAR"|"WORD_CHOICE","original":"exact contiguous substring from the user's latest message","suggested":"corrected or more natural English phrasing","reason":"concise explanation in English"}]
            - $inputSourceRule
            - Keep each "original" short (1 to 6 words matching the exact substring in the user's latest message).
            - If the user's latest message is in Persian (Farsi), or if their English is already natural and accurate, output `[]`.

            DIRECTNESS, INLINE RICH TEXT & ZERO-FLUFF STYLE RULES (STRICT):
            - NO META LABELS OR LEADING QUOTES: Never start any block or paragraph with labels like "The complete answer:", "Complete answer:", "پاسخ کامل:", "برای حفظ:", "نکته:", "Correct:", or wrap the answer in quotation marks ("..." / «...»). Jump straight into the substance on word one — the colored line already communicates the block's role.
            - DO NOT SCOLD "I DON'T KNOW": If the student says they don't know or leaves part blank, do NOT lecture them that saying "نمی‌دانم" / "I don't know" leaves the answer incomplete — simply state the missing concept directly inside `[red]`.
            - ZERO COMPLIMENTARY FILLER: Never write praise, pleasantries, or filler such as "Great job!", "Well done!", "Nice try!", "Correct!", "Excellent question!", "آفرین!", "عالی بود!", "خیلی خوب گفتی!", or "سوال خوبی پرسیدی".
            - INLINE EMPHASIS (the app renders these cleanly without showing raw symbols):
              * Use `==key phrase==` sparingly to visually highlight the most important target expression, corrected phrase, or decisive keyword (1–4 words per span).
              * Use `**bold**` for secondary key concepts or structural anchors.
              * Use `` `exact token` `` only for code, formulas, or literal symbols.
            - Focus exclusively on pedagogical evaluation and substance. Keep every block concise, sharp, and immediately scannable.
            """.trimIndent()
        )
    }

    suspend fun transcribeAudio(audioBase64: String): Result<String> =
        avalAiClient.transcribeBase64Audio(
            audioBase64 = audioBase64,
            mimeType = null,
            settingsRepository = settingsRepository
        )

    /**
     * Performs faithful OCR text extraction from a local page/photo image using DeepSeek vision
     * (with automatic AvalAI vision fallback if the primary text endpoint rejects `image_url`).
     */
    suspend fun extractTextFromImage(imageFile: File): Result<String> = withContext(Dispatchers.IO) {
        if (!imageFile.exists() || !imageFile.isFile) {
            return@withContext Result.failure(IOException("Image file does not exist."))
        }
        val imageBase64 = fileToBase64(imageFile, maxDimension = 1600, quality = 85)
            ?: return@withContext Result.failure(IOException("Could not decode image file for OCR."))

        val system = "You are a high-precision multilingual OCR and textbook extraction engine (supporting Persian/Farsi, English, scientific notation, formulas, tables, and diagram labels). Transcribe ALL readable text from the image faithfully in natural reading order with zero commentary, zero intro, and zero markdown fences."
        val prompt = """
            Extract ALL visible text from this image accurately as clean plain text (preserving paragraphs, headings, bullet points, formulas, table rows, and figure/diagram labels in reading order).
            - Keep the exact original language(s) (e.g., Persian, English, numbers, symbols).
            - Do NOT summarize, translate, or add explanations.
            - Output ONLY the extracted text.
        """.trimIndent()

        val messages = listOf(
            AvalAiClient.Message(role = "system", content = system),
            AvalAiClient.Message(
                role = "user",
                content = prompt,
                imageBase64List = listOf(imageBase64)
            )
        )

        avalAiClient.completeWithSettings(
            messages = messages,
            settingsRepository = settingsRepository,
            temperature = 0.1,
            reasoningEffort = AvalAiClient.LOCKED_REASONING_EFFORT,
            streamResponses = false
        ).map { raw ->
            val cleaned = CODE_FENCE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.trim() ?: raw.trim()
            cleaned.trim()
        }
    }

    suspend fun generateLinguisticScenario(history: List<ChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("You are continuing an ongoing language tutoring conversation.\n")
            append("In the previous session, you and the student had the following conversation:\n")
            history.forEach { msg ->
                val cleanText = if (msg.sender == "model") {
                    TutorMessageFormatter.toPlainSpeakableText(msg.text).ifBlank { msg.text }
                } else {
                    msg.text.ifBlank {
                        if (msg.allImagesBase64().isNotEmpty()) "[Attached ${msg.allImagesBase64().size} image(s)]" else ""
                    }
                }
                if (cleanText.isNotBlank()) {
                    append("${if (msg.sender == "user") "Student" else "Tutor"}: $cleanText\n")
                }
            }
            append("\nTASK:\n")
            append("Infer the key linguistic element from this conversation, then create one realistic everyday scenario that naturally calls for the student to express that idea.\n")
            append("Do not name, quote, explain, or directly demonstrate the target word, phrase, or grammatical structure. Give indirect situational clues that make the intended expression reasonably guessable, but leave meaningful room for the student to retrieve it and choose their own wording. Aim for a moderate hint: the context should point toward the target without making the answer obvious or giving it away.\n")
            append("Keep the scenario concise, about 50-70 words. Include only the context needed to make the situation clear and natural; avoid extra background or multiple questions.\n")
            append("MANDATORY: End your prompt with the exact question: 'What would you say?'")
        }

        val system = "You are an expert language coach creating concise contextual speaking scenarios from prior conversation. Infer the target linguistic element, but never state, quote, explain, or model it in the scenario. Use indirect, moderate clues so the learner must retrieve the expression and choose their wording, without making the answer obvious. Keep the scenario to 50-70 words and always end with: 'What would you say?'"

        generatePromptAnswer(prompt, system)
    }

    suspend fun generateTechnicalQuestion(
        history: List<ChatMessage>,
        subjectName: String? = null,
        textbook: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            if (!subjectName.isNullOrBlank()) {
                append("Subject: $subjectName\n")
            }
            if (!textbook.isNullOrBlank()) {
                append("Textbook (Answer Reference):\n\"\"\"\n${textbook.trim()}\n\"\"\"\n\n")
            }
            append("You are continuing an ongoing technical/subject mentoring dialogue.\n")
            append("In the previous session, you and the student discussed:\n")
            history.forEach { msg ->
                val cleanText = if (msg.sender == "model") {
                    TutorMessageFormatter.toPlainSpeakableText(msg.text).ifBlank { msg.text }
                } else {
                    msg.text.ifBlank {
                        if (msg.allImagesBase64().isNotEmpty()) "[Attached ${msg.allImagesBase64().size} image(s)]" else ""
                    }
                }
                if (cleanText.isNotBlank()) {
                    append("${if (msg.sender == "user") "Student" else "Mentor"}: $cleanText\n")
                }
            }
            append("\nTASK:\n")
            append("Extract the specific question, mechanism, concept, or technique focused on in this discussion and ask ONE clear, pointed question to test the student's recall and understanding.\n")
            append("Write the question in the primary language used in the discussion (e.g., Persian if discussed in Persian, English if discussed in English). Output ONLY the question text with no labels or preamble.")
        }

        val system = "You are a direct subject and technical mentor formulating a pointed review question from a prior study conversation."

        generatePromptAnswer(prompt, system)
    }

    data class ExistingSubjectQuestion(
        val question: String,
        val status: String = FlashcardStatus.ACTIVE,
        val answer: String? = null
    )

    sealed interface SingleQuestionGenerationOutcome {
        data class Generated(
            val parsed: ExamQuestionParser.ParsedQuestion
        ) : SingleQuestionGenerationOutcome

        data class AlreadyExists(
            val matchedQuestion: String? = null,
            val matchedStatus: String? = null
        ) : SingleQuestionGenerationOutcome
    }

    suspend fun generateSingleSubjectQuestionFromConversation(
        subjectName: String,
        textbook: String = "",
        extraKnowledge: String = "",
        existingQuestions: List<ExistingSubjectQuestion> = emptyList(),
        history: List<ChatMessage>,
        onReasoningProgress: (Int) -> Unit = {}
    ): Result<SingleQuestionGenerationOutcome> = withContext(Dispatchers.IO) {
        val prompt = buildSingleSubjectQuestionFromConversationPrompt(
            subjectName = subjectName,
            textbook = textbook,
            extraKnowledge = extraKnowledge,
            existingQuestions = existingQuestions,
            history = history
        )
        val system = buildString {
            append("You are an expert academic and technical question designer and deduplication analyst. ")
            append("Carefully analyze BOTH everything discussed in the conversation AND the ENTIRE existing question list (Active, Learned, and Dumb). ")
            append("If the question/concept the learner wants to generate already exists or is semantically repetitive of ANY question in the existing list (even if worded differently), DO NOT generate a duplicate — output ONLY `[question_already_exists]` followed by `[matched_q] ...existing question text... [/matched_q]`. ")
            append("Otherwise, formulate ONE precise, self-contained study question and its complete, authoritative answer inside `[q_item][q]...[/q][a]...[/a][/q_item]` in the primary language of the Textbook/conversation, with zero preamble or extra commentary.")
        }
        val messages = listOf(
            AvalAiClient.Message(role = "system", content = system),
            AvalAiClient.Message(role = "user", content = prompt)
        )
        avalAiClient.completeWithSettings(
            messages = messages,
            settingsRepository = settingsRepository,
            temperature = null,
            reasoningEffort = AvalAiClient.HIGH_REASONING_EFFORT,
            maxTokens = null,
            streamResponses = true,
            onReasoningProgress = onReasoningProgress
        ).mapCatching { raw ->
            parseSingleQuestionGenerationOutcome(raw, existingQuestions)
        }
    }

    suspend fun generateExtractedQuestionFromReview(
        sourceCard: Flashcard,
        reviewConversation: List<ChatMessage>,
        subject: Subject?,
        existingQuestions: List<ExistingSubjectQuestion> = emptyList(),
        extractionConversation: List<ChatMessage>,
        onReasoningProgress: (Int) -> Unit = {}
    ): Result<SingleQuestionGenerationOutcome> = withContext(Dispatchers.IO) {
        val prompt = buildExtractQuestionFromReviewGenerationPrompt(
            sourceCard = sourceCard,
            reviewConversation = reviewConversation,
            subject = subject,
            existingQuestions = existingQuestions,
            extractionConversation = extractionConversation
        )
        val system = buildString {
            append("You are an expert academic and technical question designer and deduplication analyst. ")
            append("Carefully analyze the review item's conversation, the Subject's TEXTBOOK and EXTRA KNOWLEDGE (when provided), the extraction conversation, AND the ENTIRE existing question list (Active, Learned, and Dumb). ")
            append("If the question/concept the learner wants to extract already exists or is semantically repetitive of ANY question in the existing list (even if worded differently), DO NOT generate a duplicate — output ONLY `[question_already_exists]` followed by `[matched_q] ...existing question text... [/matched_q]`. ")
            append("Otherwise, formulate ONE precise, self-contained study question and its complete, authoritative answer inside `[q_item][q]...[/q][a]...[/a][/q_item]` in the primary language of the review item / conversation, with zero preamble or extra commentary.")
        }
        val messages = listOf(
            AvalAiClient.Message(role = "system", content = system),
            AvalAiClient.Message(role = "user", content = prompt)
        )
        avalAiClient.completeWithSettings(
            messages = messages,
            settingsRepository = settingsRepository,
            temperature = null,
            reasoningEffort = AvalAiClient.HIGH_REASONING_EFFORT,
            maxTokens = null,
            streamResponses = true,
            onReasoningProgress = onReasoningProgress
        ).mapCatching { raw ->
            parseSingleQuestionGenerationOutcome(raw, existingQuestions)
        }
    }

    suspend fun summarizeSubjectQuestionTitle(
        subjectName: String,
        textbook: String = "",
        history: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("Subject: $subjectName\n")
            if (textbook.isNotBlank()) {
                append("TEXTBOOK (AUTHORITATIVE REFERENCE):\n\"\"\"\n")
                append(textbook.trim())
                append("\n\"\"\"\n\n")
            }
            append("Conversation:\n")
            history.forEach { msg ->
                val cleanText = if (msg.sender == "model") {
                    TutorMessageFormatter.toPlainSpeakableText(msg.text).ifBlank { msg.text }
                } else {
                    msg.text.ifBlank {
                        if (msg.allImagesBase64().isNotEmpty()) "[Attached ${msg.allImagesBase64().size} image(s)]" else ""
                    }
                }
                if (cleanText.isNotBlank()) {
                    append("${if (msg.sender == "user") "User" else "Tutor"}: $cleanText\n")
                }
            }
            append("\nTASK:\n")
            append("Write ONLY the single specific study question (one concise sentence, max 140 characters, no quotes, no 'Q:' prefix) grounded in the Textbook that captures what the user wanted to add from this conversation. Use the primary language of the conversation/textbook.")
        }
        val system = "You extract a single crisp study question grounded in the subject's textbook from a conversation, with zero preamble or formatting."
        generatePromptAnswer(prompt, system, temperature = 0.2).map { raw ->
            raw.lineSequence()
                .map { it.trim().removeSurrounding("\"").removeSurrounding("«", "»") }
                .firstOrNull { it.isNotBlank() }
                ?.replace(Regex("^(?i)(?:q|question|سوال)\\s*[:：-]\\s*"), "")
                ?.trim()
                .orEmpty()
        }
    }

    data class SubjectQuestionGenerationOutcome(
        val questions: List<ExamQuestionParser.ParsedQuestion>,
        val wasInterruptedMidStream: Boolean = false,
        val interruptionMessage: String? = null
    )

    suspend fun generateSubjectQuestions(
        subjectName: String,
        textbook: String,
        extraKnowledge: String,
        existingQuestions: List<ExistingSubjectQuestion>,
        onReasoningProgress: (Int) -> Unit = {},
        onQuestionStreamed: (ExamQuestionParser.ParsedQuestion) -> Unit = {}
    ): Result<SubjectQuestionGenerationOutcome> = withContext(Dispatchers.IO) {
        val cleanTextbook = textbook.trim()
        if (cleanTextbook.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Textbook is empty. Add answer reference material to Textbook first.")
            )
        }

        val prompt = buildSubjectQuestionGenerationPrompt(
            subjectName = subjectName,
            textbook = cleanTextbook,
            extraKnowledge = extraKnowledge,
            existingQuestions = existingQuestions
        )

        val system = buildString {
            append("You are an exhaustive, high-rigor academic and technical question designer. ")
            append("You examine every paragraph, concept, mechanism, distinction, and detail of the provided TEXTBOOK and extract EVERY meaningful, non-redundant question possible. ")
            append("You strictly cross-check against all existing Active, Learned, and Dumb/Rejected questions so you NEVER repeat an already-covered concept or recreate rejected/useless questions. ")
            append("In your internal thinking phase, map out uncovered concepts and check for overlaps concisely as compact notes rather than drafting full verbatim Q&A prose twice — emit the full questions and answers directly inside `[q_item]` frames so each question streams immediately, or output `[no_more_questions]` when nothing genuinely new remains.")
        }

        val messages = listOf(
            AvalAiClient.Message(role = "system", content = system),
            AvalAiClient.Message(role = "user", content = prompt)
        )

        val collector = IncrementalQuestionCollector(
            existingQuestions = existingQuestions.map { it.question },
            onNewQuestion = onQuestionStreamed
        )

        val streamResult = avalAiClient.completeWithSettings(
            messages = messages,
            settingsRepository = settingsRepository,
            temperature = null,
            reasoningEffort = AvalAiClient.HIGH_REASONING_EFFORT,
            maxTokens = null,
            streamResponses = true,
            onReasoningProgress = onReasoningProgress,
            onPartialText = { partialText ->
                collector.onPartialStream(partialText)
            }
        )

        streamResult.fold(
            onSuccess = { finalRaw ->
                collector.finish(finalRaw)
                Result.success(
                    SubjectQuestionGenerationOutcome(
                        questions = collector.emittedQuestions,
                        wasInterruptedMidStream = false
                    )
                )
            },
            onFailure = { error ->
                val partialErr = error as? AvalAiClient.PartialStreamInterruptedException
                if (partialErr != null) {
                    collector.finish(partialErr.partialContent)
                    if (collector.emittedQuestions.isNotEmpty()) {
                        return@fold Result.success(
                            SubjectQuestionGenerationOutcome(
                                questions = collector.emittedQuestions,
                                wasInterruptedMidStream = true,
                                interruptionMessage = partialErr.localizedMessage
                            )
                        )
                    }
                }
                if (collector.emittedQuestions.isNotEmpty()) {
                    Result.success(
                        SubjectQuestionGenerationOutcome(
                            questions = collector.emittedQuestions,
                            wasInterruptedMidStream = true,
                            interruptionMessage = error.localizedMessage
                        )
                    )
                } else {
                    Result.failure(error)
                }
            }
        )
    }

    data class SubjectQuestionRefinementOutcome(
        val operations: List<QuestionBankRefinementParser.RefinementOperation>,
        val wasInterruptedMidStream: Boolean = false,
        val interruptionMessage: String? = null
    )

    /**
     * Step 2 of the two-step question bank pipeline: sends a separate second API request that
     * audits the entire current question bank against the Textbook to:
     *  1. Add missing / complementary questions (supporting diverse ways of thinking AND granular
     *     or seemingly "silly"/literal exam-testable details).
     *  2. Fix and edit any active questions that don't make sense, are ambiguous, illogical, or
     *     have incomplete/inaccurate answers.
     *  3. Combine active questions ONLY when merging genuinely makes them clearer and more logical
     *     (preserving high overall question volume).
     */
    suspend fun complementAndRefineSubjectQuestions(
        subjectName: String,
        textbook: String,
        extraKnowledge: String,
        currentCards: List<Flashcard>,
        onReasoningProgress: (Int) -> Unit = {},
        onOperationStreamed: (QuestionBankRefinementParser.RefinementOperation) -> Unit = {}
    ): Result<SubjectQuestionRefinementOutcome> = withContext(Dispatchers.IO) {
        val cleanTextbook = textbook.trim()
        if (cleanTextbook.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Textbook is empty. Add answer reference material to Textbook first.")
            )
        }

        val prompt = buildSubjectQuestionRefinementPrompt(
            subjectName = subjectName,
            textbook = cleanTextbook,
            extraKnowledge = extraKnowledge,
            currentCards = currentCards
        )

        val system = buildString {
            append("You are a master exam strategist, logician, and second-pass question bank auditor. ")
            append("You audit an existing question bank against the authoritative TEXTBOOK to make the bank rock-solid, logical, practical, and exam-complete. ")
            append("You strongly favor MORE questions in general, support diverse ways of thinking (conceptual, practical/applied, cause-effect, reverse-lookup, comparisons, edge cases, AND granular/literal or seemingly 'silly' textbook facts that exams still test), ")
            append("fix any active question that doesn't make sense or has a weak answer, and combine active questions ONLY when merging genuinely creates a clearer, stronger question. ")
            append("Stream every action cleanly inside `[op_item]` blocks (or output `[no_refinements_needed]` if already 100% solid and complete) with zero outside commentary.")
        }

        val messages = listOf(
            AvalAiClient.Message(role = "system", content = system),
            AvalAiClient.Message(role = "user", content = prompt)
        )

        val activeIds = currentCards.filter { it.isActive }.map { it.id }.toSet()
        val collector = QuestionBankRefinementParser.IncrementalRefinementCollector(
            activeCardIds = activeIds,
            existingQuestions = currentCards.map { it.word },
            onOperation = onOperationStreamed
        )

        val streamResult = avalAiClient.completeWithSettings(
            messages = messages,
            settingsRepository = settingsRepository,
            temperature = null,
            reasoningEffort = AvalAiClient.HIGH_REASONING_EFFORT,
            maxTokens = null,
            streamResponses = true,
            onReasoningProgress = onReasoningProgress,
            onPartialText = { partialText ->
                collector.onPartialStream(partialText)
            }
        )

        streamResult.fold(
            onSuccess = { finalRaw ->
                collector.finish(finalRaw)
                Result.success(
                    SubjectQuestionRefinementOutcome(
                        operations = collector.emittedOperations,
                        wasInterruptedMidStream = false
                    )
                )
            },
            onFailure = { error ->
                val partialErr = error as? AvalAiClient.PartialStreamInterruptedException
                if (partialErr != null) {
                    collector.finish(partialErr.partialContent)
                    if (collector.emittedOperations.isNotEmpty()) {
                        return@fold Result.success(
                            SubjectQuestionRefinementOutcome(
                                operations = collector.emittedOperations,
                                wasInterruptedMidStream = true,
                                interruptionMessage = partialErr.localizedMessage
                            )
                        )
                    }
                }
                if (collector.emittedOperations.isNotEmpty()) {
                    Result.success(
                        SubjectQuestionRefinementOutcome(
                            operations = collector.emittedOperations,
                            wasInterruptedMidStream = true,
                            interruptionMessage = error.localizedMessage
                        )
                    )
                } else {
                    Result.failure(error)
                }
            }
        )
    }

    private suspend fun generatePromptAnswer(
        prompt: String,
        system: String,
        temperature: Double? = 0.7,
        reasoningEffort: String = AvalAiClient.LOCKED_REASONING_EFFORT
    ): Result<String> {
        val messages = listOf(
            AvalAiClient.Message(role = "system", content = system),
            AvalAiClient.Message(role = "user", content = prompt)
        )
        return avalAiClient.completeWithSettings(
            messages = messages,
            settingsRepository = settingsRepository,
            temperature = temperature,
            reasoningEffort = reasoningEffort,
            streamResponses = false
        )
    }

    /**
     * Stateful O(1)-per-chunk incremental extractor that emits each newly completed
     * `[q_item]` (or fallback `[q]...[/a]` / JSON object) exactly once as SSE chunks arrive.
     */
    internal class IncrementalQuestionCollector(
        existingQuestions: List<String>,
        private val onNewQuestion: (ExamQuestionParser.ParsedQuestion) -> Unit
    ) {
        private val seenKeys = existingQuestions
            .map { normalizeQuestionKey(it) }
            .filter { it.isNotEmpty() }
            .toHashSet()

        private val _emitted = mutableListOf<ExamQuestionParser.ParsedQuestion>()
        val emittedQuestions: List<ExamQuestionParser.ParsedQuestion>
            get() = _emitted.toList()

        private var scanCursor = 0

        @Synchronized
        fun onPartialStream(accumulated: String) {
            if (accumulated.length <= scanCursor) return
            val window = accumulated.substring(scanCursor)

            val regex = when {
                accumulated.contains("[q_item]", ignoreCase = true) -> FRAMED_QUESTION_ITEM_REGEX
                accumulated.contains("[q]", ignoreCase = true) -> STANDALONE_QA_TAG_REGEX
                else -> INDIVIDUAL_JSON_OBJECT_REGEX
            }

            var lastConsumedEnd = 0
            for (match in regex.findAll(window)) {
                val parsed = parseSingleMatch(match, regex)
                if (parsed != null) {
                    emitIfNew(parsed)
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
            val allParsed = parseGeneratedQuestions(finalRaw)
            for (item in allParsed) {
                emitIfNew(item)
            }
        }

        private fun emitIfNew(item: ExamQuestionParser.ParsedQuestion) {
            val cleanQ = cleanQuestionText(item.question)
            val cleanA = item.answer.trim()
            if (cleanQ.isEmpty() || cleanA.isEmpty()) return
            val key = normalizeQuestionKey(cleanQ)
            if (key.isNotEmpty() && seenKeys.add(key)) {
                val normalized = ExamQuestionParser.ParsedQuestion(question = cleanQ, answer = cleanA)
                _emitted.add(normalized)
                onNewQuestion(normalized)
            }
        }

        private fun parseSingleMatch(
            match: MatchResult,
            regex: Regex
        ): ExamQuestionParser.ParsedQuestion? {
            return if (regex === INDIVIDUAL_JSON_OBJECT_REGEX) {
                runCatching {
                    val obj = JSONObject(match.value)
                    val q = obj.optString("question", "").trim()
                    val a = obj.optString("answer", "").trim()
                    if (q.isNotEmpty() && a.isNotEmpty()) {
                        ExamQuestionParser.ParsedQuestion(question = q, answer = a)
                    } else {
                        null
                    }
                }.getOrNull()
            } else {
                val q = match.groupValues.getOrNull(1)?.trim().orEmpty()
                val a = match.groupValues.getOrNull(2)?.trim().orEmpty()
                if (q.isNotEmpty() && a.isNotEmpty()) {
                    ExamQuestionParser.ParsedQuestion(question = q, answer = a)
                } else {
                    null
                }
            }
        }
    }

    companion object {
        const val NO_MORE_QUESTIONS_TAG = "[no_more_questions]"

        private val KNOWN_TAGS = listOf(
            "[answer]",
            "[/answer]",
            "[lang:fa]",
            "[lang:en]",
            "[lang:persian]",
            "[lang:english]",
            "[linguistic_correction]",
            "[correction]",
            "[green]",
            "[/green]",
            "[red]",
            "[/red]",
            "[blue]",
            "[/blue]",
            "[[hl]]",
            "[[/hl]]"
        )

        private val ANSWER_TAG_ANYWHERE_REGEX =
            Regex("(?is)\\[answer\\]\\s*")

        private val CORRECTION_SECTION_REGEX =
            Regex("(?is)\\[(?:linguistic_)?correction\\]\\s*(.*)$")

        private val LEGACY_NULL_PREFIX_REGEX =
            Regex("(?is)^(?:u?l{0,2})?(?:null)+\\s*(?:\\[answer\\]\\s*)?")

        private val CODE_FENCE_REGEX =
            Regex("(?is)```(?:json|text)?\\s*(.*?)\\s*```")

        private val FRAMED_QUESTION_ITEM_REGEX =
            Regex("(?is)\\[q_item\\]\\s*\\[q\\]\\s*(.*?)\\s*\\[/q\\]\\s*\\[a\\]\\s*(.*?)\\s*\\[/a\\]\\s*\\[/q_item\\]")

        private val STANDALONE_QA_TAG_REGEX =
            Regex("(?is)\\[q\\]\\s*(.*?)\\s*\\[/q\\]\\s*\\[a\\]\\s*(.*?)\\s*\\[/a\\]")

        private val INDIVIDUAL_JSON_OBJECT_REGEX =
            Regex("""\{[^{}]*"question"\s*:\s*"(?:\\.|[^"\\])*"[^{}]*"answer"\s*:\s*"(?:\\.|[^"\\])*"[^{}]*\}""")

        /**
         * Builds the exhaustive generation prompt with full awareness of Active, Learned, and Dumb questions,
         * instructing the model to stream each question in its own self-contained `[q_item]` frame.
         */
        fun buildSubjectQuestionGenerationPrompt(
            subjectName: String,
            textbook: String,
            extraKnowledge: String,
            existingQuestions: List<ExistingSubjectQuestion>
        ): String = buildString {
            append("Subject: $subjectName\n\n")
            append("TEXTBOOK (MAIN AUTHORITATIVE SOURCE — GENERATE QUESTIONS STRICTLY FROM THIS):\n\"\"\"\n")
            append(textbook.trim())
            append("\n\"\"\"\n\n")

            if (extraKnowledge.isNotBlank()) {
                append("EXTRA KNOWLEDGE (BACKING CONTEXT ONLY — DO NOT TEST OR REQUIRE IN ANSWERS):\n\"\"\"\n")
                append(extraKnowledge.trim())
                append("\n\"\"\"\n")
                append("Rule: Extra Knowledge is strictly background context. NEVER generate a question whose answer comes only from Extra Knowledge, and NEVER require Extra Knowledge details in the reference answers.\n\n")
            }

            val cleanExisting = existingQuestions
                .map { it.copy(question = it.question.trim(), status = FlashcardStatus.normalize(it.status)) }
                .filter { it.question.isNotEmpty() }

            if (cleanExisting.isEmpty()) {
                append(
                    """
                    EXISTING QUESTION LIST:
                    (Empty — this is the first pass over the Textbook.)

                    EXHAUSTIVE GENERATION TASK (FIRST PASS):
                    Examine EVERY paragraph, section, definition, mechanism, cause-and-effect chain, comparison, condition, exception, formula, classification, and granular detail in the TEXTBOOK above in order from start to finish.
                    - Prefer MORE questions in general: generate LITERALLY EVERY worthwhile, self-contained, non-overlapping question that can be asked to master 100% of this Textbook (there is NO small cap — if the Textbook contains 30, 60, or 100+ distinct testable points, generate ALL of them).
                    - Support different ways of thinking: include conceptual ("why/how"), practical/scenario-based, comparison/distinction, reverse-lookup, AND specific literal/granular facts, numbers, lists, or seemingly simple/small details from the Textbook that could show up on an exam and must be remembered.
                    - Keep every question logical, practical, and clear — never create broken, vague, or circular questions.
                    - Ensure no two generated questions ask the exact same thing in slightly different words.
                    - In your thinking phase, keep your outline compact (do not write out every full question and answer twice inside thoughts) so you can stream all `[q_item]` blocks smoothly from beginning to end.
                    """.trimIndent()
                )
                append("\n\n")
            } else {
                val activeList = cleanExisting.filter { it.status == FlashcardStatus.ACTIVE }
                val learnedList = cleanExisting.filter { it.status == FlashcardStatus.LEARNED }
                val dumbList = cleanExisting.filter { it.status == FlashcardStatus.DUMB }

                append("EXISTING QUESTION LIST IN THIS SUBJECT (TOTAL: ${cleanExisting.size} — MUST STUDY CAREFULLY):\n")
                if (activeList.isNotEmpty()) {
                    append("\n1) ACTIVE QUESTIONS (currently in the learner's active review queue — DO NOT repeat or rephrase):\n")
                    activeList.forEachIndexed { idx, item ->
                        append("   [A${idx + 1}] ${item.question}\n")
                    }
                }
                if (learnedList.isNotEmpty()) {
                    append("\n2) LEARNED / FINISHED QUESTIONS (already mastered after completing all stages — DO NOT ask again):\n")
                    learnedList.forEachIndexed { idx, item ->
                        append("   [L${idx + 1}] ${item.question}\n")
                    }
                }
                if (dumbList.isNotEmpty()) {
                    append("\n3) DUMB / REJECTED QUESTIONS (explicitly flagged by the learner as meaningless, poorly framed, or rejected — NEVER recreate these exact rejected questions):\n")
                    dumbList.forEachIndexed { idx, item ->
                        append("   [D${idx + 1}] ${item.question}\n")
                    }
                }

                append("\n")
                append(
                    """
                    EXHAUSTIVE GAP-ANALYSIS & GENERATION TASK (CONNECTED PASS):
                    1. Compare the entire TEXTBOOK section-by-section against ALL existing questions above (Active + Learned + Dumb).
                    2. Treat any question that tests the exact same underlying fact, mechanism, or answer as an existing question as ALREADY COVERED — even if phrased differently.
                    3. Respect the DUMB / REJECTED list: never regenerate those rejected questions.
                    4. Prefer MORE questions in general: find EVERY remaining concept, mechanism, practical application, comparison, inverse angle, exception, or specific exam-testable detail in the TEXTBOOK that has NOT been asked yet, and generate logical, practical questions for all uncovered parts.
                    5. IF NOTHING GENUINELY NEW REMAINS: If the Textbook is already covered by the existing questions above, output ONLY the exact tag: [no_more_questions]
                    """.trimIndent()
                )
                append("\n\n")
            }

            append(
                """
                STREAMING OUTPUT FORMAT (STRICT — FRAME EVERY QUESTION INDIVIDUALLY):
                - Write both the question and the answer in the primary language of the TEXTBOOK (for example Persian when the Textbook is in Persian, English when in English).
                - Keep each answer accurate, direct, and grounded strictly in the TEXTBOOK.
                - Frame EVERY question-answer pair in its own self-contained `[q_item]` block so the app can append each question to the list live as it streams:
                [q_item]
                [q] ...exact question text... [/q]
                [a] ...authoritative textbook answer... [/a]
                [/q_item]
                - Output ONLY `[q_item] ... [/q_item]` blocks back-to-back (or `[no_more_questions]` if nothing new remains), with zero intro, numbering outside tags, or closing commentary.
                """.trimIndent()
            )
        }

        /**
         * Builds the Step 2 (Complement, Audit & Refinement) prompt that examines the entire
         * current question bank alongside the Textbook to add missing angles/exam details, fix
         * flawed active questions, and selectively combine fragmented active questions.
         */
        fun buildSubjectQuestionRefinementPrompt(
            subjectName: String,
            textbook: String,
            extraKnowledge: String,
            currentCards: List<Flashcard>
        ): String = buildString {
            append("Subject: $subjectName\n\n")
            append("TEXTBOOK (MAIN AUTHORITATIVE SOURCE — GROUND ALL QUESTIONS & ANSWERS STRICTLY IN THIS):\n\"\"\"\n")
            append(textbook.trim())
            append("\n\"\"\"\n\n")

            if (extraKnowledge.isNotBlank()) {
                append("EXTRA KNOWLEDGE (BACKING CONTEXT ONLY — DO NOT TEST OR REQUIRE IN ANSWERS):\n\"\"\"\n")
                append(extraKnowledge.trim())
                append("\n\"\"\"\n")
                append("Rule: Extra Knowledge is background context only. Never require Extra Knowledge facts in `[a]`.\n\n")
            }

            val activeCards = currentCards.filter { it.isActive }
            val learnedCards = currentCards.filter { it.isLearned }
            val dumbCards = currentCards.filter { it.isDumb }

            append("CURRENT QUESTION BANK IN THIS SUBJECT (TOTAL: ${currentCards.size}):\n")
            if (activeCards.isEmpty()) {
                append("\n1) ACTIVE QUESTIONS (EDITABLE / COMBINABLE): (None currently active)\n")
            } else {
                append("\n1) ACTIVE QUESTIONS (EDITABLE & COMBINABLE — reference by exact numeric ID):\n")
                activeCards.forEach { card ->
                    val compactAnswer = card.backText.orEmpty().replace(Regex("\\s+"), " ").trim()
                    append("   [ID: ${card.id}]\n")
                    append("   Q: ${card.word.trim()}\n")
                    append("   A: $compactAnswer\n")
                }
            }

            if (learnedCards.isNotEmpty()) {
                append("\n2) LEARNED QUESTIONS (READ-ONLY — already mastered; DO NOT edit, combine, or duplicate):\n")
                learnedCards.forEachIndexed { idx, card ->
                    append("   [L${idx + 1}] ${card.word.trim()}\n")
                }
            }

            if (dumbCards.isNotEmpty()) {
                append("\n3) DUMB / REJECTED QUESTIONS (READ-ONLY — explicitly rejected by learner; NEVER recreate or edit):\n")
                dumbCards.forEachIndexed { idx, card ->
                    append("   [D${idx + 1}] ${card.word.trim()}\n")
                }
            }

            append("\n")
            append(
                """
                SECOND-PASS COMPLEMENT, AUDIT & REFINEMENT MISSION (STEP 2 OF 2):
                You are performing a dedicated second pass to make this question bank rock-solid, logical, practical, and completely exam-ready. Carefully check the TEXTBOOK line-by-line against the CURRENT QUESTION BANK above and answer ALL THREE questions below:

                1) FIX & EDIT (`[action] EDIT [/action]`): "What active questions should we fix and edit?"
                   - Inspect every ACTIVE question (`[ID: ...]`) and its answer.
                   - If any question doesn't make sense, is confusing, illogical, poorly worded, lacks necessary context to stand on its own, tests something outside the Textbook, or has an incomplete/inaccurate answer, rewrite and fix it using `EDIT` with its exact `[ids] <ID> [/ids]`.
                   - Leave active questions untouched if they are already clear, logical, practical, and accurate.

                2) SELECTIVE COMBINE (`[action] COMBINE [/action]`): "What active questions would genuinely be better if we combined them?"
                   - Check if two or more ACTIVE questions (`[ID: ...]`) are near-duplicates or awkward, broken micro-fragments that make much more logical and practical sense merged into ONE cohesive question and answer (`[ids] <ID1>, <ID2> [/ids]`).
                   - IMPORTANT — DO NOT OVER-MERGE: The learner explicitly prefers MORE questions in general! Do NOT merge distinct concepts, distinct angles, or independently testable exam facts just to shorten the list. Combine ONLY when keeping them separate is genuinely redundant, confusing, or fragmented.

                3) COMPLEMENT & EXPAND (`[action] ADD [/action]`): "What other questions can we add to make the question bank even more complete?"
                   - Strongly favor MORE questions in general so nothing from the Textbook can surprise the learner on an exam.
                   - Support DIFFERENT WAYS OF THINKING:
                     * Direct recall & crisp definitions
                     * "Why / How" causal & mechanistic reasoning
                     * Practical / applied / scenario-based ("What happens if...", "When should X be used over Y?")
                     * Reverse-angle questions (given the effect, symptom, property, or example -> identify the concept/cause)
                     * Comparisons, contrasts, boundary conditions, and exceptions
                   - INCLUDE "SILLY" BUT EXAM-TESTABLE DETAILS: Sometimes a question feels simple, literal, granular, or even slightly "silly" (specific numbers, thresholds, names, order of steps, classifications, parenthetical notes, edge facts), YET professors and exams routinely test them! If it is in the Textbook and could appear on an exam, the learner wants to know, remember, and learn it — ADD it as a clear, logical, practical question (while still respecting any exact items the learner already put in DUMB).
                   - Never duplicate a question that is already covered in ACTIVE, LEARNED, DUMB, or your own edits/combinations.

                STRICT STREAMING OUTPUT FORMAT:
                - Write all questions and answers in the primary language of the TEXTBOOK (e.g., Persian if Persian, English if English).
                - Keep thinking concise so you can stream all `[op_item]` blocks smoothly.
                - Emit operations in logical order (`EDIT` and `COMBINE` fixes first, then all complementary `ADD` questions), framing EVERY single operation in its own `[op_item]` block:

                For fixing an existing ACTIVE question:
                [op_item]
                [action] EDIT [/action]
                [ids] 42 [/ids]
                [q] ...improved, logical, self-contained question... [/q]
                [a] ...complete, accurate textbook answer... [/a]
                [/op_item]

                For combining 2+ existing ACTIVE questions when genuinely better together:
                [op_item]
                [action] COMBINE [/action]
                [ids] 42, 47 [/ids]
                [q] ...unified, clearer combined question... [/q]
                [a] ...complete combined textbook answer... [/a]
                [/op_item]

                For adding a new complementary question:
                [op_item]
                [action] ADD [/action]
                [q] ...new complementary question... [/q]
                [a] ...authoritative textbook answer... [/a]
                [/op_item]

                - Do NOT reuse the same `<ID>` across multiple `EDIT` or `COMBINE` blocks, and ONLY reference IDs from section `1) ACTIVE QUESTIONS`.
                - If after thorough audit every active question is already logical/accurate, none need combining, and every possible angle and exam detail in the Textbook is already covered, output ONLY: [no_refinements_needed]
                - Output ONLY `[op_item] ... [/op_item]` blocks (or `[no_refinements_needed]`) with zero preamble or commentary outside tags.
                """.trimIndent()
            )
        }

        /**
         * Builds the system instruction for the "Add a question" conversation inside a Subject,
         * ensuring the AI actively references and grounds the discussion in the Subject's Textbook.
         */
        fun buildSubjectAddQuestionSystemInstruction(
            subject: Subject?,
            existingQuestions: List<ExistingSubjectQuestion> = emptyList()
        ): String = buildString {
            append("You are a direct, expert subject mentor helping the learner formulate, clarify, and pinpoint a specific study question to add to ")
            if (subject != null) {
                append("the subject \"${subject.name}\".\n")
            } else {
                append("their study subject.\n")
            }
            append("The learner is talking about concepts that belong to this subject's TEXTBOOK. ")
            append("Actively reference, locate, and ground your explanations and suggested question wording directly in the TEXTBOOK content below so the resulting question accurately reflects the textbook. ")
            append("Keep responses crisp, direct, and free of praise or filler.")

            val refBlock = buildSubjectReferenceInstructions(subject = subject, forReviewGrading = false)
            if (refBlock.isNotBlank()) {
                append("\n\n")
                append(refBlock)
            }

            if (existingQuestions.isNotEmpty()) {
                append("\n\nEXISTING QUESTIONS ALREADY IN THIS SUBJECT (for context so you know what is already covered or flagged):\n")
                existingQuestions.takeLast(60).forEachIndexed { idx, q ->
                    append("${idx + 1}. [${q.status}] ${q.question}\n")
                }
            }
        }

        /**
         * Formats the complete existing question list (Active, Learned, and Dumb — with no truncation)
         * so single-question generation can perform a thorough semantic repetition check.
         */
        private fun appendCompleteExistingQuestionsForDeduplication(
            builder: StringBuilder,
            existingQuestions: List<ExistingSubjectQuestion>
        ) {
            val cleanExisting = existingQuestions
                .map {
                    it.copy(
                        question = it.question.trim(),
                        status = FlashcardStatus.normalize(it.status),
                        answer = it.answer?.trim()?.takeIf { ans -> ans.isNotEmpty() }
                    )
                }
                .filter { it.question.isNotEmpty() }

            if (cleanExisting.isEmpty()) {
                builder.append("COMPLETE EXISTING QUESTION LIST (TOTAL: 0 — list is currently empty):\n(None)\n\n")
                return
            }

            builder.append("COMPLETE EXISTING QUESTION LIST (TOTAL: ${cleanExisting.size} — MUST ANALYZE EVERY SINGLE ITEM FOR REPETITION, INCLUDING ACTIVE, LEARNED, AND DUMB):\n")
            cleanExisting.forEachIndexed { idx, item ->
                builder.append("${idx + 1}. [${item.status}] Q: ${item.question}")
                if (!item.answer.isNullOrBlank()) {
                    val compactAnswer = item.answer.replace(Regex("\\s+"), " ").take(240)
                    builder.append(" | A: $compactAnswer")
                }
                builder.append("\n")
            }
            builder.append("\n")
        }

        /**
         * Builds the prompt that synthesizes ONE crisp study question AND its authoritative answer
         * after the user has discussed a topic in the Subject's "Add a question" conversation,
         * OR flags `[question_already_exists]` if the target question is already in the list.
         */
        fun buildSingleSubjectQuestionFromConversationPrompt(
            subjectName: String,
            textbook: String,
            extraKnowledge: String,
            existingQuestions: List<ExistingSubjectQuestion> = emptyList(),
            history: List<ChatMessage>
        ): String = buildString {
            append("Subject: $subjectName\n\n")
            if (textbook.isNotBlank()) {
                append("TEXTBOOK (MAIN AUTHORITATIVE ANSWER REFERENCE):\n\"\"\"\n")
                append(textbook.trim())
                append("\n\"\"\"\n\n")
            }
            if (extraKnowledge.isNotBlank()) {
                append("EXTRA KNOWLEDGE (BACKING CONTEXT ONLY — DO NOT REQUIRE IN THE ANSWER):\n\"\"\"\n")
                append(extraKnowledge.trim())
                append("\n\"\"\"\n\n")
            }
            appendCompleteExistingQuestionsForDeduplication(this, existingQuestions)
            append("CONVERSATION BETWEEN USER AND TUTOR:\n")
            history.forEach { msg ->
                val cleanText = if (msg.sender == "model") {
                    TutorMessageFormatter.toPlainSpeakableText(msg.text).ifBlank { msg.text }
                } else {
                    msg.text.ifBlank {
                        if (msg.allImagesBase64().isNotEmpty()) {
                            "[Attached ${msg.allImagesBase64().size} image(s)]"
                        } else {
                            ""
                        }
                    }
                }
                if (cleanText.isNotBlank()) {
                    append("${if (msg.sender == "user") "User" else "Tutor"}: $cleanText\n")
                }
            }
            append("\nTASK (THOROUGH CONVERSATION + FULL QUESTION LIST ANALYSIS):\n")
            append(
                """
                1. Think carefully about the exact concept, mechanism, distinction, or question the User and Tutor focused on in the conversation above.
                2. Compare that target question/concept against EVERY SINGLE ITEM in the COMPLETE EXISTING QUESTION LIST above (including ACTIVE, LEARNED, and DUMB items). Because the learner may not have studied or remembered the entire list yet, check both wording and underlying meaning/answer:
                   - If ANY existing question already asks or tests the same underlying fact, mechanism, or distinction (even if phrased in different words), treat it as REPETITIVE / ALREADY GENERATED.
                   - In that case, DO NOT generate `[q_item]`. Output ONLY:
                   [question_already_exists]
                   [matched_q] ...copy the exact matching question from the existing list... [/matched_q]
                3. ONLY if the target question is genuinely NEW and not already covered by the existing list:
                   - Formulate ONE clear, self-contained study question (`[q]...[/q]`) and its accurate, complete reference answer (`[a]...[/a]`) grounded strictly in the TEXTBOOK (when provided) and the agreed points in the conversation.
                   - Do NOT require Extra Knowledge details in `[a]`.
                   - Write both the question and the answer in the primary language used in the TEXTBOOK / conversation (for example Persian if Persian, English if English).
                   - Output ONLY this exact framed block with zero intro or commentary:
                   [q_item]
                   [q] ...exact question text... [/q]
                   [a] ...authoritative reference answer... [/a]
                   [/q_item]
                """.trimIndent()
            )
        }

        private val PERSIAN_ARABIC_SCRIPT_REGEX = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]")

        /**
         * Builds the initial opener message when the user taps "Extract Question" during review,
         * matching the language of the current review item/conversation.
         */
        fun buildExtractQuestionOpeningMessage(
            sourceCard: Flashcard,
            reviewConversation: List<ChatMessage>,
            subject: Subject?
        ): String {
            val sampleText = buildString {
                append(sourceCard.word)
                append(" ")
                reviewConversation.takeLast(4).forEach { msg ->
                    append(TutorMessageFormatter.toPlainSpeakableText(msg.text).ifBlank { msg.text })
                    append(" ")
                }
                if (isBlank() && subject != null) {
                    append(subject.name)
                }
            }
            val isPersian = PERSIAN_ARABIC_SCRIPT_REGEX.containsMatchIn(sampleText)
            return if (isPersian) {
                "کدام بخش یا نکته از این گفتگو و مرور برایتان جالب بود و می‌خواهید آن را به فهرست سؤالات خود اضافه کنید؟"
            } else {
                "What part of this review would you like to add to your list of questions?"
            }
        }

        /**
         * Builds the system instruction for the "Extract Question" window opened from Review,
         * giving the AI complete awareness of the current review item, its full conversation history,
         * the Subject's Textbook, Extra Knowledge, and existing Subject questions.
         */
        fun buildExtractQuestionFromReviewSystemInstruction(
            sourceCard: Flashcard,
            reviewConversation: List<ChatMessage>,
            subject: Subject?,
            existingQuestions: List<ExistingSubjectQuestion> = emptyList()
        ): String = buildString {
            append("You are a sharp, knowledgeable study mentor helping the learner extract a brand-new question from an interesting detail, nuance, mechanism, or side-note they just encountered while reviewing an item.\n")
            append("You have FULL awareness of:\n")
            append("1) The current review item and its entire conversation history below (so when the learner refers to 'that point', 'why that happens', or a phrase they just read, you immediately know what they mean).\n")
            if (subject != null) {
                append("2) The Subject \"${subject.name}\", including its TEXTBOOK (authoritative reference) and EXTRA KNOWLEDGE (backing context).\n")
            }
            append("\nPHILOSOPHY & CONVERSATION STYLE:\n")
            append("- When the learner points out what caught their interest, connect directly to the review discussion, Textbook, or Extra Knowledge to clarify or unpack why/how it works concisely.\n")
            append("- Once the learner confirms that exact angle is what they want to add to their question list, acknowledge it briefly and clearly (e.g., confirming the exact point) so they can tap 'Generate the question'.\n")
            append("- Stay crisp, natural, and direct with zero hollow flattery.\n\n")

            append("CURRENT REVIEW ITEM CONTEXT:\n")
            append("- Prompt / Question: ${sourceCard.word}\n")
            if (!sourceCard.backText.isNullOrBlank()) {
                append("- Reference Answer / Back: ${sourceCard.backText}\n")
            }
            if (!sourceCard.focusSummary.isNullOrBlank()) {
                append("- Focus Summary: ${sourceCard.focusSummary}\n")
            }

            if (reviewConversation.isNotEmpty()) {
                append("\nCONVERSATION OF THIS REVIEW ITEM:\n")
                reviewConversation.forEach { msg ->
                    val clean = if (msg.sender == "model") {
                        TutorMessageFormatter.toPlainSpeakableText(msg.text).ifBlank { msg.text }
                    } else {
                        msg.text.ifBlank {
                            if (msg.allImagesBase64().isNotEmpty()) "[Attached ${msg.allImagesBase64().size} image(s)]" else ""
                        }
                    }
                    if (clean.isNotBlank()) {
                        append("${if (msg.sender == "user") "Learner" else "Tutor"}: $clean\n")
                    }
                }
            }

            val refBlock = buildSubjectReferenceInstructions(subject = subject, forReviewGrading = false)
            if (refBlock.isNotBlank()) {
                append("\n\n")
                append(refBlock)
            }

            if (existingQuestions.isNotEmpty()) {
                append("\n\nEXISTING QUESTIONS ALREADY IN THIS SUBJECT (avoid duplicating these):\n")
                existingQuestions.takeLast(60).forEachIndexed { idx, q ->
                    append("${idx + 1}. [${FlashcardStatus.normalize(q.status)}] ${q.question}\n")
                }
            }
        }

        /**
         * Builds the high-thinking generation prompt that turns the "Extract Question" discussion
         * (plus the underlying review item conversation, Textbook, and Extra Knowledge) into ONE
         * crisp `[q_item]` question & answer pair for preview and Save confirmation.
         */
        fun buildExtractQuestionFromReviewGenerationPrompt(
            sourceCard: Flashcard,
            reviewConversation: List<ChatMessage>,
            subject: Subject?,
            existingQuestions: List<ExistingSubjectQuestion> = emptyList(),
            extractionConversation: List<ChatMessage>
        ): String = buildString {
            if (subject != null) {
                append("Subject: ${subject.name}\n\n")
                val textbook = subject.effectiveTextbook()
                val extraKnowledge = subject.effectiveExtraKnowledge()
                if (textbook.isNotBlank()) {
                    append("TEXTBOOK (AUTHORITATIVE REFERENCE):\n\"\"\"\n")
                    append(textbook.trim())
                    append("\n\"\"\"\n\n")
                }
                if (extraKnowledge.isNotBlank()) {
                    append("EXTRA KNOWLEDGE (BACKING CONTEXT):\n\"\"\"\n")
                    append(extraKnowledge.trim())
                    append("\n\"\"\"\n")
                    append("Note: In Extract Question mode, if the learner explicitly chose to extract an interesting insight that came from Extra Knowledge or a `[blue]` note in the review discussion, you SHOULD honor the learner's explicit choice and craft the question & answer around that exact insight; otherwise ground the answer in the Textbook and agreed discussion.\n\n")
                }
            }

            append("SOURCE REVIEW ITEM:\n")
            append("- Original Question / Card: ${sourceCard.word}\n")
            if (!sourceCard.backText.isNullOrBlank()) {
                append("- Original Reference Answer: ${sourceCard.backText}\n")
            }

            if (reviewConversation.isNotEmpty()) {
                append("\nCONVERSATION OF THE SOURCE REVIEW ITEM:\n")
                reviewConversation.forEach { msg ->
                    val clean = if (msg.sender == "model") {
                        TutorMessageFormatter.toPlainSpeakableText(msg.text).ifBlank { msg.text }
                    } else {
                        msg.text.ifBlank {
                            if (msg.allImagesBase64().isNotEmpty()) "[Attached ${msg.allImagesBase64().size} image(s)]" else ""
                        }
                    }
                    if (clean.isNotBlank()) {
                        append("${if (msg.sender == "user") "Learner" else "Tutor"}: $clean\n")
                    }
                }
            }

            append("\n")
            appendCompleteExistingQuestionsForDeduplication(this, existingQuestions)

            append("EXTRACT QUESTION CONVERSATION (WHAT THE LEARNER WANTS TO ADD):\n")
            extractionConversation.forEach { msg ->
                val cleanText = if (msg.sender == "model") {
                    TutorMessageFormatter.toPlainSpeakableText(msg.text).ifBlank { msg.text }
                } else {
                    msg.text.ifBlank {
                        if (msg.allImagesBase64().isNotEmpty()) {
                            "[Attached ${msg.allImagesBase64().size} image(s)]"
                        } else {
                            ""
                        }
                    }
                }
                if (cleanText.isNotBlank()) {
                    append("${if (msg.sender == "user") "User" else "Tutor"}: $cleanText\n")
                }
            }

            append("\nTASK (THOROUGH CONVERSATION + FULL QUESTION LIST ANALYSIS):\n")
            append(
                """
                1. Identify the exact concept, mechanism, nuance, or insight that the User wanted to extract into a standalone question during the EXTRACT QUESTION CONVERSATION above.
                2. Compare that target question/concept against EVERY SINGLE ITEM in the COMPLETE EXISTING QUESTION LIST above (including ACTIVE, LEARNED, and DUMB items, as well as the source review question). Because the learner may not have studied the entire list yet and might miss an upcoming or archived question, check both wording and underlying meaning/answer:
                   - If ANY existing question in the list already asks or tests this same concept, mechanism, or answer (even if worded differently), treat it as REPETITIVE / ALREADY GENERATED.
                   - In that case, DO NOT generate `[q_item]`. Output ONLY:
                   [question_already_exists]
                   [matched_q] ...copy the exact matching question from the existing list... [/matched_q]
                3. ONLY if the target insight is genuinely NEW and not already covered by any question in the existing list:
                   - Formulate ONE clear, self-contained study question (`[q]...[/q]`) and its accurate, complete reference answer (`[a]...[/a]`) grounded in the Textbook / review discussion and the agreed explanation.
                   - Make sure the new question stands on its own (without saying "In the previous conversation...").
                   - Write both the question and the answer in the primary language used in the review item / conversation (for example Persian if Persian, English if English).
                   - Output ONLY this exact framed block with zero intro or commentary:
                   [q_item]
                   [q] ...exact question text... [/q]
                   [a] ...authoritative reference answer... [/a]
                   [/q_item]
                """.trimIndent()
            )
        }

        /**
         * Builds the Subject reference block (`Textbook` vs `Extra Knowledge`) injected into
         * review grading and subject question conversations.
         */
        fun buildSubjectReferenceInstructions(
            subject: Subject?,
            forReviewGrading: Boolean
        ): String {
            if (subject == null) return ""
            val textbookContent = subject.effectiveTextbook()
            val extraKnowledgeContent = subject.effectiveExtraKnowledge()
            return buildString {
                append("Subject: ${subject.name}\n")
                if (textbookContent.isNotBlank()) {
                    append("\nTEXTBOOK (AUTHORITATIVE ANSWER REFERENCE):\n\"\"\"\n")
                    append(textbookContent)
                    append("\n\"\"\"\n")
                    if (forReviewGrading) {
                        append("Use the Textbook above (alongside any card-specific reference answer or prior discussion) as the authoritative ground truth when judging what the student got right in [green] and what they missed or should state better in [red].\n")
                    } else {
                        append("You have full awareness of the Textbook above — treat it as the authoritative source material and reference its exact concepts and facts when talking with the learner.\n")
                    }
                }
                if (extraKnowledgeContent.isNotBlank()) {
                    append("\nEXTRA KNOWLEDGE (BACKING CONTEXT ONLY — NOT REQUIRED IN STUDENT ANSWERS):\n\"\"\"\n")
                    append(extraKnowledgeContent)
                    append("\n\"\"\"\n")
                    if (forReviewGrading) {
                        append("STRICT RULE FOR EXTRA KNOWLEDGE: The student does NOT have to mention or know anything from Extra Knowledge. NEVER mark anything missing or wrong in [red] based on Extra Knowledge. However, you MAY use Extra Knowledge as backing context and briefly quote a relevant phrase or insight from it (for example inside an optional [blue] foundational block or in follow-up replies) when it genuinely deepens understanding.\n")
                    } else {
                        append("Extra Knowledge is supporting background only (not required in student answers), though you may draw on or quote it when helpful.\n")
                    }
                }
            }.trim()
        }

        /**
         * Parses generated questions from `[q_item][q]...[/q][a]...[/a][/q_item]` frames
         * (including salvaging any trailing `[q]...[/q][a]...[/a]` if cut off before `[/q_item]`),
         * `[no_more_questions]` / `[]` empty markers, or fallback JSON / Q-A pairs,
         * and filters out duplicates of [existingQuestions] (Active, Learned, and Dumb).
         */
        fun parseGeneratedQuestions(
            raw: String,
            existingQuestions: List<String> = emptyList()
        ): List<ExamQuestionParser.ParsedQuestion> {
            val normalizedExisting = existingQuestions
                .map { normalizeQuestionKey(it) }
                .filter { it.isNotEmpty() }
                .toHashSet()

            val stripped = CODE_FENCE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.trim()
                ?: raw.trim()

            if (stripped.isEmpty() ||
                stripped == "[]" ||
                stripped.equals(NO_MORE_QUESTIONS_TAG, ignoreCase = true) ||
                (stripped.contains(NO_MORE_QUESTIONS_TAG, ignoreCase = true) &&
                    !stripped.contains("[q]", ignoreCase = true))
            ) {
                return emptyList()
            }

            // 1. Primary format: [q] ... [/q] [a] ... [/a] (matches both full [q_item] frames and a trailing block missing [/q_item])
            val taggedPairs = STANDALONE_QA_TAG_REGEX.findAll(stripped).mapNotNull { match ->
                val q = cleanQuestionText(match.groupValues.getOrNull(1).orEmpty())
                val a = match.groupValues.getOrNull(2)?.trim().orEmpty()
                if (q.isNotEmpty() && a.isNotEmpty()) {
                    ExamQuestionParser.ParsedQuestion(question = q, answer = a)
                } else {
                    null
                }
            }.toList()

            val candidates = if (taggedPairs.isNotEmpty() || stripped.contains("[q_item]", ignoreCase = true) || stripped.contains("[q]", ignoreCase = true)) {
                taggedPairs
            } else {
                // 2. Fallback: full JSON array or salvaged individual JSON objects or Q:/A: text
                val parsedFromWholeArray: List<ExamQuestionParser.ParsedQuestion>? = runCatching {
                    val startIdx = stripped.indexOf('[')
                    val endIdx = stripped.lastIndexOf(']')
                    if (startIdx >= 0 && endIdx > startIdx) {
                        val jsonSlice = stripped.substring(startIdx, endIdx + 1)
                        if (jsonSlice.replace("\\s+".toRegex(), "") == "[]") {
                            return@runCatching emptyList<ExamQuestionParser.ParsedQuestion>()
                        }
                        val arr = JSONArray(jsonSlice)
                        buildList<ExamQuestionParser.ParsedQuestion> {
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val q = cleanQuestionText(obj.optString("question", ""))
                                val a = obj.optString("answer", "").trim()
                                if (q.isNotEmpty() && a.isNotEmpty()) {
                                    add(ExamQuestionParser.ParsedQuestion(question = q, answer = a))
                                }
                            }
                        }
                    } else {
                        null
                    }
                }.getOrNull()

                val salvagedFromObjects = if (parsedFromWholeArray == null) {
                    INDIVIDUAL_JSON_OBJECT_REGEX.findAll(stripped).mapNotNull { match ->
                        runCatching {
                            val obj = JSONObject(match.value)
                            val q = cleanQuestionText(obj.optString("question", ""))
                            val a = obj.optString("answer", "").trim()
                            if (q.isNotEmpty() && a.isNotEmpty()) {
                                ExamQuestionParser.ParsedQuestion(question = q, answer = a)
                            } else {
                                null
                            }
                        }.getOrNull()
                    }.toList()
                } else {
                    emptyList()
                }

                when {
                    parsedFromWholeArray != null -> parsedFromWholeArray
                    salvagedFromObjects.isNotEmpty() -> salvagedFromObjects
                    !stripped.startsWith("[") -> ExamQuestionParser.parse(stripped)
                    else -> emptyList()
                }
            }

            val seen = HashSet<String>(normalizedExisting)
            return candidates.filter { item ->
                val key = normalizeQuestionKey(item.question)
                key.isNotEmpty() && seen.add(key)
            }
        }

        private const val QUESTION_ALREADY_EXISTS_TAG = "[question_already_exists]"
        private val MATCHED_QUESTION_TAG_REGEX =
            Regex("(?is)\\[matched_q\\]\\s*(.*?)\\s*\\[/matched_q\\]")

        /**
         * Parses the result of single-question generation ("Add a question" or "Extract Question"):
         * - Detects `[question_already_exists]` (and optional `[matched_q]...[/matched_q]`) from the model.
         * - Also performs a deterministic normalized-key duplicate check against [existingQuestions]
         *   in case the model emitted a `[q_item]` that matches an existing question verbatim.
         */
        fun parseSingleQuestionGenerationOutcome(
            raw: String,
            existingQuestions: List<ExistingSubjectQuestion> = emptyList()
        ): SingleQuestionGenerationOutcome {
            val stripped = CODE_FENCE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.trim()
                ?: raw.trim()

            val existingByKey = LinkedHashMap<String, ExistingSubjectQuestion>()
            existingQuestions.forEach { item ->
                val key = normalizeQuestionKey(item.question)
                if (key.isNotEmpty() && !existingByKey.containsKey(key)) {
                    existingByKey[key] = item.copy(
                        question = item.question.trim(),
                        status = FlashcardStatus.normalize(item.status)
                    )
                }
            }

            if (stripped.contains(QUESTION_ALREADY_EXISTS_TAG, ignoreCase = true) ||
                stripped.equals(NO_MORE_QUESTIONS_TAG, ignoreCase = true)
            ) {
                val rawMatched = MATCHED_QUESTION_TAG_REGEX.find(stripped)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { cleanQuestionText(it) }
                    ?.takeIf { it.isNotEmpty() }

                val matchedExisting = rawMatched?.let { existingByKey[normalizeQuestionKey(it)] }
                return SingleQuestionGenerationOutcome.AlreadyExists(
                    matchedQuestion = matchedExisting?.question ?: rawMatched,
                    matchedStatus = matchedExisting?.status
                )
            }

            val parsedCandidate = parseGeneratedQuestions(raw, existingQuestions = emptyList()).firstOrNull()
                ?: throw IllegalStateException("Could not parse a generated question and answer from the AI response. Please try again.")

            val normalizedCandidate = normalizeQuestionKey(parsedCandidate.question)
            val exactDuplicate = existingByKey[normalizedCandidate]
            if (exactDuplicate != null) {
                return SingleQuestionGenerationOutcome.AlreadyExists(
                    matchedQuestion = exactDuplicate.question,
                    matchedStatus = exactDuplicate.status
                )
            }

            return SingleQuestionGenerationOutcome.Generated(parsedCandidate)
        }

        private fun cleanQuestionText(rawQuestion: String): String =
            rawQuestion.trim()
                .replace(Regex("^(?i)(?:\\[q_item\\]\\s*)?(?:q|question|سوال)\\s*\\d*\\s*[:：.-]\\s*"), "")
                .trim()

        private fun normalizeQuestionKey(text: String): String =
            text.lowercase()
                .replace(Regex("[\\s\\p{Punct}؟،؛«»]+"), " ")
                .trim()

        fun bitmapToBase64(
            bitmap: Bitmap,
            maxDimension: Int = 1440,
            quality: Int = 82
        ): String {
            val longest = max(bitmap.width, bitmap.height)
            val scaled = if (longest > maxDimension && maxDimension > 0) {
                val ratio = maxDimension.toFloat() / longest.toFloat()
                val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }
            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }

        fun fileToBase64(
            file: File,
            maxDimension: Int = 1600,
            quality: Int = 85
        ): String? {
            if (!file.exists() || !file.isFile) return null
            return runCatching {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                var sampleSize = 1
                val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
                if (longest > maxDimension && maxDimension > 0) {
                    while (longest / (sampleSize * 2) >= maxDimension) {
                        sampleSize *= 2
                    }
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                    ?: return@runCatching Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                bitmapToBase64(decoded, maxDimension = maxDimension, quality = quality)
            }.getOrNull()
        }

        private const val MAX_SERIALIZED_CHAT_CHARS = 1_200_000
    }
}
