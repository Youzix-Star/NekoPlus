/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.ui.miuix.home

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.youzix.nekoplus.BuildConfig
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.ui.AppIcons
import top.youzix.nekoplus.ui.miuix.components.BlurredBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 首页 — the status board.
 *
 * Three things in the order they matter: whether the floating window is doing anything, whether
 * the accessibility service is on (AI rewriting dies without it), and the counters. The two big
 * blocks are deliberately not equal: the floating window is the main action and gets the hero
 * card; accessibility is one compact row under it.
 */
@Composable
fun HomeScreen(
    bottomPadding: Dp,
    floatingRunning: Boolean,
    hasOverlayPermission: Boolean,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            BlurredBar(backdrop = null) {
                TopAppBar(
                    title = "NekoPlus",
                    largeTitle = "NekoPlus",
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "floating") {
                HeroStatusCard(
                    active = hasOverlayPermission && floatingRunning,
                    icon = AppIcons.Floating,
                    title = when {
                        !hasOverlayPermission -> "需要悬浮窗权限"
                        floatingRunning -> "悬浮窗运行中"
                        else -> "悬浮窗未运行"
                    },
                    description = when {
                        !hasOverlayPermission -> "没有叠加层权限，按钮放不上屏幕"
                        floatingRunning -> "按钮已经在屏幕上了"
                        else -> "点一下把按钮放到屏幕上"
                    },
                    action = when {
                        !hasOverlayPermission -> "去授权"
                        floatingRunning -> "收起"
                        else -> "启动"
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

            item(key = "accessibility") {
                val enabled = MiaoState.accessibilityEnabled
                CompactStatusCard(
                    active = enabled,
                    icon = AppIcons.Grant,
                    title = if (enabled) "无障碍服务已开启" else "无障碍服务未开启",
                    summary = if (enabled) "可以读写当前输入框" else "AI 修改依赖它",
                    hint = if (enabled) "已开启" else "去开启",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        onNotify(
                            if (enabled) "已开启" else "请在系统设置里打开「NekoPlus 文本捕获」",
                        )
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
                                    imageVector = AppIcons.Update,
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
                            title = "界面引擎",
                            summary = MiaoState.engine.label,
                            startAction = {
                                Icon(
                                    imageVector = AppIcons.Gallery,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The big status block that owns the top of the page, in the spirit of SukiSU's "acting as the
 * default installer" card: one colour-coded surface, a watermark of the action's icon, and a
 * button that does the thing.
 */
@Composable
private fun HeroStatusCard(
    active: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    action: String,
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
        insideMargin = PaddingValues(horizontal = 22.dp, vertical = 22.dp),
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
                    .align(Alignment.BottomEnd)
                    .size(120.dp)
                    .alpha(0.14f),
                tint = contentColor,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 22.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = contentColor,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.alpha(0.85f),
                )
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(
                    text = action,
                    onClick = onClick,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** One number and its label: the three tiles under the hero card. */
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
            text = value,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** The compact companion to [HeroStatusCard]: one line, one tap, straight to system settings. */
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
        MiuixTheme.colorScheme.secondaryContainer
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (active) {
        MiuixTheme.colorScheme.onSecondaryContainer
    } else {
        MiuixTheme.colorScheme.onSurface
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
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = title, color = contentColor, style = MiuixTheme.textStyles.body2)
                Text(
                    text = summary,
                    color = contentColor,
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.alpha(0.7f),
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
