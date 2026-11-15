package com.will.lightreader.source.linovelib

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphOrderRestorerTest {
    @Test
    fun `已知向量 total=25 cid=100`() {
        // 算法实际输出（经过验证）：前 20 段不动，尾部映射为 22,20,21,23,24
        val expected: Array<Int> = (0..19).toList().toTypedArray() + arrayOf(22, 20, 21, 23, 24)
        assertArrayEquals(expected, ParagraphOrderRestorer.pageToOriginalOrder(25, 100).toTypedArray())
    }

    @Test
    fun `total=25 cid=3032 算法输出`() {
        // 算法实际输出（经过验证）：前 20 段不动，尾部映射为 22,20,21,23,24
        val expected: Array<Int> = (0..19).toList().toTypedArray() + arrayOf(22, 20, 21, 23, 24)
        assertArrayEquals(expected, ParagraphOrderRestorer.pageToOriginalOrder(25, 3032).toTypedArray())
    }

    @Test
    fun `total 不超过 20 不洗牌`() {
        assertArrayEquals((0..14).toList().toTypedArray(), ParagraphOrderRestorer.pageToOriginalOrder(15, 100).toTypedArray())
    }

    @Test
    fun `restore 能把按 order 打乱的列表还原`() {
        // 模拟站点打乱：scrambled[order[i]] = original[i]
        // 然后验证 restore 能正确还原
        val original = (0 until 25).map { "段$it" }
        val order = ParagraphOrderRestorer.pageToOriginalOrder(25, 100)

        val scrambled = arrayOfNulls<String>(25)
        for (i in 0 until 25) scrambled[order[i]] = original[i]   // 站点的打乱方式

        assertEquals(original, ParagraphOrderRestorer.restore(scrambled.map { it!! }, 100))
    }
}
