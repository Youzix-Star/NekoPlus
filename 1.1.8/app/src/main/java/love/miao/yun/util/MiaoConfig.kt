package love.miao.yun.util

import android.content.Context

data class Rule(val from: String, val to: String) {
    override fun toString() = "$from=$to"
}

class MiaoConfig {
    var enableAppend: Boolean = true
    var appendText: String = "喵"
    var enableRandomEmoticon: Boolean = true
    var protectSuffix: Boolean = false
    var processingMode: String = MODE_REALTIME
    var voiceInputOptimize: Boolean = true
    var voiceDebounceMs: Long = 1500L
    var customEmoticons: List<String> = emptyList()
    var rules: List<Rule> = emptyList()
    var enabledApps: Set<String> = emptySet()
    // 个性化设置
    var spaceNoMiao: Boolean = false  // 空格不加喵
    var emoticonTriggerMode: String = EMOTICON_TRIGGER_OFF  // 颜文字触发模式
    var emoticonProbability: Float = 0.5f  // 概率模式的概率值 (0.0~1.0)
    var emoticonInterval: Int = 3  // 间隔模式的间隔句数
    var deleteOptimize: Boolean = false  // 内容删除优化
    var qqCatPaw: String = "off"  // QQ猫爪: "off"=关闭, "default"=默认猫爪, 其他=自定义emoji/hex
    var punctuationOptimize: Boolean = false  // 标点模式优化：删除喵前面的标点

    companion object {
        const val MODE_PUNCTUATION = "punctuation"
        const val MODE_REALTIME = "realtime"
        const val MODE_FLOATING_WINDOW = "floating_window"
        const val EMOTICON_TRIGGER_OFF = "off"
        const val EMOTICON_TRIGGER_PROBABILITY = "probability"
        const val EMOTICON_TRIGGER_RANDOM = "random"
        const val EMOTICON_TRIGGER_INTERVAL = "interval"
        private const val PREFS_NAME = "miao_config"

        // 颜文字触发计数器（静态，跨 config 重载保持状态）
        @Volatile
        var emoticonIntervalCounter: Int = 0

        val BUILTIN_EMOTICONS = listOf(
            "^⌯𖥦⌯^ ੭ ^", "⌯'ㅅ'⌯", "=^𖥦^=", "⌯•ㅅ•⌯", "ฅ•̀∀•́ฅ",
            "ฅ ̳͒•ˑ̫• ̳͒ฅ♡", "ฅ(̳•·̫•̳ฅ)♡", "ฅ^••^ฅ", "=^•ω•^=",
            "₍^ >ヮ<^₎", "/ᐠ - ˕ -マ Ⳋ", "ฅ^•ﻌ•^ฅ", "ฅ՞•ﻌ•՞ฅ",
            "(ฅ´ω`ฅ)", "ฅ(*`ω´*)ฅ", "ฅ꒰ ⸝˶• •˶⸝꒱ฅ", "₍˄·͈༝·͈˄*₎◞ ̑̑",
            "!!^⌯𖥦⌯^ ੭!!", "₍^⸝⸝> ·̫ <⸝⸝ ^₎", "ฅ^._.^ฅ",
            "₍🎀˄•͈༝•͈˄₎ฅ˒˒", "^•͈༝•^ฅ", "꒰ఎ(^ . ֑ .^)໒꒱", "ฅ●ω●ฅ",
            "₍⸍⸌·͈༝·͈⸍⸌₎◞", "(>^ω^<)", "ฅ^-﹃-^ฅ", "^ ̳ට ̫ ට ̳^",
            "୧₍˄·͈༝·͈˄₎୨", "^ ̳ᴗ  ̫ ᴗ ̳^", "˓˓ก(⸍⸌̣ʷ̣̫⸍̣⸌₎ค˒˒",
            "ヽ(ฅ≧へ≦)ฅ", "(`･ω･´)ฅ", "(=^･ᴥ･^=)", "(^ω^ฅ)",
            "ฅ(≧▽≦)ฅ", "ฅ(=´▽`=)ฅ", "ヾ((๑˘ㅂ˘๑)ฅ", "(ฅ◑ω◑ฅ)",
            "(๑•̀ω•́ฅ)", "(ฅ>ω<*ฅ)", "(=^.^=)", "(=´ᴥ`)",
            "(=ↀωↀ=)", "(=^-ω-^=)", "ฅ(*°ω°*ฅ)", "ヽ(=^･ω･^=)丿",
            "(^•ᴥ•^)", "( Φ ω Φ )", "(=^x^=)", "ฅ( ̳• ◡ • ̳)ฅ",
            "o( =•ω•= )m", "~o( =∩ω∩= )m", "≡ω≡"
        )

        fun load(ctx: Context): MiaoConfig {
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cfg = MiaoConfig()
            cfg.enableAppend = sp.getBoolean("enable_append", true)
            cfg.appendText = sp.getString("append_text", "喵") ?: "喵"
            cfg.enableRandomEmoticon = sp.getBoolean("enable_emoticon", true)
            cfg.protectSuffix = sp.getBoolean("protect_suffix", false)
            cfg.processingMode = sp.getString("processing_mode", MODE_REALTIME) ?: MODE_REALTIME
            cfg.voiceInputOptimize = sp.getBoolean("voice_input_optimize", false)
            cfg.voiceDebounceMs = sp.getLong("voice_debounce_ms", 1000L)
            // 个性化设置
            cfg.spaceNoMiao = sp.getBoolean("space_no_miao", false)
            cfg.emoticonTriggerMode = sp.getString("emoticon_trigger_mode", EMOTICON_TRIGGER_OFF) ?: EMOTICON_TRIGGER_OFF
            cfg.emoticonProbability = sp.getFloat("emoticon_probability", 0.5f)
            cfg.emoticonInterval = sp.getInt("emoticon_interval", 3)
            cfg.deleteOptimize = sp.getBoolean("delete_optimize", false)
        // 兼容旧版 Boolean 格式
        cfg.qqCatPaw = try {
            sp.getString("qq_cat_paw", "off") ?: "off"
        } catch (_: Exception) {
            // 旧版存的是 Boolean，迁移
            if (sp.getBoolean("qq_cat_paw", false)) "default" else "off"
        }
        cfg.punctuationOptimize = sp.getBoolean("punctuation_optimize", false)

            val rulesStr = sp.getString("rules", "") ?: ""
            if (rulesStr.isNotBlank()) {
                cfg.rules = rulesStr.split("\n").mapNotNull { parseRule(it) }
            }

            val custom = sp.getString("custom_emoticons", "") ?: ""
            cfg.customEmoticons = if (custom.isNotBlank()) {
                custom.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            } else emptyList()

            val apps = sp.getStringSet("enabled_apps", null)
            cfg.enabledApps = apps ?: emptySet()

            return cfg
        }

        fun parseRule(line: String): Rule? {
            val s = line.trim()
            if (s.isEmpty()) return null
            val separators = "=＝→"
            var idx = -1
            for (ch in separators) {
                val p = s.indexOf(ch)
                if (p >= 0 && (idx < 0 || p < idx)) idx = p
            }
            if (idx <= 0) return null
            val from = s.substring(0, idx).trim()
            val to = s.substring(idx + 1).trim()
            if (from.isEmpty()) return null
            return Rule(from, to)
        }

        fun rulesToString(rules: List<Rule>): String =
            rules.joinToString("\n") { "${it.from}=${it.to}" }

        // ── 预设管理 ──
        data class Preset(val name: String, val rulesText: String)

        fun getPresets(ctx: Context): List<Preset> {
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = sp.getString("rule_presets", "") ?: ""
            if (raw.isBlank()) return emptyList()
            return raw.split("\n---PRESET_SEP---\n").mapNotNull { block ->
                val lines = block.split("\n", limit = 2)
                if (lines.size >= 2 && lines[0].isNotBlank()) {
                    Preset(lines[0].trim(), lines[1])
                } else null
            }
        }

        fun savePreset(ctx: Context, name: String, rulesText: String) {
            val presets = getPresets(ctx).toMutableList()
            val idx = presets.indexOfFirst { it.name == name }
            if (idx >= 0) presets[idx] = Preset(name, rulesText)
            else presets.add(Preset(name, rulesText))
            val raw = presets.joinToString("\n---PRESET_SEP---\n") { "${it.name}\n${it.rulesText}" }
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString("rule_presets", raw).apply()
        }

        fun deletePreset(ctx: Context, name: String) {
            val presets = getPresets(ctx).filter { it.name != name }
            val raw = presets.joinToString("\n---PRESET_SEP---\n") { "${it.name}\n${it.rulesText}" }
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString("rule_presets", raw).apply()
        }
    }

