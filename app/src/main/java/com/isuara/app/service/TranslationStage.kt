package com.isuara.app.service

/** Where the translation currently is, for display while the user waits. */
enum class TranslationStage(val label: String) {
    IDLE(""),
    TRANSLATING("Translating with Gemini…"),
}
