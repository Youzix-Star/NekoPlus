/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt
import love.miao.yun.MainActivity
import love.miao.yun.MiaoState
import love.miao.yun.R

/**
 * A deliberately minimal floating window: it drags, it can be closed, and it announces itself
 * through [MiaoState] so the home page's status card can react. It captures nothing and processes
 * nothing — this build only exercises the UI.
 *
 * The overlay is built from plain framework views on purpose. Material components inside a
 * `TYPE_APPLICATION_OVERLAY` window need a themed context and are a recurring source of
 * inflation crashes, and a rounded panel with two labels does not need them.
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showWindow()
        MiaoState.floatingRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        rootView?.let { view -> runCatching { windowManager.removeView(view) } }
        rootView = null
        MiaoState.floatingRunning = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ overlay

    private fun showWindow() {
        val context = this
        val density = resources.displayMetrics.density

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (14 * density).roundToInt(),
                (10 * density).roundToInt(),
                (10 * density).roundToInt(),
                (10 * density).roundToInt(),
            )
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(Color.parseColor("#E64C8DFF"))
            }
        }

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        labels.addView(
            TextView(context).apply {
                text = getString(R.string.notif_title)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            },
        )
        labels.addView(
            TextView(context).apply {
                text = getString(R.string.app_subtitle)
                setTextColor(Color.parseColor("#CCFFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            },
        )
        panel.addView(labels)

        val close = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding((14 * density).roundToInt(), 0, (6 * density).roundToInt(), 0)
            setOnClickListener { stopSelf() }
        }
        panel.addView(close)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * density).roundToInt()
            y = (240 * density).roundToInt()
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        panel.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).roundToInt()
                    params.y = startY + (event.rawY - downY).roundToInt()
                    runCatching { windowManager.updateViewLayout(panel, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - downX) < 6f && abs(event.rawY - downY) < 6f) {
                        panel.performClick()
                    }
                    true
                }

                else -> false
            }
        }

        runCatching { windowManager.addView(panel, params) }
            .onSuccess {
                rootView = panel
                layoutParams = params
            }
            .onFailure { stopSelf() }
    }

    // ------------------------------------------------------------------ notification

    private fun startForegroundNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.notif_channel_desc)
                },
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "miao_floating_window"
        const val NOTIFICATION_ID = 1001
    }
}
