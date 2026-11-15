package com.will.lightreader.ui.shelf

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfPresentationTest {
    private fun book(key: String, title: String = key, author: String = "", readAt: Long = 0,
        progress: Int = 0, downloaded: Int = 0) = ShelfBook(
        key, "test", key, title, author, null, readAt, progress, "", "", downloaded,
        continueUrl = null, continueTitle = "",
    )

    @Test fun `search trims input and matches title or author without changing data`() {
        val original = listOf(book("1", "Moon Library", "白川"), book("2", "夏日", "MORI"), book("3", "魔女", "森野"))
        assertEquals(listOf("1"), presentShelf(original, "  MOON ", ShelfCategory.TIME, ShelfSort.RECENT).map { it.key })
        assertEquals(listOf("2"), presentShelf(original, "mori", ShelfCategory.TIME, ShelfSort.RECENT).map { it.key })
        assertEquals(listOf("1", "2", "3"), original.map { it.key })
    }

    @Test fun `download filter intersects search`() {
        val books = listOf(book("1", "月光", downloaded = 2), book("2", "月光续篇"), book("3", "夏天", downloaded = 3))
        assertEquals(listOf("1"), presentShelf(books, "月光", ShelfCategory.DOWNLOADED, ShelfSort.RECENT).map { it.key })
    }

    @Test fun `recent sort orders by actual reading time`() {
        assertEquals(listOf("new", "old", "unread"), presentShelf(listOf(book("old", readAt = 1), book("unread"), book("new", readAt = 9)),
            "", ShelfCategory.TIME, ShelfSort.RECENT).map { it.key })
    }

    @Test fun `progress ties use recent reading time`() {
        val books = listOf(book("old", readAt = 1, progress = 50), book("new", readAt = 9, progress = 50), book("finished", progress = 100))
        assertEquals(listOf("finished", "new", "old"), presentShelf(books, "", ShelfCategory.TIME, ShelfSort.PROGRESS).map { it.key })
    }

    @Test fun `title ordering uses Chinese collation`() {
        assertEquals(listOf("阿", "白", "曾"), presentShelf(listOf(book("曾"), book("白"), book("阿")),
            "", ShelfCategory.TIME, ShelfSort.TITLE).map { it.title })
    }

    @Test fun `empty searches and author category preserve all matching books`() {
        val books = listOf(book("1", author = "作者"), book("2", author = "作者"))
        assertEquals(2, presentShelf(books, " 作者 ", ShelfCategory.AUTHOR, ShelfSort.RECENT).size)
        assertEquals(emptyList<ShelfBook>(), presentShelf(books, "missing", ShelfCategory.TIME, ShelfSort.TITLE))
        assertEquals(emptyList<ShelfBook>(), presentShelf(emptyList(), "", ShelfCategory.TIME, ShelfSort.RECENT))
    }
}
