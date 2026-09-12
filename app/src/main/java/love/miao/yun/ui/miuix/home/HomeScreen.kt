/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.miuix.home

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import love.miao.yun.BuildConfig
import love.miao.yun.MiaoState
import love.miao.yun.ui.AppIcons
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    floatingRunning: Boolean,
    hasOverlayPermission: Boolean,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Two status cards on equal footing: the two things the user has to switch on for this
        // app to do anything. Both are tappable straight from here.
        item(key = "status-floating") {
            StatusCard(
                active = hasOverlayPermission && floatingRunning,
                icon = AppIcons.Floating,
                title = when {
                    !hasOverlayPermission -> "需要悬浮窗权限"
                    floatingRunning -> "正在作为悬浮窗"
                    else -> "悬浮窗未运行"
                },
                description = when {
                    !hasOverlayPermission -> "缺少叠加层权限"
                    floatingRunning -> "已在屏幕上"
                    else -> "当前未运行"
                },
                hint = when {
                    !hasOverlayPermission -> "去授权"
                    floatingRunning -> "点击收起"
                    else -> "点击启动"
                },
                onClick = {
                    if (hasOverlayPermission) {
                        onToggleFloating()
                        onNotify(if (floatingRunning) "悬浮窗已收起" else "悬浮窗已启动")
                    } else {
                        onRequestOverlay()
                    }
                },
            )
        }

        item(key = "status-accessibility") {
            val enabled = MiaoState.accessibilityEnabled
            CompactStatusCard(
                active = enabled,
                icon = AppIcons.Grant,
                title = if (enabled) "无障碍服务已开启" else "无障碍服务未开启",
                summary = if (enabled) {
                    "可读写当前输入框"
                } else {
                    "AI 修改依赖它"
                },
                hint = if (enabled) "已开启" else "去开启",
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    onNotify(if (enabled) "已开启" else "请在系统设置中找到「喵喵助手文本捕获」")
                },
            )
        }

        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatisticCard(
                    title = "今日处理",
                    value = MiaoState.todayCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatisticCard(
                    title = "启用规则",
                    value = MiaoState.ruleCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatisticCard(
                    title = "运行状态",
                    value = if (floatingRunning) "运行中" else "已停止",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "overview") {
            Column {
                SmallTitle(text = "概览")
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "应用版本",
                        summary = BuildConfig.VERSION_NAME,
                        startAction = {
                            Icon(
                                imageVector = AppIcons.About,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                    )
                    BasicComponent(
                        title = "系统版本",
                        summary = "Android ${Build.VERSION.RELEASE}",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Phones,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                    )
                    BasicComponent(
                        title = "悬浮窗权限",
                        summary = if (hasOverlayPermission) "已授予" else "未授予",
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Grant,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                    )
                }
            }
        }

        item(key = "quick") {
            Column {
                SmallTitle(text = "快捷操作")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = if (floatingRunning) "收起悬浮窗" else "启动悬浮窗",
                        summary = if (hasOverlayPermission) {
                            "任意界面上的悬浮按钮"
                        } else {
                            "需要先授予悬浮窗权限"
                        },
                        startAction = {
                            Icon(
                                imageVector = AppIcons.Floating,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        onClick = {
                            if (hasOverlayPermission) onToggleFloating() else onRequestOverlay()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The big status block that owns the top of the home page, in the same spirit as the reference
 * app's "acting as the default installer" card: one colour-coded, tappable surface that says what
 * the app is doing right now.
 */
@Composable
private fun StatusCard(
    active: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    hint: String,
    onClick: () -> Unit,
) {
    val containerColor = if (active) {
        MiuixTheme.colorScheme.primaryContainer
    } else {
        MiuixTheme.colorScheme.errorContainer
    }
    val contentColor = if (active) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MiuixTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        // miuix's card carries no padding of its own, so the content pads itself.
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        colors = CardDefaults.defaultColors(color = containerColor, contentColor = contentColor),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(88.dp)
                    .alpha(0.16f),
                tint = contentColor,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MiuixTheme.textStyles.title4,
                )
                Text(
                    text = description,
                    color = contentColor,
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = hint,
                    color = contentColor,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(top = 8.dp).alpha(0.75f),
                )
            }
        }
    }
}

@Composable
private fun StatisticCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The small companion to [StatusCard]: one line saying whether something is on, and what to do
 * about it. Two full-height blocks stacked on top of each other read as a wall, so the second of
 * the two things this app has to have switched on gets the compact treatment instead.
 */
@Composable
private fun CompactStatusCard(
    active: Boolean,
    icon: ImageVector,
    title: String,
    summary: String,
    hint: String,
    onClick: () -> Unit,
) {
    val containerColor = if (active) {
        MiuixTheme.colorScheme.primaryContainer
    } else {
        MiuixTheme.colorScheme.errorContainer
    }
    val contentColor = if (active) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MiuixTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = containerColor, contentColor = contentColor),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = summary,
                    color = contentColor,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.alpha(0.75f),
                )
            }
            Text(
                text = hint,
                color = contentColor,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.alpha(0.75f),
            )
        }
    }
}
