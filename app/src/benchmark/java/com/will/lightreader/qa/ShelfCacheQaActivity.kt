package com.will.lightreader.qa

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.decode.DataSource
import coil.request.CachePolicy
import coil.request.SuccessResult
import com.will.lightreader.data.shelfCoverRequest
import com.will.lightreader.di.AppContainer
import com.will.lightreader.domain.*
import com.will.lightreader.source.ComicSource
import com.will.lightreader.ui.book.BookOpening
import com.will.lightreader.ui.book.BookViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/** Exercise warmup and cold reopen across separate benchmark app processes. */
class ShelfCacheQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runId = requireNotNull(intent.getStringExtra("runId"))
        val cold = intent.getBooleanExtra("cold", false)
        val title = "Shelf cache fixture"
        val bookUrl = "https://shelf-cache.test/book/$runId"
        val cover = "http://127.0.0.1:18778/$runId.png"
        val detailCalls = AtomicInteger()
        val catalogueCalls = AtomicInteger()
        val source = object : ComicSource {
            override val id = "shelf-cache-qa"
            override val displayName = "Shelf Cache QA"
            override suspend fun search(query: String): List<BookBrief> = emptyList()
            override suspend fun detail(bookUrl: String): BookDetail {
                detailCalls.incrementAndGet()
                check(!cold)
                return BookDetail(id, runId, bookUrl, title, coverUrl = cover, intro = "持久缓存简介验证")
            }
            override suspend fun episodes(bookUrl: String): List<ChapterRef> {
                catalogueCalls.incrementAndGet()
                check(!cold)
                return listOf(ChapterRef("$bookUrl/1", "第一章"))
            }
            override suspend fun pages(episodeUrl: String): List<ComicPage> = emptyList()
        }
        val container = AppContainer(applicationContext, listOf(source))
        val repo = container.library
        val brief = BookBrief(source.id, runId, bookUrl, title, coverUrl = cover)
        val key = "${source.id}:$runId"
        lifecycleScope.launch {
            try {
                withTimeout(20_000) {
                    if (!cold) {
                        repo.saveBook(brief)
                        container.shelfCache.warm(listOf(key))
                        check(repo.cachedBookDetail(key)?.catalogueAt == 0L)
                    }
                    BookOpening.prime(brief)
                    val model = ViewModelProvider(this@ShelfCacheQaActivity, BookViewModel.factory(
                        container.sources, repo, container.prefetchImage, container.appScope, source.id, bookUrl,
                    ))[BookViewModel::class.java]
                    val state = model.state.first { !it.loading && it.chapters.isNotEmpty() }
                    check(state.detail?.intro == "持久缓存简介验证")
                    while (repo.cachedBookDetail(key)?.catalogueIsFresh() != true) delay(25)
                    check(detailCalls.get() == if (cold) 0 else 1)
                    check(catalogueCalls.get() == if (cold) 0 else 1)
                    if (cold) {
                        val request = shelfCoverRequest(applicationContext, cover).newBuilder()
                            .memoryCachePolicy(CachePolicy.DISABLED).networkCachePolicy(CachePolicy.DISABLED).build()
                        val image = container.shelfCache.imageLoader.execute(request)
                        check(image is SuccessResult && image.dataSource == DataSource.DISK)
                        repo.removeBook(key)
                    }
                }
                Log.i("ShelfCacheQA", "$runId PASS cold=$cold")
            } catch (e: Exception) {
                Log.e("ShelfCacheQA", "$runId FAIL cold=$cold", e)
            } finally {
                finish()
            }
        }
    }
}
