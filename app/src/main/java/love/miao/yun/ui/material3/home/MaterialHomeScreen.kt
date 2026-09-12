/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * The status card, the statistic cards and the page skeleton follow InstallerX-Revived's
 * Material 3 home page (GPL-3.0), which this project is licensed to build upon.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package love.miao.yun.ui.material3.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import love.miao.yun.BuildConfig
import love.miao.yun.MiaoState
import love.miao.yun.ui.material3.material3AppBarColor
import love.miao.yun.ui.material3.material3BlurEffect
import love.miao.yun.ui.material3.rememberMaterial3BlurBackdrop
import love.miao.yun.ui.material3.widgets.BaseWidget
import love.miao.yun.ui.material3.widgets.SegmentedColumn
import top.yukonga.miuix.kmp.blur.layerBackdrop

private const val REPOSITORY_URL = "https://github.com/Youzix-Star/NekoPlus"

@Composable
fun MaterialHomeScreen(
    outerPadding: PaddingValues,
    useBlur: Boolean,
    floatingRunning: Boolean,
    hasOverlayPermission: Boolean,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    val uriHandler = LocalUriHandler.current
    val deviceName = remember {
        listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.material3BlurEffect(backdrop),
                title = {
                    Text(
                        text = "喵喵助手",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                },
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
            contentPadding = PaddingValues(16.dp) + paddingValues + outerPadding,
        ) {
            item {
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

            item { Spacer(modifier = Modifier.size(12.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        title = "今日处理",
                        value = MiaoState.todayCount.toString(),
                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                    )
                    StatCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        title = "启用规则",
                        value = MiaoState.ruleCount.toString(),
                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                    )
                }
            }

            item {
                SegmentedColumn(
                    title = "设备信息",
                    contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                ) {
                    item {
                        BaseWidget(
                            title = "设备型号",
                            description = deviceName,
                            iconPlaceholder = false,
                        )
                    }
                    item {
                        BaseWidget(
                            title = "系统版本",
                            description = "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
                            iconPlaceholder = false,
                        )
                    }
                    item {
                        BaseWidget(
                            title = "应用版本",
                            description = BuildConfig.VERSION_NAME,
                            iconPlaceholder = false,
                        )
                    }
                    item {
                        BaseWidget(
                            title = "悬浮窗",
                            description = when {
                                floatingRunning -> "正在运行"
                                hasOverlayPermission -> "已授权，未运行"
                                else -> "未授权"
                            },
                            descriptionColor = if (hasOverlayPermission) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            iconPlaceholder = false,
                        )
                    }
                }
            }

            item {
                SegmentedColumn(
                    title = "了解更多",
                    contentPadding = PaddingValues(top = 16.dp),
                ) {
                    item {
                        BaseWidget(
                            iconPlaceholder = false,
                            title = "项目主页",
                            description = "在 GitHub 上查看喵喵助手的源码与发布",
                            onClick = { uriHandler.openUri(REPOSITORY_URL) },
                        )
                    }
                }
            }
        }
    }
}

/** Big value over a caption, mirroring the reference project's stat cards. */
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit = {},
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The status block that owns the top of the home page: one colour-coded, tappable card that says
 * what the app is doing right now.
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
    val icon = if (active) Icons.TwoTone.TaskAlt else Icons.TwoTone.Warning
    val title = when {
        !hasOverlayPermission -> "需要悬浮窗权限"
        floatingRunning -> "正在作为悬浮窗"
        else -> "悬浮窗未运行"
    }
    val description = when {
        !hasOverlayPermission -> "点击前往系统设置授权，然后回来启动"
        floatingRunning -> "悬浮窗已经在屏幕上了，点击可以收起"
        else -> "点击启动悬浮窗"
    }

    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(horizontal = 4.dp),
                )
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = contentColor,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmallEmphasized,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
