/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui.predictiveback

import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

/**
 * Which flavour of predictive back animation plays on a second-level page.
 *
 * Both styles come from the same reference project: [Miuix] is miuix's own built-in transition,
 * [Aosp] is the AOSP-style transition that project reimplements.
 */
enum class PredictiveBackStyle(val id: String, val label: String) {
    /** AOSP: the page scales down, hugs the swiped edge, and drifts with the finger. */
    Aosp("aosp", "AOSP"),

    /** MIUIX: miuix's own built-in navigation transition. */
    Miuix("miuix", "Miuix"),

    /** No visual feedback: the page simply slides away. */
    None("none", "无动画"),
    ;

    val transition: NavTransition
        get() = when (this) {
            Aosp -> aospNavTransition
            Miuix -> NavTransitions.MiuixDefault
            None -> NavTransitions.none
        }

    companion object {
        fun from(id: String?): PredictiveBackStyle =
            entries.firstOrNull { it.id == id } ?: Aosp
    }
}
