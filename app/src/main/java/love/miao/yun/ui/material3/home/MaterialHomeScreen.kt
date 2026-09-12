/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.material3.home

import android.os.Build
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import love.miao.yun.BuildConfig
import love.miao.yun.MiaoState
import love.miao.yun.ui.AppIcons

@Composable
fun MaterialHomeScreen(
    contentPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    floatingRunning: Boolean,
    hasOverlayPermission: Boolean,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onNotify: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "status") {
            StatusCard(
                floatingRunning = floatingRunning,
                hasOverlayPermission = hasOverlayPermission,
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

        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatisticCard("今日处理", MiaoState.todayCount.toString(), Modifier.weight(1f))
                StatisticCard("启用规则", MiaoState.ruleCount.toString(), Modifier.weight(1f))
                StatisticCard(
                    "运行状态",
                    if (floatingRunning) "运行中" else "已停止",
                    Modifier.weight(1f),
                )
            }
        }

        item(key = "overview") {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("应用版本") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(AppIcons.About, contentDescription = null) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("系统版本") },
                    supportingContent = { Text("Android ${Build.VERSION.RELEASE}") },
                    leadingContent = { Icon(AppIcons.Phones, contentDescription = null) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("悬浮窗权限") },
                    supportingContent = { Text(if (hasOverlayPermission) "已授予" else "未授予") },
                    leadingContent = { Icon(AppIcons.Grant, contentDescription = null) },
                )
            }
        }

        item(key = "quick") {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(if (floatingRunning) "收起悬浮窗" else "启动悬浮窗") },
                    supportingContent = {
                        Text(
                            if (hasOverlayPermission) {
                                "在任意界面显示一个可拖动的小面板"
                            } else {
                                "需要先授予悬浮窗权限"
                            },
                        )
                    },
                    leadingContent = { Icon(AppIcons.Floating, contentDescription = null) },
                    modifier = Modifier.clickable {
                        if (hasOverlayPermission) onToggleFloating() else onRequestOverlay()
                    },
                )
            }
        }
    }
}

/**
 * The big status block that owns the top of the home page, matching the miuix engine's card: one
 * colour-coded, tappable surface that says what the app is doing right now.
 */
@Composable
private fun StatusCard(
    floatingRunning: Boolean,
    hasOverlayPermission: Boolean,
    onClick: () -> Unit,
) {
    val active = hasOverlayPermission && floatingRunning
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val title = when {
        !hasOverlayPermission -> "需要悬浮窗权限"
        floatingRunning -> "正在作为悬浮窗"
        else -> "悬浮窗未运行"
    }
    val description = when {
        !hasOverlayPermission -> "点击前往系统设置授权，然后回来启动"
        floatingRunning -> "悬浮窗已经在屏幕上了"
        else -> "点击启动悬浮窗"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Icon(
                imageVector = AppIcons.Floating,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(88.dp)
                    .alpha(0.16f),
                tint = contentColor,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when {
                        !hasOverlayPermission -> "去授权"
                        floatingRunning -> "点击收起"
                        else -> "点击启动"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f),
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
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
