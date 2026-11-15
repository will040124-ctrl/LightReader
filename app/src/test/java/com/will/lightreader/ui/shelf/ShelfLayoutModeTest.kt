package com.will.lightreader.ui.shelf

import com.will.lightreader.ui.reader.ShelfLayoutMode
import org.junit.Assert.*
import org.junit.Test

class ShelfLayoutModeTest {
    @Test fun `automatic layout chooses compact list on phone and covers on tablet`() {
        assertFalse(ShelfLayoutMode.AUTO.usesGrid(390))
        assertFalse(ShelfLayoutMode.AUTO.usesGrid(599))
        assertTrue(ShelfLayoutMode.AUTO.usesGrid(600))
        assertTrue(ShelfLayoutMode.AUTO.usesGrid(1400))
    }
    @Test fun `explicit layout survives width changes`() {
        for (width in listOf(320, 600, 1400)) {
            assertTrue(ShelfLayoutMode.GRID.usesGrid(width))
            assertFalse(ShelfLayoutMode.LIST.usesGrid(width))
        }
    }
}
