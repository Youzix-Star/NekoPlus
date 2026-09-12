/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import love.miao.yun.ui.FloatingColorSource
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.predictiveback.PredictiveBackStyle

/**
 * Process-wide UI state for the shell.
 *
 * This build is a UI skeleton: nothing is processed and only the floating-window flag and the
 * selected UI engine are live. [service.FloatingWindowService] flips the flag when it starts and
 * stops; the home page's status card reads it.
 */
object MiaoState {
    var floatingRunning by mutableStateOf(false)

    /**
     * Whether the accessibility service is switched on.
     *
     * Refreshed by [MainActivity.onResume] rather than polled: the only way this changes is the
     * user leaving for system settings and coming back, which is exactly when onResume runs.
     */
    var accessibilityEnabled by mutableStateOf(false)

    /** Which engine draws the app; persisted on change via [love.miao.yun.ui.UiEnginePrefs]. */
    var engine by mutableStateOf(UiEngine.Miuix)

    /**
     * Translucent surfaces: the miuix bottom bar's liquid glass, and the Material 3 top bar's
     * blur. Persisted alongside the engine. Switching it off also avoids the RenderEffect cost
     * on devices that can technically render it.
     */
    var useBlur by mutableStateOf(true)

    /** Which palette the floating window uses; persisted alongside the engine. */
    var floatingColorSource by mutableStateOf(FloatingColorSource.Dynamic)

    /** Which predictive-back animation plays on second-level pages. */
    var predictiveBackStyle by mutableStateOf(PredictiveBackStyle.Aosp)

    /** Placeholder counters so the home page has something to draw. */
    var todayCount by mutableStateOf(0)
    var ruleCount by mutableStateOf(0)
}
