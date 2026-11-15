package com.will.lightreader.source.wenku8

import com.will.lightreader.source.SourceError
import org.junit.Assert.*
import org.junit.Test

class DiscoveryPagesTest {
    @Test(expected = SourceError.ParseChanged::class)
    fun `tag cloud delivered as ranking is rejected instead of empty success`() {
        Wenku8Parser.parseSearchResults("<html><body><ul class='ultops'><li><a href='tags.php?t=x'>校园</a></li></ul></body></html>")
    }
    @Test(expected = SourceError.AuthRequired::class)
    fun `tag login wall offers authentication instead of an empty cloud`() {
        Wenku8Parser.parseTagGroups("<html><body>用户登录<form><input name='username'><input name='password'></form></body></html>")
    }
    @Test fun `legitimate no-match message is empty success`() {
        assertTrue(Wenku8Parser.parseSearchResults("<html><body>没有找到相关小说</body></html>").isEmpty())
    }
    @Test fun `single search match detail becomes a result for that book`() {
        val result=Wenku8Parser.parseSearchResults("<html><body><div id='content'><span><b>测试书</b></span><a href='/novel/3/3057/index.htm'>目录</a></div></body></html>")
        assertEquals("3057",result.single().bookId);assertEquals("测试书",result.single().title)
    }
}
