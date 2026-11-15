package com.will.lightreader.ui.reader

import kotlin.math.abs
import kotlin.math.sign

/** Zero means menu; zoomed images reserve every tap for their viewing controls. */
internal fun readerTapDirection(xFraction: Float, rtl: Boolean = false, zoomed: Boolean = false): Int {
    if (!xFraction.isFinite() || zoomed) return 0
    val direction = when {
        xFraction < .28f -> -1
        xFraction > .72f -> 1
        else -> 0
    }
    return if (rtl) -direction else direction
}

/** Release velocity wins over distance, so a quick reversal can put the page back. */
internal fun commitPageTurn(distance: Float, velocity: Float, width: Float, density: Float): Boolean {
    if (width <= 0f || !distance.isFinite() || !velocity.isFinite()) return false
    if (abs(distance) < 12f * density) return false
    if (abs(velocity) >= 650f * density) return sign(velocity) == sign(distance)
    return abs(distance) >= width * .18f
}

internal fun pageTurnDuration(progress: Float, commit: Boolean): Int {
    val remaining = if (commit) 1f - progress else progress
    return (100 + 140 * remaining.coerceIn(0f, 1f)).toInt()
}
