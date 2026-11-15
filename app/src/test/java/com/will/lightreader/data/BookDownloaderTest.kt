package com.will.lightreader.data

import com.will.lightreader.domain.Block
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BookDownloaderTest {

    private fun ch(i: Int) = ChapterRef(chapterUrl = "https://x/$i", title = "第${i}章", chapterId = "$i")
    private fun content(url: String) = ChapterContent(
        chapterUrl = url, title = "t",
        blocks = listOf(Block.Paragraph("正文"), Block.Image(ref = "https://img/1.jpg")),
    )

    @Test
    fun `逐章下载并预取插图`() = runBlocking {
        val saved = mutableListOf<String>()
        val prefetched = mutableListOf<String>()
        val d = BookDownloader(
            isDownloaded = { false },
            fetch = { content(it) },
            save = { url, _ -> saved.add(url) },
            prefetchImage = { prefetched.add(it) },
            concurrency = 1,   // 顺序断言用单 worker
        )
        val p = d.download(listOf(ch(1), ch(2), ch(3)))
        assertEquals(3, p.done)
        assertEquals(0, p.failed)
        assertTrue(p.finished)
        assertEquals(listOf("https://x/1", "https://x/2", "https://x/3"), saved)
        assertEquals(3, prefetched.size)   // 每章一张插图
    }

    @Test
    fun `已下载的章跳过不重复抓`() = runBlocking {
        val saved = mutableListOf<String>()
        val d = BookDownloader(
            isDownloaded = { it == "https://x/2" },
            fetch = { content(it) },
            save = { url, _ -> saved.add(url) },
            concurrency = 1,
        )
        val p = d.download(listOf(ch(1), ch(2), ch(3)))
        assertEquals(3, p.done)
        assertEquals(listOf("https://x/1", "https://x/3"), saved)
    }

    @Test
    fun `单章失败不断流`() = runBlocking {
        val saved = mutableListOf<String>()
        val d = BookDownloader(
            isDownloaded = { false },
            fetch = { if (it.endsWith("2")) throw RuntimeException("网络打嗝") else content(it) },
            save = { url, _ -> saved.add(url) },
            retryDelayMs = 0,
            concurrency = 1,
        )
        val p = d.download(listOf(ch(1), ch(2), ch(3)))
        assertEquals(2, p.done)
        assertEquals(1, p.failed)
        assertEquals(listOf("https://x/1", "https://x/3"), saved)
    }

    @Test
    fun `瞬时失败自动重试成功`() = runBlocking {
        val attempts = mutableMapOf<String, Int>()
        val d = BookDownloader(
            isDownloaded = { false },
            fetch = {
                val n = (attempts[it] ?: 0) + 1
                attempts[it] = n
                if (n < 3) throw RuntimeException("打嗝") else content(it)   // 第 3 次成功
            },
            save = { _, _ -> },
            retryDelayMs = 0,
        )
        val p = d.download(listOf(ch(1)))
        assertEquals(1, p.done)
        assertEquals(0, p.failed)
        assertEquals(3, attempts["https://x/1"])
    }

    @Test
    fun `重试用尽记录失败章节供补救`() = runBlocking {
        val d = BookDownloader(
            isDownloaded = { false },
            fetch = { if (it.endsWith("2")) throw RuntimeException("一直打嗝") else content(it) },
            save = { _, _ -> },
            maxAttempts = 2,
            retryDelayMs = 0,
            concurrency = 1,
        )
        val p = d.download(listOf(ch(1), ch(2), ch(3)))
        assertEquals(2, p.done)
        assertEquals(1, p.failed)
        assertEquals(listOf("https://x/2"), p.failedUrls)
    }

    @Test
    fun `取消异常不吞整批中断`() = runBlocking {
        val saved = mutableListOf<String>()
        val d = BookDownloader(
            isDownloaded = { false },
            fetch = { if (it.endsWith("2")) throw kotlinx.coroutines.CancellationException("整批取消") else content(it) },
            save = { url, _ -> saved.add(url) },
            retryDelayMs = 0,
            concurrency = 1,   // 单 worker 才能确定取消发生在第 2 章
        )
        // 取消必须向上传播（整册下载中断 ≠ 跳过剩余章节）
        var propagated = false
        try {
            d.download(listOf(ch(1), ch(2), ch(3)))
        } catch (e: kotlinx.coroutines.CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
        assertEquals(listOf("https://x/1"), saved)   // 第 2 章处中断，不继续下第 3 章
    }

    @Test
    fun `并发下载每章只抓一次且进度单调`() = runBlocking {
        val total = 12
        val fetches = ConcurrentHashMap<String, AtomicInteger>()
        val saved = Collections.synchronizedList(mutableListOf<String>())
        val prefetched = AtomicInteger(0)
        val progressDone = Collections.synchronizedList(mutableListOf<Int>())
        val d = BookDownloader(
            isDownloaded = { false },
            fetch = { url ->
                fetches.getOrPut(url) { AtomicInteger(0) }.incrementAndGet()
                delay(5)   // 让 worker 真正交错
                content(url)
            },
            save = { url, _ -> saved.add(url) },
            prefetchImage = { prefetched.incrementAndGet() },
            concurrency = 3,
        )
        val p = withTimeout(5_000) {
            d.download((1..total).map { ch(it) }) { progressDone.add(it.done) }
        }

        assertEquals(total, p.done)
        assertEquals(0, p.failed)
        assertTrue(p.finished)
        // 走位索引保证不重复、不遗漏
        assertEquals(total, saved.size)
        assertEquals((1..total).map { "https://x/$it" }.toSet(), saved.toSet())
        assertEquals((1..total).map { "https://x/$it" }.toSet(), fetches.keys.toSet())
        assertTrue("每章都应只抓一次：${fetches.mapValues { it.value.get() }}", fetches.values.all { it.get() == 1 })
        assertEquals(total, prefetched.get())   // 收尾时插图全部预取完
        assertTrue("进度不能倒退：$progressDone", progressDone.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(total, progressDone.last())
    }

    @Test
    fun `插图预取不挡章节主线`() = runBlocking {
        val total = 6
        val savedCount = AtomicInteger(0)
        val d = BookDownloader(
            isDownloaded = { false },
            fetch = { content(it) },
            save = { _, _ -> savedCount.incrementAndGet() },
            // 预取卡在所有章节落盘之前：若预取占着主线就会死锁
            prefetchImage = { while (savedCount.get() < total) delay(1) },
            concurrency = 3,
        )
        val p = withTimeout(5_000) { d.download((1..total).map { ch(it) }) }
        assertEquals(total, p.done)
    }
}
