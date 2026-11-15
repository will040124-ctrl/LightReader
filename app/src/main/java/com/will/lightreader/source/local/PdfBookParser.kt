package com.will.lightreader.source.local

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.will.lightreader.domain.FixedPage
import java.io.File

class PdfBookParser(private val source: File, private val outDir: File) {
    fun parse(title: String): ImportedBook {
        val document = File(outDir, "document.pdf")
        source.copyTo(document, overwrite = true)
        try {
            return withPdfDocument(document) { renderer ->
                if (renderer.pageCount == 0) throw ImportException("PDF 中没有页面")
                if (renderer.pageCount > 10_000) throw ImportException("PDF 超过 10000 页，请拆分后导入")
                val cover = File(outDir, "cover.jpg")
                renderer.openPage(0).use { page ->
                    val width = 320
                    val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).coerceIn(1, 1280).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    } finally { bitmap.recycle() }
                }
                ImportedBook(title, intro = "PDF 原始版面 · ${renderer.pageCount} 页", cover = cover,
                    chapters = (0 until renderer.pageCount).map { index ->
                        ImportedChapter("第 ${index + 1} 页", blocks = emptyList(),
                            fixedPage = FixedPage(document.toURI().toString(), index))
                    })
            }
        } catch (e: SecurityException) { throw ImportException("此 PDF 已加密，请先解密后再导入", e) }
        catch (e: ImportException) { throw e }
        catch (e: Exception) { throw ImportException("PDF 无法读取，文件可能损坏或使用了不支持的安全限制", e) }
    }
}
