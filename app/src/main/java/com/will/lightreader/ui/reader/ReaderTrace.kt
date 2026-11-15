package com.will.lightreader.ui.reader

/** Visible in system traces for both debug and shell-profileable measurement builds. */
inline fun <T> traceReaderSection(name: String, block: () -> T): T {
    android.os.Trace.beginSection(name)
    return try { block() } finally { android.os.Trace.endSection() }
}
