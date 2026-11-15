@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.will.lightreader.ui.reader

import com.will.lightreader.ui.components.paperFeedback

import com.will.lightreader.ui.components.watercolorWash
import com.will.lightreader.ui.components.watercolorSurface
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.will.lightreader.di.AppContainer
import com.will.lightreader.domain.Block
import com.will.lightreader.domain.ChapterContent
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.local.LocalIds
import com.will.lightreader.source.web.WebSessionActivity
import com.will.lightreader.ui.ReaderSystemBars
import com.will.lightreader.ui.SystemBarsAppearance
import com.will.lightreader.ui.VolumeKeyBridge
import com.will.lightreader.ui.anim.rememberHapticTap
import com.will.lightreader.ui.components.ChapterDrawer
import com.will.lightreader.ui.theme.AppIcons
import com.will.lightreader.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/** 纸色（随状态切换平滑过渡）。 */
private data class Paper(val bg: Color, val text: Color, val muted: Color, val accent: Color)

@Composable
private fun animatePaper(paper: String): Paper {
    val base = when (paper) {
        "light" -> Paper(Color(0xFFFFFEFA), Color(0xFF30382C), Color(0xFF66705F), Color.Unspecified)
        "sepia" -> Paper(Color(0xFFF3F0E4), Color(0xFF393D32), Color(0xFF5F6958), Color.Unspecified)
        "dark" -> Paper(Color(0xFF20232D), Color(0xFFD8D5D1), Color(0xFFA4A1AF), Color.Unspecified)
        else -> Paper(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.onBackground,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Color.Unspecified,
        )
    }
    val accentTarget = if (base.bg.luminance() < 0.4f) Color(0xFFBAC4EF) else MaterialTheme.colorScheme.primary
    val paperDuration = if (Motion.reducedMotion()) 0 else 160
    val bg by animateColorAsState(base.bg, tween(paperDuration), label = "paper-bg")
    val ink by animateColorAsState(base.text, tween(paperDuration), label = "paper-text")
    val muted by animateColorAsState(base.muted, tween(paperDuration), label = "paper-muted")
    val accent by animateColorAsState(accentTarget, tween(paperDuration), label = "paper-accent")
    return Paper(bg, ink, muted, accent)
}

