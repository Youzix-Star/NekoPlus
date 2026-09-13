/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.util

import android.content.Context
import love.miao.yun.R
import love.miao.yun.ai.AiManager
import love.miao.yun.ai.TokenStats
import love.miao.yun.service.MiaoAccessibilityService

/**
 * The whole AI step, in one place: capture the input box, have the model rewrite it, write it back.
 *
 * Both the floating window and the send-button assistant run exactly this, and they have to agree
 * on what "rewritten" means — including recording the token usage — so it lives here rather than in
 * whichever caller happened to be written first.
 */
object AiRewrite {
    /**
     * Runs the rewrite.
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

        val config = AiManager.load(context)
        if (config.apiKey.isNullOrBlank()) {
            onResult(false, context.getString(R.string.ai_need_api_key))
            return
        }

        AiManager.modifyText(
            config,
            original,
            object : AiManager.Callback {
                override fun onSuccess(modifiedText: String) {
                    AiManager.consumeLastUsage()?.let { usage ->
                        TokenStats.record(
                            context,
                            usage.model ?: config.model,
                            usage.promptTokens,
                            usage.completionTokens,
                            usage.totalTokens,
                            usage.cachedTokens,
                        )
                    }
                    val written = service.replaceInputText(modifiedText)
                    onResult(
                        written,
                        context.getString(
                            if (written) R.string.ai_replaced else R.string.ai_replace_failed,
                        ),
                    )
                }

                override fun onError(message: String) {
                    onResult(false, context.getString(R.string.ai_failed) + "：" + message)
                }
            },
        )
    }
}
