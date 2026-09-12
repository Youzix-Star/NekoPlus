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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt
import love.miao.yun.MainActivity
import love.miao.yun.MiaoState
import love.miao.yun.R
import love.miao.yun.ai.AiManager
import love.miao.yun.ai.TokenStats
import love.miao.yun.floating.FloatingAction
import love.miao.yun.floating.FloatingItem
import love.miao.yun.floating.FloatingWindowPrefs
import love.miao.yun.ui.FloatingPalette
import love.miao.yun.ui.FloatingPalettes
import love.miao.yun.ui.UiEnginePrefs

/**
 * The floating window: a set of draggable buttons, one per entry in [FloatingWindowPrefs].
 *
 * Every button is independent — its own icon or label, its own action, its own size, its own place
 * on screen — which is what makes the overlay configurable at all: the original project offered the
 * choice between one big panel and one round ball, and this offers any number of either.
 *
 * The buttons are plain framework views on purpose. Material components inside a
 * `TYPE_APPLICATION_OVERLAY` window need a themed context and are a recurring source of inflation
 * crashes, and a rounded square with one glyph on it does not need them.
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager

    /** Live buttons, keyed by [FloatingItem.id], in the order the preferences list them. */
    private val buttons = LinkedHashMap<String, FloatingButton>()

    /** The palette currently painted onto every button. */
    private var palette: FloatingPalette? = null

    private val density: Float get() = resources.displayMetrics.density

    /**
     * The overlay outlives the activity, so it cannot read its settings once at startup: it
     * watches both preferences and rebuilds or repaints itself while it is on screen.
     */
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                FloatingWindowPrefs.isFloatingKey(key) -> syncButtons()
                UiEnginePrefs.isFloatingColorKey(key) -> applyPalette()
            }
        }

    /** One button on screen, plus the window plumbing needed to move and repaint it. */
    private class FloatingButton(
        val view: TextView,
        val params: WindowManager.LayoutParams,
        val background: GradientDrawable,
    ) {
        var item: FloatingItem? = null

        /** True while this button's action is running, so its label can show progress instead. */
        var busy: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        FloatingWindowPrefs.register(this, prefsListener)
        UiEnginePrefs.registerListener(this, prefsListener)
        syncButtons()
        MiaoState.floatingRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        FloatingWindowPrefs.unregister(this, prefsListener)
        UiEnginePrefs.unregisterListener(this, prefsListener)
        buttons.values.forEach { button ->
            runCatching { windowManager.removeView(button.view) }
        }
        buttons.clear()
        MiaoState.floatingRunning = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ buttons

    /** Brings the on-screen buttons in line with the preferences: add, drop, update, repaint. */
    private fun syncButtons() {
        val items = FloatingWindowPrefs.load(this)
        val wanted = items.map { it.id }.toSet()

        buttons.keys.filterNot { it in wanted }.forEach { id ->
            removeButton(id)
        }

        items.forEach { item ->
            val existing = buttons[item.id]
            if (existing == null) {
                addButton(item)
            } else {
                existing.item = item
                updateButton(existing, item)
            }
        }

        applyPalette()
    }

    private fun addButton(item: FloatingItem) {
        val buttonBackground = GradientDrawable()

        val view = TextView(this).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(0xFF000000.toInt())
            // Named apart from the view's own `background` property on purpose: inside `apply`
            // the two would otherwise resolve against each other.
            background = buttonBackground
            elevation = 6f * density
            setOnTouchListener(DragListener(item.id))
        }

        // Painted before the window is added, so a button never flashes the default background.
        val params = WindowManager.LayoutParams(
            buttonSizePx(item),
            buttonSizePx(item),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = item.x
            y = item.y
        }

        val button = FloatingButton(view = view, params = params, background = buttonBackground)
        button.item = item
        paint(button, item)
        applyPalette(button)

        runCatching { windowManager.addView(view, params) }
            .onSuccess { buttons[item.id] = button }
            .onFailure { stopSelf() }
    }

    private fun updateButton(button: FloatingButton, item: FloatingItem) {
        val sizePx = buttonSizePx(item)
        val resized = button.params.width != sizePx || button.params.height != sizePx
        button.params.width = sizePx
        button.params.height = sizePx
        paint(button, item)
        if (resized || button.params.x != item.x || button.params.y != item.y) {
            runCatching { windowManager.updateViewLayout(button.view, button.params) }
        }
    }

    private fun removeButton(id: String) {
        val button = buttons.remove(id) ?: return
        runCatching { windowManager.removeView(button.view) }
    }

    private fun buttonSizePx(item: FloatingItem): Int = (item.sizeDp * density).roundToInt()

    /** Applies everything about a button that comes from its own settings. */
    private fun paint(button: FloatingButton, item: FloatingItem) {
        val sizeDp = item.sizeDp
        val label = if (button.busy) "…" else item.label
        button.view.text = label
        button.view.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            when {
                button.busy -> sizeDp * 0.40f
                label.length <= 1 -> sizeDp * 0.44f
                label.length == 2 -> sizeDp * 0.34f
                else -> sizeDp * 0.25f
            },
        )
        button.background.cornerRadius = if (item.round) {
            sizeDp * density / 2f
        } else {
            sizeDp * density / 3.6f
        }
    }

    // ------------------------------------------------------------------ palette

    /**
     * Repaint every button from the configured colour source.
     *
     * The colours come from [FloatingPalettes], which resolves the miuix, Material 3 or Monet
     * scheme for this device's night mode without needing a composition.
     */
    private fun applyPalette() {
        palette = FloatingPalettes.resolve(this, UiEnginePrefs.loadFloatingColor(this))
        buttons.values.forEach { applyPalette(it) }
    }

    private fun applyPalette(button: FloatingButton) {
        val colors = palette ?: return
        button.background.setColor(colors.container)
        button.view.setTextColor(colors.onContainer)
    }

    // ------------------------------------------------------------------ dragging

    /**
     * Drag to move, tap to act.
     *
     * The window is repositioned with the raw touch delta rather than a gesture detector, because
     * the overlay never gets a gesture arena of its own — it is the only thing in its window.
     */
    private inner class DragListener(private val id: String) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val button = buttons[id] ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = button.params.x
                    startY = button.params.y
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > draggableSlop() || abs(dy) > draggableSlop())) {
                        dragging = true
                    }
                    if (dragging) {
                        button.params.x = startX + dx.roundToInt()
                        button.params.y = startY + dy.roundToInt()
                        runCatching { windowManager.updateViewLayout(view, button.params) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        // Only a finished drag is worth persisting, so a tap never rewrites state.
                        FloatingWindowPrefs.savePosition(
                            this@FloatingWindowService,
                            id,
                            button.params.x,
                            button.params.y,
                        )
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        button.item?.let { perform(it, button) }
                    }
                    dragging = false
                    return true
                }
            }
            return false
        }

        private fun draggableSlop(): Float = 8f * density
    }

    // ------------------------------------------------------------------ actions

    private fun perform(item: FloatingItem, button: FloatingButton) {
        when (item.actionEntry) {
            FloatingAction.AiModify -> runAiModify(button)
            FloatingAction.Capture -> captureToClipboard()
            FloatingAction.OpenApp -> openApp()
            FloatingAction.Close -> stopSelf()
        }
    }

    /** Capture the focused field, let the model rewrite it, and write the result back. */
    private fun runAiModify(button: FloatingButton) {
        val service = MiaoAccessibilityService.instance()
        if (service == null) {
            toast(getString(R.string.ai_need_accessibility))
            return
        }

        val original = service.getCurrentWindowText()
        if (original.isEmpty()) {
            toast(getString(R.string.ai_no_input))
            return
        }

        val config = AiManager.load(this)
        if (config.apiKey.isNullOrBlank()) {
            toast(getString(R.string.ai_need_api_key))
            return
        }

        setBusy(button, true)
        AiManager.modifyText(
            config,
            original,
            object : AiManager.Callback {
                override fun onSuccess(modifiedText: String) {
                    AiManager.consumeLastUsage()?.let { usage ->
                        TokenStats.record(
                            this@FloatingWindowService,
                            usage.model ?: config.model,
                            usage.promptTokens,
                            usage.completionTokens,
                            usage.totalTokens,
                            usage.cachedTokens,
                        )
                    }
                    val written = service.replaceInputText(modifiedText)
                    setBusy(button, false)
                    toast(
                        getString(
                            if (written) R.string.ai_replaced else R.string.ai_replace_failed,
                        ),
                    )
                }

                override fun onError(message: String) {
                    setBusy(button, false)
                    toast(getString(R.string.ai_failed) + "：" + message)
                }
            },
        )
    }

    /** Copy whatever the focused input box holds onto the clipboard. */
    private fun captureToClipboard() {
        val service = MiaoAccessibilityService.instance()
        if (service == null) {
            toast(getString(R.string.ai_need_accessibility))
            return
        }
        val text = service.getCurrentWindowText()
        if (text.isEmpty()) {
            toast(getString(R.string.ai_no_input))
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.notif_title), text))
        toast(getString(R.string.floating_copied, text.length))
    }

    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun setBusy(button: FloatingButton, busy: Boolean) {
        button.busy = busy
        button.item?.let { paint(button, it) }
    }

    /** Results are announced as toasts: a bare button has no panel to write a status line into. */
    private fun toast(message: String) {
        runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
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
