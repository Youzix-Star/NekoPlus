/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
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
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
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
 * Every button is independent — its own Material icon or typed label, its own tap and hold
 * actions, its own size, corner radius and opacity, its own place on screen — which is what makes
 * the overlay configurable at all. The original project offered a choice between one big panel and
 * one round ball; this offers any number of either.
 *
 * The buttons are plain framework views on purpose. Compose or Material components inside a
 * `TYPE_APPLICATION_OVERLAY` window need a themed context and a view-tree owner this service does
 * not have, and are a recurring source of inflation crashes.
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager

    /** Live buttons, keyed by [FloatingItem.id]. */
    private val buttons = LinkedHashMap<String, FloatingButton>()

    /** The palette currently painted onto every button. */
    private var palette: FloatingPalette? = null

    private val density: Float get() = resources.displayMetrics.density

    /**
     * The overlay outlives the activity, so it cannot read its settings once at startup: it
     * watches both preferences and rebuilds or repaints itself while it is on screen. The drag
     * options are read on demand instead, because they only matter while a finger is down.
     */
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                FloatingWindowPrefs.isFloatingKey(key) -> syncButtons()
                UiEnginePrefs.isFloatingColorKey(key) -> applyPalette()
            }
        }

    /** One button on screen, plus the views and window plumbing needed to move and repaint it. */
    private class FloatingButton(
        val view: FrameLayout,
        val icon: ImageView,
        val label: TextView,
        val params: WindowManager.LayoutParams,
        val background: GradientDrawable,
    ) {
        var item: FloatingItem? = null

        /** True while this button's action runs, so its face can show progress instead. */
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

        buttons.keys.filterNot { it in wanted }.forEach { id -> removeButton(id) }

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
        val sizePx = sizePx(item)

        val iconView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val labelView = TextView(this).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        }

        val container = FrameLayout(this).apply {
            addView(
                iconView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                labelView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
            // Named apart from the view's own `background` property on purpose: inside `apply`
            // the two would otherwise resolve against each other.
            background = buttonBackground
            elevation = 6f * density
            setOnTouchListener(DragListener(item.id))
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = item.x
            y = item.y
        }

        val button = FloatingButton(
            view = container,
            icon = iconView,
            label = labelView,
            params = params,
            background = buttonBackground,
        )
        button.item = item
        paint(button, item)
        applyPalette(button)

        runCatching { windowManager.addView(container, params) }
            .onSuccess { buttons[item.id] = button }
            .onFailure { stopSelf() }
    }

    private fun updateButton(button: FloatingButton, item: FloatingItem) {
        val size = sizePx(item)
        val resized = button.params.width != size || button.params.height != size
        button.params.width = size
        button.params.height = size
        paint(button, item)
        if (resized || button.params.x != item.x || button.params.y != item.y) {
            runCatching { windowManager.updateViewLayout(button.view, button.params) }
        }
    }

    private fun removeButton(id: String) {
        val button = buttons.remove(id) ?: return
        runCatching { windowManager.removeView(button.view) }
    }

    private fun sizePx(item: FloatingItem): Int = (item.sizeDp * density).roundToInt()

    /** Applies everything about a button that comes from its own settings. */
    private fun paint(button: FloatingButton, item: FloatingItem) {
        val size = sizePx(item)
        button.background.cornerRadius = item.effectiveCornerDp * density

        // A busy button always falls back to the typed face, so progress is readable no matter
        // which face the button normally wears.
        when {
            button.busy -> {
                button.icon.visibility = View.GONE
                button.label.visibility = View.VISIBLE
                button.label.text = "…"
                button.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, item.sizeDp * 0.4f)
            }

            item.showsText -> {
                button.icon.visibility = View.GONE
                button.label.visibility = View.VISIBLE
                val label = item.label
                button.label.text = label
                button.label.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    when (label.length) {
                        1 -> item.sizeDp * 0.44f
                        2 -> item.sizeDp * 0.34f
                        else -> item.sizeDp * 0.25f
                    },
                )
            }

            else -> {
                button.label.visibility = View.GONE
                button.icon.visibility = View.VISIBLE
                button.icon.setImageResource(item.iconEntry.res)
                // Inset rather than resized: the artwork keeps its own aspect and padding stays
                // in step with the button, at any size.
                val inset = (size * ICON_INSET_FRACTION).roundToInt()
                button.icon.setPadding(inset, inset, inset, inset)
            }
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
        button.view.alpha = (button.item?.opacity ?: 100) / 100f
        button.icon.setColorFilter(colors.onContainer, PorterDuff.Mode.SRC_IN)
        button.label.setTextColor(colors.onContainer)
    }

    // ------------------------------------------------------------------ dragging

    /**
     * Drag to move, tap to act, hold to run the hold action.
     *
     * Long-press is detected here rather than with `setOnLongClickListener`, because a touch
     * listener that consumes the gesture stops `View.onTouchEvent` from ever running, and with it
     * the framework's own long-press detection.
     */
    private inner class DragListener(private val id: String) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false

        private val slop = ViewConfiguration.get(this@FloatingWindowService).scaledTouchSlop.toFloat()

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val button = buttons[id] ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = button.params.x
                    startY = button.params.y
                    dragging = false
                    longPressed = false
                    view.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        view.removeCallbacks(longPress)
                        if (FloatingWindowPrefs.loadOptions(this@FloatingWindowService).dragHaptic) {
                            runCatching {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
                    if (dragging) {
                        button.params.x = startX + dx.roundToInt()
                        button.params.y = startY + dy.roundToInt()
                        runCatching { windowManager.updateViewLayout(view, button.params) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPress)
                    if (dragging) {
                        if (FloatingWindowPrefs.loadOptions(this@FloatingWindowService).snapToEdge) {
                            snapToEdge(button)
                        }
                        // Only a finished drag is worth persisting, so a tap never rewrites state.
                        FloatingWindowPrefs.savePosition(
                            this@FloatingWindowService,
                            id,
                            button.params.x,
                            button.params.y,
                        )
                    } else if (event.actionMasked == MotionEvent.ACTION_UP && !longPressed) {
                        button.item?.let { perform(it.actionEntry, button) }
                    }
                    dragging = false
                    return true
                }
            }
            return false
        }

        private val longPress = Runnable {
            if (dragging) return@Runnable
            longPressed = true
            buttons[id]?.let { button ->
                button.item?.holdActionEntry?.let { action -> perform(action, button) }
            }
        }

        /** Pulls the button to whichever side of the screen it is already closest to. */
        private fun snapToEdge(button: FloatingButton) {
            val screenWidth = resources.displayMetrics.widthPixels
            val left = button.params.x
            button.params.x = if (left + button.params.width / 2 < screenWidth / 2) {
                0
            } else {
                screenWidth - button.params.width
            }
            runCatching { windowManager.updateViewLayout(button.view, button.params) }
        }
    }

    // ------------------------------------------------------------------ actions

    private fun perform(action: FloatingAction, button: FloatingButton) {
        when (action) {
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

        /** Share of the button's edge the icon leaves as padding, so the art is ~55% of it. */
        const val ICON_INSET_FRACTION = 0.225f
    }
}
