/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.util

import android.content.Context
import android.content.SharedPreferences
import love.miao.yun.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-app settings, in and out, as one JSON file.
 *
 * It dumps the preference files this app owns and restores them key by key, rather than listing
 * every setting by hand: a hand-written list silently drops whatever the next feature adds, and
 * this one cannot. Each value carries its type, because `1` on its own cannot be told apart from
 * `1L`, `1.0` or `"1"` once it has been through JSON.
 */
object PrefsBackup {
    /** Bumped when the shape of the file changes, so an old file can be refused politely. */
    const val FORMAT = 1

    /** Only files this app owns: anything else in SharedPreferences is not ours to copy. */
    private val FILES = listOf(
        "ui_prefs",
        "floating_window",
        "ai_config",
        "token_stats",
        "onboarding",
    )

    /** A file name that says what it is and when it was taken. */
    fun suggestedFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        return "MiaoAssistant-config-$stamp.json"
    }

    fun export(context: Context): String {
        val prefs = JSONObject()
        FILES.forEach { name ->
            val keys = JSONObject()
            store(context, name).all.forEach { (key, value) ->
                keys.put(key, encode(value))
            }
            prefs.put(name, keys)
        }

        return JSONObject().apply {
            put("app", "love.miao.yun")
            put("format", FORMAT)
            put("versionName", BuildConfig.VERSION_NAME)
            put("exportedAt", System.currentTimeMillis())
            put("prefs", prefs)
        }.toString(2)
    }

    /**
     * Writes an exported file back into the app's preferences.
     *
     * @return how many settings were restored.
     * @throws IllegalArgumentException when the file is not one of ours.
     */
    fun import(context: Context, json: String): Int {
        val root = JSONObject(json)
        val prefs = root.optJSONObject("prefs")
            ?: throw IllegalArgumentException("这个文件里没有配置")
        val format = root.optInt("format", 0)
        if (format > FORMAT) {
            throw IllegalArgumentException("文件来自更新的版本（format $format）")
        }

        var restored = 0
        for (name in prefs.keys()) {
            if (name !in FILES) continue
            val keys = prefs.optJSONObject(name) ?: continue
            val editor = store(context, name).edit()
            for (key in keys.keys()) {
                val entry = keys.optJSONObject(key) ?: continue
                put(editor, key, entry)
                restored++
            }
            editor.apply()
        }
        return restored
    }

    private fun store(context: Context, name: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun encode(value: Any?): JSONObject = JSONObject().apply {
        when (value) {
            is Boolean -> {
                put("t", "b")
                put("v", value)
            }

            is Int -> {
                put("t", "i")
                put("v", value)
            }

            is Long -> {
                put("t", "l")
                put("v", value)
            }

            is Float -> {
                put("t", "f")
                put("v", value.toDouble())
            }

            is Set<*> -> {
                put("t", "set")
                put("v", JSONArray(value.map { it.toString() }))
            }

            else -> {
                put("t", "s")
                put("v", value?.toString().orEmpty())
            }
        }
    }

    private fun put(editor: SharedPreferences.Editor, key: String, entry: JSONObject) {
        when (entry.optString("t")) {
            "b" -> editor.putBoolean(key, entry.optBoolean("v"))
            "i" -> editor.putInt(key, entry.optInt("v"))
            "l" -> editor.putLong(key, entry.optLong("v"))
            "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
            "set" -> {
                val array = entry.optJSONArray("v")
                val values = buildSet {
                    if (array != null) {
                        for (index in 0 until array.length()) add(array.optString(index))
                    }
                }
                editor.putStringSet(key, values)
            }

            else -> editor.putString(key, entry.optString("v"))
        }
    }
}
