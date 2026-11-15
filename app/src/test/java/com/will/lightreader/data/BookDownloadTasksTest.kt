@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.will.lightreader.data

import com.will.lightreader.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class BookDownloadTasksTest {
    @Test fun `cancelling before the coroutine begins does not leave a stuck running state`() = runTest {
        val tasks=BookDownloadTasks();val downloader=BookDownloader(isDownloaded={false},fetch={awaitCancellation()},save={_,_->})
        tasks.start("book",this,listOf(ChapterRef("one","一")),downloader)
        tasks.cancelAndJoin("book")
        assertTrue(tasks.state("book").value!!.finished);assertTrue(tasks.state("book").value!!.cancelled)
    }
    @Test fun `duplicate starts share a task and cancellation releases it for resumption`() = runTest {
        val tasks=BookDownloadTasks();val chapters=listOf(ChapterRef("one","第一话"))
        val downloader=BookDownloader(isDownloaded={false},fetch={awaitCancellation()},save={_,_->})
        assertTrue(tasks.start("book",this,chapters,downloader));runCurrent()
        assertFalse(tasks.start("book",this,chapters,downloader))
        val same=tasks.state("book");assertSame(same,tasks.state("book"))
        tasks.cancelAndJoin("book");assertTrue(same.value!!.cancelled);assertTrue(same.value!!.finished)
        val resumed=BookDownloader(isDownloaded={true},fetch={error("must skip")},save={_,_->})
        assertTrue(tasks.start("book",this,chapters,resumed));runCurrent()
        assertFalse(same.value!!.cancelled);assertEquals(1,same.value!!.done)
    }
    @Test fun `failed targets survive detail recreation and remain retryable`() = runTest {
        val tasks=BookDownloadTasks();val chapters=listOf(ChapterRef("one","第一话"))
        val downloader=BookDownloader(isDownloaded={false},fetch={throw java.io.IOException("offline")},save={_,_->},maxAttempts=1)
        tasks.start("book",this,chapters,downloader);runCurrent()
        assertEquals(chapters,tasks.failed("book"));assertEquals("offline",tasks.state("book").value!!.lastError)
    }
}
