/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import love.miao.yun.ui.AppIcons
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One page of the first-run guide. */
private class GuidePage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/**
 * The first-run guide, drawn over everything else while it is open.
 *
 * It exists because the app needs two system switches and one optional API key before it does
 * anything useful, and finding that out by trial and error is the worst possible introduction.
 * The About page can bring it back at any time.
 */
@Composable
fun MiuixOnboarding(
    accessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onFinish: () -> Unit,
) {
    val pages = remember {
        listOf(
            GuidePage(
                icon = AppIcons.Sparkle,
                title = "把输入框里的字改好",
                body = "点悬浮窗上的按钮：抓取当前输入框，交给 AI 改写，再写回原处。全程不用来回切应用。",
            ),
            GuidePage(
                icon = AppIcons.Grant,
                title = "先开两个开关",
                body = "无障碍服务用来读写输入框，悬浮窗权限用来把按钮显示在屏幕上。",
            ),
            GuidePage(
                icon = AppIcons.Touch,
                title = "按钮怎么用",
                body = "点按执行动作，长按执行另一个，拖动移动，松手自动贴边。在「悬浮窗」页签里可以加按钮、换图标和文字、改宽高。",
            ),
            GuidePage(
                icon = AppIcons.Key,
                title = "配好 AI 就能用",
                body = "在「设置 → AI 配置」里填接口地址和 API Key。界面也可以在设置里换成 Miuix 或 Material Design。",
            ),
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    BackHandler { onFinish() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "跳过",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .clickable { onFinish() }
                    .padding(8.dp),
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MiuixTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = page.title,
                    style = MiuixTheme.textStyles.title2,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = page.body,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )

                // The two switches the guide is really about, with their live state.
                if (index == 1) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        GuideSwitch(
                            title = if (accessibilityEnabled) "无障碍服务已开启" else "无障碍服务未开启",
                            action = if (accessibilityEnabled) "已开启" else "去开启",
                            onClick = onOpenAccessibility,
                        )
                        GuideSwitch(
                            title = if (hasOverlayPermission) "悬浮窗权限已授予" else "悬浮窗权限未授予",
                            action = if (hasOverlayPermission) "已授予" else "去授权",
                            onClick = onRequestOverlay,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.indices.forEach { index ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 9.dp else 7.dp)
                        .background(
                            color = if (active) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.secondaryContainer
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pagerState.currentPage > 0) {
                Card(
                    modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(vertical = 12.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    ),
                    onClick = {
                        scope.launch { pagerState.animateToPage(pagerState.currentPage - 1) }
                    },
                    showIndication = true,
                ) {
                    Text(
                        text = "上一步",
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Button(
                onClick = {
                    if (lastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (lastPage) "开始使用" else "下一步")
            }
        }
    }
}

/** One row of the permission card: a status and the way to change it. */
@Composable
private fun GuideSwitch(title: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}