@Composable
fun ReaderScreen(
    container: AppContainer,
    sourceId: String,
    bookKey: String,
    chapterUrl: String,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { ReaderPrefs(context) }
    val vm: ReaderViewModel = viewModel(
        factory = ReaderViewModel.factory(
            container.library, container.sources, prefs, container.prefetchImage, sourceId, bookKey, chapterUrl
        )
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val paper = animatePaper(state.paper)
    var showMore by remember { mutableStateOf(false) }
    var chromeTouching by remember { mutableStateOf(false) }
    // 控制栏：点正文中部展开；6s 无操作自动收起
    var chromeOpen by remember { mutableStateOf(false) }
    var chromeTick by remember { mutableIntStateOf(0) }
    var pageInfo by remember { mutableStateOf(0 to 1) }
    // 本地 CBZ：每张图一章，但仍是漫画页模型，可提供与在线漫画相同的翻页/连续下滑切换。
    val importedComic = state.content?.fixedPage?.let { it.pdfIndex == null } == true
    var showSearch by remember { mutableStateOf(false) }
    // PDF 适页/适宽请求（序号 + 模式），从设置面板发到正文
    var pdfFitRequest by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var excerptTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.repairNotice) {
        state.repairNotice?.let { toast = it; vm.consumeRepairNotice() }
    }
    val search by vm.search.collectAsState()
    val hapticTap = rememberHapticTap()
    // 音量键翻页信号（-1=上一页，+1=下一页；到边界自动翻章）
    val pageTurns = remember { MutableSharedFlow<Int>(extraBufferCapacity = 8) }
    val pageSeeks = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1,onBufferOverflow=kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST) }
    // 位置记忆：翻页式锚点（当前页首页块号 + 进度比例）。锚点必须属于当前章才允许提交，
    // 否则换章动画里旧章的回调会把旧块号写进新章读档。
    var anchor by remember { mutableStateOf(Triple(0, 0, 0f)) }
    var anchorChapter by remember { mutableStateOf("") }
    val commitAnchor: (String, Int, Int, Float) -> Unit = { chapter, block, char, ratio ->
        val current = vm.state.value
        if (chapter == current.chapterUrl && !current.loading) {
            val next = Triple(block, char, ratio)
            if ((anchorChapter != chapter || anchor != next) &&
                current.content?.fixedPage == null && current.content?.comicPages?.isEmpty() == true) {
                vm.savePosition(block, ratio, char)
            }
            anchor = next
            anchorChapter = chapter
        }
    }
    // Backgrounding and disposal flush the last committed anchor using the application scope.
    val latestAnchor by rememberUpdatedState(anchor)
    val latestAnchorChapter by rememberUpdatedState(anchorChapter)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        fun persistPosition() {
            val s = vm.state.value
            val strip = vm.importedComicAnchor()
            if (!s.loading && s.comicMode == "scroll" && s.sourceId == com.will.lightreader.source.local.LocalIds.SOURCE &&
                s.content?.fixedPage?.pdfIndex == null && s.content?.fixedPage != null && strip != null && strip.first == s.chapterUrl) {
                container.appScope.launch {
                    runCatching { container.library.savePosition(s.bookKey, strip.first, strip.second, 0, strip.third) }
                }
                return
            }
            if (!s.loading && latestAnchorChapter == s.chapterUrl &&
                s.content?.chapterUrl == s.chapterUrl && s.content?.fixedPage == null &&
                s.content?.comicPages?.isEmpty() == true) {
                prefs.saveInlineAnchor(s.chapterUrl, latestAnchor.first, latestAnchor.second)
                container.appScope.launch {
                    runCatching {
                        container.library.savePosition(s.bookKey, s.chapterUrl, s.chapterTitle,
                            latestAnchor.first, latestAnchor.third)
                    }
                }
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) persistPosition()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshPreferences()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            persistPosition()
        }
    }
    // 换章提交后清掉旧章页码，避免控制栏短暂显示旧章的 x/y
    val chromeTimeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        6000L, containsIcons = true, containsText = true, containsControls = true) ?: 6000L
    val panelOpen = showMore || showSearch || excerptTarget != null ||
        drawerState.targetValue == DrawerValue.Open || drawerState.currentValue == DrawerValue.Open
    LaunchedEffect(chromeOpen, chromeTick, chromeTouching, panelOpen, chromeTimeout) {
        if (chromeOpen && !chromeTouching && !panelOpen) {
            delay(chromeTimeout)
            chromeOpen = false
        }
    }
    BackHandler(chromeOpen && !panelOpen) { chromeOpen = false }
    BackHandler(showSearch) { showSearch = false; vm.clearSearch() }
    // 关闭设置面板/目录后重新展开控制栏，并把键盘/TalkBack 焦点交回入口
    val moreFocus = remember { FocusRequester() }
    val catalogFocus = remember { FocusRequester() }
    val closeMore: () -> Unit = { showMore = false; chromeOpen = true; chromeTick++ }
    PanelFocusReturn(showMore, moreFocus, closeMore)
    PanelFocusReturn(drawerState.isOpen, catalogFocus) { chromeOpen = true; chromeTick++ }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1500)
            toast = null
        }
    }
    val revealChrome = { chromeOpen = true; chromeTick++ }
    // 当前页书签锚点：本摊首个正文块
    val pageBookmarked = state.bookmarks.any {
        it.chapterUrl == state.chapterUrl && it.blockIndex == anchor.first
    }
    val toggleBookmarkAtPage = bookmark@{
        if (anchorChapter != state.chapterUrl || state.loading) return@bookmark
        val blockIndex = anchor.first
        val blocks = state.content?.blocks.orEmpty()
        // 锚点可能是分隔符/短标记，优先取同页靠前的完整段落做摘要
        val text = blocks.drop(blockIndex).take(3)
            .mapNotNull { (it as? Block.Paragraph)?.text?.takeIf { t -> t.length >= 8 } }
            .firstOrNull()
            ?: (blocks.getOrNull(blockIndex) as? Block.Paragraph)?.text.orEmpty().ifBlank { state.chapterTitle }
        val existed = pageBookmarked
        vm.toggleBookmark(blockIndex, text)
        toast = if (existed) "已移除书签" else "已加入书签"
    }
    // 模式切换：保留当前图片。本地漫画每页独立成章，切回翻页时需载入当前页那一章。
    val switchComicMode: (String) -> Unit = { mode ->
        val index = pageInfo.first.coerceAtLeast(0)
        vm.setComicMode(mode, index)
        if (importedComic && mode == "paged") {
            state.chapters.getOrNull(index)?.let { chapter ->
                if (chapter.chapterUrl != state.content?.chapterUrl) vm.navigateToChapter(chapter.chapterUrl)
            }
        }
    }

    // 状态栏图标随纸色明暗自适应（微信式）；背景延伸到状态栏下，内容避让安全区
    SystemBarsAppearance(
        lightContent = paper.bg.luminance() < .4f
    )
    ReaderSystemBars(state.immersive, state.hideStatusBar)
    LaunchedEffect(Unit) { vm.loadChapters(bookKey) }

    val drawerBg = lerp(paper.bg, paper.text, 0.045f)
    ChapterDrawer(
        chapters = state.chapters,
        currentChapterUrl = state.chapterUrl,
        bookmarks = state.bookmarks,
        excerpts = state.excerpts,
        drawerState = drawerState,
        containerColor = drawerBg,
        contentColor = paper.text,
        mutedColor = paper.muted,
        accentColor = paper.accent,
        onChapterClick = { url ->
            chromeOpen = false
            scope.launch { drawerState.close() }
            vm.navigateToChapter(url)
        },
        onBookmarkClick = { mark ->
            chromeOpen = false
            scope.launch { drawerState.close() }
            vm.jumpTo(mark.chapterUrl, mark.blockIndex)
        },
        onBookmarkDelete = { vm.removeBookmark(it) },
        onExcerptClick = { excerpt ->
            chromeOpen = false
            scope.launch { drawerState.close() }
            vm.jumpTo(excerpt.chapterUrl, excerpt.blockIndex)
        },
        onExcerptDelete = { vm.removeExcerpt(it) },
        onClose = { scope.launch { drawerState.close() } },
    ) {
        ReaderViewport(Modifier.fillMaxSize().background(paper.bg).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val touching = event.changes.any { it.pressed }
                    if (chromeTouching && !touching) chromeTick++
                    chromeTouching = touching
                }
            }
        }) {
            // 自定义背景：图片铺底 + 纸色半透明罩（保文字可读）
            if (state.bgUri.isNotBlank()) {
                AsyncImage(
                    model = state.bgUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(paper.bg.copy(alpha = 0.82f)))
            }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)) {
                val content = state.content
                when {
                    content != null -> {
                        val reduced = Motion.reducedMotion()
                        AnimatedContent(
                            targetState = content,
                            transitionSpec = {
                                val imageComic = targetState.comicPages.isNotEmpty() || targetState.fixedPage?.let { it.pdfIndex == null } == true
                                val direction = state.chapterDirection * if (imageComic && state.comicRtl) -1 else 1
                                if (reduced || direction == 0) {
                                    fadeIn(tween(180, easing = Motion.Decelerate)) togetherWith
                                        fadeOut(tween(140, easing = Motion.ExitOut))
                                } else if (imageComic && state.comicMode == "scroll") {
                                    fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                                } else if (state.turnStyle == "cover") {
                                    if (direction > 0) {
                                        (slideInHorizontally(tween(240, easing = Motion.Decelerate)) { it } togetherWith
                                            ExitTransition.None).apply { targetContentZIndex = 1f }
                                    } else {
                                        (EnterTransition.None togetherWith
                                            slideOutHorizontally(tween(240, easing = Motion.Decelerate)) { it })
                                            .apply { targetContentZIndex = -1f }
                                    }
                                } else {
                                    slideInHorizontally(tween(240, easing = Motion.Decelerate)) { it * direction } togetherWith
                                        slideOutHorizontally(tween(240, easing = Motion.Decelerate)) { -it * direction }
                                }
                            },
                            label = "chapter",
                        ) { chapterContent ->
                            val imagePage = chapterContent.fixedPage?.takeIf { it.pdfIndex == null }
                            if (chapterContent.comicPages.isNotEmpty()) ComicChapterBody(
                                chapterContent, state, vm, pageTurns, paper.bg, paper.text,
                                onPage = { i, total -> if (chapterContent.chapterUrl == vm.state.value.chapterUrl) pageInfo = i to total },
                                onAnchor = { i, r -> commitAnchor(chapterContent.chapterUrl, i, 0, r) },
                                onTap = { if (chromeOpen) chromeOpen = false else revealChrome() },
                            ) else if (imagePage != null) ImportedComicBody(
                                content = chapterContent, state = state, vm = vm, pageTurns = pageTurns,
                                background = paper.bg, ink = paper.text,
                                onPage = { i, total -> pageInfo = i to total },
                                onAnchor = { i, r -> commitAnchor(state.chapterUrl, i, 0, r) },
                                onTap = { if (chromeOpen) chromeOpen = false else revealChrome() },
                            ) else if (chapterContent.fixedPage != null) FixedPageBody(
                                content = chapterContent, state = state, vm = vm, pageTurns = pageTurns,
                                background = paper.bg, ink = paper.text,
                                onPage = { i, total -> if (chapterContent.chapterUrl == vm.state.value.chapterUrl) pageInfo = i to total },
                                onAnchor = { commitAnchor(chapterContent.chapterUrl, 0, 0, 0f) },
                                onTap = { if (chromeOpen) chromeOpen = false else revealChrome() },
                                fitRequest = pdfFitRequest,
                                inputEnabled = chapterContent.chapterUrl == state.chapterUrl && !state.loading,
                            ) else ChapterBody(
                                vm = vm, state = state, content = chapterContent, paper = paper,
                                initialBlockIndex = state.startBlockIndex,
                                pageTurns = pageTurns,
                                pageSeeks = pageSeeks,
                                onAnchor = { b, c, r -> commitAnchor(chapterContent.chapterUrl, b, c, r) },
                                onPageInfo = { index, total -> if (chapterContent.chapterUrl == vm.state.value.chapterUrl) pageInfo = index to total },
                                onContentTap = { if (chromeOpen) chromeOpen = false else revealChrome() },
                                onLongPressParagraph = { blockIndex, text ->
                                    if (chromeOpen) chromeOpen = false
                                    excerptTarget = blockIndex to text
                                },
                            )
                        }
                    }
                    state.error != null -> ReaderError(
                        state, paper,
                        onRetry = { vm.load(state.chapterUrl) },
                        onWeb = {
                            context.startActivity(WebSessionActivity.intent(context, state.chapterUrl, "站点登录 / 验证"))
                        },
                    )
                    else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(1f))
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(if (state.repairing)
                                com.will.lightreader.R.drawable.watercolor_read else com.will.lightreader.R.drawable.watercolor_read),
                            contentDescription = null, modifier = Modifier.size(80.dp))
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(Modifier.size(20.dp), color = paper.accent, strokeWidth = 1.5.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(if (state.repairing) "正在重新获取这一章…" else "正在打开这一章…", color = paper.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1.4f))
                    }
                }
            }

            ReaderBottomChrome(
                state = state,
                paper = paper,
                expanded = chromeOpen,
                pageIndex = pageInfo.first,
                pageCount = pageInfo.second,
                bookmarked = pageBookmarked,
                onExpand = { hapticTap(); revealChrome() },
                onCollapse = { hapticTap(); chromeOpen = false },
                onBack = { hapticTap(); chromeOpen = false; onBack() },
                onCatalog = {
                    hapticTap(); chromeOpen = false
                    scope.launch { drawerState.open() }
                },
                onBookmark = { hapticTap(); chromeOpen = false; toggleBookmarkAtPage() },
                onSearch = { hapticTap(); chromeOpen = false; showSearch = true },
                onPrev = {
                    chromeOpen = false
                    if (state.content?.comicPages?.isNotEmpty() == true || (importedComic && state.comicMode == "scroll"))
                        scope.launch { pageTurns.emit(-1) } else vm.prevChapter()
                },
                onNext = {
                    chromeOpen = false
                    if (state.content?.comicPages?.isNotEmpty() == true || (importedComic && state.comicMode == "scroll"))
                        scope.launch { pageTurns.emit(1) } else vm.nextChapter()
                },
                onMore = { hapticTap(); chromeOpen = false; showMore = true },
                onSeek = { index ->
                    chromeTick++
                    when {
                        state.content?.comicPages?.isNotEmpty()==true || importedComic && state.comicMode=="scroll" -> vm.jumpTo(state.chapterUrl,index)
                        state.content?.fixedPage!=null -> state.chapters.getOrNull(index)?.let { vm.navigateToChapter(it.chapterUrl) }
                        else -> pageSeeks.tryEmit(index)
                    }
                },
                moreFocus = moreFocus,
                catalogFocus = catalogFocus,
            )

            // 轻提示：书签 / 摘抄操作的即时反馈
            Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(top = 12.dp)) {
                AnimatedVisibility(
                    visible = toast != null,
                    enter = fadeIn(tween(160, easing = Motion.Decelerate)) +
                        slideInVertically(tween(200, easing = Motion.Decelerate)) { -it / 3 },
                    exit = fadeOut(tween(160, easing = Motion.ExitOut)),
                ) {
                    Text(
                        toast.orEmpty(),
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(lerp(paper.bg, paper.text, 0.10f))
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = paper.text,
                    )
                }
            }

            // 换章进行中/失败：旧页保持可读，顶部提示目标章与重试；快开（<400ms）不闪提示
            val pendingChapter = state.loading && state.content != null
            val loadFailed = state.error != null && state.content != null
            var showPending by remember { mutableStateOf(false) }
            LaunchedEffect(pendingChapter) {
                if (pendingChapter) { delay(400); showPending = true } else showPending = false
            }
            Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(top = 12.dp)) {
                AnimatedVisibility(
                    visible = showPending || loadFailed,
                    enter = fadeIn(tween(160, easing = Motion.Decelerate)),
                    exit = fadeOut(tween(140, easing = Motion.ExitOut)),
                ) {
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(lerp(paper.bg, paper.text, 0.10f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val targetTitle = state.chapters.firstOrNull { it.chapterUrl == state.chapterUrl }?.title
                        if (showPending && !loadFailed) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = paper.accent, strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在打开「${targetTitle ?: "下一章"}」…", style = MaterialTheme.typography.labelMedium, color = paper.text)
                        } else {
                            Text("「${targetTitle ?: state.chapterTitle}」加载失败", style = MaterialTheme.typography.labelMedium, color = paper.text)
                            Spacer(Modifier.width(10.dp))
                            Text("重试", style = MaterialTheme.typography.labelMedium, color = paper.accent,
                                modifier = Modifier.minimumInteractiveComponentSize().clickable { vm.load(state.chapterUrl) }.padding(4.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("关闭", style = MaterialTheme.typography.labelMedium, color = paper.muted,
                                modifier = Modifier.minimumInteractiveComponentSize().clickable { vm.dismissError() }.padding(4.dp))
                        }
                    }
                }
            }

            // 面板打开时，音量键交回系统；阅读时统一走同一翻页入口。
            val volumeBlocked by rememberUpdatedState(panelOpen)
            DisposableEffect(Unit) {
                VolumeKeyBridge.handler = { dir ->
                    if (volumeBlocked) false else { pageTurns.tryEmit(dir); true }
                }
                onDispose { VolumeKeyBridge.handler = null }
            }

            if (showMore) {
                val content = state.content
                if (content?.comicPages?.isNotEmpty() == true) {
                    val current = !state.loading && content.chapterUrl == state.chapterUrl
                    val offline = current && (content.comicPages.all { it.localRef != null } || state.offlineComicChapter == state.chapterUrl)
                    val canDownload = current && vm.supportsComicDownload && !offline && !state.downloadActive
                    DocumentOptions(
                        background = paper.bg, ink = paper.text, onClose = closeMore,
                        immersive = state.immersive, hideStatusBar = state.hideStatusBar,
                        comicMode = state.comicMode, onComicMode = { switchComicMode(it) },
                        downloadLabel = when {
                            offline -> "本话已下载"
                            state.downloadActive -> state.downloadState ?: "下载中"
                            vm.supportsComicDownload -> "下载本话"
                            else -> null
                        },
                        downloadHint = state.downloadState?.takeIf {
                            current && state.comicDownloadAttempt == state.chapterUrl && it.startsWith("下载失败")
                        },
                        onDownload = if (canDownload) vm::downloadCurrent else null,
                        onOpenSettings = onOpenSettings?.let { open -> { showMore = false; open() } },
                    )
                } else if (state.content?.fixedPage != null) DocumentOptions(
                    background = paper.bg, ink = paper.text,
                    onClose = closeMore, immersive = state.immersive, hideStatusBar = state.hideStatusBar,
                    comicMode = if (importedComic) state.comicMode else null,
                    onComicMode = if (importedComic) switchComicMode else null,
                    onFitPage = if (importedComic) null else ({ pdfFitRequest = (pdfFitRequest?.first ?: 0) + 1 to "page" }),
                    onFitWidth = if (importedComic) null else ({ pdfFitRequest = (pdfFitRequest?.first ?: 0) + 1 to "width" }),
                    onOpenSettings = onOpenSettings?.let { open -> { showMore = false; open() } },
                ) else MoreSheet(
                    state = state,
                    paper = paper,
                    onFont = { vm.setFontSize(it - vm.state.value.fontSize) },
                    onLineHeight = { vm.setLineHeight(it) },
                    onPaper = { vm.setPaper(it) },
                    onClose = closeMore,
                    onOpenSettings = onOpenSettings?.let { open -> { showMore = false; open() } },
                )
            }

            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn(tween(180, easing = Motion.Decelerate)),
                exit = fadeOut(tween(150, easing = Motion.ExitOut)),
            ) {
                SearchPage(
                    state = state,
                    search = search,
                    paper = paper,
                    onQuery = { vm.searchBook(it) },
                    onHit = { hit ->
                        showSearch = false
                        vm.clearSearch()
                        vm.jumpTo(hit.chapterUrl, hit.blockIndex)
                    },
                    onClose = {
                        showSearch = false
                        vm.clearSearch()
                    },
                )
            }

            excerptTarget?.let { (blockIndex, text) ->
                ExcerptSheet(
                    paper = paper, immersive = state.immersive, hideStatusBar = state.hideStatusBar,
                    text = text,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("摘抄", text))
                        excerptTarget = null
                        toast = "已复制"
                    },
                    onAdd = {
                        vm.addExcerpt(blockIndex, text)
                        excerptTarget = null
                        toast = "已加入摘抄"
                    },
                    onClose = { excerptTarget = null },
                )
            }
        }
    }
}

