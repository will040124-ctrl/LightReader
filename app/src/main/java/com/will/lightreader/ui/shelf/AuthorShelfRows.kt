package com.will.lightreader.ui.shelf

/** Separate lazy rows prevent an expanded author from eagerly composing an entire library. */
internal sealed interface AuthorShelfRow {
    val key: String
    data class Header(val group: AuthorGroup) : AuthorShelfRow {
        override val key get() = "author:${group.author}"
    }
    data class Book(val book: ShelfBook) : AuthorShelfRow {
        override val key get() = "author-book:${book.key}"
    }
}

internal fun authorShelfRows(groups: List<AuthorGroup>, expanded: Set<String>): List<AuthorShelfRow> = buildList {
    for (group in groups) {
        add(AuthorShelfRow.Header(group))
        if (group.author in expanded) group.books.forEach { add(AuthorShelfRow.Book(it)) }
    }
}
