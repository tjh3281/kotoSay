package com.isuara.app.service

import android.content.Context

/**
 * Remembers the signer's chosen [Language] across app restarts.
 *
 * Someone whose primary language is not Malay should not have to re-select it
 * on every launch.
 */
class LanguagePreference(context: Context) {

    private companion object {
        const val PREFS = "isuara_prefs"
        // Bumped from "display_language" when Japanese became the default, so
        // installs that had already saved Malay start in Japanese once.
        const val KEY_LANGUAGE = "display_language_v2"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(): Language = Language.fromName(prefs.getString(KEY_LANGUAGE, null))

    fun set(language: Language) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }
}
