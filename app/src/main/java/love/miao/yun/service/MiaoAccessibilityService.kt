/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 *
 * Ported from the original NekoNeko app (top.youzix.nekoneko.AccessibilityService).
 */

package love.miao.yun.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
class MiaoAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: nothing is ever captured without the user asking for it.
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Only window content is needed, and only on demand — no event-driven work.
        val info = serviceInfo ?: return
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * The text inside the field the user is typing in, trimmed.
     *
     * @return the text, or an empty string when no editable field currently holds input focus.
     */
    fun getCurrentWindowText(): String {
        val input = focusedInputNode() ?: return ""
        return extractInputText(input)
    }

    /**
     * Replaces the whole content of the focused field.
     *
     * @return `false` when there is no focused field, or the app refuses `ACTION_SET_TEXT`.
     */
    fun replaceInputText(newText: String): Boolean {
        val input = focusedInputNode() ?: return false
        return setNodeText(input, newText)
    }

    /** Appends [suffix] to the focused field's current text. */
    fun appendInputText(suffix: String): Boolean {
        val input = focusedInputNode() ?: return false
        val current = input.text?.toString().orEmpty()
        return setNodeText(input, current + suffix)
    }

    // ------------------------------------------------------------------ internals

    /** The editable node holding input focus in the active window, skipping our own overlay. */
    private fun focusedInputNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        // Never capture from our own window — the overlay would otherwise match itself.
        if (packageName == root.packageName?.toString()) return null

        return findFocusedInputNode(root)
    }

    private fun findFocusedInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Preferred route: ask the framework directly.
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (focused.isEditable) return focused
            // Focus landed on a container; look for the field inside it.
            findEditableDescendant(focused)?.let { return it }
        }

        // Fallback: some apps do not report input focus, so walk the tree instead.
        return findFocusedEditableRecursive(root, 0)
    }

    private fun findEditableDescendant(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            findEditableDescendant(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findFocusedEditableRecursive(
        node: AccessibilityNodeInfo?,
        depth: Int,
    ): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_DEPTH) return null
        if ((node.isFocused || node.isAccessibilityFocused) && node.isEditable) return node
        for (i in 0 until node.childCount) {
            findFocusedEditableRecursive(node.getChild(i), depth + 1)?.let { return it }
        }
        return null
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
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

    companion object {
        private const val MAX_DEPTH = 24

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
            val expected = ComponentName(context, MiaoAccessibilityService::class.java)
                .flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
