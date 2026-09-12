/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import love.miao.yun.ui.material3.ThemeMode
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.DropDownMenuWidget
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget
import love.miao.yun.ui.predictiveback.PredictiveBackStyle
import top.yukonga.miuix.kmp.blur.layerBackdrop

@Composable
fun MaterialSettingsScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onNotify: (String) -> Unit,
    onOpenAiConfig: () -> Unit,
) {
    val context = LocalContext.current
    var autoStart by remember { mutableStateOf(false) }
    var keepAlive by remember { mutableStateOf(true) }
    val engine = MiaoState.engine
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = { Text("设置", modifier = Modifier.padding(start = 12.dp)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backdrop.material3AppBarColor(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = backdrop.material3AppBarColor(),
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
            contentPadding = paddingValues + outerPadding,
        ) {
            item {
                SegmentedColumn(title = "外观") {
                    item {
                        DropDownMenuWidget(
                            icon = AppIcons.Settings,
                            title = "主题模式",
                            choice = ThemeMode.entries.indexOf(themeMode).coerceAtLeast(0),
                            data = ThemeMode.entries.map { it.label },
                            onChoiceChange = { index ->
                                ThemeMode.entries.getOrNull(index)?.let(onThemeModeChange)
                            },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = AppIcons.Tune,
                            title = "动态取色",
                            description = "跟随壁纸取色",
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange,
                        )
                    }
                    item {
                        DropDownMenuWidget(
                            icon = AppIcons.Back,
                            title = "预见式返回动画",
                            description = "二级页面返回时的跟手动画",
                            choice = PredictiveBackStyle.entries
                                .indexOf(MiaoState.predictiveBackStyle).coerceAtLeast(0),
                            data = PredictiveBackStyle.entries.map { it.label },
                            onChoiceChange = { index ->
                                PredictiveBackStyle.entries.getOrNull(index)?.let {
                                    MiaoState.predictiveBackStyle = it
                                    UiEnginePrefs.savePredictiveBackStyle(context, it)
                                }
                            },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = AppIcons.Tune,
                            title = "毛玻璃顶栏",
                            description = "顶栏实时模糊",
                            checked = MiaoState.useBlur,
                            onCheckedChange = {
                                MiaoState.useBlur = it
                                UiEnginePrefs.saveUseBlur(context, it)
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "界面引擎") {
                    item {
                        DropDownMenuWidget(
                            icon = AppIcons.Tune,
                            title = "界面引擎",
                            description = "切换整套界面实现",
                            choice = UiEngine.entries.indexOf(engine).coerceAtLeast(0),
                            data = UiEngine.entries.map { it.label },
                            onChoiceChange = { index ->
                                UiEngine.entries.getOrNull(index)?.let {
                                    MiaoState.engine = it
                                    UiEnginePrefs.save(context, it)
                                }
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "AI 修改文本") {
                    // The accessibility switch itself is a status card on the home page; this
                    // page only configures the feature.
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Tune,
                            title = "AI 配置",
                            description = "接口、密钥与提示词",
                            onClick = onOpenAiConfig,
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
                            description = "开机自动启动",
                            checked = autoStart,
                            onCheckedChange = { autoStart = it },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = AppIcons.Settings,
                            title = "保持运行",
                            description = "常驻通知，降低被杀概率",
                            checked = keepAlive,
                            onCheckedChange = { keepAlive = it },
                        )
                    }
                }
            }
        }
    }
}
