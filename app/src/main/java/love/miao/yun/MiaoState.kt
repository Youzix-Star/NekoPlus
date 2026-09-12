/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import love.miao.yun.ui.UiEngine

/**
 * Process-wide UI state for the shell.
 *
 * This build is a UI skeleton: nothing is processed and only the floating-window flag and the
 * selected UI engine are live. [service.FloatingWindowService] flips the flag when it starts and
 * stops; the home page's status card reads it.
 */
object MiaoState {
    var floatingRunning by mutableStateOf(false)

    /** Which engine draws the app; persisted on change via [love.miao.yun.ui.UiEnginePrefs]. */
    var engine by mutableStateOf(UiEngine.Miuix)

    /** Placeholder counters so the home page has something to draw. */
    var todayCount by mutableStateOf(0)
    var ruleCount by mutableStateOf(0)
}
