package com.will.lightreader.ui.shelf

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfFocusTest {
    @Test fun `selected book survives a change in reading sort order`() {
        assertEquals(2, shelfFocusIndex(listOf("new", "a", "selected"), "selected", 0))
        assertEquals(0, shelfFocusIndex(listOf("selected", "new", "a"), "selected", 2))
    }
    @Test fun `deleted selection uses the visible bank position and empty libraries are safe`() {
        assertEquals(1, shelfFocusIndex(listOf("a", "b"), "deleted", 9))
        assertEquals(0, shelfFocusIndex(listOf("a", "b"), null, -1))
        assertEquals(-1, shelfFocusIndex(emptyList(), "deleted", 0))
    }
}
