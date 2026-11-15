package com.will.lightreader.data

import android.content.Context
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.source.net.RequestPriority
import com.will.lightreader.source.local.LocalIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal fun shelfCoverRequest(context: Context, url: String?): ImageRequest =
    ImageRequest.Builder(context).data(url?.let { if(it.startsWith("lrcomic:")) android.net.Uri.parse(it).buildUpon().clearQuery().appendQueryParameter("thumbnail","1").build().toString() else it }).size(512).scale(Scale.FIT)
        .precision(Precision.EXACT).crossfade(false).build()

/** Shared requests for shelf, detail, and warmup. Foreground rendering reuses the same decode. */
class ShelfCache(
    private val context: Context,
    private val library: LibraryRepository,
    private val fetchDetail: suspend (BookEntity) -> BookDetail?,
) {
    val imageLoader by lazy {
        Coil.imageLoader(context).newBuilder()
            .respectCacheHeaders(false)
            .decoderDispatcher(Dispatchers.IO.limitedParallelism(2))
            .build()
    }

    private val warmedCovers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun warm(keys: List<String>) = withContext(Dispatchers.IO + RequestPriority.Background) {
        val books = keys.take(32).mapNotNull { library.book(it) }
        coroutineScope {
            launch {
                for (url in books.mapNotNull { it.coverUrl?.takeIf(String::isNotBlank) }.distinct()) {
                    warmCover(url)
                }
            }
            val unavailable = mutableSetOf<String>()
            var requests = 0
            for (book in books) {
                if (requests == 12) break
                if (book.sourceId == LocalIds.SOURCE || book.sourceId in unavailable || library.cachedBookDetail(book.key) != null) continue
                requests++
                try {
                    val detail = withTimeoutOrNull(8_000) { fetchDetail(book) }
                    if (detail == null) unavailable.add(book.sourceId)
                    else library.cacheBookDetail(book.key, detail, onlyIfMissing = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    unavailable.add(book.sourceId)
                }
            }
        }
    }

    private suspend fun warmCover(url: String) {
        if (url in warmedCovers) return
        if (withTimeoutOrNull(8_000) { imageLoader.execute(shelfCoverRequest(context, url)) } is SuccessResult) {
            warmedCovers.add(url)
        }
    }
}
