package com.will.lightreader.source.script

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

private const val MaxImagePixels = 12_000_000L
internal const val MaxComicImageBytes = 24 * 1024 * 1024

/** Venera's empty/copy API. The isolate returns coordinates, never native handles or pixels. */
internal class ComicImageTransform(private val engine: ComicScriptEngine) {
    private val decodeLock = Mutex()

    suspend fun apply(bytes: ByteArray, script: String): ByteArray = decodeLock.withLock {
        require(script.toByteArray().size in 1..4096) { "图片处理脚本无效" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0 && width.toLong() * height <= MaxImagePixels) { "图片处理尺寸超出限制" }
        val result = JmImagePlan.resolve(script,width,height) ?: run {
            engine.evaluate("class ImageTransform extends ComicSource {}", "ImageTransform",
                "__lrImagePlan(${JsonPrimitive(script)}, $width, $height)", timeoutMs=5_000)
        }
        if (result is JsonNull) return@withLock bytes
        val plan = result.jsonObject
        val outWidth = plan.getValue("width").jsonPrimitive.int
        val outHeight = plan.getValue("height").jsonPrimitive.int
        require(outWidth > 0 && outHeight > 0 && outWidth.toLong() * outHeight <= MaxImagePixels)
        val copies = plan.getValue("copies").jsonArray
        require(copies.size in 1..128)
        var copiedPixels = 0L
        val rectangles = copies.map { copy ->
            val parts = copy.jsonArray.map { it.jsonPrimitive.int }
            require(parts.size == 6)
            val (x, y, srcX, srcY, copyWidth) = parts
            val copyHeight = parts[5]
            require(copyWidth > 0 && copyHeight > 0 && x in 0..outWidth - copyWidth && y in 0..outHeight - copyHeight &&
                srcX in 0..width - copyWidth && srcY in 0..height - copyHeight) { "图片处理范围无效" }
            copiedPixels += copyWidth.toLong() * copyHeight
            require(copiedPixels <= MaxImagePixels * 2) { "图片处理范围过大" }
            Rect(srcX, srcY, srcX + copyWidth, srcY + copyHeight) to Rect(x, y, x + copyWidth, y + copyHeight)
        }
        val input = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "漫画图片无效" }
        try {
            val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(output)
                rectangles.forEach { (src, dest) -> canvas.drawBitmap(input, src, dest, null) }
                val encoded = ByteArrayOutputStream()
                require(output.compress(Bitmap.CompressFormat.PNG, 100, encoded) && encoded.size() in 1..MaxComicImageBytes) {
                    "处理后的漫画图片超过大小限制"
                }
                encoded.toByteArray()
            } finally {
                output.recycle()
            }
        } finally {
            input.recycle()
        }
    }
}
