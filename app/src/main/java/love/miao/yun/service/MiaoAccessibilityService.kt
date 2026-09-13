/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported from the original NekoNeko app (top.youzix.nekoneko.AccessibilityService).
 */

package love.miao.yun.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads and rewrites the text field the user is currently typing in.
 *
 * Deliberately event-free: [onAccessibilityEvent] does nothing. Every capture and every write
 * happens because the user pressed a button in the floating window, so the service never
 * inspects the screen on its own.
 *
 * The instance is published statically because the overlay lives in
 * [FloatingWindowService] and has no other way to reach it.
 *
 * `AccessibilityNodeInfo.recycle()` is intentionally not called: it has been a no-op since
 * API 33, which is this app's `minSdk`, so the original's recycling dance is dead code here.
 */
open class MiaoAccessibilityService : AccessibilityService() {

    /**
     * The button that sits above a chat app's send button.
     *
     * Created lazily: with the feature switched off, the service still does nothing at all on its
     * own, which is the promise this app makes about an accessibility service.
     */
    private var sendAssist: SendAssistOverlay? = null

    /**
     * The assistant needs to know when the send button appears, moves or goes away — and nothing
     * else. It never reads the input box here: text is captured only when the user taps a button.
     */
    private val sendAssistPrefs =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> syncSendAssist() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val assist = sendAssist ?: return
        val packageName = event?.packageName?.toString() ?: return
        assist.onEvent(packageName)
    }

    /** Brings the overlay in line with the preference: created when on, gone when off. */
    private fun syncSendAssist() {
        if (love.miao.yun.sendassist.SendAssistPrefs.isEnabled(this)) {
            val overlay = sendAssist ?: SendAssistOverlay(this).also { sendAssist = it }
            overlay.onEvent(rootInActiveWindow?.packageName?.toString().orEmpty())
        } else {
            sendAssist?.hide()
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Only window content is needed, and only on demand — no event-driven work. Retrieving
        // every window matters: an app's input box is not always in the active window's tree.
        val info = serviceInfo ?: return
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            // Without this most apps omit their view ids, and an id is often the only stable way
            // to name the field we are after.
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info

        love.miao.yun.sendassist.SendAssistPrefs.register(this, sendAssistPrefs)
        syncSendAssist()
    }

    override fun onDestroy() {
        sendAssist?.dispose()
        sendAssist = null
        runCatching { love.miao.yun.sendassist.SendAssistPrefs.unregister(this, sendAssistPrefs) }
        instance = null
        super.onDestroy()
    }

    /**
     * The text inside the field the user is typing in, trimmed.
     *
     * @return the text, or an empty string when no field could be found.
     */
    fun getCurrentWindowText(): String {
        val input = bestInputNode() ?: return ""
        return extractInputText(input)
    }

    /**
     * Replaces the whole content of the focused field.
     *
     * @return `false` when there is no field, or the app refuses every way of writing to it.
     */
    fun replaceInputText(newText: String): Boolean {
        val input = bestInputNode() ?: return false
        return setNodeText(input, newText)
    }

    /** Appends [suffix] to the current field's text. */
    fun appendInputText(suffix: String): Boolean {
        val input = bestInputNode() ?: return false
        val current = input.text?.toString().orEmpty()
        return setNodeText(input, current + suffix)
    }

    // ------------------------------------------------------------------ input discovery

    /**
     * The node most likely to be the field the user is typing in.
     *
     * Focus alone is not enough. WeChat is the reason this is written the way it is: its chat box
     * is a custom-drawn field that reports `isEditable = false` and often does not appear in the
     * active window's tree at all, so a capture built on "find the focused editable node" finds
     * nothing there. Instead every window is walked, every node that could plausibly take text is
     * collected, and they are ranked — focused first, then editable, then the ones that merely
     * look like a text field or accept `ACTION_SET_TEXT`.
     */
    private fun bestInputNode(): AccessibilityNodeInfo? = inputCandidates().firstOrNull()

    private fun inputCandidates(): List<AccessibilityNodeInfo> {
        val foreground = rootInActiveWindow?.packageName?.toString()
        val found = mutableListOf<AccessibilityNodeInfo>()
        orderedRoots().forEach { root -> collectTextNodes(root, 0, found) }

        val unique = found.distinct()
        // Invisible fields are other apps' plumbing, not something the user is typing in: Termux
        // keeps an invisible EditText as its keyboard proxy, and writing into that instead of the
        // app in front is worse than finding nothing at all.
        val visible = unique.filter { it.isVisibleToUser }
        val pool = visible.ifEmpty { unique }
        // Then the app in front wins: a field in a background window is never the chat box.
        val inFront = pool.filter { it.packageName?.toString() == foreground }
        return (inFront.ifEmpty { pool })
            .sortedByDescending { score(it) }
            .take(MAX_CANDIDATES)
    }

    /** Every window's root, the active one first, minus our own overlay. */
    private fun orderedRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val windows = runCatching { windows }.getOrNull().orEmpty()
        windows.sortedByDescending { it.isActive }.forEach { window ->
            val root = window.root ?: return@forEach
            if (root.packageName?.toString() == packageName) return@forEach
            roots += root
        }
        rootInActiveWindow?.let { root ->
            if (root.packageName?.toString() != packageName) roots.add(0, root)
        }
        return roots.distinct()
    }

    /** How much this node looks like the field the user is typing in. */
    private fun score(node: AccessibilityNodeInfo): Int {
        var score = 0
        if (node.isFocused) score += 100
        if (node.isAccessibilityFocused) score += 40
        if (node.isEditable) score += 50
        if (supportsSetText(node)) score += 20
        val className = node.className?.toString().orEmpty()
        if (className.contains("EditText")) score += 30
        if (className.contains("AutoComplete")) score += 10
        if (className.contains("WebView")) score += 5
        if (node.text?.isNotEmpty() == true) score += 5
        if (node.isVisibleToUser) score += 60 else score -= 40
        if (node.isPassword) score -= 5
        // Containers that merely accept text actions are a weak signal on their own.
        if (node.childCount > 3) score -= 10
        return score
    }

    private fun collectTextNodes(
        node: AccessibilityNodeInfo?,
        depth: Int,
        into: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node == null || depth > MAX_DEPTH || into.size > MAX_CANDIDATES * 4) return
        if (isTextLike(node)) into += node
        for (index in 0 until node.childCount) {
            collectTextNodes(node.getChild(index), depth + 1, into)
        }
    }

    /** Whether this node is worth considering as a text field at all. */
    private fun isTextLike(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        if (supportsSetText(node)) return true
        val className = node.className?.toString().orEmpty()
        if (className.contains("EditText") || className.contains("AutoComplete")) return true
        // A focused node with text of its own is often a custom-drawn input.
        return node.isFocused && !node.text.isNullOrEmpty()
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean {
        val actions = node.actionList ?: return false
        return actions.any {
            it.id == AccessibilityNodeInfo.ACTION_SET_TEXT ||
                it.id == AccessibilityNodeInfo.ACTION_PASTE
        }
    }

    // ------------------------------------------------------------------ writing

    /**
     * Writes [text] into [node], trying the direct route first.
     *
     * `ACTION_SET_TEXT` is refused by some custom fields — WeChat's among them — while still
     * accepting a paste, so the clipboard is the fallback rather than giving up.
     */
    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("miao", text))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    /** The node's own text, falling back to the first non-empty text in its subtree. */
    private fun extractInputText(node: AccessibilityNodeInfo): String {
        val own = node.text?.toString()?.trim()
        if (!own.isNullOrEmpty()) return own
        return findFirstText(node, 0)
    }

    private fun findFirstText(node: AccessibilityNodeInfo?, depth: Int): String {
        if (node == null || depth > MAX_DEPTH) return ""
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) return text
        for (i in 0 until node.childCount) {
            val found = findFirstText(node.getChild(i), depth + 1)
            if (found.isNotEmpty()) return found
        }
        return ""
    }

    // ------------------------------------------------------------------ debugging

    /**
     * Everything this service can see, as text: every window, every node, every attribute.
     *
     * Written for the case where a capture silently fails in one particular app. The diagnosis
     * block at the top says what the ranking picked and why, and the tree below it is the raw
     * material — enough to work out what that app is actually doing without a device in hand.
     */
    fun dumpScreen(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val roots = orderedRoots()
        val candidates = inputCandidates()

        return buildString {
            appendLine("喵喵助手 界面元素抓取")
            appendLine("时间: $stamp")
            appendLine(
                "设备: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
                    "(SDK ${Build.VERSION.SDK_INT})",
            )
            appendLine("本应用包名: $packageName")
            appendLine()

            appendLine("===== 捕获诊断 =====")
            // The component name is the whole point of the disguise, so print the one the system
            // actually bound: it is the difference between "WeChat blocks us" and "the disguised
            // build was never installed".
            appendLine("本服务组件名: " + (serviceInfo?.id ?: "?"))
            appendLine(
                "服务 flags: 0x" + Integer.toHexString(serviceInfo?.flags ?: 0) +
                    " (RETRIEVE_INTERACTIVE_WINDOWS=" +
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS.toString(16) +
                    " VIEW_IDS=" + AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS.toString(16) + ")",
            )
            val active = rootInActiveWindow
            appendLine("rootInActiveWindow: " + (active?.packageName?.toString() ?: "无"))
            appendLine("  直接子节点数: ${active?.childCount ?: -1}")
            if (active != null && active.childCount == 0) {
                // A window whose tree looks empty may just be stale; refresh before believing it.
                active.refresh()
                appendLine("  refresh() 之后: ${active.childCount}")
                if (active.childCount == 0) {
                    appendLine("  ⚠ 前台窗口没有节点树：这个应用屏蔽了无障碍内容（微信 8.0.52+ 的已知行为），")
                    appendLine("    只能靠组件名伪装绕过去，伪装没生效时就是这个样子。")
                }
            }
            appendLine("可交互窗口数: ${runCatching { windows }.getOrNull()?.size ?: 0}")
            appendLine()
            appendLine("候选输入节点: ${candidates.size}")
            candidates.forEachIndexed { index, node ->
                appendLine(
                    "  #${index + 1} score=${score(node)} pkg=${node.packageName ?: "?"} " +
                        "visible=${node.isVisibleToUser} ${describe(node)}",
                )
            }
            appendLine("→ 当前会选中: " + (candidates.firstOrNull()?.let { describe(it) } ?: "无"))
            appendLine()
            // `this` inside buildString is the StringBuilder, not the service.
            appendLine(
                "发送按钮助手: " +
                    if (love.miao.yun.sendassist.SendAssistPrefs.isEnabled(this@MiaoAccessibilityService)) {
                        "已开启"
                    } else {
                        "已关闭"
                    },
            )
            active?.packageName?.toString()?.let { pkg ->
                val target = love.miao.yun.sendassist.SEND_TARGETS[pkg]
                if (target != null) {
                    val button = target.firstNotNullOfOrNull { id ->
                        runCatching { active.findAccessibilityNodeInfosByViewId(id) }.getOrNull()
                            ?.firstOrNull { it.isVisibleToUser && it.isEnabled }
                    }
                    appendLine(
                        "  已适配的应用，按 id 找到的发送按钮: " +
                            (button?.let { describe(it) } ?: "无（会退化为按文字「${love.miao.yun.sendassist.SEND_LABEL}」查找）"),
                    )
                }
            }
            appendLine()

            appendLine("===== 窗口与节点 =====")
            val windows = runCatching { windows }.getOrNull().orEmpty()
            if (windows.isEmpty()) {
                appendLine("(没有可交互窗口：无障碍服务可能未连接)")
            }
            windows.forEachIndexed { index, window ->
                appendLine(windowHeader(index, window))
                val root = window.root
                if (root == null) {
                    appendLine("  (空窗口)")
                } else {
                    var counter = 0
                    appendTree(root, 0, { counter++ }, this)
                }
                appendLine()
            }
        }
    }

    private fun windowHeader(index: Int, window: AccessibilityWindowInfo): String {
        val type = when (window.type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "应用"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "输入法"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "系统"
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "无障碍浮层"
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "分屏分隔"
            AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> "放大镜"
            else -> "类型${window.type}"
        }
        val title = runCatching { window.title?.toString() }.getOrNull().orEmpty()
        val root = runCatching { window.root }.getOrNull()
        // `直接子节点数=0` on the window the user is looking at is the signature of an app that
        // hides its whole tree from accessibility, which is worth stating rather than leaving as
        // "the dump is empty".
        return "--- 窗口 ${index + 1}: $type · id=${window.id} active=${window.isActive} " +
            "focused=${window.isFocused} layer=${window.layer} " +
            "rootPkg=${root?.packageName?.toString() ?: "?"} " +
            "rootChildren=${root?.childCount ?: -1}" +
            (if (title.isNotEmpty()) " title=$title" else "")
    }

    private fun appendTree(
        node: AccessibilityNodeInfo,
        depth: Int,
        counter: () -> Int,
        out: StringBuilder,
    ) {
        val index = counter()
        if (index > MAX_DUMP_NODES) {
            out.appendLine("  ".repeat(depth) + "(节点过多，已截断)")
            return
        }
        out.appendLine("  ".repeat(depth) + "[$index] " + describe(node))
        for (child in 0 until node.childCount) {
            val next = node.getChild(child) ?: continue
            appendTree(next, depth + 1, counter, out)
        }
    }

    /** One node, on one line, with only the fields that carry information. */
    private fun describe(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        parts += "class=" + (node.className?.toString() ?: "?")
        node.viewIdResourceName?.let { parts += "id=$it" }
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "text='${clip(it)}'" }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { parts += "desc='${clip(it)}'" }
        node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "hint='${clip(it)}'" }
        node.error?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "error='${clip(it)}'" }

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        parts += "bounds=[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"

        val flags = buildList {
            if (node.isFocused) add("focused")
            if (node.isAccessibilityFocused) add("a11yFocused")
            if (node.isEditable) add("editable")
            if (node.isFocusable) add("focusable")
            if (node.isClickable) add("clickable")
            if (node.isLongClickable) add("longClickable")
            if (node.isScrollable) add("scrollable")
            if (node.isCheckable) add("checkable")
            if (node.isChecked) add("checked")
            if (node.isSelected) add("selected")
            if (node.isPassword) add("password")
            if (node.isEnabled) add("enabled") else add("disabled")
            if (node.isVisibleToUser) add("visible") else add("invisible")
            if (node.childCount > 0) add("kids=${node.childCount}")
        }
        if (flags.isNotEmpty()) parts += flags.joinToString(",")

        val actions = node.actionList.orEmpty()
            .map { actionName(it.id) }
            .filter { it.isNotEmpty() }
        if (actions.isNotEmpty()) parts += "actions=[${actions.joinToString(",")}]"

        return parts.joinToString(" ")
    }

    private fun actionName(id: Int): String = when (id) {
        AccessibilityNodeInfo.ACTION_CLICK -> "CLICK"
        AccessibilityNodeInfo.ACTION_LONG_CLICK -> "LONG_CLICK"
        AccessibilityNodeInfo.ACTION_FOCUS -> "FOCUS"
        AccessibilityNodeInfo.ACTION_CLEAR_FOCUS -> "CLEAR_FOCUS"
        AccessibilityNodeInfo.ACTION_SELECT -> "SELECT"
        AccessibilityNodeInfo.ACTION_CLEAR_SELECTION -> "CLEAR_SELECTION"
        AccessibilityNodeInfo.ACTION_SET_TEXT -> "SET_TEXT"
        AccessibilityNodeInfo.ACTION_PASTE -> "PASTE"
        AccessibilityNodeInfo.ACTION_COPY -> "COPY"
        AccessibilityNodeInfo.ACTION_CUT -> "CUT"
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "SCROLL_FORWARD"
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "SCROLL_BACKWARD"
        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> "A11Y_FOCUS"
        AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "NEXT_GRANULARITY"
        AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "PREV_GRANULARITY"
        AccessibilityNodeInfo.ACTION_EXPAND -> "EXPAND"
        AccessibilityNodeInfo.ACTION_COLLAPSE -> "COLLAPSE"
        AccessibilityNodeInfo.ACTION_DISMISS -> "DISMISS"
        else -> "ACTION_$id"
    }

    private fun clip(text: String): String =
        if (text.length <= MAX_TEXT) text else text.take(MAX_TEXT) + "…(${text.length})"

    companion object {
        private const val MAX_DEPTH = 40
        private const val MAX_CANDIDATES = 12
        private const val MAX_DUMP_NODES = 3000
        private const val MAX_TEXT = 80

        @Volatile
        private var instance: MiaoAccessibilityService? = null

        /** The connected service, or `null` when the user has not enabled it. */
        fun instance(): MiaoAccessibilityService? = instance

        /**
         * Whether this service is switched on in system settings *right now*.
         *
         * Reads the secure setting rather than trusting [instance], because the service may be
         * enabled while not yet connected, and the UI needs to tell those apart from "off".
         */
        fun isEnabled(context: Context): Boolean {
            // The name the system binds is the disguised one, so that is the name to look for.
            val expected = ComponentName(
                context,
                com.google.android.accessibility.selecttospeak.SelectToSpeakService::class.java,
            ).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
