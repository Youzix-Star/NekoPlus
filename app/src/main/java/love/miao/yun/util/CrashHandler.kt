/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import love.miao.yun.BuildConfig

/**
 * Records the last crash to a file the user can actually get at, together with where the app was.
 *
 * Without this a crash is a disappearing window: the app is gone, the report is in a ring buffer
 * nobody can read, and all that is left is "it closed on me". The breadcrumbs matter as much as
 * the stack — they say which screen was being built when it happened.
 */
object CrashHandler {
    private const val DIR = "crash"
    private const val FILE_NAME = "latest.txt"
    private const val MAX_CRUMBS = 40

    private val crumbs = ArrayDeque<String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Records where the app is, so the next report says what the user was doing. */
    fun note(where: String) {
        synchronized(crumbs) {
            crumbs.addLast("${timeFormat.format(Date())} $where")
            while (crumbs.size > MAX_CRUMBS) crumbs.removeFirst()
        }
    }

    /** Installs the handler. Call once, from [love.miao.yun.MiaoApp]. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // Still hand over: the system's own crash dialog and its own logging stay intact.
            previous?.uncaughtException(thread, error)
        }
    }

    /** Where the report lives; shown to the user so they can find it without the app. */
    fun file(context: Context): File {
        val dir = (context.getExternalFilesDir(null) ?: context.filesDir).let { File(it, DIR) }
        return File(dir, FILE_NAME)
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val target = file(context)
        target.parentFile?.mkdirs()

        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))

        val report = buildString {
            appendLine("喵喵助手 崩溃报告")
            appendLine("时间: ${stampFormat.format(Date())}")
            appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(
                "设备: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
                    "(SDK ${Build.VERSION.SDK_INT})",
            )
            appendLine("线程: ${thread.name}")
            appendLine()
            appendLine("最近的界面：")
            synchronized(crumbs) { crumbs.forEach { appendLine("  $it") } }
            appendLine()
            appendLine("异常：")
            append(trace.toString())
        }

        target.writeText(report)
    }
}
