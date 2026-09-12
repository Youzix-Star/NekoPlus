package love.miao.yun.service

import android.annotation.SuppressLint
import android.app.Service
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
import android.widget.LinearLayout
import android.widget.TextView
import love.miao.yun.util.MiaoConfig

class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatingSvc"
        private const val PREFS_NAME = "miao_config"
        private const val KEY_FLOATING = "floating_enabled"
        private const val STATUS_CHECK_INTERVAL = 1000L

        var isRunning = false
            private set

        // 状态变化回调，供无障碍服务通知
        var onStatusChanged: (() -> Unit)? = null

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FLOATING, false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_FLOATING, enabled).apply()
        }

        fun start(context: Context) {
            setEnabled(context, true)
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            setEnabled(context, false)
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var statusText: TextView? = null
    private var dotView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastKnownState = false
    private var isSemiTransparent = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "FloatingWindowService created")

        // 必须最先调用 startForeground，避免 ForegroundServiceDidNotStartInTimeException
        startForegroundNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
        startStatusPolling()

        // 注册状态变化回调
        onStatusChanged = { handler.post { updateStatus() } }
    }

    private fun startForegroundNotification() {
        // 必须保证 startForeground 一定被调用，否则会抛 ForegroundServiceDidNotStartInTimeException
        val notification = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "miao_floating_channel",
                    "悬浮窗服务",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.createNotificationChannel(channel)

                android.app.Notification.Builder(this, "miao_floating_channel")
                    .setContentTitle("喵喵助手")
                    .setContentText("悬浮窗运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setContentTitle("喵喵助手")
                    .setContentText("悬浮窗运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification creation failed, using fallback", e)
            // 降级：用最基本的通知
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("喵喵助手")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
        try {
            startForeground(2, notification)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    /**
     * 定期轮询无障碍服务状态，确保悬浮窗显示一致
     */
    private fun startStatusPolling() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateStatus()
                handler.postDelayed(this, STATUS_CHECK_INTERVAL)
            }
        }, STATUS_CHECK_INTERVAL)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 12, 24, 12)
        }

        dotView = View(this).apply {
            val dotDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE53935.toInt()) // 默认红色（关闭）
            }
            background = dotDrawable
            layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                marginEnd = 12
            }
        }

        statusText = TextView(this).apply {
            text = "喵喵：关闭"
            setTextColor(0xFFE53935.toInt())
            textSize = 12f
        }

        container.addView(dotView)
        container.addView(statusText)

        val bg = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = 24f
            setStroke(2, 0xFFE0E0E0.toInt())
        }
        container.background = bg
        container.elevation = 8f

        floatingView = container

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager?.addView(floatingView, params)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            // 长按触发：切换半透明
            longPressTriggered = true
            isSemiTransparent = !isSemiTransparent
            floatingView?.alpha = if (isSemiTransparent) 0.3f else 1.0f
        }

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    longPressTriggered = false
                    handler.postDelayed(longPressRunnable, 500L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 25) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!isDragging && !longPressTriggered) {
                        toggleService()
                    }
                    true
                }
                else -> false
            }
        }

        updateStatus()
    }

    private fun toggleService() {
        val currentlyEnabled = MiaoAccessibilityService.isEnabled(this)
        MiaoAccessibilityService.setEnabled(this, !currentlyEnabled)
        updateStatus()
    }

    fun updateStatus() {
        val enabled = MiaoAccessibilityService.isEnabled(this)
        val running = MiaoAccessibilityService.isRunning
        val active = enabled && running

        // 只在状态变化时更新 UI
        if (active == lastKnownState) return
        lastKnownState = active

        statusText?.text = if (active) "喵喵：开启" else "喵喵：关闭"
        statusText?.setTextColor(if (active) 0xFF212121.toInt() else 0xFFE53935.toInt())

        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        }
        dotView?.background = dotDrawable

        Log.d(TAG, "Status updated: active=$active, enabled=$enabled, running=$running")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateStatus()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        onStatusChanged = null
        try {
            windowManager?.removeView(floatingView)
        } catch (_: Exception) {}
        Log.d(TAG, "FloatingWindowService destroyed")
    }
}
