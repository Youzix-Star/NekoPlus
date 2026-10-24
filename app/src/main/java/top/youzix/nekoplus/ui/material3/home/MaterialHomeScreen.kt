/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The layout is SukiSU-Ultra's HomeMaterial.kt (GPL-3.0): one tall hero surface with a status tag
 * and a big number, a row of tiles under it, then the spec rows. What it shows here is this app's
 * own state, not the reference project's root/kernel data.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package top.youzix.nekoplus.ui.material3.home

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.youzix.nekoplus.BuildConfig
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.material3.components.ExpressiveScaffold
import top.youzix.nekoplus.ui.material3.components.IconTile
import top.youzix.nekoplus.ui.material3.components.SegmentedColumn
import top.youzix.nekoplus.ui.material3.components.SegmentedListItem
import top.youzix.nekoplus.ui.material3.components.StatusTag
import top.youzix.nekoplus.ui.material3.components.TonalCard
import top.youzix.nekoplus.ui.material3.components.expressiveTopAppBarColors
import top.youzix.nekoplus.ui.material3.material3BlurEffect
import top.youzix.nekoplus.ui.material3.rememberMaterial3BlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

private val HeroShape = RoundedCornerShape(28.dp)
private val TileShape = RoundedCornerShape(22.dp)

/**
 * 首页 — the status board.
 *
 * Three things in the order they matter: whether the floating window is doing anything, whether
 * the accessibility service is on, and the counters. The hero card is the action; the row under it
 * is one tap into system settings.
 */
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
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)

    ExpressiveScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("NekoPlus") },
                modifier = Modifier.material3BlurEffect(backdrop),
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroCard(
                active = hasOverlayPermission && floatingRunning,
                title = when {
                    !hasOverlayPermission -> "需要悬浮窗权限"
                    floatingRunning -> "悬浮窗运行中"
                    else -> "悬浮窗未运行"
                },
                description = when {
                    !hasOverlayPermission -> "没有叠加层权限，按钮放不上屏幕"
                    floatingRunning -> "按钮已经在屏幕上了，点一下收起"
                    else -> "点一下把按钮放到屏幕上"
                },
                badge = when {
                    !hasOverlayPermission -> "待授权"
                    floatingRunning -> "运行中"
                    else -> "已停止"
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

            AccessibilityRow(
                enabled = MiaoState.accessibilityEnabled,
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    onNotify(
                        if (MiaoState.accessibilityEnabled) "已开启"
                        else "请在系统设置里打开「NekoPlus 文本捕获」",
                    )
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(
                    value = MiaoState.todayCount.toString(),
                    label = "今日处理",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = MiaoState.ruleCount.toString(),
                    label = "启用规则",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = if (floatingRunning) "运行中" else "已停止",
                    label = "运行状态",
                    modifier = Modifier.weight(1f),
                )
            }

            SegmentedColumn(title = "概览") {
                item {
                    SegmentedListItem(
                        headlineContent = { Text("应用版本") },
                        supportingContent = { Text(BuildConfig.VERSION_NAME) },
                        leadingContent = { IconTile(AppIcons.Update, "应用版本") },
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = { Text("系统版本") },
                        supportingContent = { Text("Android ${Build.VERSION.RELEASE}") },
                        leadingContent = { IconTile(AppIcons.Phones, "系统版本") },
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = { Text("界面引擎") },
                        supportingContent = { Text(MiaoState.engine.label) },
                        leadingContent = { IconTile(AppIcons.Gallery, "界面引擎") },
                    )
                }
            }

            Spacer(Modifier.height(outerPadding.calculateBottomPadding() + 24.dp))
        }
    }
}

/** The tall hero surface: status tag, headline, and a monospace line like the reference app's. */
@Composable
private fun HeroCard(
    active: Boolean,
    title: String,
    description: String,
    badge: String,
    onClick: () -> Unit,
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = HeroShape,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 188.dp)
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusTag(
                    label = badge,
                    backgroundColor = contentColor,
                    contentColor = containerColor,
                )
                Icon(
                    imageVector = AppIcons.Floating,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** One line saying whether the accessibility service is on, and one tap to go change it. */
@Composable
private fun AccessibilityRow(enabled: Boolean, onClick: () -> Unit) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        contentColor = contentColor,
        shape = TileShape,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.Grant,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = if (enabled) "无障碍服务已开启" else "无障碍服务未开启",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (enabled) "可以读写当前输入框" else "AI 修改依赖它",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = if (enabled) "已开启" else "去开启",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** One number and its label. */
@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    TonalCard(
        modifier = modifier,
        shape = TileShape,
    ) {
        Column(
            modifier = Modifier
                .height(88.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
