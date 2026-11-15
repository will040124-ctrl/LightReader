package com.will.lightreader.data

import com.will.lightreader.domain.Block
import com.will.lightreader.domain.ChapterContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChapterCacheTest {
    @Test fun `long comic episode urls cannot collide after the shared book prefix`() {
        val cache=ChapterCache(tmp.newFolder("long-comic"))
        val prefix="https://lightreader.invalid/comic-js-mangadex_public/"+"a".repeat(80)+"/"
        fun chapter(id:String)=ChapterContent(prefix+id,"",emptyList(),comicPages=listOf(com.will.lightreader.domain.ComicPage(id,"https://example.test/$id.png")))
        val one=chapter("first");val two=chapter("second")
        val firstPath=cache.write(one.chapterUrl,one);val secondPath=cache.write(two.chapterUrl,two)
        org.junit.Assert.assertNotEquals(firstPath,secondPath)
        assertEquals(one,cache.readByUrl(one.chapterUrl));assertEquals(two,cache.readByUrl(two.chapterUrl))
    }
    @Test fun `cached comic page manifest is readable but cannot be relinked as offline images`() {
        val cache = ChapterCache(tmp.newFolder("comic-manifest"))
        val comic = ChapterContent("https://example.test/episode", "", emptyList(),
            comicPages = listOf(com.will.lightreader.domain.ComicPage("page1", "https://example.test/image.png")))
        cache.write(comic.chapterUrl, comic)
        assertEquals(comic, cache.readByUrl(comic.chapterUrl))
        org.junit.Assert.assertFalse(cache.offlineCompleteByUrl(comic.chapterUrl))
        cache.write(content.chapterUrl, content)
        assertTrue(cache.offlineCompleteByUrl(content.chapterUrl))
    }
    @Test fun `a response containing only its own title cannot replace cached prose`() {
        val cache = ChapterCache(tmp.newFolder("title-only"))
        cache.write(content.chapterUrl, content)
        try {
            cache.write(content.chapterUrl, content.copy(blocks = listOf(Block.Paragraph(content.title))))
            fail("title-only response must not replace prose")
        } catch (_: java.io.IOException) { }
        assertEquals(content, cache.readByUrl(content.chapterUrl))
    }
    @Test fun `damaged new content never overwrites a good chapter`() {
        val dir = tmp.newFolder("safe-chapters")
        val cache = ChapterCache(dir)
        cache.write(content.chapterUrl, content)
        try {
            cache.write(content.chapterUrl, content.copy(blocks = listOf(Block.Paragraph("�".repeat(50)))))
            fail("damaged content must be rejected")
        } catch (_: java.io.IOException) { }
        assertEquals(content, cache.readByUrl(content.chapterUrl))
    }

    @Test fun `old damaged bytes are retained until a healthy replacement is available`() {
        val dir = tmp.newFolder("old-chapters")
        val cache = ChapterCache(dir)
        val target = java.io.File(dir, cache.pathFor(content.chapterUrl).substringAfterLast('/'))
        target.writeText(kotlinx.serialization.json.Json.encodeToString(ChapterContent.serializer(),
            content.copy(blocks = listOf(Block.Paragraph("�".repeat(100))))))
        val oldBytes = target.readBytes()
        assertNull(cache.readByUrl(content.chapterUrl))
        assertTrue(oldBytes.contentEquals(target.readBytes()))
        cache.write(content.chapterUrl, content)
        assertEquals(content, cache.readByUrl(content.chapterUrl))
        assertTrue(java.io.File(dir.parentFile, "chapter-recovery/${target.name}").readBytes().contentEquals(oldBytes))
    }
    @get:Rule
    val tmp = TemporaryFolder()

    private val content = ChapterContent(
        chapterUrl = "https://www.linovelib.com/novel/3032/151118.html",
        title = "序章",
        blocks = listOf(
            Block.Paragraph("第一段。"),
            Block.Image(ref = "https://img.a/1.jpg", caption = "彩页"),
            Block.Divider,
            Block.Paragraph("第二段。"),
        ),
    )

    @Test
    fun `写入后能按路径读回完整结构`() {
        val cache = ChapterCache(tmp.newFolder("chapters"))
        val path = cache.write(content.chapterUrl, content)
        assertEquals("chapters/", path.substringBeforeLast("/") + "/")
        assertEquals(content, cache.read(path))
    }

    @Test
    fun `重复写同一章覆盖不膨胀`() {
        val cache = ChapterCache(tmp.newFolder("chapters"))
        val p1 = cache.write(content.chapterUrl, content)
        val p2 = cache.write(content.chapterUrl, content.copy(title = "序章（改）"))
        assertEquals(p1, p2)
        assertEquals("序章（改）", cache.read(p2)?.title)
        assertEquals(1, tmp.root.listFiles()?.size ?: 0)
    }

    @Test
    fun `文件缺失读取返回 null`() {
        val cache = ChapterCache(tmp.newFolder("chapters"))
        assertNull(cache.read("chapters/not-exist.json"))
    }

    @Test
    fun `按URL直读无指针也能命中缓存`() {
        val cache = ChapterCache(tmp.newFolder("chapters"))
        cache.write(content.chapterUrl, content)
        // 没有 DB contentPath 指针时，按 URL 确定性文件名也能读回（离线优先兜底）
        assertEquals(content, cache.readByUrl(content.chapterUrl))
    }

    @Test
    fun `按URL直读未缓存返回 null`() {
        val cache = ChapterCache(tmp.newFolder("chapters"))
        assertNull(cache.readByUrl("https://never/saved.html"))
    }
}
