package com.will.lightreader.ui.shelf

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class BookSolidGeometryTest {
    @Test fun `the left cover keeps its neighbor mask when it first leaves the bank`() {
        val neighbor = projectBookSolid(0f)
        assertTrue(neighbor.contains(96f - 36f, 180f))
        assertTrue(neighbor.contains(96f, 180f))
        assertEquals(1f, shelfPullTuck(0f), 0f)
        assertEquals(1f, shelfPullTuck(ShelfBankLayerThreshold), 0f)
        assertTrue(shelfPullTuck(.3f) in .4f.. .6f)
        assertEquals(0f, shelfPullTuck(1f), 0f)
    }
    @Test fun `return starts at the handoff pose and inserts during the turn`() {
        assertEquals(projectBookSolid(1f, facing = 1f), projectReturningBook(0f))
        assertEquals(projectBookSolid(0f), projectReturningBook(1f))
        val midway = projectReturningBook(.5f)
        val turnInPlace = projectBookSolid(1f, facing = .31f)
        assertTrue(midway.cover.top > turnInPlace.cover.top)
        assertNotEquals(midway.cover.a.y, midway.cover.b.y)
        assertEquals(midway.spine.b, midway.cover.a)
        assertEquals(midway.cap.c, midway.cover.b)
    }

    @Test fun `turning the entire book preserves all shared corners and finishes as an upright cover`() {
        for (step in 0..20) {
            val s = projectBookSolid(1f,facing=step/20f)
            assertEquals(s.spine.b,s.cover.a)
            assertEquals(s.spine.c,s.cover.d)
            assertEquals(s.cap.c,s.cover.b)
        }
        val face=projectBookSolid(1f,facing=1f).cover
        assertEquals(face.a.y,face.b.y,.0001f)
        assertEquals(face.a.x,face.d.x,.0001f)
        assertEquals(104f,face.b.x-face.a.x,.0001f)
        assertEquals(154f,face.d.y-face.a.y,.0001f)
    }
    @Test fun `every visible face stays joined while a book is extracted`() {
        for (step in 0..20) {
            val s = projectBookSolid(step / 20f)
            assertEquals(s.spine.b, s.cover.a)
            assertEquals(s.spine.c, s.cover.d)
            assertEquals(s.cap.a, s.spine.a)
            assertEquals(s.cap.d, s.spine.b)
            assertEquals(s.cap.c, s.cover.b)
        }
    }
    @Test fun `extraction is rigid translation and never changes book thickness or cover size`() {
        fun length(a: SolidPoint, b: SolidPoint) = hypot(a.x-b.x,a.y-b.y)
        val rest = projectBookSolid(0f)
        for (weight in listOf(.1f,.5f,1f)) {
            val moved = projectBookSolid(weight)
            assertEquals(length(rest.spine.a,rest.spine.b), length(moved.spine.a,moved.spine.b), .0001f)
            assertEquals(length(rest.cover.a,rest.cover.b), length(moved.cover.a,moved.cover.b), .0001f)
            assertEquals(length(rest.cover.a,rest.cover.d), length(moved.cover.a,moved.cover.d), .0001f)
        }
    }
    @Test fun `hit testing uses projected faces instead of their rectangular bounding boxes`() {
        val s = projectBookSolid(1f)
        val face = s.cover
        assertTrue(s.contains(face.points.map { it.x }.average().toFloat(),face.points.map { it.y }.average().toFloat()))
        assertFalse(face.contains(face.left+.1f,face.top+.1f))
        assertFalse(s.contains(-100f,-100f))
    }
    @Test fun `out of range and invalid input cannot create broken geometry`() {
        assertEquals(projectBookSolid(0f),projectBookSolid(-1f))
        assertEquals(projectBookSolid(1f),projectBookSolid(2f))
        assertEquals(projectBookSolid(0f),projectBookSolid(Float.NaN))
    }
}
