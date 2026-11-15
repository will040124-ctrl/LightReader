package com.will.lightreader.source.wenku8

import com.will.lightreader.domain.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Wenku8ParserTest {
    @Test
    fun `详情页解析与基线一致`() {
        val file = Wenku8Baselines.firstFixtureName("book-")
        val bid = Regex("book-(\\d+)").find(file)!!.groupValues[1]
        val parsed = Wenku8Parser.parseBookDetails(Wenku8Baselines.fixture(file), bid)
        assertTrue(parsed.detail.title.isNotBlank())
        assertTrue(parsed.catalogueUrl.isNotBlank())
        val snap = Wenku8Baselines.snapshot("book-$bid",
            "title=${parsed.detail.title}\nauthor=${parsed.detail.author}\ncatalogue=${parsed.catalogueUrl}\n" +
                "cover=${parsed.detail.coverUrl}\nstatus=${parsed.detail.status}\nupdated=${parsed.detail.updatedAt}\n" +
                "latest=${parsed.detail.latestChapter}\ntags=${parsed.detail.tags}\nintro=${parsed.detail.intro.take(120)}")
        Wenku8Baselines.assertMatchesBaseline("book-detail", snap)
    }

    @Test
    fun `目录页解析出分卷与章节`() {
        val file = Wenku8Baselines.firstFixtureName("catalogue-")
        // 用真实形态的目录地址：相对链接会据此归一化成 /novel/.../{cid}.htm
        val chapters = Wenku8Parser.parseCatalogue(Wenku8Baselines.fixture(file), "https://www.wenku8.net/novel/2/2000/index.htm")
        assertTrue(chapters.isNotEmpty())
        val snap = Wenku8Baselines.snapshot("catalogue",
            chapters.joinToString("\n") { "${it.volume} | ${it.chapterId} | ${it.title} | ${it.chapterUrl}" })
        Wenku8Baselines.assertMatchesBaseline("catalogue", snap)
    }

    @Test
    fun `章节正文出块且图片映射为 Image`() {
        val file = Wenku8Baselines.firstFixtureName("chapter-")
        val blocks = Wenku8Parser.parseChapter(Wenku8Baselines.fixture(file), "https://www.wenku8.net/novel/2/2003/120269.htm", "测试章")
        assertTrue(blocks.any { it is Block.Paragraph && it.text.isNotBlank() })
        val snap = Wenku8Baselines.snapshot("chapter", blocks.joinToString("\n") {
            when (it) {
                is Block.Paragraph -> "P ${it.text}"
                is Block.Image -> "I ${it.ref}"
                Block.Divider -> "D"
            }
        })
        Wenku8Baselines.assertMatchesBaseline("chapter", snap)
    }

    @Test
    fun `搜索与榜单解析出书目（样本缺则跳过单个）`() {
        var checked = 0
        for (name in listOf("search.htm", "toplist-dayvisit.htm")) {
            val html = Wenku8Baselines.optionalFixture(name) ?: continue
            checked++
            val items = Wenku8Parser.parseSearchResults(html)
            assertTrue("$name 至少一条", items.isNotEmpty())
            val snap = Wenku8Baselines.snapshot(name, items.joinToString("\n") { "${it.bookId} ${it.title}" })
            Wenku8Baselines.assertMatchesBaseline("list-" + name.removeSuffix(".htm"), snap)
        }
        assumeTrue("搜索/榜单样本均未采集则跳过", checked > 0)
    }

    @Test
    fun `大赏解析出书目`() {
        val f = Wenku8Baselines.optionalFixture("sugoi-")
        assumeTrue("大赏样本未采集则跳过", f != null)
        val items = Wenku8Parser.parseSugoiResults(f!!)
        assertTrue(items.isNotEmpty())
        val snap = Wenku8Baselines.snapshot("sugoi", items.joinToString("\n") { "${it.bookId} ${it.title} ${it.author}" })
        Wenku8Baselines.assertMatchesBaseline("sugoi", snap)
    }

    @Test
    fun `下载包兜底解析（样本缺则跳过）`() {
        val f = Wenku8Baselines.optionalFixture("packed-")
        assumeTrue("下载包样本未采集则跳过", f != null)
        val blocks = Wenku8Parser.parsePacked(f!!, "https://www.wenku8.net/novel/0/1/1.htm")
        assertTrue(blocks.isNotEmpty())
        val snap = Wenku8Baselines.snapshot("packed", blocks.joinToString("\n") {
            if (it is Block.Image) "I ${it.ref}" else "P ${(it as Block.Paragraph).text}"
        })
        Wenku8Baselines.assertMatchesBaseline("packed", snap)
    }

    @Test
    fun `GBK 搜索词编码`() {
        assertEquals("%C4%A7%C5%AE", Wenku8Routes.gbkPercentEncode("魔女"))
    }

    @Test
    fun `标签结果卡片解析作者封面标签与状态`() {
        val html = """
            <html><body>
              <div style="width:373px;height:136px;float:left;margin:5px 0px 5px 5px;">
                <a href="/book/4505.htm" title="义妹生活"><img src="http://img.wenku8.com/image/4/4505/4505s.jpg"></a>
                <p>作者:戌淳/分类:其他文库</p>
                <p>更新:2026-11-06/字数:79K/已完结</p>
                <p>Tags:<span>科幻 穿越 校园</span></p>
                <p>简介:某段简介</p>
              </div>
              <div style="width:373px;height:136px;float:left;margin:5px 0px 5px 5px;">
                <a href="/book/4506.htm" title="无封面书"><img src="http://img.wenku8.com/image/noimg.gif"></a>
                <p>作者:某作者/分类:电击文库</p>
                <p>更新:2026-11-05/字数:12K/连载中</p>
              </div>
            </body></html>
        """.trimIndent()
        val items = Wenku8Parser.parseSearchResults(html)
        assertEquals(2, items.size)
        val first = items[0]
        assertEquals("戌淳", first.author)
        assertEquals("https://img.wenku8.com/image/4/4505/4505s.jpg", first.coverUrl)
        assertEquals(listOf("科幻", "穿越", "校园"), first.tags)
        assertEquals("已完结", first.status)
        val second = items[1]
        assertEquals("某作者", second.author)
        // noimg 视为无封面，按 bookId 推导图床地址（并升 https）
        assertEquals("https://img.wenku8.com/image/4/4506/4506s.jpg", second.coverUrl)
        assertEquals("连载中", second.status)
    }

    @Test
    fun `标签结果分页以站点页码链接为准`() {
        val card = """
            <div style="width:373px;height:136px;float:left;margin:5px">
              <a href="/book/1001.htm" title="书A"><img src="http://img.wenku8.com/image/1/1001/1001s.jpg"></a>
              <p>作者:甲/分类:其他文库</p>
            </div>
        """.trimIndent()
        val pager = """<div class="pages"><a href="tags.php?t=x&amp;page=2">2</a>
            <a href="tags.php?t=x&amp;page=50">50</a></div>"""
        val html = "<html><body>$card$pager</body></html>"
        val firstPage = Wenku8Parser.parseBrowsePage(html, 1)
        assertEquals(1, firstPage.items.size)
        assertTrue(firstPage.hasMore)
        assertFalse(Wenku8Parser.parseBrowsePage(html, 50).hasMore)
    }
}
