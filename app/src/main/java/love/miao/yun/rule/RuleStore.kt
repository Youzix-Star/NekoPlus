/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.rule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence for the rule pipeline.
 *
 * The preference file, the keys and the JSON shape all match what the previous `RuleManager` wrote,
 * so a user's existing rules load unchanged: a payload without a `kind` becomes a
 * [RuleKind.REPLACE] rule. New fields are additive.
 */
object RuleStore {
    private const val PREFS = "text_rules"
    private const val KEY_RULES = "rules"
    private const val KEY_PRESETS = "rule_presets"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Rule> = runCatching {
        val raw = prefs(context).getString(KEY_RULES, null) ?: return emptyList()
        Rule.listFromJson(JSONArray(raw))
    }.getOrElse { emptyList() }

    fun save(context: Context, rules: List<Rule>) {
        runCatching {
            prefs(context).edit()
                .putString(KEY_RULES, Rule.listToJson(rules).toString())
                .apply()
        }
    }

    // ------------------------------------------------------------------ presets

    fun getPresetNames(context: Context): List<String> = runCatching {
        val json = presetsJson(context)
        buildList {
            val keys = json.keys()
            while (keys.hasNext()) add(keys.next())
        }.sorted()
    }.getOrElse { emptyList() }

    fun loadPreset(context: Context, name: String): List<Rule> = runCatching {
        Rule.listFromJson(presetsJson(context).optJSONArray(name))
    }.getOrElse { emptyList() }

    fun savePreset(context: Context, name: String, rules: List<Rule>) {
        runCatching {
            val json = presetsJson(context)
            json.put(name, Rule.listToJson(rules))
            putPresetsJson(context, json)
        }
    }

    fun deletePreset(context: Context, name: String): Boolean = runCatching {
        val json = presetsJson(context)
        if (!json.has(name)) return false
        json.remove(name)
        putPresetsJson(context, json)
        true
    }.getOrElse { false }

    private fun presetsJson(context: Context): JSONObject = runCatching {
        val raw = prefs(context).getString(KEY_PRESETS, null) ?: return JSONObject()
        JSONObject(raw)
    }.getOrElse { JSONObject() }

    private fun putPresetsJson(context: Context, json: JSONObject) {
        prefs(context).edit().putString(KEY_PRESETS, json.toString()).apply()
    }
}

/**
 * Runs the pipeline for one capture.
 *
 * Keeps the per-rule sentence counters that [RandomMode.INTERVAL] needs, so the engine itself stays
 * a pure function. Java callers use [run] directly; this object is the only stateful part.
 */
object RuleRunner {
    private val sentenceCounters = mutableMapOf<String, Int>()

    fun run(text: String?, rules: List<Rule>, packageName: String?): RuleEngine.Result {
        val context = RuleContext(
            packageName = packageName,
            sentenceCount = { ruleId -> sentenceCounters[ruleId] ?: 0 },
        )
        val result = RuleEngine.apply(text, rules, context)
        for (rule in rules) {
            if (rule.kind == RuleKind.SUFFIX || rule.kind == RuleKind.RANDOM_SUFFIX) {
                sentenceCounters[rule.id] = (sentenceCounters[rule.id] ?: 0) + 1
            }
        }
        return result
    }

    fun reset() = sentenceCounters.clear()
}
