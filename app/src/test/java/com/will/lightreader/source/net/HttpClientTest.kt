package com.will.lightreader.source.net

import com.will.lightreader.source.SourceError
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketException

class HttpClientTest {
    @Test fun `successful HTTP code carrying a block page still requires a real browser check`() = runBlocking {
        server.enqueue(MockResponse().setBody("<title>Attention Required! | Cloudflare</title><div id='cf-error-details'>Sorry, you have been blocked</div>"))
        try {
            client().getText(server.url("/tags").toString())
            fail("A block page must not become book content")
        } catch (e: SourceError.ChallengeRequired) { assertTrue(e.accessBlocked) }
    }
    @Test fun `form POST preserves UTF8 fields without changing to GET`() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"code\":0}"))
        client().postForm(server.url("/directory").toString(),mapOf("bookId" to "123","query" to "书 & 信"))
        val request=server.takeRequest()
        assertEquals("POST",request.method)
        assertTrue(request.body.readUtf8().contains("query=%E4%B9%A6%20%26%20%E4%BF%A1"))
    }
    @Test fun `cancel aborts a stalled response without waiting for read timeout`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val task = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            client().getText(server.url("/slow").toString())
        }
        assertTrue(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS) != null)
        kotlinx.coroutines.withTimeout(1500) { task.cancel(); task.join() }
        assertTrue(task.isCancelled)
    }
    private lateinit var server: MockWebServer
    private val sleeps = mutableListOf<Long>()

    @Before fun setUp() { server = MockWebServer(); server.start(); sleeps.clear() }
    @After fun tearDown() { server.shutdown() }

    private fun client() = HttpClient(
        jar = PersistentCookieJar(java.io.File.createTempFile("cookies", ".json")),
        sleep = { ms -> sleeps.add(ms) },
        minIntervalMs = 0,
    )

    @Test
    fun `200 返回正文`() = runBlocking {
        server.enqueue(MockResponse().setBody("hello"))
        assertEquals("hello", client().getText(server.url("/a").toString()).text)
    }

    @Test
    fun `403 云朵挑战页映射为 ChallengeRequired`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("<html>Just a moment...cf-chl</html>"))
        try {
            client().getText(server.url("/a").toString())
            fail("应当抛 ChallengeRequired")
        } catch (e: SourceError.ChallengeRequired) { /* expected */ }
    }

    @Test
    fun `429 先退避重试，第三次成功`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", client().getText(server.url("/a").toString()).text)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1500L, 3000L), sleeps)
    }

    @Test
    fun `429 连续失败映射为 RateLimited`() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(429)) }
        try {
            client().getText(server.url("/a").toString())
            fail("应当抛 RateLimited")
        } catch (e: SourceError.RateLimited) { /* expected */ }
    }

    @Test
    fun `连接错误映射为 Network`() = runBlocking {
        val port = server.port
        server.shutdown()
        try {
            client().getText("http://127.0.0.1:$port/a")
            fail("应当抛 Network")
        } catch (e: SourceError.Network) {
            assertTrue(e.cause is SocketException || e.cause is IOException)
        }
    }

    @Test
    fun `GB18030 正文按指定字符集解码`() = runBlocking {
        val gb = java.nio.charset.Charset.forName("GB18030")
        server.enqueue(MockResponse().setBody(okio.Buffer().write("轻小说文库".toByteArray(gb))))
        assertEquals("轻小说文库", client().getText(server.url("/a").toString(), gb).text)
    }
}
