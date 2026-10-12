package com.kotosay.app.avatar.grammar

import android.util.Log
import com.kotosay.app.service.GeminiClient
import com.kotosay.app.service.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Restructures a spoken sentence into BIM sign glosses with one Gemini call,
 * falling back to a local rule-based tokenizer when no key is configured or
 * the call fails.
 */
class GeminiSignGrammarService {

    companion object {
        private const val TAG = "GeminiSignGrammar"
        private const val FALLBACK_MODEL = "Offline sign grammar"
    }

    suspend fun restructure(
        sentence: String,
        language: Language = Language.MALAY,
    ): SignGrammarResult = withContext(Dispatchers.IO) {
        if (!GeminiClient.isConfigured) {
            Log.w(TAG, "No Gemini API key — using local fallback tokenization")
            return@withContext fallbackTokenize(sentence)
        }

        try {
            val raw = GeminiClient.complete(
                SignGrammarPrompt.SYSTEM,
                SignGrammarPrompt.userTurn(sentence, language.englishName),
            )
            parse(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini grammar call failed, using fallback: ${e.message}")
            fallbackTokenize(sentence)
        }
    }

    private fun parse(raw: String): SignGrammarResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start in 0 until end) { "No JSON in reply: ${raw.take(200)}" }

        val obj = JSONObject(raw.substring(start, end + 1))
        val tokensJson = obj.optJSONArray("tokens")
        val displayJson = obj.optJSONArray("display")

        val tokens = mutableListOf<String>()
        if (tokensJson != null) {
            for (i in 0 until tokensJson.length()) {
                val t = tokensJson.optString(i).trim().lowercase()
                if (t.isNotEmpty()) tokens.add(t)
            }
        }
        require(tokens.isNotEmpty()) { "Empty tokens list in response" }

        val display = mutableListOf<String>()
        if (displayJson != null) {
            for (i in 0 until displayJson.length()) {
                val d = displayJson.optString(i).trim()
                if (d.isNotEmpty()) display.add(d)
            }
        }
        // The chips pair one-to-one with the clips the avatar plays; a reply
        // whose display list drifted out of step falls back to the glosses.
        if (display.size != tokens.size) {
            display.clear()
            display.addAll(tokens.map { it.replaceFirstChar { c -> c.uppercase() } })
        }

        return SignGrammarResult(
            reasoning = obj.optString("reasoning", "Restructured according to sign language Topic-Comment grammar."),
            tokens = tokens,
            displayTokens = display,
            model = GeminiClient.lastModel ?: "Gemini",
        )
    }

    private fun fallbackTokenize(sentence: String): SignGrammarResult {
        val clean = sentence.trim().lowercase().replace(Regex("[.,?!;]"), "")
        val rawParts = clean.split("\\s+".toRegex())

        val dropWords = setOf("yang", "boleh", "di", "ke", "adalah", "ialah", "sekarang", "kerana", "sangat", "pun", "akan", "encik")
        val filtered = rawParts.filter { it !in dropWords }

        val questionWords = setOf("apa", "siapa", "bila", "mana", "kenapa", "bagaimana")
        val qWord = filtered.find { it in questionWords }
        val finalTokens = if (qWord != null) {
            filtered.filter { it != qWord } + qWord
        } else {
            filtered
        }

        val reasoning = if (qWord != null) {
            "Sign language question syntax: Question marker [${qWord.uppercase()}] shifted to sentence end, spoken glue particles dropped, and Topic-Comment order applied."
        } else {
            "Sign language Topic-Comment syntax: Spoken glue particles dropped and core topic concepts prioritized."
        }

        return SignGrammarResult(
            reasoning = reasoning,
            tokens = finalTokens,
            displayTokens = finalTokens.map { it.replaceFirstChar { c -> c.uppercase() } },
            model = FALLBACK_MODEL,
        )
    }
}
