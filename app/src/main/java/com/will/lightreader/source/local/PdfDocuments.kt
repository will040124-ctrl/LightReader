package com.will.lightreader.source.local

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** PdfRenderer takes descriptor ownership only after construction succeeds. */
inline fun <T> withPdfDocument(file: File, block: (PdfRenderer) -> T): T {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = try { PdfRenderer(descriptor) }
        catch (e: Throwable) { descriptor.close(); throw e }
    return renderer.use(block)
}
