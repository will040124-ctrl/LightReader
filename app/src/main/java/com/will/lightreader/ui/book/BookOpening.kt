package com.will.lightreader.ui.book

import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail

/** A bounded navigation snapshot keeps the target jacket present on the very first frame. */
object BookOpening {
    private data class Snapshot(val book: BookBrief, val fromShelf: Boolean)
    private val snapshots = object : LinkedHashMap<Pair<String, String>, Snapshot>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, Snapshot>?) = size > 32
    }
    fun prime(book: BookBrief, fromShelf: Boolean = false) { snapshots[book.sourceId to book.bookUrl] = Snapshot(book, fromShelf) }
    fun shelfCover(sourceId: String, url: String): BookBrief? = snapshots[sourceId to url]?.takeIf { it.fromShelf }?.book
    fun detail(sourceId: String, url: String): BookDetail? = snapshots[sourceId to url]?.book?.let {
        BookDetail(it.sourceId, it.bookId, it.bookUrl, it.title, it.author, it.coverUrl, tags = it.tags, status = it.status)
    }
}
