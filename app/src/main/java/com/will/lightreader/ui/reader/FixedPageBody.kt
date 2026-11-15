package com.will.lightreader.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.will.lightreader.domain.ChapterContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.will.lightreader.ui.theme.Motion

/** Original-layout reader shares chapter navigation, page bookmarks and saved position. */
@Composable
fun FixedPageBody(content: ChapterContent, state: ReaderState, vm: ReaderViewModel,
    pageTurns: kotlinx.coroutines.flow.SharedFlow<Int>, background: Color, ink: Color,
    onPage: (Int, Int) -> Unit, onAnchor: () -> Unit, onTap: () -> Unit,
    onTurn: ((Int) -> Unit)? = null, pageIndex: Int? = null, pageCount: Int? = null,
    imageHeaders: Map<String, String> = emptyMap(), online: Boolean = false, initialZoom:Float=1f,
    imageTransform:String?=null, fitRequest:Pair<Int,String>?=null, inputEnabled: Boolean = true) {
    val page = requireNotNull(content.fixedPage)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zoom by remember(content.chapterUrl) { mutableFloatStateOf(initialZoom) }
    var pan by remember(content.chapterUrl) { mutableStateOf(Offset.Zero) }
    var imageSize by remember(page) { mutableStateOf(Size.Zero) }
    var stageSize by remember { mutableStateOf(Size.Zero) }
    val reduced = Motion.reducedMotion()
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    fun moveZoom(target: Float, targetPan: Offset = Offset.Zero) {
        zoomJob?.cancel()
        val fromZoom = zoom
        val fromPan = pan
        zoomJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(if (reduced) 0 else 180, easing = Motion.Decelerate)) { fraction, _ ->
                zoom = fromZoom + (target - fromZoom) * fraction
                pan = fromPan + (targetPan - fromPan) * fraction
            }
        }
    }
    val comic = page.pdfIndex == null
    val index = pageIndex ?: state.chapters.indexOfFirst { it.chapterUrl == content.chapterUrl }.coerceAtLeast(0)
    fun fitPage() { moveZoom(1f) }
    fun fitWidth() {
        if (imageSize.width > 0 && stageSize.width > 0 && stageSize.height > 0) {
            val fitScale = minOf(stageSize.width / imageSize.width, stageSize.height / imageSize.height)
            moveZoom((stageSize.width / (imageSize.width * fitScale)).coerceIn(1f, 5f))
        }
    }
    var appliedFit by remember(content.chapterUrl) { mutableStateOf<Int?>(null) }
    LaunchedEffect(fitRequest, imageSize, stageSize) {
        if (fitRequest == null || fitRequest.first == appliedFit || imageSize.width <= 0 || stageSize.width <= 0) return@LaunchedEffect
        appliedFit = fitRequest.first
        when (fitRequest.second) {
            "page" -> fitPage()
            "width" -> fitWidth()
        }
    }
    fun navigate(delta: Int) {
        if (onTurn != null) { onTurn(delta); return }
        if (state.chapterUrl == content.chapterUrl && !state.loading)
            state.chapters.getOrNull(index + delta)?.let { vm.navigateToChapter(it.chapterUrl) }
    }
    val latestNavigate by rememberUpdatedState<(Int) -> Unit> { navigate(it) }
    val latestTap by rememberUpdatedState(onTap)
    LaunchedEffect(content.chapterUrl, state.chapters.size) { onPage(index, pageCount ?: state.chapters.size.coerceAtLeast(1)); onAnchor() }
    LaunchedEffect(content.chapterUrl, state.chapterUrl, inputEnabled) {
        if (inputEnabled && content.chapterUrl == state.chapterUrl) pageTurns.collect { latestNavigate(it) }
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(bottom = readerFooterPadding()).clipToBounds()) {
            val stageW = constraints.maxWidth.toFloat()
            val stageH = constraints.maxHeight.toFloat()
            SideEffect { stageSize = Size(stageW, stageH) }
            val latestRtl by rememberUpdatedState(state.comicRtl && comic)
            var lastTap by remember(content.chapterUrl) { mutableLongStateOf(0L) }
            var lastTapPosition by remember(content.chapterUrl) { mutableStateOf(Offset.Zero) }
            var tapJob by remember { mutableStateOf<Job?>(null) }
            DisposableEffect(content.chapterUrl) { onDispose { tapJob?.cancel() } }
            val gestures = Modifier.fillMaxSize().pointerInput(content.chapterUrl, stageW, stageH, inputEnabled) {
                if (!inputEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    tapJob?.cancel()
                    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    val startedZoomed = zoom > 1.01f
                    var moved = false
                    var multiple = false
                    var cancelled = false
                    var distance = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) { cancelled = true; break }
                        event.changes.firstOrNull { it.id == down.id }?.let { velocity.addPosition(it.uptimeMillis, it.position) }
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            zoomJob?.cancel()
                            multiple = true; moved = true; tapJob?.cancel()
                            val old = zoom
                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                            val centroid = event.calculateCentroid(useCurrent = true) - Offset(stageW / 2, stageH / 2)
                            val factor = zoom / old
                            pan = (pan - centroid) * factor + centroid + event.calculatePan()
                        } else if (pressed == 1) {
                            val delta = event.calculatePan()
                            distance += delta
                            if (distance.getDistance() > viewConfiguration.touchSlop) {
                                zoomJob?.cancel()
                                moved = true
                                tapJob?.cancel()
                                lastTap = 0
                            }
                            if (zoom > 1.01f) pan += delta
                        }
                        val (limitX, limitY) = pagePanLimits(stageW, stageH, imageSize.width, imageSize.height, zoom)
                        pan = Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
                        if (moved) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (cancelled) {
                        lastTap = 0
                    } else if (!multiple && !startedZoomed && zoom <= 1.01f &&
                        abs(distance.x) > abs(distance.y) * 1.2f &&
                        commitPageTurn(distance.x, velocity.calculateVelocity().x, stageW, density)) {
                        latestNavigate((if (distance.x < 0) 1 else -1) * if (latestRtl) -1 else 1)
                    } else if (!moved) {
                        val now = down.uptimeMillis
                        if (lastTap > 0 && now - lastTap < viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - lastTapPosition).getDistance() <= 48.dp.toPx()) {
                            tapJob?.cancel()
                            val targetZoom = if (zoom > 1.01f) 1f else 2.5f
                            val focus = down.position - Offset(stageW / 2, stageH / 2)
                            val (limitX, limitY) = pagePanLimits(stageW, stageH, imageSize.width, imageSize.height, targetZoom)
                            moveZoom(targetZoom, Offset((-focus.x * (targetZoom - 1f)).coerceIn(-limitX, limitX),
                                (-focus.y * (targetZoom - 1f)).coerceIn(-limitY, limitY)))
                            lastTap = 0
                        } else {
                            lastTap = now
                            lastTapPosition = down.position
                            val tapDirection = readerTapDirection(down.position.x / stageW, latestRtl, zoom > 1.01f)
                            tapJob = scope.launch {
                                delay(viewConfiguration.doubleTapTimeoutMillis)
                                if (tapDirection == 0) latestTap() else latestNavigate(tapDirection)
                                lastTap = 0
                            }
                        }
                    }
                }
            }
            val imageModifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
            }
            Box(gestures, contentAlignment = Alignment.Center) {
                if (page.pdfIndex != null) {
                    // Bucket resolution: pinch frames only transform a retained bitmap, not render PDF.
                    val resolution = ((stageW * if (zoom > 2f) 2 else 1).toInt() / 256 + 1) * 256
                    var bitmap by remember(page) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    var error by remember(page) { mutableStateOf<String?>(null) }
                    var retry by remember { mutableIntStateOf(0) }
                    LaunchedEffect(page, resolution, retry) {
                        try {
                            bitmap = PdfPageImages.load(page, resolution); error = null
                            bitmap?.let { imageSize = Size(it.width.toFloat(), it.height.toFloat()) }
                            if (page.pdfIndex + 1 < state.chapters.size) launch {
                                try { PdfPageImages.load(page.copy(pdfIndex = page.pdfIndex + 1), resolution) }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { /* Current page remains readable if prefetch fails. */ }
                            }
                        }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "这一页暂时无法显示" }
                    }
                    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "第 ${index + 1} 页", modifier = imageModifier, contentScale = ContentScale.Fit) }
                    if (error != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = ink); TextButton(onClick = { retry++ }) { Text("重试") }
                    } else if (bitmap == null) CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    var imageRetry by remember(page) { mutableIntStateOf(0) }
                    key(imageRetry) {
                    val decodeBounds = remember(stageW, stageH) {
                        com.will.lightreader.media.comicPageDecodeBounds(stageW.toInt(), stageH.toInt())
                    }
                    val imageRequest = remember(page.ref, state.sourceId, imageHeaders, online, imageTransform, decodeBounds) {
                        coil.request.ImageRequest.Builder(context).data(if(online && state.sourceId.startsWith("comic-js-"))
                            com.will.lightreader.source.script.ComicScriptRoutes.image(state.sourceId,page.ref,imageTransform) else page.ref)
                        .size(decodeBounds.width, decodeBounds.height).precision(coil.size.Precision.EXACT).crossfade(false)
                        .apply {
                            imageHeaders.forEach { (name, value) -> addHeader(name, value) }
                            if (online) {
                                val identity = com.will.lightreader.source.script.comicImageIdentity(state.sourceId,page.ref,imageHeaders,imageTransform)
                                memoryCacheKey(identity); diskCacheKey(identity)
                            }
                        }.build()
                    }
                    coil.compose.SubcomposeAsyncImage(model = imageRequest, contentDescription = "第 ${index + 1} 页",
                        onSuccess = { imageSize = Size(it.result.drawable.intrinsicWidth.toFloat(), it.result.drawable.intrinsicHeight.toFloat()) },
                        modifier = imageModifier, contentScale = ContentScale.Fit,
                        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } },
                        error = { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (online) "这一页加载失败" else if(state.sourceId.startsWith("comic-js-"))"离线图片无法读取，请重新下载本话" else "图片无法读取，请重新导入", color = ink)
                            if (online) TextButton(onClick = { imageRetry++ }) { Text("重试本页") }
                        } })
                    }
                }
                if (zoom > 1.01f) Text("${(zoom * 100).toInt()}%", color = ink,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(background.copy(alpha = .9f))
                        .padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}
