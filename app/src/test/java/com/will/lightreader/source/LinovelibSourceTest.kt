package com.will.lightreader.source

import com.will.lightreader.domain.Block
import com.will.lightreader.source.linovelib.LinovelibSource
import com.will.lightreader.source.net.HttpClient
import com.will.lightreader.source.net.PersistentCookieJar
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinovelibSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: LinovelibSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = HttpClient(
            jar = PersistentCookieJar(java.io.File.createTempFile("cookies", ".json")),
            sleep = {}, minIntervalMs = 0,
        )
        source = LinovelibSource(http, baseOverride = server.url("/").toString().removeSuffix("/"))
    }

    @After fun tearDown() = server.shutdown()

    private fun fixtureBody(name: String): MockResponse =
        MockResponse().setBody(javaClass.classLoader!!
            .getResourceAsStream("fixtures/linovelib/$name")!!.readBytes().decodeToString())

    @Test
    fun `search 请求 search 接口并解析列表`() = runBlocking {
        server.enqueue(fixtureBody("list-top.html"))
        val items = source.search("魔女")
        assertTrue(items.isNotEmpty())
        assertTrue(server.takeRequest().path!!.startsWith("/search.php?searchtype=articlename&searchkey="))
    }

    @Test
    fun `popular 请求榜单路径`() = runBlocking {
        server.enqueue(fixtureBody("list-top.html"))
        val items = source.popular("lastupdate")
        assertTrue(items.isNotEmpty())
        assertEquals("/top/lastupdate/1.html", server.takeRequest().path)
    }

    @Test
    fun `chapterContent 合并两页并跟随下一页`() = runBlocking {
        val p1 = """<html><body><div id="mlfy_main"><div id="mlfy_main_text"><div id="TextContent">
            <p>第二段</p><p>第一段</p></div></div>
            <div class="mlfy_page"><a href="/novel/3032/151118_2.html">下一页</a></div></div></body></html>"""
        val p2 = """<html><body><div id="mlfy_main"><div id="mlfy_main_text"><div id="TextContent">
            <p>第四段</p><p>第三段</p></div></div>
            <div class="mlfy_page"><a href="/novel/3032/151118.html">返回</a></div></div></body></html>"""
        server.enqueue(MockResponse().setBody(p1))
        server.enqueue(MockResponse().setBody(p2))

        val content = source.chapterContent("https://www.linovelib.com/novel/3032/151118.html")

        // cid=151118、页内 2 段（≤20 不洗牌）→ 保持页内顺序；两页顺序拼接
        val texts = content.blocks.map { (it as Block.Paragraph).text }
        assertEquals(listOf("第二段", "第一段", "第四段", "第三段"), texts)
        assertEquals(2, server.requestCount)
        assertTrue(server.takeRequest().path!!.endsWith("/novel/3032/151118.html"))
        assertTrue(server.takeRequest().path!!.endsWith("/novel/3032/151118_2.html"))
    }

    @Test
    fun `下架文案映射 TakenDown`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><body>抱歉，作品已下架！</body></html>"))
        try {
            source.chapterContent("https://www.linovelib.com/novel/3032/151118.html")
            throw AssertionError("应当抛 TakenDown")
        } catch (e: SourceError.TakenDown) { /* expected */ }
    }

    @Test
    fun `chapterList 目录优先，单请求拿全书`() = runBlocking {
        val catalog = """<html><body><div class="catalog-volume"><ul>
            <li class="chapter-li chapter-bar"><a>第一卷</a></li>
            <li class="chapter-li jsChapter"><a class="chapter-li-a" href="/novel/3032/100.html">第一章</a></li>
            <li class="chapter-li jsChapter"><a class="chapter-li-a" href="/novel/3032/101.html">第二章</a></li>
            </ul></div></body></html>"""
        server.enqueue(MockResponse().setBody(catalog))   // 目录
        val chapters = source.chapterList("https://www.linovelib.com/novel/3032.html")
        assertEquals(2, chapters.size)
        assertEquals("第一卷", chapters[0].volume)
        assertEquals("100", chapters[0].chapterId)
    }
}
