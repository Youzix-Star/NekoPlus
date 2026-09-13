/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.text

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Where the rules live, and how the rules 1.1.8 left behind get in.
 *
 * The configuration is stored as its **own text form**, not as JSON of the model. That is not
 * laziness: the text is a complete, exact representation — rendering then parsing gives back the
 * same rules, which the unit tests check — so there is exactly one format to keep working, the same
 * one the user sees in the editor and can paste to a friend.
 */
object TextPrefs {
    const val PREFS = "text_rules"

    /** The file 1.1.8 wrote, in the same package: an upgrade can still read it. */
    private const val LEGACY_PREFS = "miao_config"

    private const val KEY_CURRENT = "current"
    private const val KEY_PACKS = "packs"
    private const val KEY_AUTO_AFTER_AI = "auto_after_ai"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun legacy(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    /** The user's rules; a fresh install gets 1.1.8's behaviour written out as two editable lines. */
    fun load(context: Context): TextRules {
        val text = prefs(context).getString(KEY_CURRENT, null)
        if (text.isNullOrBlank()) return TextDefaults.starter()
        return TextRuleText.parse(text).config
    }

    fun save(context: Context, rules: TextRules) {
        prefs(context).edit().putString(KEY_CURRENT, TextRuleText.render(rules)).apply()
    }

    /** Whether the rules run over what the model wrote, before it reaches the input box. */
    fun loadAutoAfterAi(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_AFTER_AI, true)

    fun saveAutoAfterAi(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_AFTER_AI, value).apply()
    }

    // ------------------------------------------------------------------ packs

