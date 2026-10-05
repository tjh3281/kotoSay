package com.isuara.app.service

import android.util.Log
import com.isuara.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single text-generation path for the whole app: Gemini 3.8 Flash.
 *
 * Plain HttpURLConnection against the REST endpoint, the same transport
 * [GeminiTtsService] uses, so the app carries no SDK for one POST.
 *
 * Keys come from `GEMINI_API_KEY` (and the optional `_2`/`_3`) in
 * local.properties, shared with the TTS service. They rotate per call, and a
 * failing key falls through to the next, because a per-key quota 429 is the
 * dominant failure on the free tier and a different key usually succeeds.
 */
object GeminiClient {

    private const val TAG = "GeminiClient"

    const val MODEL = "gemini-3.8-flash"

    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

    private const val TIMEOUT_MS = 30_000

    val apiKeys: List<String> = BuildConfig.GEMINI_API_KEYS
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val isConfigured: Boolean get() = apiKeys.isNotEmpty()

    private val nextKey = AtomicInteger(0)

    /**
     * One completion, returning the model's text.
     *
     * Asks for `application/json` output, so the reply is the bare object the
     * prompt describes rather than prose around it. Blocking: call from IO.
     *
     * @throws IllegalStateException if no key is configured or every key failed.
     */
    fun complete(system: String, user: String): String {
        check(apiKeys.isNotEmpty()) { "GEMINI_API_KEY is not configured" }

        var lastError: Exception? = null
        repeat(apiKeys.size) {
            val key = apiKeys[nextKey.getAndIncrement().mod(apiKeys.size)]
            try {
                return request(system, user, key)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Gemini call failed, trying next key: ${e.message}")
            }
        }
        throw IllegalStateException("all ${apiKeys.size} Gemini key(s) failed", lastError)
    }

    private fun request(system: String, user: String, apiKey: String): String {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put(
                "parts", JSONArray().put(JSONObject().put("text", system)),
            ))
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", user)))
            }))
            put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        }.toString()

        val connection = (URL(ENDPOINT.format(MODEL)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("HTTP $code: ${detail.take(180)}")
            }
            val reply = connection.inputStream.bufferedReader().use { it.readText() }
            return extractText(reply)
        } finally {
            connection.disconnect()
        }
    }

    /** Joins the text parts of the first candidate; throws if it carries none. */
    private fun extractText(reply: String): String {
        val candidate = JSONObject(reply).optJSONArray("candidates")?.optJSONObject(0)
            ?: throw IllegalStateException("no candidates in reply")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IllegalStateException(
                "no content, finishReason=${candidate.optString("finishReason")}"
            )
        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                // Thought summaries are not part of the answer.
                if (part.optBoolean("thought")) continue
                append(part.optString("text"))
            }
        }
        check(text.isNotBlank()) { "empty text in reply" }
        return text
    }
}
