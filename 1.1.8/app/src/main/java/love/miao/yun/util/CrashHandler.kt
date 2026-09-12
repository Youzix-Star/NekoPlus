package love.miao.yun.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val CRASH_DIR = "crash_logs"

        fun getCrashDir(context: Context): File {
            return File(context.getExternalFilesDir(null), CRASH_DIR)
        }

        fun getLatestCrashFile(context: Context): File? {
            val dir = getCrashDir(context)
            if (!dir.exists()) return null
            val files = dir.listFiles { f -> f.name.endsWith(".txt") }
            return files?.maxByOrNull { it.lastModified() }
        }

        fun getCrashLog(context: Context): String? {
            val file = getLatestCrashFile(context) ?: return null
            return try {
                file.readText()
            } catch (_: Exception) {
                null
            }
        }

        fun clearCrashLogs(context: Context) {
            try {
                getCrashDir(context).listFiles()?.forEach { it.delete() }
            } catch (_: Exception) {}
        }
    }

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    fun init() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val crashLog = buildCrashLog(t, e)
            val dir = getCrashDir(context)
            if (!dir.exists()) dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "crash_$timestamp.txt")
            file.writeText(crashLog)
        } catch (_: Exception) {}

        defaultHandler?.uncaughtException(t, e)
    }

    private fun buildCrashLog(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("=== 喵喵助手 崩溃日志 ===")
        pw.println()
        pw.println("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        pw.println("线程: ${thread.name}")
        pw.println("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
        pw.println("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        pw.println("版本: ${getAppVersion()}")
        pw.println()
        pw.println("--- 异常堆栈 ---")
        throwable.printStackTrace(pw)

        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            pw.println()
            pw.println("--- Caused by ---")
            cause.printStackTrace(pw)
            cause = cause.cause
            depth++
        }

        pw.flush()
        return sw.toString()
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
