/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui.onboarding

import androidx.compose.ui.graphics.vector.ImageVector
import love.miao.yun.ui.AppIcons

/** One page of the first-run guide. */
internal class GuidePage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/**
 * The guide's pages.
 *
 * They live here rather than inside either engine, so the miuix and Material versions can never
 * drift apart in what they actually tell the user.
 */
internal val GuidePages: List<GuidePage> = listOf(
    GuidePage(
        icon = AppIcons.Sparkle,
        title = "把输入框里的字改好",
        body = "点悬浮窗上的按钮：抓内容 → AI 改写 → 写回。",
    ),
    GuidePage(
        icon = AppIcons.Grant,
        title = "先开两个开关",
        body = "无障碍服务读写输入框，悬浮窗权限显示按钮。",
    ),
    GuidePage(
        icon = AppIcons.Touch,
        title = "按钮怎么用",
        body = "点按、长按各执行一个动作，拖动移动，松手贴边。",
    ),
    GuidePage(
        icon = AppIcons.Key,
        title = "只在你点的时候才动",
        body = "平时不读取任何内容。填好「设置 → AI 配置」就能用。",
    ),
)
