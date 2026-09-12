/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.miuix.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import love.miao.yun.MiaoState
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.miuix.ThemeModeOptions
import love.miao.yun.ui.rememberBackupActions
import love.miao.yun.ui.predictiveback.PredictiveBackStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    colorSchemeMode: ColorSchemeMode,
    onColorSchemeModeChange: (ColorSchemeMode) -> Unit,
    useLiquidGlass: Boolean,
    onUseLiquidGlassChange: (Boolean) -> Unit,
    engine: UiEngine,
    onEngineChange: (UiEngine) -> Unit,
    onNotify: (String) -> Unit,
    onOpenAiConfig: () -> Unit,
) {
    var autoStart by remember { mutableStateOf(false) }
    var keepAlive by remember { mutableStateOf(true) }
    val backup = rememberBackupActions(onNotify)

    // One row that opens a chooser, mirroring how the reference app picks its UI engine.
    val themeItems = remember { ThemeModeOptions.map { DropdownItem(text = it.second) } }
    val engineItems = remember { UiEngine.entries.map { DropdownItem(text = it.label) } }
    val backStyleItems = remember { PredictiveBackStyle.entries.map { DropdownItem(text = it.label) } }
    val context = LocalContext.current
    val selectedThemeIndex = ThemeModeOptions
        .indexOfFirst { it.first == colorSchemeMode }
        .coerceAtLeast(0)

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
                    WindowSpinnerPreference(
                        title = "主题模式",
                        items = themeItems,
                        selectedIndex = selectedThemeIndex,
                        onSelectedIndexChange = { index ->
                            ThemeModeOptions.getOrNull(index)?.let { onColorSchemeModeChange(it.first) }
                        },
                    )
                    WindowSpinnerPreference(
                        title = "预见式返回动画",
                        summary = "二级页面的返回跟手动画",
                        items = backStyleItems,
                        selectedIndex = PredictiveBackStyle.entries
                            .indexOf(MiaoState.predictiveBackStyle).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            PredictiveBackStyle.entries.getOrNull(index)?.let {
                                MiaoState.predictiveBackStyle = it
                                UiEnginePrefs.savePredictiveBackStyle(context, it)
                            }
                        },
                    )
                    SwitchPreference(
                        title = "液态玻璃底栏",
                        summary = "底栏实时模糊与高光",
                        checked = useLiquidGlass,
                        onCheckedChange = onUseLiquidGlassChange,
                    )
                }
            }
        }

        item(key = "engine") {
            Column {
                SmallTitle(text = "界面引擎")
                Card(modifier = Modifier.fillMaxWidth()) {
                    WindowSpinnerPreference(
                        title = "界面引擎",
                        summary = "切换整套界面实现",
                        items = engineItems,
                        selectedIndex = UiEngine.entries.indexOf(engine).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            UiEngine.entries.getOrNull(index)?.let(onEngineChange)
                        },
                    )
                }
            }
        }

        item(key = "ai") {
            Column {
                SmallTitle(text = "AI 修改文本")
                Card(modifier = Modifier.fillMaxWidth()) {
                    // The accessibility switch itself lives on the home page as a status card;
                    // this page only configures the feature.
                    ArrowPreference(
                        title = "AI 配置",
                        summary = "接口、密钥与提示词",
                        onClick = onOpenAiConfig,
                    )
                }
            }
        }

        item(key = "backup") {
            Column {
                SmallTitle(text = "备份")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "导出配置",
                        summary = "全部设置存成一个 JSON",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Update,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = backup.export,
                    )
                    ArrowPreference(
                        title = "导入配置",
                        summary = "从 JSON 恢复，注意文件里含 API Key",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = backup.import,
                    )
                }
            }
        }

        item(key = "service") {
            Column {
                SmallTitle(text = "服务")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "开机自启",
                        summary = "开机自动启动",
                        checked = autoStart,
                        onCheckedChange = { autoStart = it },
                    )
                    SwitchPreference(
                        title = "保持运行",
                        summary = "常驻通知，降低被杀概率",
                        checked = keepAlive,
                        onCheckedChange = { keepAlive = it },
                    )
                }
            }
        }
    }
}
