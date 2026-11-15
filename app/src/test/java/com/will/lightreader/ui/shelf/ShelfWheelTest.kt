package com.will.lightreader.ui.shelf

import org.junit.Assert.*
import org.junit.Test

class ShelfWheelTest {
    @Test fun `center remains full size and flat`() {
        assertEquals(WheelPose(1f, 0f, 1f), wheelPose(550f,100,1000))
    }
    @Test fun `edges mirror their tilt without reducing readability beyond the bound`() {
        val top=wheelPose(100f,100,1000);val bottom=wheelPose(1000f,100,1000)
        assertEquals(top.scale,bottom.scale,0f);assertEquals(-top.tilt,bottom.tilt,0f)
        assertTrue(top.scale>=.94f);assertTrue(top.alpha>=.86f)
        assertEquals(top,wheelPose(-10000f,100,1000))
    }
    @Test fun `unmeasured viewport has a neutral pose`() {
        assertEquals(WheelPose(1f,0f,1f),wheelPose(30f,0,0))
        assertEquals(WheelPose(1f,0f,1f),wheelPose(Float.NaN,0,100))
    }
}
