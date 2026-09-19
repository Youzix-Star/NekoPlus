// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2023-2026 HyperCeiler Contributions
//
// Ported from HyperCeiler — https://github.com/ReChronoRain/HyperCeiler
//   library/provision/src/main/java/com/sevtinge/hyperceiler/provision/renderengine/GlowPainter.java
//   library/provision/src/main/res/raw/glow.glsl  (shipped here unchanged as res/raw/glow.glsl)
// Upstream is AGPL-3.0-only. Every uniform below keeps upstream's own default value, so the glow
// looks the same; the one deliberate difference is that the three colours come from this app's
// theme instead of HyperCeiler's brand colours.

package top.youzix.nekoplus.ui.onboarding

import android.content.Context
import android.graphics.RuntimeShader
import top.youzix.nekoplus.R

/**
 * The animated glow that the first-run guide is drawn on.
 *
 * HyperCeiler's provisioning flow paints its background with an AGSL shader, driven from Java as a
 * [RuntimeShader] and handed to the view as a `RenderEffect`. The shader itself is kept verbatim in
 * `res/raw/glow.glsl`; this class is nothing but the same list of uniforms with the same defaults,
 * so diffing the two files tells you exactly what this app changed.
 *
 * [create] returns `null` when the driver refuses to compile the shader. Callers must fall back to
 * something flat — a guide with a plain background beats a guide with no text.
 */
internal class GlowPainter private constructor(private val shader: RuntimeShader) {

    /** The compiled shader, for whoever draws it (as a brush, or as a layer's render effect). */
    val runtimeShader: RuntimeShader get() = shader

    fun setPalette(palette: GlowPalette) {
        shader.setFloatUniform("uColorBlack", palette.start.red, palette.start.green, palette.start.blue)
        shader.setFloatUniform("uColorMid", palette.mid.red, palette.mid.green, palette.mid.blue)
        shader.setFloatUniform("uColorWhite", palette.end.red, palette.end.green, palette.end.blue)
    }

    /** Animation clock, in seconds. Upstream ping-pongs it; see [GlowClock]. */
    fun setTime(seconds: Float) {
        shader.setFloatUniform("uTime", seconds)
    }

    /** The drawing area in pixels: the shader turns it into `uv = fragCoord / uResolution`. */
    fun setResolution(width: Float, height: Float) {
        shader.setFloatUniform("uResolution", width, height)
    }

    /**
     * The opening ring. It sweeps once, right at the start of the clock, so it only makes sense on
     * the page the guide opens with.
     */
    fun setCircleVisible(visible: Boolean) {
        shader.setFloatUniform("uShowCircle", if (visible) 1f else 0f)
    }

    /** Where the ring is centred, as an offset from the middle of the screen (upstream: -0.5…0.5). */
    fun setCircleYOffset(offset: Float) {
        shader.setFloatUniform("uCircleYOffset", offset)
    }

    /** Upstream's defaults, in upstream's order. Change a value here only with a reason. */
    private fun applyUpstreamDefaults() {
        set("uScale2", 0.82f)
        set("uSpeed2", 0.49f)
        set("uColorInMin", 0.3f)
        set("uColorInMax", 1.0f)
        set("uColorOutMin", 0.3f)
        set("uColorOutMax", 0.86f)
        set("uColorMidPoint", 0.47f)
        set("uUseOklab", 1.0f)
        set("uScale", 1.3f)
        set("uSpeed", 0.4f)
        set("uBrightnessInMin", 0.25f)
        set("uBrightnessInMax", 1.0f)
        set("uBrightnessOutMin", 0.25f)
        set("uBrightnessOutMax", 1.0f)
        set("uShowCircle", 1.0f)
        set("uCircleThickness", 0.4f)
        set("uCircleFinalRadius", 1.0f)
        set("uCircleYOffset", 0.1f)
        set("uCircleSpeed", 0.9f)
        set("uCircleColorFreq", 1.0f)
        set("uCircleColorSpeed", 0.0f)
        set("uCircleEasing", 1.4f)
        set("uCircleAnimationOffset", 0.0f)
        set("uMaskDelay", 0.3f)
        set("uMaskThickness", 0.3f)
        set("uCircleScreenBlend", 1.0f)
        set("uCircleAddBlend", 0.04f)
        set("uCircleColorOffset", 0.25f)
        set("uCircleUVDistort", 0.0f)
        set("uColorToDistortWidthRatio", 0.6f)
        set("uDistortStartTime", 0.2f)
        set("uDistortEndTime", 0.3f)
        set("uDistortStart", 0.0f)
        set("uDistortEnd", 1.0f)
        set("uStripeFrequency", 0.0f)
        set("uStripeStrengthX", 0.0f)
        set("uStripeStrengthY", 0.0f)
        set("uStripeUVDistort", 0.0f)
    }

    private fun set(name: String, value: Float) {
        shader.setFloatUniform(name, value)
    }

    companion object {
        /** Compiles the shader, or returns `null` if this device's driver will not have it. */
        fun create(context: Context, palette: GlowPalette): GlowPainter? = runCatching {
            val source = context.resources.openRawResource(R.raw.glow).use { it.readBytes().decodeToString() }
            GlowPainter(RuntimeShader(source)).apply {
                applyUpstreamDefaults()
                setPalette(palette)
            }
        }.getOrNull()
    }
}

/**
 * The shader's clock.
 *
 * Upstream (`GlowController`) walks the time upwards and then back down, bouncing between 2 and 120
 * seconds, which is what keeps the noise from repeating visibly. The opening ring lives in the first
 * second or so, hence the 0 start rather than 2.
 */
internal class GlowClock {
    private var lastNanos = 0L
    private var started = false
    private var direction = 1f

    var seconds: Float = 0f
        private set

    fun advance(nowNanos: Long): Float {
        if (started) {
            val delta = (nowNanos - lastNanos) / 1_000_000_000f
            seconds += delta * direction
            if (direction > 0f && seconds >= UPPER_BOUND) {
                direction = -1f
            } else if (direction < 0f && seconds <= LOWER_BOUND) {
                direction = 1f
            }
        }
        started = true
        lastNanos = nowNanos
        return seconds
    }

    private companion object {
        const val LOWER_BOUND = 2f
        const val UPPER_BOUND = 120f
    }
}
