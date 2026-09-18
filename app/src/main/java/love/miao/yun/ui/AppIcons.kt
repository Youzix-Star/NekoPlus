/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's mark, as text.
 *
 * The icon is a rendered string rather than artwork — the same string is baked into the launcher
 * PNGs under `res/mipmap-*`, so this constant and those files have to stay in step.
 *
 * Codepoints: U+1BE0, U+035F x2, U+032B, U+035F x2, U+1BC4, U+0A6D.
 */
const val AppIconText = "ᯠ ͟͟    ̫  ͟͟ ᯄ ੭"

/** Material icons used by the app. */
object AppIcons {
    val Home: ImageVector = Icons.Rounded.Home
    val Floating: ImageVector = Icons.Rounded.PictureInPicture
    val Settings: ImageVector = Icons.Rounded.Settings
    val About: ImageVector = Icons.Rounded.Info

    val SourceCode: ImageVector = Icons.Rounded.Code
    val License: ImageVector = Icons.Rounded.Description
    val Update: ImageVector = Icons.Rounded.SystemUpdate
    val Refresh: ImageVector = Icons.Rounded.Refresh
    val Developer: ImageVector = Icons.Rounded.Person
    val Feedback: ImageVector = Icons.Rounded.Email
    val Phones: ImageVector = Icons.Rounded.PhoneAndroid
    val Rule: ImageVector = Icons.Rounded.Article
    val Grant: ImageVector = Icons.Rounded.CheckCircle
    val Tune: ImageVector = Icons.Rounded.Tune
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack

    // Used by the first-run guide.
    val Sparkle: ImageVector = Icons.Rounded.AutoAwesome
    val Touch: ImageVector = Icons.Rounded.TouchApp
    val Key: ImageVector = Icons.Rounded.Key
    val Read: ImageVector = Icons.Rounded.TextFields
    val Write: ImageVector = Icons.Rounded.Send
    val Hold: ImageVector = Icons.Rounded.PanTool
    val Drag: ImageVector = Icons.Rounded.OpenWith
    val Forward: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward
}
