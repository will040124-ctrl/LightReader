package com.will.lightreader.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** An explicit widget intent carries only a library key; navigation resolves fresh DB state. */
object ReadingRequests {
    const val ACTION = "com.will.lightreader.CONTINUE_READING"
    const val EXTRA_BOOK = "continue_book_key"
    data class Request(val bookKey: String, val token: Long = System.nanoTime())
    val pending = MutableStateFlow<Request?>(null)
}
