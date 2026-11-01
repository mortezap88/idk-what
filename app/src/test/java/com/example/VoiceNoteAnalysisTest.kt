package com.example

import com.example.network.AvalAiClient
import com.example.network.CorrectionJsonCodec
import com.example.network.CorrectionType
import com.example.notepad.data.gemini.GeminiService
import com.example.settings.AppSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceNoteAnalysisTest {

    private val service = GeminiService()

    @Test
    fun supportedModels_splitCleanlyIntoTttAndSttWithHormouzModels() {
        val tttIds = AppSettingsRepository.TTT_MODEL_OPTIONS.map { it.id }
        val sttIds = AppSettingsRepository.STT_MODEL_OPTIONS.map { it.id }

        assertEquals(
            listOf(
                AppSettingsRepository.HOUSHYAR24_LUNA_TTT_MODEL,
                AppSettingsRepository.HORMOUZ_GLM_5_3_FLASH_TTT_MODEL,
                AppSettingsRepository.HORMOUZ_LUNA_TTT_MODEL
            ),
            tttIds
        )
        assertEquals(
            listOf(
                AppSettingsRepository.HORMOUZ_MAI_TRANSCRIBE_2_STT_MODEL,
                AppSettingsRepository.ONEXAI_GEMINI_STT_MODEL
            ),
            sttIds
        )
        assertEquals(tttIds + sttIds, AppSettingsRepository.SUPPORTED_MODELS.map { it.id })
    }

    @Test
    fun hormouzProviderKeysModelsAndEndpoints_persistAndRouteAccurately() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = AppSettingsRepository(context)
        repo.clearApiKeys(com.example.settings.ApiKeyProvider.HORMOUZ)

        repo.addApiKey(com.example.settings.ApiKeyProvider.HORMOUZ, "ng-test-hormouz-key-9876")
        repo.saveTttModel(AppSettingsRepository.HORMOUZ_GLM_5_3_FLASH_TTT_MODEL)
        repo.saveSttModel(AppSettingsRepository.HORMOUZ_MAI_TRANSCRIBE_2_STT_MODEL)

        val loaded = repo.loadSettings()
        assertTrue(loaded.hasHormouzApiKey)
        assertEquals(listOf("••••9876"), loaded.hormouzKeyPreviews)
        assertEquals(AppSettingsRepository.HORMOUZ_GLM_5_3_FLASH_TTT_MODEL, loaded.activeTttModel)
        assertEquals(com.example.settings.ApiKeyProvider.HORMOUZ, loaded.tttProvider)
        assertEquals("low", loaded.activeTttOption.reasoningEffort)
        assertEquals(AppSettingsRepository.HORMOUZ_MAI_TRANSCRIBE_2_STT_MODEL, loaded.activeSttModel)
        assertEquals(com.example.settings.ApiKeyProvider.HORMOUZ, loaded.sttProvider)
        assertTrue(loaded.activeSttOption.isDedicatedSttOnly)

        assertEquals(
            listOf("https://api.hormouz.net/v1/chat/completions"),
            AvalAiClient.chatCompletionUrlsFor(com.example.settings.ApiKeyProvider.HORMOUZ)
        )
        assertEquals(
            listOf("https://api.hormouz.net/v1/audio/transcriptions"),
            AvalAiClient.audioTranscriptionUrlsFor(com.example.settings.ApiKeyProvider.HORMOUZ)
        )
        assertTrue(
            AvalAiClient.isDedicatedAudioTranscriptionModel(
                AppSettingsRepository.HORMOUZ_MAI_TRANSCRIBE_2_STT_MODEL
            )
        )

        val glmPayload = org.json.JSONObject(
            AvalAiClient.buildChatCompletionsPayload(
                model = AppSettingsRepository.HORMOUZ_GLM_5_3_FLASH_TTT_MODEL,
                messages = listOf(AvalAiClient.Message(role = "user", content = "Explain mitosis")),
                temperature = 0.7,
                stream = true,
                reasoningEffort = AppSettingsRepository.LOCKED_REASONING_EFFORT
            )
        )
        assertEquals("z-ai/glm-5.3-flash", glmPayload.getString("model"))
        assertEquals("low", glmPayload.getString("reasoning_effort"))

        repo.clearApiKeys(com.example.settings.ApiKeyProvider.HORMOUZ)
    }

    @Test
    fun englishVoiceNote_highlightsGrammarAndImprovements_withoutTranslation() {
        val raw = """
            [transcription]
            Yesterday I go to the store for buying milk.
            [translation]
            NONE
            [correction]
            [
              {"type":"GRAMMAR","original":"go","suggested":"went","reason":"Use past tense with 'Yesterday'."},
              {"type":"WORD_CHOICE","original":"for buying","suggested":"to buy","reason":"Use the infinitive of purpose 'to buy'."}
            ]
        """.trimIndent()

        val result = service.parseVoiceNoteAnalysis(raw)

        assertEquals("Yesterday I go to the store for buying milk.", result.transcription)
        assertNull(result.englishTranslation)
        assertEquals(2, result.corrections.size)
        assertEquals(CorrectionType.GRAMMAR, result.corrections[0].type)
        assertEquals("go", result.corrections[0].original)
        assertEquals("went", result.corrections[0].suggested)
        assertEquals(CorrectionType.WORD_CHOICE, result.corrections[1].type)
        assertEquals("for buying", result.corrections[1].original)
        assertEquals("to buy", result.corrections[1].suggested)
    }

    @Test
    fun englishVoiceNote_neverShowsTranslationEvenIfModelOutputsText() {
        val raw = """
            [transcription]
            Let's see if this work.
            [translation]
            بیایید ببینیم این کار می‌کند یا نه
            [correction]
            [{"type":"GRAMMAR","original":"this work","suggested":"this works","reason":"Singular 'this' takes 'works'."}]
        """.trimIndent()

        val result = service.parseVoiceNoteAnalysis(raw)

        assertEquals("Let's see if this work.", result.transcription)
        assertNull(result.englishTranslation)
        assertEquals(1, result.corrections.size)
        assertEquals("this works", result.corrections.single().suggested)
    }

    @Test
    fun persianVoiceNote_includesEnglishTranslationOnlyWithoutLinguisticCorrections() {
        val raw = """
            [transcription]
            دیروز رفتم فروشگاه که شیر بخرم
            [translation]
            Yesterday I went to the store to buy milk.
            [correction]
            [{"type":"WORD_CHOICE","original":"فروشگاه که","suggested":"به فروشگاه تا","reason":"Sounds slightly more formal and clear."}]
        """.trimIndent()

        val result = service.parseVoiceNoteAnalysis(raw)

        assertEquals("دیروز رفتم فروشگاه که شیر بخرم", result.transcription)
        assertEquals("Yesterday I went to the store to buy milk.", result.englishTranslation)
        assertEquals(0, result.corrections.size)

        val englishResult = service.parsePostTranscriptAnalysis(
            verbatimTranscript = "Yesterday I go to the store to buy milk",
            rawAnalysisOutput = """
                [correction]
                [{"type":"GRAMMAR","original":"I go","suggested":"I went","reason":"Use past tense with Yesterday."}]
            """.trimIndent()
        )
        assertNull(englishResult.englishTranslation)
        assertEquals(1, englishResult.corrections.size)
        assertEquals(CorrectionType.GRAMMAR, englishResult.corrections.single().type)

        val (updatedText, remaining) = CorrectionJsonCodec.applyCorrectionToText(
            text = englishResult.transcription,
            corrections = englishResult.corrections,
            correctionId = englishResult.corrections.single().id
        )
        assertEquals("Yesterday I went to the store to buy milk", updatedText)
        assertEquals(0, remaining.size)
    }

    @Test
    fun avalAiScribeV2Stt_buildsMultipartBodySniffsContainersAndTrimsSilenceSafely() {
        val multipart = AvalAiClient.buildScribeV2MultipartBody(
            audioBytes = byteArrayOf(1, 2, 3, 4),
            mimeType = "audio/mp4",
            fileName = "rec_12345.m4a",
            model = AppSettingsRepository.SCRIBE_V2_STT_MODEL,
            includeFastScribeOptions = true
        )
        // file + model + response_format=json + tag_audio_events=false + timestamps_granularity=none
        assertEquals(5, multipart.size)

        val fallbackMultipart = AvalAiClient.buildScribeV2MultipartBody(
            audioBytes = byteArrayOf(1, 2, 3, 4),
            mimeType = "audio/mp4",
            fileName = "rec_12345.m4a",
            model = AppSettingsRepository.SCRIBE_V2_STT_MODEL,
            includeFastScribeOptions = false
        )
        assertEquals(3, fallbackMultipart.size)

        val m4aHeader = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte())
        val wavBytes = com.example.audio.AudioRecorderManager.pcmToWav(ByteArray(640))
        assertEquals("audio/mp4", AvalAiClient.sniffAudioMimeType(m4aHeader))
        assertEquals("audio/wav", AvalAiClient.sniffAudioMimeType(wavBytes))

        // Build 2.0s of 16kHz PCM: 0.6s ambient hum -> 0.5s speech -> 0.9s trailing hum
        val sampleRate = 16000
        val totalSamples = sampleRate * 2
        val pcmBuffer = java.nio.ByteBuffer.allocate(totalSamples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            val sample: Short = when (i) {
                in (sampleRate * 6 / 10) until (sampleRate * 11 / 10) ->
                    (kotlin.math.sin(i * 0.25) * 6000.0).toInt().toShort()
                else ->
                    (if (i % 2 == 0) 45 else -45).toShort() // >30 old threshold, so old trimmer failed to trim!
            }
            pcmBuffer.putShort(sample)
        }
        val trimmedPcm = com.example.audio.AudioRecorderManager.trimAndCompactSpeechPcm(pcmBuffer.array(), sampleRate)
        // Should trim away the 0.6s leading + 0.9s trailing hum while keeping the 0.5s speech + ~0.28s safety margins
        assertTrue("Expected trimmed speech to be much shorter than raw 2.0s clip", trimmedPcm.size in (sampleRate)..(sampleRate * 2))

        val transcriptionResponse = """
            {"text":"دیروز رفتم فروشگاه که شیر بخرم"}
        """.trimIndent()
        val pureSilenceEvent = """
            {"text":"(صدای پس‌زمینه)"}
        """.trimIndent()

        assertEquals(
            "دیروز رفتم فروشگاه که شیر بخرم",
            AvalAiClient.extractTranscriptionText(transcriptionResponse)
        )
        assertEquals("", AvalAiClient.extractTranscriptionText(pureSilenceEvent))
        assertEquals("wav", AvalAiClient.resolveInputAudioFormat("audio/wav", null))
        assertEquals("m4a", AvalAiClient.resolveInputAudioFormat("audio/mp4", null))
        assertEquals("rec_12345.m4a", AvalAiClient.resolveAudioFileName("audio/mp4", "rec_12345.m4a"))
    }

    @Test
    fun avalAiClient_extractsCompletionTextAndClassifiesRotatableErrors() {
        val responseJson = """
            {
              "id": "chatcmpl-123",
              "model": "deepseek-chat",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "[answer]\nWell done!\n[correction]\n[]"
                }
              }]
            }
        """.trimIndent()

        assertEquals(
            "[answer]\nWell done!\n[correction]\n[]",
            AvalAiClient.extractChatCompletionText(responseJson)
        )
        assertTrue(AvalAiClient.isRotatableKeyStatus(401, "invalid_api_key"))
        assertTrue(AvalAiClient.isRotatableKeyStatus(402, "insufficient credit"))
        assertTrue(AvalAiClient.isRotatableKeyStatus(429, "rate limit"))
    }

    @Test
    fun persianSttVoiceNote_ignoresPunctuationBoundariesAndColloquialCopulaDrops() {
        val raw = """
            [transcription]
            خب این یه تست هدف خاصی ندارم
            [translation]
            Well, this is a test; I don't have any specific goal.
            [correction]
            [
              {
                "type": "GRAMMAR",
                "original": "این یه تست هدف خاصی ندارم",
                "suggested": "این یه تسته؛ هدف خاصی ندارم",
                "reason": "The first clause is missing the colloquial copula (\"تسته\") and needs a boundary before the second clause."
              }
            ]
        """.trimIndent()

        val result = service.parseVoiceNoteAnalysis(raw)

        assertEquals("خب این یه تست هدف خاصی ندارم", result.transcription)
        assertEquals("Well, this is a test; I don't have any specific goal.", result.englishTranslation)
        assertEquals(0, result.corrections.size)
    }

    @Test
    fun correctionCodec_ignoresMarksCapitalizationContractionsAndColloquialForms() {
        val transcript = "well i dont know im gonna go خونه today"
        val rawCorrections = """
            [
              {"type":"GRAMMAR","original":"well","suggested":"Well,","reason":"Capitalize and add a comma."},
              {"type":"GRAMMAR","original":"i dont","suggested":"I don't","reason":"Capitalize I and add apostrophe."},
              {"type":"WORD_CHOICE","original":"im gonna","suggested":"I am going to","reason":"More formal phrasing."},
              {"type":"WORD_CHOICE","original":"خونه","suggested":"خانه","reason":"Formal Persian spelling."},
              {"type":"GRAMMAR","original":"today","suggested":"today.","reason":"Add a period at the end."}
            ]
        """.trimIndent()

        val filtered = CorrectionJsonCodec.parseFromModelOutput(
            raw = rawCorrections,
            transcript = transcript,
            isSpokenStt = true
        )

        assertEquals(0, filtered.size)
    }

    @Test
    fun avalAiStreamDelta_ignoresNullContentChunksAndExtractsTwoPartTutoringResponse() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = com.example.network.GeminiRepository(AppSettingsRepository(context))

        val ssePayloads = listOf(
            """{"id":"1","choices":[{"index":0,"delta":{"role":"assistant","content":null}}]}""",
            """{"id":"1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":"Thinking..."}}]}""",
            """{"id":"1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":"Evaluating answer..."}}]}""",
            """{"id":"1","choices":[{"index":0,"delta":{"content":"[ans"}}]}""",
            """{"id":"1","choices":[{"index":0,"delta":{"content":"wer]\n"}}]}""",
            """{"id":"1","choices":[{"index":0,"delta":{"content":"آفرین، بخش نقش آن کاملاً درست است."}}]}""",
            """{"id":"1","choices":[{"index":0,"delta":{"content":"\n[linguistic"}}]}""",
            """{"id":"1","choices":[{"index":0,"delta":{"content":"_correction]\n"}}]}""",
            """{"id":"1","choices":[{"index":0,"delta":{"content":"[{\"type\":\"WORD_CHOICE\",\"original\":\"عمودی\",\"suggested\":\"عمود بر هم\",\"reason\":\"Use 'عمود بر هم' for perpendicular cylinders.\"}]"}}]}"""
        )

        val accumulated = StringBuilder()
        val partialAnswers = mutableListOf<String>()
        for (payload in ssePayloads) {
            val delta = AvalAiClient.extractStreamDeltaContent(payload)
            if (delta.isNotEmpty()) {
                accumulated.append(delta)
                partialAnswers.add(
                    repo.parseTutoringResponse(
                        raw = accumulated.toString(),
                        userText = "دو استوانه عمودی در تقسیم یاخته نقش دارند",
                        isSpokenStt = true
                    ).answer
                )
            }
        }

        // Null-content chunks must produce zero text and never emit "null" or "[answer]"
        assertTrue(partialAnswers.none { it.contains("null") || it.contains("[answer]") || it.contains("[linguistic") })

        val finalParsed = repo.parseTutoringResponse(
            raw = accumulated.toString(),
            userText = "دو استوانه عمودی در تقسیم یاخته نقش دارند",
            isSpokenStt = true
        )
        assertEquals("آفرین، بخش نقش آن کاملاً درست است.", finalParsed.answer)
        assertEquals(1, finalParsed.corrections.size)
        assertEquals("عمودی", finalParsed.corrections.single().original)
        assertEquals("عمود بر هم", finalParsed.corrections.single().suggested)
    }

    @Test
    fun avalAiPayload_locksReasoningEffortToLow() {
        val payloadJson = AvalAiClient.buildChatCompletionsPayload(
            model = AppSettingsRepository.HAIKU_TTT_MODEL,
            messages = listOf(AvalAiClient.Message(role = "user", content = "Hello")),
            temperature = 0.7,
            stream = true
        )
        val parsed = org.json.JSONObject(payloadJson)
        assertEquals(AppSettingsRepository.HAIKU_TTT_MODEL, parsed.getString("model"))
        assertEquals("low", parsed.getString("reasoning_effort"))
        assertEquals(0.7, parsed.getDouble("temperature"), 0.0001)
        assertTrue(parsed.getBoolean("stream"))

        val noTempPayload = AvalAiClient.buildChatCompletionsPayload(
            model = AppSettingsRepository.HAIKU_TTT_MODEL,
            messages = listOf(AvalAiClient.Message(role = "user", content = "Hello")),
            temperature = null,
            stream = false
        )
        val parsedNoTemp = org.json.JSONObject(noTempPayload)
        assertEquals("low", parsedNoTemp.getString("reasoning_effort"))
        assertTrue(!parsedNoTemp.has("temperature"))
        assertTrue(AvalAiClient.isTemperatureRejectedForReasoning("Unsupported parameter: temperature is not supported when reasoning is enabled"))
        assertTrue(AvalAiClient.isReasoningEffortUnsupported("Unrecognized request argument supplied: reasoning_effort"))
    }

    @Test
    fun liveSpeechFrameFilter_preservesPreRollTrimsLeadingSilenceAndCapsLongPauses() {
        val frameBytes = 640 // 20ms @ 16kHz 16-bit mono
        val filter = com.example.audio.AudioRecorderManager.LiveSpeechFrameFilter(frameBytes)
        var emittedFrames = 0
        val dummyChunk = ByteArray(frameBytes) { 1 }

        // 25 frames (500ms) of quiet ambient room hum -> nothing emitted yet
        repeat(25) {
            filter.onPcmChunk(dummyChunk, frameBytes, rms = 45.0) { _, _ ->
                emittedFrames++
            }
        }
        assertEquals(0, emittedFrames)
        assertTrue(!filter.hasDetectedSpeech)

        // 10 frames (200ms) of active speech -> flushes 7 pre-roll frames + 10 speech frames = 17
        repeat(10) {
            filter.onPcmChunk(dummyChunk, frameBytes, rms = 2400.0) { _, _ ->
                emittedFrames++
            }
        }
        assertTrue(filter.hasDetectedSpeech)
        assertEquals(17, emittedFrames)

        // 40 frames (800ms) of mid-sentence silence -> capped to 18 frames (~360ms)
        repeat(40) {
            filter.onPcmChunk(dummyChunk, frameBytes, rms = 40.0) { _, _ ->
                emittedFrames++
            }
        }
        assertEquals(17 + 18, emittedFrames)

        // Verify normalized audio level bounds for Listening animation
        assertEquals(0f, com.example.audio.AudioRecorderManager.rmsToNormalizedLevel(60.0), 0.0001f)
        assertTrue(com.example.audio.AudioRecorderManager.rmsToNormalizedLevel(2200.0) in 0.4f..0.95f)
        assertEquals(1f, com.example.audio.AudioRecorderManager.rmsToNormalizedLevel(12000.0), 0.0001f)
    }
}
