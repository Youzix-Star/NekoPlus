/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import love.miao.yun.floating.FloatingActionRunner
import love.miao.yun.floating.FloatingButtonViews
import love.miao.yun.floating.applyPalette
import love.miao.yun.floating.applyStyle
import love.miao.yun.floating.floatingButtonViews
import love.miao.yun.sendassist.ASSIST_ACTIONS
import love.miao.yun.sendassist.SendAssistConfig
import love.miao.yun.sendassist.SendAssistGeometry
import love.miao.yun.sendassist.SendAssistPrefs
import love.miao.yun.sendassist.findSendButton
import love.miao.yun.sendassist.pressSendButton
import love.miao.yun.sendassist.sendTargetFor
import love.miao.yun.ui.FloatingColorSource
import love.miao.yun.ui.FloatingPalettes
import love.miao.yun.ui.UiEnginePrefs
import kotlin.math.roundToInt

/**
 * A floating button anchored above a chat app's send button.
 *
 * The same button the floating window draws — same model, same renderer, same chains — with one
 * difference: where it goes is decided by the chat app rather than by a drag. Tapping it runs its
 * chain (AI 修改 by default, optionally followed by 按发送), which is what makes a rewrite reach the
 * conversation without the user having to find the send button again after the text changed under
 * their fingers.
 *
 * It is an **accessibility overlay** (`TYPE_ACCESSIBILITY_OVERLAY`), which is why it needs neither
 * the overlay permission nor a foreground service of its own: it belongs to the accessibility
 * service that already has to be running, and goes away with it.
 *
 * ## Why it looks at the screen on a timer
 *
 * It used to place itself only when an accessibility event arrived, and events are not a schedule:
 * opening a chat from a notification, or coming back to an app that was already in front, can
 * produce a burst that arrives before the chat box exists and then nothing at all — so the button
 * did not appear until the user left and came back. A timer cannot miss, and one look every
 * [TICK_MS] costs one view-id lookup. Nothing is read from the input box here; only the send
 * button's position is, and only to put the button next to it.
 */
