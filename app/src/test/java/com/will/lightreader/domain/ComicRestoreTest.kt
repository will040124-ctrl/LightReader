package com.will.lightreader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicRestoreTest {
    private fun pages(n: Int) = (0 until n).map { ComicPage("p$it", "https://example.com/$it.jpg") }

    @Test fun `saved page id wins over fallback index`() {
        assertEquals(2, restoreComicPage(pages(5), "p2", 0))
    }

    @Test fun `missing id falls back to coerced index`() {
        assertEquals(1, restoreComicPage(pages(5), "gone", 1))
        assertEquals(4, restoreComicPage(pages(5), null, 99))
        assertEquals(0, restoreComicPage(pages(5), null, -3))
    }

    @Test fun `empty pages restore to zero`() {
        assertEquals(0, restoreComicPage(emptyList(), "p0", 3))
    }
}
