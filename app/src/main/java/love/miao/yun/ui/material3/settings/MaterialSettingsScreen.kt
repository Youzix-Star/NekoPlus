/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.material3.ThemeMode
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget

@Composable
fun MaterialSettingsScreen(
    outerPadding: PaddingValues,
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("设置", modifier = Modifier.padding(start = 12.dp)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues + outerPadding,
        ) {
            item {
                SegmentedColumn(title = "外观") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Settings,
                            title = "主题模式",
                            description = themeMode.label,
                            onClick = { showThemeDialog = true },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = AppIcons.Tune,
                            title = "动态取色",
                            description = "Android 12+ 跟随壁纸取色",
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange,
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "界面引擎") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Tune,
                            title = "界面引擎",
                            description = "${engine.label} · Miuix 与 Material Design 是两套完整的界面实现",
                            onClick = { showEngineDialog = true },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "服务") {
                    item {
                        SwitchWidget(
                            icon = AppIcons.Settings,
                            title = "开机自启",
                            description = "开机后自动恢复悬浮窗",
                            checked = autoStart,
                            onCheckedChange = { autoStart = it },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = AppIcons.Settings,
                            title = "保持运行",
                            description = "显示常驻通知，降低被系统清理的概率",
                            checked = keepAlive,
                            onCheckedChange = { keepAlive = it },
                        )
                    }
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
