/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import top.youzix.nekoplus.ui.theme.ColorMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Dark flag of the colour scheme Miuix currently resolves to.
 *
 * The vendored liquid-glass components need it, and reading it from the composition keeps every
 * layer in agreement with [ThemeController].
 */
private val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun isInDarkTheme(): Boolean = LocalIsDarkTheme.current

/** The miuix engine's theme: one colour mode, handed to miuix's own [ThemeController]. */
@Composable
fun MiuixAppTheme(
    colorMode: ColorMode,
    content: @Composable () -> Unit,
) {
    val isDark = colorMode.isDark()
    val controller = remember(colorMode) {
        ThemeController(colorSchemeMode = colorMode.toMiuix())
    }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalIsDarkTheme provides isDark, content = content)
    }
}
