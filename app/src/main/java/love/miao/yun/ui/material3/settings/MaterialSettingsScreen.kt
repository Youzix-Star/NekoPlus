/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package love.miao.yun.ui.material3.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import love.miao.yun.MiaoState
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.material3.SectionLabel
import love.miao.yun.ui.material3.SwitchRow
import love.miao.yun.ui.material3.ThemeMode

@Composable
fun MaterialSettingsScreen(
    contentPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var autoStart by remember { mutableStateOf(false) }
    var keepAlive by remember { mutableStateOf(true) }
    val engine = MiaoState.engine

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("外观")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("主题模式") },
                        supportingContent = { Text(themeMode.label) },
                        modifier = Modifier.clickable { showThemeDialog = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchRow(
                        title = "动态取色",
                        summary = "Android 12+ 跟随壁纸取色",
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                    )
                }
            }
        }

        item(key = "engine") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("界面引擎")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("界面引擎") },
                        supportingContent = {
                            Text("${engine.label} · Miuix 与 Material Design 是两套完整的界面实现")
                        },
                        modifier = Modifier.clickable { showEngineDialog = true },
                    )
                }
            }
        }

        item(key = "service") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("服务")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchRow(
                        title = "开机自启",
                        summary = "开机后自动恢复悬浮窗",
                        checked = autoStart,
                        onCheckedChange = { autoStart = it },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchRow(
                        title = "保持运行",
                        summary = "显示常驻通知，降低被系统清理的概率",
                        checked = keepAlive,
                        onCheckedChange = { keepAlive = it },
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = "主题模式",
            options = ThemeMode.entries.map { it.label },
            selectedIndex = ThemeMode.entries.indexOf(themeMode).coerceAtLeast(0),
            onSelect = { index ->
                ThemeMode.entries.getOrNull(index)?.let(onThemeModeChange)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showEngineDialog) {
        ChoiceDialog(
            title = "界面引擎",
            options = UiEngine.entries.map { it.label },
            selectedIndex = UiEngine.entries.indexOf(engine).coerceAtLeast(0),
            onSelect = { index ->
                UiEngine.entries.getOrNull(index)?.let {
                    MiaoState.engine = it
                    UiEnginePrefs.save(context, it)
                }
                showEngineDialog = false
            },
            onDismiss = { showEngineDialog = false },
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                        )
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
