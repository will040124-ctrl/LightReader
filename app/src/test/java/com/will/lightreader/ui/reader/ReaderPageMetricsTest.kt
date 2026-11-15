package com.will.lightreader.ui.reader
import org.junit.Assert.*
import org.junit.Test
class ReaderPageMetricsTest {
    @Test fun `single page uses the current window and never overflows compact devices`() {
        for (width in listOf(240f,320f,360f,411f,600f,875f,1400f)) {
            val m=readerPageMetrics(width,700f,18,1f,false)
            assertFalse(m.twoPage);assertTrue(m.contentWidthDp<=width)
            assertTrue(m.contentWidthDp-m.sidePaddingDp*2>0)
        }
    }
    @Test fun `single page follows the entire window at every font size`() {
        val small=readerPageMetrics(1400f,850f,13,1f,false)
        val normal=readerPageMetrics(1400f,850f,18,1f,false)
        assertEquals(1400f,normal.contentWidthDp,.01f)
        assertEquals(normal.contentWidthDp,small.contentWidthDp,.01f)
        for(width in listOf(360f,780f,1100f))assertEquals(width,readerPageMetrics(width,700f,18,1f,false).contentWidthDp,.01f)
    }
    @Test fun `short landscape windows get compact headings and reduced top padding`() {
        val m=readerPageMetrics(900f,360f,18,1f,true)
        assertFalse(m.twoPage);assertTrue(m.compactHeading);assertEquals(1,m.titleLines)
        assertEquals(12f,m.topPaddingDp,.01f)
    }
    @Test fun `large text keeps its selected size and a readable wide column`() {
        val normal=readerPageMetrics(1400f,850f,18,1f,true)
        val large=readerPageMetrics(1400f,850f,32,1.5f,true)
        assertFalse(large.twoPage);assertTrue(large.contentWidthDp>normal.contentWidthDp)
    }
    @Test fun `footer clearance includes navigation and growing text`() {
        assertTrue(readerPageMetrics(360f,700f,18,1f,false,48f).bottomPaddingDp>=114f)
        assertTrue(readerPageMetrics(360f,700f,18,3f,false).bottomPaddingDp>=82f)
    }
    @Test fun `wide and tall enough windows retain a true two-page spread`() {
        val m=readerPageMetrics(1400f,850f,18,1f,true)
        assertTrue(m.twoPage);assertEquals(1080f,m.contentWidthDp,.01f);assertEquals(48f,m.gutterDp,.01f)
    }
    @Test fun `long presses ignore empty margins and the spine`() {
        assertNull(readerColumnAt(100f,1400f,1080f,16f,48f,true))
        assertNull(readerColumnAt(700f,1400f,1080f,16f,48f,true))
        assertEquals(0,readerColumnAt(400f,1400f,1080f,16f,48f,true))
        assertEquals(1,readerColumnAt(1000f,1400f,1080f,16f,48f,true))
    }
    @Test fun `short landscape single page retains more reading height than a stacked footer`() {
        val landscape=readerPageMetrics(780f,360f,18,1f,false,24f)
        val stacked=readerPageMetrics(780f,360f,18,1f,false,24f,compactFooter=false)
        assertFalse(landscape.twoPage)
        assertTrue(landscape.bottomPaddingDp<stacked.bottomPaddingDp)
        assertTrue(360f-landscape.topPaddingDp-landscape.bottomPaddingDp>200f)
        assertTrue(landscape.headingSpacingDp<readerPageMetrics(360f,780f,18,1f,false).headingSpacingDp)
    }
}
