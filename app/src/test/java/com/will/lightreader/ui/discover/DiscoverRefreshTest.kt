@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.will.lightreader.ui.discover

import com.will.lightreader.domain.*
import com.will.lightreader.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class DiscoverRefreshTest {
    @Test fun `source switch uses source-specific kinds and hides unsupported tabs`() = runTest(dispatcher) {
        val alternate = Source("alternate", SourceDiscovery(listOf("editor" to "精选"), false, false, "精选"))
        val vm=DiscoverViewModel(SourceRegistry(listOf(Source(),alternate)))
        runCurrent();vm.switchSource(alternate.id);runCurrent()
        assertEquals("editor",vm.state.value.kind);assertEquals(listOf(DiscoverTab.RANKING),vm.availableTabs())
        vm.selectTab(DiscoverTab.TAGS);assertEquals(DiscoverTab.RANKING,vm.state.value.tab)
        assertEquals("精选",vm.tabLabel(DiscoverTab.RANKING))
    }
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Source(
        override val id: String = "wenku8",
        override val discovery: SourceDiscovery = SourceDiscovery(),
    ) : NovelSource {
        override val displayName = "test"
        var calls = 0
        var tagsAction: suspend () -> List<TagGroup> = { listOf(TagGroup("test", listOf("校园"))) }
        override suspend fun tags() = tagsAction()
        var action: suspend () -> List<BookBrief> = { listOf(book("initial")) }
        override suspend fun popular(kind: String): List<BookBrief> { calls++; return action() }
        override suspend fun search(query: String) = popular(query)
        var pagedSearch:(suspend (String,Int)->BrowsePage)?=null
        override suspend fun searchPage(query:String,page:Int)=pagedSearch?.invoke(query,page) ?: super.searchPage(query,page)
        override suspend fun searchByAuthor(query: String) = popular(query)
        override suspend fun authorWorks(authorName: String, authorUrl: String?) = emptyList<BookBrief>()
        override suspend fun bookDetail(bookUrl: String) = BookDetail(id, bookUrl, bookUrl, bookUrl)
        override suspend fun chapterList(bookUrl: String) = emptyList<ChapterRef>()
        override suspend fun chapterContent(chapterUrl: String) = ChapterContent(chapterUrl, "", emptyList())
        override suspend fun completedBooks(page: Int) = BrowsePage(listOf(book("completed")), false)
        companion object { fun book(id: String) = BookBrief("wenku8", id, id, id) }
    }
    @Test fun `refresh failure releases indicator and preserves displayed results`() = runTest(dispatcher) {
        val source=Source();val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        source.action={ throw SourceError.Network(java.io.IOException("offline")) }
        vm.refresh();runCurrent()
        assertFalse(vm.state.value.refreshing)
        assertEquals("initial",vm.state.value.items.single().bookId)
        assertNotNull(vm.state.value.refreshMessage)
    }
    @Test fun `paged search appends results and retains the list when loading more fails`() = runTest(dispatcher) {
        val source=Source();source.pagedSearch={_,page->when(page) {
            1->BrowsePage(listOf(Source.book("first")),true)
            2->BrowsePage(listOf(Source.book("first"),Source.book("second")),true)
            else->throw java.io.IOException("offline")
        } }
        val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        vm.search("comic");runCurrent();assertTrue(vm.state.value.hasMore)
        vm.loadMore();runCurrent()
        assertEquals(listOf("first","second"),vm.state.value.items.map { it.bookId })
        assertEquals(2,vm.state.value.page)
        vm.loadMore();runCurrent()
        assertEquals(2,vm.state.value.items.size);assertEquals(2,vm.state.value.page)
        assertFalse(vm.state.value.loadingMore);assertNotNull(vm.state.value.refreshMessage)
    }
    @Test fun `changing a query cancels stale pagination results`() = runTest(dispatcher) {
        val source=Source();val pending=CompletableDeferred<BrowsePage>()
        source.pagedSearch={query,page->if(page==1)BrowsePage(listOf(Source.book(query)),true) else pending.await()}
        val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        vm.search("old");runCurrent();vm.loadMore();runCurrent()
        vm.search("new");runCurrent();pending.complete(BrowsePage(listOf(Source.book("stale")),false));runCurrent()
        assertEquals("new",vm.state.value.items.single().bookId)
        assertEquals(1,vm.state.value.page);assertFalse(vm.state.value.loadingMore)
    }
    @Test fun `cancelled tag load can restart after a tab change`() = runTest(dispatcher) {
        val source=Source();val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        source.tagsAction={awaitCancellation()};vm.selectTab(DiscoverTab.TAGS);runCurrent()
        assertTrue(vm.state.value.tagsLoading)
        vm.selectTab(DiscoverTab.COMPLETED);runCurrent();assertFalse(vm.state.value.tagsLoading)
        source.tagsAction={listOf(TagGroup("new",listOf("恋爱")))}
        vm.selectTab(DiscoverTab.TAGS);runCurrent()
        assertEquals("恋爱",vm.state.value.tags.single().tags.single());assertFalse(vm.state.value.tagsLoading)
    }
    @Test fun `tag refresh owns indicator and retains tags on failure`() = runTest(dispatcher) {
        val source=Source();val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        vm.selectTab(DiscoverTab.TAGS);runCurrent()
        source.tagsAction={delay(100);throw java.io.IOException("offline")}
        vm.refresh();runCurrent();assertTrue(vm.state.value.refreshing)
        advanceTimeBy(101);runCurrent()
        assertFalse(vm.state.value.refreshing);assertEquals("校园",vm.state.value.tags.single().tags.single())
        assertNotNull(vm.state.value.refreshMessage)
    }
    @Test fun `changing active search scope repeats the search`() = runTest(dispatcher) {
        val source=Source();val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        vm.search("query");runCurrent();val calls=source.calls
        vm.setScope(SearchScope.AUTHOR);runCurrent()
        assertEquals(SearchScope.AUTHOR,vm.state.value.scope)
        assertTrue(source.calls>calls)
    }
    @Test fun `leaving a refreshing page cancels its ownership`() = runTest(dispatcher) {
        val source=Source();val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        val pending=CompletableDeferred<List<BookBrief>>()
        source.action={pending.await()};vm.refresh();runCurrent()
        assertTrue(vm.state.value.refreshing)
        vm.selectTab(DiscoverTab.COMPLETED);runCurrent()
        pending.complete(listOf(Source.book("stale")));runCurrent()
        assertFalse(vm.state.value.refreshing)
        assertEquals("completed",vm.state.value.items.single().bookId)
    }
    @Test fun `duplicate pulls share one in-flight request`() = runTest(dispatcher) {
        val source=Source();val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        val pending=CompletableDeferred<List<BookBrief>>()
        source.action={pending.await()};vm.refresh();runCurrent();vm.refresh();runCurrent()
        assertEquals(2,source.calls)
        pending.complete(listOf(Source.book("new")));runCurrent()
        assertFalse(vm.state.value.refreshing)
        assertEquals("new",vm.state.value.items.single().bookId)
    }
    @Test fun `timeout does not leave an indicator or erase results`() = runTest(dispatcher) {
        val source=Source();val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        source.action={ awaitCancellation() };vm.refresh();runCurrent()
        advanceTimeBy(35_001);runCurrent()
        assertFalse(vm.state.value.refreshing)
        assertEquals("initial",vm.state.value.items.single().bookId)
    }
    @Test fun `switching home destinations drops a pull indicator immediately`() = runTest(dispatcher) {
        val source=Source();val vm=DiscoverViewModel(SourceRegistry(listOf(source)));runCurrent()
        source.action={awaitCancellation()};vm.refresh();runCurrent()
        assertTrue(vm.state.value.refreshing)
        vm.leavePage()
        assertFalse(vm.state.value.refreshing)
        runCurrent()
        assertEquals("initial",vm.state.value.items.single().bookId)
    }
}
