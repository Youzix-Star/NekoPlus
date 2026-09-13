/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.sendassist

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import org.json.JSONObject
import kotlin.math.roundToInt

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
 * Only WeChat and QQ for now, on purpose: both were captured from a real device, and guessing at a
 * third app's layout without one is how a feature ends up sending messages nobody asked for.
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

/** What the assistant does when its button is tapped. */
enum class SendAssistAction(val id: String, val label: String) {
    /** Capture the input box, have the model rewrite it, write it back. */
    AiModify("ai", "AI 修改"),

    /** Capture the input box and put it on the clipboard. */
    Capture("copy", "复制文本"),

    /** Write the whole screen's nodes to the debug file, for working out a new app's layout. */
    DumpUi("dump", "导出界面元素"),
    ;

    companion object {
        fun from(id: String?): SendAssistAction =
            entries.firstOrNull { it.id == id } ?: AiModify
    }
}

/**
 * Everything one app's assistant needs: whether it is on, what it does, and how it looks.
 *
 * Per app rather than global, because the two apps' input bars are not the same shape and a size
 * that sits well above WeChat's send button can end up covering QQ's.
 */
data class SendAssistConfig(
    val packageName: String,
    val enabled: Boolean = false,
    val action: String = SendAssistAction.AiModify.id,
    val autoSend: Boolean = false,
    val sizeDp: Int = SendAssistPrefs.DEFAULT_SIZE_DP,
    /** Corner radius; `-1` means "follow the size", which is a third of it. */
    val cornerDp: Int = -1,
    val opacity: Int = SendAssistPrefs.DEFAULT_OPACITY,
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
) {
    val actionEntry: SendAssistAction get() = SendAssistAction.from(action)

    /** The radius actually allowed at this size, and never more than half of it. */
    val effectiveCornerDp: Int
        get() = (if (cornerDp < 0) sizeDp / 3 else cornerDp).coerceIn(0, sizeDp / 2)

    /** "AI 修改 · 自动发送 · 56 dp · 92% · 偏移 +0/-8" */
    val summary: String
        get() = buildString {
            append(actionEntry.label)
            if (autoSend) append(" · 自动发送")
            append(" · ${sizeDp} dp · $opacity%")
            if (offsetXDp != 0 || offsetYDp != 0) {
                append(" · 偏移 ${signed(offsetXDp)}/${signed(offsetYDp)}")
            }
        }

    private fun signed(value: Int): String = if (value > 0) "+$value" else "$value"
}

/**
 * What the assistant last saw inside a chat app, in dp, so the preview can draw the real thing.
 *
 * The mock input bar in the settings page used to be a guess: a send button that is 64×40 dp on
 * every phone, in every app. Each app's send key is its own size, and a preview drawn against the
 * wrong one is worse than no preview, because a size that looks right there lands wrong in WeChat.
 * So the overlay writes down what it actually found, and the preview replays it.
 */
