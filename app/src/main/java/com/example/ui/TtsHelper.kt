package com.example.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** One entry of the installed voices reported by the system TTS engine. */
data class TtsVoiceOption(
    val name: String,
    val localeTag: String,
    val label: String
)

class TtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingRequest: PendingRequest? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var voiceLoadGeneration = 0

    private val _voices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val voices: StateFlow<List<TtsVoiceOption>> = _voices.asStateFlow()

    private data class PendingRequest(val text: String, val voiceName: String?, val rate: Float)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                runCatching { engine.language = Locale.US }
                isReady = true
                refreshVoices()
                pendingRequest?.let { request ->
                    pendingRequest = null
                    speak(request.text, request.voiceName, request.rate)
                }
            }
        }
    }

    /**
     * Engines often report no voices straight after initialisation, so keep asking for a
     * couple of seconds before giving up on the voice picker.
     */
    fun refreshVoices() {
        val generation = ++voiceLoadGeneration
        loadVoices(generation, 0)
    }

    private fun loadVoices(generation: Int, attempt: Int) {
        if (generation != voiceLoadGeneration) return
        val engine = tts ?: return
        val installed = runCatching { engine.voices }.getOrNull().orEmpty()
        if (installed.isNotEmpty()) {
            publishVoices(installed)
            return
        }
        if (attempt < MAX_VOICE_LOAD_ATTEMPTS) {
            mainHandler.postDelayed(
                { loadVoices(generation, attempt + 1) },
                VOICE_LOAD_RETRY_DELAY_MS
            )
        }
    }

    private fun publishVoices(installed: Collection<Voice>) {
        _voices.value = installed
            .asSequence()
            .filter { it.locale.language.isNotBlank() }
            .sortedWith(
                compareBy<Voice>(
                    { it.locale.language != Locale.ENGLISH.language },
                    { it.locale.toLanguageTag() },
                    { it.name }
                )
            )
            .map { voice ->
                TtsVoiceOption(
                    name = voice.name,
                    localeTag = voice.locale.toLanguageTag(),
                    label = "${voice.locale.displayLanguage} · ${voice.locale.toLanguageTag()} · ${voice.name}"
                )
            }
            .toList()
    }

    fun speak(text: String, voiceName: String? = null, rate: Float = 1f) {
        if (text.isBlank()) return
        if (!isReady) {
            pendingRequest = PendingRequest(text, voiceName, rate)
            return
        }
        val engine = tts ?: return
        if (!voiceName.isNullOrBlank()) {
            runCatching {
                engine.voice = engine.voices?.firstOrNull { it.name == voiceName }
            }
        }
        engine.setSpeechRate(rate.coerceIn(0.5f, 2f))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    fun shutdown() {
        voiceLoadGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        pendingRequest = null
        isReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        _voices.value = emptyList()
    }

    private companion object {
        const val MAX_VOICE_LOAD_ATTEMPTS = 12
        const val VOICE_LOAD_RETRY_DELAY_MS = 500L
    }
}
