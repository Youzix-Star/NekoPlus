/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.sendassist

import android.content.Context
import android.content.SharedPreferences

/**
 * The apps whose send button this feature knows how to find, and the view ids it looks for first.
 *
 * Only WeChat and QQ for now, on purpose: both were captured from a real device (see the debug
 * dump), and guessing at a third app's layout without one is how a feature ends up sending
 * messages nobody asked for.
 */
val SEND_TARGETS: Map<String, List<String>> = mapOf(
    "com.tencent.mm" to listOf("com.tencent.mm:id/bql"),
    "com.tencent.mobileqq" to listOf("com.tencent.mobileqq:id/send_btn"),
)

/**
 * What the send button is called on screen.
 *
 * The ids above are obfuscated per release and will move; the label is the stable part, so a
 * button whose id is not recognised is still found by its text.
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
 * Settings for the send-button assistant.
 *
 * Its own preference file, so the whole feature can be backed up, cleared or ignored as one thing.
 */
object SendAssistPrefs {
    private const val PREFS = "send_assist"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ACTION = "action"
    private const val KEY_AUTO_SEND = "auto_send"
    private const val KEY_SIZE = "size_dp"
    private const val KEY_CORNER = "corner_dp"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_OFFSET_X = "offset_x_dp"
    private const val KEY_OFFSET_Y = "offset_y_dp"

    const val MIN_SIZE_DP = 24
    const val MAX_SIZE_DP = 72
    const val DEFAULT_SIZE_DP = 40
    const val MIN_OPACITY = 30
    const val MAX_OPACITY = 100
    const val DEFAULT_OPACITY = 92

    /** How far the button may be nudged off its default spot, in either direction. */
    const val MAX_OFFSET_DP = 64

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Off until asked for: an overlay that appears on its own in a chat app needs consent. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun action(context: Context): SendAssistAction =
        SendAssistAction.from(prefs(context).getString(KEY_ACTION, null))

    fun setAction(context: Context, action: SendAssistAction) {
        prefs(context).edit().putString(KEY_ACTION, action.id).apply()
    }

    /**
     * Whether to press send afterwards.
     *
     * Off by default even with the assistant on: rewriting a draft and sending it are different
     * decisions, and the second one is not undoable.
     */
    fun autoSend(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_SEND, false)

    fun setAutoSend(context: Context, autoSend: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SEND, autoSend).apply()
    }

    /** Edge length of the button. */
    fun sizeDp(context: Context): Int =
        prefs(context).getInt(KEY_SIZE, DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)

    fun setSizeDp(context: Context, value: Int) {
        val clamped = value.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
        prefs(context).edit().putInt(KEY_SIZE, clamped).putInt(KEY_CORNER, cornerFor(context, clamped)).apply()
    }

    /** Corner radius; a third of the edge until the user says otherwise. */
    fun cornerDp(context: Context): Int =
        prefs(context).getInt(KEY_CORNER, sizeDp(context) / 3)
            .coerceIn(0, sizeDp(context) / 2)

    fun setCornerDp(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_CORNER, value.coerceIn(0, sizeDp(context) / 2)).apply()
    }

    fun opacity(context: Context): Int =
        prefs(context).getInt(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)

    fun setOpacity(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_OPACITY, value.coerceIn(MIN_OPACITY, MAX_OPACITY)).apply()
    }

    /**
     * Where the button sits, relative to its default spot — right-aligned with the send button,
     * one button's height above it. `+x` is to the right and `+y` is downwards, so a negative `y`
     * lifts it further up.
     */
    fun offsetXDp(context: Context): Int =
        prefs(context).getInt(KEY_OFFSET_X, 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP)

    fun setOffsetXDp(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_OFFSET_X, value.coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP))
            .apply()
    }

    fun offsetYDp(context: Context): Int =
        prefs(context).getInt(KEY_OFFSET_Y, 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP)

    fun setOffsetYDp(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_OFFSET_Y, value.coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP))
            .apply()
    }

    private fun cornerFor(context: Context, size: Int): Int =
        prefs(context).getInt(KEY_CORNER, size / 3).coerceIn(0, size / 2)

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
