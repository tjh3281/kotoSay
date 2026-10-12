package com.isuara.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [Translation.ofGlosses], the word-by-word fallback used when every
 * Gemini model fails. The lookup is a stub standing in for
 * `SignPredictor.glossIn`, so no assets are needed.
 */
class TranslationFallbackTest {

    private val dictionary = mapOf(
        "Saya" to mapOf(Language.JAPANESE to "私", Language.ENGLISH to "I / Me"),
        "Sekolah" to mapOf(Language.JAPANESE to "学校", Language.ENGLISH to "School"),
    )

    private fun lookup(gloss: String, language: Language): String? =
        dictionary[gloss]?.get(language)

    @Test
    fun `renders each gloss in the selected language`() {
        val t = Translation.ofGlosses(listOf("Saya", "Sekolah"), ::lookup)
        assertEquals("私、学校", t.forLanguage(Language.JAPANESE))
        assertEquals("I / Me School", t.forLanguage(Language.ENGLISH))
    }

    @Test
    fun `keeps Malay as the canonical rendering`() {
        val t = Translation.ofGlosses(listOf("Saya", "Sekolah"), ::lookup)
        assertEquals("Saya Sekolah", t.ms)
    }

    @Test
    fun `an unmapped gloss stays in Malay`() {
        val t = Translation.ofGlosses(listOf("Saya", "Pergi"), ::lookup)
        assertEquals("私、Pergi", t.forLanguage(Language.JAPANESE))
        assertEquals("Saya Pergi", t.forLanguage(Language.TAMIL))
    }
}
