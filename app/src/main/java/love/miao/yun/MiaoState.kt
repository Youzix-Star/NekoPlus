/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide UI state for the shell.
 *
 * This build is a UI skeleton: nothing is persisted and nothing is processed. The only live value
 * is whether the floating window is on screen, which [service.FloatingWindowService] flips when it
 * starts and stops, and which the home page's status card reads.
 */
object MiaoState {
    var floatingRunning by mutableStateOf(false)

    /** Placeholder counters so the home page has something to draw. */
    var todayCount by mutableStateOf(0)
    var ruleCount by mutableStateOf(0)
    var startedAt by mutableStateOf(0L)
}
