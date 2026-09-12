/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.rule

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * What a rule does to the captured text.
 *
 * The old engine only knew "find and replace"; everything MiaoAssistant expressed with a pile of
 * switches (append to every sentence, keep spaces alone, strip punctuation before the suffix,
 * sprinkle emoticons, wrap in cat paws) is now one of these kinds, so a single ordered pipeline
 * covers all of it.
 */
enum class RuleKind(val id: String) {
    /** Find/replace, either literal or a regular expression with `$1` groups. */
    REPLACE("replace"),

    /** Append [Rule.text] (optionally after every sentence). */
    SUFFIX("suffix"),

    /** Prepend [Rule.text]. */
    PREFIX("prefix"),

    /** Append one entry of [Rule.candidates], chosen randomly. */
    RANDOM_SUFFIX("random"),

    /** Hand the text to the configured AI model; runs outside the synchronous pipeline. */
    AI("ai"),
    ;

    companion object {
        /** Unknown or missing ids fall back to [REPLACE] so old stored rules keep working. */
        fun from(id: String?): RuleKind = entries.firstOrNull { it.id == id } ?: REPLACE
    }
}

/** When a rule is allowed to run. */
enum class RuleTrigger(val id: String) {
    /** Only when the user presses a button in the floating window. */
    MANUAL("manual"),

    /** While typing, on every accessibility text change. */
    AUTO("auto"),

    /** Right after the user types a punctuation mark. */
    PUNCTUATION("punct"),
    ;

    companion object {
        fun from(id: String?): RuleTrigger = entries.firstOrNull { it.id == id } ?: MANUAL
    }
}

/** How [RuleKind.RANDOM_SUFFIX] decides whether to fire. */
enum class RandomMode(val id: String) {
    /** Fire with probability [Rule.probability]. */
    PROBABILITY("probability"),

    /** Fire on a coin flip, i.e. a fixed 50%. */
    HALF("half"),

    /** Fire once every [Rule.interval] sentences. */
    INTERVAL("interval"),
    ;

    companion object {
        fun from(id: String?): RandomMode = entries.firstOrNull { it.id == id } ?: PROBABILITY
    }
}

/**
 * One step of the rewriting pipeline.
 *
 * Only the fields relevant to [kind] are meaningful; the rest keep their defaults. Serialisation is
 * backwards compatible with the JSON written by the previous `RuleManager`: the five legacy keys
 * (`name`, `pattern`, `replacement`, `enabled`, `useRegex`) are unchanged, and a payload without a
 * `kind` key loads as a [RuleKind.REPLACE] rule.
 */
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: RuleKind = RuleKind.REPLACE,
    val enabled: Boolean = true,

    // ---- REPLACE ----
    val pattern: String = "",
    val replacement: String = "",
    val useRegex: Boolean = false,

    // ---- SUFFIX / PREFIX ----
    /** The text to add. */
    val text: String = "",
    /** Append after every sentence instead of once at the very end. */
    val perSentence: Boolean = false,
    /** Only touch segments that contain CJK characters, so kaomoji are left alone. */
    val cjkOnly: Boolean = true,

    // ---- RANDOM_SUFFIX ----
    val candidates: List<String> = emptyList(),
    val probability: Float = 0.5f,
    val interval: Int = 3,
    val randomMode: RandomMode = RandomMode.PROBABILITY,

    // ---- AI ----
    /** Name of the AI preset to run; empty means "use the live AI configuration". */
    val preset: String = "",

    /** Package names this rule applies to; empty means every app. */
    val scope: Set<String> = emptySet(),
    val trigger: RuleTrigger = RuleTrigger.MANUAL,
) {
    /** True when this rule should run for [packageName]. */
    fun matchesApp(packageName: String?): Boolean =
        scope.isEmpty() || (packageName != null && scope.contains(packageName))

    /** Short one-line description of what the rule does, used by the list UI. */
    val summary: String
        get() = when (kind) {
            RuleKind.REPLACE -> if (useRegex) "$pattern → $replacement" else "$pattern = $replacement"
            RuleKind.SUFFIX -> if (perSentence) "每句结尾追加「$text」" else "结尾追加「$text」"
            RuleKind.PREFIX -> "开头插入「$text」"
            RuleKind.RANDOM_SUFFIX -> when (randomMode) {
                RandomMode.PROBABILITY -> "按 ${(probability * 100).toInt()}% 概率追加随机颜文字"
                RandomMode.HALF -> "50% 概率追加随机颜文字"
                RandomMode.INTERVAL -> "每 $interval 句追加一次随机颜文字"
            }
            RuleKind.AI -> if (preset.isBlank()) "调用 AI 改写" else "调用 AI 改写（$preset）"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("kind", kind.id)
        put("enabled", enabled)
        put("pattern", pattern)
        put("replacement", replacement)
        put("useRegex", useRegex)
        put("text", text)
        put("perSentence", perSentence)
        put("cjkOnly", cjkOnly)
        put("candidates", JSONArray(candidates))
        put("probability", probability.toDouble())
        put("interval", interval)
        put("randomMode", randomMode.id)
        put("preset", preset)
        put("scope", JSONArray(scope.toList()))
        put("trigger", trigger.id)
    }

    companion object {
        fun fromJson(json: JSONObject): Rule {
            val candidates = json.optJSONArray("candidates")?.let { array ->
                buildList { for (i in 0 until array.length()) add(array.optString(i)) }
            }.orEmpty()
            val scope = json.optJSONArray("scope")?.let { array ->
                buildList { for (i in 0 until array.length()) add(array.optString(i)) }
            }.orEmpty().toSet()

            return Rule(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = json.optString("name", ""),
                kind = RuleKind.from(json.optString("kind").ifBlank { null }),
                enabled = json.optBoolean("enabled", true),
                pattern = json.optString("pattern", ""),
                replacement = json.optString("replacement", ""),
                useRegex = json.optBoolean("useRegex", false),
                text = json.optString("text", ""),
                perSentence = json.optBoolean("perSentence", false),
                cjkOnly = json.optBoolean("cjkOnly", true),
                candidates = candidates,
                probability = json.optDouble("probability", 0.5).toFloat(),
                interval = json.optInt("interval", 3),
                randomMode = RandomMode.from(json.optString("randomMode").ifBlank { null }),
                preset = json.optString("preset", ""),
                scope = scope,
                trigger = RuleTrigger.from(json.optString("trigger").ifBlank { null }),
            )
        }

        fun listToJson(rules: List<Rule>): JSONArray =
            JSONArray().apply { rules.forEach { put(it.toJson()) } }

        fun listFromJson(array: JSONArray?): List<Rule> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(fromJson(item))
                }
            }
        }
    }
}
