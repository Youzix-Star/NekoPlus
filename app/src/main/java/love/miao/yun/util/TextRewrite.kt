/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.util

import android.content.Context
import love.miao.yun.R
import love.miao.yun.service.MiaoAccessibilityService
import love.miao.yun.text.TextEngine
import love.miao.yun.text.TextPrefs

/**
 * The whole rules step, in one place: read the input box, run the rules over it, write it back.
 *
 * The same shape as [AiRewrite], and for the same reason — a floating button, the send-button
 * assistant and the "after the model answered" hook all have to agree on what running the rules
 * means. The model is not involved here at all: this is the part of the feature that works with no
 * API key, no network, and no waiting.
 */
object TextRewrite {

    /**
     * One engine for the whole app.
     *
     * The `每 N 次` condition counts across calls, so the counter has to outlive a single tap; and
     * the settings page uses an engine of its own, so trying a rule out never advances this one.
     */
    private val engine = TextEngine()

    /** Runs the rules over [text]; [packageName] decides which per-app rules apply. */
    fun apply(context: Context, text: String, packageName: String?): String {
        val rules = TextPrefs.load(context)
        if (rules.rules.none { it.enabled }) return text
        return engine.apply(text, rules, packageName)
    }

    /**
     * Runs the rules over whatever is in the input box.
     *
     * @param onResult called once, **on the main thread**, with whether the text was written back
     *   and a message ready to show the user.
     */
    fun run(
        context: Context,
        service: MiaoAccessibilityService,
        onResult: (written: Boolean, message: String) -> Unit,
    ) {
        val original = service.getCurrentWindowText()
        if (original.isEmpty()) {
            onResult(false, context.getString(R.string.ai_no_input))
            return
        }

        val result = apply(context, original, service.currentPackage())
        if (result == original) {
            onResult(true, context.getString(R.string.text_rules_no_change))
            return
        }

        val written = service.replaceInputText(result)
        onResult(
            written,
            context.getString(
                if (written) R.string.text_rules_applied else R.string.ai_replace_failed,
            ),
        )
    }
}
