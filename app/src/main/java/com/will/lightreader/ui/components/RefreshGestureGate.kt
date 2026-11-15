package com.will.lightreader.ui.components

import kotlin.math.abs
import kotlin.math.hypot

/** Eligibility is latched at pointer down; reaching the top mid-scroll cannot arm refresh. */
class RefreshGestureGate {
    var allowed = false
        private set
    private var x = 0f
    private var y = 0f
    private var directionChosen = false

    fun start(atTop: Boolean, busy: Boolean) {
        allowed = atTop && !busy
        x = 0f; y = 0f; directionChosen = false
    }

    fun move(dx: Float, dy: Float, slop: Float) {
        x += dx; y += dy
        if (!directionChosen && hypot(x, y) > slop) {
            allowed = allowed && y > 0f && y > abs(x)
            directionChosen = true
        }
    }

    fun accepts(atTop: Boolean, busy: Boolean): Boolean = allowed && atTop && !busy
}
