/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui.onboarding

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glow is a shader, which no unit test can look at. What *can* be tested is everything that
 * decides what the shader is fed: the three colours and the clock that animates them.
 */
class GlowPaletteTest {

    @Test
    fun `a vivid colour is left untouched`() {
        val pink = Color(0.961f, 0.157f, 0.157f)

        assertEquals(pink, vivid(pink))
    }

    @Test
    fun `a muted colour is pushed to the saturation floor`() {
        // Saturation 0.17, which would smudge rather than glow.
        val muted = Color(0.60f, 0.50f, 0.55f)

        val result = vivid(muted)

        assertEquals(0.45f, saturationOf(result), 1e-3f)
        // Hue survives: red is still the dominant channel.
        assertTrue(result.red > result.green)
        assertTrue(result.red > result.blue)
    }

    @Test
    fun `a grey is brightened instead of invented into a hue`() {
        val grey = Color(0.30f, 0.30f, 0.30f)

        val result = vivid(grey)

        assertEquals(result.red, result.green, 1e-4f)
        assertEquals(result.green, result.blue, 1e-4f)
        assertEquals(0.55f, result.red, 1e-3f)
    }

    @Test
    fun `upstream's own colours come back in upstream's own arrangement`() {
        // HyperCeiler's brand trio, which the shader expects as dark -> light -> middle.
        val pink = Color(0.961f, 0.157f, 0.157f)
        val periwinkle = Color(0.604f, 0.659f, 0.961f)
        val violet = Color(0.302f, 0.29f, 0.843f)

        val palette = GlowPalette.fromTheme(pink, periwinkle, violet)

        assertEquals(pink, palette.start)
        assertEquals(violet, palette.end)
        // The light one lands in the middle, whichever argument it arrived in.
        assertEquals(0.961f, palette.mid.blue, 1e-3f)
        assertTrue(palette.mid.green > palette.mid.red)
    }

    @Test
    fun `the lightest colour is the midpoint even when it arrives as the tertiary`() {
        val primary = Color(0.9f, 0.2f, 0.3f)
        val secondary = Color(0.2f, 0.6f, 0.9f)
        val tertiary = Color(0.95f, 0.9f, 0.4f)

        val palette = GlowPalette.fromTheme(primary, secondary, tertiary)

        assertEquals(primary, palette.start)
        assertEquals(tertiary, palette.mid)
        assertEquals(secondary, palette.end)
    }

    @Test
    fun `the clock bounces between its bounds instead of running away`() {
        val clock = GlowClock()
        var now = 0L
        val times = mutableListOf(clock.advance(now))

        repeat(400) {
            now += 1_000_000_000L
            times += clock.advance(now)
        }

        assertEquals(0f, times.first(), 1e-6f)
        assertEquals(120f, times.max(), 1e-6f)
        assertTrue("the clock must turn around at the top", times.last() < 120f)
        assertTrue("it must not fall through the bottom", times.min() >= 0f)
    }

    @Test
    fun `the clock counts from zero and measures the gap between frames`() {
        val clock = GlowClock()

        assertEquals(0f, clock.advance(4_000_000_000L), 1e-6f)
        assertEquals(0.016f, clock.advance(4_016_000_000L), 1e-6f)
    }

    @Test
    fun `a dark theme gets a deeper glow so the light ink keeps its contrast`() {
        val primary = Color(0.9f, 0.2f, 0.3f)
        val secondary = Color(0.2f, 0.6f, 0.9f)
        val tertiary = Color(0.95f, 0.9f, 0.4f)

        val light = GlowPalette.fromTheme(primary, secondary, tertiary, dark = false)
        val dark = GlowPalette.fromTheme(primary, secondary, tertiary, dark = true)

        // Relational, not an exact factor: `Color` stores its channels as half floats, so asking
        // for `light * 0.55` to the third decimal is asking the wrong question.
        val lightStart = highestOf(light.start)
        val darkStart = highestOf(dark.start)
        val lightMid = highestOf(light.mid)
        val darkMid = highestOf(dark.mid)

        assertTrue("dark=$darkStart should be below light=$lightStart", darkStart < lightStart)
        assertTrue("dark=$darkMid should be below light=$lightMid", darkMid < lightMid)
        assertTrue("dark=$darkStart is crushed, light=$lightStart", darkStart > lightStart * 0.4f)
        assertTrue("dark=$darkMid is crushed, light=$lightMid", darkMid > lightMid * 0.4f)

        // Dimming must not shuffle which colour plays which part, nor turn one hue into another.
        assertTrue(dominantChannel(light.start) == dominantChannel(dark.start))
        assertTrue(dominantChannel(light.mid) == dominantChannel(dark.mid))
        assertTrue(dominantChannel(light.end) == dominantChannel(dark.end))
    }

    @Test
    fun `the surface colour decides whether the glow is bright or deep`() {
        val primary = Color(0.9f, 0.2f, 0.3f)
        val secondary = Color(0.2f, 0.6f, 0.9f)
        val tertiary = Color(0.95f, 0.9f, 0.4f)

        val onLight = glowPaletteOf(primary, secondary, tertiary, surface = Color(0.98f, 0.98f, 0.98f))
        val onDark = glowPaletteOf(primary, secondary, tertiary, surface = Color(0.06f, 0.06f, 0.06f))

        assertTrue(highestOf(onDark.mid) < highestOf(onLight.mid))
    }

    private fun highestOf(color: Color): Float = maxOf(color.red, color.green, color.blue)

    /** 0 for red, 1 for green, 2 for blue — the channel a colour gets its hue from. */
    private fun dominantChannel(color: Color): Int =
        listOf(color.red, color.green, color.blue).withIndex().maxBy { it.value }.index

    private fun saturationOf(color: Color): Float {
        val highest = maxOf(color.red, color.green, color.blue)
        val lowest = minOf(color.red, color.green, color.blue)
        return if (highest == 0f) 0f else (highest - lowest) / highest
    }
}
