package com.will.lightreader.ui.theme

import org.junit.Assert.*
import org.junit.Test

class AppearanceModeTest {
    @Test fun `system mode follows changes in system appearance`() {
        assertFalse(AppearanceMode.SYSTEM.isDark(false))
        assertTrue(AppearanceMode.SYSTEM.isDark(true))
    }
    @Test fun `explicit mode overrides either system appearance`() {
        for (system in listOf(false, true)) {
            assertTrue(AppearanceMode.DARK.isDark(system))
            assertFalse(AppearanceMode.LIGHT.isDark(system))
        }
    }
    @Test fun `missing or unknown persisted mode safely follows system`() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.stored(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.stored("old-value"))
        assertEquals(AppearanceMode.DARK, AppearanceMode.stored("DARK"))
    }
}
