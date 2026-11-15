package com.will.lightreader.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnGestureTest {
    @Test fun `edge taps respect reading direction while center and zoom stay controls`() {
        assertEquals(-1, readerTapDirection(.1f))
        assertEquals(1, readerTapDirection(.9f))
        assertEquals(0, readerTapDirection(.5f))
        assertEquals(1, readerTapDirection(.1f, rtl = true))
        assertEquals(-1, readerTapDirection(.9f, rtl = true))
        assertEquals(0, readerTapDirection(.9f, zoomed = true))
        assertEquals(0, readerTapDirection(Float.NaN))
    }
    @Test fun `fast reversal returns even a mostly extracted page`() {
        assertFalse(commitPageTurn(-250f, 1_000f, 360f, 1f))
        assertFalse(commitPageTurn(250f, -1_000f, 360f, 1f))
    }

    @Test fun `short intentional fling commits and tiny movement stays put`() {
        assertTrue(commitPageTurn(-30f, -1_000f, 360f, 1f))
        assertFalse(commitPageTurn(-5f, -1_000f, 360f, 1f))
    }

    @Test fun `slow release depends on distance at the current screen density`() {
        assertTrue(commitPageTurn(80f, 0f, 360f, 1f))
        assertFalse(commitPageTurn(50f, 0f, 360f, 1f))
        assertTrue(commitPageTurn(160f, 0f, 720f, 2f))
        assertFalse(commitPageTurn(Float.NaN, 0f, 360f, 1f))
    }
}
