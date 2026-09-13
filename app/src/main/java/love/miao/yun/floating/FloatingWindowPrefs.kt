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

    /**
     * Write every window and node the accessibility service can see to a file.
     *
     * For the app that will not co-operate: it has to be pressed while that app is on screen, so
     * it belongs on a floating button rather than in a settings page.
     */
    DumpUi("dump", "导出界面元素"),
    ;

    companion object {
        fun from(id: String?): FloatingAction = entries.firstOrNull { it.id == id } ?: AiModify

        /** `null` for "no action", which is what a hold gesture is left at by default. */
        fun fromOrNull(id: String?): FloatingAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The built-in icon set: Material Icons (Rounded), from google/material-design-icons.
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
 * Ready-made shapes.
 *
 * Width and height are independent, which is what makes a thin strip along a screen edge possible;
 * these are the combinations worth one tap, because dragging two sliders to line them up is not.
 */
enum class FloatingShape(
    val label: String,
    val widthDp: Int,
    val heightDp: Int,
    /** Corner radius as a share of the shorter edge; 0.5 is a capsule. */
    val cornerRatio: Float,
) {
    Circle("圆形", 56, 56, 0.5f),
    Rounded("方形", 56, 56, 0.2f),
    BarHorizontal("横条", 132, 40, 0.5f),
    BarVertical("竖条", 40, 132, 0.5f),
    ;

    /** Applies this shape to an item, keeping its position and everything else. */
    fun apply(item: FloatingItem): FloatingItem {
        val shorter = minOf(widthDp, heightDp)
        return item.copy(
            widthDp = widthDp,
            heightDp = heightDp,
            cornerDp = (shorter * cornerRatio).roundToInt(),
        )
    }

    companion object {
        /** The shape an item currently matches, or `null` when it is a custom size. */
        fun of(item: FloatingItem): FloatingShape? =
            entries.firstOrNull { it.widthDp == item.widthDp && it.heightDp == item.heightDp }
    }
}

/**
 * One draggable button on the overlay.
 *
 * @param id stable identity, so a drag can be saved without rebuilding the button.
 * @param enabled whether the button is on screen at all; a button can be parked without being
 *   deleted, and it keeps every setting while it is away.
 * @param icon one of [FloatingIcon]'s ids.
 * @param showIcon whether to draw [icon]; drawing it is the user's call, never inferred, because
 *   only they know whether a small or oddly shaped one looks right to them.
 * @param text the label, used when [icon] is [FloatingIcon.Text].
 * @param actions one of [FloatingAction]'s ids each; what a tap runs, in order.
 * @param holdActions the same for a long press; empty means a long press does nothing.
 * @param widthDp the button's width; independent of [heightDp], so it can be a strip.
 * @param heightDp the button's height.
 * @param cornerDp corner radius; half of the shorter edge is a capsule.
 * @param opacity how opaque the button is, in percent.
 * @param x horizontal offset in raw pixels, as the window manager stores it.
 * @param y vertical offset in raw pixels.
 */
data class FloatingItem(
    val id: String,
    val enabled: Boolean,
    val icon: String,
    val showIcon: Boolean,
    val text: String,
    val actions: List<String>,
    val holdActions: List<String>,
    val widthDp: Int,
    val heightDp: Int,
    val cornerDp: Int,
    val opacity: Int,
    val x: Int,
    val y: Int,
) {
    val iconEntry: FloatingIcon get() = FloatingIcon.from(icon)

    /** The tap chain; never empty, so a button always does something. */
    val actionEntries: List<FloatingAction>
        get() = actions.mapNotNull { FloatingAction.fromOrNull(it) }
            .ifEmpty { listOf(FloatingAction.AiModify) }

    /** The hold chain; empty when a long press is not wired up. */
    val holdActionEntries: List<FloatingAction>
        get() = holdActions.mapNotNull { FloatingAction.fromOrNull(it) }

    /** True when the button draws a typed label rather than an icon. */
    val showsText: Boolean get() = iconEntry.isText

    /** What a typed-label button paints; falls back so it can never be invisible. */
    val label: String get() = text.ifBlank { "AI" }

    val shortEdgeDp: Int get() = minOf(widthDp, heightDp)
    val longEdgeDp: Int get() = maxOf(widthDp, heightDp)

    /** The corner radius actually allowed at this size. */
    val effectiveCornerDp: Int get() = cornerDp.coerceIn(0, shortEdgeDp / 2)

    /** Whether the icon is actually painted: the user's switch, and never on a text button. */
    val iconVisible: Boolean get() = showIcon && !showsText

    /**
     * Whether this size leaves the square artwork uncomfortably squeezed.
     *
     * Only ever a hint for the editor — the decision to hide it stays with the user, because a
     * small icon on a strip is a perfectly reasonable look and only they can judge it.
     */
    val iconCramped: Boolean
        get() = shortEdgeDp < FloatingWindowPrefs.MIN_ICON_EDGE_DP ||
            longEdgeDp > (shortEdgeDp * FloatingWindowPrefs.ICON_MAX_ASPECT).roundToInt()

    /** "AI 修改 → 复制文本" */
    val actionSummary: String get() = actionEntries.joinToString(" → ") { it.label }

    /** "" when a long press is not wired up. */
    val holdActionSummary: String get() = holdActionEntries.joinToString(" → ") { it.label }

    /** How the button looks in a list: its icon name, or the label it shows. */
    val displayName: String
        get() = if (showsText) label else iconEntry.label

    /** "132×40 dp · 胶囊 · 100% · 不显示图标" */
    val summary: String
        get() {
            val shape = when {
                effectiveCornerDp == 0 -> "直角"
                effectiveCornerDp >= shortEdgeDp / 2 -> "胶囊"
                else -> "${cornerDp} dp 圆角"
            }
            val icon = if (iconVisible) "" else " · 不显示图标"
            return "${widthDp}×${heightDp} dp · $shape · $opacity%$icon"
        }
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

    /** Each edge is set on its own, so the range covers a small chip and a screen-edge strip. */
    const val MIN_SIZE_DP = 24
    const val MAX_SIZE_DP = 160
    const val DEFAULT_SIZE_DP = 56
    const val MIN_OPACITY = 30
    const val MAX_OPACITY = 100

    /** Below this shorter edge an icon is unreadable. */
    const val MIN_ICON_EDGE_DP = 30

    /** Past this long-to-short ratio the button counts as a strip; used for the editor's hint. */
    const val ICON_MAX_ASPECT = 2.2f

    /** How many steps one button's tap and hold chains may hold. */
    const val MAX_CHAIN = 3

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
                    put("enabled", item.enabled)
                    put("icon", item.icon)
                    put("showIcon", item.showIcon)
                    put("text", item.text)
                    put("actions", JSONArray(item.actions))
                    put("holds", JSONArray(item.holdActions))
                    put("width", item.widthDp)
                    put("height", item.heightDp)
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
                enabled = true,
                icon = FloatingIcon.AutoAwesome.id,
                showIcon = true,
                text = "",
                actions = listOf(FloatingAction.AiModify.id),
                holdActions = emptyList(),
                widthDp = DEFAULT_SIZE_DP,
                heightDp = DEFAULT_SIZE_DP,
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
            enabled = true,
            icon = icon.id,
            showIcon = true,
            text = "",
            actions = listOf(FloatingAction.AiModify.id),
            holdActions = emptyList(),
            widthDp = DEFAULT_SIZE_DP,
            heightDp = DEFAULT_SIZE_DP,
            cornerDp = DEFAULT_SIZE_DP / 2,
            opacity = 100,
            x = (START_X_DP * density).roundToInt(),
            y = (START_Y_DP * density).roundToInt() + existing.size * stepPx,
        )
    }

    /**
     * Where the button at [index] goes when its position is reset: the same staggered column a
     * fresh button is born into.
     */
    fun startPosition(context: Context, index: Int): Pair<Int, Int> {
        val density = densityOf(context)
        val step = ((DEFAULT_SIZE_DP + ITEM_GAP_DP) * density).roundToInt()
        return (START_X_DP * density).roundToInt() to
            ((START_Y_DP * density).roundToInt() + index * step)
    }

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

    private fun densityOf(context: Context): Float =
        context.resources.displayMetrics.density

    /** Reads a stored chain, dropping anything no longer recognised. */
    private fun readChain(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optString(it).takeIf { id -> FloatingAction.fromOrNull(id) != null } }
            .take(MAX_CHAIN)
    }

    private fun parse(raw: String?): List<FloatingItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optString("id")
                if (id.isBlank()) return@mapNotNull null
                // `size` is what an earlier build stored, before the two edges were split.
                val legacySize = json.optInt("size", DEFAULT_SIZE_DP)
                    .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
                val width = json.optInt("width", legacySize).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
                val height = json.optInt("height", legacySize).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
                val shorter = minOf(width, height)
                // `round` is what the first build stored; a button saved back then keeps the
                // shape it was given instead of jumping to a default.
                val corner = if (json.has("corner")) {
                    json.optInt("corner", shorter / 2)
                } else {
                    if (json.optBoolean("round", true)) shorter / 2 else shorter / 3
                }
                FloatingItem(
                    id = id,
                    enabled = json.optBoolean("enabled", true),
                    icon = FloatingIcon.from(json.optString("icon")).id,
                    showIcon = json.optBoolean("showIcon", true),
                    text = json.optString("text", ""),
                    // `action` / `hold` are what the first build stored: one action each, before
                    // a button could run a chain.
                    actions = readChain(json.optJSONArray("actions"))
                        .ifEmpty { listOf(FloatingAction.from(json.optString("action")).id) },
                    holdActions = readChain(json.optJSONArray("holds"))
                        .ifEmpty {
                            listOfNotNull(
                                FloatingAction.fromOrNull(json.optString("hold"))?.id,
                            )
                        },
                    widthDp = width,
                    heightDp = height,
                    cornerDp = corner.coerceIn(0, shorter / 2),
                    opacity = json.optInt("opacity", MAX_OPACITY)
                        .coerceIn(MIN_OPACITY, MAX_OPACITY),
                    x = json.optInt("x", 24),
                    y = json.optInt("y", 320),
                )
            }
        }.getOrDefault(emptyList())
    }
}
