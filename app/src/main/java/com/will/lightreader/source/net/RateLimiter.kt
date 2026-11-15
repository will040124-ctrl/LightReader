package com.will.lightreader.source.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * 同域限速：交互请求与后台请求共用一条节奏，但后台必须给用户让路。
 *
 * - 交互（开书 / 翻章 / 搜索）：相邻两次至少隔 [minIntervalMs]；
 * - 后台（离线下载）：相邻两次至少隔 [backgroundIntervalMs]，且要等用户最后一次交互静默
 *   [quietAfterInteractiveMs]、并且没有交互请求在排队时才放行。
 *
 * clock/sleep 可注入，测试里用假时钟跑得又快又确定。
 */
class RateLimiter(
    private val minIntervalMs: Long = 400,
    private val backgroundIntervalMs: Long = 800,
    private val quietAfterInteractiveMs: Long = 1200,
    private val minGapMs: Long = 120,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var lastAnyAt: Long? = null
    private var lastInteractiveAt: Long? = null
    private var lastBackgroundAt: Long? = null
    private val pendingInteractive = AtomicInteger(0)

    suspend fun acquire(background: Boolean = false) {
        if (!background) pendingInteractive.incrementAndGet()
        try {
            while (true) {
                val wait = mutex.withLock {
                    val now = clock()
                    val earliest = if (background) {
                        // 用户还在等页面：后台先退出去，别占着这一轮
                        if (pendingInteractive.get() > 0) return@withLock YIELD
                        earliestOf(
                            lastBackgroundAt?.plus(backgroundIntervalMs),
                            lastInteractiveAt?.plus(quietAfterInteractiveMs),
                            lastAnyAt?.plus(minGapMs),
                        )
                    } else {
                        earliestOf(
                            lastInteractiveAt?.plus(minIntervalMs),
                            lastAnyAt?.plus(minGapMs),
                        )
                    }
                    if (now >= earliest) {
                        lastAnyAt = now
                        if (background) lastBackgroundAt = now else lastInteractiveAt = now
                        return@withLock 0L
                    }
                    earliest - now
                }
                if (wait == 0L) return
                sleep(if (wait == YIELD) YIELD_RETRY_MS else wait)
            }
        } finally {
            if (!background) pendingInteractive.decrementAndGet()
        }
    }

    private fun earliestOf(vararg at: Long?): Long =
        at.filterNotNull().maxOrNull() ?: Long.MIN_VALUE

    private companion object {
        const val MIN_GAP_MS = 120L      // 任意两次请求之间的最小缝
        const val YIELD = -1L            // 后台让位信号，负值与"还要等几毫秒"区分开
        const val YIELD_RETRY_MS = 150L
    }
}
