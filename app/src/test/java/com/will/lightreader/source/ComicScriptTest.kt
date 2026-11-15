package com.will.lightreader.source

import com.will.lightreader.source.script.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicScriptTest {
    @Test fun `wildcard grants require private domains and cannot match siblings root hosts or suffix attacks`() {
        assertTrue(validComicDomainPermission("*.mangadex.network"))
        assertTrue(comicDomainAllowed("node.mangadex.network",listOf("*.mangadex.network")))
        for(host in listOf("mangadex.network","evil-mangadex.network","node.mangadex.network.attacker.com"))
            assertFalse(comicDomainAllowed(host,listOf("*.mangadex.network")))
        assertFalse(comicDomainAllowed("node.mangadex.network",listOf("mangadex.network")))
        for(grant in listOf("*.com","*.co.uk","*.github.io","*.127.0.0.1","*.*.example.com"))assertFalse(grant,validComicDomainPermission(grant))
        val network=ComicScriptNetwork()
        network.validate("https://node.mangadex.network/page",listOf("*.mangadex.network"))
        for(url in listOf("http://node.mangadex.network/page","https://node.mangadex.network:8443/page","https://other.network/page")) {
            try { network.validate(url,listOf("*.mangadex.network"));fail("must reject") } catch(_:IllegalArgumentException) { }
        }
    }
    @Test fun `binary transport preserves bytes in both directions`() = runTest {
        MockWebServer().use { server ->
            val bytes=byteArrayOf(0,-1,-128,65)
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
            val url=server.url("/bytes").newBuilder().host("127.0.0.1").build().toString()
            val result=ComicScriptNetwork(true).request(url,listOf("127.0.0.1"),"POST",bodyBytes=bytes)
            assertArrayEquals(bytes,result.bytes)
            assertArrayEquals(bytes,server.takeRequest().body.readByteArray())
        }
    }
    @Test fun `repository accepts actual and documented filename spellings but rejects conflicts and traversal`() {
        val url="https://sources.example/root/index.json";val network=ComicScriptNetwork()
        for(key in listOf("fileName","filename")) {
            val result=parseComicRepository("""[{"name":"Example","$key":"a/source.js","version":"1.0.0"}]""",url,network)
            assertEquals("https://sources.example/root/a/source.js",result.single().url)
        }
        for(text in listOf("""[{"fileName":"../escape.js"}]""","""[{"fileName":"a.js","filename":"b.js"}]""",
            """[{"url":"https://127.0.0.1/source.js"}]""","""[{"url":"https://sources.example/a.js","fileName":"b.js"}]""")) {
            try { parseComicRepository(text,url,network);fail("must reject index") } catch(_:IllegalArgumentException) { }
        }
    }
    @Test fun `image cache identity cannot alias headers containing map punctuation`() {
        assertEquals(comicImageIdentity("source","https://example.com/a",mapOf("Referer" to "https://example.com/")),
            comicImageIdentity("source","https://example.com/a",mapOf("referer" to "https://example.com/")))
        assertNotEquals(comicImageIdentity("source","https://example.com/a",linkedMapOf("A" to "x","a" to "y")),
            comicImageIdentity("source","https://example.com/a",mapOf("a" to "x,y")))
        assertEquals(comicImageIdentity("source","https://example.com/a",linkedMapOf("A" to "x","a" to "y")),
            comicImageIdentity("source","https://example.com/a",mapOf("a" to "y")))
        assertNotEquals(comicImageIdentity("source","https://example.com/a",mapOf("A" to "x, B=y")),
            comicImageIdentity("source","https://example.com/a",mapOf("A" to "x","B" to "y")))
        assertNotEquals(comicImageIdentity("one","https://example.com/a",emptyMap()),comicImageIdentity("two","https://example.com/a",emptyMap()))
    }
    @get:Rule val temp=TemporaryFolder()
    private fun pkg(version:String="1.0.0")=ComicScriptPackage("sample","示例",version,"Example","class Example extends ComicSource {}",listOf("example.com"))
    @Test fun `install enable restart update and rollback retain the correct package`() {
        val root=temp.newFolder();val store=ComicScriptStore(root)
        store.install(pkg());store.setEnabled("sample",false)
        assertFalse(ComicScriptStore(root).get("sample")!!.enabled)
        store.install(pkg("1.2.0").copy(enabled=false))
        assertEquals("1.2.0",store.get("sample")!!.version)
        store.rollback("sample")
        assertEquals("1.0.0",store.get("sample")!!.version);assertFalse(store.get("sample")!!.enabled)
    }
    @Test fun `invalid or same-version update cannot replace an installed source`() {
        val store=ComicScriptStore(temp.newFolder());store.install(pkg())
        for(value in listOf(pkg().copy(domains=emptyList()),pkg(),pkg("0.9.0"))) {
            try { store.install(value);fail("must reject") } catch(_:IllegalArgumentException) { }
        }
        assertEquals(pkg(),store.get("sample"))
    }
    @Test fun `permission edits survive restart and rollback without restoring revoked hosts`() {
        val root=temp.newFolder();val store=ComicScriptStore(root)
        store.install(pkg());store.install(pkg("1.1.0"))
        store.setDomains("sample",listOf("images.example.com"))
        assertEquals(listOf("images.example.com"),ComicScriptStore(root).get("sample")!!.domains)
        try { store.setDomains("sample",emptyList());fail("must reject") } catch(_:IllegalArgumentException) { }
        store.rollback("sample")
        assertEquals(listOf("images.example.com"),store.get("sample")!!.domains)
        assertEquals("1.0.0",store.get("sample")!!.version)
    }
    @Test fun `source key cannot escape storage directory`() {
        try { pkg().copy(key="../outside").validate();fail("must reject") } catch(_:IllegalArgumentException) { }
    }
    @Test fun `versions compare numerically and source removal keeps unrelated files`() {
        assertTrue(newerComicVersion("1.10.0","1.9.0"))
        val root=temp.newFolder();val unrelated=java.io.File(root,"library.keep").apply { writeText("keep") }
        val store=ComicScriptStore(root);store.install(pkg());store.remove("sample")
        assertTrue(store.all().isEmpty());assertEquals("keep",unrelated.readText())
    }
    @Test fun `comic identifiers roundtrip punctuation unicode and slashes without crossing sources`() {
        val raw="作品/a?1#二"
        val book=ComicScriptRoutes.book("comic-js-test",raw)
        assertEquals(raw,ComicScriptRoutes.bookId(book,"comic-js-test"))
        assertEquals(raw to "话/2",ComicScriptRoutes.episodeIds(ComicScriptRoutes.episode("comic-js-test",raw,"话/2"),"comic-js-test"))
        try { ComicScriptRoutes.bookId(book,"comic-js-other");fail("must reject") } catch(_:IllegalArgumentException) { }
    }
    @Test fun `production transport refuses private IP cleartext and undeclared hosts`() {
        val network=ComicScriptNetwork()
        for(url in listOf("https://127.0.0.1/test","http://example.com/test","https://other.example/test","https://user:pass@example.com/test")) {
            try { network.validate(url,listOf("example.com","127.0.0.1"));fail(url) } catch(_:IllegalArgumentException) { }
        }
    }
    @Test fun `redirect cannot escape declared domains and oversize responses are rejected`() = runTest {
        MockWebServer().use { server ->
            val network=ComicScriptNetwork(true)
            val url=server.url("/").newBuilder().host("127.0.0.1").build().toString()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","https://outside.example/image"))
            try { network.request(url,listOf("127.0.0.1"));fail("must reject redirect") } catch(_:IllegalArgumentException) { }
            assertEquals(1,server.requestCount)
            server.enqueue(MockResponse().setBody("x".repeat(100)))
            try { network.request(url,listOf("127.0.0.1"),limit=16);fail("must reject body") } catch(_:IllegalArgumentException) { }
        }
    }
    @Test fun `cancel after response headers aborts a stalled body`() = kotlinx.coroutines.runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(1024)).throttleBody(1,5,java.util.concurrent.TimeUnit.SECONDS))
            val url=server.url("/").newBuilder().host("127.0.0.1").build().toString()
            val job=launch(kotlinx.coroutines.Dispatchers.IO) { ComicScriptNetwork(true).request(url,listOf("127.0.0.1"),image=true) }
            assertNotNull(server.takeRequest(3,java.util.concurrent.TimeUnit.SECONDS))
            kotlinx.coroutines.delay(50)
            val start=System.nanoTime();job.cancel();job.join()
            assertTrue((System.nanoTime()-start)/1_000_000<1500)
        }
    }
}
