package com.isuara.app.service

import java.util.Locale

/**
 * Display/speech language for glosses and translated sentences.
 *
 * Two roles, deliberately separate:
 * - Malay is the canonical *data* language: sign glosses, the sentence buffer
 *   and the LLM prompt are all Malay internally. [labelMapKey] is null for it
 *   because the gloss already is the Malay word.
 * - [DISPLAY] (Japanese) is the canonical *display* language: the headline
 *   word, sentence and translation are always shown in it. The language the
 *   user picks only adds a second, smaller row beneath — or nothing, when the
 *   pick is [DISPLAY] itself. The first-launch pick is [DEFAULT].
 *
 * Non-Malay entries name their key in the `translations` object of
 * `label_map.json`.
 */
enum class Language(
    val labelMapKey: String?,
    val locale: Locale,
    /** Plain English name, also what Gemini prompts are told. */
    val englishName: String,
    /** Compact label for the bottom-row chip. */
    val shortLabel: String,
) {
    // Declaration order is the menu order.
    ENGLISH("en", Locale.US, "English", "EN"),
    JAPANESE("ja", Locale.JAPAN, "Japanese", "日"),
    MALAY(null, Locale("ms", "MY"), "Malay", "MS"),
    MANDARIN("zh", Locale.SIMPLIFIED_CHINESE, "Mandarin", "中"),
    TAMIL("ta", Locale("ta", "IN"), "Tamil", "த");

    /** True when a second row should be rendered beneath the Japanese one. */
    val isSecondary: Boolean get() = this != DISPLAY

    /** Picking the display language itself means no second row. */
    val menuLabel: String get() = if (isSecondary) englishName else "$englishName only"

    companion object {
        /** The language every headline is shown in, and spoken as a fallback. */
        val DISPLAY = JAPANESE

        /** Selected on first launch: Japanese headline with English beneath. */
        val DEFAULT = ENGLISH

        fun fromName(name: String?): Language =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
