/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.miuix.floating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import love.miao.yun.ui.AppIcons
import kotlin.math.roundToInt

@Composable
fun FloatingScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
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
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "window") {
            Column {
                SmallTitle(text = "悬浮窗")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = if (floatingRunning) "收起悬浮窗" else "启动悬浮窗",
                        summary = if (floatingRunning) "当前正在屏幕上显示" else "还没有启动",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Floating,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = {
                            onToggleFloating()
                            onNotify(if (floatingRunning) "悬浮窗已收起" else "悬浮窗已启动")
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = "贴边吸附",
                        summary = "松手后自动吸到屏幕边缘",
                        checked = snapToEdge,
                        onCheckedChange = { snapToEdge = it },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = "拖动反馈",
                        summary = "拖动时轻微震动",
                        checked = haptic,
                        onCheckedChange = { haptic = it },
                    )
                }
            }
        }

        item(key = "appearance") {
            Column {
                SmallTitle(text = "外观")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SliderPreference(
                        value = size,
                        onValueChange = { size = it },
                        title = "悬浮窗大小",
                        valueText = "${size.roundToInt()} dp",
                        valueRange = 32f..80f,
                        steps = 47,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SliderPreference(
                        value = corner,
                        onValueChange = { corner = it },
                        title = "圆角半径",
                        valueText = "${corner.roundToInt()} dp",
                        valueRange = 0f..40f,
                        steps = 39,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SliderPreference(
                        value = opacity,
                        onValueChange = { opacity = it },
                        title = "不透明度",
                        valueText = "${(opacity * 100).roundToInt()}%",
                        valueRange = 0.3f..1f,
                        steps = 13,
                    )
                }
            }
        }
    }
}
