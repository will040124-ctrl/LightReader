package com.will.lightreader.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.FixedPage
import kotlinx.coroutines.flow.SharedFlow

/** One chapter remains one episode; only the selected image is decoded. */
@Composable
fun ComicChapterBody(content: ChapterContent, state: ReaderState, vm: ReaderViewModel,
    pageTurns: SharedFlow<Int>, background: Color, ink: Color,
    onPage: (Int, Int) -> Unit, onAnchor: (Int, Float) -> Unit, onTap: () -> Unit) {
    val pages = content.comicPages
    // rememberSaveable：旋转后停在当前页，而不是过时的初始读档页
    var index by androidx.compose.runtime.saveable.rememberSaveable(content.chapterUrl) { mutableIntStateOf(state.startBlockIndex.coerceIn(pages.indices)) }
    val active = content.chapterUrl == state.chapterUrl && !state.loading
    LaunchedEffect(index, active) {
        if (active && state.comicMode!="scroll") {
            onPage(index, pages.size)
            onAnchor(index, if (pages.size <= 1) 1f else index.toFloat() / (pages.size - 1))
            vm.rememberComicPage(content.chapterUrl, index)
        }
    }
    LaunchedEffect(state.jumpTick, active) {
        if (active) state.jumpBlock?.let {
            index = it.coerceIn(pages.indices)
            vm.consumeJump()
        }
    }
    // 有界预取：只取下一页入 Coil 缓存；翻页/换章即取消，快速反向不留无用请求
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewport = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val prefetchBounds = remember(viewport) { com.will.lightreader.media.comicPageDecodeBounds(viewport.width, viewport.height) }
    LaunchedEffect(index, content.chapterUrl, state.comicMode, prefetchBounds, active) {
        if (!active) return@LaunchedEffect
        if (state.comicMode == "scroll") return@LaunchedEffect
        val next = pages.getOrNull(index + 1)?.takeIf { it.localRef == null } ?: return@LaunchedEffect
        val headers = vm.comicImageHeaders(next)
        val request = coil.request.ImageRequest.Builder(context)
            .data(if (state.sourceId.startsWith("comic-js-"))
                com.will.lightreader.source.script.ComicScriptRoutes.image(state.sourceId, next.url, next.imageTransform) else next.url)
            .size(prefetchBounds.width, prefetchBounds.height).precision(coil.size.Precision.EXACT).crossfade(false)
            .apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
                val identity = com.will.lightreader.source.script.comicImageIdentity(state.sourceId, next.url, headers, next.imageTransform)
                memoryCacheKey(identity); diskCacheKey(identity)
            }.build()
        try { coil.Coil.imageLoader(context).execute(request) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* 预取失败不影响当前页 */ }
    }
    fun turn(delta: Int) {
        if (!active) return
        when {
            index + delta in pages.indices -> index += delta
            delta > 0 -> vm.nextChapter()
            else -> vm.prevChapter(fromEnd = true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        if(state.comicMode=="scroll") {
            ComicScrollBody(content,state,vm,pageTurns,background,ink,onPage={i,total->index=i;onPage(i,total)},onAnchor,onTap)
        } else {
        val reduced = com.will.lightreader.ui.theme.Motion.reducedMotion()
        AnimatedContent(targetState = index, transitionSpec = {
            val direction = (if (targetState > initialState) 1 else -1) * if (state.comicRtl) -1 else 1
            if (reduced) fadeIn(tween(100)) togetherWith fadeOut(tween(100))
            else slideInHorizontally(tween(220, easing = com.will.lightreader.ui.theme.Motion.Decelerate)) { it * direction } togetherWith
                slideOutHorizontally(tween(220, easing = com.will.lightreader.ui.theme.Motion.Decelerate)) { -it * direction }
        }, label = "comic-page") { displayed ->
        key(pages[displayed].id) {
            FixedPageBody(content.copy(fixedPage=FixedPage(pages[displayed].localRef ?: pages[displayed].url)),state,vm,pageTurns,
                background,ink,onPage={_,_->},onAnchor={},onTap=onTap,
                onTurn=::turn,pageIndex=displayed,pageCount=pages.size,
                imageHeaders=if(pages[displayed].localRef==null)vm.comicImageHeaders(pages[displayed]) else emptyMap(),online=pages[displayed].localRef==null,
                imageTransform=pages[displayed].imageTransform, inputEnabled=active && displayed == index)
        }
        }
        }
    }
}
