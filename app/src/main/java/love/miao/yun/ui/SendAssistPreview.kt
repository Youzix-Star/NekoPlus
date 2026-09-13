/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import love.miao.yun.R
import love.miao.yun.sendassist.SendAssistConfig

/** The mock input bar's proportions, matching what the offsets are measured against. */
private val BarInset = 12.dp
private val BarPadding = 10.dp
private val SendWidth = 64.dp
private val SendHeight = 40.dp
private val Gap = 8.dp

/**
 * A fake chat input bar with the assistant button above its send button.
 *
 * Tuning an overlay that only exists inside another app is guesswork otherwise: you set a number,
 * switch to WeChat, look, switch back. Every value here is drawn at its real size and in its real
 * colour, with the same geometry the overlay uses — same gap, same offsets, same anchor — so the
 * sliders can be judged without leaving the settings page.
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
    val size = config.sizeDp.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor),
    ) {
        Text(
            text = "预览",
            color = labelColor.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 10.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = BarInset, vertical = BarPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SendHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fieldColor),
            )
            Spacer(modifier = Modifier.width(Gap))
            Box(
                modifier = Modifier
                    .width(SendWidth)
                    .height(SendHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "发送", color = onAccentColor, fontSize = 13.sp)
            }
        }

        // Same anchor as the overlay: right-aligned with the send button, one gap above its top,
        // then shifted by the configured offsets (+x right, +y down).
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = BarInset, bottom = BarPadding + SendHeight + Gap)
                .offset(x = config.offsetXDp.dp, y = config.offsetYDp.dp)
                .size(size)
                .alpha(config.opacity / 100f)
                .clip(RoundedCornerShape(config.effectiveCornerDp.dp))
                .background(palette.container),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_ball_auto_awesome),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.onContainer),
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}
