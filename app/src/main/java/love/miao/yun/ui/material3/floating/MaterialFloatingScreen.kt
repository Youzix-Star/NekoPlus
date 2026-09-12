/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.floating

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import love.miao.yun.MiaoState
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.FloatingColorSource
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.BaseItemContainer
import love.miao.yun.ui.material3.widgets.DropDownMenuWidget
import love.miao.yun.ui.material3.widgets.IntNumberPickerWidget
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget
import top.yukonga.miuix.kmp.blur.layerBackdrop

@Composable
fun MaterialFloatingScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    floatingRunning: Boolean,
    onToggleFloating: () -> Unit,
    onNotify: (String) -> Unit,
) {
    // Kept as Ints because the ported reference widget is integer-based.
    var size by remember { mutableIntStateOf(48) }
    var corner by remember { mutableIntStateOf(24) }
    var opacityPercent by remember { mutableIntStateOf(90) }
    var snapToEdge by remember { mutableStateOf(true) }
    var haptic by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
                title = { Text("悬浮窗", modifier = Modifier.padding(start = 12.dp)) },
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
                SegmentedColumn(title = "悬浮窗") {
                    item {
                        NavigationItemWidget(
                            icon = AppIcons.Floating,
                            title = if (floatingRunning) "收起悬浮窗" else "启动悬浮窗",
                            description = if (floatingRunning) "当前正在屏幕上显示" else "还没有启动",
                            onClick = {
                                onToggleFloating()
                                onNotify(if (floatingRunning) "悬浮窗已收起" else "悬浮窗已启动")
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "行为") {
                    item {
                        SwitchWidget(
                            icon = AppIcons.Tune,
                            title = "贴边吸附",
                            description = "松手后自动吸到屏幕边缘",
                            checked = snapToEdge,
                            onCheckedChange = { snapToEdge = it },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = AppIcons.Tune,
                            title = "拖动反馈",
                            description = "拖动时轻微震动",
                            checked = haptic,
                            onCheckedChange = { haptic = it },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = "外观") {
                    item {
                        DropDownMenuWidget(
                            icon = AppIcons.Floating,
                            title = "取色来源",
                            description = "悬浮窗是独立于界面的悬浮层，取色可以单独选择",
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
                        // IntNumberPickerWidget paints no background of its own, so it has
                        // to be wrapped in a container to sit on a card like every other row.
                        BaseItemContainer {
                            IntNumberPickerWidget(
                                title = "悬浮窗大小",
                                value = size,
                                startInt = 32,
                                endInt = 80,
                                valueSuffix = " dp",
                                onValueChange = { size = it },
                            )
                        }
                    }
                    item {
                        // IntNumberPickerWidget paints no background of its own, so it has
                        // to be wrapped in a container to sit on a card like every other row.
                        BaseItemContainer {
                            IntNumberPickerWidget(
                                title = "圆角半径",
                                value = corner,
                                startInt = 0,
                                endInt = 40,
                                valueSuffix = " dp",
                                onValueChange = { corner = it },
                            )
                        }
                    }
                    item {
                        // IntNumberPickerWidget paints no background of its own, so it has
                        // to be wrapped in a container to sit on a card like every other row.
                        BaseItemContainer {
                            IntNumberPickerWidget(
                                title = "不透明度",
                                value = opacityPercent,
                                startInt = 30,
                                endInt = 100,
                                valueSuffix = " %",
                                onValueChange = { opacityPercent = it },
                            )
                        }
                    }
                }
            }
        }
    }
}
