/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.material3.floating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
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
import love.miao.yun.ui.material3.LabeledSlider
import love.miao.yun.ui.material3.SectionLabel
import love.miao.yun.ui.material3.SwitchRow
import kotlin.math.roundToInt

@Composable
fun MaterialFloatingScreen(
    contentPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    floatingRunning: Boolean,
    onToggleFloating: () -> Unit,
    onNotify: (String) -> Unit,
) {
    var size by remember { mutableFloatStateOf(48f) }
    var corner by remember { mutableFloatStateOf(24f) }
    var opacity by remember { mutableFloatStateOf(0.9f) }
    var snapToEdge by remember { mutableStateOf(true) }
    var haptic by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "window") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("悬浮窗")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(if (floatingRunning) "收起悬浮窗" else "启动悬浮窗") },
                        supportingContent = {
                            Text(if (floatingRunning) "当前正在屏幕上显示" else "还没有启动")
                        },
                        leadingContent = { Icon(AppIcons.Floating, contentDescription = null) },
                        modifier = Modifier.clickable {
                            onToggleFloating()
                            onNotify(if (floatingRunning) "悬浮窗已收起" else "悬浮窗已启动")
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchRow(
                        title = "贴边吸附",
                        summary = "松手后自动吸到屏幕边缘",
                        checked = snapToEdge,
                        onCheckedChange = { snapToEdge = it },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchRow(
                        title = "拖动反馈",
                        summary = "拖动时轻微震动",
                        checked = haptic,
                        onCheckedChange = { haptic = it },
                    )
                }
            }
        }

        item(key = "appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("外观")
                Card(modifier = Modifier.fillMaxWidth()) {
                    LabeledSlider(
                        label = "悬浮窗大小",
                        valueText = "${size.roundToInt()} dp",
                        value = size,
                        range = 32f..80f,
                        steps = 47,
                        onValueChange = { size = it },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    LabeledSlider(
                        label = "圆角半径",
                        valueText = "${corner.roundToInt()} dp",
                        value = corner,
                        range = 0f..40f,
                        steps = 39,
                        onValueChange = { corner = it },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    LabeledSlider(
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
