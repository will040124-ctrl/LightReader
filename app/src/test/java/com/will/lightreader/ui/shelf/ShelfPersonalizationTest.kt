package com.will.lightreader.ui.shelf

import org.junit.Assert.*
import org.junit.Test

class ShelfPersonalizationTest {
    @Test fun `unknown artwork setting falls back to the established mascot`() {
        assertEquals(ShelfArtwork.MASCOT,ShelfArtwork.stored("bad"));assertEquals(ShelfArtwork.MASCOT,ShelfArtwork.stored(null))
    }
    @Test fun `cover mode never uses blank covers or replaces the other artwork modes`() {
        assertNull(shelfArtworkModel(ShelfArtwork.LAST_COVER,"  "))
        assertNull(shelfArtworkModel(ShelfArtwork.MASCOT,"cover"))
        assertEquals("cover",shelfArtworkModel(ShelfArtwork.LAST_COVER,"cover"))
    }
}
