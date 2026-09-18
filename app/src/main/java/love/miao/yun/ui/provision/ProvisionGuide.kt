/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The one place where this app talks to the ported first-run guide.
 *
 * The guide is HyperCeiler's `library/provision` module (AGPL-3.0-only, Copyright (C) 2023-2026
 * HyperCeiler Contributions), copied into this repository as-is: its activities, fragments,
 * layouts, drawables and AGSL shader all live under `com.sevtinge.hyperceiler.provision` and
 * `fan.provision`. This file adds nothing to it — it only answers the two questions the app has to
 * ask, "should the guide be shown?" and "start it", in terms of the flag that was already there
 * before the port (`OnboardingPrefs`), so the guide still behaves like the Compose one it replaces:
 * once on first launch, and again only when About asks for it.
 */
package love.miao.yun.ui.provision

import android.content.Context
import android.content.Intent
import com.sevtinge.hyperceiler.provision.activity.DefaultActivity
import fan.provision.OobeUtils
import love.miao.yun.ui.onboarding.OnboardingPrefs

object ProvisionGuide {

    /** Whether the user has already been through the guide — the app's own flag, unchanged. */
    @JvmStatic
    fun isDone(context: Context): Boolean = OnboardingPrefs.isDone(context)

    /**
     * Marks the guide as seen; called when it completes.
     *
     * `OobeUtils.setProvisioned` is set separately by the guide's own congratulation page, exactly
     * as upstream does, so the two flags stay independent: this one is the app's, that one is the
     * guide's.
     */
    @JvmStatic
    fun markDone(context: Context) {
        OnboardingPrefs.setDone(context, true)
    }

    /**
     * Starts the guide at the very first page.
     *
     * `OobeUtils.resetOobeState` is the module's own reset: it clears `pref_oobe_state`, which holds
     * both the "already provisioned" flag (the guide's activities finish themselves while it is set)
     * and the persisted state chain — without clearing that, a second run from About would resume on
     * the congratulation page.
     */
    @JvmStatic
    fun launch(context: Context) {
        OobeUtils.resetOobeState(context)
        context.startActivity(Intent(context, DefaultActivity::class.java))
    }
}
