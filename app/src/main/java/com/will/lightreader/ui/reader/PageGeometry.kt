package com.will.lightreader.ui.reader

import kotlin.math.min

/** Clamp against the fitted image bounds rather than the entire viewport's empty margins. */
fun pagePanLimits(viewWidth: Float, viewHeight: Float, imageWidth: Float, imageHeight: Float, zoom: Float): Pair<Float, Float> {
    if (imageWidth <= 0 || imageHeight <= 0) return 0f to 0f
    val fit = min(viewWidth / imageWidth, viewHeight / imageHeight)
    return ((imageWidth * fit * zoom - viewWidth) / 2).coerceAtLeast(0f) to
        ((imageHeight * fit * zoom - viewHeight) / 2).coerceAtLeast(0f)
}
