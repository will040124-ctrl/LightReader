package com.will.lightreader.source.linovelib

import com.will.lightreader.domain.Block
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LinovelibParserTest {
    @Test
    fun `详情页解析与基线一致`() {
        val file = FixtureBaselines.firstFixtureName("novel-")
        val nid = Regex("novel-(\\d+)").find(file)!!.groupValues[1]
        val novel = LinovelibParser.parseNovelDetail(FixtureBaselines.fixture(file), nid)
        assertTrue(novel.detail.title.isNotBlank())
        // 注意：部分年代的详情页不带卷列表（只有「开始阅读 → /catalog」），volumes 允许为空
        val snap = FixtureBaselines.snapshot("novel-$nid",
            "title=${novel.detail.title}\nauthor=${novel.detail.author}\ncover=${novel.detail.coverUrl}\n" +
                "tags=${novel.detail.tags}\nupdatedAt=${novel.detail.updatedAt}\nintro=${novel.detail.intro.take(120)}\n" +
                novel.volumes.joinToString("\n") { "vol ${it.vid} ${it.title} ${it.coverUrl}" })
        FixtureBaselines.assertMatchesBaseline("novel-detail", snap)
    }

    @Test
    fun `目录页或卷页解析出章节（样本缺则跳过）`() {
        val catalogName = FixtureBaselines.optionalFixtureName("catalog-")
        if (catalogName != null) {
            val nid = Regex("catalog-(\\d+)").find(catalogName)!!.groupValues[1]
            val chapters = LinovelibParser.parseCatalog(FixtureBaselines.fixture(catalogName), nid)
            assertTrue(chapters.isNotEmpty())
            val snap = FixtureBaselines.snapshot("catalog", chapters.joinToString("\n") { "${it.volume} | ${it.chapterId} | ${it.title}" })
            FixtureBaselines.assertMatchesBaseline("catalog-chapters", snap)
            return
        }
        val volFile = FixtureBaselines.optionalFixtureName("vol-")
        assumeTrue("catalog/vol 样本均未采集则跳过", volFile != null)
        val m = Regex("vol-(\\d+)-(\\d+)").find(volFile!!)!!
        val chapters = LinovelibParser.parseVolumeChapters(
            FixtureBaselines.fixture(volFile), m.groupValues[1], m.groupValues[2], "测试卷")
        assertTrue(chapters.isNotEmpty())
        val snap = FixtureBaselines.snapshot("vol", chapters.joinToString("\n") { "${it.chapterId} ${it.title} ${it.chapterUrl}" })
        FixtureBaselines.assertMatchesBaseline("vol-chapters", snap)
    }

    @Test
    fun `章节页正文出块且乱序已还原`() {
        val file = FixtureBaselines.firstFixtureName("chapter-")
        val m = Regex("chapter-(\\d+)-(\\d+)").find(file)!!
        val page = LinovelibParser.parseChapterPage(FixtureBaselines.fixture(file), m.groupValues[1], m.groupValues[2])
        assertTrue(page.blocks.isNotEmpty())
        assertTrue(page.blocks.any { it is Block.Paragraph && it.text.isNotBlank() })
        val snap = FixtureBaselines.snapshot("chapter",
            "title=${page.title}\nnext=${page.nextPageUrl}\n" + page.blocks.joinToString("\n") {
                when (it) {
                    is Block.Paragraph -> "P ${it.text}"
                    is Block.Image -> "I ${it.ref} ${it.caption ?: ""}"
                    Block.Divider -> "D"
                }
            })
        FixtureBaselines.assertMatchesBaseline("chapter-page", snap)
    }

    @Test
    fun `第二个章节样本解析不抛错`() {
        val names = FixtureBaselines.fixtureNames("chapter-")
        assumeTrue("只有一个章节样本则跳过", names.size > 1)
        val m = Regex("chapter-(\\d+)-(\\d+)").find(names[1])!!
        val page = LinovelibParser.parseChapterPage(FixtureBaselines.fixture(names[1]), m.groupValues[1], m.groupValues[2])
        assertTrue(page.blocks.isNotEmpty())
    }

    @Test
    fun `列表页解析出书目（样本缺则跳过单个）`() {
        var checked = 0
        for (name in listOf("list-wenku.html", "list-top.html", "search.html")) {
            val html = FixtureBaselines.optionalFixture(name) ?: continue
            checked++
            val items = LinovelibParser.parseBookList(html, LinovelibRoutes.BASE)
            assertTrue("$name 至少一条", items.isNotEmpty())
            val snap = FixtureBaselines.snapshot(name, items.joinToString("\n") { "${it.bookId} ${it.title} ${it.author} ${it.coverUrl}" })
            FixtureBaselines.assertMatchesBaseline("list-" + name.removeSuffix(".html"), snap)
        }
        assumeTrue("一个列表样本都没有则跳过", checked > 0)
    }
}
