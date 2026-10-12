package com.isuara.app.service

import android.util.Log
import com.isuara.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single text-generation path for the whole app.
 *
 * Plain HttpURLConnection against the REST endpoint, the same transport
 * [GeminiTtsService] uses, so the app carries no SDK for one POST.
 *
 * Keys come from `GEMINI_API_KEY` (and the optional `_2`/`_3`) in
 * local.properties, shared with the TTS service. They rotate per call, and a
 * failing key falls through to the next, because a per-key quota 429 is the
 * dominant failure on the free tier and a different key usually succeeds.
 *
 * Models are tried in [MODELS] order. A 503 means the model itself is
 * overloaded for everyone, so no other key can help — the client moves
 * straight to the next model instead of burning a round trip per key.
 */
object GeminiClient {

    private const val TAG = "GeminiClient"

    /**
     * Preferred first. The Lite models lead: 3.8 Flash returned 503 "high
     * demand" on every key for long stretches, and the full Flash models were
     * slower or timed out while the Lite ones answered in about 2-4s.
     */
    val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.6-flash")

    /** The model that served the most recent successful call, for logs and UI. */
    @Volatile
    var lastModel: String? = null
        private set

    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

    /** Bounds the worst case across the whole model chain. */
    private const val TIMEOUT_MS = 15_000

    /** The model is overloaded for every caller; another key will not help. */
    private class Overloaded(message: String) : IllegalStateException(message)

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
     * @throws IllegalStateException if no key is configured or every model and
     *   key failed.
     */
    fun complete(system: String, user: String): String {
        check(apiKeys.isNotEmpty()) { "GEMINI_API_KEY is not configured" }

        var lastError: Exception? = null
        for (model in MODELS) {
            for (attempt in apiKeys.indices) {
                val key = apiKeys[nextKey.getAndIncrement().mod(apiKeys.size)]
                try {
                    val text = request(model, system, user, key)
                    lastModel = model
                    Log.i(TAG, "Gemini [$model] ok")
                    return text
                } catch (e: Overloaded) {
                    lastError = e
                    Log.w(TAG, "$model overloaded, trying next model: ${e.message}")
                    break
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "$model call failed, trying next key: ${e.message}")
                }
            }
        }
        throw IllegalStateException(
            "all ${MODELS.size} Gemini model(s) and ${apiKeys.size} key(s) failed",
            lastError,
        )
    }

    private fun request(model: String, system: String, user: String, apiKey: String): String {
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

        val connection = (URL(ENDPOINT.format(model)).openConnection() as HttpURLConnection).apply {
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
                val message = "HTTP $code: ${detail.take(180)}"
                if (code == HttpURLConnection.HTTP_UNAVAILABLE) throw Overloaded(message)
                throw IllegalStateException(message)
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
