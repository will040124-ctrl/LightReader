@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.will.lightreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.will.lightreader.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.min

@OptIn(FlowPreview::class)
@Composable
fun ComicScrollBody(content:ChapterContent,state:ReaderState,vm:ReaderViewModel,pageTurns:SharedFlow<Int>,
    background:Color,ink:Color,onPage:(Int,Int)->Unit,onAnchor:(Int,Float)->Unit,onTap:()->Unit) {
    val pages=content.comicPages
    val active=state.chapterUrl==content.chapterUrl && !state.loading
    val list=rememberLazyListState(initialFirstVisibleItemIndex=state.startBlockIndex.coerceIn(pages.indices))
    val ready=remember(content.chapterUrl) { mutableStateMapOf<String,Int>() }
    var restoring by remember(content.chapterUrl) { mutableStateOf(true) }
    // rememberSaveable：旋转/宽度变化后用最后提交的位置重新锚定，而不是过时的初始读档
    var lastIndex by androidx.compose.runtime.saveable.rememberSaveable(content.chapterUrl) { mutableIntStateOf(state.startBlockIndex.coerceIn(pages.indices)) }
    var lastFraction by androidx.compose.runtime.saveable.rememberSaveable(content.chapterUrl) { mutableFloatStateOf(state.comicScrollFraction) }
    var zoomPage by remember { mutableStateOf<Int?>(null) }
    var pull by remember { mutableFloatStateOf(0f) }
    val density=LocalDensity.current;val threshold=with(density) { 96.dp.toPx() }
    fun position():Pair<Int,Float> {
        val index=list.firstVisibleItemIndex.coerceIn(pages.indices)
        val item=list.layoutInfo.visibleItemsInfo.firstOrNull { it.index==index }
        val fraction=if(list.firstVisibleItemIndex>=pages.size)1f else comicScrollFraction(list.firstVisibleItemScrollOffset,item?.size ?: 0)
        return index to fraction
    }
    fun persist() { if(active&&!restoring) { val p=position();lastIndex=p.first;lastFraction=p.second;vm.rememberComicScroll(content.chapterUrl,p.first,p.second,true) } }
    val latestPersist by rememberUpdatedState(::persist)
    DisposableEffect(content.chapterUrl) { onDispose { latestPersist() } }
    fun nextChapter() { persist();vm.nextChapter(fromBeginning=true) }
    val latestNext by rememberUpdatedState(::nextChapter)
    val latestPrev by rememberUpdatedState<()->Unit> { persist();vm.prevChapter() }
    val latestActive by rememberUpdatedState(active)
    val nested=remember(list,threshold) { object:NestedScrollConnection {
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
            if(source==NestedScrollSource.UserInput && latestActive && zoomPage==null) {
                pull=when {
                    !list.canScrollForward && available.y<0 -> (pull-available.y).coerceAtMost(threshold*1.5f)
                    !list.canScrollBackward && available.y>0 -> (pull-available.y).coerceAtLeast(-threshold*1.5f)
                    consumed.y!=0f ->0f
                    else->pull
                }
            }
            return Offset.Zero
        }
    } }
    LaunchedEffect(active,restoring) {
        if(active&&!restoring)snapshotFlow { position().first }.distinctUntilChanged().collect { index ->
            onPage(index,pages.size);onAnchor(index,if(pages.size<=1)1f else index.toFloat()/pages.lastIndex)
        }
    }
    LaunchedEffect(active,restoring) {
        if(active&&!restoring)snapshotFlow { position() }.sample(650).collect { (index,fraction) ->
            lastIndex=index;lastFraction=fraction;vm.rememberComicScroll(content.chapterUrl,index,fraction,true)
        }
    }
    LaunchedEffect(active,restoring) {
        if(active&&!restoring)snapshotFlow { list.isScrollInProgress }.collectLatest { scrolling ->
            if(!scrolling) { delay(100);val p=position();lastIndex=p.first;lastFraction=p.second;persist() }
        }
    }
    LaunchedEffect(state.jumpTick, active) {
        if(active)state.jumpBlock?.let {
            list.scrollToItem(it.coerceIn(pages.indices));lastIndex=it.coerceIn(pages.indices);lastFraction=0f
            vm.consumeJump()
        }
    }
    LaunchedEffect(active,zoomPage) {
        if(active&&zoomPage==null)pageTurns.collect { direction ->
            if(direction>0 && !list.canScrollForward && !state.atLast)nextChapter()
            else if(direction<0 && !list.canScrollBackward && !state.atFirst)latestPrev()
            else list.animateScrollBy(direction*list.layoutInfo.viewportSize.height*.8f)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val width=min(maxWidth.value,720f).dp
        val widthPx=with(density) { width.roundToPx() }
        LaunchedEffect(content.chapterUrl,widthPx) {
            val target=lastIndex;val fraction=lastFraction
            restoring=true
            try {
                list.scrollToItem(target)
                val item=withTimeoutOrNull(15000) {
                    snapshotFlow {
                        val visible=list.layoutInfo.visibleItemsInfo.firstOrNull { it.index==target }
                        Triple(visible?.size ?: 0,visible!=null&&ready[pages[target].id]==visible.size,list.isScrollInProgress)
                    }.first { it.third || it.first>0&&it.second&&list.layoutInfo.viewportSize.width==widthPx }
                }
                if(item!=null&&!item.third)list.scrollToItem(target,comicScrollOffset(fraction,item.first))
            } finally { restoring=false }
        }
        LazyColumn(state=list,userScrollEnabled=active&&zoomPage==null,
            modifier=Modifier.width(width).fillMaxHeight().align(Alignment.TopCenter).nestedScroll(nested)
                .pointerInput(active,state.atFirst,state.atLast) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                        do { val event=awaitPointerEvent(PointerEventPass.Final) } while(event.changes.any { it.pressed })
                        if(active && zoomPage==null) {
                            if(pull>=threshold&&!state.atLast)latestNext()
                            else if(pull<=-threshold&&!state.atFirst)latestPrev()
                        }
                        pull=0f
                    }
                },contentPadding=PaddingValues(bottom=readerFooterPadding()+16.dp)) {
            itemsIndexed(pages,key={_,page->page.id}) { index,page ->
                ComicStripImage(page,index,widthPx,vm,ink,onReady={ready[page.id]=it},onTap=onTap,onZoom={zoomPage=index})
            }
            item(key="chapter-end") {
                Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(state.atLast)"已读至本书末尾" else if(pull>=threshold)"松手进入下一话" else "本话读完了，继续上拉进入下一话",color=ink)
                    if(!state.atLast)TextButton(enabled=active,onClick={nextChapter()}) { Text("继续下一话") }
                }
            }
        }
        if(pull<=-threshold&&!state.atFirst)Text("松手返回上一话",color=ink,modifier=Modifier.align(Alignment.TopCenter).padding(12.dp))
    }
    zoomPage?.let { index ->
        Dialog(onDismissRequest={zoomPage=null},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
            com.will.lightreader.ui.ReaderSystemBars(state.immersive, state.hideStatusBar)
            com.will.lightreader.ui.SystemBarsAppearance(background.luminance() < .45f)
            Column(Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)) {
                TextButton(onClick={zoomPage=null},modifier=Modifier.align(Alignment.End),
                    colors=ButtonDefaults.textButtonColors(contentColor=ink)) { Text("关闭图片放大") }
                val noTurns=remember { MutableSharedFlow<Int>() }
                FixedPageBody(content.copy(fixedPage=FixedPage(pages[index].localRef ?: pages[index].url)),state,vm,noTurns,
                    background,ink,onPage={_,_->},onAnchor={},onTap={},onTurn={},pageIndex=index,pageCount=pages.size,
                    imageHeaders=if(pages[index].localRef==null)vm.comicImageHeaders(pages[index]) else emptyMap(),online=pages[index].localRef==null,initialZoom=2f)
            }
        }
    }
}

