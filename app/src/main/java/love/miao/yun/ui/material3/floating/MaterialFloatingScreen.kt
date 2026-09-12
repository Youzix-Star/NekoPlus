/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * Rows are built from the segmented-column widgets ported from InstallerX-Revived (GPL-3.0).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.floating

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import love.miao.yun.ui.AppIcons
import love.miao.yun.ui.material3.widgets.NavigationItemWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import love.miao.yun.ui.material3.widgets.SwitchWidget
import kotlin.math.roundToInt

@Composable
fun MaterialFloatingScreen(
    outerPadding: PaddingValues,
    floatingRunning: Boolean,
    onToggleFloating: () -> Unit,
    onNotify: (String) -> Unit,
) {
    var size by remember { mutableFloatStateOf(48f) }
    var corner by remember { mutableFloatStateOf(24f) }
    var opacity by remember { mutableFloatStateOf(0.9f) }
    var snapToEdge by remember { mutableStateOf(true) }
    var haptic by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("悬浮窗", modifier = Modifier.padding(start = 12.dp)) },
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
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "外观", style = MaterialTheme.typography.titleMediumEmphasized)
                        SliderRow(
                            label = "悬浮窗大小",
                            valueText = "${size.roundToInt()} dp",
                            value = size,
                            range = 32f..80f,
                            steps = 47,
                            onValueChange = { size = it },
                        )
                        SliderRow(
                            label = "圆角半径",
                            valueText = "${corner.roundToInt()} dp",
                            value = corner,
                            range = 0f..40f,
                            steps = 39,
                            onValueChange = { corner = it },
                        )
                        SliderRow(
                            label = "不透明度",
                            valueText = "${(opacity * 100).roundToInt()}%",
                            value = opacity,
                            range = 0.3f..1f,
                            steps = 13,
                            onValueChange = { opacity = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}
