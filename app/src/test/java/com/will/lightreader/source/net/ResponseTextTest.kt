package com.will.lightreader.source.net

import com.will.lightreader.domain.TextIntegrity
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class ResponseTextTest {
    private val gb = Charset.forName("GB18030")
    private val sample = "专业青梅竹马，八奈见杏菜。ひらがな、𠮷与星星🌙。"
    @Test fun `legacy body survives a UTF8 caller default`() {
        val text = "<meta charset=gb18030><p>专业青梅竹马，八奈见杏菜。</p>"
        assertEquals(text, ResponseText.decode(text.toByteArray(gb)))
    }
    @Test fun `utf8 download packet survives a legacy site hint`() {
        assertEquals(sample, ResponseText.decode(sample.toByteArray(), fallback = gb))
        assertEquals(sample, ResponseText.decode(sample.toByteArray(), "text/plain; charset=gbk", gb))
    }
    @Test fun `title-only chapter is incomplete but short real prose is allowed`() {
        val chapter = com.will.lightreader.domain.ChapterContent("url", "", listOf(com.will.lightreader.domain.Block.Paragraph("第一卷 第一章 开始")))
        assertTrue(TextIntegrity.titleOnly(chapter, "第一章 开始"))
        assertFalse(TextIntegrity.titleOnly(chapter.copy(blocks = listOf(com.will.lightreader.domain.Block.Paragraph("你好，世界。"))), "第一章 开始"))
    }
    @Test fun `bom takes precedence over stale declaration`() {
        val text = "星の向こう、图书馆。"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, ResponseText.decode(bytes, "text/plain; charset=gbk", gb))
    }
    @Test fun `declared GBK response decodes strictly`() {
        val text = "夜读时光，章节正常。"
        assertEquals(text, ResponseText.decode(text.toByteArray(Charset.forName("GBK")), "text/html; charset=gbk"))
    }
    @Test fun `damaged cache is recognized without rejecting foreign language or symbols`() {
        assertTrue(TextIntegrity.damaged("\uFFFD".repeat(50) + "abc"))
        assertFalse(TextIntegrity.damaged(sample))
        assertFalse(TextIntegrity.damaged("ASCII and symbols ♬ ☆ ♡"))
        assertFalse(TextIntegrity.damaged("A literal replacement symbol: �"))
    }
    @Test fun `browser bridge requests bytes before decoding`() {
        val script = BrowserResponseText.decoder(gb)
        assertTrue(script.contains("r.arrayBuffer()"))
        assertTrue(script.contains("fatal:true"))
        assertFalse(script.contains("r.text()"))
    }
}
