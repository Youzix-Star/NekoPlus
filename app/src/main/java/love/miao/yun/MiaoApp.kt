/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun

import android.app.Application
import love.miao.yun.util.CrashHandler

/** Installs the crash recorder before anything else in the app has a chance to fail. */
class MiaoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
