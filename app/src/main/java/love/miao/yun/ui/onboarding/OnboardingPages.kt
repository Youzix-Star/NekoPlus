/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
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
        title = "只在你点的时候才动",
        body = "服务平时不读取任何内容，只有按下按钮的那一刻才抓取当前输入框；API Key 存在本机，请求直接发给你填的接口。填好「设置 → AI 配置」就能用。",
    ),
)
