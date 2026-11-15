@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.will.lightreader.ui.author

import com.will.lightreader.domain.*
import com.will.lightreader.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

class AuthorRefreshTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Source : NovelSource {
        override val id = "test"
        override val displayName = "test"
        var calls = 0
        var action: suspend () -> List<BookBrief> = { listOf(BookBrief(id,"original","original","original")) }
        override suspend fun authorWorks(authorName: String, authorUrl: String?): List<BookBrief> { calls++; return action() }
        override suspend fun search(query: String) = emptyList<BookBrief>()
        override suspend fun popular(kind: String) = emptyList<BookBrief>()
        override suspend fun bookDetail(bookUrl: String) = BookDetail(id,bookUrl,bookUrl,bookUrl)
        override suspend fun chapterList(bookUrl: String) = emptyList<ChapterRef>()
        override suspend fun chapterContent(chapterUrl: String) = ChapterContent(chapterUrl,"",emptyList())
    }
    private fun model(source: Source) = AuthorViewModel(SourceRegistry(listOf(source)),source.id,"author",null)
    @Test fun `initial timeout releases loading and permits retry`() = runTest(dispatcher) {
        val source=Source();source.action={awaitCancellation()};val vm=model(source);runCurrent()
        advanceTimeBy(8_001);runCurrent()
        assertFalse(vm.state.value.loading);assertTrue(vm.state.value.error is SourceError.Network)
        source.action={listOf(BookBrief(source.id,"retry","retry","retry"))}
        vm.load();runCurrent();assertEquals("retry",vm.state.value.items.single().bookId)
    }
    @Test fun `refresh timeout keeps old books and releases indicator`() = runTest(dispatcher) {
        val source=Source();val vm=model(source);runCurrent();source.action={awaitCancellation()}
        vm.refresh();runCurrent();advanceTimeBy(8_001);runCurrent()
        assertFalse(vm.state.value.refreshing);assertEquals("original",vm.state.value.items.single().bookId)
        assertNotNull(vm.state.value.error)
    }
    @Test fun `repeated refresh starts only one request`() = runTest(dispatcher) {
        val source=Source();val vm=model(source);runCurrent();val pending=CompletableDeferred<List<BookBrief>>()
        source.action={pending.await()};vm.refresh();vm.refresh();runCurrent();assertEquals(2,source.calls)
        pending.complete(emptyList());runCurrent();assertFalse(vm.state.value.refreshing)
    }
    @Test fun `leaving releases loading so returning can resume a cancelled request`() = runTest(dispatcher) {
        val source=Source();source.action={awaitCancellation()};val vm=model(source);runCurrent()
        vm.leavePage();runCurrent();assertFalse(vm.state.value.loading)
        source.action={listOf(BookBrief(source.id,"returned","returned","returned"))}
        vm.ensureLoaded();runCurrent();assertEquals("returned",vm.state.value.items.single().bookId)
    }
    @Test fun `a noncooperative late refresh cannot overwrite the page after leaving`() = runTest(dispatcher) {
        val source=Source();val vm=model(source);runCurrent();val pending=CompletableDeferred<List<BookBrief>>()
        source.action={withContext(NonCancellable){pending.await()}}
        vm.refresh();runCurrent();vm.leavePage()
        pending.complete(listOf(BookBrief(source.id,"late","late","late")));runCurrent()
        assertFalse(vm.state.value.refreshing);assertEquals("original",vm.state.value.items.single().bookId)
    }

}
