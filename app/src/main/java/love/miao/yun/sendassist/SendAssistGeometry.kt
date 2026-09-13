/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 */

package love.miao.yun.sendassist

import android.graphics.Rect
import android.graphics.RectF

/**
 * Where the assistant button goes: the one definition, used by the overlay that draws it on the
 * phone and by the diagram in the settings page.
 *
 * The anchor is the send button's **top-right corner**. The assistant's right edge lines up with the
 * send button's right edge and its **bottom edge rests on the send button's top edge** — 0 dp means
 * touching, which is the look the button kept ending up with on a real phone, so it is what the
 * number now means instead of pretending to be 8 dp of clearance.
 *
 * `offsetX` is positive to the right and `offsetY` positive downwards, in the same unit as [send].
 * [topLimit] is the highest the button may get: `0` on a screen, or negative when the y axis starts
 * below the screen's top edge. A chat app's input bar can end up near the top with the keyboard up,
 * and a button pushed past the top can never be tapped.
 */
object SendAssistGeometry {

    fun assistantRect(
        send: Rect,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int,
        topLimit: Int,
    ): Rect {
        val right = send.right + offsetX
        val top = (send.top - height + offsetY).coerceAtLeast(topLimit)
        return Rect(right - width, top, right, top + height)
    }

    /** The same placement in dp, for the settings diagram, which has no pixels to work with. */
    fun assistantRect(
        send: RectF,
        width: Float,
        height: Float,
        offsetX: Float,
        offsetY: Float,
        topLimit: Float,
    ): RectF {
        val right = send.right + offsetX
        val top = (send.top - height + offsetY).coerceAtLeast(topLimit)
        return RectF(right - width, top, right, top + height)
    }
}
