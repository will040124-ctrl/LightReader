package com.will.lightreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PaginationTest {

    @Test
    fun `顺序装箱正常分页`() {
        // 页高 100：块高 40+40 装下，第三个 40 开新页
        val pages = paginate(listOf(40, 40, 40), 100)
        assertEquals(listOf(listOf(0, 1), listOf(2)), pages)
    }

    @Test
    fun `正好装满不分裂`() {
        val pages = paginate(listOf(50, 50), 100)
        assertEquals(listOf(listOf(0, 1)), pages)
    }

    @Test
    fun `超高块独占一页`() {
        val pages = paginate(listOf(30, 200, 30), 100)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), pages)
    }

    @Test
    fun `空内容一页`() {
        assertEquals(listOf(emptyList<Int>()), paginate(emptyList(), 100))
    }

    @Test
    fun `页号定位`() {
        val pages = listOf(listOf(0, 1), listOf(2, 3), listOf(4))
        assertEquals(0, pageOf(pages, 1))
        assertEquals(1, pageOf(pages, 2))
        assertEquals(2, pageOf(pages, 4))
        assertEquals(0, pageOf(pages, 99))   // 越界回落
    }
}
