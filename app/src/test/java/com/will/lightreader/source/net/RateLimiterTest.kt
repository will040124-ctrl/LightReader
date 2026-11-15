package com.will.lightreader.source.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    @Test
    fun `两次请求间隔不足时睡眠补足`() = runBlocking {
        var clock = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = RateLimiter(minIntervalMs = 1000, clock = { clock }, sleep = { ms -> sleeps.add(ms); clock += ms })

        limiter.acquire()   // 首次放行，不睡
        limiter.acquire()   // 距上次 0ms，需睡 1000ms

        assertEquals(listOf(1000L), sleeps)
    }

    @Test
    fun `间隔已足则不睡眠`() = runBlocking {
        var clock = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = RateLimiter(minIntervalMs = 1000, clock = { clock }, sleep = { ms -> sleeps.add(ms); clock += ms })

        limiter.acquire()
        clock += 1500
        limiter.acquire()

        assertEquals(emptyList<Long>(), sleeps)
    }

    @Test
    fun `后台请求等用户静默后才放行`() = runBlocking {
        var clock = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = RateLimiter(
            minIntervalMs = 400,
            backgroundIntervalMs = 800,
            quietAfterInteractiveMs = 1200,
            clock = { clock },
            sleep = { ms -> sleeps.add(ms); clock += ms },
        )

        limiter.acquire()                    // 用户刚点了页面
        limiter.acquire(background = true)   // 后台：等满静默 1200ms
        limiter.acquire(background = true)   // 再下一章：后台间隔 800ms

        assertEquals(listOf(1200L, 800L), sleeps)
    }

    @Test
    fun `用户请求不排在后台上`() = runBlocking {
        // 后台的静默窗口拉得很长：只要交互请求能先走，就说明优先级生效
        val limiter = RateLimiter(
            minIntervalMs = 50,
            backgroundIntervalMs = 50,
            quietAfterInteractiveMs = 5_000,
        )
        limiter.acquire()

        val backgroundDone = CompletableDeferred<Unit>()
        val background = launch {
            limiter.acquire(background = true)
            backgroundDone.complete(Unit)
        }
        delay(60)   // 让后台进入等待

        val started = System.currentTimeMillis()
        limiter.acquire()
        val elapsed = System.currentTimeMillis() - started

        assertTrue("交互请求耗时 ${elapsed}ms，不该被后台拖住", elapsed < 1_000)
        assertFalse("用户请求在等时，后台不能抢跑", backgroundDone.isCompleted)
        background.cancelAndJoin()
    }
}