data class SendAssistMetrics(
    val sendWidthDp: Int,
    val sendHeightDp: Int,
    /** Distance from the screen's right edge to the send button's right edge. */
    val sendRightInsetDp: Int,
    /** Distance from the screen's top edge to the send button's top edge. */
    val sendTopDp: Int,
    val inputHeightDp: Int,
    /** Distance from the screen's right edge to the input field's right edge. */
    val inputRightInsetDp: Int,
    /** The input field's top relative to the send button's top — usually slightly below zero. */
    val inputTopVsSendDp: Int,
    /** Whether the input field was measured, or assumed because the app exposed no editable node. */
    val inputMeasured: Boolean,
    /** Distance from the bar's bottom to the screen's bottom; a keyboard makes this large. */
    val barToBottomDp: Int,
    val screenWidthDp: Int,
    /**
     * The gap actually observed between the button's bottom and the send button's top, in dp, with
     * the user's own offset taken back out.
     *
     * It is normally [SendAssistGeometry.GAP_DP]. Where it is not, the placement that came out is
     * not the placement that was asked for — a window that is laid out in a different coordinate
     * space than the accessibility tree reports, most likely — and the preview has to draw what the
     * phone does rather than what the arithmetic says, or the two disagree and the settings become
     * unusable. Measured with `getLocationOnScreen`, so it is the phone's answer, not a guess; a
     * negative value means the button is overlapping the send key by that much.
     */
    val gapDp: Int,
    /** False until the button has been placed at least once and its real landing spot read back. */
    val gapMeasured: Boolean,
    val capturedAt: Long,
) {
    /** True when the input bar sits on the keyboard rather than on the screen's bottom edge. */
    val keyboardUp: Boolean get() = barToBottomDp > KEYBOARD_THRESHOLD_DP

    /** The gap to place with: the measured one when there is one, the designed one otherwise. */
    val effectiveGapDp: Float
        get() = if (gapMeasured) gapDp.toFloat() else SendAssistGeometry.GAP_DP

    /**
     * How much lower than the design the button really lands, in dp; 0 when it lands as intended.
     *
     * Positive means lower — the button sitting closer to the send key than the 8 dp it was drawn
     * for, which is the difference the preview used to hide.
     */
    val sinkingDp: Int
        get() = if (!gapMeasured) 0 else (SendAssistGeometry.GAP_DP - gapDp).toInt()

    /** "发送键 58×36 dp · 距右 12 dp · 输入框高 44 dp · 3 分钟前" */
    fun summary(now: Long = System.currentTimeMillis()): String = buildString {
        append("发送键 ${sendWidthDp}×${sendHeightDp} dp")
        append(" · 距右 ${sendRightInsetDp} dp")
        append(if (inputMeasured) " · 输入框高 ${inputHeightDp} dp" else " · 输入框按推测画")
        append(" · " + age(now))
    }

    /** How long ago the bar was seen, in words: the numbers are from a moment, not from forever. */
    private fun age(now: Long): String {
        val minutes = ((now - capturedAt).coerceAtLeast(0L) / 60_000L)
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 24 * 60 -> "${minutes / 60} 小时前"
            else -> "${minutes / (24 * 60)} 天前"
        }
    }

    /** Age-independent identity: two measurements differing only in age are the same measurement. */
    fun sameAs(other: SendAssistMetrics?): Boolean =
        other != null && copy(capturedAt = 0L) == other.copy(capturedAt = 0L)

    companion object {
        /** Below this the bar has to be on the screen's bottom edge, not on a keyboard. */
        const val KEYBOARD_THRESHOLD_DP = 60

        private const val ASSUMED_FIELD_HEIGHT_DP = 40
        private const val ASSUMED_FIELD_GAP_DP = 8
        private const val MIN_SCREEN_WIDTH_DP = 200
        private const val MIN_SEND_DP = 12
        private const val MAX_SEND_DP = 400

        /**
         * Measures [send] — and [input], when the app offers one — against the screen.
         *
         * Returns null for anything that cannot be a send button on a phone screen: an empty node,
         * a 4 dp sliver, a 900 dp "button". Bad numbers are worse than no numbers, because the
         * preview would then draw a confident lie.
         */
        fun of(density: Float, screen: Rect, send: Rect, input: Rect?, now: Long): SendAssistMetrics? {
            if (density <= 0f || screen.isEmpty || send.isEmpty) return null
            val field = input?.takeIf { !it.isEmpty }?.takeIf { it.onSameRowAs(send) }

            fun toDp(px: Int): Int = (px / density).roundToInt()

            val screenWidth = toDp(screen.width())
            val sendWidth = toDp(send.width())
            val sendHeight = toDp(send.height())
            if (screenWidth < MIN_SCREEN_WIDTH_DP) return null
            if (sendWidth !in MIN_SEND_DP..MAX_SEND_DP) return null
            if (sendHeight !in MIN_SEND_DP..MAX_SEND_DP) return null

            val sendRightInset = toDp(screen.right - send.right).coerceAtLeast(0)
            return SendAssistMetrics(
                sendWidthDp = sendWidth,
                sendHeightDp = sendHeight,
                sendRightInsetDp = sendRightInset,
                sendTopDp = toDp(send.top - screen.top).coerceAtLeast(0),
                inputHeightDp = field?.let { toDp(it.height()) } ?: ASSUMED_FIELD_HEIGHT_DP,
                inputRightInsetDp = field?.let { toDp(screen.right - it.right) }
                    ?: (sendRightInset + sendWidth + ASSUMED_FIELD_GAP_DP),
                inputTopVsSendDp = field?.let { toDp(it.top - send.top) } ?: 0,
                inputMeasured = field != null,
                barToBottomDp = toDp(screen.bottom - maxOf(send.bottom, field?.bottom ?: send.bottom))
                    .coerceAtLeast(0),
                screenWidthDp = screenWidth,
                // Filled in by the overlay once the button has actually been placed: the gap is a
                // fact about where the window landed, and nothing here knows that yet.
                gapDp = SendAssistGeometry.GAP_DP.toInt(),
                gapMeasured = false,
                capturedAt = now,
            )
        }

        /**
         * Whether an editable node is the message box rather than something else on screen.
         *
         * WeChat's chat list has a search field and no send button at all, and the conversation has
         * both; but a header search field can survive into a conversation, and measuring *that* as
         * the input bar would draw a bar at the top of the screen. The message box shares a row with
         * the send button, so the row is the test — generously, because a taller box is normal.
         */
        private fun Rect.onSameRowAs(send: Rect): Boolean {
            val slack = maxOf(send.height(), height())
            return centerY() in (send.top - slack)..(send.bottom + slack)
        }
    }
}

/**
 * Settings for the send-button assistant: one record per supported app.
 *
 * Its own preference file, so the whole feature can be backed up, cleared or ignored as one thing.
 */
object SendAssistPrefs {
    const val PREFS = "send_assist"

    const val MIN_SIZE_DP = 24
    const val MAX_SIZE_DP = 72
    const val DEFAULT_SIZE_DP = 40
    const val MIN_OPACITY = 30
    const val MAX_OPACITY = 100
    const val DEFAULT_OPACITY = 92

