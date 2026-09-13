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
