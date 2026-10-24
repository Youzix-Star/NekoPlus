/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package top.youzix.nekoplus.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.youzix.nekoplus.MiaoState
import top.youzix.nekoplus.BuildConfig

/**
 * One paste-ready snapshot of what this build is running on.
 *
 * Read at the moment it is asked for, so the snapshot time means something.
 */
object DeviceInfo {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun snapshot(context: Context): String = buildString {
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}(${Build.DEVICE})")
        appendLine("Package Name: ${context.packageName}")
        appendLine("App Version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
        appendLine("Snapshot Time: ${LocalDateTime.now().format(stamp)}")
        appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Display Size: ${displaySize(context)}")
        appendLine("Android Version: ${Build.VERSION.RELEASE} API${Build.VERSION.SDK_INT}")
        appendLine("Language: ${Locale.getDefault().toLanguageTag()}")
        appendLine("UI Engine: ${MiaoState.engine.label}")
        appendLine("前台服务: " + (top.youzix.nekoplus.service.FloatingWindowService.foregroundLimitation(context) ?: "可用"))
        appendLine("Screen Density: ${context.resources.displayMetrics.densityDpi} dpi")
        append("Dark Theme: ${isDarkNow(context)}")
    }

    /** The real screen, not the window: this is what a screenshot will match. */
    private fun displaySize(context: Context): String {
        val manager = context.getSystemService(WindowManager::class.java)
        val bounds = manager?.currentWindowMetrics?.bounds ?: return "unknown"
        return "${bounds.width()}x${bounds.height()}"
    }

    private fun isDarkNow(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
