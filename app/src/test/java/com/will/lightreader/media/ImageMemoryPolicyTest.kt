package com.will.lightreader.media
import org.junit.Assert.*
import org.junit.Test
class ImageMemoryPolicyTest {
    @Test fun `cache follows the application heap and stays bounded`() {
        assertEquals(96*1024*1024,imageMemoryCacheBytes(8192))
        assertEquals(8*1024*1024,imageMemoryCacheBytes(-1))
        assertTrue(imageMemoryCacheBytes(128) < imageMemoryCacheBytes(256))
    }
    @Test fun `decode remains within six megapixels on oversized viewports`() {
        for ((w,h) in listOf(1080 to 1920,2800 to 1752,Int.MAX_VALUE to Int.MAX_VALUE,0 to -1)) {
            val size=comicPageDecodeBounds(w,h)
            assertTrue(size.width>0 && size.height>0)
            assertTrue(size.width.toLong()*size.height<=6_000_000L)
        }
    }
    @Test fun `normal viewport preserves its aspect ratio and offers zoom headroom`() {
        val size=comicPageDecodeBounds(600,800)
        assertEquals(1200,size.width);assertEquals(1600,size.height)
    }
}
