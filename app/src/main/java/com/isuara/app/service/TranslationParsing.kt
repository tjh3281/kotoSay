package com.isuara.app.service

import org.json.JSONObject

/**
 * Provider-neutral parsing of LLM replies.
 *
 * Tolerant of replies that are not a bare object: models can wrap it in
 * markdown fences or put prose before it. Both are handled by scanning for the
 * last brace-balanced object.
 */
object TranslationParsing {

    /**
     * The last complete, brace-balanced `{...}` object in [raw], or null.
     *
     * Deliberately not "first { to last }": if prose before the object
     * contains a brace, the naive span swallows it and parsing fails. A single
     * pass tracking string state, escapes and depth finds the real object
     * regardless of what precedes it.
     *
     * Takes the LAST object so that a model which restates the prompt's example
     * before answering does not have its example picked as the answer.
     */
    private fun jsonSpan(raw: String): String? {
        var depth = 0
        var start = -1
        var last: String? = null
        var inString = false
        var escaped = false

        for (i in raw.indices) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                c == '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && start >= 0) last = raw.substring(start, i + 1)
                    }
                }
            }
        }
        return last
    }

    private fun parse(raw: String, what: String): JSONObject {
        val span = jsonSpan(raw)
        require(span != null) { "no JSON object in $what: ${raw.take(200)}" }
        return try {
            JSONObject(span)
        } catch (e: Exception) {
            throw IllegalArgumentException("malformed JSON in $what: ${raw.take(200)}", e)
        }
    }

    /**
     * Pulls the four-language translation out of a raw model reply.
     *
     * The `emotion` and `style` keys are deliberately NOT required. They are an
     * enrichment: a model that omits them, or that predates the prompt asking
     * for them, must still yield a usable translation. Requiring them would turn
     * a cosmetic shortfall into a total failure.
     *
     * @throws IllegalArgumentException if no complete object is present or any
     *   of the four languages is missing or blank.
     */
    fun extractTranslation(raw: String): Translation {
        val obj = parse(raw, "reply")
        val ms = obj.optString("ms").trim()
        val en = obj.optString("en").trim()
        val zh = obj.optString("zh").trim()
        val ta = obj.optString("ta").trim()
        require(ms.isNotEmpty() && en.isNotEmpty() && zh.isNotEmpty() && ta.isNotEmpty()) {
            "missing language in reply: ms=${ms.isNotEmpty()} en=${en.isNotEmpty()} " +
                "zh=${zh.isNotEmpty()} ta=${ta.isNotEmpty()}"
        }
        return Translation(
            ms = ms,
            en = en,
            zh = zh,
            ta = ta,
            emotion = obj.optString("emotion").trim().ifEmpty { null },
            style = obj.optString("style").trim().ifEmpty { null },
        )
    }
}
