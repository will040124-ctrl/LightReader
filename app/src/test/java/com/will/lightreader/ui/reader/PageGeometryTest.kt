package com.will.lightreader.ui.reader

import org.junit.Assert.*
import org.junit.Test

class PageGeometryTest {
    @Test fun `fit mode cannot pan into empty margins`() {
        assertEquals(0f to 0f, pagePanLimits(1000f,600f,400f,600f,1f))
        assertEquals(0f to 0f, pagePanLimits(400f,800f,900f,500f,1f))
    }
    @Test fun `portrait zoom is constrained by actual page extents`() {
        assertEquals(0f to 300f, pagePanLimits(1000f,600f,400f,600f,2f))
        assertEquals(100f to 600f, pagePanLimits(1000f,600f,400f,600f,3f))
    }
}
