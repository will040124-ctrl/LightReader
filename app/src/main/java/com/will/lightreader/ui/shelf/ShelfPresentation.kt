package com.will.lightreader.ui.shelf

enum class ShelfSort(val label: String) { RECENT("最近阅读"), TITLE("书名排序"), PROGRESS("阅读进度") }

internal fun shelfFocusIndex(keys: List<String>, selected: String?, fallback: Int): Int =
    if (keys.isEmpty()) -1 else keys.indexOf(selected).takeIf { it >= 0 } ?: fallback.coerceIn(keys.indices)

/** Pure presentation transform shared by grid and list; filtering never mutates the library. */
fun presentShelf(books: List<ShelfBook>, query: String, category: ShelfCategory, sort: ShelfSort): List<ShelfBook> {
    val term = query.trim()
    val filtered = books.filter { book ->
        (category != ShelfCategory.DOWNLOADED || book.downloadedCount > 0) &&
            (term.isEmpty() || book.title.contains(term, ignoreCase = true) || book.author.contains(term, ignoreCase = true))
    }
    return when (sort) {
        ShelfSort.RECENT -> filtered.sortedByDescending { it.lastReadAt }
        ShelfSort.TITLE -> filtered.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.CHINA)) { it.title })
        ShelfSort.PROGRESS -> filtered.sortedWith(compareByDescending<ShelfBook> { it.progressPercent }.thenByDescending { it.lastReadAt })
    }
}
