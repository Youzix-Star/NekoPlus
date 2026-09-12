/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.floating

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import love.miao.yun.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** What tapping — or holding — one floating button does. */
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
        fun from(id: String?): FloatingAction = entries.firstOrNull { it.id == id } ?: AiModify

        /** `null` for "no action", which is what a hold gesture is left at by default. */
        fun fromOrNull(id: String?): FloatingAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The built-in icon set, taken from Material Symbols Rounded.
 *
 * These are vector **drawables**, not glyphs: a drawable is the one form both Compose
 * (`painterResource`) and the plain-View overlay (`setImageResource`) can draw, so the picker in
 * the settings shows literally the same artwork that ends up on the button. [Text] switches the
 * button over to a label the user types instead.
 *
 * @param label what the picker calls it; the button itself only draws the art.
 */
enum class FloatingIcon(val id: String, val label: String, @DrawableRes val res: Int) {
    AutoAwesome("auto_awesome", "智能改写", R.drawable.ic_ball_auto_awesome),
    Copy("content_copy", "复制", R.drawable.ic_ball_content_copy),
    Translate("translate", "翻译", R.drawable.ic_ball_translate),
    Rule("rule", "规则", R.drawable.ic_ball_rule),
    Edit("edit", "编辑", R.drawable.ic_ball_edit),
    Search("search", "搜索", R.drawable.ic_ball_search),
    Mic("mic", "语音", R.drawable.ic_ball_mic),
    Bolt("bolt", "快捷", R.drawable.ic_ball_bolt),
    Star("star", "收藏", R.drawable.ic_ball_star),
    Home("home", "首页", R.drawable.ic_ball_home),
    Settings("settings", "设置", R.drawable.ic_ball_settings),
    Palette("palette", "外观", R.drawable.ic_ball_palette),
    OpenInNew("open_in_new", "打开应用", R.drawable.ic_ball_open_in_new),
    Close("close", "收起", R.drawable.ic_ball_close),
    DarkMode("dark_mode", "深色", R.drawable.ic_ball_dark_mode),

    /** Not an icon at all: the button shows [FloatingItem.text] instead. */
    Text("text", "自定义文字", 0),
    ;

    val isText: Boolean get() = this == Text

    companion object {
        /** Ids an earlier build stored, so an existing button keeps the icon it was given. */
        private val ALIASES = mapOf(
            "sparkle" to AutoAwesome,
            "copy" to Copy,
            "pencil" to Edit,
            "gear" to Settings,
            "cat" to Star,
        )

        fun from(id: String?): FloatingIcon =
            entries.firstOrNull { it.id == id }
                ?: ALIASES[id]
                ?: AutoAwesome
    }
}

/**
 * One draggable button on the overlay.
 *
 * @param id stable identity, so a drag can be saved without rebuilding the button.
 * @param icon one of [FloatingIcon]'s ids.
 * @param text the label, used when [icon] is [FloatingIcon.Text].
 * @param action one of [FloatingAction]'s ids; what a tap does.
 * @param holdAction one of [FloatingAction]'s ids, or empty for nothing; what a long press does.
 * @param sizeDp the button's edge length.
 * @param cornerDp corner radius; half of [sizeDp] is a circle.
 * @param opacity how opaque the button is, in percent.
 * @param x horizontal offset in raw pixels, as the window manager stores it.
 * @param y vertical offset in raw pixels.
 */
data class FloatingItem(
    val id: String,
    val icon: String,
    val text: String,
    val action: String,
    val holdAction: String,
    val sizeDp: Int,
    val cornerDp: Int,
    val opacity: Int,
    val x: Int,
    val y: Int,
) {
    val iconEntry: FloatingIcon get() = FloatingIcon.from(icon)
    val actionEntry: FloatingAction get() = FloatingAction.from(action)
    val holdActionEntry: FloatingAction? get() = FloatingAction.fromOrNull(holdAction)

    /** True when the button draws a typed label rather than an icon. */
    val showsText: Boolean get() = iconEntry.isText

    /** What a typed-label button paints; falls back so it can never be invisible. */
    val label: String get() = text.ifBlank { "AI" }

    /** The corner radius actually allowed for this button's size. */
    val effectiveCornerDp: Int get() = cornerDp.coerceIn(0, sizeDp / 2)

    /** How the button looks in a list: its icon name, or the label it shows. */
    val displayName: String
        get() = if (showsText) label else iconEntry.label
}

/**
 * How the overlay behaves, as opposed to how one button looks.
 *
 * @param snapToEdge pull a button to the nearest side after a drag.
 * @param dragHaptic tick when a drag starts.
 */
data class FloatingOptions(
    val snapToEdge: Boolean = true,
    val dragHaptic: Boolean = false,
)

