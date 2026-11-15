package com.will.lightreader.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ComicScrollAnchorTest {
    @Test fun `strip item heights are bounded without changing normal image proportions`() {
        assertEquals(2f/3f,comicStripAspect(600,900,1440),.0001f)
        assertTrue(1440/comicStripAspect(100,1000000,1440)<=200001f)
        assertEquals(2f/3f,comicStripAspect(0,0,1440),.0001f)
    }
    @Test fun `image position restores proportionally after screen width changes`() {
        val fraction=comicScrollFraction(900,3000)
        assertEquals(.3f,fraction,.0001f)
        assertEquals(450,comicScrollOffset(fraction,1500))
    }
    @Test fun `invalid and edge offsets stay within an image`() {
        assertEquals(0,comicScrollOffset(Float.NaN,100))
        assertEquals(0,comicScrollOffset(Float.POSITIVE_INFINITY,100))
        assertEquals(99,comicScrollOffset(2f,100))
        assertEquals(0f,comicScrollFraction(10,0),0f)
        assertEquals(0f,comicScrollFraction(-10,100),0f)
        assertEquals(1f,comicScrollFraction(200,100),0f)
    }
}
