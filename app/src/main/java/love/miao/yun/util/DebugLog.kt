package love.miao.yun.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 内置调试日志收集器
 * 激活方式：更多设置 → 自定义颜文字 输入 5201110 → 返回主页
 */
object DebugLog {
    private const val PREFS_NAME = "miao_config"
    private const val KEY_DEBUG_MODE = "debug_log_mode"

    data class LogEntry(
        val time: Long = System.currentTimeMillis(),
        val tag: String,
        val level: String, // D, W, E, I
        val message: String
    ) {
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        fun formatted(): String = "[${timeFormat.format(Date(time))}] $level/$tag: $message"

        fun displayText(): String = "$level/$tag: $message"

        fun timeText(): String = timeFormat.format(Date(time))
    }

    private val logs = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_LOGS = 500

    var onLogAdded: ((LogEntry) -> Unit)? = null

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEBUG_MODE, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }

    fun d(tag: String, message: String) {
        add("D", tag, message)
    }

    fun w(tag: String, message: String) {
        add("W", tag, message)
    }

    fun e(tag: String, message: String) {
        add("E", tag, message)
    }

    fun i(tag: String, message: String) {
        add("I", tag, message)
    }

    private fun add(level: String, tag: String, message: String) {
        val entry = LogEntry(tag = tag, level = level, message = message)
        logs.add(entry)
        // 超过上限时移除最旧的
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        onLogAdded?.invoke(entry)
    }

    fun getAll(): List<LogEntry> = logs.toList()

    fun getRecent(count: Int): List<LogEntry> {
        val all = logs.toList()
        return if (all.size <= count) all else all.subList(all.size - count, all.size)
    }

    fun clear() {
        logs.clear()
    }

    fun getAllFormatted(): String {
        return logs.joinToString("\n") { it.formatted() }
    }
}
