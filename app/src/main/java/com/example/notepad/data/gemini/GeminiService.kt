package com.example.notepad.data.gemini

import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
import com.example.audio.AudioTranscoder
import com.example.network.AvalAiClient
import com.example.network.CorrectionJsonCodec
import com.example.network.LinguisticCorrection
import com.example.settings.ApiKeyProvider
import com.example.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class VoiceNoteAnalysis(
    val transcription: String,
    val englishTranslation: String? = null,
    val corrections: List<LinguisticCorrection> = emptyList()
)

/**
 * Processes a single whole Notepad voice recording in two dedicated requests (no patching):
 * 1. **Request 1 — Transcription (STT)**: Sends the single recorded voice clip to the selected STT
 *    model and returns the verbatim spoken transcript.
 * 2. **Request 2 — Language-Specific Second Pass (TTT)**:
 *    - **If Persian**: Translates the transcript naturally into English (main second part; no Persian corrections).
 *    - **If English**: Runs spoken linguistic analysis (grammar & word-choice highlights; no translation).
 */
class GeminiService(
    private val avalAiClient: AvalAiClient = AvalAiClient()
) {

    companion object {
        private const val TAG = "GeminiService"
        const val TRANSCRIPTION_MODEL = AppSettingsRepository.GEMINI_STT_MODEL
        const val ANALYSIS_MODEL = AppSettingsRepository.HOUSHYAR24_LUNA_TTT_MODEL
        const val SILENCE_TRANSCRIPTION = "Silence: no speech was audible."

        private val PERSIAN_ARABIC_SCRIPT_REGEX =
            Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
        private val LATIN_LETTER_REGEX = Regex("[A-Za-z]")
        private val PATCH_MARKER_REGEX = Regex("(?i)\\{patch\\s*\\d+\\}|\\[patch\\s*\\d+\\]")
        private val MULTI_SPACE_REGEX = Regex("\\s{2,}")

        internal fun containsPersianScript(text: String): Boolean =
            PERSIAN_ARABIC_SCRIPT_REGEX.containsMatchIn(text)

        fun stripPatchMarkers(text: String): String {
            if (text.isBlank()) return text
            return text
                .replace(PATCH_MARKER_REGEX, "")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
        }

        fun isSilenceToken(text: String): Boolean {
            val cleaned = stripPatchMarkers(text).trim()
            return cleaned.isEmpty() ||
                cleaned.equals("NO_SPEECH", ignoreCase = true) ||
                cleaned.equals("SILENCE", ignoreCase = true) ||
                cleaned.equals("[SILENCE]", ignoreCase = true) ||
                cleaned.equals(SILENCE_TRANSCRIPTION, ignoreCase = true)
        }
    }

    private fun resolveApiKeyCandidates(
        avalaiKeyCandidates: List<Pair<Int, String>>,
        provider: ApiKeyProvider = ApiKeyProvider.ONEXAI
    ): List<Pair<Int, String>> {
        val explicit = avalaiKeyCandidates
            .map { (idx, key) -> idx to key.trim() }
            .filter { (_, key) ->
                key.isNotBlank() &&
                    key != "MY_ONEXAI_API_KEY" &&
                    key != "MY_AVALAI_API_KEY" &&
                    key != "MY_HORMOUZ_API_KEY" &&
                    key != "MY_HOUSHYAR24_API_KEY"
            }
        if (explicit.isNotEmpty()) return explicit

        val buildFieldName = when (provider) {
            ApiKeyProvider.HORMOUZ -> "HORMOUZ_API_KEY"
            ApiKeyProvider.HOUSHYAR24 -> "HOUSHYAR24_API_KEY"
            ApiKeyProvider.ONEXAI -> "ONEXAI_API_KEY"
        }
        val placeholderValue = "MY_$buildFieldName"

        val secretsFallback = runCatching {
            val f = BuildConfig::class.java.getField(buildFieldName)
            (f.get(null) as? String)?.trim()
        }.getOrNull()?.takeIf {
            it.isNotBlank() && it != placeholderValue
        }

        return if (secretsFallback != null) {
            listOf(0 to secretsFallback)
        } else {
            emptyList()
        }
    }

    /**
     * Full single-voice pipeline:
     * 1. Request 1: Transcribes the entire saved WAV file (`transcribeVoiceNoteFile`).
     * 2. Request 2: Translates to English if Persian, or performs spoken linguistic analysis if English (`analyzeCompletedTranscript`).
     */
    suspend fun analyzeVoiceNote(
        audioFile: File,
        avalaiKeyCandidates: List<Pair<Int, String>>,
        model: String = TRANSCRIPTION_MODEL,
        provider: ApiKeyProvider = AvalAiClient.providerForModel(model),
        analysisModel: String? = null,
        analysisProvider: ApiKeyProvider? = null,
        analysisKeyCandidates: List<Pair<Int, String>>? = null,
        onVerbatimTranscriptReady: (String) -> Unit = {},
        onAvalaiKeySucceeded: (Int) -> Unit = {},
        onAnalysisKeySucceeded: (Int) -> Unit = onAvalaiKeySucceeded
    ): Result<VoiceNoteAnalysis> = withContext(Dispatchers.IO) {
        val transcriptResult = transcribeVoiceNoteFile(
            audioFile = audioFile,
            model = model,
            provider = provider,
            avalaiKeyCandidates = avalaiKeyCandidates,
            onAvalaiKeySucceeded = onAvalaiKeySucceeded
        )
        if (transcriptResult.isFailure) {
            return@withContext Result.failure(
                transcriptResult.exceptionOrNull()
                    ?: VoiceNoteApiException("Voice note transcription failed.")
            )
        }

        val verbatimTranscript = transcriptResult.getOrThrow().trim()
        if (isSilenceToken(verbatimTranscript)) {
            return@withContext Result.success(
                VoiceNoteAnalysis(transcription = SILENCE_TRANSCRIPTION)
            )
        }

        onVerbatimTranscriptReady(verbatimTranscript)

        val resolvedSttKeys = resolveApiKeyCandidates(avalaiKeyCandidates, provider)
        val effectiveAnalysisModel = analysisModel
            ?: if (AvalAiClient.isDedicatedAudioTranscriptionModel(model)) {
                AppSettingsRepository.HORMOUZ_GLM_5_3_FLASH_TTT_MODEL
            } else {
                model
            }
        val effectiveAnalysisProvider = analysisProvider ?: AvalAiClient.providerForModel(effectiveAnalysisModel)
        val effectiveAnalysisKeys = resolveApiKeyCandidates(
            analysisKeyCandidates ?: resolvedSttKeys,
            effectiveAnalysisProvider
        )

        val postAnalysis = analyzeCompletedTranscript(
            transcript = verbatimTranscript,
            model = effectiveAnalysisModel,
            provider = effectiveAnalysisProvider,
            avalaiKeyCandidates = effectiveAnalysisKeys,
            onAvalaiKeySucceeded = onAnalysisKeySucceeded
        )
        if (postAnalysis.isSuccess) {
            Result.success(postAnalysis.getOrThrow())
        } else {
            Log.w(
                TAG,
                "Second-stage voice note request failed; preserving verbatim transcript: ${postAnalysis.exceptionOrNull()?.message}"
            )
            Result.success(VoiceNoteAnalysis(transcription = verbatimTranscript))
        }
    }

    /**
     * Request 1: Single-voice verbatim speech-to-text for the full recorded WAV file.
     * Returns `""` when the recording contains only silence.
     */
    suspend fun transcribeVoiceNoteFile(
        audioFile: File,
        model: String = TRANSCRIPTION_MODEL,
        provider: ApiKeyProvider = AvalAiClient.providerForModel(model),
        avalaiKeyCandidates: List<Pair<Int, String>>,
        onAvalaiKeySucceeded: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val audioBytes = try {
            audioFile.readBytes()
        } catch (e: IOException) {
            Log.e(TAG, "Could not read recorded audio", e)
            return@withContext Result.failure(
                AudioFileException("Audio file issue: could not read the recording.")
            )
        }
        if (audioBytes.isEmpty()) {
            return@withContext Result.failure(
                AudioFileException("Audio file issue: the recording is empty.")
            )
        }
        transcribeSingleVoiceBytes(
            audioBytes = audioBytes,
            model = model,
            provider = provider,
            avalaiKeyCandidates = avalaiKeyCandidates,
            onAvalaiKeySucceeded = onAvalaiKeySucceeded
        )
    }

    /**
     * Request 1 on raw audio bytes: single-voice verbatim speech-to-text.
     */
    suspend fun transcribeSingleVoiceBytes(
        audioBytes: ByteArray,
        model: String = TRANSCRIPTION_MODEL,
        provider: ApiKeyProvider = AvalAiClient.providerForModel(model),
        avalaiKeyCandidates: List<Pair<Int, String>>,
        onAvalaiKeySucceeded: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val resolvedKeys = resolveApiKeyCandidates(avalaiKeyCandidates, provider)
        if (resolvedKeys.isEmpty()) {
            return@withContext Result.failure(
                VoiceNoteApiException(AvalAiClient.MISSING_AVALAI_KEY_MESSAGE)
            )
        }
        if (audioBytes.isEmpty()) {
            return@withContext Result.failure(
                AudioFileException("Voice recording is empty.")
            )
        }

        val wavBytes = AudioTranscoder.ensureWavBytes(audioBytes, null)
        if (wavBytes.isEmpty()) {
            return@withContext Result.failure(
                AudioFileException("Voice recording could not be prepared.")
            )
        }

        val startedAt = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        val sttResult = avalAiClient.transcribeNotepadVoiceRecording(
            audioBytes = wavBytes,
            mimeType = "audio/wav",
            apiKeyCandidates = resolvedKeys,
            model = model,
            provider = provider,
            onKeySucceeded = onAvalaiKeySucceeded
        )
        val elapsedMs = runCatching { SystemClock.elapsedRealtime() - startedAt }.getOrDefault(0L)

        if (sttResult.isFailure) {
            val err = sttResult.exceptionOrNull()
            Log.w(TAG, "Single-voice STT failed after ${elapsedMs}ms: ${err?.message}")
            return@withContext Result.failure(
                VoiceNoteApiException(err?.localizedMessage ?: "Voice note transcription failed.")
            )
        }

        val rawTranscript = stripPatchMarkers(sttResult.getOrNull().orEmpty())
            .replace(Regex("(?i)^\\[transcription\\]\\s*"), "")
            .trim()
        val clean = if (isSilenceToken(rawTranscript)) "" else rawTranscript
        Log.d(TAG, "Single-voice STT succeeded in ${elapsedMs}ms (${clean.length} chars)")
        Result.success(clean)
    }

    /**
     * Request 2: Language-dependent second request on the completed [transcript]:
     * - If [transcript] is in Persian -> requests a natural English translation ONLY (`corrections = emptyList()`).
     * - If [transcript] is in English -> requests spoken linguistic analysis ONLY (`englishTranslation = null`).
     * Never modifies [transcript] itself.
     */
    suspend fun analyzeCompletedTranscript(
        transcript: String,
        model: String = ANALYSIS_MODEL,
        provider: ApiKeyProvider = AvalAiClient.providerForModel(model),
        avalaiKeyCandidates: List<Pair<Int, String>>,
        onAvalaiKeySucceeded: (Int) -> Unit = {}
    ): Result<VoiceNoteAnalysis> = withContext(Dispatchers.IO) {
        val cleanTranscript = stripPatchMarkers(transcript).trim()
        if (isSilenceToken(cleanTranscript)) {
            return@withContext Result.success(VoiceNoteAnalysis(transcription = SILENCE_TRANSCRIPTION))
        }

        val resolvedKeys = resolveApiKeyCandidates(avalaiKeyCandidates, provider)
        if (resolvedKeys.isEmpty()) {
            return@withContext Result.failure(
                VoiceNoteApiException(AvalAiClient.MISSING_AVALAI_KEY_MESSAGE)
            )
        }

        val messages = buildVoiceNoteAnalysisMessages(cleanTranscript)
        val startedAt = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        val rawResult = avalAiClient.analyzeNotepadTranscript(
            messages = messages,
            apiKeyCandidates = resolvedKeys,
            model = model,
            provider = provider,
            onKeySucceeded = onAvalaiKeySucceeded
        )
        val elapsedMs = runCatching { SystemClock.elapsedRealtime() - startedAt }.getOrDefault(0L)

        if (rawResult.isFailure) {
            val err = rawResult.exceptionOrNull()
            Log.w(TAG, "Voice note second-stage request failed after ${elapsedMs}ms: ${err?.message}")
            return@withContext Result.failure(
                VoiceNoteApiException(err?.localizedMessage ?: "Voice note post-transcription request failed.")
            )
        }

        val parsed = parsePostTranscriptAnalysis(
            verbatimTranscript = cleanTranscript,
            rawAnalysisOutput = rawResult.getOrNull().orEmpty()
        )
        Log.d(
            TAG,
            "Voice note second-stage request succeeded in ${elapsedMs}ms (persian=${containsPersianScript(cleanTranscript)}, translation=${parsed.englishTranslation != null}, corrections=${parsed.corrections.size})"
        )
        Result.success(parsed)
    }

    /**
     * Builds the dedicated Request 2 prompt:
     * - Persian transcript -> 100% focused on natural English translation (`[translation]`).
     * - English transcript -> 100% focused on spoken linguistic analysis (`[correction]`).
     */
    internal fun buildVoiceNoteAnalysisMessages(transcript: String): List<AvalAiClient.Message> {
        val spokenInPersian = containsPersianScript(transcript)
        return if (spokenInPersian) {
            val systemPrompt = """
                You translate raw Speech-to-Text (STT) transcriptions of spontaneous spoken Persian (Farsi) voice notes into natural, idiomatic English.
                Output ONLY in this exact section with no extra commentary before or after:
                [translation]
                <Natural, accurate English translation of everything the speaker said. Do not add notes, explanations, or grammar critiques.>
            """.trimIndent()
            val userPrompt = "Translate this spoken Persian voice note transcription into natural English:\n$transcript"
            listOf(
                AvalAiClient.Message(role = "system", content = systemPrompt),
                AvalAiClient.Message(role = "user", content = userPrompt)
            )
        } else {
            val systemPrompt = """
                You perform linguistic analysis on raw Speech-to-Text (STT) transcriptions of spontaneous spoken English voice notes.
                Output ONLY in this exact section with no extra commentary before or after:
                [correction]
                <A JSON array of ONLY clear, substantive spoken English grammar errors or phrasing improvements: [{"type":"GRAMMAR"|"WORD_CHOICE","original":"exact contiguous substring from transcription","suggested":"corrected or improved words in English","reason":"concise explanation in English"}].
                CRITICAL — STT & SPOKEN SPEECH RULES (DO NOT BE OVERLY SENSITIVE):
                - Never rewrite or output the full transcription; only return exact short substrings in "original".
                - This text comes from automatic Speech-to-Text (STT) of casual speech. Missing or imperfect punctuation marks (., ,, ;, :, ?, !, ', ", -), unseparated run-on clauses, capitalization, and apostrophes are 100% normal STT output — NEVER flag them and NEVER add punctuation marks in "suggested".
                - Natural spoken/colloquial English is completely valid: filler words ("well", "uh", "um", "you know"), contractions ("I'm", "gonna", "wanna"), and spoken ellipsis are normal — NEVER flag them, and do NOT convert casual speech into formal written prose.
                - Use "GRAMMAR" ONLY for clear spoken grammar mistakes that sound genuinely wrong out loud (e.g., wrong verb tense, wrong subject-verb agreement, wrong preposition, broken word order).
                - Use "WORD_CHOICE" ONLY when a word or phrase sounds noticeably unnatural, awkward, or unidiomatic in everyday conversation and has a clearly more natural spoken alternative.
                - Keep each "original" short (1 to 6 words around the specific error). If the utterance already sounds natural in everyday speech, return []. When in doubt, return [].>
            """.trimIndent()
            val userPrompt = "Perform spoken English linguistic analysis on this raw Speech-to-Text voice note transcription:\n$transcript"
            listOf(
                AvalAiClient.Message(role = "system", content = systemPrompt),
                AvalAiClient.Message(role = "user", content = userPrompt)
            )
        }
    }

    /**
     * Parses the Request 2 output while strictly locking `transcription` to [verbatimTranscript]:
     * - If [verbatimTranscript] is Persian: extracts `englishTranslation` and keeps `corrections = emptyList()`.
     * - If [verbatimTranscript] is English: extracts `corrections` and keeps `englishTranslation = null`.
     */
    internal fun parsePostTranscriptAnalysis(
        verbatimTranscript: String,
        rawAnalysisOutput: String
    ): VoiceNoteAnalysis {
        val cleanTranscript = stripPatchMarkers(verbatimTranscript).trim()
        if (isSilenceToken(cleanTranscript)) {
            return VoiceNoteAnalysis(transcription = SILENCE_TRANSCRIPTION)
        }

        val cleaned = rawAnalysisOutput.trim()
        val spokenInPersian = containsPersianScript(cleanTranscript)

        if (spokenInPersian) {
            val translationMatch = Regex(
                "(?is)\\[translation\\]\\s*(.*?)(?=\\[transcription\\]|\\[correction\\]|\\[analysis\\]|\\[linguistic_correction\\]|$)"
            ).find(cleaned)

            val rawTranslation = (translationMatch?.groupValues?.getOrNull(1) ?: cleaned)
                .replace(Regex("(?is)\\[(?:correction|analysis|linguistic_correction)\\].*$"), "")
                .trim()
            val cleanTranslation = stripPatchMarkers(rawTranslation)
            val validEnglishTranslation = cleanTranslation.takeIf { candidate ->
                candidate.isNotBlank() &&
                    !candidate.equals("NONE", ignoreCase = true) &&
                    !candidate.equals("N/A", ignoreCase = true) &&
                    !candidate.equals("NULL", ignoreCase = true) &&
                    LATIN_LETTER_REGEX.containsMatchIn(candidate)
            }

            return VoiceNoteAnalysis(
                transcription = cleanTranscript,
                englishTranslation = validEnglishTranslation,
                corrections = emptyList()
            )
        }

        val correctionMatch = Regex(
            "(?is)\\[(?:correction|analysis|linguistic_correction)\\]\\s*(.*?)(?=\\[transcription\\]|\\[translation\\]|$)"
        ).find(cleaned)

        val correctionRaw = correctionMatch?.groupValues?.getOrNull(1)?.trim()
            ?: if (cleaned.contains("[")) cleaned else ""
        val corrections = if (correctionRaw.isNotBlank()) {
            CorrectionJsonCodec.parseFromModelOutput(
                raw = correctionRaw,
                transcript = cleanTranscript,
                isSpokenStt = true
            )
        } else {
            emptyList()
        }

        return VoiceNoteAnalysis(
            transcription = cleanTranscript,
            englishTranslation = null,
            corrections = corrections
        )
    }

    internal fun parseVoiceNoteAnalysis(rawText: String): VoiceNoteAnalysis {
        val cleaned = rawText.trim()
        if (isSilenceToken(cleaned)) {
            return VoiceNoteAnalysis(transcription = SILENCE_TRANSCRIPTION)
        }

        val transcriptionMatch = Regex(
            "(?is)\\[transcription\\]\\s*(.*?)(?=\\[translation\\]|\\[correction\\]|\\[analysis\\]|\\[linguistic_correction\\]|$)"
        ).find(cleaned)

        val extractedTranscription = transcriptionMatch?.groupValues?.getOrNull(1)?.trim()
            ?: cleaned
                .substringBefore("[translation]")
                .substringBefore("[correction]")
                .substringBefore("[analysis]")
                .substringBefore("[linguistic_correction]")
                .trim()

        val cleanTranscription = stripPatchMarkers(extractedTranscription)
        if (isSilenceToken(cleanTranscription)) {
            return VoiceNoteAnalysis(transcription = SILENCE_TRANSCRIPTION)
        }

        return parsePostTranscriptAnalysis(
            verbatimTranscript = cleanTranscription,
            rawAnalysisOutput = cleaned
        )
    }

    private class VoiceNoteApiException(message: String) : Exception(message)
    private class AudioFileException(message: String) : Exception(message)
}