/** 快速设置：字号与行距松手应用，纸色即时切换；完整偏好留在主设置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    state: ReaderState, paper: Paper, onFont: (Int) -> Unit, onLineHeight: (Float) -> Unit,
    onPaper: (String) -> Unit, onClose: () -> Unit, onOpenSettings: (() -> Unit)?,
) {
    val sheetBg = lerp(paper.bg, paper.text, .06f)
    val buttonBg = lerp(paper.bg, paper.text, .08f)
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg, contentColor = paper.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        ReaderSystemBars(state.immersive, state.hideStatusBar)
        SystemBarsAppearance(paper.bg.luminance() < .45f)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("阅读设置", Modifier.watercolorWash(paper.accent),
                style = MaterialTheme.typography.titleLarge, color = paper.text)
            ReaderPreferenceSlider("字号",state.fontSize.toFloat(),13f..32f,18,paper.text,paper.accent,
                format={ "${kotlin.math.round(it).toInt()}" },onCommit={onFont(kotlin.math.round(it).toInt())})
            ReaderPreferenceSlider("行距",state.lineHeight,1.4f..2.4f,19,paper.text,paper.accent,
                format={String.format(java.util.Locale.ROOT,"%.2f 倍",it)},
                onCommit={onLineHeight(kotlin.math.round(it*20f)/20f)})
            Text("纸色", style = MaterialTheme.typography.bodyLarge, color = paper.text)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("follow" to "跟随外观", "light" to "纯白", "sepia" to "米黄", "dark" to "夜读").forEach { (id, label) ->
                    SheetBtn(label, paper, buttonBg, active = state.paper == id) { onPaper(id) }
                }
            }
            if (onOpenSettings != null) androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                Text("更多偏好", color = paper.accent)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 书内搜索：输入 220ms 防抖，命中后跳段；范围 = 当前章 + 已下载章节。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPage(
    state: ReaderState,
    search: BookSearch,
    paper: Paper,
    onQuery: (String) -> Unit,
    onHit: (SearchHit) -> Unit,
    onClose: () -> Unit,
) {
    val pageBg = lerp(paper.bg, paper.text, 0.03f)
    val fieldBg = lerp(paper.bg, paper.text, 0.10f)
    val local = state.sourceId == LocalIds.SOURCE
    var query by remember { mutableStateOf(search.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        delay(220)
        onQuery(query)
    }
    Column(
        Modifier.fillMaxSize().background(pageBg)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).imePadding().windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("书内搜索", style = MaterialTheme.typography.titleLarge, color = paper.text)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (local) "范围：全书 ${state.chapters.size} 章" else "范围：当前章 + 已下载章节",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
            Box(
                Modifier.minimumInteractiveComponentSize().size(38.dp).clip(CircleShape)
                    .background(lerp(paper.bg, paper.text, 0.10f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Close, contentDescription = "关闭搜索",
                    tint = paper.muted, modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(14.dp)).background(fieldBg)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Search, contentDescription = null,
                tint = paper.muted, modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = paper.text),
                cursorBrush = SolidColor(paper.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onQuery(query) }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "输入关键词，搜索这本书",
                                style = MaterialTheme.typography.bodyMedium,
                                color = paper.muted.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.minimumInteractiveComponentSize().size(32.dp).clip(CircleShape)
                        .clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Close, contentDescription = "清空",
                        tint = paper.muted, modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            query.isBlank() -> Text(
                if (local) "本地书全部章节已导入，直接搜全书"
                else "当前章已缓存：${state.chapterTitle.ifBlank { "本章" }}；其余章节需先下载",
                style = MaterialTheme.typography.bodySmall,
                color = paper.muted,
                modifier = Modifier.padding(vertical = 18.dp),
            )
            search.running -> Row(
                Modifier.padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    Modifier.size(16.dp), strokeWidth = 2.dp, color = paper.accent,
                )
                Spacer(Modifier.width(10.dp))
                Text("搜索中…", style = MaterialTheme.typography.bodySmall, color = paper.muted)
            }
            search.done && search.hits.isEmpty() -> Text(
                "没有找到「${search.query}」",
                style = MaterialTheme.typography.bodySmall,
                color = paper.muted,
                modifier = Modifier.padding(vertical = 18.dp),
            )
            else -> {
                Text(
                    "${search.hits.size} 条结果 · 扫描 ${search.scanned} 章",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(search.hits) { hit ->
                        SearchHitRow(hit, paper) { onHit(hit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, paper: Paper, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                hit.chapterTitle.ifBlank { "本章" },
                style = MaterialTheme.typography.labelSmall,
                color = paper.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                hit.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = paper.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            AppIcons.Chevron, contentDescription = null,
            tint = paper.muted, modifier = Modifier.size(12.dp).rotate(-90f),
        )
    }
}

/** 长按段落：复制 / 加入摘抄。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcerptSheet(
    paper: Paper,
    immersive: Boolean,
    hideStatusBar: Boolean,
    text: String,
    onCopy: () -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    val sheetBg = lerp(paper.bg, paper.text, 0.06f)
    val quoteBg = lerp(paper.bg, paper.text, 0.05f)
    val btnBg = lerp(paper.bg, paper.text, 0.12f)
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = sheetBg,
        contentColor = paper.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        ReaderSystemBars(immersive, hideStatusBar)
        SystemBarsAppearance(paper.bg.luminance() < .45f)
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(horizontal = 22.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.Sparkle, contentDescription = null,
                    tint = paper.accent, modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("摘抄这一段", Modifier.watercolorWash(paper.accent), style = MaterialTheme.typography.titleLarge, color = paper.text)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(16.dp)).watercolorSurface(quoteBg, paper.accent, .06f)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = paper.text)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetBtn("复制", paper, btnBg, Modifier.weight(1f)) { onCopy() }
                SheetBtn(
                    "加入摘抄", paper, paper.accent.copy(alpha = 0.16f), Modifier.weight(1f),
                ) { onAdd() }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SheetBtn(text: String, paper: Paper, bg: Color, modifier: Modifier = Modifier,
    active: Boolean = false, onClick: () -> Unit) {
    com.will.lightreader.ui.components.WatercolorChoice(text, modifier = modifier, active = active,
        paper = bg, ink = paper.accent, contentColor = paper.text, onClick = onClick)
}

/** 面板关闭后把焦点交回入口按钮；动作行挂载前 requestFocus 会失败，逐帧重试。 */
@Composable
private fun PanelFocusReturn(open: Boolean, focus: FocusRequester, onClosed: () -> Unit) {
    var wasOpen by remember { mutableStateOf(false) }
    val latestOnClosed by rememberUpdatedState(onClosed)
    LaunchedEffect(open) {
        val closing = !open && wasOpen
        wasOpen = open
        if (closing) {
            latestOnClosed()
            repeat(3) {
                withFrameNanos { }
                if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }
}

/** A compact reading footer expands into page navigation and three primary actions. */
@Composable
private fun ReaderBottomChrome(
    state: ReaderState,
    paper: Paper,
    expanded: Boolean,
    pageIndex: Int,
    pageCount: Int,
    bookmarked: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onBack: () -> Unit,
    onCatalog: () -> Unit,
    onBookmark: () -> Unit,
    onSearch: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit,
    onSeek: (Int) -> Unit,
    moreFocus: FocusRequester? = null,
    catalogFocus: FocusRequester? = null,
) {
    val reduced = Motion.reducedMotion()
    val chromeBg = lerp(paper.bg, paper.text, .045f)
    val duration = if (reduced) 0 else 180
    val ready = !state.loading && state.content?.chapterUrl == state.chapterUrl
    val pageMode = state.content?.fixedPage != null || state.content?.comicPages?.isNotEmpty() == true
    val displayedTitle = state.content?.title?.takeIf { state.content.chapterUrl != state.chapterUrl }
        ?: state.chapterTitle
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val footerWidth=if(pageMode)660.dp else readerPageMetrics(maxWidth.value,maxHeight.value,
            state.fontSize,LocalDensity.current.fontScale,state.twoPage).contentWidthDp.dp
        AnimatedVisibility(visible = expanded, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(duration)) + slideInVertically(tween(duration)) { if (reduced) 0 else -it / 6 },
            exit = fadeOut(tween(if (reduced) 0 else 120))) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp)).watercolorSurface(chromeBg, paper.accent, .04f),
                verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = "返回", tint = paper.text)
                }
                Text(displayedTitle, Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleSmall, color = paper.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!pageMode) androidx.compose.material3.IconButton(onClick = onSearch, enabled = ready) {
                    Icon(AppIcons.Search, contentDescription = "书内搜索", tint = paper.text)
                }
                androidx.compose.material3.IconButton(onClick = onCollapse) {
                    Icon(AppIcons.Close, contentDescription = "收起工具栏", tint = paper.muted)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = footerWidth).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility).padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)).watercolorSurface(if(expanded)chromeBg else paper.bg, paper.accent, if(expanded).045f else 0f)
            .animateContentSize(tween(duration, easing = Motion.Decelerate))
            .padding(horizontal = 12.dp)) {
            ReaderInlineProgress(key=state.chapterUrl,title=displayedTitle,current=pageIndex,total=pageCount,enabled=ready,
                ink=paper.muted,accent=paper.accent,onTitleClick=onCatalog,onSeek=onSeek)
            AnimatedVisibility(visible = expanded,
                enter = fadeIn(tween(duration)) + slideInVertically(tween(duration)) { if (reduced) 0 else it / 6 },
                exit = fadeOut(tween(if (reduced) 0 else 100))) {
                ReaderActions(paper = paper, pageMode = pageMode, ready = ready,
                    enabledPrev = ready && (!state.atFirst || (pageMode && pageIndex > 0)),
                    enabledNext = ready && (!state.atLast || (pageMode && pageIndex < pageCount - 1)),
                    bookmarked = bookmarked, onCatalog = onCatalog, onBookmark = onBookmark,
                    onPrev = onPrev, onNext = onNext, onMore = onMore,
                    moreFocus = moreFocus, catalogFocus = catalogFocus)
            }
        }
    }
}

