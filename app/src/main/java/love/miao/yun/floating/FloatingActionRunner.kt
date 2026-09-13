/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.floating

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import love.miao.yun.MainActivity
import love.miao.yun.R
import love.miao.yun.service.MiaoAccessibilityService
import love.miao.yun.util.AiRewrite
import love.miao.yun.util.DebugDump

/**
 * Runs a button's chain of actions, in order.
 *
 * Steps are chained by callback rather than fired together, because not every action finishes at
 * once: the point of a chain is that the next step sees what the previous one produced — rewriting
 * with the model and then pressing send, for instance.
 *
 * Shared by the floating window and the send-button assistant, so a step cannot mean one thing on a
 * floating button and something else on the assistant's.
 */
class FloatingActionRunner(
    private val context: Context,
    private val host: Host,
) {

    /** The parts of running a chain that only the button's own owner can do. */
    interface Host {
        /** Show or clear progress on the button that is running. */
        fun onBusy(busy: Boolean)

        /** Press the chat app's own send button, for a chain that ends in 发送. */
        fun onSend()

        /** Take the buttons off the screen; the last step of a chain, so nothing follows it. */
        fun onClose()
    }

    fun run(actions: List<FloatingAction>) {
        if (actions.isEmpty()) return
        host.onBusy(true)

        fun step(index: Int) {
            val action = actions.getOrNull(index)
            if (action == null) {
                host.onBusy(false)
                return
            }
            perform(action) {
                if (index == actions.lastIndex) host.onBusy(false) else step(index + 1)
            }
        }

        step(0)
    }

    /** One step. [onDone] must run exactly once, unless the step takes the buttons away. */
    private fun perform(action: FloatingAction, onDone: () -> Unit) {
        when (action) {
            FloatingAction.AiModify -> aiModify(onDone)

            FloatingAction.Capture -> {
                captureToClipboard()
                onDone()
            }

            FloatingAction.Send -> {
                host.onSend()
                onDone()
            }

            FloatingAction.OpenApp -> {
                openApp()
                onDone()
            }

            FloatingAction.DumpUi -> {
                dumpScreen()
                onDone()
            }

            // The last step of all: the buttons are gone, so there is nothing left to continue to.
            FloatingAction.Close -> host.onClose()
        }
    }

    /** Capture the focused field, let the model rewrite it, and write the result back. */
    private fun aiModify(onDone: () -> Unit) {
        val service = MiaoAccessibilityService.instance()
        if (service == null) {
            toast(context.getString(R.string.ai_need_accessibility))
            onDone()
            return
        }
        AiRewrite.run(context, service) { _, message ->
            toast(message)
            onDone()
        }
    }

    /** Copy whatever the focused input box holds onto the clipboard. */
    private fun captureToClipboard() {
        val service = MiaoAccessibilityService.instance()
        if (service == null) {
            toast(context.getString(R.string.ai_need_accessibility))
            return
        }
        val text = service.getCurrentWindowText()
        if (text.isEmpty()) {
            toast(context.getString(R.string.ai_no_input))
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.notif_title), text),
        )
        toast(context.getString(R.string.floating_copied, text.length))
    }

    /** Writes everything the accessibility service can see to a file the settings page reads. */
    private fun dumpScreen() {
        val service = MiaoAccessibilityService.instance()
        if (service == null) {
            toast(context.getString(R.string.ai_need_accessibility))
            return
        }
        val saved = DebugDump.save(context, service.dumpScreen())
        toast(if (saved) "界面元素已导出，去「设置 → 调试」查看" else "导出失败")
    }

    private fun openApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Results are announced as toasts: a bare button has no panel to write a status line into. */
    private fun toast(message: String) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
