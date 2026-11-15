package com.will.lightreader.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.will.lightreader.domain.FixedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import kotlin.math.sqrt

/** Render only visible PDF pages; cache is bounded by bytes, never by document page count. */
object PdfPageImages {
    private val slots = Semaphore(2)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun load(page: FixedPage, requestedWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        slots.withPermit {
            val file = File(URI(page.ref))
            val width = requestedWidth.coerceIn(256, 3072)
            val key = "${file.path}:${file.lastModified()}:${file.length()}:${page.pdfIndex}:$width"
            cache.get(key)?.let { return@withPermit it }
            currentCoroutineContext().ensureActive()
            traceReaderSection("PDF.renderPage") { com.will.lightreader.source.local.withPdfDocument(file) { renderer ->
                renderer.openPage(requireNotNull(page.pdfIndex)).use { pdf ->
                    val ratio = pdf.height.toDouble() / pdf.width.coerceAtLeast(1)
                    val w = minOf(width.toDouble(), sqrt(6_000_000.0 / ratio)).toInt().coerceAtLeast(1)
                    val h = (w * ratio).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        pdf.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        currentCoroutineContext().ensureActive()
                        cache.put(key, bitmap)
                        bitmap
                    } catch (e: Throwable) { bitmap.recycle(); throw e }
                }
            } }
        }
    }

    fun trim() { cache.evictAll() }
}
