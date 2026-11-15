package com.will.lightreader.ui.book

import com.will.lightreader.ui.components.watercolorWash
import com.will.lightreader.ui.components.watercolorSurface
import com.will.lightreader.ui.components.watercolorPaper
import com.will.lightreader.ui.components.paperFeedback
import com.will.lightreader.ui.components.WatercolorPanel
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.will.lightreader.R
import com.will.lightreader.di.AppContainer
import com.will.lightreader.domain.BookDetail
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.web.WebSessionActivity
import com.will.lightreader.ui.SystemBarsAppearance
import com.will.lightreader.ui.anim.rememberHapticTap
import com.will.lightreader.ui.components.TonalPanel
import com.will.lightreader.ui.components.pressScale
import com.will.lightreader.ui.discover.errorHeadline
import com.will.lightreader.ui.discover.errorHint
import com.will.lightreader.ui.shelf.Cover
import com.will.lightreader.ui.theme.AppIcons
import com.will.lightreader.ui.theme.M3E

/**
 * 书籍详情（M3E tonal）：
 * - 手机：单列滚动（封面信息 → 简介 → 目录）。
 * - 平板（≥720dp）：双栏 —— 左栏整列封面卡（底部读书猫娘插画），右栏简介 + 目录，各自独立滚动。
 * - 标签可点，跳转发现页的标签结果；作者可点，进入作者其他作品。
 */
