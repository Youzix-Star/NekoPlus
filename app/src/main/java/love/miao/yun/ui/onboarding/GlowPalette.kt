/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui.onboarding

import androidx.compose.ui.graphics.Color
import kotlin.math.max

/** Below this a colour reads as grey, and a grey glow looks like a bug. */
private const val MIN_SATURATION = 0.45f

/** Below this the background stops looking lit. */
private const val MIN_VALUE = 0.55f

/**
 * How much of a light-theme palette survives into a dark one.
 *
 * The same bright pastels on a phone in dark mode would be a flashbang, and white text on them is
 * unreadable. Dimming keeps the hues and the movement, and makes the guide agree with the theme's
 * ink instead of fighting it.
 */
private const val DARK_DIM = 0.55f

/**
 * The three colours the glow walks through.
 *
 * The shader treats them as a ramp — `start` below 47% of the noise, then `mid`, then `end` — and
 * mixes neighbours in Oklab, so what matters is not the individual hues but their relationship: one
 * dark end, one clearly lighter middle, and a third that sits between the two.
 *
 * Upstream calls these `uColorBlack` / `uColorMid` / `uColorWhite` because its values come from
 * HyperCeiler's brand: a strong pink (0.961, 0.157, 0.157), a light periwinkle (0.604, 0.659, 0.961)
 * and a violet (0.302, 0.29, 0.843). Those three, sorted by luminance, are exactly
 * dark → light → middle, which is the arrangement [fromTheme] reproduces from any trio of colours.
 */
internal data class GlowPalette(
    val start: Color,
    val mid: Color,
    val end: Color,
) {
    companion object {
        /** Upstream's own palette, kept for comparison. */
        val HyperCeiler: GlowPalette = GlowPalette(
            start = Color(0.961f, 0.157f, 0.157f),
            mid = Color(0.604f, 0.659f, 0.961f),
            end = Color(0.302f, 0.29f, 0.843f),
        )

        /**
         * Builds a palette out of the running theme, so the guide matches the colours the rest of
         * the app is using — wallpaper-derived Monet included.
         *
         * The order is by luminance rather than by role: `mid` is the lightest of the three, and the
         * other two are split into the darker and the lighter half. That is what upstream's own trio
         * does, and it survives a theme whose primary is lighter than its tertiary.
         */
        fun fromTheme(
            primary: Color,
            secondary: Color,
            tertiary: Color,
            dark: Boolean = false,
        ): GlowPalette {
            val sorted = listOf(primary, secondary, tertiary)
                .map { if (dark) scale(vivid(it), DARK_DIM) else vivid(it) }
                .sortedBy { luminanceOf(it) }
            return GlowPalette(start = sorted[0], mid = sorted[2], end = sorted[1])
        }
    }
}

/**
 * Lifts a colour until it can carry a glow.
 *
 * A dynamic scheme is allowed to hand out muted, low-contrast colours — that is fine for buttons and
 * terrible for a full-screen gradient, which then looks like a smudge. Hue is never touched: the
 * channels are pushed away from the maximum, which is what saturation means in HSV, and the whole
 * colour is only brightened when even that maximum is too dark.
 */
internal fun vivid(color: Color): Color {
    val highest = max(color.red, max(color.green, color.blue))
    val lowest = minOf(color.red, color.green, color.blue)

    // A grey has no hue to preserve; all that can be done is make it bright enough to read as light.
    if (highest - lowest < 1e-4f) {
        return if (highest in 1e-4f..MIN_VALUE) scale(color, MIN_VALUE / highest) else color
    }

    val saturation = (highest - lowest) / highest
    val saturating = if (saturation >= MIN_SATURATION) 1f else MIN_SATURATION / saturation
    val lifted = Color(
        red = lift(color.red, highest, saturating),
        green = lift(color.green, highest, saturating),
        blue = lift(color.blue, highest, saturating),
        alpha = color.alpha,
    )
    return if (highest >= MIN_VALUE) lifted else scale(lifted, MIN_VALUE / highest)
}

private fun lift(channel: Float, highest: Float, factor: Float): Float =
    (highest + (channel - highest) * factor).coerceIn(0f, 1f)

/**
 * Rec. 709 relative luminance, written out rather than taken from Compose's own `luminance()`:
 * this file is unit-tested on the JVM, where staying clear of the graphics stack is the difference
 * between a test that runs and one that throws "not mocked".
 */
private fun luminanceOf(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

private fun scale(color: Color, factor: Float): Color = Color(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
    alpha = color.alpha,
)

/**
 * The palette for a screen that is drawn over the glow.
 *
 * Whether the glow should be bright or deep follows the *surface* colour rather than a "dark mode"
 * flag: the theme is free to be forced light on a dark phone, and what matters is the ink the
 * screens are about to draw with.
 */
internal fun glowPaletteOf(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    surface: Color,
): GlowPalette = GlowPalette.fromTheme(
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    dark = luminanceOf(surface) < 0.5f,
)
