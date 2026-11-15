package com.will.lightreader.ui.components

import com.will.lightreader.domain.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterDrawerTest {

    @Test
    fun `ChapterRef 创建正确`() {
        val chapter = ChapterRef(
            chapterUrl = "https://example.com/chapter1",
            title = "第一章",
            volume = "卷一"
        )

        assertEquals("https://example.com/chapter1", chapter.chapterUrl)
        assertEquals("第一章", chapter.title)
        assertEquals("卷一", chapter.volume)
    }

    @Test
    fun `章节数量计算正确`() {
        val chapters = listOf(
            ChapterRef("url1", "第一章"),
            ChapterRef("url2", "第二章"),
            ChapterRef("url3", "第三章")
        )

        assertEquals(3, chapters.size)
    }

    @Test
    fun `当前章节识别正确`() {
        val chapters = listOf(
            ChapterRef("url1", "第一章"),
            ChapterRef("url2", "第二章"),
            ChapterRef("url3", "第三章")
        )
        val currentUrl = "url2"

        val currentChapter = chapters.find { it.chapterUrl == currentUrl }
        assertEquals("第二章", currentChapter?.title)
    }
}
