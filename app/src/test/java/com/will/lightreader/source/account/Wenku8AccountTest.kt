package com.will.lightreader.source.account

import com.will.lightreader.data.planAccountImport
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.net.FetchedPage
import com.will.lightreader.source.net.PageFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class Wenku8AccountTest {
    private fun table(id:String="1",next:String="",read:String="/novel/0/$id/21.htm") = """
        <table class="grid"><tr><th>书名</th><th>最新章节</th><th>已读章节</th><th>操作</th></tr>
        <tr><td><a href="/book/$id.htm">测试书$id</a></td><td><a href="/novel/0/$id/99.htm">最新章节</a></td>
        <td><a href="$read">网站已读章节</a></td><td>删除</td></tr></table>$next
    """
    private class Pages(val fetch:suspend(String)->String):PageFetcher {
        val requests=mutableListOf<String>()
        override suspend fun getText(url:String,charset:Charset):FetchedPage {requests+=url;return FetchedPage(fetch(url),false)}
    }
    @Test fun `only recognized account table is imported and latest chapter is never used as reading`() {
        val page=Wenku8AccountParser.parse("<a href='/book/90.htm'>推荐书</a>"+table())
        assertEquals(listOf("1"),page.entries.map{it.book.bookId})
        assertTrue(page.entries.single().readChapterUrl!!.endsWith("/21.htm"))
    }
    @Test fun `login form in site header does not invalidate a real account table`() {
        assertEquals(1,Wenku8AccountParser.parse("<form>用户登录<input name='password'></form>"+table()).entries.size)
    }
    @Test(expected=SourceError.AuthRequired::class) fun `login wall is never an empty account library`() {
        Wenku8AccountParser.parse("<div id='content'>请先登录</div><form><input name='password'></form>")
    }
    @Test(expected=SourceError.ParseChanged::class) fun `unknown page cannot be mistaken for an empty library`() {
        Wenku8AccountParser.parse("<body>网站正在维护</body>")
    }
    @Test fun `explicit empty recognized library is supported`() {
        val html="<table><tr><th>书名</th><th>操作</th></tr><tr><td>书架为空</td></tr></table>"
        assertTrue(Wenku8AccountParser.parse(html).entries.isEmpty())
    }
    @Test fun `live zero-count bookcase with its site headers is recognized`() {
        val html="<a href='/logout.php'>退出登录</a><div>您的书架可收藏300本，已收藏0本，本组有0本。</div>"+
            "<table><tr><th>名称</th><th>作者</th><th>最新章节</th><th>书签</th><th>更新</th><th>操作</th></tr><tr><td colspan='6'>选中项目</td></tr></table>"
        assertTrue(Wenku8AccountParser.parse(html).entries.isEmpty())
    }
    @Test(expected=SourceError.AuthRequired::class) fun `anonymous zero counter must not masquerade as an empty account`() {
        Wenku8AccountParser.parse("<div>已收藏0本</div><table><tr><th>名称</th><th>最新章节</th><th>书签</th><th>操作</th></tr></table>")
    }
    @Test fun `pagination cannot follow external destructive or malformed links`() {
        val links="""<a href='?page=2'>下一页</a><a href='?act=delete&amp;id=1'>删除</a>
            <a href='https://attacker.test/modules/article/bookcase.php?page=3'>外链</a><a href='?classid=%'>坏链接</a>"""
        assertEquals(listOf(Wenku8AccountParser.BOOKCASE+"?page=2"),Wenku8AccountParser.parse(table(next=links)).moreUrls)
    }
    @Test fun `read chapter must belong to the same book`() {
        assertNull(Wenku8AccountParser.parse(table(read="/novel/0/2/21.htm")).entries.single().readChapterUrl)
    }
    @Test fun `catalogue links in bookcase identify the same canonical book`() {
        val html=table().replace("/book/1.htm","/novel/0/1/index.htm")
        assertEquals("https://www.wenku8.net/book/1.htm",Wenku8AccountParser.parse(html).entries.single().book.bookUrl)
    }
    @Test fun `all pages are read and repeated books merged`() = runTest {
        val pages=Pages { url -> if(url.contains("page=2")) table("2")+table("1") else table(next="<a href='?page=2'>下一页</a>") }
        val result=Wenku8AccountSource(pages).readLibrary()
        assertEquals(2,result.pages);assertEquals(listOf("1","2"),result.entries.map{it.book.bookId})
    }
    @Test fun `an error on a later page does not return a partial snapshot`() = runTest {
        val pages=Pages { url -> if(url.contains("page=2")) throw SourceError.AuthRequired(url) else table(next="<a href='?page=2'>下一页</a>") }
        try { Wenku8AccountSource(pages).readLibrary();fail("Partial result returned") } catch(_:SourceError.AuthRequired) {}
    }
    @Test fun `repeated pagination content fails closed`() = runTest {
        val pages=Pages { table(next="<a href='?page=2'>下一页</a>") }
        try { Wenku8AccountSource(pages).readLibrary();fail("Repeated page returned") } catch(_:SourceError.ParseChanged) {}
    }
    @Test fun `merge preserves every existing position and does not duplicate books`() {
        val entry=Wenku8AccountParser.parse(table()).entries.single();val snapshot=AccountLibrarySnapshot("wenku8",listOf(entry,entry),1)
        val plan=planAccountImport(snapshot,setOf("1"),setOf("wenku8:1"),setOf("wenku8:1"),true)
        assertTrue(plan.newBooks.isEmpty());assertTrue(plan.positions.isEmpty());assertEquals(1,plan.existing)
    }
    @Test fun `positions are filled only when explicitly enabled and absent`() {
        val snapshot=AccountLibrarySnapshot("wenku8",Wenku8AccountParser.parse(table()).entries,1)
        assertEquals(1,planAccountImport(snapshot,setOf("1"),emptySet(),emptySet(),true).positions.size)
        assertTrue(planAccountImport(snapshot,setOf("1"),emptySet(),emptySet(),false).positions.isEmpty())
    }
    @Test(expected=IllegalArgumentException::class) fun `foreign entry cannot enter file or source operations`() {
        val entry=Wenku8AccountParser.parse(table()).entries.single()
        planAccountImport(AccountLibrarySnapshot("wenku8",listOf(entry.copy(book=entry.book.copy(bookUrl="https://other.test/book/1.htm"))),1),setOf("1"),emptySet(),emptySet(),false)
    }
}
