/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.settings

import android.content.Intent
import android.provider.Settings
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
import love.miao.yun.service.MiaoAccessibilityService
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.FloatingColorSource
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.material3.ThemeMode
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.DropDownMenuWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget
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
    val accessibilityEnabled = MiaoAccessibilityService.isEnabled(context)
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
                            description = "Android 12+ 跟随壁纸取色",
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange,
                        )
                    }
                    item {
                        DropDownMenuWidget(
                            icon = AppIcons.Floating,
                            title = "悬浮窗取色",
                            description = "悬浮层独立取色，可跟随动态取色、Miuix 或 Material Design",
                            choice = FloatingColorSource.entries
                                .indexOf(MiaoState.floatingColorSource).coerceAtLeast(0),
                            data = FloatingColorSource.entries.map { it.label },
                            onChoiceChange = { index ->
                                FloatingColorSource.entries.getOrNull(index)?.let {
                                    MiaoState.floatingColorSource = it
                                    UiEnginePrefs.saveFloatingColor(context, it)
                                }
                            },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = AppIcons.Tune,
                            title = "毛玻璃顶栏",
                            description = "顶栏使用实时模糊；关闭后为不透明表面色",
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
                            description = "Miuix 与 Material Design 是两套完整的界面实现",
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
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Tune,
                            title = "AI 配置",
                            description = "接口地址、API Key、模型与提示词",
                            onClick = onOpenAiConfig,
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Grant,
                            title = "无障碍服务",
                            description = if (accessibilityEnabled) {
                                "已开启，可以读取并写回当前输入框"
                            } else {
                                "未开启，AI 修改需要它才能拿到输入框文本"
                            },
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                    )
                                }
                            },
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
}
