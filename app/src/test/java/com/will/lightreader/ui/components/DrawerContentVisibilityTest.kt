package com.will.lightreader.ui.components

import org.junit.Assert.*
import org.junit.Test

class DrawerContentVisibilityTest {
    @Test fun `unmeasured and fully closed drawer does not populate`() {
        assertFalse(drawerContentVisible(false, false, 0, Float.NaN))
        assertFalse(drawerContentVisible(false, false, 720, Float.NaN))
        assertFalse(drawerContentVisible(false, false, 720, -720f))
    }
    @Test fun `opening by button populates before offset moves`() {
        assertTrue(drawerContentVisible(false, true, 0, Float.NaN))
        assertTrue(drawerContentVisible(false, true, 720, -720f))
    }
    @Test fun `edge drag shows contents before the target becomes open`() {
        assertTrue(drawerContentVisible(false, false, 720, -718f))
        assertTrue(drawerContentVisible(false, false, 600, -350f))
    }
    @Test fun `closing keeps contents until the sheet is actually closed`() {
        assertTrue(drawerContentVisible(true, false, 720, -720f))
        assertTrue(drawerContentVisible(false, false, 720, -719f))
        assertFalse(drawerContentVisible(false, false, 720, -720f))
    }
}
