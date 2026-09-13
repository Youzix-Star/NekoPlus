/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import love.miao.yun.R
import love.miao.yun.sendassist.SEND_LABEL
import love.miao.yun.sendassist.SendAssistAction
import love.miao.yun.sendassist.SendAssistConfig
import love.miao.yun.sendassist.SendAssistGeometry
import love.miao.yun.sendassist.SendAssistMetrics
import love.miao.yun.sendassist.SendAssistPrefs
import love.miao.yun.sendassist.sendTargetFor
import love.miao.yun.ui.FloatingPalettes
import love.miao.yun.ui.UiEnginePrefs
import love.miao.yun.util.AiRewrite
import love.miao.yun.util.DebugDump
import kotlin.math.abs

/**
 * A button that sits just above a chat app's send button.
 *
 * Tapping it runs the configured action — AI 修改 by default — and, if asked to, presses send
 * afterwards. The point is to make the rewrite reach the conversation without the user having to
 * find the send button again after the text changes under their fingers.
 *
 * It is an **accessibility overlay** (`TYPE_ACCESSIBILITY_OVERLAY`), which is why it needs neither
 * the overlay permission nor a service of its own: it belongs to the accessibility service that
 * already has to be running, and it goes away with it.
 */
class SendAssistOverlay(private val service: MiaoAccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val density = service.resources.displayMetrics.density

    private val windowManager: WindowManager? =
        service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var view: FrameLayout? = null
    private var icon: ImageView? = null
    private var spinner: ProgressBar? = null
    private var background: GradientDrawable? = null
    private var params: WindowManager.LayoutParams? = null

    private var busy = false
    private var placed: Rect? = null
    private var misses = 0

    /**
     * Read-backs of where the button really lands, relative to the send button, per app.
     *
     * `sendBounds` comes from `getBoundsInScreen` — screen coordinates — while the window is placed
     * in window coordinates, and on a phone where those two do not start in the same place the
     * button ends up somewhere other than the arithmetic says. It is **not** corrected here: the
     * placement is what the user has been using and adjusting to, and moving it under them would be
     * a change nobody asked for. It is measured instead, and the preview draws the measured result,
     * so the settings page and the phone agree about what "0 dp" looks like.
     */
    private val gapAttempts = mutableMapOf<String, Int>()

    /** The last measurement written per package, so the same numbers are not rewritten forever. */
    private val measured = mutableMapOf<String, SendAssistMetrics>()

    /** The send button's bounds at the last measurement, and when it was taken. */
    private val probedBounds = mutableMapOf<String, Rect>()
    private val probedAt = mutableMapOf<String, Long>()

    /** The settings of the app the button is currently drawn for; null while it is not on screen. */
    private var config: SendAssistConfig? = null

    /** Events arrive in bursts; one look at the tree per burst is enough. */
    private val refresh = Runnable { refreshNow() }

    /**
     * Called for every accessibility event.
     *
     * The event's sender decides nothing on its own: hiding whenever a foreign package speaks is
     * what made the button flash and disappear, because two of the loudest packages on screen are
     * our own overlay (adding the button fires an event for `love.miao.yun`) and the keyboard.
     * Every event therefore just asks for another look at the real window state.
     */
    fun onEvent(packageName: String) {
        if (packageName == service.packageName) return
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, REFRESH_DELAY_MS)
    }

    /** Looks at the screen right now, without waiting for an event. */
    fun refresh() {
        handler.removeCallbacks(refresh)
        refreshNow()
    }

    /** Drops the overlay, e.g. when the feature is switched off. */
    fun hide() {
        handler.removeCallbacks(refresh)
        misses = 0
        config = null
        val current = view ?: return
        view = null
        params = null
        placed = null
        runCatching { windowManager?.removeView(current) }
    }

    fun dispose() = hide()

    // ------------------------------------------------------------------ placement

    private fun refreshNow() {
        val window = findTargetWindow() ?: return miss()
        val settings = SendAssistPrefs.load(service, window.first)

        // An app whose own switch is off is still worth measuring once, so its preview can draw the
        // real bar before the feature is turned on — but only once, and only by view id: a full tree
        // walk four times a second inside an app we are not even assisting is work for nothing.
        val measuringOnly = !settings.enabled
        if (measuringOnly && SendAssistPrefs.loadMetrics(service, window.first) != null) {
            return hide()
        }

        val send = findSendButton(window.second, window.first, idsOnly = measuringOnly)
        if (send == null) {
            if (settings.enabled) miss()
            return
        }
        val bounds = Rect()
        send.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            if (settings.enabled) miss()
            return
        }

        captureMeasurement(window.first, bounds)

        if (measuringOnly) return hide()

        misses = 0
        config = settings
        show(bounds, settings)
    }

    // ------------------------------------------------------------------ measuring

    /** The whole display, in screen coordinates: what `getBoundsInScreen` is measured against. */
    private fun screenBounds(): Rect {
        val fromWindowManager = runCatching {
            val manager = service.getSystemService(WindowManager::class.java)
            manager?.maximumWindowMetrics?.bounds
        }.getOrNull()
        if (fromWindowManager != null && !fromWindowManager.isEmpty) return fromWindowManager

        val metrics = service.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * Writes down the input bar's real shape, so the settings preview can draw this app rather than
     * a generic one.
     *
     * Measuring means a walk of the node tree to find the input box, and this runs on every event
     * burst — so it happens only when the send button has *moved*, which is the one signal that the
     * layout underneath changed. The keyboard animating in changes it on every frame, hence the
     * rate limit: the last frame of the animation is the one worth keeping.
     */
    private fun captureMeasurement(packageName: String, sendBounds: Rect) {
        if (probedBounds[packageName] == sendBounds) return
        val now = System.currentTimeMillis()
        if (now - (probedAt[packageName] ?: 0L) < PROBE_INTERVAL_MS) return
        probedBounds[packageName] = Rect(sendBounds)
        probedAt[packageName] = now

        val input = runCatching { service.bestInputNode() }.getOrNull()
            ?.takeIf { it.packageName?.toString() == packageName }
            ?.let { node ->
                Rect().also { node.getBoundsInScreen(it) }.takeIf { !it.isEmpty }
            }

        val metrics = SendAssistMetrics.of(
            density = density,
            screen = screenBounds(),
            send = sendBounds,
            input = input,
            // Wall clock, not uptime: the settings page shows how long ago the bar was seen, and
            // "4 小时前" has to survive the phone being rebooted in between.
            now = now,
        ) ?: return

        if (metrics.sameAs(measured[packageName])) return
        measured[packageName] = metrics
        runCatching { SendAssistPrefs.saveMetrics(service, packageName, metrics) }
    }

    /** "实测 mm 58×36 dp/间隙 -40 dp" for the debug dump; "无" until something is learned. */
    fun describe(): String = buildString {
        append("实测 ")
        append(
            measured.entries.sortedBy { it.key }.joinToString("、") { (packageName, metrics) ->
                val gap = if (metrics.gapMeasured) {
                    "/间隙 ${signed(metrics.gapDp)} dp"
                } else {
                    "/间隙 未实测"
                }
                packageName.substringAfterLast('.') +
                    " ${metrics.sendWidthDp}×${metrics.sendHeightDp} dp" + gap
            }.ifEmpty { "无" },
        )
    }

    private fun signed(value: Int): String = if (value > 0) "+$value" else "$value"

    /**
     * The window of a supported chat app, active one first.
     *
     * `rootInActiveWindow` is not always the chat app: with the keyboard up the focused window can
     * be the input method's, and our own overlay adds another. Anything other than "the chat app
     * is nowhere on screen" is not a reason to give up.
     */
    private fun findTargetWindow(): Pair<String, AccessibilityNodeInfo>? {
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
     * A missing send button hides the overlay, but only after it has been missing twice in a row:
     * a single miss happens mid-layout, mid-animation, and while the keyboard is coming up, and
     * reacting to it makes the button flicker.
     */
    private fun miss() {
        if (++misses >= MISSES_BEFORE_HIDE) hide()
    }

    /**
     * The send button of [packageName], or null when there is none on screen.
     *
     * The known view ids are tried first; when an app renames them — WeChat's are obfuscated per
     * release — a visible, enabled button labelled [SEND_LABEL] is accepted instead, lowest on the
     * screen first, because that is where a send button lives and a header is not.
     *
     * [idsOnly] stops after the view ids, for the one measurement taken in an app the assistant is
     * not switched on for: that walk is the expensive half and there is nothing to place.
     */
    private fun findSendButton(
        root: AccessibilityNodeInfo,
        packageName: String,
        idsOnly: Boolean = false,
    ): AccessibilityNodeInfo? {
        sendTargetFor(packageName)?.sendIds.orEmpty().forEach { id ->
            val byId = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull()
            // Visible is the one condition that matters: QQ keeps a second, hidden `send_btn` in
            // its album panel, and a chat app disables the real one while the box is empty —
            // which is exactly when the assistant should still be offering itself.
            byId?.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        if (idsOnly) return null

        val labelled = mutableListOf<AccessibilityNodeInfo>()
        collectLabelled(root, 0, labelled)
        return labelled.maxByOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.top
        }
    }

    private fun collectLabelled(
        node: AccessibilityNodeInfo?,
        depth: Int,
        into: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node == null || depth > MAX_DEPTH) return
        val className = node.className?.toString().orEmpty()
        val label = node.text?.toString() ?: node.contentDescription?.toString().orEmpty()
        if (className.contains("Button") &&
            label.contains(SEND_LABEL) &&
            node.isVisibleToUser &&
            node.isClickable
        ) {
            into += node
        }
        for (index in 0 until node.childCount) {
            collectLabelled(node.getChild(index), depth + 1, into)
        }
    }

    private fun show(bounds: Rect, settings: SendAssistConfig) {
        val size = (settings.sizeDp * density).toInt()
        val target = SendAssistGeometry.assistantRect(
            send = bounds,
            size = size,
            gap = (SendAssistGeometry.GAP_DP * density).toInt(),
            offsetX = (settings.offsetXDp * density).toInt(),
            offsetY = (settings.offsetYDp * density).toInt(),
            topLimit = 0,
        )

        val existing = view
        if (existing == null) {
            create(size, settings)
        } else if (placed == target) {
            return
        }
        placed = target

        val layout = params ?: return
        layout.width = size
        layout.height = size
        layout.x = target.left
        layout.y = target.top
        val current = view ?: return
        if (existing == null) {
            runCatching { windowManager?.addView(current, layout) }
        } else {
            runCatching { windowManager?.updateViewLayout(current, layout) }
        }
        readBackGap(current, bounds, settings)
    }

    /**
     * Reads back where the button actually ended up and stores the gap it produced.
     *
     * The arithmetic above is only as good as the assumption that window coordinates and the
     * accessibility tree's screen coordinates start in the same place. Rather than assume, ask the
     * only authority there is — `getLocationOnScreen` — and hand the answer to the settings preview,
     * which is the one place that has to draw the button where the phone draws it.
     *
     * An answer that cannot be a placement is discarded instead of stored: a window that has not
     * been through a layout pass yet reports `0,0`, which would read as a gap of half a screen.
     */
    private fun readBackGap(current: View, sendBounds: Rect, settings: SendAssistConfig) {
        val packageName = settings.packageName
        if ((gapAttempts[packageName] ?: 0) >= MAX_GAP_ATTEMPTS) return
        gapAttempts[packageName] = (gapAttempts[packageName] ?: 0) + 1

        val sizePx = (settings.sizeDp * density).toInt()
        val offsetY = (settings.offsetYDp * density).toInt()
        val designedGap = (SendAssistGeometry.GAP_DP * density).toInt()

        current.postDelayed(
            {
                if (view !== current) return@postDelayed
                val location = IntArray(2)
                runCatching { current.getLocationOnScreen(location) }.getOrElse { return@postDelayed }
                // The user's own offset is taken back out: what is being measured is the difference
                // between the placement asked for and the placement delivered, not their tuning.
                val gapPx = sendBounds.top - (location[1] + sizePx) - offsetY
                if (abs(gapPx - designedGap) > MAX_SINKING_PX) return@postDelayed

                val base = measured[packageName]
                    ?: SendAssistPrefs.loadMetrics(service, packageName)
                    ?: return@postDelayed
                val gapDp = Math.round(gapPx / density)
                if (base.gapMeasured && base.gapDp == gapDp) return@postDelayed

                val updated = base.copy(
                    gapDp = gapDp,
                    gapMeasured = true,
                    capturedAt = System.currentTimeMillis(),
                )
                measured[packageName] = updated
                runCatching { SendAssistPrefs.saveMetrics(service, packageName, updated) }
            },
            VERIFY_DELAY_MS,
        )
    }

    /**
     * Repaints size, corner, opacity and padding, and repositions.
     *
     * Called when the settings change: the button is on screen while the user is dragging those
     * sliders in our own app, so without this they would have to reopen the chat app to see it.
     */
    fun applyStyle() {
        val current = view ?: return
        val packageName = config?.packageName ?: return
        val settings = SendAssistPrefs.load(service, packageName)
        val size = (settings.sizeDp * density).toInt()

        config = settings
        background?.cornerRadius = settings.effectiveCornerDp * density
        current.alpha = settings.opacity / 100f
        icon?.let { iconView ->
            val inset = (size * 0.26f).toInt()
            iconView.setPadding(inset, inset, inset, inset)
        }
        spinner?.layoutParams = (spinner?.layoutParams as? FrameLayout.LayoutParams)?.apply {
            width = (size * 0.55f).toInt()
            height = (size * 0.55f).toInt()
        }
        params?.let { layout ->
            layout.width = size
            layout.height = size
            runCatching { windowManager?.updateViewLayout(current, layout) }
        }
        // The next look at the screen decides where it goes, with the new size.
        placed = null
    }

    private fun create(size: Int, settings: SendAssistConfig) {
        val drawable = GradientDrawable().apply {
            cornerRadius = settings.effectiveCornerDp * density
        }
        background = drawable

        val iconView = ImageView(service).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_ball_auto_awesome)
            val inset = (size * 0.26f).toInt()
            setPadding(inset, inset, inset, inset)
        }
        val progress = ProgressBar(service).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        val container = FrameLayout(service).apply {
            addView(
                iconView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                progress,
                FrameLayout.LayoutParams(
                    (size * 0.55f).toInt(),
                    (size * 0.55f).toInt(),
                    Gravity.CENTER,
                ),
            )
            background = drawable
            elevation = 6f * density
            alpha = settings.opacity / 100f
            contentDescription = "喵喵助手"
            setOnClickListener { activate() }
        }

        val layout = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        view = container
        icon = iconView
        spinner = progress
        params = layout
        applyPalette()
    }

    private fun applyPalette() {
        val colors = FloatingPalettes.resolve(service, UiEnginePrefs.loadFloatingColor(service))
        background?.setColor(colors.container)
        icon?.setColorFilter(colors.onContainer, PorterDuff.Mode.SRC_IN)
        spinner?.indeterminateTintList = android.content.res.ColorStateList.valueOf(colors.onContainer)
    }

    // ------------------------------------------------------------------ acting

    private fun activate() {
        if (busy) return
        val settings = config ?: return
        when (settings.actionEntry) {
            SendAssistAction.AiModify -> {
                setBusy(true)
                AiRewrite.run(service, service) { written, message ->
                    setBusy(false)
                    toast(message)
                    // Only send what the model actually produced; a failed rewrite must never be
                    // followed by pressing send.
                    if (written && settings.autoSend) sendNow()
                }
            }

            SendAssistAction.Capture -> {
                val text = service.getCurrentWindowText()
                if (text.isEmpty()) {
                    toast(service.getString(R.string.ai_no_input))
                } else {
                    val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("miao", text))
                    toast(service.getString(R.string.floating_copied, text.length))
                    if (settings.autoSend) sendNow()
                }
            }

            SendAssistAction.DumpUi -> {
                val saved = DebugDump.save(service, service.dumpScreen())
                toast(if (saved) "界面元素已导出" else "导出失败")
            }
        }
    }

    /** Presses the app's send button. Re-found, because writing text moved the tree underneath us. */
    private fun sendNow() {
        val root = service.rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        val send = findSendButton(root, packageName) ?: run {
            toast("没找到发送按钮")
            return
        }
        if (!send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            toast("发送失败，请手动点发送")
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        icon?.visibility = if (value) View.GONE else View.VISIBLE
        spinner?.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(service, message, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val REFRESH_DELAY_MS = 250L

        /** How many consecutive looks may fail to find the send button before the overlay goes. */
        const val MISSES_BEFORE_HIDE = 2

        const val MAX_DEPTH = 40

        /** Two measurements of the same app are never taken closer together than this. */
        const val PROBE_INTERVAL_MS = 800L

        /** Long enough for the freshly added window to have been through a layout pass. */
        const val VERIFY_DELAY_MS = 150L

        /** A real displacement is a system inset; anything bigger is a window that is not placed yet. */
        const val MAX_SINKING_PX = 240

        /** Retries at reading the landing spot back, per app per session. */
        const val MAX_GAP_ATTEMPTS = 3
    }
}
