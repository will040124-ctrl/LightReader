package com.will.lightreader.source

import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRegistryTest {
    private val fake = object : NovelSource {
        override val id = "fake"; override val displayName = "假源"
        override suspend fun search(query: String) = emptyList<BookBrief>()
        override suspend fun authorWorks(authorName: String, authorUrl: String?) = emptyList<BookBrief>()
        override suspend fun popular(kind: String) = emptyList<BookBrief>()
        override suspend fun bookDetail(bookUrl: String): BookDetail = throw NotImplementedError()
        override suspend fun chapterList(bookUrl: String): List<ChapterRef> = throw NotImplementedError()
        override suspend fun chapterContent(chapterUrl: String): ChapterContent = throw NotImplementedError()
    }

    @Test
    fun `按 id 取源`() {
        val reg = SourceRegistry(listOf(fake))
        assertEquals("fake", reg.get("fake").id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `未知 id 抛错`() {
        SourceRegistry(listOf(fake)).get("nope")
    }
}
