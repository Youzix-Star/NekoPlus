/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import love.miao.yun.ui.UiEngine
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.ui.material3.MaterialApp
import love.miao.yun.ui.miuix.MiuixApp

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
        MiaoState.engine = UiEnginePrefs.load(this)
        MiaoState.useBlur = UiEnginePrefs.loadUseBlur(this)
        setContent {
            when (MiaoState.engine) {
                UiEngine.Miuix -> MiuixApp()
                UiEngine.Material3 -> MaterialApp()
            }
        }
    }
}
