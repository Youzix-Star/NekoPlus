/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Row layout follows SukiSU-Ultra's SettingsMiuix.kt (GPL-3.0): appearance in one card, then the
 * engine, then the feature entries. The diagnostic rows and the backup live on About, which is a
 * tab of its own in this app.
 */

package top.youzix.nekoplus.ui.miuix.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.UiEngine
import top.youzix.nekoplus.ui.UiEnginePrefs
import top.youzix.nekoplus.ui.miuix.components.BlurredBar
import top.youzix.nekoplus.ui.theme.ColorMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsScreen(
    bottomPadding: Dp,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenTextRules: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            BlurredBar(backdrop = null) {
                TopAppBar(title = "设置", scrollBehavior = scrollBehavior)
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        SettingsContent(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding,
            ),
            scrollBehavior = scrollBehavior,
            engine = engine,
            onEngineChange = onEngineChange,
            onOpenAiConfig = onOpenAiConfig,
            onOpenTextRules = onOpenTextRules,
            onNotify = onNotify,
        )
    }
}

@Composable
private fun SettingsContent(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenTextRules: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val engineLabels = remember { UiEngine.entries.map { it.label } }
    val modeLabels = remember { ColorMode.entries.map { it.label } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "appearance") {
            Column {
                SmallTitle(text = "外观")
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        title = "主题模式",
                        summary = "浅色 / 深色 / 跟随系统",
                        items = modeLabels,
                        selectedIndex = ColorMode.entries.indexOf(MiaoState.colorMode).coerceAtLeast(0),
                        startAction = {
                            Icon(AppIcons.Palette, null, Modifier.size(22.dp), colorScheme.onBackground)
                        },
                        onSelectedIndexChange = { index ->
                            ColorMode.entries.getOrNull(index)?.let {
                                MiaoState.colorMode = it
                                UiEnginePrefs.saveColorMode(context, it)
                            }
                        },
                    )
                    SwitchPreference(
                        title = "模糊",
                        summary = "底栏与顶栏的毛玻璃",
                        startAction = {
                            Icon(AppIcons.BlurOn, null, Modifier.size(22.dp), colorScheme.onBackground)
                        },
                        checked = MiaoState.useBlur,
                        onCheckedChange = {
                            MiaoState.useBlur = it
                            UiEnginePrefs.saveUseBlur(context, it)
                        },
                    )
                    OverlayDropdownPreference(
                        title = "界面引擎",
                        summary = "换一套界面实现",
                        items = engineLabels,
                        selectedIndex = UiEngine.entries.indexOf(engine).coerceAtLeast(0),
                        startAction = {
                            Icon(AppIcons.Gallery, null, Modifier.size(22.dp), colorScheme.onBackground)
                        },
                        onSelectedIndexChange = { index ->
                            UiEngine.entries.getOrNull(index)?.let(onEngineChange)
                        },
                    )
                }
            }
        }

        item(key = "ai") {
            Column {
                SmallTitle(text = "AI")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "AI 配置",
                        summary = "接口、密钥与提示词",
                        startAction = {
                            Icon(AppIcons.Sparkle, null, Modifier.size(22.dp), colorScheme.onBackground)
                        },
                        onClick = onOpenAiConfig,
                    )
                }
            }
        }

        item(key = "text") {
            Column {
                SmallTitle(text = "替换")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "替换规则",
                        summary = "后缀、颜文字、按条件替换",
                        startAction = {
                            Icon(AppIcons.Rule, null, Modifier.size(22.dp), colorScheme.onBackground)
                        },
                        onClick = onOpenTextRules,
                    )
                }
            }
        }
    }
}