    fun save(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().apply {
            putBoolean("enable_append", enableAppend)
            putString("append_text", appendText)
            putBoolean("enable_emoticon", enableRandomEmoticon)
            putBoolean("protect_suffix", protectSuffix)
            putString("processing_mode", processingMode)
            putBoolean("voice_input_optimize", voiceInputOptimize)
            putLong("voice_debounce_ms", voiceDebounceMs)
            // 个性化设置
            putBoolean("space_no_miao", spaceNoMiao)
            putString("emoticon_trigger_mode", emoticonTriggerMode)
            putFloat("emoticon_probability", emoticonProbability)
            putInt("emoticon_interval", emoticonInterval)
            putBoolean("delete_optimize", deleteOptimize)
            putString("qq_cat_paw", qqCatPaw)
            putBoolean("punctuation_optimize", punctuationOptimize)
            putString("rules", rulesToString(rules))
            putString("custom_emoticons", customEmoticons.joinToString("\n"))
            putStringSet("enabled_apps", enabledApps)
            apply()
        }
    }

    fun getActiveEmoticons(): List<String> =
        if (customEmoticons.isNotEmpty()) customEmoticons else BUILTIN_EMOTICONS

    fun shouldTriggerEmoticon(): Boolean {
        if (!enableRandomEmoticon) return false
        return when (emoticonTriggerMode) {
            EMOTICON_TRIGGER_PROBABILITY -> Math.random() < emoticonProbability
            EMOTICON_TRIGGER_RANDOM -> Math.random() < 0.5  // 50%概率
            EMOTICON_TRIGGER_INTERVAL -> {
                emoticonIntervalCounter++
                if (emoticonIntervalCounter >= emoticonInterval) {
                    emoticonIntervalCounter = 0
                    true
                } else false
            }
            else -> true  // OFF模式但enableRandomEmoticon为true时，正常触发
        }
    }

    fun clone(): MiaoConfig {
        val c = MiaoConfig()
        c.enableAppend = enableAppend
        c.appendText = appendText
        c.enableRandomEmoticon = enableRandomEmoticon
        c.protectSuffix = protectSuffix
        c.processingMode = processingMode
        c.voiceInputOptimize = voiceInputOptimize
        c.voiceDebounceMs = voiceDebounceMs
        c.customEmoticons = customEmoticons
        c.rules = rules
        c.enabledApps = enabledApps
        c.spaceNoMiao = spaceNoMiao
        c.emoticonTriggerMode = emoticonTriggerMode
        c.emoticonProbability = emoticonProbability
        c.emoticonInterval = emoticonInterval
        c.deleteOptimize = deleteOptimize
        c.qqCatPaw = qqCatPaw
        c.punctuationOptimize = punctuationOptimize
        return c
    }
}
