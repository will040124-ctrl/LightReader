package com.will.lightreader.ui.shelf

import android.graphics.Matrix as NativeMatrix
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.will.lightreader.R
import com.will.lightreader.LightReaderApp
import com.will.lightreader.ui.components.bookCoverTransition
import com.will.lightreader.ui.components.BookCoverMotion
import com.will.lightreader.ui.components.rememberBookReturnBarrier
import com.will.lightreader.ui.components.watercolorWash
import com.will.lightreader.ui.theme.LocalAppDark
import com.will.lightreader.ui.theme.Motion
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class SolidHit(val index: Int, val origin: Offset, val solid: BookSolid, val rank: Float, val occlusion: ShelfOcclusion?)
private data class ShelfOcclusion(val book: BookSolid, val dx: Float, val top: Float)
private data class ShelfSwitch(val from: String, val to: String)

internal data class SolidShelfTextures(val atlas: ImageBitmap, val wash: ImageBitmap) {
    companion object {
        fun load(app: LightReaderApp): Deferred<SolidShelfTextures> =
            app.container.appScope.async(start = CoroutineStart.LAZY) {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                SolidShelfTextures(
                    BitmapFactory.decodeResource(app.resources, R.drawable.watercolor_book_materials, options).asImageBitmap(),
                    BitmapFactory.decodeResource(app.resources, R.drawable.watercolor_pigment_wash, options).asImageBitmap(),
                )
            }
    }
}