@Composable
private fun ReaderActions(
    paper: Paper, pageMode: Boolean, ready: Boolean, enabledPrev: Boolean, enabledNext: Boolean, bookmarked: Boolean,
    onCatalog: () -> Unit, onBookmark: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit,
    onMore: () -> Unit,
    moreFocus: FocusRequester?, catalogFocus: FocusRequester?,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            androidx.compose.material3.TextButton(onClick = onPrev, enabled = enabledPrev) {
                Text(if (pageMode) "上一页" else "上一章", color = if (enabledPrev) paper.accent else paper.muted.copy(alpha = .5f))
            }
            androidx.compose.material3.TextButton(onClick = onNext, enabled = enabledNext) {
                Text(if (pageMode) "下一页" else "下一章", color = if (enabledNext) paper.accent else paper.muted.copy(alpha = .5f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            ReaderAction(AppIcons.Book, "目录", paper, true, onCatalog, focus = catalogFocus)
            ReaderAction(AppIcons.Bookmark, "书签", paper, ready, onBookmark, active = bookmarked)
            ReaderAction(AppIcons.Tune, "设置", paper, true, onMore, focus = moreFocus)
        }
    }
}

@Composable
private fun RowScope.ReaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    paper: Paper,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
    focus: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> paper.text.copy(alpha = 0.32f)
        active -> paper.accent
        else -> paper.text
    }
    Column(
        Modifier.weight(1f).height(56.dp)
            .paperFeedback(interaction, .97f, paper.accent)
            .clip(RoundedCornerShape(14.dp))
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun ReaderError(state: ReaderState, paper: Paper, onRetry: () -> Unit, onWeb: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.will.lightreader.R.drawable.watercolor_read),
            contentDescription = null,
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            state.error?.let { com.will.lightreader.ui.discover.errorHeadline(it) } ?: "出错了",
            color = paper.text, style = MaterialTheme.typography.titleMedium,
        )
        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                com.will.lightreader.ui.discover.errorHint(it),
                color = paper.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        BarText("重试", paper.accent, onClick = onRetry)
        if (state.error is SourceError.ChallengeRequired || state.error is SourceError.AuthRequired) {
            Spacer(Modifier.height(8.dp))
            BarText("打开站点登录 / 验证", paper.accent, onClick = onWeb)
        }
    }
}

