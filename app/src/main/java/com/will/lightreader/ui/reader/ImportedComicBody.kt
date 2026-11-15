@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.will.lightreader.ui.reader

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.domain.FixedPage
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.min

/**
 * Imported CBZ keeps one image per chapter in the original page model. 连续下滑 stitches those
 * chapters back into one strip so local manga gets the same 翻页/连续 choice as online comics.
 */
@Composable
fun ImportedComicBody(
    content: ChapterContent,
    state: ReaderState,
    vm: ReaderViewModel,
    pageTurns: SharedFlow<Int>,
    background: Color,
    ink: Color,
    onPage: (Int, Int) -> Unit,
    onAnchor: (Int, Float) -> Unit,
    onTap: () -> Unit,
) {
    val chapters = state.chapters
    val scroll = state.comicMode == "scroll"
    val pageIndex = chapters.indexOfFirst { it.chapterUrl == content.chapterUrl }.coerceAtLeast(0)
    Column(Modifier.fillMaxSize()) {
        if (scroll) {
            ImportedComicScrollBody(
                state = state, vm = vm, pageTurns = pageTurns,
                background = background, ink = ink, onTap = onTap,
                onPage = onPage,
                onAnchor = onAnchor,
                committed = content.chapterUrl == state.content?.chapterUrl,
            )
        } else {
            FixedPageBody(
                content = content, state = state, vm = vm, pageTurns = pageTurns,
                background = background, ink = ink,
                onPage = onPage,
                onAnchor = { onAnchor(pageIndex, 0f) },
                onTap = onTap,
                inputEnabled = content.chapterUrl == state.chapterUrl && !state.loading,
            )
        }
    }
}

