package com.will.lightreader.data

import com.will.lightreader.domain.Block
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.ChapterRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 离线下载器：并发抓正文存缓存 + 预取插图（Coil 磁盘缓存 = 离线可读）。
 * 依赖全部以 lambda 注入，纯逻辑可单测；单章失败不断流，计入 failed。
 * 瞬时失败（限流/超时）自动重试 [maxAttempts] 次，仍失败的记入 failedUrls 供手动补救。
 *
 * 并发 [concurrency] 个 worker 共享一条走位索引；真正的请求节奏由取页层的限速器控制，
 * 这里只是把网络往返和写盘叠起来。插图预取不占章节主线（结构化并发在收尾时统一等齐）。
 */
class BookDownloader(
    private val isDownloaded: suspend (chapterUrl: String) -> Boolean,
    private val fetch: suspend (chapterUrl: String) -> ChapterContent,
    private val save: suspend (chapterUrl: String, content: ChapterContent) -> Unit,
    private val prefetchImage: suspend (url: String) -> Unit = {},
    private val maxAttempts: Int = 3,
    private val retryDelayMs: Long = 800,
    private val concurrency: Int = 3,
) {
    data class Progress(
        val done: Int,
        val total: Int,
        val failed: Int = 0,
        val currentTitle: String = "",
        val finished: Boolean = false,
        val failedUrls: List<String> = emptyList(),   // 重试用尽仍失败的章节，可单独补救
        val cancelled:Boolean=false,
        val lastError:String?=null,
        val pageDone:Int=0,val pageTotal:Int=0,
    )

    /** 下载一批章节（已下载的跳过）。返回最终进度。 */
    suspend fun download(chapters: List<ChapterRef>, onProgress: (Progress) -> Unit = {}): Progress {
        val total = chapters.size
        val done = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val failedUrls = Collections.synchronizedList(mutableListOf<String>())
        val cursor = AtomicInteger(0)
        val currentTitle = AtomicReference("")
        val lastError = AtomicReference<String?>(null)
        val imageSlots = Semaphore(2)

        fun report(title: String = currentTitle.get()) {
            onProgress(Progress(done.get(), total, failed.get(), title, failedUrls = failedUrls.toList(),lastError=lastError.get()))
        }

        report()
        // coroutineScope 会等所有 worker 和插图预取子任务跑完；结果必须在它返回之后再算
        coroutineScope {
            val batch = this   // 一章取消 = 整批中断，兄弟 worker 一起停
            val workers = concurrency.coerceIn(1, total.coerceAtLeast(1))
            repeat(workers) {
                launch {
                    while (true) {
                        val i = cursor.getAndIncrement()
                        if (i >= total) break
                        val ch = chapters[i]
                        currentTitle.set(ch.title)
                        report(ch.title)
                        try {
                            if (!isDownloaded(ch.chapterUrl)) {
                                var attempt = 0
                                while (true) {
                                    attempt++
                                    try {
                                        val content = fetch(ch.chapterUrl)
                                        save(ch.chapterUrl, content)
                                        // 插图预取丢给子任务，不挡下一章的抓取
                                        content.blocks.filterIsInstance<Block.Image>()
                                            .map { it.ref }
                                            .filter { it.isNotBlank() }
                                            .forEach { ref ->
                                                launch {
                                                    imageSlots.withPermit { runCatching { prefetchImage(ref) } }
                                                }
                                            }
                                        break
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e   // 取消不是失败，绝不吞
                                        if (e is com.will.lightreader.source.script.ComicImageFailure || e is IllegalArgumentException || attempt >= maxAttempts) throw e   // 重试用尽，交给外层计失败
                                        delay(retryDelayMs)                    // 瞬时打嗝：稍候再来
                                    }
                                }
                            }
                            done.incrementAndGet()
                            report(ch.title)
                        } catch (e: CancellationException) {
                            batch.cancel(e)   // 取消不是失败：整批下载必须中断而非"跳过"
                            throw e
                        } catch (e: Exception) {
                            lastError.set((e.message ?: "章节下载失败").take(180))
                            failed.incrementAndGet()
                            failedUrls.add(ch.chapterUrl)
                            report(ch.title)
                        }
                    }
                }
            }
        }
        return Progress(done.get(), total, failed.get(), finished = true, failedUrls = failedUrls.toList(),lastError=lastError.get())
            .also(onProgress)
    }
}
