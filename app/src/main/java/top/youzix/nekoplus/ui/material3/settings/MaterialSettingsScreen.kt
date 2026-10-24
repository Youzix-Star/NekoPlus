/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The page is SukiSU-Ultra's SettingsMaterial.kt (GPL-3.0), same chrome and same row spacing:
 * an expressive scaffold, a column of segmented groups inset 16dp, 13dp between groups.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.nekoplus.ui.material3.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.UiEngine
import top.youzix.nekoplus.ui.UiEnginePrefs
import top.youzix.nekoplus.ui.material3.components.ExpressiveScaffold
import top.youzix.nekoplus.ui.material3.components.IconTile
import top.youzix.nekoplus.ui.material3.components.SegmentedColumn
import top.youzix.nekoplus.ui.material3.components.SegmentedDropdownItem
import top.youzix.nekoplus.ui.material3.components.SegmentedListItem
import top.youzix.nekoplus.ui.material3.components.SegmentedSwitchItem
import top.youzix.nekoplus.ui.material3.components.expressiveTopAppBarColors
import top.youzix.nekoplus.ui.theme.ColorMode

@Composable
fun MaterialSettingsScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenTextRules: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    ExpressiveScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("设置") },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(outerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = "外观",
            ) {
                item {
                    SegmentedDropdownItem(
                        icon = AppIcons.Palette,
                        title = "主题模式",
                        summary = "浅色 / 深色 / 跟随系统",
                        items = ColorMode.entries.map { it.label },
                        selectedIndex = ColorMode.entries.indexOf(MiaoState.colorMode).coerceAtLeast(0),
                        onItemSelected = { index ->
                            ColorMode.entries.getOrNull(index)?.let {
                                MiaoState.colorMode = it
                                UiEnginePrefs.saveColorMode(context, it)
                            }
                        },
                    )
                }
                item {
                    SegmentedSwitchItem(
                        icon = AppIcons.BlurOn,
                        title = "模糊",
                        summary = "底栏与顶栏的毛玻璃",
                        checked = MiaoState.useBlur,
                        onCheckedChange = {
                            MiaoState.useBlur = it
                            UiEnginePrefs.saveUseBlur(context, it)
                        },
                    )
                }
            }

            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = "引擎",
            ) {
                item {
                    SegmentedDropdownItem(
                        icon = AppIcons.Gallery,
                        title = "界面引擎",
                        summary = "换一套界面实现",
                        items = UiEngine.entries.map { it.label },
                        selectedIndex = UiEngine.entries.indexOf(engine).coerceAtLeast(0),
                        onItemSelected = { index ->
                            UiEngine.entries.getOrNull(index)?.let(onEngineChange)
                        },
                    )
                }
            }

            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = "AI",
            ) {
                item {
                    SegmentedListItem(
                        onClick = onOpenAiConfig,
                        headlineContent = { Text("AI 配置") },
                        supportingContent = { Text("接口、密钥与提示词") },
                        leadingContent = { IconTile(AppIcons.Sparkle, "AI 配置") },
                        trailingContent = { Icon(AppIcons.ChevronRight, null) },
                    )
                }
            }

            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = "替换",
            ) {
                item {
                    SegmentedListItem(
                        onClick = onOpenTextRules,
                        headlineContent = { Text("替换规则") },
                        supportingContent = { Text("后缀、颜文字、按条件替换") },
                        leadingContent = { IconTile(AppIcons.Rule, "替换规则") },
                        trailingContent = { Icon(AppIcons.ChevronRight, null) },
                    )
                }
            }
        }
    }
}
