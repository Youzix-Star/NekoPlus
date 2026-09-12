/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.licenses

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn

private data class LicenseEntry(val name: String, val license: String, val url: String)

/** Second-level page: the licences of everything this app is built on. */
@Composable
fun MaterialLicensesScreen(outerPadding: PaddingValues) {
    val uriHandler = LocalUriHandler.current
    val entries = remember {
        listOf(
            LicenseEntry("miuix", "Apache License 2.0", "https://github.com/compose-miuix-ui/miuix"),
            LicenseEntry(
                "AndroidX / Jetpack Compose",
                "Apache License 2.0",
                "https://cs.android.com/androidx/platform/frameworks/support",
            ),
            LicenseEntry("Material Icons Extended", "Apache License 2.0", "https://fonts.google.com/icons"),
            LicenseEntry(
                "AndroidLiquidGlass (Kyant0)",
                "Apache License 2.0",
                "https://github.com/Kyant0/AndroidLiquidGlass",
            ),
            LicenseEntry(
                "Kotlin / kotlinx.coroutines",
                "Apache License 2.0",
                "https://github.com/JetBrains/kotlin",
            ),
            LicenseEntry(
                "InstallerX-Revived",
                "GPL-3.0",
                "https://github.com/wxxsfxyzm/InstallerX-Revived",
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = outerPadding,
    ) {
        item {
            SegmentedColumn(title = "本项目基于 AGPL-3.0 开源") {
                entries.forEach { entry ->
                    item(key = entry.name) {
                        NavigationItemWidget(
                            title = entry.name,
                            description = entry.license,
                            onClick = { uriHandler.openUri(entry.url) },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "点击任意一项可在浏览器中查看其许可证原文。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
    }
}
