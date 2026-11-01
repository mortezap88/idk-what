package com.example.notepad.data.gemini

import android.content.Context
import com.example.network.ApiKeyRotation
import com.example.settings.AiModelOption
import com.example.settings.ApiKeyProvider
import com.example.settings.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges Notepad's single-voice 2-request pipeline onto FlashRecall's shared encrypted API-key pools
 * and user-selected STT & TTT models:
 * - Request 1 (Transcription): uses the user's selected STT model (`activeSttOption`).
 * - Request 2 (Translation if Persian / Linguistic analysis if English): uses the user's selected TTT
 *   model (`activeTttOption`) when its provider key is configured, with automatic fallback to any
 *   other configured text provider.
 */
class GeminiPreferences(context: Context) {

    data class ResolvedAnalysisTarget(
        val model: String,
        val provider: ApiKeyProvider,
        val keyCandidates: List<Pair<Int, String>>
    )

    private val settings = AppSettingsRepository(context.applicationContext)

    private val _apiKey = MutableStateFlow(resolveActiveKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    val avalaiApiKey: StateFlow<String> get() = apiKey

    fun activeSttOption(): AiModelOption = runCatching {
        settings.loadSettings().activeSttOption
    }.getOrDefault(AppSettingsRepository.GEMINI_1XAI_OPTION)

    private fun resolveActiveKey(): String = runCatching {
        val provider = activeSttOption().provider
        val keys = settings.getApiKeys(provider)
        if (keys.isEmpty()) {
            ""
        } else {
            keys[settings.activeKeyIndex(provider).coerceIn(0, keys.lastIndex)]
        }
    }.getOrDefault("")

    fun refreshFromSharedSettings() {
        _apiKey.value = resolveActiveKey()
    }

    /** Returns `(poolIndex, apiKey)` pairs in active-first rotation order for [provider]. */
    fun getOrderedKeyCandidates(
        provider: ApiKeyProvider = activeSttOption().provider
    ): List<Pair<Int, String>> = runCatching {
        val keys = settings.getApiKeys(provider)
        if (keys.isEmpty()) return@runCatching emptyList()
        val activeIndex = settings.activeKeyIndex(provider)
        ApiKeyRotation.order(keys.size, activeIndex).map { index -> index to keys[index] }
    }.getOrDefault(emptyList())

    /**
     * Resolves the Request 2 text model & key pool (Persian->English translation OR English linguistic analysis).
     * Prefers the user's active TTT model when its provider has a configured API key, falling back
     * gracefully to Hormouz GLM 5.3 Flash, Houshyar24 GPT 6 Luna, or 1xAI Gemini 3.1 Flash-Lite.
     */
    fun resolvePostAnalysisTarget(): ResolvedAnalysisTarget {
        val current = settings.loadSettings()
        val tttOption = current.activeTttOption
        val tttKeys = getOrderedKeyCandidates(tttOption.provider)
        if (tttKeys.isNotEmpty()) {
            return ResolvedAnalysisTarget(
                model = tttOption.id,
                provider = tttOption.provider,
                keyCandidates = tttKeys
            )
        }

        val hormouzKeys = getOrderedKeyCandidates(ApiKeyProvider.HORMOUZ)
        if (hormouzKeys.isNotEmpty()) {
            return ResolvedAnalysisTarget(
                model = AppSettingsRepository.HORMOUZ_GLM_5_3_FLASH_TTT_MODEL,
                provider = ApiKeyProvider.HORMOUZ,
                keyCandidates = hormouzKeys
            )
        }

        val houshyarKeys = getOrderedKeyCandidates(ApiKeyProvider.HOUSHYAR24)
        if (houshyarKeys.isNotEmpty()) {
            return ResolvedAnalysisTarget(
                model = AppSettingsRepository.HOUSHYAR24_LUNA_TTT_MODEL,
                provider = ApiKeyProvider.HOUSHYAR24,
                keyCandidates = houshyarKeys
            )
        }

        val onexaiKeys = getOrderedKeyCandidates(ApiKeyProvider.ONEXAI)
        if (onexaiKeys.isNotEmpty()) {
            return ResolvedAnalysisTarget(
                model = AppSettingsRepository.ONEXAI_GEMINI_STT_MODEL,
                provider = ApiKeyProvider.ONEXAI,
                keyCandidates = onexaiKeys
            )
        }

        return ResolvedAnalysisTarget(
            model = tttOption.id,
            provider = tttOption.provider,
            keyCandidates = emptyList()
        )
    }

    fun markKeySucceeded(
        poolIndex: Int,
        provider: ApiKeyProvider = activeSttOption().provider
    ) {
        runCatching {
            if (poolIndex != settings.activeKeyIndex(provider)) {
                settings.saveActiveKeyIndex(provider, poolIndex)
                refreshFromSharedSettings()
            }
        }
    }

    fun updateSettings(newKey: String) {
        val trimmed = newKey.trim()
        if (trimmed.isNotEmpty() && trimmed != _apiKey.value) {
            val inferredProvider = when {
                trimmed.startsWith("ng-", ignoreCase = true) -> ApiKeyProvider.HORMOUZ
                trimmed.startsWith("sk-hy24-", ignoreCase = true) -> ApiKeyProvider.HOUSHYAR24
                else -> activeSttOption().provider
            }
            runCatching { settings.addApiKey(inferredProvider, trimmed) }
        }
        refreshFromSharedSettings()
    }
}
