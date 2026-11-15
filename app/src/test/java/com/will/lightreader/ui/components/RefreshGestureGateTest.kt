package com.will.lightreader.ui.components

import org.junit.Assert.*
import org.junit.Test

class RefreshGestureGateTest {
    @Test fun `scroll beginning below top cannot arm refresh when reaching top`() {
        val gate = RefreshGestureGate(); gate.start(atTop = false, busy = false)
        gate.move(0f, 200f, 8f)
        assertFalse(gate.accepts(atTop = true, busy = false))
    }
    @Test fun `fresh downward gesture at top may refresh until release`() {
        val gate = RefreshGestureGate(); gate.start(true, false); gate.move(1f, 30f, 8f)
        assertTrue(gate.accepts(true, false))
        assertFalse(gate.accepts(false, false)); assertFalse(gate.accepts(true, true))
    }
    @Test fun `initial upward or horizontal drag stays ineligible even after reversing`() {
        for ((x,y) in listOf(0f to -20f, 30f to 2f)) {
            val gate = RefreshGestureGate(); gate.start(true, false); gate.move(x,y,8f); gate.move(0f,200f,8f)
            assertFalse(gate.accepts(true,false))
        }
    }
    @Test fun `busy start remains blocked and a new idle gesture resets the decision`() {
        val gate = RefreshGestureGate(); gate.start(true,true);gate.move(0f,20f,8f)
        assertFalse(gate.accepts(true,false))
        gate.start(true,false);gate.move(0f,20f,8f);assertTrue(gate.accepts(true,false))
    }
}
