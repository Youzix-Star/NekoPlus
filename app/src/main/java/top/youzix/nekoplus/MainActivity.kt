/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.youzix.nekoplus.service.MiaoAccessibilityService
import top.youzix.nekoplus.ui.UiEngine
import top.youzix.nekoplus.ui.UiEnginePrefs
import top.youzix.nekoplus.ui.material3.MaterialApp
import top.youzix.nekoplus.ui.miuix.MiuixApp
import top.youzix.nekoplus.ui.provision.ProvisionGuide

/**
 * Picks the UI engine and hands the whole window to it.
 *
 * Two complete implementations of the same app live side by side — [MiuixApp] and [MaterialApp] —
 * and the choice is persisted, so switching in Settings takes effect immediately.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A first run walks through the guide; every later start goes straight to the app, and
        // About can bring the guide back. The guide is HyperCeiler's provisioning flow, ported as
        // its own activities, so the launch is handed over before any Compose content exists —
        // which is the same moment the Compose guide it replaces used to open at, and it comes back
        // to this activity when it is done.
        if (!ProvisionGuide.isDone(this)) {
            ProvisionGuide.launch(this)
            finish()
            return
        }
        MiaoState.engine = UiEnginePrefs.load(this)
        MiaoState.useBlur = UiEnginePrefs.loadUseBlur(this)
        MiaoState.floatingColorSource = UiEnginePrefs.loadFloatingColor(this)
        MiaoState.developerMode = UiEnginePrefs.loadDeveloperMode(this)
        setContent {
            when (MiaoState.engine) {
                UiEngine.Miuix -> MiuixApp()
                UiEngine.Material3 -> MaterialApp()
            }
        }
    }

    /**
     * The accessibility switch can only be flipped in system settings, so re-read it whenever the
     * app comes back to the foreground — that is precisely when it may have changed.
     */
    override fun onResume() {
        super.onResume()
        MiaoState.accessibilityEnabled = MiaoAccessibilityService.isEnabled(this)
    }
}
