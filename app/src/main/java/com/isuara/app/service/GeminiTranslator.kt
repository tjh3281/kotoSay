package com.isuara.app.service

import android.util.Log
import com.isuara.app.emotion.EmotionReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * GeminiTranslator — the four-language translation from one Gemini call.
 *
 * Failures propagate as exceptions — callers distinguish them from a real
 * translation by catching, not by inspecting the returned text.
 */
class GeminiTranslator : Translator {

    companion object {
        private const val TAG = "GeminiTranslator"
    }

    private val _stage = MutableStateFlow(TranslationStage.IDLE)
    override val stage: StateFlow<TranslationStage> = _stage.asStateFlow()

    override suspend fun translate(
        words: List<String>,
        emotion: EmotionReading?,
    ): Translation = withContext(Dispatchers.IO) {
        require(words.isNotEmpty()) { "no glosses to translate" }

        // One retry: a model that rambles once usually complies on a re-ask, and
        // a second round trip is cheaper than falling back to raw glosses.
        var lastError: Exception? = null
        _stage.value = TranslationStage.TRANSLATING
        try {
            repeat(2) { attempt ->
                try {
                    val raw = GeminiClient.complete(
                        TranslationPrompts.SYSTEM,
                        TranslationPrompts.userTurn(words, emotion),
                    )
                    val translation = TranslationParsing.extractTranslation(raw)
                    Log.i(TAG, "Translation [${GeminiClient.MODEL}] $words -> ${translation.ms}")
                    return@withContext translation
                } catch (e: IllegalArgumentException) {
                    lastError = e
                    Log.w(TAG, "Unparseable reply on attempt ${attempt + 1}: ${e.message}")
                }
            }
        } finally {
            _stage.value = TranslationStage.IDLE
        }
        throw lastError ?: IllegalStateException("translation failed")
    }
}
