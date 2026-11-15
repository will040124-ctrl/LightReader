package com.will.lightreader.media

import kotlin.math.sqrt

/** Bound retained images independently from the device's physical RAM. */
internal fun imageMemoryCacheBytes(memoryClassMb: Int): Int =
    (memoryClassMb.coerceIn(32, 8192).toLong() * 1024 * 1024 / 5)
        .coerceIn(8L * 1024 * 1024, 96L * 1024 * 1024).toInt()

internal data class ImageDecodeBounds(val width: Int, val height: Int)

/** One fixed 2x viewport decode supports zoom without replacing the image mid-gesture. */
internal fun comicPageDecodeBounds(viewWidth: Int, viewHeight: Int): ImageDecodeBounds {
    val width = viewWidth.coerceIn(256, 2048).toDouble() * 2
    val height = viewHeight.coerceIn(256, 4096).toDouble() * 2
    val scale = minOf(1.0, sqrt(6_000_000.0 / (width * height)))
    return ImageDecodeBounds((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
}
