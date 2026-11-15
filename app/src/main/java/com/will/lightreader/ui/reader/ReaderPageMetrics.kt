package com.will.lightreader.ui.reader

/** Geometry follows the available window, never the device name or orientation flag. */
internal data class ReaderPageMetrics(
    val twoPage: Boolean, val contentWidthDp: Float, val sidePaddingDp: Float, val gutterDp: Float,
    val topPaddingDp: Float, val bottomPaddingDp: Float,
    val compactHeading: Boolean, val titleSizeSp: Float,
) {
    val titleLines: Int get() = if (compactHeading) 1 else 2
    val headingSpacingDp: Float get() = if (compactHeading) 21f else 71f
}

internal fun readerPageMetrics(widthDp: Float, heightDp: Float, fontSizeSp: Int, fontScale: Float,
    requestedTwoPage: Boolean, navigationBottomDp: Float = 0f, compactFooter:Boolean?=null): ReaderPageMetrics {
    val width = widthDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = heightDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val font = fontSizeSp.coerceIn(13, 32).toFloat()
    val two = requestedTwoPage && width >= 840f && height >= 480f && width >= font * scale * 30f + 80f
    val compact = height < 480f
    val side = if (two) 16f else (when {
        width < 360f -> 14f
        width < 600f -> 20f
        width < 840f -> 28f
        compact -> 32f
        else -> 40f
    }).coerceAtMost(width * .1f)
    val contentWidth = if(two)minOf(width,1080f) else width
    // The persistent direct slider reserves a 48dp touch target, title and outer/inner padding.
    val footer = readerFooterClearanceDp(scale,navigationBottomDp,compactFooter ?: (compact && width>height))
    return ReaderPageMetrics(two, contentWidth, side, if (two) 48f else 0f,
        if (compact) 12f else 28f, maxOf(if (compact) 64f else 76f, footer), compact,
        if (two) font * 1.72f else if (compact) (font * 1.3f).coerceIn(18f, 28f) else (font * 1.55f).coerceIn(22f, 38f))
}

/** The empty margins and the book gutter are not paragraphs. */
internal fun readerColumnAt(x: Float, viewportWidth: Float, contentWidth: Float, padding: Float,
    gutter: Float, twoPage: Boolean): Int? {
    val left = (viewportWidth - contentWidth) / 2f + padding
    val right = (viewportWidth + contentWidth) / 2f - padding
    if (!x.isFinite() || x < left || x > right) return null
    if (!twoPage) return 0
    val middle = viewportWidth / 2f
    if (x > middle - gutter / 2f && x < middle + gutter / 2f) return null
    return if (x < middle) 0 else 1
}

internal fun readerFooterClearanceDp(fontScale:Float,navigationBottomDp:Float=0f,compact:Boolean=false):Float {
    val scale=fontScale.takeIf { it.isFinite() && it>0f }?.coerceIn(.8f,3f) ?: 1f
    return (if(compact)maxOf(48f,34f*scale)+16f else maxOf(24f,17f*scale)+64f)+navigationBottomDp.coerceAtLeast(0f)
}
