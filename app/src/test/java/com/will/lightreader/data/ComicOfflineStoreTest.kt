package com.will.lightreader.data

import com.will.lightreader.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicOfflineStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private fun content(count:Int=3)=ChapterContent("https://example.com/episode","话",emptyList(),comicPages=(1..count).map { ComicPage("page-$it","https://images.example.com/$it.png") })
    private fun bytes(page:ComicPage)=byteArrayOf(42,page.id.last().code.toByte(),3,4)
    private fun store(root:java.io.File,limit:Long=1024*1024)=ComicOfflineStore(root,limit,parallelism=1) { it.firstOrNull()==42.toByte() }
    @Test fun `removing one episode preserves other episodes and unrelated files`() = runTest {
        val root=temp.newFolder();val cache=store(root);val one=content(1);val two=one.copy(chapterUrl=one.chapterUrl+"-other")
        cache.download(one,fetch=::bytes);cache.download(two,fetch=::bytes)
        val keep=java.io.File(root,"keep.txt").apply { writeText("keep") }
        cache.delete(one.chapterUrl)
        assertNull(cache.read(one.chapterUrl));assertNotNull(cache.read(two.chapterUrl));assertEquals("keep",keep.readText())
    }
    @Test fun `partial failure is never complete and a fresh store resumes only missing pages`() = runTest {
        val root=temp.newFolder();val value=content();val first=store(root)
        try { first.download(value) { if(it.id=="page-2")throw java.io.IOException("offline") else bytes(it) };fail("must fail") } catch(_:java.io.IOException) { }
        assertNull(first.read(value.chapterUrl))
        val fetched=mutableListOf<String>();val result=store(root).download(value) { fetched+=it.id;bytes(it) }
        assertEquals(listOf("page-2","page-3"),fetched)
        assertTrue(result.comicPages.all { java.io.File(java.net.URI(it.localRef!!)).isFile })
        assertEquals(value.comicPages.map { it.id },store(root).read(value.chapterUrl)!!.comicPages.map { it.id })
    }
    @Test fun `cancellation keeps completed pages but cannot publish an incomplete episode`() = runTest {
        val root=temp.newFolder();val value=content()
        try { store(root).download(value) { if(it.id=="page-2")throw CancellationException("cancel") else bytes(it) };fail("must cancel") } catch(_:CancellationException) { }
        assertNull(store(root).read(value.chapterUrl))
        var count=0;store(root).download(value) { count++;bytes(it) }
        assertEquals(2,count)
    }
    @Test fun `same size corruption is detected and repair fetches only the damaged image`() = runTest {
        val root=temp.newFolder();val value=content();val first=store(root)
        val saved=first.download(value,fetch=::bytes)
        java.io.File(java.net.URI(saved.comicPages[1].localRef!!)).writeBytes(byteArrayOf(42,0,3,4))
        assertNull(first.read(value.chapterUrl))
        val fetched=mutableListOf<String>();first.download(value) { fetched+=it.id;bytes(it) }
        assertEquals(listOf("page-2"),fetched)
        assertNotNull(first.read(value.chapterUrl))
    }
    @Test fun `a failed refresh preserves the previous complete manifest`() = runTest {
        val root=temp.newFolder();val first=store(root);val old=content(1)
        first.download(old,fetch=::bytes)
        try { first.download(content(2)) { if(it.id=="page-2")throw java.io.IOException("bad") else bytes(it) };fail("must fail") } catch(_:java.io.IOException) { }
        assertEquals(1,first.read(old.chapterUrl)!!.comicPages.size)
    }
    @Test fun `quota and invalid image reject completion and local paths are not serialized`() = runTest {
        val root=temp.newFolder();val value=content(1)
        try { store(root,1).download(value,fetch=::bytes);fail("must reject quota") } catch(_:IllegalArgumentException) { }
        assertNull(store(root).read(value.chapterUrl))
        try { store(root).download(value) { byteArrayOf(0) };fail("must reject image") } catch(_:IllegalArgumentException) { }
        val page=value.comicPages.first().copy(localRef="file:///private/path",requestHeaders=mapOf("Authorization" to "test-secret"))
        val encoded=Json.encodeToString(ComicPage.serializer(),page)
        assertFalse(encoded.contains("private/path"));assertFalse(encoded.contains("test-secret"))
    }
    @Test fun `parallel workers are bounded and retain original page order`() = runTest {
        val value=content(9);var active=0;var peak=0
        val cache=ComicOfflineStore(temp.newFolder(), parallelism=3) { it.firstOrNull()==42.toByte() }
        val saved=cache.download(value) {
            active++;peak=maxOf(peak,active)
            kotlinx.coroutines.delay((10-it.id.last().digitToInt())*10L)
            active--;bytes(it)
        }
        assertEquals(3,peak)
        assertEquals(value.comicPages.map { it.id },saved.comicPages.map { it.id })
        assertNotNull(cache.read(value.chapterUrl))
    }
}
