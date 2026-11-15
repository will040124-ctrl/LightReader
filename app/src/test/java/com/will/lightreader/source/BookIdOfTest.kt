package com.will.lightreader.source

import com.will.lightreader.source.linovelib.LinovelibRoutes
import com.will.lightreader.source.wenku8.Wenku8Routes
import org.junit.Assert.assertEquals
import org.junit.Test

class BookIdOfTest {
    @Test
    fun `linovelib bookIdOf 从 novel URL 提取书 ID`() {
        assertEquals("3032", LinovelibRoutes.bookIdOf("https://www.linovelib.com/novel/3032.html"))
    }

    @Test
    fun `wenku8 bookIdOf 从 book URL 提取书 ID`() {
        assertEquals("100", Wenku8Routes.bookIdOf("https://www.wenku8.net/book/100.htm"))
    }

    @Test
    fun `bookIdOf 与 saveBook 的 bookId 来源一致`() {
        // 回归锚点：BookViewModel.bookKey 必须用 bookIdOf，与 Repository.saveBook(brief.bookId) 同源
        val fromDetail = LinovelibRoutes.bookIdOf("https://www.linovelib.com/novel/3032.html")
        val fromVm = LinovelibRoutes.bookIdOf("https://www.linovelib.com/novel/3032.html")
        assertEquals(fromDetail, fromVm)
    }
}
