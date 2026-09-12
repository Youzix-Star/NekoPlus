/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.floating

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** What tapping one floating button does. */
enum class FloatingAction(val id: String, val label: String) {
    /** Capture the focused field, have the model rewrite it, write it back. */
    AiModify("ai", "AI 修改"),

    /** Capture the focused field and put it on the clipboard. */
    Capture("capture", "复制文本"),

    /** Bring the app to the front. */
    OpenApp("app", "打开应用"),

    /** Take every floating button off the screen. */
    Close("close", "收起悬浮窗"),
    ;

    companion object {
        fun from(id: String?): FloatingAction =
            entries.firstOrNull { it.id == id } ?: AiModify
    }
}

/**
 * The built-in icon set.
 *
 * The icons are drawn as glyphs rather than as drawables on purpose: the overlay is built from
 * plain framework views, and a glyph renders identically in the picker and on the button, so what
 * the settings screen shows is literally what will appear on screen. The original project's set of
 * nine actions is covered, and [Text] switches the button over to a user-typed label instead.
 */
enum class FloatingIcon(val id: String, val glyph: String, val label: String) {
    Sparkle("sparkle", "✨", "AI 改写"),
    Translate("translate", "譯", "翻译"),
    Copy("copy", "⧉", "复制"),
    Rule("rule", "≡", "规则"),
    Bolt("bolt", "⚡", "快捷"),
    Pencil("pencil", "✎", "编辑"),
    Cat("cat", "🐱", "猫"),
    Gear("gear", "⚙", "设置"),
    Home("home", "⌂", "主页"),

    /** Not an icon at all: the button shows [FloatingItem.text]. */
    Text("text", "", "自定义文字"),
    ;

    companion object {
        fun from(id: String?): FloatingIcon = entries.firstOrNull { it.id == id } ?: Sparkle
    }
}

/**
 * One draggable button on the overlay.
 *
 * @param id stable identity, so a drag can be saved without rebuilding the button.
 * @param icon one of [FloatingIcon]'s ids.
 * @param text what the button shows when [icon] is [FloatingIcon.Text].
 * @param action one of [FloatingAction]'s ids.
 * @param sizeDp the button's edge length.
 * @param round whether the button is a circle or a squircle.
 * @param x horizontal offset in raw pixels, as the window manager stores it.
 * @param y vertical offset in raw pixels.
 */
data class FloatingItem(
    val id: String,
    val icon: String,
    val text: String,
    val action: String,
    val sizeDp: Int,
    val round: Boolean,
    val x: Int,
    val y: Int,
) {
    val iconEntry: FloatingIcon get() = FloatingIcon.from(icon)
    val actionEntry: FloatingAction get() = FloatingAction.from(action)

    /** What the button paints: the custom label, or the chosen glyph. */
    val label: String
        get() = if (iconEntry == FloatingIcon.Text) text.ifBlank { "AI" } else iconEntry.glyph
}

/**
 * The floating buttons, stored as JSON because the list is variable length.
 *
 * Everyone who reads this — the overlay itself and both settings screens — goes through here, so
 * there is exactly one definition of what the buttons are.
 */
object FloatingWindowPrefs {
    private const val PREFS = "floating_window"
    private const val KEY_ITEMS = "items"

    const val MIN_SIZE_DP = 36
    const val MAX_SIZE_DP = 88
    const val DEFAULT_SIZE_DP = 56

    /** Where the first button appears, and how far apart fresh ones are stacked. */
    private const val START_X_DP = 24
    private const val START_Y_DP = 320
    private const val ITEM_GAP_DP = 16

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The buttons on screen, always at least one. */
    fun load(context: Context): List<FloatingItem> {
        val raw = prefs(context).getString(KEY_ITEMS, null)
        val parsed = parse(raw)
        return parsed.ifEmpty { default(context) }
    }

    fun save(context: Context, items: List<FloatingItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("icon", item.icon)
                    put("text", item.text)
                    put("action", item.action)
                    put("size", item.sizeDp)
                    put("round", item.round)
                    put("x", item.x)
                    put("y", item.y)
                },
            )
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    /** Persists where a button was dragged to, leaving everything else as it was. */
    fun savePosition(context: Context, id: String, x: Int, y: Int) {
        val items = load(context)
        if (items.none { it.id == id }) return
        save(context, items.map { if (it.id == id) it.copy(x = x, y = y) else it })
    }

    /** The single button a fresh install starts with. */
    fun default(context: Context): List<FloatingItem> = listOf(
        FloatingItem(
            id = "ai",
            icon = FloatingIcon.Sparkle.id,
            text = "",
            action = FloatingAction.AiModify.id,
            sizeDp = DEFAULT_SIZE_DP,
            round = true,
            // Positions are raw pixels — that is what the window manager stores — so the defaults
            // have to be scaled by the display density rather than kept as dp numbers.
            x = (START_X_DP * densityOf(context)).roundToInt(),
            y = (START_Y_DP * densityOf(context)).roundToInt(),
        ),
    )

    /**
     * A new button, placed clear of the ones already on screen and given an icon that has not been
     * used yet, so it is obvious at a glance which button is which.
     */
    fun newItem(existing: List<FloatingItem>, density: Float): FloatingItem {
        val used = existing.map { it.icon }.toSet()
        val icon = FloatingIcon.entries
            .firstOrNull { it != FloatingIcon.Text && it.id !in used }
            ?: FloatingIcon.Bolt
        val stepPx = ((DEFAULT_SIZE_DP + ITEM_GAP_DP) * density).roundToInt()
        return FloatingItem(
            id = "item-" + System.currentTimeMillis().toString(36),
            icon = icon.id,
            text = "",
            action = FloatingAction.AiModify.id,
            sizeDp = DEFAULT_SIZE_DP,
            round = true,
            x = (START_X_DP * density).roundToInt(),
            y = (START_Y_DP * density).roundToInt() + existing.size * stepPx,
        )
    }

    private fun densityOf(context: Context): Float =
        context.resources.displayMetrics.density

    /** True when [key] is the button list, i.e. the overlay must be rebuilt. */
    fun isFloatingKey(key: String?): Boolean = key == KEY_ITEMS

    /** Observable access for the overlay, which outlives the activity. Pair with [unregister]. */
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

    private fun parse(raw: String?): List<FloatingItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optString("id")
                if (id.isBlank()) return@mapNotNull null
                FloatingItem(
                    id = id,
                    icon = json.optString("icon", FloatingIcon.Sparkle.id),
                    text = json.optString("text", ""),
                    action = json.optString("action", FloatingAction.AiModify.id),
                    sizeDp = json.optInt("size", DEFAULT_SIZE_DP)
                        .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
                    round = json.optBoolean("round", true),
                    x = json.optInt("x", 24),
                    y = json.optInt("y", 320),
                )
            }
        }.getOrDefault(emptyList())
    }
}
