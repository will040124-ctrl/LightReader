package com.will.lightreader.widget

import com.will.lightreader.data.*
import org.junit.Assert.*
import org.junit.Test

class ContinueWidgetStateTest {
    private fun book(id:String)=BookEntity("test:$id","test",id,"https://example.test/$id","书$id",coverUrl="file:///cover-$id.png")
    @Test fun `empty widget opens the library without a stale target`() {
        assertNull(continueWidgetState(emptyList(),emptyList(),emptyList(),true).bookKey)
    }
    @Test fun `adding a book without reading does not replace the continuation`() {
        val state=continueWidgetState(listOf(book("1"),book("2")),listOf(PositionEntity("test:1","c1",updatedAt=4)),emptyList(),true)
        assertEquals("test:1",state.bookKey)
    }
    @Test fun `recent saved position wins and deleted books are excluded`() {
        val state=continueWidgetState(listOf(book("1"),book("2")),listOf(PositionEntity("missing","c0",updatedAt=99),
            PositionEntity("test:1","c1",updatedAt=2),PositionEntity("test:2","c2",updatedAt=5)),emptyList(),true)
        assertEquals("test:2",state.bookKey)
    }
    @Test fun `privacy hides all reading details while retaining a working target`() {
        val state=continueWidgetState(listOf(book("1")),listOf(PositionEntity("test:1","c1",chapterTitle="秘密章节")),emptyList(),false)
        assertEquals("test:1",state.bookKey);assertNull(state.coverUrl);assertNull(state.progress)
        assertFalse(state.title.contains("书1"));assertFalse(state.chapter.contains("秘密"))
    }
    @Test fun `progress accounts for chapter order and within-chapter position`() {
        val state=continueWidgetState(listOf(book("1")),listOf(PositionEntity("test:1","c2",offsetRatio=.5f)),
            listOf(ChapterEntity("c2","test:1","第二章",idx=1),ChapterEntity("c1","test:1","第一章",idx=0)),true)
        assertEquals(75,state.progress);assertEquals("第二章",state.chapter)
    }
    @Test fun `unknown chapter does not invent a percentage`() {
        assertNull(continueWidgetState(listOf(book("1")),listOf(PositionEntity("test:1","missing")),emptyList(),true).progress)
    }
    @Test fun `fixed page books report the page reached instead of a text offset`() {
        val b=book("1").copy(sourceId="local")
        val state=continueWidgetState(listOf(b),listOf(PositionEntity(b.key,"p3")),
            (1..3).map { ChapterEntity("p$it",b.key,"第 $it 页",idx=it-1) },true)
        assertEquals(100,state.progress)
    }
}
