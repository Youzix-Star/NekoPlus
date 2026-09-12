package love.miao.yun.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import love.miao.yun.util.DebugLog

class LogFloatingService : Service() {

    companion object {
        private const val TAG = "LogFloat"
        var isRunning = false
            private set

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, LogFloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LogFloatingService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var logContainer: LinearLayout? = null
    private var scrollView: ScrollView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val logViews = mutableListOf<TextView>()
    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "LogFloatingService created")

        // 必须最先调用 startForeground，避免 ForegroundServiceDidNotStartInTimeException
        startForegroundNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
        loadExistingLogs()

        DebugLog.onLogAdded = { entry ->
            handler.post { addLogEntry(entry) }
        }
    }

    private fun startForegroundNotification() {
        val notification = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "miao_log_channel",
                    "日志悬浮窗",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.createNotificationChannel(channel)

                android.app.Notification.Builder(this, "miao_log_channel")
                    .setContentTitle("喵喵助手 - 日志")
                    .setContentText("日志悬浮窗运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setContentTitle("喵喵助手 - 日志")
                    .setContentText("日志悬浮窗运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification creation failed, using fallback", e)
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("喵喵助手 - 日志")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
        try {
            startForeground(4, notification)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        val density = resources.displayMetrics.density

        // 主容器
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF01A1A2E.toInt())
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
        }

        // 标题栏
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "MiaoLog"
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 12f
        }

        val copyAllBtn = TextView(this).apply {
            text = "复制全部"
            setTextColor(0xFF64B5F6.toInt())
            textSize = 12f
            setPadding((12 * density).toInt(), 0, 0, 0)
            setOnClickListener {
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("miao_log", DebugLog.getAllFormatted()))
                    Toast.makeText(this@LogFloatingService, "已复制全部日志", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        }

        val clearBtn = TextView(this).apply {
            text = "清除"
            setTextColor(0xFF64B5F6.toInt())
            textSize = 12f
            setPadding((12 * density).toInt(), 0, 0, 0)
            setOnClickListener {
                DebugLog.clear()
                logContainer?.removeAllViews()
                logViews.clear()
                Toast.makeText(this@LogFloatingService, "日志已清除", Toast.LENGTH_SHORT).show()
            }
        }

        val collapseBtn = TextView(this).apply {
            text = "收起"
            setTextColor(0xFF64B5F6.toInt())
            textSize = 12f
            setPadding((12 * density).toInt(), 0, 0, 0)
            setOnClickListener {
                isExpanded = !isExpanded
                scrollView?.visibility = if (isExpanded) View.VISIBLE else View.GONE
                text = if (isExpanded) "收起" else "展开"
            }
        }

        val closeBtn = TextView(this).apply {
            text = "关闭"
            setTextColor(0xFFEF5350.toInt())
            textSize = 12f
            setPadding((12 * density).toInt(), 0, 0, 0)
            setOnClickListener {
                DebugLog.setEnabled(this@LogFloatingService, false)
                stop(this@LogFloatingService)
                Toast.makeText(this@LogFloatingService, "日志已关闭", Toast.LENGTH_SHORT).show()
            }
        }

        titleBar.addView(titleText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleBar.addView(copyAllBtn)
        titleBar.addView(clearBtn)
        titleBar.addView(collapseBtn)
        titleBar.addView(closeBtn)

        // 日志滚动区域
        logContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView = ScrollView(this).apply {
            addView(logContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (300 * density).toInt()
            )
            visibility = View.GONE // 默认收起
        }

        mainLayout.addView(titleBar)
        mainLayout.addView(scrollView)

        val bg = GradientDrawable().apply {
            setColor(0xF01A1A2E.toInt())
            cornerRadius = 12f * density
            setStroke((1 * density).toInt(), 0xFF333355.toInt())
        }
        mainLayout.background = bg

        floatingView = mainLayout

        val params = WindowManager.LayoutParams(
            (320 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (100 * density).toInt()
        }

        windowManager?.addView(floatingView, params)

        // 拖动
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        titleBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 25) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        isExpanded = !isExpanded
                        scrollView?.visibility = if (isExpanded) View.VISIBLE else View.GONE
                        collapseBtn.text = if (isExpanded) "收起" else "展开"
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun loadExistingLogs() {
        val logs = DebugLog.getRecent(100)
        for (entry in logs) {
            addLogEntry(entry, scroll = false)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addLogEntry(entry: DebugLog.LogEntry, scroll: Boolean = true) {
        val density = resources.displayMetrics.density

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
        }

        val levelColor = when (entry.level) {
            "E" -> 0xFFEF5350.toInt()
            "W" -> 0xFFFFA726.toInt()
            "I" -> 0xFF66BB6A.toInt()
            else -> 0xFF90CAF9.toInt()
        }

        val levelTag = TextView(this).apply {
            text = entry.level
            setTextColor(levelColor)
            textSize = 10f
            minWidth = (16 * density).toInt()
        }

        val timeText = TextView(this).apply {
            text = entry.timeText()
            setTextColor(0xFF888899.toInt())
            textSize = 10f
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
        }

        val msgText = TextView(this).apply {
            text = entry.displayText()
            setTextColor(0xFFCCCCDD.toInt())
            textSize = 11f
            maxLines = 3
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        row.addView(levelTag)
        row.addView(timeText)
        row.addView(msgText)

        // 点击无操作（复制全部在标题栏）

        logContainer?.addView(row)
        logViews.add(msgText)

        // 限制显示条数
        while (logViews.size > 200) {
            logContainer?.removeViewAt(0)
            logViews.removeAt(0)
        }

        if (scroll) {
            handler.post {
                scrollView?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        DebugLog.onLogAdded = null
        try {
            windowManager?.removeView(floatingView)
        } catch (_: Exception) {}
        Log.d(TAG, "LogFloatingService destroyed")
    }
}