    /** Every saved pack, in the order they were saved. */
    fun loadPacks(context: Context): List<TextPack> {
        val raw = prefs(context).getString(KEY_PACKS, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val text = item.optString("text")
                TextPack(name, TextRuleText.parse(text).config)
            }
        }.getOrDefault(emptyList())
    }

    /** Saves the current rules under [name], replacing a pack of the same name. */
    fun savePack(context: Context, name: String, rules: TextRules) {
        val kept = loadPacks(context).filterNot { it.name == name }
        val all = kept + TextPack(name, rules)
        val json = JSONArray()
        all.forEach { pack ->
            json.put(
                JSONObject().apply {
                    put("name", pack.name)
                    put("text", TextRuleText.render(pack.rules))
                },
            )
        }
        prefs(context).edit().putString(KEY_PACKS, json.toString()).apply()
    }

    fun deletePack(context: Context, name: String) {
        val kept = loadPacks(context).filterNot { it.name == name }
        val json = JSONArray()
        kept.forEach { pack ->
            json.put(
                JSONObject().apply {
                    put("name", pack.name)
                    put("text", TextRuleText.render(pack.rules))
                },
            )
        }
        prefs(context).edit().putString(KEY_PACKS, json.toString()).apply()
    }

    fun register(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    // ------------------------------------------------------------------ 1.1.8

    /** True when 1.1.8 left its settings behind in this app's data. */
    fun hasLegacyConfig(context: Context): Boolean =
        runCatching { legacy(context).all.isNotEmpty() }.getOrDefault(false)

    /**
     * Translates 1.1.8's settings into rules.
     *
     * Everything it could do becomes a line the user can read and change:
     *
     * | 1.1.8 | 这里 |
     * | --- | --- |
     * | 每行 `A=B` 的替换规则 | 原样搬过来，一字不改 |
     * | 加喵（实时模式：每句追加） | `每句末尾加"{后缀}"` |
     * | 加喵（标点/悬浮窗模式：只在末尾） | `末尾加"{后缀}"` |
     * | 空格不加喵 | `每句末尾加"{后缀}"（空格不加）` |
     * | 标点模式优化 | `去掉末尾标点` |
     * | 自定义颜文字 | `{颜文字}` 这个变量的值 |
     * | QQ 猫爪（写死的那串字节） | `首尾包裹"{猫爪}" @com.tencent.mobileqq`，值可改 |
     * | 启用应用 | 每条规则后面的 `@包名` |
     */
    fun importLegacy(context: Context): TextRules? {
        val store = runCatching { legacy(context) }.getOrNull() ?: return null
        if (store.all.isEmpty()) return null

        val variables = LinkedHashMap(TextDefaults.variables)
        val rules = mutableListOf<TextRule>()

        val rulesText = store.getString("rules", "").orEmpty()
        rulesText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            TextRuleText.parse(trimmed).config.rules.firstOrNull()?.let { rules += it }
        }

        val suffix = store.getString("append_text", null)?.takeIf { it.isNotBlank() }
        if (suffix != null) variables[TextDefaults.SUFFIX_NAME] = suffix

        val custom = store.getString("custom_emoticons", "").orEmpty()
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (custom.isNotEmpty()) {
            variables[TextDefaults.EMOTICON_NAME] =
                custom.joinToString(TextDefaults.EMOTICON_SEPARATOR)
        }

        // The apps 1.1.8 was switched on for; every rule it had was limited to them.
        val apps = runCatching { store.getStringSet("enabled_apps", null) }.getOrNull().orEmpty()

        val append = store.getBoolean("enable_append", true)
        if (append && suffix != null) {
            val perSentence = store.getString("processing_mode", null) != "punctuation"
            val skipSpaced = store.getBoolean("space_no_miao", false)
            if (store.getBoolean("punctuation_optimize", false)) {
                rules += TextRule(condition = TextCondition.Always, action = TextAction.TrimTrailingPunctuation, apps = apps)
            }
            rules += TextRule(
                condition = TextCondition.Always,
                action = if (perSentence) {
                    TextAction.SuffixPerSentence("{" + TextDefaults.SUFFIX_NAME + "}", skipSpaced)
                } else {
                    TextAction.Suffix("{" + TextDefaults.SUFFIX_NAME + "}")
                },
                apps = apps,
            )
        }

        if (store.getBoolean("enable_emoticon", false)) {
            rules += TextRule(condition = TextCondition.Always, action = TextAction.Emoticon, apps = apps)
        }

        // The cat paw: 1.1.8 applied it to QQ (and WeChat's keyboard) and nowhere else, with a value
        // nobody could change. Here it is a rule with a value, and the value is the user's.
        val catPaw = store.getString("qq_cat_paw", "off") ?: "off"
        if (catPaw != "off") {
            catPawValue(catPaw)?.let { mark -> variables[TextDefaults.CAT_PAW_NAME] = mark }
            rules += TextRule(
                condition = TextCondition.Always,
                action = TextAction.Wrap("{" + TextDefaults.CAT_PAW_NAME + "}"),
                apps = setOf("com.tencent.mobileqq", "com.tencent.wetype"),
            )
        }

        if (rules.isEmpty() && variables == TextDefaults.variables) return null
        return TextRules(rules, variables)
    }

    /**
     * 1.1.8's cat paw value: the built-in byte sequence, raw hex bytes, or a typed emoji.
     *
     * The built-in one is the reason this feature is being rewritten: it is an invisible sequence
     * that only QQ's font draws, so it is kept as the value when it was in use, but it is now a
     * value like any other.
     */
    private fun catPawValue(stored: String): String? = when {
        stored == "default" -> LEGACY_CAT_PAW
        stored.startsWith("hex:") -> runCatching {
            val bytes = stored.removePrefix("hex:").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            String(bytes, Charsets.UTF_8)
        }.getOrNull()

        stored.isNotBlank() -> stored
        else -> null
    }

    /** The sequence 1.1.8 shipped as "the cat paw": 14 C7 BF 0F 43 00. */
    private val LEGACY_CAT_PAW =
        String(byteArrayOf(0x14, 0xC7.toByte(), 0xBF.toByte(), 0x0F, 0x43, 0x00), Charsets.UTF_8)
}
