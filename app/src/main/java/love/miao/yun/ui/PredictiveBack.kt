/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0
 */

package love.miao.yun.ui

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp

/** AOSP's cross-activity predictive back never shrinks the window below this. */
internal const val AOSP_MIN_SCALE = 0.9f

/** AOSP keeps this much of the screen visible around the scaled window. */
internal val AOSP_EDGE_MARGIN = 8.dp

/** AOSP's back-gesture shaping curve. */
internal val BackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * Applies AOSP's cross-activity predictive back transform.
 *
 * The window shrinks to [AOSP_MIN_SCALE], hugs the edge the gesture came from while keeping
 * [AOSP_EDGE_MARGIN] of the screen visible, and follows the finger vertically with a damped curve,
 * so the content tracks both axes of the gesture the way the platform transition does.
 *
 * Shared by both UI engines so they feel identical during a back gesture.
 */
internal fun GraphicsLayerScope.aospPredictiveBack(
    progress: Float,
    swipeEdge: Int,
    touchDeltaY: Float,
) {
    if (progress <= 0f) {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        return
    }

    val shaped = 1f - BackGestureEasing.transform(progress.coerceIn(0f, 1f))
    val scale = AOSP_MIN_SCALE + (1f - AOSP_MIN_SCALE) * shaped
    val marginPx = AOSP_EDGE_MARGIN.toPx()
    val widthPx = size.width
    val heightPx = size.height

    scaleX = scale
    scaleY = scale

    val hugMax = ((widthPx - widthPx * scale) / 2f - marginPx).coerceAtLeast(0f)
    val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    translationX = direction * (1f - shaped) * hugMax

    val half = heightPx / 2f
    translationY = if (half > 0f) {
        val ratio = touchDeltaY.absoluteCoerceAtMost(half) / half
        val damped = 1f - (1f - ratio) * (1f - ratio)
        val maxShift = ((heightPx - heightPx * scale) / 2f - marginPx).coerceAtLeast(0f)
        maxShift * damped * (if (touchDeltaY < 0f) -1f else 1f)
    } else {
        0f
    }
}

private fun Float.absoluteCoerceAtMost(max: Float): Float =
    if (this < 0f) (-this).coerceAtMost(max) else coerceAtMost(max)
