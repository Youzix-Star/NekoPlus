/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.sendassist

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import love.miao.yun.floating.FloatingAction
import love.miao.yun.floating.FloatingIcon
import love.miao.yun.floating.FloatingItem
import love.miao.yun.floating.FloatingWindowPrefs
import love.miao.yun.floating.readFloatingItem
import love.miao.yun.floating.toJson
import love.miao.yun.service.MiaoAccessibilityService
import org.json.JSONObject

/**
 * One chat app the assistant knows how to work with.
 *
 * @param sendIds the view ids to try first; both were captured from a real device, and both are
 *   obfuscated per release, so they are a head start rather than the only way in.
 */
data class SendTarget(
    val packageName: String,
    val label: String,
    val sendIds: List<String>,
)

/**
 * The supported apps.
 *
 * Only WeChat and QQ, on purpose: both were captured from a real device, and guessing at a third
 * app's layout without one is how a feature ends up sending messages nobody asked for.
 */
val SEND_TARGETS: List<SendTarget> = listOf(
    SendTarget("com.tencent.mm", "微信", listOf("com.tencent.mm:id/bql")),
    SendTarget("com.tencent.mobileqq", "QQ", listOf("com.tencent.mobileqq:id/send_btn")),
)

/** The target for a package name, or null when that app is not supported. */
fun sendTargetFor(packageName: String?): SendTarget? =
    SEND_TARGETS.firstOrNull { it.packageName == packageName }

/**
 * What the send button is called on screen.
 *
 * The ids above move between releases; the label is the stable part, so a button whose id is not
 * recognised is still found by its text.
 */
const val SEND_LABEL = "发送"

/** How deep the search for a button labelled [SEND_LABEL] goes. */
private const val MAX_DEPTH = 40

/**
 * The send button of [packageName] on screen, or null when there is none.
 *
 * The known view ids are tried first; when an app renames them — WeChat's are obfuscated per release
 * — a visible, clickable button labelled [SEND_LABEL] is accepted instead, lowest on the screen
 * first, because that is where a send button lives and a header is not.
 *
 * [allowLabelWalk] is the caller's throttle, not a preference: the walk visits every node of the
 * tree, and the caller runs this on a timer, so it passes `false` on most rounds and `true` now and
 * then.
 */
fun findSendButton(
    root: AccessibilityNodeInfo,
    packageName: String,
    allowLabelWalk: Boolean = true,
): AccessibilityNodeInfo? {
    sendTargetFor(packageName)?.sendIds.orEmpty().forEach { id ->
        val byId = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull()
        // Visible is the one condition that matters: QQ keeps a second, hidden `send_btn` in its
        // album panel, and a chat app disables the real one while the box is empty — which is
        // exactly when the assistant should still be offering itself.
        byId?.firstOrNull { it.isVisibleToUser }?.let { return it }
    }
    if (!allowLabelWalk) return null

    val labelled = mutableListOf<AccessibilityNodeInfo>()
    collectLabelled(root, 0, labelled)
    return labelled.maxByOrNull { node ->
        val rect = Rect()
        node.getBoundsInScreen(rect)
        rect.top
    }
}

