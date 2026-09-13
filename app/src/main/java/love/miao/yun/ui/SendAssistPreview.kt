/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui

import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import love.miao.yun.R
import love.miao.yun.sendassist.SendAssistConfig
import love.miao.yun.sendassist.SendAssistGeometry
import love.miao.yun.sendassist.SendAssistMetrics
import love.miao.yun.sendassist.SendAssistPrefs
import love.miao.yun.sendassist.sendTargetFor

/** Room left under the input bar. */
private const val BelowRoomDp = 12f

/** The tallest the scene box may get before the whole thing is drawn scaled down instead. */
private const val MaxSceneHeightDp = 240f

/**
 * A chat app's input bar with the assistant button above its send button.
 *
 * Everything here is drawn at its real size in dp, in the same coordinate space the overlay uses,
 * and placed by the same [SendAssistGeometry.assistantRect] the overlay calls. That is deliberate:
 * the first version of this preview re-implemented the placement and invented its own send button,
 * so it agreed with the settings page and disagreed with the phone.
 *
 * Once the overlay has been inside an app, the bar drawn here is that app's own bar, measured: the
 * send key's real size, the real distance to the screen's edge, the real height of the field. Until
 * then the stand-in below says so rather than pretending.
 */
@Composable
fun SendAssistPreview(
    config: SendAssistConfig,
    surfaceColor: Color,
    fieldColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The real button borrows the floating window's palette, so the preview has to as well.
    val palette = FloatingPalettes.resolve(context, UiEnginePrefs.loadFloatingColor(context))
    // Read on every composition rather than remembered: the measurement is written while the user
    // is inside the chat app, and coming back to this page should already show the new numbers.
    val scene = barScene(config, SendAssistPrefs.loadMetrics(context, config.packageName))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor),
    ) {
        Text(
            text = "预览 · " + scene.caption,
            color = labelColor.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp)
                .height(scene.heightDp.dp),
        ) {
            // The box is usually narrower than the phone, and what matters is anchored to the
            // screen's right edge, so the scene is cropped from the left rather than scaled: every
            // number stays a true dp, which is the only way a size can be judged. The exception is
            // a button pushed so far from the send key that both cannot fit — then the scale is
            // stated in the caption, because a silent zoom is a lie with extra steps.
            val scale = scene.scale
            val available = maxWidth.value.let { if (it.isFinite()) it else scene.screenWidthDp }
            val originX = (scene.screenWidthDp - available).coerceAtLeast(0f)

            fun sceneX(x: Float): Float = (x - originX) * scale
            fun sceneY(y: Float): Float = (y - scene.topDp) * scale

            fun place(rect: RectF): Modifier =
                Modifier.offset(x = sceneX(rect.left).dp, y = sceneY(rect.top).dp)

            // The keyboard the bar is sitting on, when it is sitting on one.
            if (scene.keyboardUp) {
                val bandTop = sceneY(scene.barBottomDp)
                Box(
                    modifier = Modifier
                        .offset(y = bandTop.dp)
                        .fillMaxWidth()
                        .height((scene.heightDp - bandTop).coerceAtLeast(0f).dp)
                        .background(fieldColor.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "键盘", color = labelColor.copy(alpha = 0.45f), fontSize = 10.sp)
                }
            }

            Box(
                modifier = place(scene.field)
                    .size((scene.field.width() * scale).dp, (scene.field.height() * scale).dp)
                    .clip(RoundedCornerShape((6f * scale).dp))
                    .background(fieldColor),
            )

            Box(
                modifier = place(scene.send)
                    .size((scene.send.width() * scale).dp, (scene.send.height() * scale).dp)
                    .clip(RoundedCornerShape((6f * scale).dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                if (scene.send.height() * scale >= 26f) {
                    Text(
                        text = "发送",
                        color = onAccentColor,
                        fontSize = (scene.send.height() * scale * 0.32f).coerceIn(8f, 14f).sp,
                    )
                }
            }

            Box(
                modifier = place(scene.assistant)
                    .size((config.sizeDp * scale).dp)
                    .alpha(config.opacity / 100f)
                    .clip(RoundedCornerShape((config.effectiveCornerDp * scale).dp))
                    .background(Color(palette.container)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ball_auto_awesome),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color(palette.onContainer)),
                    modifier = Modifier.size((config.sizeDp * scale * 0.48f).dp),
                )
            }
        }
    }
}

/**
 * The input bar to draw, in dp, in screen coordinates.
 *
 * Coordinates run left-to-right and top-to-bottom from the screen's origin, exactly like the pixels
 * the overlay passes to [SendAssistGeometry.assistantRect], so a scene's [assistant] is the real
 * placement rather than a redrawing of it.
 */
