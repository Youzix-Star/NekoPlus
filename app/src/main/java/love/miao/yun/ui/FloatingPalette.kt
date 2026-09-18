/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme as material3DarkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme as material3LightColorScheme
import androidx.compose.ui.graphics.toArgb
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/**
 * The handful of colours the floating window needs, already flattened to ARGB integers so the
 * service can hand them straight to the framework views.
 *
 * @property container the pill's background.
 * @property onContainer the primary label and the close glyph.
 * @property onContainerMuted the secondary line, faded out of [onContainer].
 */
data class FloatingPalette(
    val container: Int,
    val onContainer: Int,
    val onContainerMuted: Int,
)

/**
 * Resolves a [FloatingPalette] for a [FloatingColorSource].
 *
 * All three sources are read through plain (non-`@Composable`) colour-scheme factories, which is
 * what lets a `Service` — with no composition anywhere near it — ask for a palette at all.
 * Light or dark is decided by the **system** night mode rather than the app's own theme setting,
 * because the overlay outlives the activity and has no access to the in-app choice.
 */
object FloatingPalettes {

    fun resolve(context: Context, source: FloatingColorSource): FloatingPalette {
        val night = isNight(context)
        return when (source) {
            FloatingColorSource.Dynamic -> fromMaterial3(
                if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
            )

            FloatingColorSource.Material3 -> fromMaterial3(
                if (night) material3DarkColorScheme() else material3LightColorScheme(),
            )

            FloatingColorSource.Miuix -> fromMiuix(
                if (night) miuixDarkColorScheme() else miuixLightColorScheme(),
            )
        }
    }

    private fun isNight(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun fromMaterial3(scheme: ColorScheme): FloatingPalette {
        val on = scheme.onPrimaryContainer
        return FloatingPalette(
            container = scheme.primaryContainer.toArgb(),
            onContainer = on.toArgb(),
            onContainerMuted = on.copy(alpha = MUTED_ALPHA).toArgb(),
        )
    }

    private fun fromMiuix(colors: Colors): FloatingPalette {
        val on = colors.onPrimaryContainer
        return FloatingPalette(
            container = colors.primaryContainer.toArgb(),
            onContainer = on.toArgb(),
            onContainerMuted = on.copy(alpha = MUTED_ALPHA).toArgb(),
        )
    }

    private const val MUTED_ALPHA = 0.72f
}