/**
 * The floating buttons, stored as JSON because the list is variable length.
 *
 * Everyone who reads this — the overlay itself and both settings screens — goes through here, so
 * there is exactly one definition of what the buttons are.
 */
object FloatingWindowPrefs {
    private const val PREFS = "floating_window"
    private const val KEY_ITEMS = "items"
    private const val KEY_SNAP = "snap_to_edge"
    private const val KEY_HAPTIC = "drag_haptic"

    const val MIN_SIZE_DP = 36
    const val MAX_SIZE_DP = 88
    const val DEFAULT_SIZE_DP = 56
    const val MIN_OPACITY = 30
    const val MAX_OPACITY = 100

    /** Where the first button appears, and how far apart fresh ones are stacked. */
    private const val START_X_DP = 24
    private const val START_Y_DP = 320
    private const val ITEM_GAP_DP = 16

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The buttons on screen, always at least one. */
    fun load(context: Context): List<FloatingItem> {
        val parsed = parse(prefs(context).getString(KEY_ITEMS, null))
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
                    put("hold", item.holdAction)
                    put("size", item.sizeDp)
                    put("corner", item.cornerDp)
                    put("opacity", item.opacity)
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

    fun loadOptions(context: Context): FloatingOptions {
        val store = prefs(context)
        return FloatingOptions(
            // Snapping defaults on: a button that never settles against an edge drifts into the
            // middle of whatever the user is reading.
            snapToEdge = store.getBoolean(KEY_SNAP, true),
            dragHaptic = store.getBoolean(KEY_HAPTIC, false),
        )
    }

    fun saveOptions(context: Context, options: FloatingOptions) {
        prefs(context).edit()
            .putBoolean(KEY_SNAP, options.snapToEdge)
            .putBoolean(KEY_HAPTIC, options.dragHaptic)
            .apply()
    }

    /** The single button a fresh install starts with. */
    fun default(context: Context): List<FloatingItem> {
        val density = densityOf(context)
        return listOf(
            FloatingItem(
                id = "ai",
                icon = FloatingIcon.AutoAwesome.id,
                text = "",
                action = FloatingAction.AiModify.id,
                holdAction = "",
                sizeDp = DEFAULT_SIZE_DP,
                cornerDp = DEFAULT_SIZE_DP / 2,
                opacity = 100,
                // Positions are raw pixels — that is what the window manager stores — so the
                // defaults have to be scaled by the display density rather than kept as dp.
                x = (START_X_DP * density).roundToInt(),
                y = (START_Y_DP * density).roundToInt(),
            ),
        )
    }

    /**
     * A new button, placed clear of the ones already on screen and given an icon that has not been
     * used yet, so it is obvious at a glance which button is which.
     */
    fun newItem(existing: List<FloatingItem>, density: Float): FloatingItem {
        val used = existing.map { it.icon }.toSet()
        val icon = FloatingIcon.entries
            .firstOrNull { !it.isText && it.id !in used && it != FloatingIcon.AutoAwesome }
            ?: FloatingIcon.Bolt
        val stepPx = ((DEFAULT_SIZE_DP + ITEM_GAP_DP) * density).roundToInt()
        return FloatingItem(
            id = "item-" + System.currentTimeMillis().toString(36),
            icon = icon.id,
            text = "",
            action = FloatingAction.AiModify.id,
            holdAction = "",
            sizeDp = DEFAULT_SIZE_DP,
            cornerDp = DEFAULT_SIZE_DP / 2,
            opacity = 100,
            x = (START_X_DP * density).roundToInt(),
            y = (START_Y_DP * density).roundToInt() + existing.size * stepPx,
        )
    }

    /** Which palette the overlay paints itself with; persisted next to the engine choice. */
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

    private fun densityOf(context: Context): Float =
        context.resources.displayMetrics.density

    private fun parse(raw: String?): List<FloatingItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optString("id")
                if (id.isBlank()) return@mapNotNull null
                val size = json.optInt("size", DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
                // `round` is what an earlier build stored; a button saved back then keeps the
                // shape it was given instead of jumping to a default.
                val corner = if (json.has("corner")) {
                    json.optInt("corner", size / 2)
                } else {
                    if (json.optBoolean("round", true)) size / 2 else size / 3
                }
                FloatingItem(
                    id = id,
                    icon = FloatingIcon.from(json.optString("icon")).id,
                    text = json.optString("text", ""),
                    action = FloatingAction.from(json.optString("action")).id,
                    holdAction = json.optString("hold", ""),
                    sizeDp = size,
                    cornerDp = corner.coerceIn(0, size / 2),
                    opacity = json.optInt("opacity", MAX_OPACITY)
                        .coerceIn(MIN_OPACITY, MAX_OPACITY),
                    x = json.optInt("x", 24),
                    y = json.optInt("y", 320),
                )
            }
        }.getOrDefault(emptyList())
    }
}
