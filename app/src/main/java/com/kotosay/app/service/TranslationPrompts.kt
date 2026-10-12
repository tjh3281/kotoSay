package com.kotosay.app.service

import com.kotosay.app.emotion.EmotionReading

/**
 * Prompts for the Gemini translator.
 *
 * The observed facial expression goes in the per-request user turn rather than
 * in [SYSTEM], so the system prompt stays byte-identical across requests and
 * remains cacheable.
 */
object TranslationPrompts {

    val SYSTEM = """
        You are a professional Bahasa Isyarat Malaysia (BIM) sign language interpreter.

        Rules:
        1. Rearrange and expand the BIM keywords (glosses) into a natural Bahasa Melayu sentence (Subject + Verb + Object).
        2. Infer context and add implied verbs (e.g., "rasa," "mahu"), emotions (e.g., "Gembira", "Sedih", "Kecewa", "Maaf") and grammatical particles.
        3. If a phrase is ambiguous, default to the most direct, standard interpretation.
        4. Produce THE SAME sentence in five languages: Bahasa Melayu, English, Simplified Chinese, Tamil, and Japanese.
        5. Write Tamil in Tamil script, not romanised. Write Japanese in natural Japanese script (kanji and kana), not romaji.
        6. Each language must be ONE single sentence. Do NOT explain your process.

        The input may include the signer's observed facial expression. In sign
        language the face is grammar, not decoration, so when it is given:
        7. Let it shape HOW the sentence is said — word choice, intensity,
           particles, punctuation. It must NEVER add facts the glosses do not
           support. A frightened face on [Polis, Tolong] means "Tolong! Polis!",
           never "Saya takut polis akan menangkap saya."
        8. Register: use standard Bahasa Melayu by default. When the expression is
           high-arousal (fear, anger, urgency, surprise), switch to the natural
           colloquial Malaysian a person would actually shout — "Cepat!",
           "Tolong sikit!", "Jom lah!" — rather than textbook phrasing. Apply the
           same shift in register to the English, Chinese, Tamil and Japanese
           sentences so all five stay consistent with each other.
        9. Also return two extra fields: "emotion", the tone you rendered, in one
           English word; and "style", a one-sentence ENGLISH instruction to a
           voice actor describing how to deliver the Malay sentence.

        Return ONLY a single JSON object. No markdown, no code fences, no commentary, no reasoning:
        {"ms": "<Bahasa Melayu>", "en": "<English>", "zh": "<Simplified Chinese>", "ta": "<Tamil>", "ja": "<Japanese>", "emotion": "<one English word>", "style": "<one English sentence>"}

        Examples:
        Input: [Polis, Siapa, Salah]
        Output: {"ms": "Siapa yang polis salahkan tadi?", "en": "Who did the police blame just now?", "zh": "警察刚才怪谁?", "ta": "காவல்துறை யாரை குற்றம் சாட்டியது?", "ja": "さっき警察は誰のせいにしたの？", "emotion": "neutral", "style": "Say this calmly and clearly, as a straightforward question."}

        Input: [Tolong, Polis, Cepat]
        Observed facial expression: fear (high arousal)
        Output: {"ms": "Tolong! Panggil polis, cepat!", "en": "Help! Call the police, quick!", "zh": "救命!快叫警察!", "ta": "உதவி! சீக்கிரம் போலீஸை கூப்பிடுங்கள்!", "ja": "助けて！早く警察を呼んで！", "emotion": "fear", "style": "Say this urgently and fearfully, fast and loud, with a strained voice."}
    """.trimIndent()

    /**
     * Builds the per-request user turn.
     *
     * [emotion] is appended as a plain line rather than folded into the gloss
     * list so a model cannot mistake it for another sign to translate.
     */
    fun userTurn(words: List<String>, emotion: EmotionReading? = null): String = buildString {
        append("Input: $words")
        emotionLine(emotion)?.let { append("\n").append(it) }
        append("\nOutput:")
    }

    /**
     * The observed-expression line, or null when nothing was observed.
     *
     * Says "high arousal" rather than a raw number because that is the
     * distinction rule 8 turns on, and models follow a named category far more
     * reliably than a threshold they have to apply themselves. Low-confidence
     * readings are dropped entirely: a hedged hint is worse than no hint, since
     * the model cannot tell how much to discount it.
     */
    private fun emotionLine(emotion: EmotionReading?): String? {
        if (emotion == null || emotion.confidence < MIN_EMOTION_CONFIDENCE) return null
        val arousal = if (emotion.isHighArousal) "high arousal" else "low arousal"
        return "Observed facial expression: ${emotion.label.descriptorEn} ($arousal)"
    }

    /**
     * Below this the reading is too weak to steer a sentence with.
     *
     * The expression classifier returns a full distribution and will always name
     * a winner; when that winner is barely ahead, acting on it would put
     * confident emotion into a sentence on the strength of noise.
     */
    private const val MIN_EMOTION_CONFIDENCE = 0.35f
}
