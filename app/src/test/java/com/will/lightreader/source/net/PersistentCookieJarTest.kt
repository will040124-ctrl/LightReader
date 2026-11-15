package com.will.lightreader.source.net

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentCookieJarTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val url = "https://www.wenku8.net/book/1973.htm".toHttpUrl()

    @Test
    fun `存入后新实例能读回 cookie`() {
        val file = tmp.newFile("cookies.json")
        val jar1 = PersistentCookieJar(file)
        val cookie = Cookie.Builder().name("PHPSESSID").value("abc").hostOnlyDomain("www.wenku8.net").build()
        jar1.saveFromResponse(url, listOf(cookie))

        val jar2 = PersistentCookieJar(file)
        val loaded = jar2.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("abc", loaded[0].value)
    }

    @Test
    fun `过期 cookie 不参与请求`() {
        val file = tmp.newFile("cookies.json")
        val jar = PersistentCookieJar(file)
        val expired = Cookie.Builder().name("old").value("x").hostOnlyDomain("www.wenku8.net")
            .expiresAt(System.currentTimeMillis() - 60_000).build()
        jar.saveFromResponse(url, listOf(expired))
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `importRaw 能导入浏览器复制的 cookie 串`() {
        val file = tmp.newFile("cookies.json")
        val jar = PersistentCookieJar(file)
        jar.importRaw("www.wenku8.net", "PHPSESSID=abc; jieqiUserInfo=xyz")
        val names = jar.loadForRequest(url).map { it.name }.toSet()
        assertEquals(setOf("PHPSESSID", "jieqiUserInfo"), names)
    }
}
