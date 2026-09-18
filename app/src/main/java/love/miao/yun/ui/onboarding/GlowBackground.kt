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
 * A compiled glow and its clock.
 *
 * The handle exists because the opening transition needs the *same* instant of the *same* aurora
 * drawn twice: once behind the page that is leaving, and once more inside the circle that opens.
 * Upstream gets that for free by cropping the screen (`captureRoundedBitmap`); here it is the shader
 * again, with the same uniforms, so the two agree pixel for pixel.
 */
internal class GlowHandle(
    internal val painter: GlowPainter?,
    internal val brush: ShaderBrush?,
    internal val time: () -> Float,
    internal val palette: GlowPalette,
)

/**
 * Compiles the shader once and drives its clock — 30 times a second, because the aurora drifts over
 * two minutes and nobody can tell, while a full-screen noise shader at 60 fps is real battery.
 */
@Composable
internal fun rememberGlow(palette: GlowPalette, animate: Boolean = true): GlowHandle {
    val context = LocalContext.current
    // Compiling AGSL is not cheap, so the shader outlives recomposition and is rebuilt only when the
    // palette changes.
    val painter = remember(palette) {
        if (isRuntimeShaderSupported()) GlowPainter.create(context, palette) else null
    }
    // `ShaderBrush` takes any platform `Shader`, and an AGSL `RuntimeShader` is one.
    val brush = remember(painter) { painter?.let { ShaderBrush(it.runtimeShader) } }
    val clock = remember { GlowClock() }

    // Read inside the draw block only: nothing but the background has any reason to know the time.
    val time = remember { mutableFloatStateOf(0f) }

    if (painter != null && animate) {
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

    return remember(painter, brush) { GlowHandle(painter, brush, { time.floatValue }, palette) }
}

/**
 * Draws the glow across the space this modifier is given.
 *
 * A flat three-colour gradient goes underneath as well: the shader's output is opaque and covers it,
 * but if a driver ever refuses the shader at draw time rather than at compile time, the guide still
 * has a background instead of the app showing through.
 *
 * [circleVisible] is upstream's opening ring, which only plays once at the start of the clock, so it
 * belongs on the page the guide opens with and nowhere else.
 */
internal fun Modifier.glow(
    handle: GlowHandle,
    circleVisible: Boolean = false,
    circleYOffset: Float = 0f,
): Modifier = this
    .background(Brush.linearGradient(listOf(handle.palette.start, handle.palette.mid, handle.palette.end)))
    .drawBehind {
        val painter = handle.painter ?: return@drawBehind
        val brush = handle.brush ?: return@drawBehind
        painter.setTime(handle.time())
        painter.setResolution(size.width, size.height)
        painter.setCircleVisible(circleVisible)
        painter.setCircleYOffset(circleYOffset)
        drawRect(brush)
    }
