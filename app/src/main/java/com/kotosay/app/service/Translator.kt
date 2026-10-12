package com.kotosay.app.service

import com.kotosay.app.emotion.EmotionReading
import kotlinx.coroutines.flow.StateFlow

/**
 * One sentence rendered in every supported language.
 *
 * The translator returns all five in a single call, so switching the display
 * language afterwards is instant and costs nothing.
 */
data class Translation(
    val ms: String,
    val en: String,
    val zh: String,
    val ta: String,
    val ja: String,
    /**
     * The tone the model judged it was rendering, in its own words.
     *
     * Optional, and defaulted so [ofRawGlosses] and every existing caller are
     * unaffected: a model that ignores the instruction must still produce a
     * usable translation rather than failing the whole request.
     */
    val emotion: String? = null,
    /**
     * A one-sentence English delivery directive for the voice engine.
     *
     * English regardless of the spoken language — that is Google's guidance for
     * Gemini-TTS style prompts, and it is also what keeps this field usable when
     * the user has switched the display to Tamil or Mandarin.
     */
    val style: String? = null,
) {
    fun forLanguage(language: Language): String = when (language) {
        Language.MALAY -> ms
        Language.ENGLISH -> en
        Language.MANDARIN -> zh
        Language.TAMIL -> ta
        Language.JAPANESE -> ja
    }


    companion object {
        /** Fallback used when no translator is configured or the call fails. */
        fun ofRawGlosses(text: String) =
            Translation(ms = text, en = text, zh = text, ta = text, ja = text)

        /**
         * Fallback that keeps the selected language when Gemini is unreachable:
         * each gloss rendered word by word via [glossIn], so a Japanese user
         * hears Japanese words rather than Malay. Not a sentence, but the right
         * language. A gloss with no mapping stays in Malay.
         */
        fun ofGlosses(
            words: List<String>,
            glossIn: (String, Language) -> String?,
        ): Translation {
            fun render(language: Language, separator: String) =
                words.joinToString(separator) { glossIn(it, language) ?: it }
            return Translation(
                ms = words.joinToString(" "),
                en = render(Language.ENGLISH, " "),
                zh = render(Language.MANDARIN, "、"),
                ta = render(Language.TAMIL, " "),
                ja = render(Language.JAPANESE, "、"),
            )
        }
    }
}

/**
 * Turns an ordered list of detected BIM glosses into a natural sentence.
 *
 * Implementations throw on failure rather than returning an error string, so a
 * caller can tell a real translation from a failure without inspecting the text.
 */
interface Translator {
    /**
     * @param emotion what the signer's face was doing while they signed, or null
     *   when no face was read. It steers register, intensity and particles — how
     *   the sentence is said — and must never add facts the glosses do not
     *   support. Defaulted so callers that have no emotion source are unchanged.
     */
    suspend fun translate(words: List<String>, emotion: EmotionReading? = null): Translation

    /**
     * Progress through the pipeline, for the UI to display while waiting.
     *
     * Mirrors how SignPredictor exposes its state, so the UI collects it with
     * the pattern already used elsewhere.
     */
    val stage: StateFlow<TranslationStage>
}
