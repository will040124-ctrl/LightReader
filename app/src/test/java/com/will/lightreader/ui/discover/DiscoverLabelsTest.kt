package com.will.lightreader.ui.discover
import org.junit.Assert.*
import org.junit.Test
class DiscoverLabelsTest {
    @Test fun `remove duplicated carousel instruction without damaging real titles`() {
        assertEquals("连载更新",comicSectionHeading("连载更新→右滑看更多→"))
        assertEquals("精选",comicSectionHeading("精选 · 右滑看更多"))
        assertEquals("C108&推荐本本",comicSectionHeading("C108&推荐本本"))
        assertEquals("右滑故事的新篇章",comicSectionHeading("右滑故事的新篇章"))
        assertEquals("为你推荐",comicSectionHeading("  "))
    }
}