@Composable
fun BookScreen(
    container: AppContainer,
    sourceId: String,
    bookUrl: String,
    onBack: () -> Unit,
    onOpenChapter: (bookKey: String, chapterUrl: String) -> Unit,
    onOpenAuthor: (authorName: String, authorUrl: String?) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val vm: BookViewModel = viewModel(
        factory = BookViewModel.factory(container.sources, container.library, container.prefetchImage, container.appScope, sourceId, bookUrl)
    )
    androidx.compose.runtime.DisposableEffect(vm) { onDispose { vm.leavePage() } }
    val download by vm.download.collectAsState()
    var showDlSheet by remember { mutableStateOf(false) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 720

    // 内容进场时钟：开书后略延迟，让封面先飞一会儿，内容再错峰浮出
    val reduced = com.will.lightreader.ui.theme.Motion.reducedMotion()
    var entered by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { if (!reduced) { kotlinx.coroutines.delay(120); entered = true } }
    val risePx = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }

    LaunchedEffect(state.refreshMessage) {
        state.refreshMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.consumeRefreshMessage() }
    }

    // 从阅读页返回时刷新「读到」：VM 还在 back stack 上，状态不会自动更新
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshProgress() }

    // 下载按钮文案 / 进度 / 是否可点
    val dl = download
    LaunchedEffect(dl?.finished,dl?.lastError) {
        if(dl?.finished==true && !dl.cancelled)dl.lastError?.let { Toast.makeText(context,it,Toast.LENGTH_LONG).show() }
    }
    val dlLabel = when {
        dl == null -> "下载"
        !dl.finished -> if(dl.pageTotal>0)"取消下载 ${dl.pageDone}/${dl.pageTotal} 页" else "取消下载 ${dl.done + dl.failed}/${dl.total}"
        dl.cancelled -> "继续下载"
        dl.failed > 0 -> "重试 ${dl.failed} 章"
        else -> "已下载 ✓"
    }
    val dlProgress = dl?.let { p ->
        if (!p.finished && p.total > 0) (p.done + p.failed).toFloat() / p.total else null
    }
    val onDownloadAction: () -> Unit = {
        when {
            dl?.finished == false -> vm.cancelDownload()
            dl?.cancelled == true -> showDlSheet = true
            dl?.finished == true && dl.failed > 0 -> vm.retryFailed()
            state.offline -> Toast.makeText(context,
                "当前离线，联网后可下载", Toast.LENGTH_SHORT).show()
            else -> showDlSheet = true
        }
    }

    CompositionLocalProvider(
        LocalBookEntered provides entered,
        LocalBookRisePx provides (if (reduced) 0f else risePx),
    ) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        SystemBarsAppearance(lightContent = com.will.lightreader.ui.theme.LocalAppDark.current)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            BookTopBar(
                title = state.detail?.title ?: "书籍详情",
                downloadLabel = dlLabel,
                downloadProgress = dlProgress,
                downloadEnabled = true,
                showDownload = dl?.finished==false || (container.sources.find(sourceId)?.supportsBulkDownload == true && state.detail != null && state.chapters.isNotEmpty() &&
                    state.error !is SourceError.Unsupported &&
                    sourceId != com.will.lightreader.source.local.LocalIds.SOURCE),
                onBack = onBack,
                onDownload = onDownloadAction,
            )

            var loadingFeedback by remember { mutableStateOf(false) }
            LaunchedEffect(state.loading, state.detail != null) {
                loadingFeedback = false
                if (state.loading && state.detail != null) {
                    kotlinx.coroutines.delay(160)
                    loadingFeedback = true
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                androidx.compose.animation.AnimatedVisibility(loadingFeedback,
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if (reduced) 0 else 100)),
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (reduced) 0 else 100))) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            when {
                state.error != null && !state.hasCachedDetail -> BookErrorPane(
                    error = state.error!!,
                    onRetry = { vm.reload() },
                    onWeb = {
                        context.startActivity(WebSessionActivity.intent(context, bookUrl, "站点登录 / 验证"))
                    },
                )
                state.loading && state.detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("正在打开这本书…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> androidx.compose.runtime.CompositionLocalProvider(
                    LocalBulkDownload provides (container.sources.find(sourceId)?.supportsBulkDownload == true),
                    LocalOpeningJacket provides vm.openingShelfCover,
                ) {
                    state.error?.let { error ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(errorHeadline(error), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            if (error !is SourceError.Unsupported) TextButton(onClick = { vm.reload() }) { Text("重试目录") }
                            if (error is SourceError.ChallengeRequired || error is SourceError.AuthRequired) {
                                TextButton(onClick = {
                                    context.startActivity(WebSessionActivity.intent(context, bookUrl, "站点登录 / 验证"))
                                }) { Text(if (error is SourceError.ChallengeRequired && error.accessBlocked) "查看站点" else "登录 / 验证") }
                            }
                        }
                    }
                    BookContent(
                    detail = state.detail!!,
                    state = state,
                    wide = wide,
                    onRefresh = { vm.refresh() },
                    onOpenAuthor = { onOpenAuthor(state.detail!!.author, state.detail!!.authorUrl) },
                    onOpenTag = onOpenTag,
                    onContinue = { url -> onOpenChapter(vm.bookKey, url) },
                    onDownloadAll = { vm.downloadAll() },
                    onOpenChapter = { ch ->
                        if (state.offline && ch.chapterUrl !in state.downloadedKeys) {
                            Toast.makeText(context,
                                "这一章还未下载，联网后可阅读", Toast.LENGTH_SHORT).show()
                        } else {
                            onOpenChapter(vm.bookKey, ch.chapterUrl)
                        }
                    },
                    onRemoveDownload = { vm.removeDownload(it) },
                ) }
            }
        }

        if (showDlSheet) {
            VolumeDownloadSheet(
                volumes = state.chapters.map { it.volume.ifBlank { "全书" } }.distinct(),
                onConfirm = { vols -> showDlSheet = false; vm.downloadVolumes(vols) },
                onClose = { showDlSheet = false },
            )
        }
    }
    }
}

// =====================================================================
// 顶栏
// =====================================================================

@Composable
private fun BookTopBar(
    title: String,
    downloadLabel: String,
    downloadProgress: Float?,
    downloadEnabled: Boolean,
    showDownload: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 64.dp).then(bookEnter(0)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            title, Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        if (showDownload) com.will.lightreader.ui.components.WatercolorAction(
            label = downloadLabel,
            icon = when {
                downloadProgress != null -> AppIcons.Close
                downloadLabel.startsWith("已下载") -> AppIcons.Check
                else -> AppIcons.Download
            },
            enabled = downloadEnabled, progress = downloadProgress,
            modifier = Modifier.widthIn(max = 176.dp), onClick = onDownload)

    }
}

