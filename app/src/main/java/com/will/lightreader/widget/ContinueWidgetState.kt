package com.will.lightreader.widget

import com.will.lightreader.data.BookEntity
import com.will.lightreader.data.ChapterEntity
import com.will.lightreader.data.PositionEntity

data class ContinueWidgetState(val bookKey: String?, val title: String, val chapter: String,
    val progress: Int?, val coverUrl: String?)

fun continueWidgetState(books: List<BookEntity>, positions: List<PositionEntity>,
    chapters: List<ChapterEntity>, showDetails: Boolean): ContinueWidgetState {
    val byKey = books.associateBy { it.key }
    val position = positions.filter { it.bookKey in byKey && it.chapterUrl.isNotBlank() }.maxByOrNull { it.updatedAt }
        ?: return ContinueWidgetState(null, "把故事接着读下去", "打开轻阅，选择一本喜欢的书", null, null)
    val book = byKey.getValue(position.bookKey)
    if (!showDetails) return ContinueWidgetState(book.key, "回到你的故事", "轻点继续阅读", null, null)
    val directory = chapters.filter { it.bookKey == book.key }.sortedBy { it.idx }
    val index = directory.indexOfFirst { it.key == position.chapterUrl }
    val pageBased = book.sourceId == "local" && directory.firstOrNull()?.title?.matches(Regex("第 \\d+ 页")) == true
    val progress = if (index >= 0 && directory.isNotEmpty())
        ((index + if (pageBased) 1f else position.offsetRatio.coerceIn(0f, 1f)) / directory.size * 100).toInt().coerceIn(0, 100) else null
    return ContinueWidgetState(book.key, book.title,
        position.chapterTitle.ifBlank { directory.getOrNull(index)?.title.orEmpty() }.ifBlank { "继续上次的故事" },
        progress, book.coverUrl)
}
