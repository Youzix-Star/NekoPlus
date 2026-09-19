/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The shape of the guide — a full-screen glow, a title block and a list of rows per step — follows
 * HyperCeiler's provisioning flow (library/provision, AGPL-3.0-only). The words are this app's.
 */

package top.youzix.nekoplus.ui.onboarding

import androidx.compose.ui.graphics.vector.ImageVector
import top.youzix.nekoplus.ui.AppIcons

/** What a step is for. The screens switch on this rather than on an index, so reordering is safe. */
internal enum class GuideStep {
    Welcome,
    HowItWorks,
    Permissions,
    Buttons,
    Done,
}

/** One line of a step: an icon, a claim, and the reason to believe it. */
internal class GuideRow(
    val icon: ImageVector,
    val title: String,
    val detail: String,
)

/** One step of the guide. */
internal class GuidePage(
    val step: GuideStep,
    val title: String,
    val subtitle: String,
    val rows: List<GuideRow> = emptyList(),
)

/**
 * The guide's steps.
 *
 * They live here rather than inside either engine, so the miuix and Material versions can never
 * drift apart in what they actually tell the user. The permission step carries no rows on purpose:
 * its lines show live system state and have to be built by the screen.
 */
internal val GuidePages: List<GuidePage> = listOf(
    GuidePage(
        step = GuideStep.Welcome,
        title = "喵喵助手",
        subtitle = "把输入框里的字改好",
    ),
    GuidePage(
        step = GuideStep.HowItWorks,
        title = "它是怎么工作的",
        subtitle = "三步，全都在你按下按钮之后",
        rows = listOf(
            GuideRow(
                icon = AppIcons.Read,
                title = "抓取当前输入框",
                detail = "只找你正在打字的那个框",
            ),
            GuideRow(
                icon = AppIcons.Sparkle,
                title = "按规则或 AI 改写",
                detail = "接口与提示词在「设置 → AI 配置」",
            ),
            GuideRow(
                icon = AppIcons.Write,
                title = "写回同一个框",
                detail = "不满意就直接撤销，原文还在",
            ),
            GuideRow(
                icon = AppIcons.Key,
                title = "只在你点的时候才动",
                detail = "平时不读取任何内容",
            ),
        ),
    ),
    GuidePage(
        step = GuideStep.Permissions,
        title = "两个开关",
        subtitle = "您可以稍后设置下列必要权限",
    ),
    GuidePage(
        step = GuideStep.Buttons,
        title = "悬浮窗按钮",
        subtitle = "点按、长按各执行一个动作",
        rows = listOf(
            GuideRow(
                icon = AppIcons.Touch,
                title = "点按",
                detail = "执行第一个动作",
            ),
            GuideRow(
                icon = AppIcons.Hold,
                title = "长按",
                detail = "执行第二个动作",
            ),
            GuideRow(
                icon = AppIcons.Drag,
                title = "拖动",
                detail = "松手自动贴边，不挡着打字",
            ),
        ),
    ),
    GuidePage(
        step = GuideStep.Done,
        title = "设置完毕",
        subtitle = "Ciallo～(∠・ω c)⌒★",
        rows = listOf(
            GuideRow(
                icon = AppIcons.Tune,
                title = "想换一套界面",
                detail = "设置 → 外观，miuix 与 Material 3 随时切换",
            ),
        ),
    ),
)