// =====================================================================
// 正文
// =====================================================================

@Composable
private fun BookContent(
    detail: BookDetail,
    state: BookState,
    wide: Boolean,
    onRefresh: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenTag: (String) -> Unit,
    onContinue: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onOpenChapter: (ChapterRef) -> Unit,
    onRemoveDownload: (String) -> Unit,
) {
    // 已下载查看：全部 / 仅已下载 筛选
    var onlyDownloaded by remember { mutableStateOf(false) }
    val downloadedKeys = state.downloadedKeys
    val catalogueState = androidx.compose.foundation.lazy.rememberLazyListState()
    val chapterNumbers = remember(state.chapters) { state.chapters.mapIndexed { index, chapter -> chapter.chapterUrl to index + 1 }.toMap() }
    val scope = rememberCoroutineScope()
    val reduced = com.will.lightreader.ui.theme.Motion.reducedMotion()
    val shown = if (onlyDownloaded) state.chapters.filter { it.chapterUrl in downloadedKeys } else state.chapters
    val rows: List<Any> = remember(shown) {
        buildList {
            var lastVolume = ""
            for (ch in shown) {
                if (ch.volume.isNotBlank() && ch.volume != lastVolume) {
                    add(ch.volume)
                    lastVolume = ch.volume
                }
                add(ch)
            }
        }
    }
    val currentRow = remember(rows, state.readChapterUrl) { rows.indexOfFirst { it is ChapterRef && it.chapterUrl == state.readChapterUrl } }
    val locateCurrent: (() -> Unit)? = if (currentRow < 0) null else ({
        val headerItems = if (wide) 1 else 2 + if (detail.intro.isNotBlank()) 1 else 0
        val target = currentRow + headerItems
        scope.launch {
            if (reduced) catalogueState.scrollToItem(target) else catalogueState.animateScrollToItem(target)
        }
    })

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (wide) {
            Row(Modifier.fillMaxSize().widthIn(max = 1280.dp)) {
                // 左栏：整列一张 hero 卡，猫娘插画落在右下角填空
                Box(
                    Modifier.weight(0.8f).fillMaxHeight()
                        .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                ) {
                    BookHeroBlock(
                        detail = detail, state = state, wide = true,
                        onOpenAuthor = onOpenAuthor, onOpenTag = onOpenTag,
                        onContinue = onContinue, onDownloadAll = onDownloadAll,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 右栏：简介 + 目录（下拉刷新）
                Column(Modifier.weight(1.3f).fillMaxHeight()) {
                    val rightHeader: @Composable () -> Unit = {
                        Column {
                            if (detail.intro.isNotBlank()) {
                                IntroCard(detail.intro)
                                Spacer(Modifier.height(12.dp))
                            }
                            CatalogueHeader(
                                count = state.chapters.size,
                                pageBased = detail.tags.any { it == "PDF" || it.contains("CBZ") },
                                downloadedCount = downloadedKeys.size,
                                onlyDownloaded = onlyDownloaded,
                                onAll = { onlyDownloaded = false },
                                onLocate = locateCurrent,
                                onDownloaded = { onlyDownloaded = true },
                            )
                        }
                    }
                    com.will.lightreader.ui.components.GuardedPullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        atTop = { !catalogueState.canScrollBackward },
                        resetKey = onlyDownloaded,
                        modifier = Modifier.fillMaxSize().then(bookEnter(3)),
                    ) {
                        LazyColumn(
                            Modifier.fillMaxSize(), state = catalogueState,
                            contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { rightHeader() }
                            if (onlyDownloaded && shown.isEmpty()) {
                                item { EmptyDownloaded(onDownloadAll = onDownloadAll) }
                            } else {
                                itemsIndexed(
                                    rows,
                                    key = { i, row -> if (row is ChapterRef) row.chapterUrl else "vol:$i:$row" },
                                ) { _, row ->
                                    when (row) {
                                        is String -> VolumeHeader(row)
                                        is ChapterRef -> ChapterRow(
                                            ch = row,
                                            number = chapterNumbers[row.chapterUrl] ?: 0,
                                            current = row.chapterUrl == state.readChapterUrl,
                                            downloaded = row.chapterUrl in downloadedKeys &&
                                                detail.sourceId != com.will.lightreader.source.local.LocalIds.SOURCE,
                                            onRemove = { onRemoveDownload(row.chapterUrl) },
                                            onClick = { onOpenChapter(row) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            com.will.lightreader.ui.components.GuardedPullToRefresh(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                atTop = { !catalogueState.canScrollBackward },
                resetKey = onlyDownloaded,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(), state = catalogueState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        BookHeroBlock(
                            detail = detail, state = state, wide = false,
                            onOpenAuthor = onOpenAuthor, onOpenTag = onOpenTag,
                            onContinue = onContinue, onDownloadAll = onDownloadAll,
                        )
                    }
                    if (detail.intro.isNotBlank()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            IntroCard(detail.intro)
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        CatalogueHeader(
                            count = state.chapters.size,
                            pageBased = detail.tags.any { it == "PDF" || it.contains("CBZ") },
                            downloadedCount = downloadedKeys.size,
                            onlyDownloaded = onlyDownloaded,
                            onAll = { onlyDownloaded = false },
                            onLocate = locateCurrent,
                            onDownloaded = { onlyDownloaded = true },
                        )
                    }
                    if (onlyDownloaded && shown.isEmpty()) {
                        item { EmptyDownloaded(onDownloadAll = onDownloadAll) }
                    } else {
                        itemsIndexed(
                            rows,
                            key = { i, row -> if (row is ChapterRef) row.chapterUrl else "vol:$i:$row" },
                        ) { _, row ->
                            when (row) {
                                is String -> VolumeHeader(row)
                                is ChapterRef -> ChapterRow(
                                    ch = row,
                                    number = chapterNumbers[row.chapterUrl] ?: 0,
                                    current = row.chapterUrl == state.readChapterUrl,
                                    downloaded = row.chapterUrl in downloadedKeys &&
                                        detail.sourceId != com.will.lightreader.source.local.LocalIds.SOURCE,
                                    onRemove = { onRemoveDownload(row.chapterUrl) },
                                    onClick = { onOpenChapter(row) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// 封面信息块
// =====================================================================

private val LocalBulkDownload = androidx.compose.runtime.staticCompositionLocalOf { true }
private val LocalOpeningJacket = androidx.compose.runtime.staticCompositionLocalOf<com.will.lightreader.domain.BookBrief?> { null }
private val LocalBookEntered = androidx.compose.runtime.staticCompositionLocalOf { true }
private val LocalBookRisePx = androidx.compose.runtime.staticCompositionLocalOf { 0f }

/**
 * 详情页内容进场：淡入 + 上移 28dp，按块错峰 70ms，让页面「组装」出来而非硬切。
 * 封面是共享元素独立飞行，绝不套这个 modifier（否则会被位移拖着跑）。
 * 目录几百行只在容器级套一次，不做逐行动画（首帧重布局会挤帧）。
 */
@Composable
private fun bookEnter(index: Int): Modifier {
    val risePx = LocalBookRisePx.current
    if (risePx == 0f) return Modifier
    val entered = LocalBookEntered.current
    val p by androidx.compose.animation.core.animateFloatAsState(
        if (entered) 1f else 0f,
        androidx.compose.animation.core.tween(420, delayMillis = index * 70, easing = com.will.lightreader.ui.theme.Motion.Decelerate),
        label = "book-enter",
    )
    return Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * risePx }
}

@Composable
private fun BookHeroBlock(
    detail: BookDetail,
    state: BookState,
    wide: Boolean,
    onOpenAuthor: () -> Unit,
    onOpenTag: (String) -> Unit,
    onContinue: (String) -> Unit,
    onDownloadAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WatercolorPanel(modifier.fillMaxWidth()) {
        val local = detail.sourceId == com.will.lightreader.source.local.LocalIds.SOURCE
        if (wide) {
            // 平板：整列一张卡，底部读书猫娘插画填掉下方留白
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val showArt = maxHeight >= 620.dp
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        HeroInfo(detail, state, true, local, onOpenAuthor, onOpenTag, onContinue, onDownloadAll)
                    }
                    if (showArt) ReadingCorner(state)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                HeroInfo(detail, state, false, local, onOpenAuthor, onOpenTag, onContinue, onDownloadAll)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroInfo(
    detail: BookDetail,
    state: BookState,
    wide: Boolean,
    local: Boolean,
    onOpenAuthor: () -> Unit,
    onOpenTag: (String) -> Unit,
    onContinue: (String) -> Unit,
    onDownloadAll: () -> Unit,
) {
    val jacket = LocalOpeningJacket.current
    val paper = com.will.lightreader.ui.shelf.rememberShelfTextures()?.atlas
    Row {
        androidx.compose.runtime.CompositionLocalProvider(com.will.lightreader.ui.shelf.LocalBookMaterials provides paper) {
            Cover(
                url = if (jacket == null) detail.coverUrl else jacket.coverUrl,
                title = jacket?.title ?: detail.title,
                modifier = Modifier.width(if (wide) 132.dp else 108.dp).aspectRatio(com.will.lightreader.ui.shelf.JacketAspectRatio),
                radius = 1.dp,
                sharedKey = "cover:${detail.sourceId}:${jacket?.bookUrl ?: detail.bookUrl}",
                jacketKey = "${detail.sourceId}:${jacket?.bookId ?: detail.bookId}",
            )
        }
        Spacer(Modifier.width(if (wide) 18.dp else 14.dp))
        Column(Modifier.weight(1f).then(bookEnter(1))) {
            Text(
                detail.title,
                style = (if (wide) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            if (detail.author.isNotBlank()) {
                if (local) {
                    Text(
                        detail.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier.fillMaxWidth().paperFeedback(interaction, .99f)
                            .clickable(interactionSource = interaction, indication = null,
                                onClickLabel = "查看作者的其他作品") { onOpenAuthor() }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(detail.author, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Icon(AppIcons.Chevron, contentDescription = null,
                            modifier = Modifier.size(16.dp).rotate(-90f), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (detail.status.isNotBlank()) StatusPill(detail.status)
                if (detail.status.isNotBlank() && detail.updatedAt.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                }
                if (detail.updatedAt.isNotBlank()) {
                    Text(
                        "更新 ${detail.updatedAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (detail.latestChapter.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "最新：" + detail.latestChapter,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // 标签：可点 → 发现页标签结果
    if (detail.tags.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        FlowRow(
            modifier = Modifier.then(bookEnter(1)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detail.tags.forEach { tag ->
                ClickableTag(tag) { onOpenTag(tag) }
            }
        }
    }

    // 阅读进度 + 继续阅读
    Spacer(Modifier.height(16.dp))
    ReadProgressBlock(state = state, onContinue = onContinue)

    // 平板左栏补一个整书下载入口（顶栏下载在宽屏下距离远）
    if (wide && !local && state.chapters.isNotEmpty() && LocalBulkDownload.current) {
        Spacer(Modifier.height(12.dp))
        com.will.lightreader.ui.components.WatercolorAction("下载全书", AppIcons.Download,
            modifier = Modifier.then(bookEnter(2)), onClick = onDownloadAll)
    }
}

/** 平板左栏底部：读书猫娘 + 阅读角落，填满封面卡下方的空白。 */
@Composable
private fun ReadingCorner(state: BookState) {
    val started = state.readPercent > 0
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            Modifier.weight(1f).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "阅读进度",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                when {
                    started -> "已读 ${state.readPercent}%"
                    state.readChapter.isNotBlank() -> "已保存上次的阅读位置"
                    else -> "尚未开始阅读"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.readChapter.isNotBlank()) {
                Text(
                    "读到：" + state.readChapter,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        coil.compose.AsyncImage(
            model = R.drawable.watercolor_read,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ReadProgressBlock(state: BookState, onContinue: (String) -> Unit) {    val hasProgress = state.readPercent > 0 || state.readChapter.isNotBlank()
    val continueUrl = state.readChapterUrl.ifBlank { state.chapters.firstOrNull()?.chapterUrl ?: "" }
    Column(Modifier.fillMaxWidth().then(bookEnter(2))) {
        if (hasProgress) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier.fillMaxWidth((state.readPercent / 100f).coerceAtLeast(0.03f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "已读 ${state.readPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.readChapter.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "读到：" + state.readChapter, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (continueUrl.isNotBlank()) {
            com.will.lightreader.ui.components.WatercolorAction(
                if (state.readChapterUrl.isNotBlank()) "继续阅读" else "开始阅读", AppIcons.Book,
                emphasis = true, onClick = { onContinue(continueUrl) })
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val done = status.contains("完结")
    Box(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(
                if (done) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            color = if (done) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 可点标签：tonal 胶囊，按压轻缩放。 */
@Composable
private fun ClickableTag(text: String, onClick: () -> Unit) {
    com.will.lightreader.ui.components.WatercolorChoice(text, onClick = onClick)
}

// =====================================================================
// 简介 / 目录
// =====================================================================

@Composable
private fun IntroCard(intro: String) {
    var expanded by rememberSaveable(intro) { mutableStateOf(false) }
    var canExpand by remember(intro) { mutableStateOf(false) }
    WatercolorPanel(Modifier.fillMaxWidth().then(bookEnter(2))) {
        Column(Modifier.fillMaxWidth().animateContentSize(M3E.spatialSpec()).padding(18.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("简介", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
            if (canExpand) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier
                    .semantics { stateDescription = if (expanded) "简介已展开" else "简介已收起" }) {
                    Text(if (expanded) "收起简介" else "展开简介")
                    val angle = animateFloatAsState(if (expanded) 180f else 0f, M3E.fastSpatialSpec(), label = "intro-chevron")
                    Icon(AppIcons.Chevron, null, Modifier.padding(start = 6.dp).size(16.dp).graphicsLayer { rotationZ = angle.value })
                }
            }
            }
            Spacer(Modifier.height(8.dp))
            Text(intro, maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) canExpand = it.hasVisualOverflow },
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = (MaterialTheme.typography.bodyMedium.fontSize.value * 1.65f).sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)

        }
    }
}

@Composable
private fun CatalogueHeader(
    count: Int,
    pageBased: Boolean = false,
    downloadedCount: Int,
    onlyDownloaded: Boolean,
    onLocate: (() -> Unit)? = null,
    onAll: () -> Unit,
    onDownloaded: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp).then(bookEnter(3))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "目录（$count ${if (pageBased) "页" else "章"}）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            onLocate?.let { TextButton(onClick = it) { Text("定位已读") } }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipPill("全部", active = !onlyDownloaded, onClick = onAll)
            FilterChipPill("已下载 $downloadedCount", active = onlyDownloaded, onClick = onDownloaded)
        }
    }
}

@Composable
private fun VolumeHeader(title: String) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 27.sp),
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        com.will.lightreader.ui.components.WatercolorRule()
    }
}

@Composable
private fun EmptyDownloaded(onDownloadAll: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.watercolor_read),
            contentDescription = null,
            modifier = Modifier.size(160.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "还没有下载章节",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (LocalBulkDownload.current) "(=^･ω･^=) 把整本书叼回家，离线也能读" else "本源暂不支持整书下载；阅读过的公开章节会保存在本机",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (LocalBulkDownload.current) com.will.lightreader.ui.components.WatercolorAction("一键下载全部", AppIcons.Download, onClick = onDownloadAll)
    }
}

// =====================================================================
// 章节行 / 筛选胶囊
// =====================================================================

@Composable
private fun ChapterRow(
    ch: ChapterRef, number: Int, current: Boolean, downloaded: Boolean,
    onRemove: () -> Unit, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberHapticTap()
    var menu by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (current) MaterialTheme.colorScheme.primaryContainer
        else if (pressed) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        M3E.effectsSpec(), label = "chapter-background")
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp)
        .watercolorPaper(background, MaterialTheme.colorScheme.primary, if (current || pressed) .85f else .5f).clickable(interactionSource = interaction, indication = null) { haptic(); onClick() }
        .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ch.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp,
                lineHeight = 26.sp, fontWeight = if (current) FontWeight.Medium else FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(number.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (current) Text("读到这里", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (downloaded) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(AppIcons.Check, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("已下载", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (downloaded) Box {
            IconButton(onClick = { menu = true }) {
                Icon(AppIcons.More, contentDescription = "章节操作：${ch.title}", modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("移除此章下载") }, onClick = { menu = false; onRemove() })
            }
        } else Icon(AppIcons.Chevron, null, Modifier.padding(end = 8.dp).size(15.dp)
            .rotate(-90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilterChipPill(text: String, active: Boolean, onClick: () -> Unit) {
    com.will.lightreader.ui.components.WatercolorChoice(text, active, onClick = onClick)
}

// =====================================================================
// 错误 / 下载面板
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookErrorPane(error: SourceError, onRetry: () -> Unit, onWeb: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val illustrationSize = if (maxHeight < 320.dp) 80.dp else 128.dp
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.TopCenter) {
        TonalPanel(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.watercolor_read),
                    contentDescription = null,
                    modifier = Modifier.size(illustrationSize),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    errorHeadline(error),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    errorHint(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 旧版书源的书重试再多次也没用，只留返回书架
                    if (error !is SourceError.Unsupported) {
                        FilledTonalButton(onClick = onRetry) { Text("重试") }
                    }
                    if (error is SourceError.ChallengeRequired || error is SourceError.AuthRequired) {
                        Button(onClick = onWeb) { Text(if (error is SourceError.ChallengeRequired && error.accessBlocked) "查看站点提示" else "打开站点登录 / 验证") }
                    }
                }
            }
        }
    }
    }
}

/** 按册下载：分卷多选（默认全选）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeDownloadSheet(
    volumes: List<String>,
    onConfirm: (Set<String>) -> Unit,
    onClose: () -> Unit,
) {
    val selected = remember(volumes) { mutableStateOf(volumes.toSet()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val maxHeight = with(androidx.compose.ui.platform.LocalDensity.current) { window.height.toDp() * .9f }
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("选择要下载的册（${volumes.size} 册）", Modifier.watercolorWash(MaterialTheme.colorScheme.primary),
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
            com.will.lightreader.ui.components.WatercolorRule()
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(volumes, key = { _, v -> v }) { _, v ->
                    val interaction = remember { MutableInteractionSource() }
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).paperFeedback(interaction)
                        .watercolorWash(MaterialTheme.colorScheme.primary, if (v in selected.value) .16f else 0f)
                        .toggleable(value = v in selected.value, role = androidx.compose.ui.semantics.Role.Checkbox,
                            interactionSource = interaction, indication = null) {
                            selected.value = if (it) selected.value + v else selected.value - v
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = v in selected.value, onCheckedChange = null)
                        Text(v, Modifier.weight(1f).padding(start = 10.dp), maxLines = 2,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            com.will.lightreader.ui.components.WatercolorAction(
                "下载所选（${selected.value.size} 册）", AppIcons.Download,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                emphasis = true, enabled = selected.value.isNotEmpty(), onClick = { onConfirm(selected.value) })
        }
    }
}
