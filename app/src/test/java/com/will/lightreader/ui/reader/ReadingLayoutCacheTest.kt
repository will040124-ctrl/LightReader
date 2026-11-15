package com.will.lightreader.ui.reader

import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.Block
import org.junit.Assert.*
import org.junit.Test

class ReadingLayoutCacheTest {
    private val content = ChapterContent("url", "title", listOf(Block.Paragraph("正文")))
    private val key = ReadingLayoutKey(content, 320, 600, 80, 18, 1.7f, true, 8, 2f, 1f, "Ltr", false)
    private val flow = readingFlow(listOf(FlowUnit(0, 50, 0, 2, true)), 600)
    @Test fun `same content reuses layout but changed geometry font and content miss`() {
        val cache = BoundedReadingLayouts(); cache.put(key, flow)
        assertSame(flow, cache.get(key.copy(content = content.copy())))
        for (different in listOf(key.copy(width = 600), key.copy(height = 900), key.copy(fontScale = 1.3f),
            key.copy(serif = false), key.copy(content = content.copy(blocks = listOf(Block.Paragraph("新正文"))))))
            assertNull(cache.get(different))
    }
    @Test fun `least recently used layout evicts and memory trim empties cache`() {
        val cache = BoundedReadingLayouts(capacity = 2)
        cache.put(key, flow); cache.put(key.copy(width = 400), flow); cache.get(key)
        cache.put(key.copy(width = 500), flow)
        assertNull(cache.get(key.copy(width = 400))); assertSame(flow, cache.get(key))
        cache.clear(); assertNull(cache.get(key))
    }
    @Test fun `oversized layout is not retained`() {
        val cache = BoundedReadingLayouts(maxFragments = 0); cache.put(key, flow)
        assertNull(cache.get(key))
    }
}