/** A compact bank with native inertia and centre snapping. Occlusion stays in book order. */
@Composable
internal fun SolidBookshelf(books: List<ShelfBook>, state: LazyListState, wash: ImageBitmap?, focusedKey: String?,
    onFocus: (String) -> Unit, onOpen: (ShelfBook) -> Unit) {
    if (books.isEmpty()) return
    val dark = LocalAppDark.current
    val reduced = Motion.reducedMotion()
    val awaitCoverReturn = rememberBookReturnBarrier()
    val captionInset = if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else 24.dp
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val hits = remember { mutableMapOf<String, SolidHit>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var pulledKey by remember { mutableStateOf<String?>(null) }
    var openingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOpenKey by remember { mutableStateOf<String?>(null) }
    var returningKey by remember { mutableStateOf<String?>(null) }
    var handoff by remember { mutableStateOf<ShelfSwitch?>(null) }
    LaunchedEffect(books, openingKey, returningKey) {
        if (openingKey != null && books.none { it.key == openingKey }) {
            pendingOpenKey = null
            openingKey = null
        }
        if (returningKey != null && books.none { it.key == returningKey }) returningKey = null
    }
    LaunchedEffect(Unit) {
        // A returning route first receives its upright cover, then settles it into the bank.
        if (openingKey != null) {
            if (!reduced) awaitCoverReturn()
            returningKey = openingKey
            openingKey = null
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                pulledKey = null
                handoff = null
            }
        }
    }
    LaunchedEffect(books, handoff?.from) {
        if (handoff != null && books.none { it.key == handoff?.from }) handoff = null
    }
    val keys = remember(books) { books.map { it.key } }
    val focus by remember(keys, focusedKey, state) {
        derivedStateOf { shelfFocusIndex(keys, focusedKey, state.firstVisibleItemIndex) }
    }
    fun focusBook(index: Int) { books.getOrNull(index)?.let { onFocus(it.key) } }
    val activeKey = openingKey ?: returningKey ?: pulledKey ?: handoff?.from
    val bookIndex = if (activeKey == null) focus.coerceIn(books.indices) else
        books.indexOfFirst { it.key == activeKey }.takeIf { it >= 0 } ?: focus.coerceIn(books.indices)
    val currentBook = books[bookIndex]
    val activateBook: (Int) -> Unit = { index ->
        val book = books.getOrNull(index)
        if (openingKey == null && returningKey == null && book != null) {
            val currentPull = pulledKey
            val currentSwitch = handoff
            when {
                currentSwitch != null -> handoff = currentSwitch.copy(to = book.key)
                currentPull == null -> {
                    focusBook(index)
                    pulledKey = book.key
                }
                currentPull != book.key -> {
                    handoff = ShelfSwitch(currentPull, book.key)
                    pulledKey = null
                }
                else -> {
                    openingKey = book.key
                    pendingOpenKey = book.key
                    pulledKey = null
                }
            }
        }
    }
    val currentActivation by rememberUpdatedState(activateBook)
    fun stepFocus(delta: Int) {
        if (openingKey != null || returningKey != null) return
        val destination = (focus + delta).coerceIn(books.indices)
        pulledKey = null
        handoff = null
        focusBook(destination)
        if (state.canScrollForward || state.canScrollBackward) scope.launch { state.animateScrollToItem(destination) }
    }
    val currentStep by rememberUpdatedState<(Int) -> Unit>(::stepFocus)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter) {
                val preferredScale = if (maxWidth >= 600.dp) 1.15f else 1f
                val scale = (maxHeight.value / 266f).coerceAtMost(preferredScale)
                if (scale <= 0f) return@BoxWithConstraints
                val slotWidth = (144 * scale).dp
                val bankWidth = slotWidth + (36 * scale).dp * books.lastIndex
                val fits = bankWidth <= maxWidth - 2.dp
                val endSpace = (maxWidth - bankWidth).coerceAtLeast(0.dp)
                val centeredPadding = ((maxWidth - slotWidth) / 2).coerceAtLeast(0.dp)
                val leading = if (fits) (endSpace / 2 - (16 * scale).dp).coerceAtLeast(0.dp) else centeredPadding
                val trailing = if (fits) endSpace - leading else centeredPadding
                LaunchedEffect(state, fits, keys) {
                    if (fits) {
                        if (state.firstVisibleItemIndex != 0 || state.firstVisibleItemScrollOffset != 0) state.scrollToItem(0)
                    } else {
                        // Restore the selected key before observing the newly composed bank.
                        if (focusedKey in keys) state.scrollToItem(focus)
                        snapshotFlow {
                            if (state.isScrollInProgress) null else {
                                val layout = state.layoutInfo
                                val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                                layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - centre) }?.index
                            }
                        }.filterNotNull().collect { focusBook(it) }
                    }
                }
                val fitSwipe = if (fits && books.size > 1) Modifier.pointerInput(Unit) {
                    var distance = 0f
                    detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                        val delta = (-distance / 46.dp.toPx()).roundToInt()
                        if (delta != 0) currentStep(delta)
                    }) { change, delta ->
                        change.consume()
                        distance += delta
                    }
                } else Modifier
                Box(Modifier.fillMaxWidth().height((266*scale).dp)) {
                    ProjectedShelfFloor(wash, dark, scale, false, Modifier.matchParentSize())
                    LazyRow(state = state, modifier = Modifier.fillMaxWidth().height((266*scale).dp)
                        .onGloballyPositioned { rootOrigin = it.positionInRoot() }
                        .pointerInput(books, density) {
                            detectTapGestures { point ->
                                val rootPoint = rootOrigin + point
                                // The front-most actual polygon wins; empty corners never select a book.
                                hits.values.asSequence().filter { hit ->
                                    val local = (rootPoint - hit.origin) / density
                                    val covered = hit.occlusion?.let { mask ->
                                        local.y >= mask.top && mask.book.contains(local.x - mask.dx, local.y)
                                    } ?: false
                                    !covered && hit.solid.contains(local.x, local.y)
                                }.maxWithOrNull(compareBy<SolidHit> { it.rank }.thenBy { it.index })?.let { currentActivation(it.index) }
                            }
                        }.then(fitSwipe),
                        contentPadding = PaddingValues(start = leading, end = trailing),
                        userScrollEnabled = !fits && openingKey == null && returningKey == null,
                        horizontalArrangement = Arrangement.spacedBy((-108*scale).dp),
                        flingBehavior = rememberSnapFlingBehavior(state)) {
                        items(count = books.size, key = { books[it].key }) { index ->
                            val book = books[index]
                            SolidVolume(book, index, pulledKey == book.key, openingKey == book.key, returningKey == book.key, dark, scale, reduced,
                                handingOff = handoff?.from == book.key,
                                wash = wash, hasNext = index < books.lastIndex,
                                shelfSource = currentBook.key == book.key && openingKey == null,
                                onGeometry = { origin, solid, rank, mask -> hits[book.key] = SolidHit(index, origin, solid, rank, mask) },
                                onDispose = { hits.remove(book.key) }, onActivate = { activateBook(index) },
                                onUpright = {
                                    if (pendingOpenKey == book.key) {
                                        pendingOpenKey = null
                                        onOpen(book)
                                    }
                                }, onReturned = { if (returningKey == book.key) returningKey = null },
                                onHandedOff = {
                                    val next = handoff?.takeIf { it.from == book.key }?.to
                                    if (next != null) {
                                        handoff = null
                                        val nextIndex = books.indexOfFirst { it.key == next }
                                        if (nextIndex >= 0 && state.layoutInfo.visibleItemsInfo.any { it.key == next }) {
                                            focusBook(nextIndex)
                                            pulledKey = next
                                        }
                                    }
                                })
                        }
                    }
                    ProjectedShelfFloor(wash, dark, scale, true, Modifier.matchParentSize())
                    if (!fits && activeKey == null) Text("左右滑动浏览全部藏书", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = captionInset, end = captionInset, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val pulled = pulledKey == currentBook.key
                val pigment = MaterialTheme.colorScheme.tertiary
                Column(Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(enabled = openingKey == null && returningKey == null && handoff == null, role = Role.Button,
                        onClickLabel = if (pulled) "进入详情" else "抽出书籍") { activateBook(bookIndex) },
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(when {
                        returningKey != null || handoff != null -> "正在归位"
                        pulled -> "已取出 · 再点进入"
                        else -> "藏书索引"
                    }, color = pigment,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp))
                    Text(currentBook.title, modifier = Modifier.watercolorWash(pigment, if (pulled) .32f else .14f),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium, fontSize = 19.sp, letterSpacing = .25.sp, lineHeight = 23.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(currentBook.author.ifBlank { "本地藏书" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(letterSpacing = .4.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(enabled = bookIndex > 0 && openingKey == null && returningKey == null, onClick = { stepFocus(-1) }) {
                    Text("‹", Modifier.semantics { contentDescription = "上一本" })
                }
                Text("${bookIndex + 1} / ${books.size}", style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Serif, fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(enabled = bookIndex < books.lastIndex && openingKey == null && returningKey == null, onClick = { stepFocus(1) }) {
                    Text("›", Modifier.semantics { contentDescription = "下一本" })
                }
            }
        }
}

@Composable
private fun SolidVolume(book: ShelfBook, index: Int, pulled: Boolean, opening: Boolean, returning: Boolean, dark: Boolean, scale: Float, reduced: Boolean,
    handingOff: Boolean, wash: ImageBitmap?, hasNext: Boolean, shelfSource: Boolean,
    onGeometry: (Offset, BookSolid, Float, ShelfOcclusion?) -> Unit, onDispose: () -> Unit,
    onActivate: () -> Unit, onUpright: () -> Unit, onReturned: () -> Unit, onHandedOff: () -> Unit) {
    val amount = animateFloatAsState(if (pulled || opening) 1f else 0f,
        when {
            reduced -> tween(0)
            handingOff -> tween(215, easing = Motion.Decelerate)
            else -> spring(dampingRatio = 1f, stiffness = 340f)
        }, label = "rigid-book-pull")
    val targetFacing = if (opening) 1f else 0f
    val facing = animateFloatAsState(targetFacing, tween(if (reduced) 0 else BookCoverMotion.TurnMs,
        easing = Motion.Decelerate),label = "whole-book-turn")
    val upright = opening && facing.value == 1f
    val currentUpright by rememberUpdatedState(onUpright)
    val currentReturned by rememberUpdatedState(onReturned)
    val currentHandoff by rememberUpdatedState(onHandedOff)
    val readyToHandoff = handingOff && amount.value <= ShelfBankLayerThreshold
    LaunchedEffect(readyToHandoff) {
        if (readyToHandoff) {
            withFrameNanos { }
            currentHandoff()
        }
    }
    LaunchedEffect(upright) {
        if (upright) {
            withFrameNanos { }
            currentUpright()
        }
    }
    val landing = remember(returning) { Animatable(0f) }
    LaunchedEffect(returning) {
        if (returning) {
            landing.animateTo(1f, tween(if (reduced) 0 else 470, easing = androidx.compose.animation.core.LinearEasing))
            currentReturned()
        }
    }
    val solid = if (returning) remember(landing.value, scale) { projectReturningBook(landing.value).scaled(scale) }
        else remember(amount.value, facing.value, scale) { projectBookSolid(amount.value, facing = facing.value).scaled(scale) }
    val rank = when {
        opening || returning || facing.value > .001f -> 1_000_000_000f
        pulled -> 900_000_000f
        amount.value > ShelfBankLayerThreshold -> 800_000_000f
        else -> index.toFloat()
    }
    // Global coordinates are hit-test data, not visual state. Scrolling must not recompose every book.
    val origin = remember { arrayOf(Offset.Zero) }
    DisposableEffect(book.key) { onDispose { onDispose() } }
    val color = jacketColor(book.key, dark)
    val ink = if (dark) Color(0xFFE9E4D9) else Color(0xFF353E4C)
    val capInk = if (dark) Color(0xFFB2AD9F) else Color(0xFFF2EADC)
    val marker = MaterialTheme.colorScheme.tertiary
    val step = with(LocalDensity.current) { ((144 * scale).dp.roundToPx() + (-108 * scale).dp.roundToPx()).toDp().value }
    val nextOffset = if (LocalLayoutDirection.current == LayoutDirection.Ltr) step else -step
    val tuck = when {
        !hasNext -> 0f
        returning -> ((landing.value - .52f) / .42f).coerceIn(0f, 1f)
        pulled || opening || amount.value > ShelfBankLayerThreshold -> shelfPullTuck(amount.value)
        else -> 0f
    }
    val neighbor = remember(scale) { projectBookSolid(0f).scaled(scale) }
    val occlusion = if (tuck > 0f) ShelfOcclusion(neighbor, nextOffset, 266f * scale * (1f - tuck)) else null
    SideEffect { onGeometry(origin[0], solid, rank, occlusion) }
    Box(Modifier.width((144*scale).dp).height((266*scale).dp)
        .zIndex(rank)
        .tuckBehindBook(occlusion)
        .onGloballyPositioned { origin[0] = it.positionInRoot(); onGeometry(origin[0], solid, rank, occlusion) }
        .semantics {
            contentDescription = "架上书籍：${book.title}"
            stateDescription = if (returning) "正在归位" else if (pulled || opening) "已抽出" else "在架上"
            onClick(if (pulled || opening) "进入详情" else "抽出书籍") { onActivate(); true }
        }) {
        ProjectedBookFace(solid.cover, JacketWidth*scale,JacketHeight*scale, sharedKey = if (pulled || opening) "cover:${book.sourceId}:${book.bookUrl}" else null) {
            val titleAlpha = if (returning) ((.88f - landing.value) / .42f).coerceIn(0f, 1f) else amount.value.coerceIn(0f, 1f)
            Cover(book.coverUrl, book.title, Modifier.fillMaxSize(), radius = 1.dp, jacketKey = book.key,
                fallbackTitleAlpha = titleAlpha)
        }
        Canvas(Modifier.fillMaxSize()) {
            if (shelfSource && !pulled && !opening && !returning && amount.value < .015f) {
                drawCircle(marker.copy(alpha = .65f), (2.2f * scale).dp.toPx(),
                    Offset((59f * scale).dp.toPx(), (258f * scale).dp.toPx()))
            }
            // Once upright the cap is edge-on; drawing its collapsed outline leaves a ghost line during flight.
            if (solid.cap.bottom-solid.cap.top < .01f) return@Canvas
            fun path(face: SolidFace) = Path().apply {
                moveTo(face.a.x.dp.toPx(),face.a.y.dp.toPx())
                face.points.drop(1).forEach { lineTo(it.x.dp.toPx(),it.y.dp.toPx()) }; close()
            }
            val cap = path(solid.cap)
            drawPath(cap, capInk.copy(alpha = .9f))
            for (n in 1..4) {
                val f = n / 5f
                val a = solid.cap.a; val b = solid.cap.d; val e = solid.cap.b; val c = solid.cap.c
                drawLine(color.copy(alpha = .18f), Offset((a.x+(b.x-a.x)*f).dp.toPx(),(a.y+(b.y-a.y)*f).dp.toPx()),
                    Offset((e.x+(c.x-e.x)*f).dp.toPx(),(e.y+(c.y-e.y)*f).dp.toPx()), .45.dp.toPx())
            }
            drawPath(cap, color.copy(alpha = .17f), style = Stroke(.4.dp.toPx()))
        }
        ProjectedBookFace(solid.spine,17f*scale,154f*scale) {
            Column(Modifier.fillMaxSize().background(lerp(MaterialTheme.colorScheme.surface,color,.68f))
                .bookMaterial(LocalBookMaterials.current,BookMaterial.PAPER,.16f)
                .drawWithContent {
                    drawContent()
                    if (wash != null) drawImage(wash, srcOffset = IntOffset(0,65), srcSize = IntSize(wash.width,225),
                        dstSize = IntSize(size.width.roundToInt(),size.height.roundToInt()), alpha = .22f, blendMode = BlendMode.Multiply)
                }
                .padding(top = (10*scale).dp,bottom = (8*scale).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val vertical = book.title.any { it in '\u2E80'..'\u9FFF' || it in '\uAC00'..'\uD7A3' }
                if (vertical) Text(book.title.take(8).map { it.toString() }.joinToString("\n"), Modifier.weight(1f),
                    fontSize = (10*scale).sp, lineHeight = (14*scale).sp, fontFamily = FontFamily.Serif, color = ink)
                else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(book.title, Modifier.requiredSize((120*scale).dp, (17*scale).dp)
                        .graphicsLayer { rotationZ = 90f }, fontSize = (9*scale).sp, lineHeight = (10*scale).sp,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Serif, color = ink)
                }
                Text("${index+1}",fontSize = (7*scale).sp,maxLines = 1,softWrap = false,color = ink.copy(alpha = .7f))
            }
        }
    }
}

/** Reveal the neighboring volume progressively, before the returning volume rejoins the bank's z-order. */
private fun Modifier.tuckBehindBook(occlusion: ShelfOcclusion?): Modifier =
    if (occlusion == null) this else drawWithCache {
        val next = occlusion.book
        val dx = occlusion.dx.dp.toPx()
        val mask = Path().apply {
            for (face in listOf(next.spine, next.cover, next.cap)) {
                moveTo(face.a.x.dp.toPx() + dx, face.a.y.dp.toPx())
                for (point in listOf(face.b, face.c, face.d)) lineTo(point.x.dp.toPx() + dx, point.y.dp.toPx())
                close()
            }
        }
        val edge = occlusion.top.dp.toPx()
        onDrawWithContent {
            if (edge > 0f) clipRect(bottom = edge) { this@onDrawWithContent.drawContent() }
            clipRect(top = edge) {
                clipPath(mask, clipOp = ClipOp.Difference) { this@onDrawWithContent.drawContent() }
            }
        }
    }

private fun BookSolid.scaled(scale: Float): BookSolid {
    fun SolidPoint.scaled() = SolidPoint(x*scale,y*scale)
    fun SolidFace.scaled() = SolidFace(a.scaled(),b.scaled(),c.scaled(),d.scaled())
    return BookSolid(spine.scaled(),cover.scaled(),cap.scaled())
}

/** The shelf top uses the same depth basis and resting baseline as the books. */
@Composable
private fun ProjectedShelfFloor(wash: ImageBitmap?, dark: Boolean, scale: Float,
    front: Boolean, modifier: Modifier) {
    Box(modifier.drawWithCache {
        val origin = 12.dp.toPx()
        val base = (249.2f * scale).dp.toPx()
        val rest = projectBookSolid(0f).scaled(scale)
        val dx = (rest.cover.b.x - rest.cover.a.x).dp.toPx()
        val dy = (rest.cover.b.y - rest.cover.a.y).dp.toPx()
        val width = (size.width - dx - origin * 2).coerceAtLeast(1f)
        val top = Path().apply {
            moveTo(origin, base); lineTo(origin + width, base)
            lineTo(origin + width + dx, base + dy); lineTo(origin + dx, base + dy); close()
        }
        val matrix = NativeMatrix().apply {
            setPolyToPoly(floatArrayOf(0f, 0f, width, 0f, 0f, 100f), 0,
                floatArrayOf(origin, base, origin + width, base, origin + dx, base + dy), 0, 3)
        }
        val topSource = wash?.let { IntSize(it.width, 225) }
        val frontSource = wash?.let { IntSize(it.width, 155) }
        val topTarget = IntSize(width.roundToInt(), 100)
        val frontTarget = IntSize(width.roundToInt(), (5 * scale).dp.toPx().roundToInt().coerceAtLeast(1))
        val frontOffset = IntOffset(origin.roundToInt(), (base - 1.dp.toPx()).roundToInt())
        onDrawBehind {
            if (!front) {
                if (wash == null) drawPath(top, Color(0xFFAD927B).copy(alpha = if (dark) .24f else .17f))
                else drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save(); canvas.nativeCanvas.concat(matrix)
                    drawImage(wash, srcOffset = IntOffset(0, 65), srcSize = checkNotNull(topSource),
                        dstSize = topTarget, alpha = if (dark) .4f else .72f, filterQuality = FilterQuality.Medium)
                    canvas.nativeCanvas.restore()
                }
            } else if (wash != null) {
                drawImage(wash, srcOffset = IntOffset(0, 115), srcSize = checkNotNull(frontSource),
                    dstOffset = frontOffset, dstSize = frontTarget,
                    alpha = if (dark) .25f else .36f, filterQuality = FilterQuality.Medium)
            }
        }
    })
}

/** An affine face is painted inside its projected bounds, including the shared-cover origin. */
@Composable
private fun ProjectedBookFace(face: SolidFace, width: Float, height: Float, sharedKey: String? = null,
    content: @Composable () -> Unit) {
    if (face.right-face.left < .01f || face.bottom-face.top < .01f) return
    val density = LocalDensity.current.density
    val matrix = remember(face,width,height,density) {
        val originX = (face.left*density).roundToInt()
        val originY = (face.top*density).roundToInt()
        NativeMatrix().apply {
            setPolyToPoly(floatArrayOf(0f,0f,width*density,0f,0f,height*density),0,
                floatArrayOf(face.a.x*density-originX,face.a.y*density-originY,
                    face.b.x*density-originX,face.b.y*density-originY,
                    face.d.x*density-originX,face.d.y*density-originY),0,3)
        }
    }
    val bounds = Modifier.offset { IntOffset((face.left*density).roundToInt(),(face.top*density).roundToInt()) }
        .bookCoverTransition(sharedKey)
    Layout(content = {
        Box(Modifier.drawWithContent {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save(); canvas.nativeCanvas.concat(matrix)
                this@drawWithContent.drawContent(); canvas.nativeCanvas.restore()
            }
        }) { content() }
    }, modifier = bounds) { measurables, _ ->
        val child = measurables.single().measure(Constraints.fixed((width*density).roundToInt(),(height*density).roundToInt()))
        layout(ceil((face.right-face.left)*density).toInt(),ceil((face.bottom-face.top)*density).toInt()) { child.place(0,0) }
    }
}
