package com.will.lightreader.source

import com.will.lightreader.source.net.HttpClient
import com.will.lightreader.source.net.PersistentCookieJar
import com.will.lightreader.source.wenku8.Wenku8Source
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

object Wenku8SourceTestSupport {
    val gb18030 = java.nio.charset.Charset.forName("GB18030")
}

class Wenku8SourceTest {
    @Test(expected = SourceError.ParseChanged::class)
    fun `partial DOM containing only a chapter title is never treated as body`() {
        com.will.lightreader.source.wenku8.Wenku8Parser.parseChapter(
            "<div id='contentmain'><div id='title'>第一卷 第一章 开始</div><div id='adv900'><script src='ad.js'></script></div></div>",
            "https://www.wenku8.net/novel/3/3057/125417.htm", "第一章 开始")
    }
    private lateinit var server: MockWebServer
    private lateinit var source: Wenku8Source

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = HttpClient(PersistentCookieJar(java.io.File.createTempFile("cookies", ".json")), sleep = {}, minIntervalMs = 0)
        source = Wenku8Source(http, baseOverride = server.url("/").toString().removeSuffix("/"))
    }

    @After fun tearDown() = server.shutdown()

    private fun gbBody(html: String): MockResponse =
        MockResponse().setBody(Buffer().write(html.toByteArray(Wenku8SourceTestSupport.gb18030)))

    @Test
    fun `search 用 GBK 百分号编码并按 GB18030 解码响应`() = runBlocking {
        server.enqueue(gbBody("<html><body><a href=\"/book/1973.htm\" title=\"书名\">书名</a></body></html>"))
        val items = source.search("魔女")
        assertEquals(1, items.size)
        val path = server.takeRequest().path!!
        assertTrue(path.contains("searchkey=%C4%A7%C5%AE"))
    }

    @Test
    fun `版权下架映射 TakenDown`() = runBlocking {
        server.enqueue(gbBody("<html><body>因版权问题，该书不再提供</body></html>"))
        try {
            source.chapterContent("https://www.wenku8.net/novel/0/1973/1.htm")
            throw AssertionError("应当抛 TakenDown")
        } catch (e: SourceError.TakenDown) { /* expected */ }
    }

    @Test
    fun `null 正文走下载包兜底`() = runBlocking {
        server.enqueue(gbBody("<html><body><div id=\"content\"><span>null</span></div></body></html>"))
        server.enqueue(MockResponse().setBody("第一段。\n\nhttps://img.wenku8.com/1.jpg(100K)\n\n第二段。"))
        val withPack = Wenku8Source(
            http = HttpClient(PersistentCookieJar(java.io.File.createTempFile("cookies", ".json")), sleep = {}, minIntervalMs = 0),
            baseOverride = server.url("/").toString().removeSuffix("/"),
            packedOverride = server.url("/pack").toString(),
        )
        val content = withPack.chapterContent("https://www.wenku8.net/novel/0/1973/1.htm")
        assertEquals(3, content.blocks.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `标签筛选映射站点参数 v1 热门 v2 完结`() = runBlocking {
        val listHtml = """<html><body><div style="width:373px;height:136px;float:left;margin:5px">
            <a href="/book/1001.htm" title="书A"><img src="http://img.wenku8.com/image/1/1001/1001s.jpg"></a>
            <p>作者:甲/分类:其他文库</p></div></body></html>"""
        server.enqueue(gbBody(listHtml))
        source.tagBooks("校园", 1, completedOnly = false, popular = true)
        val popularPath = server.takeRequest().path!!
        assertTrue(popularPath, popularPath.contains("v=1"))

        server.enqueue(gbBody(listHtml))
        source.tagBooks("校园", 2, completedOnly = true)
        val completedPath = server.takeRequest().path!!
        assertTrue(completedPath, completedPath.contains("v=2"))
        assertTrue(completedPath, completedPath.contains("page=2"))
    }

    @Test
    fun `chapterList 目录 URL 直接解析`() = runBlocking {
        server.enqueue(gbBody("""<html><body><table>
            <tr><td colspan="3">第一卷</td></tr>
            <tr><td><a href="/novel/2/2000/70709.htm">第一章 莉莉</a></td></tr>
            </table></body></html>"""))
        val chapters = source.chapterList("https://www.wenku8.net/novel/2/2000/index.htm")
        assertEquals(1, chapters.size)
        assertEquals("第一卷", chapters[0].volume)
    }
}