class SendAssistOverlay(private val service: MiaoAccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val density = service.resources.displayMetrics.density

    private val windowManager: WindowManager? =
        service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var views: FloatingButtonViews? = null
    private var params: WindowManager.LayoutParams? = null

    /** The config the button on screen was built from, so a change of app is noticed. */
    private var shown: SendAssistConfig? = null

    /** Where it was last put, in screen pixels; equal means there is nothing to move. */
    private var placed: Rect? = null

    /** Consecutive looks that found no send button, so one bad frame does not blink the button. */
    private var misses = 0

    /** The colour source the button was last painted from, so a change of theme repaints it. */
    private var paintedSource: FloatingColorSource? = null

    /** When the expensive search — every node, looking for a button labelled 发送 — last ran. */
    private var lastLabelWalkAt = 0L

    private var running = false

    private val tick = Runnable { look() }

    /** Starts watching, and looks straight away rather than after the first interval. */
    fun start() {
        if (running) return
        running = true
        handler.post(tick)
    }

    /** Called when the assistant is switched off for every app. */
    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        views?.let { windowManager?.removeView(it.container) }
        views = null
        params = null
        shown = null
        placed = null
        misses = 0
    }

    fun dispose() = stop()

    // ------------------------------------------------------------------ the look

    private fun look() {
        if (!running) return
        handler.postDelayed(tick, TICK_MS)

        val window = findChatWindow()
        if (window == null) return disappear()

        val config = SendAssistPrefs.load(service, window.first)
        if (!config.enabled) return disappear()

        val send = findSendButton(
            root = window.second,
            packageName = window.first,
            allowLabelWalk = labelWalkAllowed(),
        )
        if (send == null) return miss()

        val bounds = Rect()
        send.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return miss()

        misses = 0
        ensureViews(config)
        place(bounds, config)
    }

    /**
     * The window of a supported chat app, active one first.
     *
     * `rootInActiveWindow` is not always the chat app: with the keyboard up the focused window can
     * be the input method's, and our own overlay adds another. Anything other than "the chat app is
     * nowhere on screen" is not a reason to give up.
     */
    private fun findChatWindow(): Pair<String, AccessibilityNodeInfo>? {
        service.rootInActiveWindow?.let { root ->
            val packageName = root.packageName?.toString()
            if (sendTargetFor(packageName) != null) return packageName!! to root
        }

        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        windows.sortedByDescending { it.isActive }.forEach { window ->
            val root = runCatching { window.root }.getOrNull() ?: return@forEach
            val packageName = root.packageName?.toString() ?: return@forEach
            if (sendTargetFor(packageName) != null) return packageName to root
        }
        return null
    }

    /**
     * Whether this round may walk the whole node tree looking for a button labelled 发送.
     *
     * The view-id lookup above is cheap enough to run four times a second; visiting every node is
     * not, and it only matters when an app has renamed its ids. Throttled to once a second.
     */
    private fun labelWalkAllowed(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastLabelWalkAt < LABEL_WALK_INTERVAL_MS) return false
        lastLabelWalkAt = now
        return true
    }

    /**
     * The send button is gone, so the button goes with it — but only after it has been gone twice
     * in a row. A single miss happens mid-layout, mid-animation, and while the keyboard is coming
     * up, and reacting to it is what made the button flicker.
     */
    private fun miss() {
        if (++misses >= MISSES_BEFORE_HIDE) disappear()
    }

    /** Takes the button off screen without destroying its window: coming back is then instant. */
    private fun disappear() {
        views?.container?.visibility = View.GONE
        placed = null
    }

    // ------------------------------------------------------------------ placement

    private fun ensureViews(config: SendAssistConfig) {
        val item = config.item
        val existing = views
        if (existing == null) {
            val created = floatingButtonViews(service, density)
            created.container.contentDescription = "喵喵助手"
            created.container.setOnClickListener { runChain(config, hold = false) }
            created.container.setOnLongClickListener {
                // A long press on the floating window's buttons runs a second chain; there is no
                // reason for the assistant's to be the one button that cannot. An empty chain is
                // not consumed, so a long press still counts as a tap.
                val hold = SendAssistPrefs.load(service, config.packageName).item.holdActionEntries
                if (hold.none { it in ASSIST_ACTIONS }) return@setOnLongClickListener false
                runChain(config, hold = true)
                true
            }

            val layout = WindowManager.LayoutParams(
                (item.widthDp * density).roundToInt(),
                (item.heightDp * density).roundToInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    // Placement comes from `getBoundsInScreen`, which counts from the top of the
                    // screen. A window without this is laid out below the status bar instead, and
                    // the button lands a status bar lower than the arithmetic says.
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            views = created
            params = layout
            runCatching { windowManager?.addView(created.container, layout) }
                .onFailure { views = null; params = null }
        }

        val current = views ?: return
        val source = UiEnginePrefs.loadFloatingColor(service)
        // Repainted only when one of the inputs actually changed: this runs four times a second,
        // and re-setting the icon four times a second means building a drawable four times a
        // second for a picture that has not moved.
        if (shown == config && paintedSource == source) return

        shown = config
        paintedSource = source
        placed = null
        current.applyStyle(config.item, density)
        current.applyPalette(FloatingPalettes.resolve(service, source), config.item.opacity)
    }

    private fun place(bounds: Rect, config: SendAssistConfig) {
        val item = config.item
        val target = SendAssistGeometry.assistantRect(
            send = bounds,
            width = (item.widthDp * density).roundToInt(),
            height = (item.heightDp * density).roundToInt(),
            offsetX = (config.offsetXDp * density).roundToInt(),
            offsetY = (config.offsetYDp * density).roundToInt(),
            topLimit = 0,
        )

        val current = views ?: return
        val layout = params ?: return
        if (placed == target && current.container.visibility == View.VISIBLE) return
        placed = target
        layout.width = target.width()
        layout.height = target.height()
        layout.x = target.left
        layout.y = target.top
        val updated = if (current.container.isAttachedToWindow) {
            runCatching { windowManager?.updateViewLayout(current.container, layout) }
        } else {
            runCatching { windowManager?.addView(current.container, layout) }
        }
        if (updated.isSuccess) current.container.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ acting

    /**
     * Runs one of the button's chains.
     *
     * The config is read again at tap time rather than taken from the closure, because the closure
     * was built when the button was created: a chain edited since then has to be the one that runs.
     */
    private fun runChain(config: SendAssistConfig, hold: Boolean) {
        val current = SendAssistPrefs.load(service, config.packageName)
        val steps = if (hold) {
            current.item.holdActionEntries.filter { it in ASSIST_ACTIONS }
        } else {
            current.actionEntries
        }

        FloatingActionRunner(
            context = service,
            host = object : FloatingActionRunner.Host {
                override fun onBusy(busy: Boolean) {
                    views?.let { button ->
                        button.busy = busy
                        button.applyStyle(current.item, density)
                    }
                }

                override fun onSend() {
                    if (!pressSendButton(service)) toast("没找到发送按钮")
                }

                /** Nothing to close: the assistant's button is anchored to the screen it is on. */
                override fun onClose() = Unit
            },
        ).run(steps)
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(service, message, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        /** Four looks a second: fast enough to feel instant, cheap enough to leave running. */
        const val TICK_MS = 250L

        /** How many consecutive looks may fail to find the send button before the button goes. */
        const val MISSES_BEFORE_HIDE = 2

        /** The full-tree search for a 发送 button runs at most this often. */
        const val LABEL_WALK_INTERVAL_MS = 1000L
    }
}
