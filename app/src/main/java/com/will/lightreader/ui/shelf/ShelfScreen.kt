package com.will.lightreader.ui.shelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.will.lightreader.R
import com.will.lightreader.di.AppContainer
import com.will.lightreader.domain.Block
import com.will.lightreader.source.local.LocalIds
import com.will.lightreader.ui.components.BookmarkNotch
import com.will.lightreader.ui.components.watercolorWash
import com.will.lightreader.ui.components.TonalSegmented
import com.will.lightreader.ui.components.paperFeedback
import com.will.lightreader.ui.components.pressScale
import com.will.lightreader.ui.reader.ReaderPrefs
import com.will.lightreader.ui.reader.ShelfLayoutMode
import com.will.lightreader.ui.theme.AppIcons
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import com.will.lightreader.ui.components.watercolorSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bunko library: real jackets, printed metadata, a cached reading margin on large tablets. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ShelfScreen(
    container: AppContainer,
    onFindBook: () -> Unit,
    onOpenBook: (String, String) -> Unit,
    onContinue: (String, String, String) -> Unit,
) {
    val vm: ShelfViewModel = viewModel(factory = ShelfViewModel.factory(container.library, container.localImporter))
    androidx.compose.runtime.DisposableEffect(vm) { onDispose { vm.leavePage() } }
    val books by vm.books.collectAsState()
    val initialShelfLoaded by vm.initialShelfLoaded.collectAsState()
    val category by vm.category.collectAsState()
    val expanded by vm.expandedAuthors.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val importing by vm.importState.collectAsState()
    val importMessage by vm.importMessage.collectAsState()
    val context = LocalContext.current
    val prefs = remember { ReaderPrefs(context) }
    var artwork by remember { mutableStateOf(prefs.shelfArtwork) }
    var wheelEnabled by remember { mutableStateOf(prefs.shelfWheelEnabled) }
    val touch = remember { ShelfTouch() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val authorState = androidx.compose.foundation.lazy.rememberLazyListState()
    val physicalState = androidx.compose.foundation.lazy.rememberLazyListState()
    var physicalFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    var shelfExpanded by rememberSaveable { mutableStateOf(!prefs.shelfStartsClosed) }
    val scope = rememberCoroutineScope()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val emptyState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val shortShelf = width < 600 && configuration.screenHeightDp < 700
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ShelfSort.RECENT) }
    var layout by rememberSaveable { mutableStateOf(prefs.shelfLayoutMode) }
    var sortMenu by remember { mutableStateOf(false) }
    val grid = layout.usesGrid(width)
    LaunchedEffect(Unit) { layout = prefs.shelfLayoutMode }
    val reduced = com.will.lightreader.ui.theme.Motion.reducedMotion()
    val rowPlacement = com.will.lightreader.ui.theme.M3E.spatialSpec<androidx.compose.ui.unit.IntOffset>()
    val rowFade = if (reduced) null else tween<Float>(160)
    val presentationAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    var presentedLayout by remember { mutableStateOf(grid to category) }
    LaunchedEffect(grid, category) {
        val target = grid to category
        if (target == presentedLayout) return@LaunchedEffect
        presentedLayout = target
        if (!reduced) {
            presentationAlpha.snapTo(.78f)
            presentationAlpha.animateTo(1f, tween(180))
        }
    }
    val hPad = if (width >= 600) 32.dp else 24.dp
    val shown = remember(books, query, category, sort) { presentShelf(books, query, category, sort) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(container, lifecycle, books, shown, shelfExpanded) {
        if (books.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            snapshotFlow {
                physicalState.isScrollInProgress || listState.isScrollInProgress ||
                    authorState.isScrollInProgress || gridState.isScrollInProgress
            }.collectLatest { scrolling ->
                if (scrolling) return@collectLatest
                kotlinx.coroutines.delay(900)
                val visible = when {
                    shown.isEmpty() -> emptyList()
                    !shelfExpanded -> physicalState.layoutInfo.visibleItemsInfo.mapNotNull { shown.getOrNull(it.index)?.key }
                    category == ShelfCategory.AUTHOR -> authorState.layoutInfo.visibleItemsInfo.mapNotNull {
                        (it.key as? String)?.takeIf { key -> key.startsWith("author-book:") }?.removePrefix("author-book:")
                    }
                    grid -> gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
                    else -> listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
                }
                try {
                    container.shelfCache.warm((visible + books.map { it.key }).distinct())
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // An opportunistic cache failure does not interrupt the shelf.
                }
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = shelfExpanded && books.isNotEmpty()) {
        query = ""
        shelfExpanded = false
    }
    val openShelfBook: (ShelfBook) -> Unit = { book ->
        com.will.lightreader.ui.book.BookOpening.prime(com.will.lightreader.domain.BookBrief(
            book.sourceId, book.key.substringAfter(':'), book.bookUrl, book.title, book.author, book.coverUrl), fromShelf = true)
        onOpenBook(book.sourceId, book.bookUrl)
    }
    val continueBook = latestReadingBook(books)
    val downloadedCount = books.count { it.downloadedCount > 0 }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { vm.importBooks(it) }
    val pickFiles = { picker.launch(arrayOf("*/*")) }
    val pendingImport by com.will.lightreader.ui.ImportRequests.pending.collectAsState()
    LaunchedEffect(pendingImport) {
        pendingImport?.let { uri ->
            com.will.lightreader.ui.ImportRequests.pending.value = null
            vm.importBooks(listOf(uri))
        }
    }
    LaunchedEffect(importMessage) {
        importMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            vm.consumeImportMessage()
        }
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(vm) {
        artwork = prefs.shelfArtwork
        wheelEnabled = prefs.shelfWheelEnabled
        layout = prefs.shelfLayoutMode
        vm.onResume()
        onPauseOrDispose { }
    }
    val shelfTextures = if (books.isNotEmpty()) rememberShelfTextures() else null
    CompositionLocalProvider(LocalBookMaterials provides shelfTextures?.atlas) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(start = hPad, end = hPad,
                top = if (shortShelf) 6.dp else 12.dp, bottom = if (shortShelf) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("藏书室", modifier = Modifier.watercolorWash(MaterialTheme.colorScheme.primary),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif, letterSpacing = .9.sp))
                    Text("${books.size} 本", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
                if (width >= 360 && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.4f)
                    com.will.lightreader.ui.components.WatercolorCompanion(waving = true,
                        modifier = Modifier.padding(end = 8.dp).size(if (shortShelf) 36.dp else 42.dp))
                com.will.lightreader.ui.components.WatercolorAction(
                    label = if (importing.running) "正在导入" else if (width < 400) "导入" else "导入书籍",
                    icon = AppIcons.ImportBook, enabled = !importing.running, busy = importing.running,
                    description = "导入本地书籍", modifier = Modifier.widthIn(max = if (width < 400) 128.dp else 156.dp),
                    onClick = pickFiles)

            }
            if (importing.running) {
                Column(Modifier.padding(horizontal = hPad, vertical = 8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(importing.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (continueBook != null && query.isBlank() && artwork != ShelfArtwork.HIDDEN) ReadingRoomCard(continueBook, width >= 600,
                Modifier.padding(start = hPad, end = hPad, bottom = if (shortShelf) 4.dp else 12.dp),
                compact = true, artwork = artwork, dense = shortShelf) {
                continueBook.continueUrl?.let { onContinue(continueBook.sourceId, continueBook.key, it) }
            }
            if (books.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = hPad), verticalAlignment = Alignment.CenterVertically) {
                    TonalSegmented(labels = listOf("全部", "按作者", "已下载"), selectedIndex = category.ordinal,
                        onSelect = { vm.setCategory(ShelfCategory.entries[it]); if (it == 1) shelfExpanded = true },
                        modifier = if (width >= 600) Modifier.weight(1f, fill = false).widthIn(max = 440.dp) else Modifier.weight(1f),
                        badge = { if (it == 2 && downloadedCount > 0) "$downloadedCount" else null })
                    Spacer(Modifier.width(10.dp))
                    if (width >= 600) Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(AppIcons.Tune, contentDescription = "排序：${sort.label}", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            ShelfSort.entries.forEach { candidate ->
                                DropdownMenuItem(text = { Text(candidate.label) },
                                    leadingIcon = { if (sort == candidate) Icon(AppIcons.Check, contentDescription = null) },
                                    onClick = { sort = candidate; sortMenu = false })
                            }
                        }
                    }
                    IconButton(onClick = {
                        layout = if (grid) ShelfLayoutMode.LIST else ShelfLayoutMode.GRID
                        prefs.shelfLayoutMode = layout
                        shelfExpanded = true
                    }) { Icon(if (grid) AppIcons.List else AppIcons.Grid,
                        contentDescription = if (grid) "切换列表视图" else "切换封面视图", modifier = Modifier.size(18.dp)) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = hPad, vertical = if (shortShelf) 2.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it; if (it.isNotBlank()) shelfExpanded = true }, singleLine = true,
                        modifier = Modifier.weight(1f).watercolorWash(MaterialTheme.colorScheme.primary, .24f),
                        shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent), textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = { Text("搜索书名或作者", style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(17.dp)) },
                        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) {
                            Icon(AppIcons.Close, contentDescription = "清空搜索", modifier = Modifier.size(17.dp))
                        } }) else null, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = {
                        if (shelfExpanded) {
                            query = ""
                            if (category == ShelfCategory.AUTHOR) vm.setCategory(ShelfCategory.TIME)
                        }
                        shelfExpanded = !shelfExpanded
                    }) {
                        Icon(if (shelfExpanded) AppIcons.Book else if (grid) AppIcons.Grid else AppIcons.List,
                            null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (shelfExpanded) "收回书架" else "展开书架", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            CompositionLocalProvider(LocalShelfTouch provides touch) {
            com.will.lightreader.ui.components.GuardedPullToRefresh(isRefreshing = refreshing,
                onRefresh = { vm.refresh(userInitiated = true) }, blocked = importing.running || !shelfExpanded,
                resetKey = Triple(layout, category, query),
                atTop = { when {
                    books.isEmpty() || shown.isEmpty() -> !emptyState.canScrollBackward
                    !shelfExpanded -> !physicalState.canScrollBackward
                    grid && category != ShelfCategory.AUTHOR -> !gridState.canScrollBackward
                    category == ShelfCategory.AUTHOR -> !authorState.canScrollBackward
                    else -> !listState.canScrollBackward
                } }, modifier = Modifier.weight(1f).trackShelfTouch(touch)
                .then(Modifier.graphicsLayer { alpha = presentationAlpha.value })) {
                ShelfUnfold(shelfExpanded) { unfolded ->
                when {
                    !unfolded && books.isNotEmpty() && shown.isNotEmpty() -> SolidBookshelf(shown, physicalState,
                        shelfTextures?.wash, physicalFocusKey, { physicalFocusKey = it }, openShelfBook)
                    books.isEmpty() -> if (initialShelfLoaded) EmptyLibrary(onFindBook, pickFiles, emptyState)
                    shown.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(emptyState)
                        .padding(horizontal = hPad, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(R.drawable.watercolor_read, null, Modifier.size(96.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(if (query.isNotBlank()) "没有找到相关书籍" else "还没有离线内容", style = MaterialTheme.typography.titleMedium)
                        Text(if (query.isNotBlank()) "试试其他书名或作者" else "在书籍详情页下载后，就能离线阅读。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        val groups = remember(shown, category) { if (category == ShelfCategory.AUTHOR) groupByAuthor(shown) else emptyList() }
                        val authorRows = remember(groups, expanded) { authorShelfRows(groups, expanded) }
                        val open = openShelfBook
                        val delete: (ShelfBook) -> Unit = { vm.removeBook(it.key) }
                        val clear: (ShelfBook) -> Unit = { vm.clearDownloads(it.key) }
                        if (grid && category != ShelfCategory.AUTHOR) {
                            LazyVerticalGrid(columns = GridCells.Adaptive(if (width >= 600) 154.dp else 132.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (width >= 600) 28.dp else 24.dp),
                                verticalArrangement = Arrangement.spacedBy(26.dp)) {
                                items(shown, key = { it.key }) { b ->
                                    BookMenuCard(b, true, { open(b) }, { delete(b) }, { clear(b) }, Modifier.animateItem(fadeInSpec = rowFade, placementSpec = rowPlacement, fadeOutSpec = rowFade))
                                }
                            }
                        } else {
                            val activeList = if (category == ShelfCategory.AUTHOR) authorState else listState
                            LazyColumn(Modifier.fillMaxSize(), state = activeList, contentPadding = PaddingValues(start = hPad, end = hPad, top = 8.dp, bottom = 28.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (category == ShelfCategory.AUTHOR) {
                                    groups.forEach { group ->
                                        stickyHeader(key = "author:${group.author}") {
                                            AuthorHeader(group, group.author in expanded, {
                                                scope.launch {
                                                    val header = authorRows.indexOfFirst { it is AuthorShelfRow.Header && it.group.author == group.author }
                                                    // A collapsed group keeps its own header visible even after a long scroll.
                                                    if (group.author in expanded && header >= 0 && authorState.firstVisibleItemIndex > header)
                                                        authorState.scrollToItem(header)
                                                    vm.toggleAuthor(group.author)
                                                }
                                            }, Modifier.fillMaxWidth().animateItem(fadeInSpec = null, placementSpec = rowPlacement, fadeOutSpec = null))
                                        }
                                        if (group.author in expanded) items(group.books, key = { "author-book:${it.key}" }, contentType = { "book" }) { b ->
                                            BookMenuCard(b, false, { open(b) }, { delete(b) }, { clear(b) },
                                                Modifier.animateItem(fadeInSpec = rowFade, placementSpec = rowPlacement, fadeOutSpec = rowFade).shelfWheel(authorState, "author-book:${b.key}", wheelEnabled))
                                        }
                                    }
                                } else {
                                    items(shown, key = { it.key }) { b ->
                                        BookMenuCard(b, false, { open(b) }, { delete(b) }, { clear(b) }, Modifier.animateItem(fadeInSpec = rowFade, placementSpec = rowPlacement, fadeOutSpec = rowFade).shelfWheel(listState, b.key, wheelEnabled))
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        }
    }
    }
}

@Composable
private fun EmptyLibrary(onFind: () -> Unit, onImport: () -> Unit, scroll: androidx.compose.foundation.ScrollState) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AsyncImage(R.drawable.watercolor_read, null, Modifier.size(128.dp))
        Spacer(Modifier.height(20.dp))
        Text("书架还空着", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif))
        Spacer(Modifier.height(8.dp))
        Text("找一本轻小说，或带一本自己的书来。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        com.will.lightreader.ui.components.WatercolorAction("去发现找书", AppIcons.Compass,
            emphasis = true, onClick = onFind)
        Spacer(Modifier.height(10.dp))
        com.will.lightreader.ui.components.WatercolorAction("导入本地书籍", AppIcons.ImportBook, onClick = onImport)
        Text("PDF · 漫画 · EPUB · TXT", modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookMenuCard(book: ShelfBook, grid: Boolean, onOpen: () -> Unit, onDelete: () -> Unit,
    onClearDownloads: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Box(modifier) {
        Box(Modifier.fillMaxWidth().then(if (grid) Modifier else Modifier.paperFeedback(interaction, .985f)).combinedClickable(interactionSource = interaction,
            indication = null, onClick = onOpen, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })) {
            if (grid) BookTile(book) else BookRow(book)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("查看详情") }, onClick = { menu = false; onOpen() })
            if (book.downloadedCount > 0 && book.sourceId != LocalIds.SOURCE) {
                DropdownMenuItem(text = { Text("移除下载缓存") }, onClick = { menu = false; onClearDownloads() })
            }
            DropdownMenuItem(text = { Text("从书架删除", color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; confirm = true })
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("删除这本书？") },
        text = { Text("《${book.title}》的阅读进度与离线内容也会删除。") },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("保留") } })
}

@Composable
private fun AuthorHeader(group: AuthorGroup, expanded: Boolean, toggle: () -> Unit, modifier: Modifier) {
    val angle by androidx.compose.animation.core.animateFloatAsState(if (expanded) 0f else -90f,
        com.will.lightreader.ui.theme.M3E.spatialSpec(), label = "author-chevron")
    val interaction = remember { MutableInteractionSource() }
    val haptic = com.will.lightreader.ui.anim.rememberHapticTap()
    Row(modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp))
        .watercolorSurface(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, if (expanded) .10f else .045f)
        .semantics { contentDescription = "作者：${group.author}"; stateDescription = if (expanded) "已展开，共${group.count}本" else "已收起，共${group.count}本" }
        .clickable(interactionSource = interaction, indication = null, onClickLabel = if (expanded) "收起书籍" else "展开书籍") { haptic(); toggle() }
        .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(group.representative.coverUrl, group.representative.title, Modifier.size(38.dp, 54.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(group.author, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(if (expanded) "已展开 ${group.count} 本作品" else group.representative.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(if (expanded) "收起" else "${group.count} 本 · 展开", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Icon(AppIcons.Chevron, null, Modifier.size(16.dp).rotate(angle), tint = MaterialTheme.colorScheme.primary)
    }
}
