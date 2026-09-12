package love.miao.yun.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "FloatingBtn"
        private const val PREFS_NAME = "miao_config"
        private const val KEY_ENABLED = "floating_button_enabled"

        var isRunning = false
            private set

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }

        fun start(context: Context) {
            if (isRunning) return
            setEnabled(context, true)
            val intent = Intent(context, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            setEnabled(context, false)
            context.stopService(Intent(context, FloatingButtonService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: android.view.View? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isSemiTransparent = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "FloatingButtonService created")

        // 必须最先调用 startForeground，避免 ForegroundServiceDidNotStartInTimeException
        startForegroundNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
    }

    private fun startForegroundNotification() {
        val notification = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "miao_floating_btn_channel",
                    "悬浮按钮服务",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.createNotificationChannel(channel)

                android.app.Notification.Builder(this, "miao_floating_btn_channel")
                    .setContentTitle("喵喵助手")
                    .setContentText("悬浮按钮运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setContentTitle("喵喵助手")
                    .setContentText("悬浮按钮运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification creation failed, using fallback", e)
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("喵喵助手")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
        try {
            startForeground(3, notification)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingButton() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 12, 24, 12)
        }

        val emojiText = TextView(this).apply {
            text = "(>^ω^<)"
            setTextColor(0xFF212121.toInt())
            textSize = 14f
        }

        container.addView(emojiText)

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
            gravity = Gravity.TOP or Gravity.END
            x = (20 * resources.displayMetrics.density).toInt()
            y = (200 * resources.displayMetrics.density).toInt()
        }

        windowManager?.addView(floatingView, params)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var longPressTriggered = false
        val longPressRunnable = Runnable {
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
                    params.x = initialX - dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!isDragging && !longPressTriggered) {
                        Log.d(TAG, "Floating button clicked")
                        MiaoAccessibilityService.instance?.processNow()
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            windowManager?.removeView(floatingView)
        } catch (_: Exception) {}
        Log.d(TAG, "FloatingButtonService destroyed")
    }
}