@Composable
private fun ChapterBody(
    vm: ReaderViewModel,
    state: ReaderState,
    content: ChapterContent,
    paper: Paper,
    initialBlockIndex: Int,
    pageTurns: SharedFlow<Int>,
    pageSeeks: SharedFlow<Int>,
    onAnchor: (blockIndex: Int, charOffset: Int, ratio: Float) -> Unit,
    onPageInfo: (index: Int, total: Int) -> Unit,
    onContentTap: () -> Unit,
    onLongPressParagraph: (blockIndex: Int, text: String) -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val resolver = LocalFontFamilyResolver.current
    val direction = LocalLayoutDirection.current
    val workerMeasurer = remember(resolver, density, direction) {
        TextMeasurer(resolver, density, direction, cacheSize = 32)
    }
    val readingFamily = if (state.serif) FontFamily.Serif else FontFamily.SansSerif
    val latestContentTap by rememberUpdatedState(onContentTap)
    val latestLongPress by rememberUpdatedState(onLongPressParagraph)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val navigationBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
        val metrics = readerPageMetrics(maxWidth.value, maxHeight.value, state.fontSize, density.fontScale,
            state.twoPage, with(density) { navigationBottom.toDp().value },LocalCompactReaderFooter.current)
        val twoPage = metrics.twoPage
        val pagePadH = with(density) { metrics.sidePaddingDp.dp.toPx() }
        val centerGap = with(density) { metrics.gutterDp.dp.toPx() }
        val padTop = with(density) { metrics.topPaddingDp.dp.toPx() }
        val padBottom = with(density) { metrics.bottomPaddingDp.dp.toPx() }
        val contentW = with(density) { metrics.contentWidthDp.dp.toPx() }.coerceAtMost(constraints.maxWidth.toFloat())
        val pageW = contentW - 2 * pagePadH
        val colW = (if (twoPage) (pageW - centerGap) / 2 else pageW).toInt().coerceAtLeast(1)
        val pageH = (constraints.maxHeight - padTop - padBottom).toInt().coerceAtLeast(1)
        val viewportW = constraints.maxWidth.toFloat()
        // Animate only the real paper area. Side margins remain stationary on a large single page.
        val stageW = contentW

        val paraStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = readingFamily,
            fontSize = state.fontSize.sp,
            lineHeight = (state.fontSize * state.lineHeight).sp,
            letterSpacing = 0.012.em,
            textAlign = TextAlign.Justify,
            lineBreak = LineBreak.Paragraph,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        val vPadDp = (state.fontSize * 0.23f).dp
        val vPadPx = with(density) { vPadDp.toPx().toInt() }

        val overlineStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp, letterSpacing = 0.18.em, fontWeight = FontWeight.Medium,
        )
        val titleStyle = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = readingFamily,
            fontSize = metrics.titleSizeSp.sp,
            lineHeight = (metrics.titleSizeSp * 1.26f).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.01.em,
        )
        val metaStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.02.em)

        val chapterTitle = state.chapters.firstOrNull { it.chapterUrl == content.chapterUrl }?.title
            ?.takeIf { it.isNotBlank() } ?: content.title
        val chapterIdx = state.chapters.indexOfFirst { it.chapterUrl == content.chapterUrl }
        // 开篇眉题用卷名：标题里已有「第 N 章」，再按目录序数标一次容易对不上
        val chapterNo = state.chapters.getOrNull(chapterIdx)?.volume?.takeIf { it.isNotBlank() } ?: "轻阅"
        val wordCount = remember(content) { content.blocks.sumOf { if (it is Block.Paragraph) it.text.length else 0 } }
        val metaText = "本章 ${"%,d".format(wordCount)} 字 · 约 ${max(1, (wordCount + 399) / 400)} 分钟"

        // 章节开篇占首页一块高度（章节号 + 标题 + 字数 + 细线）
        val headHeight = remember(chapterNo, chapterTitle, metaText, state.fontSize, state.serif, colW, density, metrics) {
            val overlineH = textMeasurer.measure(AnnotatedString(chapterNo), overlineStyle,
                constraints = Constraints(maxWidth = colW), maxLines = 1).size.height
            val titleH = textMeasurer.measure(
                AnnotatedString(chapterTitle), titleStyle,
                constraints = Constraints(maxWidth = colW), maxLines = metrics.titleLines,
            ).size.height
            val metaH = textMeasurer.measure(AnnotatedString(metaText), metaStyle,
                constraints = Constraints(maxWidth = colW), maxLines = 1).size.height
            (if(metrics.compactHeading)maxOf(titleH,metaH) else overlineH+titleH+metaH) + with(density) { metrics.headingSpacingDp.dp.toPx().toInt() }
        }

        // rememberSaveable：旋转/进程重建后恢复当前文字锚点，而不是过时的初始读档
        var liveAnchor by rememberSaveable(content.chapterUrl) { mutableStateOf(state.startBlockIndex to state.startCharOffset) }
        var landingAtEnd by rememberSaveable(content.chapterUrl) { mutableStateOf(state.startAtEnd) }
        val layoutKey = remember(content, state.fontSize, state.lineHeight, state.serif, twoPage, colW, pageH, headHeight, density, direction) {
            ReadingLayoutKey(content, colW, pageH, headHeight, state.fontSize, state.lineHeight, state.serif,
                vPadPx, density.density, density.fontScale, direction.toString(), twoPage)
        }
        val flowKey = remember(layoutKey) { Any() }
        val cachedFlow = remember(layoutKey) { readingLayouts.get(layoutKey) }
        val generatedFlow by produceState<Pair<Any, ReadingFlow>?>(cachedFlow?.let { flowKey to it }, flowKey) {
            if (cachedFlow != null) { value = flowKey to cachedFlow; return@produceState }
            val measured = withContext(Dispatchers.Default) { traceReaderSection("Reader.paginate") {
                val units = mutableListOf(FlowUnit(-1, headHeight))
                content.blocks.forEachIndexed { index, block ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    when (block) {
                        is Block.Paragraph -> {
                            val text = "　　" + block.text
                            val result = workerMeasurer.measure(AnnotatedString(text), style = paraStyle,
                                constraints = Constraints(maxWidth = colW.coerceAtLeast(1)))
                            for (line in 0 until result.lineCount) {
                                val top = if (line == 0) vPadPx else 0
                                val bottom = if (line == result.lineCount - 1) vPadPx else 0
                                val height = kotlin.math.ceil(result.getLineBottom(line) - result.getLineTop(line)).toInt()
                                units += FlowUnit(index, height + top + bottom, result.getLineStart(line),
                                    result.getLineEnd(line), true, top, bottom)
                            }
                        }
                        is Block.Image -> units += FlowUnit(index, minOf(pageH, with(density) { 300.dp.toPx().toInt() }))
                        Block.Divider -> units += FlowUnit(index, with(density) { 60.dp.toPx().toInt() })
                    }
                }
                units += FlowUnit(content.blocks.size, with(density) { 132.dp.toPx().toInt() })
                readingFlow(units, pageH)
            } }
            readingLayouts.put(layoutKey, measured)
            value = flowKey to measured
        }
        val flow = generatedFlow?.takeIf { it.first === flowKey }?.second
        if (flow == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = paper.accent, strokeWidth = 1.5.dp)
            }
            return@BoxWithConstraints
        }
        val pages = flow.pages
        val blockHeights = remember(flow) { flow.fragments.map { it.height } }
        val pagesPerSpread = if (twoPage) 2 else 1
        val spreads = ((pages.size + pagesPerSpread - 1) / pagesPerSpread).coerceAtLeast(1)
        var spread by remember(content, twoPage, flowKey) {
            mutableStateOf(if (landingAtEnd) spreads - 1 else
                (flow.pageAt(liveAnchor.first, liveAnchor.second) / pagesPerSpread).coerceIn(0, spreads - 1))
        }
        val safeSpread = spread.coerceIn(0, spreads - 1)
        val inputEnabled = content.chapterUrl == state.chapterUrl && !state.loading
        // ---------- 平移翻页：新旧整屏并排成一条纸带，同速横向滑过 ----------
        val scope = rememberCoroutineScope()
        val reduced = Motion.reducedMotion()
        val slideEase = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
        var turnFrom by remember(content, twoPage, flowKey) { mutableStateOf<Int?>(null) }
        var turnTarget by remember(content, twoPage, flowKey) { mutableStateOf<Int?>(null) }
        var retainedSpread by remember(content, twoPage, flowKey) { mutableStateOf<Int?>(null) }
        var pendingTurn by remember(content, twoPage, flowKey) { mutableIntStateOf(0) }
        var turnDir by remember(content, twoPage, flowKey) { mutableIntStateOf(1) }
        var turnJob by remember(content, twoPage, flowKey) { mutableStateOf<Job?>(null) }
        val slide = remember(content, twoPage, flowKey) { Animatable(0f) }
        val fadeAlpha = remember(content, twoPage, flowKey) { Animatable(1f) }
        var dragging by remember(content, flowKey) { mutableStateOf(false) }
        var fingerProgress by remember(content, flowKey) { mutableStateOf(0f) }
        DisposableEffect(flowKey) { onDispose { turnJob?.cancel() } }
        fun motionProgress(): Float = if (dragging) fingerProgress else slide.value
        LaunchedEffect(inputEnabled) {
            if (!inputEnabled) {
                turnJob?.cancel()
                pendingTurn = 0
                dragging = false
                turnFrom = null
                turnTarget = null
                slide.snapTo(0f)
            }
        }
        // Each new layout starts on the page containing liveAnchor. Its earlier page start
        // must not replace that exact character, or every resize drifts backwards.
        var anchoredSpread by remember(content, twoPage, flowKey) { mutableIntStateOf(safeSpread) }
        LaunchedEffect(safeSpread, flowKey, dragging, turnFrom) {
            if (dragging || turnFrom != null) return@LaunchedEffect
            val visibleFragments = (0 until pagesPerSpread).flatMap { column ->
                pages.getOrNull(safeSpread * pagesPerSpread + column).orEmpty()
            }.map { flow.fragments[it] }
            val fragment = visibleFragments.firstOrNull { it.source in content.blocks.indices }
                ?: visibleFragments.firstOrNull { it.source == content.blocks.size }
            if (fragment != null) {
                liveAnchor = if (landingAtEnd) fragment.source to fragment.from else
                    settledReaderAnchor(anchoredSpread, safeSpread, liveAnchor, fragment.source to fragment.from)
                landingAtEnd = false
                anchoredSpread = safeSpread
                onAnchor(liveAnchor.first, liveAnchor.second, if (spreads <= 1) 0f else safeSpread.toFloat() / (spreads - 1))
            }
        }
        LaunchedEffect(flowKey) { onPageInfo(spread.coerceIn(0, spreads - 1), spreads) }



        fun settleTurn(commit: Boolean, progress: Float = motionProgress()) {
            turnJob?.cancel()
            dragging = false
            turnJob = scope.launch {
                slide.snapTo(progress)
                if (!reduced) slide.animateTo(if (commit) 1f else 0f,
                    tween(pageTurnDuration(progress, commit), easing = slideEase))
                if (commit) {
                    retainedSpread = turnFrom
                    spread = turnTarget ?: spread
                }
                turnFrom = null
                turnTarget = null
                slide.snapTo(0f)
                onPageInfo(spread, spreads)
            }
        }

        fun goToSpread(target: Int) {
            val to = target.coerceIn(0, spreads - 1)
            pendingTurn = 0
            if (to == spread && turnFrom == null) return
            // Repeated seeks to either visible side continue from the current paper position.
            if (turnFrom != null && (to == turnTarget || to == turnFrom)) {
                settleTurn(to == turnTarget)
                return
            }
            turnJob?.cancel()
            turnFrom = spread
            turnTarget = to
            turnDir = if (to > spread) 1 else -1
            settleTurn(true, 0f)
        }

        fun requestTurn(direction: Int) {
            if (!inputEnabled) return
            if (turnFrom != null) {
                if (direction == turnDir) pendingTurn = direction
                else {
                    pendingTurn = 0
                    settleTurn(false)
                }
                return
            }
            val target = spread + direction
            when {
                target in 0 until spreads -> goToSpread(target)
                direction > 0 -> vm.nextChapter()
                else -> vm.prevChapter(fromEnd = true)
            }
        }

        // At most one additional turn is retained while the current paper settles.
        LaunchedEffect(turnFrom, dragging, pendingTurn, inputEnabled) {
            if (inputEnabled && turnFrom == null && !dragging && pendingTurn != 0) {
                val direction = pendingTurn
                pendingTurn = 0
                requestTurn(direction)
            }
        }
        LaunchedEffect(flowKey, spreads, inputEnabled, reduced) {
            if (inputEnabled) pageTurns.collect { requestTurn(it) }
        }
        LaunchedEffect(flowKey, inputEnabled) {
            if (inputEnabled) pageSeeks.collect { goToSpread(it) }
        }

        // 书签 / 摘抄 / 搜索跳转：定位目标块所在摊（只处理当前章，旧章动画体不消费跳转）
        LaunchedEffect(state.jumpTick) {
            if (content.chapterUrl != state.chapterUrl) return@LaunchedEffect
            val target = state.jumpBlock ?: return@LaunchedEffect
            if (state.jumpTick <= 0) return@LaunchedEffect
            val targetSpread = flow.pageAt(target) / pagesPerSpread
            goToSpread(targetSpread.coerceIn(0, spreads - 1))
            vm.consumeJump()
        }

        val hapticPress = rememberHapticTap()
        // 长按位置 → 所在栏的页 → 按测量高度定位段落块
        val paragraphAt: (Offset) -> Pair<Int, String>? = { pos ->
            val column = readerColumnAt(pos.x, viewportW, contentW, pagePadH, centerGap, twoPage)
            // Only a real text column can own a long press.
            // 手势协程持有的闭包会陈旧，这里读实时 spread
            val cur = spread.coerceIn(0, spreads - 1)
            val page = column?.let { pages.getOrNull(cur * pagesPerSpread + it) }
            var acc = padTop
            var found: Pair<Int, String>? = null
            if (page != null) {
                for (i in page) {
                    val h = blockHeights.getOrElse(i) { 0 }.toFloat()
                    if (pos.y >= acc && pos.y < acc + h) {
                        val sourceIndex = flow.fragments[i].source
                        val block = content.blocks.getOrNull(sourceIndex)
                        if (block is Block.Paragraph) found = sourceIndex to block.text
                        break
                    }
                    acc += h
                }
            }
            found
        }

        // ---------- 点击翻页：左区上一页 / 右区下一页 / 中间唤菜单；长按摘抄 ----------
        Box(
            Modifier.fillMaxSize().pointerInput(content, flowKey, spreads, twoPage, blockHeights, inputEnabled) {
                if (!inputEnabled) return@pointerInput
                detectTapGestures(
                    onLongPress = { pos ->
                        paragraphAt(pos)?.let { (idx, text) ->
                            hapticPress()
                            latestLongPress(idx, text)
                        }
                    },
                    onTap = { pos ->
                        // 手势 lambda 里必须读当前 spread 状态（快照值会陈旧，第二次点击翻不动）
                        val localX = pos.x - (viewportW - contentW) / 2f
                        when (val direction = readerTapDirection(localX / contentW)) {
                            0 -> latestContentTap()
                            else -> requestTurn(direction)
                        }
                    },
                )
            }
                .pointerInput(content, flowKey, twoPage, inputEnabled, reduced) {
                    if (!inputEnabled) return@pointerInput
                    val velocity = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var overSlop = 0f
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            overSlop = over
                        } ?: return@awaitEachGesture
                        // Continue from the exact in-flight position when the finger takes over.
                        val progress = motionProgress()
                        val origin = turnFrom ?: spread
                        var total = if (turnFrom != null) -turnDir * progress * stageW else 0f
                        turnJob?.cancel()
                        pendingTurn = 0
                        velocity.resetTracking()
                        fingerProgress = progress
                        dragging = true
                        fun applyDrag(change: PointerInputChange, dx: Float) {
                            total += dx
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val direction = if (total < 0) 1 else -1
                            val target = origin + direction
                            if (target in 0 until spreads) {
                                turnDir = direction
                                turnFrom = origin
                                turnTarget = target
                                fingerProgress = (kotlin.math.abs(total) / stageW).coerceIn(0f, 1f)
                            } else {
                                turnFrom = null
                                turnTarget = null
                                fingerProgress = 0f
                            }
                        }
                        applyDrag(drag, overSlop)
                        var pointerId = drag.id
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || change.changedToUpIgnoreConsumed()) {
                                val next = event.changes.firstOrNull { it.pressed }
                                if (next == null) break
                                pointerId = next.id
                                continue
                            }
                            if (change.isConsumed) { cancelled = true; break }
                            // 悬停/竖向微抖（dx==0）的事件也必须消费：detectHorizontalDragGestures
                            // 会漏掉它们，抽屉的 anchoredDraggable 在 Final pass 看到未消费事件
                            // 就会把手势抢回去，左→右翻页时目录闪一下。
                            event.changes.forEach { it.consume() }
                            val dx = change.positionChangeIgnoreConsumed().x
                            if (dx != 0f) applyDrag(change, dx)
                        }
                        if (cancelled) {
                            if (turnFrom != null) settleTurn(false) else dragging = false
                        } else if (turnFrom != null) {
                            settleTurn(commitPageTurn(total, velocity.calculateVelocity().x, stageW, density.density))
                        } else {
                            dragging = false
                            if (commitPageTurn(total, velocity.calculateVelocity().x, stageW, density.density)) {
                                if (total < 0 && origin == spreads - 1) vm.nextChapter()
                                if (total > 0 && origin == 0) vm.prevChapter(fromEnd = true)
                            }
                        }
                    }
                }
        ) {
            Box(Modifier.width(with(density) { contentW.toDp() }).fillMaxHeight()
                .align(Alignment.TopCenter).clipToBounds()) {
            // 一页的排版抽出来：底层是新页，翻页层是旧页（往回翻时换成目标页）
            val spreadContent: @Composable (Int) -> Unit = { s ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    // 渲染边距必须与测量一致（pagePadH）：否则字宽对不上 → 贴边/内容不满
                    Row(
                        Modifier
                            .width(with(density) { (pageW + 2 * pagePadH).toDp() })
                            .fillMaxHeight()
                            .padding(horizontal = with(density) { pagePadH.toDp() }, vertical = 0.dp)
                            .padding(top = with(density) { padTop.toDp() }, bottom = with(density) { padBottom.toDp() }),
                    ) {
                        for (k in 0 until pagesPerSpread) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                Column(
                                    Modifier.fillMaxSize().clipToBounds(),
                                ) {
                                    pages.getOrNull(s * pagesPerSpread + k)?.forEach { i ->
                                        val fragment = flow.fragments[i]
                                        when {
                                            fragment.source == -1 -> ChapterOpening(chapterNo, chapterTitle, metaText, paper,
                                                overlineStyle, titleStyle, metaStyle, metrics.compactHeading)
                                            fragment.source == content.blocks.size -> ChapterFooter(vm, state, paper, content.chapterUrl)
                                            fragment.text -> {
                                                val sourceText = "　　" + (content.blocks[fragment.source] as Block.Paragraph).text
                                                Text(sourceText.substring(fragment.from, fragment.to), style = paraStyle, color = paper.text,
                                                    modifier = Modifier.fillMaxWidth().padding(
                                                        top = with(density) { fragment.top.toDp() },
                                                        bottom = with(density) { fragment.bottom.toDp() }))
                                            }
                                            else -> Box(Modifier.fillMaxWidth().height(with(density) { fragment.height.toDp() }).clipToBounds()) {
                                                BlockView(content.blocks[fragment.source], paraStyle, paper, vPadDp)
                                            }
                                        }
                                    }
                                }
                            }
                            if (twoPage && k == 0) {
                                Spacer(Modifier.width(with(density) { centerGap.toDp() }))
                            }
                        }
                    }
                }
            }

            // 两层纸：平移时并排同速滑过；覆盖时前翻新页压上来、回翻当前页滑走
            val from = turnFrom
            val cover = state.turnStyle == "cover"
            // Key the actual paper page, so the arriving page retains its Text nodes after
            // settling. Slot order changes must not recreate and measure the whole page.
            // Retain only the already measured outgoing paper. Returning to it can
            // reuse its Text nodes without composing speculative neighbouring pages.
            val visibleSpreads = if (reduced) listOf(safeSpread) else
                listOfNotNull(safeSpread, from, turnTarget, retainedSpread).distinct()
            visibleSpreads.forEach { page ->
                androidx.compose.runtime.key(page) {
                    val old = page == from
                    val visible = page == safeSpread || page == from || page == turnTarget
                    val above = if (cover && turnDir > 0) !old else old
                    Box(Modifier.fillMaxSize()
                        .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
                        .zIndex(if (visible && above) 1f else 0f).graphicsLayer {
                        alpha = if (!visible) 0f else if (reduced) fadeAlpha.value else 1f
                        translationX = when {
                            !visible -> stageW * 2f
                            reduced -> 0f
                            from == null -> 0f
                            old && !cover -> -turnDir * motionProgress() * stageW
                            old && turnDir < 0 -> motionProgress() * stageW
                            old -> 0f
                            cover -> if (turnDir > 0) (1f - motionProgress()) * stageW else 0f
                            else -> turnDir * (1f - motionProgress()) * stageW
                        }
                    }.background(paper.bg)) { spreadContent(page) }
                }
            }
            // 覆盖阴影：贴住移动页左缘，翻页结束随之淡掉
            if (!reduced && cover && from != null) {
                val shadowW = with(density) { 30.dp.toPx() }
                val shadowTint = if (paper.bg.luminance() < 0.4f) Color.Black else Color(0xFF332715)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(30.dp)
                        .zIndex(2f)
                        .graphicsLayer {
                            translationX =
                                (if (turnDir > 0) (1f - motionProgress()) * stageW else motionProgress() * stageW) - shadowW
                            alpha = 1f - motionProgress()
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, shadowTint.copy(alpha = 0.18f))
                            )
                        )
                )
            }
            // 书脊：双页中缝一道柔光（贴纸书装订线）
            if (twoPage) {
                Box(
                    Modifier.fillMaxHeight().width(1.dp).zIndex(3f).align(Alignment.Center).background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, paper.muted.copy(alpha = 0.10f), Color.Transparent)
                        )
                    )
                )
            }
            }
        }
    }
}

