/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.floating

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import love.miao.yun.ui.FloatingPalette
import kotlin.math.roundToInt

/**
 * One floating button's views, and the painting of them.
 *
 * Shared by the floating window and the send-button assistant, which are the same object in two
 * places: a coloured shape with a Material icon, a typed label, or a spinner on it. Both used to
 * build and paint those views themselves, which is how two renderers drift apart — the assistant
 * had its own icon inset, its own corner handling and its own idea of what a busy button looks like.
 *
 * Framework views rather than Compose, on purpose: an overlay window has no themed context and no
 * view-tree owner, and Material components inside one are a recurring source of inflation crashes.
 */
class FloatingButtonViews(
    val container: FrameLayout,
    val background: GradientDrawable,
    val icon: ImageView,
    val label: TextView,
    val spinner: ProgressBar,
) {
    /** True while this button's chain of actions runs, so its face can show progress instead. */
    var busy: Boolean = false
}

/** Share of the shorter edge the icon leaves as padding, so the artwork is about 55% of it. */
private const val ICON_INSET_FRACTION = 0.225f

/** The spinner's edge as a share of the shorter edge, so it stays round on a strip. */
private const val SPINNER_EDGE_FRACTION = 0.55f

/** Camera height for the drop shadow, in dp. */
private const val ELEVATION_DP = 6f

/** Builds the views. They are unpositioned and unpainted until [applyStyle] and [applyPalette]. */
fun floatingButtonViews(context: Context, density: Float): FloatingButtonViews {
    // Named apart from the view's own `background` property: inside the `apply` below, a local
    // called `background` would be shadowed by the receiver's, and the click of a mistake is a
    // button with no pill behind it at all.
    val shape = GradientDrawable()

    val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    val label = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
    }
    // The framework's indeterminate spinner: the same "something is happening" affordance the two
    // UI engines use, in the one form an overlay window can host.
    val spinner = ProgressBar(context).apply {
        isIndeterminate = true
        visibility = View.GONE
    }

    val container = FrameLayout(context).apply {
        addView(
            icon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        addView(
            spinner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        background = shape
        elevation = ELEVATION_DP * density
    }

    return FloatingButtonViews(
        container = container,
        background = shape,
        icon = icon,
        label = label,
        spinner = spinner,
    )
}

/** Applies everything about a button that comes from its own settings. */
fun FloatingButtonViews.applyStyle(item: FloatingItem, density: Float) {
    val shortEdgePx = (item.shortEdgeDp * density).roundToInt()
    background.cornerRadius = item.effectiveCornerDp * density

    val spinnerEdge = (shortEdgePx * SPINNER_EDGE_FRACTION).roundToInt()
    spinner.layoutParams = (spinner.layoutParams as FrameLayout.LayoutParams).apply {
        width = spinnerEdge
        height = spinnerEdge
    }

    when {
        busy -> {
            icon.visibility = View.GONE
            label.visibility = View.GONE
            spinner.visibility = View.VISIBLE
        }

        item.showsText -> {
            spinner.visibility = View.GONE
            icon.visibility = View.GONE
            label.visibility = View.VISIBLE
            val text = item.label
            label.text = text
            label.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                when (text.length) {
                    1 -> item.shortEdgeDp * 0.44f
                    2 -> item.shortEdgeDp * 0.34f
                    else -> item.shortEdgeDp * 0.25f
                },
            )
        }

        item.iconVisible -> {
            spinner.visibility = View.GONE
            label.visibility = View.GONE
            icon.visibility = View.VISIBLE
            icon.setImageResource(item.iconEntry.res)
            // Inset rather than resized: the artwork keeps its own aspect, and the padding stays in
            // step with the button at any size.
            val inset = (shortEdgePx * ICON_INSET_FRACTION).roundToInt()
            icon.setPadding(inset, inset, inset, inset)
        }

        else -> {
            // A strip with no room for square art: leave it blank rather than let the icon squeeze
            // out of shape. Text mode is the way to label a strip.
            spinner.visibility = View.GONE
            icon.visibility = View.GONE
            label.visibility = View.GONE
        }
    }
}

/** Applies the colour source and the button's own opacity. */
fun FloatingButtonViews.applyPalette(palette: FloatingPalette, opacity: Int) {
    background.setColor(palette.container)
    container.alpha = opacity / 100f
    icon.setColorFilter(palette.onContainer, PorterDuff.Mode.SRC_IN)
    label.setTextColor(palette.onContainer)
    spinner.indeterminateTintList = ColorStateList.valueOf(palette.onContainer)
}
