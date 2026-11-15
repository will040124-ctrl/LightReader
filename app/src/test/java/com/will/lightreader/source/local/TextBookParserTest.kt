package com.will.lightreader.source.local

import com.will.lightreader.domain.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TextBookParserTest {

    private val sample = """
        书名：提灯少女
        作者：晚风

        第一章 雨夜
        雨下了一整夜。
        少女把书抱在怀里，
        她没有回头。

        第二章 灯下
        灯亮了。
    """.trimIndent()

    @Test
    fun `UTF-8 文本按章节切分并合并硬折行`() {
        val book = TextBookParser.parse("提灯少女.txt", sample.toByteArray(Charsets.UTF_8))
        assertEquals("提灯少女", book.title)
        assertEquals("晚风", book.author)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 雨夜", book.chapters[0].title)
        val paras = book.chapters[0].blocks.filterIsInstance<Block.Paragraph>().map { it.text }
        assertEquals(1, paras.size)
        assertTrue("中文硬折行应拼成一段", paras[0].startsWith("雨下了一整夜。") && paras[0].endsWith("她没有回头。"))
        assertEquals("第二章 灯下", book.chapters[1].title)
    }

    @Test
    fun `GB18030 编码可识别`() {
        val bytes = sample.toByteArray(Charset.forName("GB18030"))
        val book = TextBookParser.parse("提灯少女.txt", bytes)
        assertEquals(2, book.chapters.size)
        assertTrue(book.chapters[0].blocks.filterIsInstance<Block.Paragraph>().first().text.contains("雨下了一整夜"))
    }

    @Test
    fun `CRLF 与双 CR 换行同样合并硬折行`() {
        val crlf = sample.replace("\n", "\r\n")
        val a = TextBookParser.parse("a.txt", crlf.toByteArray(Charsets.UTF_8))
        assertEquals(2, a.chapters.size)
        assertEquals(1, a.chapters[0].blocks.filterIsInstance<Block.Paragraph>().size)

        val doubled = sample.replace("\n", "\r\r\n")
        val b = TextBookParser.parse("b.txt", doubled.toByteArray(Charsets.UTF_8))
        assertEquals(2, b.chapters.size)
        assertEquals("第一章 雨夜", b.chapters[0].title)
    }

    @Test
    fun `没有章节标题时整本作为一章`() {
        val text = buildString {
            repeat(30) { appendLine("这是一行没有章节标记的正文，用来测试兜底逻辑。") }
        }
        val book = TextBookParser.parse("乱七八糟.txt", text.toByteArray(Charsets.UTF_8))
        assertEquals(1, book.chapters.size)
        assertEquals("乱七八糟", book.chapters[0].title)
        assertTrue(book.chapters[0].blocks.filterIsInstance<Block.Paragraph>().isNotEmpty())
    }

    @Test
    fun `Markdown 按一级标题切章`() {
        val md = "# 第一章 开始\n\n正文一。\n\n# 第二章 结束\n\n正文二。\n"
        val book = MarkdownBookParser.parse("故事.md", md.toByteArray(Charsets.UTF_8))
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 开始", book.chapters[0].title)
        assertEquals("正文二。", book.chapters[1].blocks.filterIsInstance<Block.Paragraph>().first().text)
    }

    @Test
    fun `格式识别`() {
        assertEquals(LocalFormat.EPUB, LocalFormat.of("a.epub"))
        assertEquals(LocalFormat.UNKNOWN, LocalFormat.of("noext", byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertEquals(LocalFormat.TXT, LocalFormat.of("a.TXT"))
        assertEquals(LocalFormat.MARKDOWN, LocalFormat.of("a.md"))
        assertEquals(LocalFormat.PDF, LocalFormat.of("a.pdf"))
    }
}
