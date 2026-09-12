/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.miuix

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The colours this app gives a miuix text field.
 *
 * miuix defaults the background to `secondaryContainer`, which under dynamic colour is a saturated
 * tint that reads heavy the moment several fields are stacked in one card. The reference project's
 * edit screens sit their fields on `surfaceContainer` instead — light, neutral, and still obviously
 * a field — so that is what we use here, with the label dropped to the muted text colour and the
 * focus ring left as the accent.
 */
@Composable
fun miaoTextFieldColors(): TextFieldColors = TextFieldDefaults.textFieldColors(
    backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
    labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    borderColor = MiuixTheme.colorScheme.primary,
)