private class BarScene(
    val screenWidthDp: Float,
    val field: RectF,
    val send: RectF,
    val assistant: RectF,
    val topDp: Float,
    val heightDp: Float,
    val barBottomDp: Float,
    val scale: Float,
    val keyboardUp: Boolean,
    val caption: String,
)

/**
 * The measured bar when there is one, and an honest stand-in when there is not.
 *
 * Values are clamped on the way in as well as on the way out: a phone whose send button measured
 * 300 dp across — a mis-identified node, most likely — must not be able to blow up the settings
 * page.
 */
private fun barScene(config: SendAssistConfig, metrics: SendAssistMetrics?): BarScene {
    val size = config.sizeDp.toFloat()
    val name = sendTargetFor(config.packageName)?.label ?: "该应用"
    // The gap the phone actually produced, once it has been measured — not the one the geometry was
    // written for. Where those two differ, this is the whole point of the preview: it has to show
    // what happens, so that "0 dp" here and "0 dp" in WeChat look like the same thing.
    val gap = metrics?.effectiveGapDp ?: SendAssistGeometry.GAP_DP

    val screenWidth: Float
    val send: RectF
    val field: RectF
    val below: Float
    val keyboardUp: Boolean
    val caption: String

    if (metrics != null) {
        screenWidth = metrics.screenWidthDp.toFloat()
        val sendRight = screenWidth - metrics.sendRightInsetDp
        val sendTop = metrics.sendTopDp.toFloat()
        val sendWidth = metrics.sendWidthDp.coerceIn(24, 200).toFloat()
        val sendHeight = metrics.sendHeightDp.coerceIn(24, 120).toFloat()
        send = RectF(sendRight - sendWidth, sendTop, sendRight, sendTop + sendHeight)

        val fieldRight = screenWidth - metrics.inputRightInsetDp.coerceAtLeast(0)
        val fieldHeight = metrics.inputHeightDp.coerceIn(24, 120).toFloat()
        val fieldTop = sendTop + metrics.inputTopVsSendDp
        field = RectF(0f, fieldTop, fieldRight, fieldTop + fieldHeight)

        keyboardUp = metrics.keyboardUp
        // Standing in for the keyboard: drawing its real 250 dp would be a page of grey.
        below = metrics.barToBottomDp.coerceIn(6, 34).toFloat()
        caption = "$name 实测：" + metrics.summary() + sinkingNote(metrics.sinkingDp)
    } else {
        screenWidth = 360f
        // 600 dp down the screen, so the stand-in bar is nowhere near the top clamp. Where the bar
        // sits on a real phone is measured, and only the measured branch knows it.
        val sendTop = 600f
        send = RectF(screenWidth - 12f - 64f, sendTop, screenWidth - 12f, sendTop + 40f)
        field = RectF(0f, sendTop, send.left - 8f, sendTop + 40f)
        below = 10f
        keyboardUp = false
        caption = "尚未进过$name，按通用输入栏示意"
    }

    val assistant = SendAssistGeometry.assistantRect(
        send = send,
        size = size,
        gap = gap,
        offsetX = config.offsetXDp.toFloat(),
        offsetY = config.offsetYDp.toFloat(),
        topLimit = 0f,
    )

    val barBottom = maxOf(send.bottom, field.bottom)
    val top = minOf(assistant.top, send.top, field.top)
    val naturalHeight = (barBottom + below + BelowRoomDp - top).coerceAtLeast(1f)
    val scale = (MaxSceneHeightDp / naturalHeight).coerceAtMost(1f)
    val scaledCaption = if (scale < 1f) {
        caption + "（整体按 ${(scale * 100).toInt()}% 显示：按钮被推得离发送键很远）"
    } else {
        caption
    }

    return BarScene(
        screenWidthDp = screenWidth,
        field = field,
        send = send,
        assistant = assistant,
        topDp = top,
        heightDp = naturalHeight * scale,
        barBottomDp = barBottom,
        scale = scale,
        keyboardUp = keyboardUp,
        caption = scaledCaption,
    )
}

/**
 * What to say when the phone lands the button somewhere other than the geometry asks for.
 *
 * Said out loud rather than hidden, because a preview that silently disagrees with the phone is
 * what made the offsets untrustworthy in the first place. Under 4 dp is rounding, not a finding.
 */
private fun sinkingNote(sinkingDp: Int): String = when {
    sinkingDp >= 4 -> "（按钮实际比设定低 $sinkingDp dp，已按实际画）"
    sinkingDp <= -4 -> "（按钮实际比设定高 ${-sinkingDp} dp，已按实际画）"
    else -> ""
}
