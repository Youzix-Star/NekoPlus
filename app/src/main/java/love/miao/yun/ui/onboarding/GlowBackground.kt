// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2023-2026 HyperCeiler Contributions
//
// The look of this background — the shader, its uniforms and the clock that drives them — is ported
// from HyperCeiler (https://github.com/ReChronoRain/HyperCeiler), library/provision. Upstream is
// AGPL-3.0-only.
//
// One deliberate deviation: upstream renders the shader into a View that is 20% of the screen and
// scales it back up 5x, which softens the noise and cuts the pixel count 25-fold. Reproducing that
// here would mean a platform View inside Compose (Compose's own RenderEffect API has no shader
// factory — only blur and offset), and a scaled platform View is at the mercy of the interop
// layer's clipping. The shader's noise is low-frequency, so a full-resolution draw looks the same
// bar a slightly crisper edge, and the cost is paid back by ticking the clock at 30 fps instead of
// 60 — the aurora drifts over two minutes, so nobody can tell.

package love.miao.yun.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/** One frame of a 30 fps clock, in nanoseconds. */
private const val FRAME_NANOS = 33_000_000L

/**
 * The guide's background: a slow, enormous gradient that never quite repeats.
 *
 * [animate] off gives a still frame of the same shader, and a device whose driver refuses to compile
 * it gets a plain three-colour gradient instead — a guide with a flat background beats a guide with
 * no background at all.
 *
 * [circleVisible] is upstream's opening ring, which only plays once at the start of the clock, so it
 * belongs on the page the guide opens with and nowhere else.
 */
@Composable
internal fun GlowBackground(
    palette: GlowPalette,
    modifier: Modifier = Modifier.fillMaxSize(),
    animate: Boolean = true,
    circleVisible: Boolean = false,
    circleYOffset: Float = 0f,
) {
    val context = LocalContext.current
    // Compiling AGSL is not cheap, so the shader outlives recomposition and is rebuilt only when the
    // palette changes.
    val painter = remember(palette) {
        if (isRuntimeShaderSupported()) GlowPainter.create(context, palette) else null
    }

    if (painter == null) {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(listOf(palette.start, palette.mid, palette.end)),
            ),
        )
        return
    }

    // `ShaderBrush` takes any platform `Shader`, and an AGSL `RuntimeShader` is one.
    val brush = remember(painter) { ShaderBrush(painter.runtimeShader) }
    val clock = remember { GlowClock() }

    // Read inside the draw block only: the clock ticks 30 times a second and nothing but the
    // background has any reason to know that.
    val time = remember { mutableFloatStateOf(0f) }

    if (animate) {
        LaunchedEffect(painter) {
            var lastUpdate = 0L
            while (true) {
                withFrameNanos { now ->
                    if (now - lastUpdate >= FRAME_NANOS) {
                        lastUpdate = now
                        time.floatValue = clock.advance(now)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier.drawBehind {
            painter.setTime(time.floatValue)
            painter.setResolution(size.width, size.height)
            painter.setCircleVisible(circleVisible)
            painter.setCircleYOffset(circleYOffset)
            drawRect(brush)
        },
    )
}
