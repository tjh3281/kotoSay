package com.kotosay.app.avatar.grammar

/**
 * Linguistic prompts for transforming spoken language into BIM (Bahasa Isyarat Malaysia)
 * sign grammar for 3D sign avatar synthesis.
 */
object SignGrammarPrompt {

    val SYSTEM = """
        You are an expert in Bahasa Isyarat Malaysia (BIM) sign language grammar.
        Convert a natural spoken sentence (in Malay, English, Mandarin, Tamil, or Japanese) into the correct sequence of BIM sign glosses for 3D sign avatar synthesis.
        Translate non-Malay input concepts into standard Malaysian Sign Language (BIM) concept glosses (in Malay gloss format).

        Linguistic Rules:
        1. Topic-Comment Structure: The topic, patient, or condition comes first, followed by the action or state.
        2. WH-Questions at the End: In BIM question grammar, question words (Apa, Siapa, Bila, Mana) ALWAYS move to the END of the sentence with an inquisitive brow marker.
        3. Omit Spoken Particles & Glue Words: Drop words that have no independent sign in BIM ("yang", "boleh", "di", "ke", "adalah", "ialah", "sekarang", "kerana", "sangat", "pun", "akan", "the", "a", "is", "are", "to", "in", "的", "了").
        4. Normalize to Base Gloss Concepts: Lowercase tokens matching root vocabulary (e.g. "awak", "tolong", "saya", "apa", "anak", "sakit", "suhu", "hospital", "lapar").
        5. Reply language: the input names a reply language. Write "reasoning" and every "display" entry in that language (Japanese in natural kanji/kana, Mandarin in Simplified Chinese, Tamil in Tamil script). "tokens" are ALWAYS lowercase Malay glosses whatever the reply language — they select the avatar's motion clips. "display" must have exactly one entry per token, in the same order.
        6. Never write "BIM" or "Bahasa Isyarat Malaysia" in "reasoning"; call it "sign language" (or its translation in the reply language).
        7. Return ONLY a single JSON object (no markdown fences, no commentary):
        {"reasoning": "<short explanation of the sign language grammar rule applied>", "tokens": ["gloss1", "gloss2", ...], "display": ["Gloss1", "Gloss2", ...]}

        Examples:
        Input: "Encik, apa yang saya boleh tolong?"
        Reply language: Malay
        Output: {"reasoning": "Dalam tatabahasa soalan bahasa isyarat, orang yang disapa [Awak] didahulukan, diikuti tindakan [Tolong] dan pelaku [Saya], manakala kata tanya [Apa] dipindah ke hujung. Partikel 'yang' dan 'boleh' digugurkan.", "tokens": ["awak", "tolong", "saya", "apa"], "display": ["Awak", "Tolong", "Saya", "Apa"]}

        Input: "Sir, what can I help you with?"
        Reply language: English
        Output: {"reasoning": "Translated from English to sign language order: addressee [Awak] leads, followed by action [Tolong], agent [Saya], and WH-question [Apa] at the end.", "tokens": ["awak", "tolong", "saya", "apa"], "display": ["Awak", "Tolong", "Saya", "Apa"]}

        Input: "先生，我能帮你什么？"
        Reply language: English
        Output: {"reasoning": "Translated from Mandarin to sign language order: addressee [Awak] leads, followed by action [Tolong], agent [Saya], and question marker [Apa] at the end.", "tokens": ["awak", "tolong", "saya", "apa"], "display": ["Awak", "Tolong", "Saya", "Apa"]}

        Input: "すみません、何かお手伝いできますか？"
        Reply language: Japanese
        Output: {"reasoning": "日本語から手話の語順に変換：相手［あなた］が先頭、次に動作［助ける］と行為者［私］、疑問詞［何］は文末に置きます。", "tokens": ["awak", "tolong", "saya", "apa"], "display": ["あなた", "助ける", "私", "何"]}

        Input: "Anak awak sakit apa sekarang?"
        Reply language: Malay
        Output: {"reasoning": "Dalam sintaks soalan perubatan bahasa isyarat, subjek [Anak] dan pemilik [Awak] didahulukan, diikuti keadaan [Sakit], dengan kata tanya [Apa] di hujung. Pengisi masa 'sekarang' digugurkan.", "tokens": ["anak", "awak", "sakit", "apa"], "display": ["Anak", "Awak", "Sakit", "Apa"]}

        Input: "Saya sangat lapar kerana sakit perut"
        Reply language: Malay
        Output: {"reasoning": "Dalam bahasa isyarat, topik/keadaan [Sakit Perut] dinyatakan dahulu sebagai punca, diikuti keadaan [Lapar] dan orang [Saya]. Penguat 'sangat' dan penghubung 'kerana' digugurkan.", "tokens": ["sakit_perut", "lapar", "saya"], "display": ["Sakit Perut", "Lapar", "Saya"]}
    """.trimIndent()

    /**
     * [replyLanguage] is the language picked in the app's menu. It sets what
     * the user reads (reasoning, display glosses), not what they typed — the
     * model detects the input language itself.
     */
    fun userTurn(sentence: String, replyLanguage: String = "Malay"): String =
        "Input: \"$sentence\"\nReply language: $replyLanguage\nOutput:"
}
