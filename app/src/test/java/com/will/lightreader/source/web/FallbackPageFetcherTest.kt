package com.will.lightreader.source.web

import com.will.lightreader.source.SourceError
import com.will.lightreader.source.net.FetchedPage
import com.will.lightreader.source.net.PageFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.nio.charset.Charset

class FallbackPageFetcherTest {
    @Test fun `blocked browser response preserves its recovery meaning and does not loop`() = runBlocking {
        val primary = Fake { throw SourceError.ChallengeRequired("http://x", accessBlocked = true) }
        val browser = Fake(true) { throw SourceError.ChallengeRequired("http://x", accessBlocked = true) }
        val chain = FallbackPageFetcher(primary, browser)
        repeat(2) {
            try { chain.getText("http://x"); fail("Blocked content must not be returned") }
            catch (e: SourceError.ChallengeRequired) { assertTrue(e.accessBlocked) }
        }
        assertEquals(1, primary.calls)
        assertEquals(2, browser.calls)
    }
    private class Fake(private val processed: Boolean = false, private val result: () -> String) : PageFetcher {
        var calls = 0
        override suspend fun getText(url: String, charset: Charset): FetchedPage {
            calls++
            return FetchedPage(result(), processed)
        }
    }

    @Test
    fun `主路径成功不惊动兜底`() = runBlocking {
        val primary = Fake { "ok" }
        val fallback = Fake(true) { "web" }
        val page = FallbackPageFetcher(primary, fallback).getText("http://x")
        assertEquals("ok", page.text)
        assertFalse(page.browserProcessed)   // OkHttp 原始 HTML：站点乱序需还原
        assertEquals(1, primary.calls)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `被拦截时降级 WebView 并标记浏览器已处理`() = runBlocking {
        val primary = Fake { throw SourceError.ChallengeRequired("http://x") }
        val fallback = Fake(true) { "<html>web</html>" }
        val page = FallbackPageFetcher(primary, fallback).getText("http://x")
        assertEquals("<html>web</html>", page.text)
        assertTrue(page.browserProcessed)    // WebView DOM：站点 JS 已修序，不能再还原
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `需登录也降级`() = runBlocking {
        val primary = Fake { throw SourceError.AuthRequired("http://x") }
        val fallback = Fake(true) { "web" }
        assertEquals("web", FallbackPageFetcher(primary, fallback).getText("http://x").text)
    }

    @Test
    fun `网络错误不降级照常上抛`() = runBlocking {
        val primary = Fake { throw SourceError.Network(IOException("断网")) }
        val fallback = Fake(true) { "web" }
        try {
            FallbackPageFetcher(primary, fallback).getText("http://x")
            fail("应当抛 Network")
        } catch (e: SourceError.Network) { /* expected */ }
        assertEquals(0, fallback.calls)
    }

    /** 按调用次数决定行为的假取页器，用来模拟「先被拦、后来好了」。 */
    private class Scripted(private val step: (Int) -> String) : PageFetcher {
        var calls = 0
        override suspend fun getText(url: String, charset: Charset): FetchedPage {
            calls++
            return FetchedPage(step(calls), true)
        }
    }

    @Test
    fun `被挑战后熔断不再逐请求试探`() = runBlocking {
        var clock = 0L
        val primary = Scripted { throw SourceError.ChallengeRequired("http://x") }
        val fallback = Scripted { "web" }
        val chain = FallbackPageFetcher(primary, fallback, blockedTtlMs = 60_000, now = { clock })

        repeat(4) { assertEquals("web", chain.getText("http://x").text) }

        assertEquals(1, primary.calls)      // 只挨了一次 403，后面直接进兜底
        assertEquals(4, fallback.calls)
    }

    @Test
    fun `熔断冷却过后重新试探主路径`() = runBlocking {
        var clock = 0L
        val primary = Scripted { n ->
            if (n == 1) throw SourceError.ChallengeRequired("http://x") else "ok"
        }
        val fallback = Scripted { "web" }
        val chain = FallbackPageFetcher(primary, fallback, blockedTtlMs = 1_000, now = { clock })

        assertEquals("web", chain.getText("http://x").text)
        assertEquals("web", chain.getText("http://x").text)
        assertEquals(1, primary.calls)

        clock = 1_001
        assertEquals("ok", chain.getText("http://x").text)
        assertEquals(2, primary.calls)
    }

    @Test
    fun `网络错误不开启熔断`() = runBlocking {
        val primary = Fake { throw SourceError.Network(IOException("断网")) }
        val fallback = Fake(true) { "web" }
        val chain = FallbackPageFetcher(primary, fallback)

        repeat(2) { runCatching { chain.getText("http://x") } }

        assertEquals(2, primary.calls)
        assertEquals(0, fallback.calls)
    }
}
