/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package love.miao.yun.ui.material3.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import love.miao.yun.BuildConfig
import love.miao.yun.R
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.material3.SectionLabel

private const val REPOSITORY_URL = "https://github.com/Youzix-Star/NekoPlus"
private const val DEVELOPER_URL = "https://github.com/Youzix-Star"

@Composable
fun MaterialAboutScreen(
    contentPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    onNotify: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var showLicenses by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { AppHeader() }

        item(key = "about") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("关于")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("获取源代码") },
                        supportingContent = { Text("在 GitHub 上查看喵喵助手的源码") },
                        leadingContent = { Icon(AppIcons.SourceCode, contentDescription = null) },
                        modifier = Modifier.clickable { uriHandler.openUri(REPOSITORY_URL) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("开源许可") },
                        supportingContent = { Text("miuix、Compose、Material Icons 等依赖的许可证") },
                        leadingContent = { Icon(AppIcons.License, contentDescription = null) },
                        modifier = Modifier.clickable { showLicenses = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("检查更新") },
                        supportingContent = { Text("当前为界面骨架，暂未接入更新检查") },
                        leadingContent = { Icon(AppIcons.Update, contentDescription = null) },
                        modifier = Modifier.clickable { onNotify("暂未实现更新检查") },
                    )
                }
            }
        }

        item(key = "developer") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("开发者")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Youzix-Star") },
                        supportingContent = { Text("github.com/Youzix-Star") },
                        leadingContent = { Icon(AppIcons.Developer, contentDescription = null) },
                        modifier = Modifier.clickable { uriHandler.openUri(DEVELOPER_URL) },
                    )
                }
            }
        }

        item(key = "footer") {
            Text(
                text = "仅用于演示 Material Design 组件用法，暂未实现实际功能。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
            )
        }
    }

    if (showLicenses) {
        LicenseDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "喵喵助手", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "用 Material Design 拼起来的悬浮窗助手",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    val licenses = remember {
        listOf(
            "miuix" to "Apache License 2.0",
            "AndroidX / Jetpack Compose" to "Apache License 2.0",
            "Material Icons Extended" to "Apache License 2.0",
            "AndroidLiquidGlass (Kyant0)" to "Apache License 2.0",
            "Kotlin / kotlinx.coroutines" to "Apache License 2.0",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开源许可") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "本项目基于 AGPL-3.0 开源",
                    style = MaterialTheme.typography.bodyMedium,
                )
                licenses.forEach { (name, license) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}