    /** How far the button may be nudged off its default spot, in either direction. */
    const val MAX_OFFSET_DP = 64

    /**
     * The bounds a measured landing gap has to fall inside to be believed.
     *
     * Wide enough for any system inset and for a button that overlaps the send key entirely, narrow
     * enough that a window which reported `0,0` because it had not been laid out yet is rejected.
     */
    private const val MIN_GAP_DP = -200
    private const val MAX_GAP_DP = 200

    private const val KEY_PREFIX = "config:"

    /** Measurements live beside the settings but are written by the overlay, never by the user. */
    private const val METRICS_PREFIX = "metrics:"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Off until asked for: an overlay that appears on its own in a chat app needs consent. */
    fun load(context: Context, packageName: String): SendAssistConfig {
        val raw = prefs(context).getString(KEY_PREFIX + packageName, null) ?: return SendAssistConfig(packageName)
        return runCatching {
            val json = JSONObject(raw)
            SendAssistConfig(
                packageName = packageName,
                enabled = json.optBoolean("enabled", false),
                action = SendAssistAction.from(json.optString("action")).id,
                autoSend = json.optBoolean("autoSend", false),
                sizeDp = json.optInt("size", DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
                cornerDp = json.optInt("corner", -1),
                opacity = json.optInt("opacity", DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY),
                offsetXDp = json.optInt("offsetX", 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP),
                offsetYDp = json.optInt("offsetY", 0).coerceIn(-MAX_OFFSET_DP, MAX_OFFSET_DP),
            )
        }.getOrElse { SendAssistConfig(packageName) }
    }

    fun save(context: Context, config: SendAssistConfig) {
        val json = JSONObject().apply {
            put("enabled", config.enabled)
            put("action", config.action)
            put("autoSend", config.autoSend)
            put("size", config.sizeDp)
            put("corner", config.cornerDp)
            put("opacity", config.opacity)
            put("offsetX", config.offsetXDp)
            put("offsetY", config.offsetYDp)
        }
        prefs(context).edit().putString(KEY_PREFIX + config.packageName, json.toString()).apply()
    }

    /** Whether any app has the assistant switched on; when none has, it is never created at all. */
    fun anyEnabled(context: Context): Boolean =
        SEND_TARGETS.any { load(context, it.packageName).enabled }

    /**
     * What the overlay last measured inside [packageName], or null when it has never been opened.
     *
     * Loaded on the settings screen's composition, so it is validated the same way it is on the way
     * in: a hand-edited or truncated record reads as "never measured" rather than as a broken
     * preview. The overlay is the only writer.
     */
    fun loadMetrics(context: Context, packageName: String): SendAssistMetrics? {
        val raw = prefs(context).getString(METRICS_PREFIX + packageName, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val metrics = SendAssistMetrics(
                sendWidthDp = json.optInt("sendWidth", 0),
                sendHeightDp = json.optInt("sendHeight", 0),
                sendRightInsetDp = json.optInt("sendRightInset", 0),
                sendTopDp = json.optInt("sendTop", 0),
                inputHeightDp = json.optInt("inputHeight", 0),
                inputRightInsetDp = json.optInt("inputRightInset", 0),
                inputTopVsSendDp = json.optInt("inputTopVsSend", 0),
                inputMeasured = json.optBoolean("inputMeasured", false),
                barToBottomDp = json.optInt("barToBottom", 0),
                screenWidthDp = json.optInt("screenWidth", 0),
                gapDp = json.optInt("gap", SendAssistGeometry.GAP_DP.toInt()),
                gapMeasured = json.optBoolean("gapMeasured", false),
                capturedAt = json.optLong("at", 0L),
            )
            if (metrics.screenWidthDp < 200) return null
            if (metrics.sendWidthDp !in 12..400) return null
            if (metrics.sendHeightDp !in 12..400) return null
            if (metrics.gapMeasured && metrics.gapDp !in MIN_GAP_DP..MAX_GAP_DP) return null
            metrics
        }.getOrNull()
    }

    fun saveMetrics(context: Context, packageName: String, metrics: SendAssistMetrics) {
        val json = JSONObject().apply {
            put("sendWidth", metrics.sendWidthDp)
            put("sendHeight", metrics.sendHeightDp)
            put("sendRightInset", metrics.sendRightInsetDp)
            put("sendTop", metrics.sendTopDp)
            put("inputHeight", metrics.inputHeightDp)
            put("inputRightInset", metrics.inputRightInsetDp)
            put("inputTopVsSend", metrics.inputTopVsSendDp)
            put("inputMeasured", metrics.inputMeasured)
            put("barToBottom", metrics.barToBottomDp)
            put("screenWidth", metrics.screenWidthDp)
            put("gap", metrics.gapDp)
            put("gapMeasured", metrics.gapMeasured)
            put("at", metrics.capturedAt)
        }
        prefs(context).edit().putString(METRICS_PREFIX + packageName, json.toString()).apply()
    }

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
}
