/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * The step chain, which is the piece of upstream's StateMachine that survives the port: one step at
 * a time, and no wrapping at either end.
 */

package top.youzix.nekoplus.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideFlowTest {

    private val flow = GuideFlow(stepCount = 5)

    @Test
    fun `forward walks the chain one step at a time`() {
        assertEquals(1, flow.forward(0))
        assertEquals(2, flow.forward(1))
        assertEquals(4, flow.forward(3))
    }

    @Test
    fun `forward stops at the last step instead of wrapping round`() {
        assertNull(flow.forward(4))
    }

    @Test
    fun `backward stops at the first step instead of wrapping round`() {
        assertEquals(3, flow.backward(4))
        assertEquals(0, flow.backward(1))
        assertNull(flow.backward(0))
    }

    @Test
    fun `first and last follow the index`() {
        assertTrue(flow.isFirst(0))
        assertFalse(flow.isFirst(1))
        assertTrue(flow.isLast(4))
        assertFalse(flow.isLast(3))
        assertEquals(5, flow.size)
    }
}
