/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/** The theme choice offered in Settings. */
enum class ColorMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
    ;

    @Composable
    fun isDark(): Boolean = when (this) {
        Light -> false
        Dark -> true
        System -> isSystemInDarkTheme()
    }

    /** The miuix engine always renders dynamic colour; the mode only picks light or dark. */
    fun toMiuix(): ColorSchemeMode = when (this) {
        System -> ColorSchemeMode.MonetSystem
        Light -> ColorSchemeMode.MonetLight
        Dark -> ColorSchemeMode.MonetDark
    }

    companion object {
        fun fromName(name: String?): ColorMode = entries.firstOrNull { it.name == name } ?: System
    }
}

/** The Material engine's own scheme, used outside the dynamic-colour modes. */
fun defaultMaterialScheme(dark: Boolean) = if (dark) darkColorScheme() else lightColorScheme()
