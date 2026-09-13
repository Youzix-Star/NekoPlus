/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.sendassist

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * One chat app the assistant knows how to work with.
 *
 * @param sendIds the view ids to try first; both were captured from a real device, and both are
 *   obfuscated per release, so they are a head start rather than the only way in.
 */
data class SendTarget(
    val packageName: String,
    val label: String,
    val sendIds: List<String>,
)

/**
 * The supported apps.
 *
 * Only WeChat and QQ for now, on purpose: both were captured from a real device, and guessing at a
 * third app's layout without one is how a feature ends up sending messages nobody asked for.
 */
val SEND_TARGETS: List<SendTarget> = listOf(
    SendTarget("com.tencent.mm", "微信", listOf("com.tencent.mm:id/bql")),
    SendTarget("com.tencent.mobileqq", "QQ", listOf("com.tencent.mobileqq:id/send_btn")),
)

/** The target for a package name, or null when that app is not supported. */
fun sendTargetFor(packageName: String?): SendTarget? =
    SEND_TARGETS.firstOrNull { it.packageName == packageName }

/**
 * What the send button is called on screen.
 *
 * The ids above move between releases; the label is the stable part, so a button whose id is not
 * recognised is still found by its text.
 */
const val SEND_LABEL = "发送"

/** What the assistant does when its button is tapped. */
enum class SendAssistAction(val id: String, val label: String) {
    /** Capture the input box, have the model rewrite it, write it back. */
    AiModify("ai", "AI 修改"),

    /** Capture the input box and put it on the clipboard. */
    Capture("copy", "复制文本"),

    /** Write the whole screen's nodes to the debug file, for working out a new app's layout. */
    DumpUi("dump", "导出界面元素"),
    ;

    companion object {
        fun from(id: String?): SendAssistAction =
            entries.firstOrNull { it.id == id } ?: AiModify
    }
}

/**
 * Everything one app's assistant needs: whether it is on, what it does, and how it looks.
 *
 * Per app rather than global, because the two apps' input bars are not the same shape and a size
 * that sits well above WeChat's send button can end up covering QQ's.
 */
data class SendAssistConfig(
    val packageName: String,
    val enabled: Boolean = false,
    val action: String = SendAssistAction.AiModify.id,
    val autoSend: Boolean = false,
    val sizeDp: Int = SendAssistPrefs.DEFAULT_SIZE_DP,
    /** Corner radius; `-1` means "follow the size", which is a third of it. */
    val cornerDp: Int = -1,
    val opacity: Int = SendAssistPrefs.DEFAULT_OPACITY,
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
) {
    val actionEntry: SendAssistAction get() = SendAssistAction.from(action)

    /** The radius actually allowed at this size, and never more than half of it. */
    val effectiveCornerDp: Int
        get() = (if (cornerDp < 0) sizeDp / 3 else cornerDp).coerceIn(0, sizeDp / 2)

    /** "AI 修改 · 自动发送 · 56 dp · 92% · 偏移 +0/-8" */
    val summary: String
        get() = buildString {
            append(actionEntry.label)
            if (autoSend) append(" · 自动发送")
            append(" · ${sizeDp} dp · $opacity%")
            if (offsetXDp != 0 || offsetYDp != 0) {
                append(" · 偏移 ${signed(offsetXDp)}/${signed(offsetYDp)}")
            }
        }

    private fun signed(value: Int): String = if (value > 0) "+$value" else "$value"
}

/**
 * Settings for the send-button assistant: one record per supported app.
 *
 * Its own preference file, so the whole feature can be backed up, cleared or ignored as one thing.
 */
object SendAssistPrefs {
    const val PREFS = "send_assist"

    const val MIN_SIZE_DP = 24
    const val MAX_SIZE_DP = 72
    const val DEFAULT_SIZE_DP = 40
    const val MIN_OPACITY = 30
    const val MAX_OPACITY = 100
    const val DEFAULT_OPACITY = 92

    /** How far the button may be nudged off its default spot, in either direction. */
    const val MAX_OFFSET_DP = 64

    private const val KEY_PREFIX = "config:"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Off until asked for: an overlay that appears on its own in a chat app needs consent. */
    fun load(context: Context, packageName: String): SendAssistConfig {
        val raw = prefs(context).getString(KEY_PREFIX + packageName, null) ?: return SendAssistConfig(packageName)
        return runCatching {
            val json = JSONObject(raw)
            SendAssistConfig(
                packageName = packageName,
                enabled = json.optBoolean("enabled", false),
                action = SendAssistAction.from(json.optString("action")).id,
                autoSend = json.optBoolean("autoSend", false),
                sizeDp = json.optInt("size", DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
                cornerDp = json.optInt("corner", -1),
                opacity = json.optInt("opacity", DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY),
                offsetXDp = json.optInt("offsetX", 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP),
                offsetYDp = json.optInt("offsetY", 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP),
            )
        }.getOrElse { SendAssistConfig(packageName) }
    }

    fun save(context: Context, config: SendAssistConfig) {
        val json = JSONObject().apply {
            put("enabled", config.enabled)
            put("action", config.action)
            put("autoSend", config.autoSend)
            put("size", config.sizeDp)
            put("corner", config.cornerDp)
            put("opacity", config.opacity)
            put("offsetX", config.offsetXDp)
            put("offsetY", config.offsetYDp)
        }
        prefs(context).edit().putString(KEY_PREFIX + config.packageName, json.toString()).apply()
    }

    /** Whether any app has the assistant switched on; when none has, it is never created at all. */
    fun anyEnabled(context: Context): Boolean =
        SEND_TARGETS.any { load(context, it.packageName).enabled }

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
}
