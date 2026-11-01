package com.example

import com.example.network.AvalAiClient
import com.example.network.CorrectionType
import com.example.notepad.data.gemini.GeminiService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {
    @Test
    fun testTwoPartTranscriptionAndAnalysisParsing() {
        val client = AvalAiClient()
        val rawModelOutput = """
            [transcription]
            I goes to the library yesterday.
            [analysis]
            [{"type":"GRAMMAR","original":"I goes","suggested":"I went","reason":"Past tense of 'go' is 'went'."}]
        """.trimIndent()

        val result = client.parseTranscriptionAndCorrections(rawModelOutput)
        assertEquals("I goes to the library yesterday.", result.transcript)
        assertEquals(1, result.corrections.size)
        assertEquals(CorrectionType.GRAMMAR, result.corrections[0].type)
        assertEquals("I goes", result.corrections[0].original)
        assertEquals("I went", result.corrections[0].suggested)
    }

    @Test
    fun testNotepadVoiceNotePersianWithTranslation() {
        val service = GeminiService()
        val rawPersianOutput = """
            [transcription]
            سلام من فردا به کتابخانه می‌روم
            [translation]
            Hello, I am going to the library tomorrow.
            [analysis]
            []
        """.trimIndent()

        val parsed = service.parseVoiceNoteAnalysis(rawPersianOutput)
        assertEquals("سلام من فردا به کتابخانه می‌روم", parsed.transcription)
        assertEquals("Hello, I am going to the library tomorrow.", parsed.englishTranslation)
        assertTrue(parsed.corrections.isEmpty())
    }

    @Test
    fun testNotepadVoiceNoteEnglishWithoutTranslation() {
        val service = GeminiService()
        val rawEnglishOutput = """
            [transcription]
            She do not like coffee
            [translation]
            NONE
            [correction]
            [{"type":"GRAMMAR","original":"do not like","suggested":"does not like","reason":"Subject-verb agreement with third-person singular"}]
        """.trimIndent()

        val parsed = service.parseVoiceNoteAnalysis(rawEnglishOutput)
        assertEquals("She do not like coffee", parsed.transcription)
        assertNull(parsed.englishTranslation)
        assertEquals(1, parsed.corrections.size)
        assertEquals("do not like", parsed.corrections[0].original)
        assertEquals("does not like", parsed.corrections[0].suggested)
    }

    @Test
    fun testWholeClipSpeechDetectorHandlesSingleVoiceRecording() {
        val detector = com.example.notepad.audio.AudioRecorderHelper.WholeClipSpeechDetector()
        repeat(10) { detector.onFrameRms(25.0) }
        assertFalse(detector.hasDetectedSpeech)

        repeat(6) { detector.onFrameRms(600.0) }
        assertTrue(detector.hasDetectedSpeech)
    }

    @Test
    fun testStripPatchMarkersForCleanUserDisplay() {
        val taggedText = "{patch 1} i would like to {patch 2} speak to my friend wheneven i go to her house but, am ... {patch 3} not always. Sometimes it just want to be {patch 4} just alone. you know"
        val cleanText = GeminiService.stripPatchMarkers(taggedText)

        assertFalse(cleanText.contains("{patch", ignoreCase = true))
        assertFalse(cleanText.contains("patch 1", ignoreCase = true))
        assertFalse(cleanText.contains("patch 2", ignoreCase = true))
        assertFalse(cleanText.contains("patch 3", ignoreCase = true))
        assertFalse(cleanText.contains("patch 4", ignoreCase = true))
        assertEquals(
            "i would like to speak to my friend wheneven i go to her house but, am ... not always. Sometimes it just want to be just alone. you know",
            cleanText
        )

        // Also test bracketed variation and case insensitivity
        val bracketed = "[PATCH 1] Hello world [patch 2] this is clean"
        assertEquals("Hello world this is clean", GeminiService.stripPatchMarkers(bracketed))
    }

    @Test
    fun testModelEchoedPatchMarkersAreCleanedFromParsedAnalysis() {
        val service = GeminiService()
        val rawModelOutput = """
            [transcription]
            {patch 2} speak to my friend whenever I go to her house
            [translation]
            NONE
            [correction]
            []
        """.trimIndent()

        val parsed = service.parseVoiceNoteAnalysis(rawModelOutput)
        assertEquals("speak to my friend whenever I go to her house", parsed.transcription)
        assertFalse(parsed.transcription.contains("patch", ignoreCase = true))
    }
}