/** 章节开篇：章节号 / 标题 / 字数·时长 / 细线。 */
@Composable
private fun ChapterOpening(
    chapterNo: String,
    title: String,
    meta: String,
    paper: Paper,
    overlineStyle: TextStyle,
    titleStyle: TextStyle,
    metaStyle: TextStyle,
    compact: Boolean = false,
) {
    if(compact) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showMeta=maxWidth>=480.dp
            Column {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(title,Modifier.weight(1f),style=titleStyle,color=paper.text,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(showMeta)Text(meta,style=metaStyle,color=paper.muted,maxLines=1)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(40.dp).height(1.dp).watercolorWash(paper.accent,3f))
                Spacer(Modifier.height(12.dp))
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Text(chapterNo, style = overlineStyle, color = paper.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Text(
            title,
            style = titleStyle,
            color = paper.text,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Text(meta, style = metaStyle, color = paper.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(if (compact) 10.dp else 22.dp))
        Box(
            Modifier.width(48.dp).height(1.dp).watercolorWash(paper.accent, 3f),
        )
        Spacer(Modifier.height(if (compact) 14.dp else 28.dp))
    }
}

/** 正文块渲染（单页/双页共用）。 */
@Composable
private fun BlockView(block: Block, paraStyle: TextStyle, paper: Paper, vPad: Dp) {
    when (block) {
        is Block.Paragraph -> Text(
            text = "　　" + block.text,
            style = paraStyle,
            color = paper.text,
            modifier = Modifier.fillMaxWidth().padding(vertical = vPad),
        )
        is Block.Image -> ImageBlock(block, paper)
        Block.Divider -> Text(
            "＊　＊　＊",
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            color = paper.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** 章尾：下一章入口 / 终章提示。 */
@Composable
private fun ChapterFooter(vm: ReaderViewModel, state: ReaderState, paper: Paper, chapterUrl: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.atLast) {
            Text(
                "· 全书完 ·",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.16.em),
                color = paper.muted,
            )
            Spacer(Modifier.height(8.dp))
            Text("已经是最后一章", style = MaterialTheme.typography.bodySmall, color = paper.muted)
        } else {
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .watercolorSurface(paper.bg, paper.accent, .16f)
                    .clickable(enabled = !state.loading && state.chapterUrl == chapterUrl) { vm.nextChapter() }
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "下一章",
                        style = MaterialTheme.typography.titleSmall,
                        color = paper.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        AppIcons.Chevron,
                        contentDescription = null,
                        tint = paper.accent,
                        modifier = Modifier.size(12.dp).rotate(-90f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("本章完", style = MaterialTheme.typography.bodySmall, color = paper.muted)
        }
    }
}

@Composable
private fun BarText(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, com.will.lightreader.ui.theme.M3E.fastSpatialSpec(), label = "bar-btn")
    Text(
        text,
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = color,
        style = MaterialTheme.typography.titleSmall,
    )
}

/** 插图：加载中/失败/占位都有可见的框，不再留白。 */
@Composable
private fun ImageBlock(block: Block.Image, paper: Paper) {
    val shape = RoundedCornerShape(12.dp)
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(
            Modifier.fillMaxWidth()
                .height(240.dp)
                .clip(shape)
                .background(paper.muted.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                block.ref.isBlank() -> Text(
                    "［插图］", color = paper.muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 40.dp),
                )
                failed -> Text(
                    "［插图加载失败］", color = paper.muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 40.dp),
                )
                else -> AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current).data(block.ref)
                        .listener(onError = { _, _ -> failed = true })
                        .build(),
                    contentDescription = block.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (!block.caption.isNullOrBlank()) {
            Text(
                block.caption, Modifier.fillMaxWidth().padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall, color = paper.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