/** Presses the chat app's send button. False when there is none to press. */
fun pressSendButton(service: MiaoAccessibilityService): Boolean {
    val root = service.rootInActiveWindow ?: return false
    val packageName = root.packageName?.toString() ?: return false
    val send = findSendButton(root, packageName) ?: return false
    return send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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

/**
 * The steps a chain may hold on the assistant.
 *
 * A subset of [FloatingAction], not a second enum: the assistant runs the same actions a floating
 * button does, which is what stops "AI 修改" from meaning two different things. What is left out is
 * only what would be nonsense here — opening our own app has nothing to do with a chat box, and
 * 收起悬浮窗 has nothing to close.
 */
val ASSIST_ACTIONS: List<FloatingAction> = listOf(
    FloatingAction.AiModify,
    FloatingAction.Capture,
    FloatingAction.Send,
    FloatingAction.DumpUi,
)

/**
 * One app's assistant: how it looks, what it does, and where it sits relative to the send button.
 *
 * [item] is an ordinary [FloatingItem] — the same model a floating button is — because the assistant
 * *is* a floating button; the only thing that makes it different is that its position is derived
 * from the chat app's send button instead of being dragged.
 *
 * @param offsetXDp how far right of the send button's right edge to sit.
 * @param offsetYDp how far below the resting spot to sit, so `-8` lifts it clear of the send key.
 */
data class SendAssistConfig(
    val packageName: String,
    val item: FloatingItem,
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
) {
    val enabled: Boolean get() = item.enabled

    /** The stored chain cut down to the steps this surface can actually run. */
    val actionEntries: List<FloatingAction>
        get() = item.actionEntries.filter { it in ASSIST_ACTIONS }
            .ifEmpty { listOf(FloatingAction.AiModify) }

    /** "AI 修改 → 按发送 · 44×44 dp · 胶囊 · 92%" */
    val summary: String
        get() = buildString {
            append(actionEntries.joinToString(" → ") { it.label })
            append(" · " + item.summary)
            if (offsetXDp != 0 || offsetYDp != 0) {
                append(" · 偏移 ${signed(offsetXDp)}/${signed(offsetYDp)}")
            }
        }

    private fun signed(value: Int): String = if (value > 0) "+$value" else "$value"
}

/**
 * The assistant's settings: one record per supported app, in its own preference file.
 *
 * Its own file rather than a corner of the floating window's, so the whole feature can be backed up,
 * cleared or ignored as one thing.
 */
object SendAssistPrefs {
    const val PREFS = "send_assist"

    /** How far the button may be nudged off its resting place, in either direction. */
    const val MAX_OFFSET_DP = 64

    /** The size a fresh assistant starts at: small enough to not cover the send key it sits on. */
    const val DEFAULT_SIZE_DP = 44
    const val DEFAULT_OPACITY = 92

    private const val KEY_PREFIX = "config:"
    private const val ITEM_ID = "assist"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Off until asked for: an overlay that appears on its own in a chat app needs consent. */
    fun load(context: Context, packageName: String): SendAssistConfig {
        val raw = prefs(context).getString(KEY_PREFIX + packageName, null)
            ?: return SendAssistConfig(packageName, defaultItem())
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return SendAssistConfig(packageName, defaultItem())
        return runCatching { read(packageName, json) }
            .getOrElse { SendAssistConfig(packageName, defaultItem()) }
    }

    fun save(context: Context, config: SendAssistConfig) {
        val json = JSONObject().apply {
            put("offsets", JSONObject().apply {
                put("x", config.offsetXDp.coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP))
                put("y", config.offsetYDp.coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP))
            })
            put("item", config.item.toJson())
        }
        prefs(context).edit().putString(KEY_PREFIX + config.packageName, json.toString()).apply()
    }

    /** Whether any app has the assistant switched on; when none has, it never runs at all. */
    fun anyEnabled(context: Context): Boolean =
        SEND_TARGETS.any { load(context, it.packageName).enabled }

    /** Observable access for the service, which outlives the activity. Pair with [unregister]. */
    fun register(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** The button a fresh app starts with: the AI rewrite, at a size that fits above a send key. */
    private fun defaultItem(): FloatingItem = FloatingItem(
        id = ITEM_ID,
        enabled = false,
        icon = FloatingIcon.AutoAwesome.id,
        showIcon = true,
        text = "",
        actions = listOf(FloatingAction.AiModify.id),
        holdActions = emptyList(),
        widthDp = DEFAULT_SIZE_DP,
        heightDp = DEFAULT_SIZE_DP,
        cornerDp = DEFAULT_SIZE_DP / 2,
        opacity = DEFAULT_OPACITY,
        // Position is the send button's business, not the user's, so these stay at zero.
        x = 0,
        y = 0,
    )

    private fun read(packageName: String, json: JSONObject): SendAssistConfig {
        val stored = json.optJSONObject("item")
        if (stored != null) {
            val item = readFloatingItem(stored, fallbackId = ITEM_ID)
                ?: defaultItem()
            val offsets = json.optJSONObject("offsets")
            return SendAssistConfig(
                packageName = packageName,
                item = item,
                offsetXDp = (offsets?.optInt("x", 0) ?: 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP),
                offsetYDp = (offsets?.optInt("y", 0) ?: 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP),
            )
        }
        return migrate(packageName, json)
    }

    /**
     * Reads a record written before the assistant shared the floating button's model.
     *
     * The old shape held one action and an "auto send" switch; both become chain steps, which is
     * what they always were. Nobody has to reconfigure anything — the settings they tuned are the
     * settings they keep.
     */
    private fun migrate(packageName: String, json: JSONObject): SendAssistConfig {
        val size = json.optInt("size", DEFAULT_SIZE_DP)
            .coerceIn(FloatingWindowPrefs.MIN_SIZE_DP, FloatingWindowPrefs.MAX_SIZE_DP)
        val corner = json.optInt("corner", -1)
            .let { if (it < 0) size / 3 else it }
            .coerceIn(0, size / 2)

        val action = LEGACY_ACTIONS[json.optString("action")] ?: FloatingAction.AiModify
        val chain = buildList {
            add(action.id)
            if (json.optBoolean("autoSend", false)) add(FloatingAction.Send.id)
        }

        return SendAssistConfig(
            packageName = packageName,
            item = FloatingItem(
                id = ITEM_ID,
                enabled = json.optBoolean("enabled", false),
                icon = FloatingIcon.AutoAwesome.id,
                showIcon = true,
                text = "",
                actions = chain,
                holdActions = emptyList(),
                widthDp = size,
                heightDp = size,
                cornerDp = corner,
                opacity = json.optInt("opacity", DEFAULT_OPACITY).coerceIn(
                    FloatingWindowPrefs.MIN_OPACITY,
                    FloatingWindowPrefs.MAX_OPACITY,
                ),
                x = 0,
                y = 0,
            ),
            offsetXDp = json.optInt("offsetX", 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP),
            offsetYDp = json.optInt("offsetY", 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP),
        )
    }

    /** The ids an older build of this feature stored, and what they are called now. */
    private val LEGACY_ACTIONS = mapOf(
        "ai" to FloatingAction.AiModify,
        "copy" to FloatingAction.Capture,
        "dump" to FloatingAction.DumpUi,
    )
}
