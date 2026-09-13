/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.sendassist

import android.graphics.Rect
import android.graphics.RectF

/**
 * Where the assistant button goes, worked out in exactly one place.
 *
 * The settings preview and the real overlay both call [assistantRect]. That is the entire point:
 * a preview that re-implements the placement is a second opinion, and the two drifted apart the
 * first time one of them was right and the other was not.
 */
object SendAssistGeometry {

    /** The gap between the assistant's bottom edge and the send button's top edge, before offsets. */
    const val GAP_DP = 8f

    /**
     * The rect the assistant button occupies.
     *
     * Only two edges of [send] matter: the assistant hangs off the send button's **right edge** and
     * sits [gap] above its **top edge**. Everything else about the send button — how wide it is,
     * how tall — is irrelevant, which is what lets a measured send key and a guessed one produce
     * the same placement.
     *
     * `size`, `gap`, `offsetX`, `offsetY` and [topLimit] must all be in the same unit as [send]:
     * the overlay passes pixels in screen coordinates, the preview passes dp. `offsetX` is positive
     * to the right and `offsetY` positive downwards, on screen.
     *
     * [topLimit] is the smallest top the button may have — `0` on a screen, or a negative number
     * when the y axis starts below the screen's top edge. A chat app's input bar can end up near the
     * top of the screen with the keyboard up, and a button pushed past the top can never be tapped.
     */
    fun assistantRect(
        send: RectF,
        size: Float,
        gap: Float,
        offsetX: Float,
        offsetY: Float,
        topLimit: Float,
    ): RectF {
        val right = send.right + offsetX
        val top = (send.top - gap - size + offsetY).coerceAtLeast(topLimit)
        return RectF(right - size, top, right, top + size)
    }

    /** [assistantRect] with integer pixels, for `WindowManager` and for the debug dump. */
    fun assistantRect(
        send: Rect,
        size: Int,
        gap: Int,
        offsetX: Int,
        offsetY: Int,
        topLimit: Int,
    ): Rect {
        val rect = assistantRect(
            send = RectF(send),
            size = size.toFloat(),
            gap = gap.toFloat(),
            offsetX = offsetX.toFloat(),
            offsetY = offsetY.toFloat(),
            topLimit = topLimit.toFloat(),
        )
        return Rect(
            Math.round(rect.left),
            Math.round(rect.top),
            Math.round(rect.right),
            Math.round(rect.bottom),
        )
    }
}
