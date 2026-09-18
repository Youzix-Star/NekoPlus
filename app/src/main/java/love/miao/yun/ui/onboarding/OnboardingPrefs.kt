/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui.onboarding

import android.content.Context

/**
 * Whether the first-run guide has been seen.
 *
 * It is deliberately a preference rather than a "first launch" guess: the user can bring the guide
 * back from About at any time, and closing it again must not depend on how the app was started.
 */
object OnboardingPrefs {
    private const val PREFS = "onboarding"
    private const val KEY_DONE = "done"

    fun isDone(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONE, false)

    fun setDone(context: Context, done: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONE, done)
            .apply()
    }
}
