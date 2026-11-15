package com.will.lightreader.ui.book

import com.will.lightreader.domain.*
import com.will.lightreader.source.SourceError
import org.junit.Assert.*
import org.junit.Test

class BookLoadFailureTest {
    @Test fun `a cover primed from search cannot hide the chapter loading error`() {
        val state=BookState(detail=BookDetail("test","1","url","炎拳"))
        val error=SourceError.ContentUnavailable("当前英语没有可读章节")
        val result=bookLoadFailure(state,false,error)
        assertSame(error,result.error);assertFalse(result.loading)
    }
    @Test fun `existing local chapters are retained when a refresh fails`() {
        val chapters=listOf(ChapterRef("url","第一话"));val state=BookState(chapters=chapters)
        val result=bookLoadFailure(state,true,SourceError.ContentUnavailable("暂无英语章节"))
        assertEquals(chapters,result.chapters);assertNull(result.error);assertTrue(result.offline)
        assertTrue(result.refreshMessage!!.contains("暂无英语章节"))
    }
}