@Composable
private fun ComicStripImage(page:ComicPage,index:Int,widthPx:Int,vm:ReaderViewModel,ink:Color,
    onReady:(Int)->Unit,onTap:()->Unit,onZoom:()->Unit) {
    val context=LocalContext.current
    val errorHeight=with(LocalDensity.current) { 240.dp.toPx() }
    var aspect by remember(page.id) { mutableFloatStateOf(2f/3f) }
    var retry by remember(page.id) { mutableIntStateOf(0) }
    var measured by remember(page.id) { mutableIntStateOf(0) }
    var resolved by remember(page.id) { mutableStateOf(false) }
    LaunchedEffect(resolved,measured,aspect,widthPx) {
        if(resolved && measured>0 && kotlin.math.abs(measured-widthPx/aspect)<=2f)onReady(measured)
    }
    val headers=if(page.localRef==null)vm.comicImageHeaders(page) else emptyMap()
    val request=remember(page,widthPx,headers,retry) {
        coil.request.ImageRequest.Builder(context)
            .data(page.localRef ?: if(vm.state.value.sourceId.startsWith("comic-js-"))
                com.will.lightreader.source.script.ComicScriptRoutes.image(vm.state.value.sourceId,page.url,page.imageTransform) else page.url)
            .size(coil.size.Size(coil.size.Dimension.Pixels(widthPx.coerceIn(1,1200)),coil.size.Dimension.Pixels(12000)))
            .apply { headers.forEach { (key,value)->addHeader(key,value) }
                if(page.localRef==null) { val id=com.will.lightreader.source.script.comicImageIdentity(vm.state.value.sourceId,page.url,headers,page.imageTransform);memoryCacheKey(id);diskCacheKey(id) }
            }.build()
    }
    Box(Modifier.fillMaxWidth().aspectRatio(aspect).onSizeChanged { measured=it.height }.pointerInput(page.id) { detectTapGestures(onTap={onTap()},onDoubleTap={onZoom()}) }) {
        key(retry) {
            coil.compose.SubcomposeAsyncImage(model=request,contentDescription="第 ${index+1} 页",modifier=Modifier.fillMaxSize(),
                contentScale=androidx.compose.ui.layout.ContentScale.Fit,
                onSuccess={result ->
                    val drawable=result.result.drawable
                    aspect=comicStripAspect(drawable.intrinsicWidth,drawable.intrinsicHeight,widthPx)
                    resolved=true
                },onError={aspect=widthPx/errorHeight;resolved=true},
                loading={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }},
                error={Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Text("第 ${index+1} 页暂时无法显示",color=ink)
                    TextButton(onClick={retry++}) { Text("重试本页") }
                }})
        }
    }
}
