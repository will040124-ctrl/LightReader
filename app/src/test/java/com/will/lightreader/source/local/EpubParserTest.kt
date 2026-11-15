package com.will.lightreader.source.local

import com.will.lightreader.domain.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 用内存构造的最小 EPUB 钉死解析行为：元数据 / 目录分卷 / 正文块 / 插图 / 封面。 */
class EpubParserTest {

    private fun singleFileBook(body: String, nav: String, ncx: String? = null): ImportedBook {
        val dir = tempDir()
        val entries = linkedMapOf(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""",
            "content.opf" to """<package version="${if (ncx == null) "3.0" else "2.0"}"><metadata><title>Nested</title></metadata>
                <manifest><item id="all" href="all.xhtml" media-type="application/xhtml+xml"/>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                </manifest><spine toc="ncx"><itemref idref="all"/></spine></package>""",
            "all.xhtml" to "<html><body>$body</body></html>",
            "nav.xhtml" to nav,
        )
        if (ncx != null) entries["toc.ncx"] = ncx
        return EpubParser(buildEpub(dir, entries), dir).parse("fallback")
    }

    @Test
    fun `嵌套容器内多个目录锚点按真实节点分章且保留长段落`() {
        val long = "长段落不会被丢弃。".repeat(3000)
        val book = singleFileBook(
            "<div><section><h2 id='a'>第一章</h2><p>$long</p><h2 id='b'>第二章</h2><p>末章正文</p></section></div>",
            "<nav><ol><li><a href='all.xhtml#a'>第一章</a></li><li><a href='all.xhtml#b'>第二章</a></li></ol></nav>",
        )
        assertEquals(listOf("第一章", "第二章"), book.chapters.map { it.title })
        assertEquals(listOf(long), book.chapters[0].blocks.filterIsInstance<Block.Paragraph>().map { it.text })
        assertEquals(listOf("末章正文"), book.chapters[1].blocks.filterIsInstance<Block.Paragraph>().map { it.text })
    }

    @Test
    fun `同段落行内锚点不漏字也不串章`() {
        val book = singleFileBook(
            "<div><p>前言<span id='a'></span>上半<span id='b'></span>下半</p></div>",
            "<nav><ol><li><a href='all.xhtml#a'>上</a></li><li><a href='all.xhtml#b'>下</a></li></ol></nav>",
        )
        assertEquals(listOf("前言上半", "下半"), book.chapters.map { chapter ->
            chapter.blocks.filterIsInstance<Block.Paragraph>().joinToString("") { it.text }
        })
    }

    @Test
    fun `EPUB2与无效nav回退NCX目录`() {
        val book = singleFileBook("<h2 id='a'>一</h2><p>内容甲</p><h2 id='b'>二</h2><p>内容乙</p>",
            "<html><body>无有效目录</body></html>",
            """<ncx><navMap><navPoint><navLabel><text>一</text></navLabel><content src="all.xhtml#a"/></navPoint>
                <navPoint><navLabel><text>二</text></navLabel><content src="all.xhtml#b"/></navPoint></navMap></ncx>""")
        assertEquals(listOf("一", "二"), book.chapters.map { it.title })
        assertEquals(listOf("内容甲", "内容乙"), book.chapters.flatMap { it.blocks }.filterIsInstance<Block.Paragraph>().map { it.text })
    }

    private fun tempDir(): File = Files.createTempDirectory("lr-epub").toFile()

    private fun buildEpub(dir: File, entries: Map<String, String>): File {
        val epub = File(dir, "book.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            for ((name, body) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return epub
    }

    private fun opf(spine: List<String>, manifestExtra: String = "") = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>春日与猫</dc:title>
            <dc:creator>王文周</dc:creator>
            <dc:language>zh-CN</dc:language>
            <dc:description>&lt;p&gt;一个关于&lt;b&gt;猫耳少女&lt;/b&gt;的故事。&lt;/p&gt;</dc:description>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="cover" href="Images/cover.png" media-type="image/png" properties="cover-image"/>
            ${spine.joinToString("\n            ") { "<item id=\"${it}\" href=\"Text/$it.xhtml\" media-type=\"application/xhtml+xml\"/>" }}
            <item id="pic" href="Images/p1.png" media-type="image/png"/>
            $manifestExtra
          </manifest>
          <spine>
            ${spine.joinToString("\n            ") { "<itemref idref=\"$it\"/>" }}
          </spine>
        </package>
    """.trimIndent()

    @Test
    fun `解析元数据 目录分卷 正文块 插图与封面`() {
        val dir = tempDir()
        val epub = buildEpub(
            dir,
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                """.trimIndent(),
                "OEBPS/content.opf" to opf(listOf("ch1", "ch2")),
                "OEBPS/nav.xhtml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                    <body><nav epub:type="toc"><ol>
                      <li><span>第一卷 相遇</span><ol>
                        <li><a href="Text/ch1.xhtml">第一章 雨夜</a></li>
                        <li><a href="Text/ch2.xhtml#s1">第二章 猫耳</a></li>
                      </ol></li>
                    </ol></nav></body></html>
                """.trimIndent(),
                "OEBPS/Text/ch1.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml"><body>
                      <h1>第一章 雨夜</h1>
                      <p>雨下了一整夜。</p>
                      <p>少女把书抱在怀里。<br/>她没有回头。</p>
                      <hr/>
                      <p>&#160;</p>
                      <img src="../Images/p1.png" alt="插图"/>
                    </body></html>
                """.trimIndent(),
                "OEBPS/Text/ch2.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml"><body>
                      <p>第二章的正文。</p>
                    </body></html>
                """.trimIndent(),
                "OEBPS/Images/p1.png" to "PNGDATA",
                "OEBPS/Images/cover.png" to "COVERDATA",
            ),
        )

        val book = EpubParser(epub, dir).parse("fallback")

        assertEquals("春日与猫", book.title)
        assertEquals("王文周", book.author)
        assertEquals("一个关于猫耳少女的故事。", book.intro)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 雨夜", book.chapters[0].title)
        assertEquals("第一卷 相遇", book.chapters[0].volume)
        assertEquals("第二章 猫耳", book.chapters[1].title)

        val blocks = book.chapters[0].blocks
        val paragraphs = blocks.filterIsInstance<Block.Paragraph>().map { it.text }
        assertTrue("段落应包含正文", paragraphs.contains("雨下了一整夜。"))
        assertTrue("br 折行应合并成一句", paragraphs.any { it.contains("少女把书抱在怀里") && it.contains("她没有回头") })
        assertTrue("hr 应保留为分隔线", blocks.any { it is Block.Divider })
        val image = blocks.filterIsInstance<Block.Image>().firstOrNull()
        assertNotNull("插图应转成本地文件引用", image)
        assertTrue(image!!.ref.startsWith("file://"))
        assertTrue("插图文件应落盘", File(image.ref.removePrefix("file://")).isFile)

        assertNotNull("封面应解析出来", book.cover)
        assertEquals("COVERDATA", book.cover!!.readText())
    }

    @Test
    fun `同一文件多锚点时按锚点切章`() {
        val dir = tempDir()
        val epub = buildEpub(
            dir,
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                """.trimIndent(),
                "content.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>单文件</dc:title><dc:creator>某人</dc:creator>
                      </metadata>
                      <manifest>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="all" href="all.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine><itemref idref="all"/></spine>
                    </package>
                """.trimIndent(),
                "nav.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                    <body><nav epub:type="toc"><ol>
                      <li><a href="all.xhtml#a">第一章 上半</a></li>
                      <li><a href="all.xhtml#b">第二章 下半</a></li>
                    </ol></nav></body></html>
                """.trimIndent(),
                "all.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml"><body>
                      <h2 id="a">第一章 上半</h2><p>上半的内容。</p>
                      <h2 id="b">第二章 下半</h2><p>下半的内容。</p>
                    </body></html>
                """.trimIndent(),
            ),
        )

        val book = EpubParser(epub, dir).parse("fallback")

        assertEquals(2, book.chapters.size)
        assertEquals("第一章 上半", book.chapters[0].title)
        assertEquals(listOf("上半的内容。"), book.chapters[0].blocks.filterIsInstance<Block.Paragraph>().map { it.text })
        assertEquals("下半的内容。", (book.chapters[1].blocks.filterIsInstance<Block.Paragraph>().first()).text)
    }

    @Test
    fun `目录缺失时按标题回退`() {
        val dir = tempDir()
        val epub = buildEpub(
            dir,
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>
                """.trimIndent(),
                "content.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>无目录</dc:title></metadata>
                      <manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/></manifest>
                      <spine><itemref idref="c"/></spine>
                    </package>
                """.trimIndent(),
                "c.xhtml" to "<html><body><h1>楔子</h1><p>正文一段。</p></body></html>",
            ),
        )

        val book = EpubParser(epub, dir).parse("fallback")
        assertEquals(1, book.chapters.size)
        assertEquals("楔子", book.chapters[0].title)
    }
}
