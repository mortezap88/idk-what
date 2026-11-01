package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioTranscoder
import com.example.network.AvalAiClient
import com.example.network.CorrectionType
import com.example.network.GeminiRepository
import com.example.network.TutorResponseMode
import com.example.notepad.audio.AudioRecorderHelper
import com.example.notepad.data.gemini.GeminiService
import com.example.settings.AppSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceNotePipelineTest {

    @Test
    fun wholeClipSpeechDetector_detectsSpeechAcrossPausesWithoutSlicingAndRejectsAmbientNoise() {
        val detector = AudioRecorderHelper.WholeClipSpeechDetector()

        // 40 frames (800ms) of low ambient hum -> never triggers speech
        repeat(40) {
            detector.onFrameRms(rms = 32.0)
        }
        assertFalse(detector.hasDetectedSpeech)

        // Brief spoken phrase followed by long pause and second phrase in one single recording
        repeat(15) {
            detector.onFrameRms(rms = 850.0)
        }
        repeat(60) {
            detector.onFrameRms(rms = 30.0)
        }
        repeat(12) {
            detector.onFrameRms(rms = 920.0)
        }
        assertTrue(detector.hasDetectedSpeech)
    }

    @Test
    fun notepadTwoRequestPipeline_persianRequestsTranslationOnly_englishRequestsLinguisticAnalysisOnly() {
        val service = GeminiService()

        // Case 1: Persian transcript -> Request 2 asks ONLY for [translation] and ignores any corrections
        val persianTranscript = "دیروز رفتم کتابخانه تا برای امتحان مطالعه کنم"
        val persianMessages = service.buildVoiceNoteAnalysisMessages(persianTranscript)
        assertTrue(persianMessages[0].content.contains("[translation]"))
        assertFalse(persianMessages[0].content.contains("[correction]"))

        val persianParsed = service.parsePostTranscriptAnalysis(
            verbatimTranscript = persianTranscript,
            rawAnalysisOutput = """
                [translation]
                Yesterday I went to the library to study for the exam.
                [correction]
                [{"type":"WORD_CHOICE","original":"رفتم کتابخانه","suggested":"به کتابخانه رفتم","reason":"Formal word order"}]
            """.trimIndent()
        )
        assertEquals(persianTranscript, persianParsed.transcription)
        assertEquals(
            "Yesterday I went to the library to study for the exam.",
            persianParsed.englishTranslation
        )
        assertTrue(
            "Persian voice notes must only produce translation and zero corrections",
            persianParsed.corrections.isEmpty()
        )

        // Case 2: English transcript -> Request 2 asks ONLY for [correction] and ignores any translation
        val englishTranscript = "Yesterday I go to the store with my friend"
        val englishMessages = service.buildVoiceNoteAnalysisMessages(englishTranscript)
        assertTrue(englishMessages[0].content.contains("[correction]"))
        assertFalse(englishMessages[0].content.contains("[translation]"))

        val englishParsed = service.parsePostTranscriptAnalysis(
            verbatimTranscript = englishTranscript,
            rawAnalysisOutput = """
                [correction]
                [{"type":"GRAMMAR","original":"I go","suggested":"I went","reason":"Use past tense with Yesterday."}]
            """.trimIndent()
        )
        assertEquals(englishTranscript, englishParsed.transcription)
        assertNull(englishParsed.englishTranslation)
        assertEquals(1, englishParsed.corrections.size)
        assertEquals(CorrectionType.GRAMMAR, englishParsed.corrections.single().type)
        assertEquals("I went", englishParsed.corrections.single().suggested)
    }

    @Test
    fun mainTutorTwoPartOutput_instructsAndParsesAnswerPlusLinguisticCorrection() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = GeminiRepository(AppSettingsRepository(context))

        val instruction = repo.tutoringFormattingInstruction(
            systemInstruction = "Help the learner understand cell division.",
            isSpokenInput = true,
            responseMode = TutorResponseMode.INITIAL_REVIEW_EVALUATION
        )
        assertTrue(instruction.contains("[answer]"))
        assertTrue(instruction.contains("[linguistic_correction]"))

        val parsed = repo.parseTutoringResponse(
            raw = """
                [answer]
                [lang:en]
                [green]You identified mitosis as producing two genetically identical daughter cells.[/green]
                [red]Specify that ==sister chromatids== separate during **anaphase**, not metaphase.[/red]
                [linguistic_correction]
                [{"type":"GRAMMAR","original":"chromatids separates","suggested":"chromatids separate","reason":"Plural subject 'chromatids' takes 'separate'."}]
            """.trimIndent(),
            userText = "Mitosis makes two identical cells and sister chromatids separates in metaphase",
            isSpokenStt = true
        )

        assertTrue(parsed.answer.contains("anaphase"))
        assertFalse(parsed.answer.contains("[linguistic_correction]"))
        assertEquals(1, parsed.corrections.size)
        assertEquals("chromatids separate", parsed.corrections.single().suggested)
    }

    @Test
    fun audioTranscoder_preserveWavKeepsLargeWavLosslessWithoutSwitchingToM4a() {
        // 3 seconds of 16kHz 16-bit mono PCM = 96,000 bytes (> 48 KiB threshold!)
        val threeSecondsPcm = ByteArray(16000 * 2 * 3) { idx -> ((idx * 17) and 0x7F).toByte() }
        val wavBytes = AudioTranscoder.pcmToWav(threeSecondsPcm, 16000, 1, 16)
        assertTrue(wavBytes.size > 48 * 1024)

        val payload = AudioTranscoder.prepareAudioForGemini(
            audioBytes = wavBytes,
            preserveWav = true
        )
        assertEquals("audio/wav", payload.mimeType)
        assertEquals(wavBytes.size, payload.bytes.size)
    }

    @Test
    fun avalAiPromptAndModelResolution_honorCustomModelAndNeverForbidLegitimateRepetition() {
        assertEquals(
            "gemini-2.5-pro-exp",
            AvalAiClient.resolveSttModel("gemini-2.5-pro-exp")
        )
        assertEquals(
            AppSettingsRepository.ONEXAI_GEMINI_STT_MODEL,
            AvalAiClient.resolveSttModel("   ")
        )

        val prompt = AvalAiClient.buildSingleVoiceTranscriptionPrompt()
        assertFalse(prompt.contains("{patch", ignoreCase = true))
        assertFalse(prompt.contains("Do not repeat words", ignoreCase = true))
        assertTrue(prompt.contains("repeats a word", ignoreCase = true))

        val service = GeminiService()
        val postParsed = service.parsePostTranscriptAnalysis(
            verbatimTranscript = "Yesterday I go to the store",
            rawAnalysisOutput = """
                [transcription]
                Yesterday I went to the store.
                [translation]
                NONE
                [correction]
                [{"type":"GRAMMAR","original":"go","suggested":"went","reason":"Use past tense with Yesterday."}]
            """.trimIndent()
        )
        // Post-analysis must NEVER overwrite the verbatim transcript!
        assertEquals("Yesterday I go to the store", postParsed.transcription)
        assertEquals(1, postParsed.corrections.size)
        assertEquals("went", postParsed.corrections.single().suggested)
    }
}
