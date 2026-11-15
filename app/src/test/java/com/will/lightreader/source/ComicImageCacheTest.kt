package com.will.lightreader.source
import com.will.lightreader.source.script.ComicImageCache
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
class ComicImageCacheTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun `same image coalesces simultaneous consumers and survives restart`() = runTest {
        val root=temp.newFolder();val cache=ComicImageCache(root);var fetched=0;val key="a".repeat(64)
        val hits=coroutineScope { (1..8).map { async { cache.get(key) { fetched++;delay(100);byteArrayOf(1,2,3) } } }.awaitAll() }
        assertEquals(1,fetched);assertEquals(7,hits.count { it.disk })
        assertArrayEquals(byteArrayOf(1,2,3),ComicImageCache(root).get(key,network=false) { error("must not fetch") }.bytes)
    }
    @Test fun `failure is not cached and disabled disk reads cannot serve stale bytes`() = runTest {
        val cache=ComicImageCache(temp.newFolder());val key="b".repeat(64)
        try { cache.get(key) { throw java.io.IOException("offline") };fail() } catch(_:java.io.IOException) { }
        assertFalse(cache.get(key) { byteArrayOf(4) }.disk)
        assertArrayEquals(byteArrayOf(5),cache.get(key,read=false) { byteArrayOf(5) }.bytes)
        try { cache.get(key,read=false,network=false) { fail();byteArrayOf() };fail() } catch(_:IllegalArgumentException) { }
    }
    @Test fun `a rapid burst respects the disk budget without waiting for another request`() = runTest {
        val root=temp.newFolder();val cache=ComicImageCache(root,maxBytes=6)
        val one="c".repeat(64);val two="d".repeat(64)
        cache.get(one) { byteArrayOf(1,2,3,4) }
        cache.get(two) { byteArrayOf(5,6,7,8) }
        assertTrue(root.listFiles()!!.sumOf { it.length() }<=6)
        assertTrue(cache.get(two,network=false) { error("network") }.disk)
        try { cache.get(one,network=false) { error("network") };fail() } catch(_:IllegalArgumentException) { }
    }
}