@Composable
private fun ImportedComicScrollBody(
    state: ReaderState,
    vm: ReaderViewModel,
    pageTurns: SharedFlow<Int>,
    background: Color,
    ink: Color,
    onTap: () -> Unit,
    onPage: (Int, Int) -> Unit,
    onAnchor: (Int, Float) -> Unit,
    committed: Boolean,
) {
    val chapters = state.chapters
    if (chapters.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
        return
    }
    val initial = remember {
        chapters.indexOfFirst { it.chapterUrl == state.chapterUrl }
            .let { if (it >= 0) it else state.startBlockIndex }
            .coerceIn(chapters.indices)
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val density = LocalDensity.current
    var zoom by remember { mutableStateOf<Pair<String, String>?>(null) }

    var lastIndex by rememberSaveable(state.bookKey) { mutableIntStateOf(initial) }
    var lastFraction by rememberSaveable(state.bookKey) { mutableFloatStateOf(state.comicScrollFraction) }
    var restoring by remember { mutableStateOf(true) }
    val ready = remember { mutableStateMapOf<String, Int>() }
    fun position(): Pair<Int, Float> {
        val index = list.firstVisibleItemIndex.coerceIn(chapters.indices)
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        return index to if (list.firstVisibleItemIndex >= chapters.size) 1f
            else comicScrollFraction(list.firstVisibleItemScrollOffset, item?.size ?: 0)
    }
    fun persist() {
        if (!committed || restoring) return
        val (index, fraction) = position()
        lastIndex = index
        lastFraction = fraction
        onAnchor(index, fraction)
        vm.saveImportedComicPosition(chapters[index].chapterUrl, chapters[index].title, fraction)
    }
    val latestPersist by rememberUpdatedState(::persist)
    DisposableEffect(chapters) { onDispose { latestPersist() } }
    LaunchedEffect(chapters, committed, restoring) {
        if (committed && !restoring) snapshotFlow { position() }.collect { (index, fraction) ->
            lastIndex = index
            lastFraction = fraction
            vm.rememberImportedComicPosition(chapters[index].chapterUrl, chapters[index].title, fraction)
        }
    }
    LaunchedEffect(chapters, committed, restoring) {
        if (committed && !restoring) snapshotFlow { list.firstVisibleItemIndex }
            .distinctUntilChanged().collect { index ->
                if (index in chapters.indices) onPage(index, chapters.size)
            }
    }
    LaunchedEffect(chapters, committed, restoring) {
        if (committed && !restoring) snapshotFlow { list.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling) { delay(150); latestPersist() }
        }
    }
    LaunchedEffect(committed) {
        if (committed) pageTurns.collect { direction ->
            list.animateScrollBy(direction * list.layoutInfo.viewportSize.height * .8f)
        }
    }
    LaunchedEffect(state.jumpTick, committed) {
        if (committed) state.jumpBlock?.let {
            lastIndex = it.coerceIn(chapters.indices)
            lastFraction = 0f
            list.scrollToItem(lastIndex)
            vm.consumeJump()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val width = min(maxWidth.value, 720f).dp
        val widthPx = with(density) { width.roundToPx() }
        LaunchedEffect(chapters, widthPx) {
            val target = lastIndex.coerceIn(chapters.indices)
            val fraction = lastFraction
            restoring = true
            try {
                list.scrollToItem(target)
                val item = withTimeoutOrNull(15000) {
                    snapshotFlow {
                        val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                        Triple(visible?.size ?: 0, visible != null && ready[chapters[target].chapterUrl] == visible.size,
                            list.isScrollInProgress || lastIndex != target)
                    }.first { it.third || it.first > 0 && it.second && list.layoutInfo.viewportSize.width == widthPx }
                }
                if (item != null && !item.third) list.scrollToItem(target, comicScrollOffset(fraction, item.first))
            } finally { restoring = false }
        }
        LazyColumn(
            state = list,
            userScrollEnabled = committed && zoom == null,
            modifier = Modifier.width(width).fillMaxHeight().align(Alignment.TopCenter),
            contentPadding = PaddingValues(bottom = readerFooterPadding()+16.dp),
        ) {
            itemsIndexed(chapters, key = { _, chapter -> chapter.chapterUrl }) { index, chapter ->
                ImportedPageImage(
                    chapterUrl = chapter.chapterUrl, index = index, widthPx = widthPx,
                    ink = ink, vm = vm, onTap = onTap,
                    onReady = { ready[chapter.chapterUrl] = it },
                    onZoom = { ref -> zoom = chapter.chapterUrl to ref },
                )
            }
            item(key = "book-end") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("已读至本书末尾", color = ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    zoom?.let { (chapterUrl, ref) ->
        Dialog(
            onDismissRequest = { zoom = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            com.will.lightreader.ui.ReaderSystemBars(state.immersive, state.hideStatusBar)
            com.will.lightreader.ui.SystemBarsAppearance(background.luminance() < .45f)
            Column(Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)) {
                TextButton(onClick = { zoom = null }, modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(contentColor = ink)) {
                    Text("关闭图片放大")
                }
                val noTurns = remember { kotlinx.coroutines.flow.MutableSharedFlow<Int>() }
                val page = ChapterContent(chapterUrl, "", emptyList(), FixedPage(ref))
                FixedPageBody(
                    content = page, state = state, vm = vm, pageTurns = noTurns,
                    background = background, ink = ink,
                    onPage = { _, _ -> }, onAnchor = {}, onTap = {}, onTurn = {},
                    pageIndex = chapters.indexOfFirst { it.chapterUrl == chapterUrl }.coerceAtLeast(0),
                    pageCount = chapters.size, initialZoom = 2f,
                )
            }
        }
    }
}

@Composable
private fun ImportedPageImage(
    chapterUrl: String,
    index: Int,
    widthPx: Int,
    ink: Color,
    vm: ReaderViewModel,
    onTap: () -> Unit,
    onZoom: (String) -> Unit,
    onReady: (Int) -> Unit,
) {
    var ref by remember(chapterUrl) { mutableStateOf<String?>(null) }
    var failed by remember(chapterUrl) { mutableStateOf(false) }
    var retry by remember(chapterUrl) { mutableIntStateOf(0) }
    LaunchedEffect(chapterUrl, retry) {
        val loaded = vm.importedComicPageRef(chapterUrl)
        ref = loaded
        failed = loaded == null
    }
    var aspect by remember(chapterUrl) { mutableFloatStateOf(2f / 3f) }
    var measured by remember(chapterUrl) { mutableIntStateOf(0) }
    var resolved by remember(chapterUrl) { mutableStateOf(false) }
    LaunchedEffect(resolved, failed, measured, aspect, widthPx) {
        if ((resolved || failed) && measured > 0 && kotlin.math.abs(measured - widthPx / aspect) <= 2f) onReady(measured)
    }
    val current = ref
    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect).onSizeChanged { measured = it.height }
            .pointerInput(chapterUrl, current) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { current?.let(onZoom) },
                )
            },
    ) {
        if (current == null) {
            if (failed) Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("第 ${index + 1} 页无法读取，请重新导入", color = ink)
                TextButton(onClick = { retry++ }) { Text("重试") }
            } else CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            key(retry) {
                coil.compose.SubcomposeAsyncImage(
                    model = current,
                    contentDescription = "第 ${index + 1} 页",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onSuccess = { result ->
                        val drawable = result.result.drawable
                        aspect = comicStripAspect(drawable.intrinsicWidth, drawable.intrinsicHeight, widthPx)
                        resolved = true
                    },
                    onError = { resolved = true },
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } },
                    error = {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("第 ${index + 1} 页暂时无法显示", color = ink)
                            TextButton(onClick = { retry++ }) { Text("重试本页") }
                        }
                    },
                )
            }
        }
    }
}
