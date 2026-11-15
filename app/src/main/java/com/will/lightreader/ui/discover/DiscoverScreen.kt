package com.will.lightreader.ui.discover


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import com.will.lightreader.ui.theme.AppIcons
import com.will.lightreader.ui.theme.Motion
import com.will.lightreader.ui.components.watercolorPaper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import com.will.lightreader.ui.components.GuardedPullToRefresh
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.will.lightreader.ui.components.watercolorWash
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.will.lightreader.R
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.domain.TagGroup
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.web.WebSessionActivity
import com.will.lightreader.ui.components.paperFeedback
import com.will.lightreader.ui.components.WatercolorPanel
import com.will.lightreader.ui.components.TonalPanel
import com.will.lightreader.ui.components.TonalSegmented
import com.will.lightreader.ui.components.pressScale
import com.will.lightreader.ui.shelf.Cover
import com.will.lightreader.ui.theme.M3E

/**
 * 发现（M3E tonal）：关键词搜索（书名 / 作者）+ 三个入口：
 * 榜单 / 标签云（Tags 分组）/ 完结全本（20 本一页，滚动续页）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(vm: DiscoverViewModel, onOpenBook: (BookBrief) -> Unit) {
    androidx.compose.runtime.DisposableEffect(vm) { onDispose { vm.leavePage() } }
    val state by vm.state.collectAsState()
    val sourceRevision by vm.sourceRevision.collectAsState()
    LaunchedEffect(sourceRevision) { vm.sourcesChanged() }
    val context = LocalContext.current
    LaunchedEffect(state.refreshMessage) {
        state.refreshMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.consumeRefreshMessage()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { vm.ensureLoaded() }
    var query by rememberSaveable { mutableStateOf(state.query) }
    var syncedQuery by rememberSaveable { mutableStateOf(state.query) }
    var syncedSource by rememberSaveable { mutableStateOf(state.sourceId) }
    var sourceMenu by remember { mutableStateOf(false) }
    val paneStates = rememberSaveableStateHolder()
    // Keep an unsubmitted draft across home tabs; only a new committed query/source replaces it.
    LaunchedEffect(state.query, state.sourceId) {
        if (syncedQuery != state.query || syncedSource != state.sourceId) query = state.query
        syncedQuery = state.query
        syncedSource = state.sourceId
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 980.dp).fillMaxWidth().fillMaxHeight()) {
            val sources = vm.availableSources()
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("\u53d1\u73b0", Modifier.weight(1f).watercolorWash(MaterialTheme.colorScheme.primary, .18f),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif))
                val sourceInk=MaterialTheme.colorScheme.primary
                val sourceInteraction=remember { MutableInteractionSource() }
                val sourceAngle by animateFloatAsState(if(sourceMenu)180f else 0f,
                    tween(if(Motion.reducedMotion())0 else 160),label="source-chevron")
                Box {
                    Row(Modifier.widthIn(max=190.dp).heightIn(min=48.dp)
                        .paperFeedback(sourceInteraction,.985f,sourceInk)
                        .watercolorPaper(MaterialTheme.colorScheme.surfaceContainerHigh,sourceInk,.5f)
                        .clickable(enabled=sources.size>1,interactionSource=sourceInteraction,indication=null,
                            role=androidx.compose.ui.semantics.Role.Button) { sourceMenu=true }
                        .semantics {
                            contentDescription=if(sources.size>1)"切换内容来源" else "当前内容来源"
                            stateDescription=if(sourceMenu)"已展开" else "已收起"
                        }.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(AppIcons.Sources,null,Modifier.size(19.dp),tint=sourceInk.copy(alpha=.75f))
                        Text(sources.firstOrNull { it.first==state.sourceId }?.second ?: "内容来源",
                            Modifier.weight(1f,fill=false),maxLines=1,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.labelLarge,color=sourceInk)
                        if(sources.size>1)Icon(AppIcons.Chevron,null,Modifier.size(18.dp).rotate(sourceAngle),tint=sourceInk)
                    }
                    DropdownMenu(expanded=sourceMenu,onDismissRequest={sourceMenu=false},modifier=Modifier.widthIn(min=220.dp,max=320.dp)) {
                        sources.forEach { (id,name) ->
                            val active=id==state.sourceId
                            DropdownMenuItem(text={ Text(name,maxLines=2,overflow=TextOverflow.Ellipsis) },
                                leadingIcon={ Icon(AppIcons.Sources,null,tint=if(active)sourceInk else MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingIcon=if(active)({ Icon(AppIcons.Check,"当前来源",tint=sourceInk) }) else null,
                                modifier=Modifier.heightIn(min=52.dp).semantics { selected=active }
                                    .then(if(active)Modifier.watercolorWash(sourceInk,.12f) else Modifier),onClick={
                                    sourceMenu=false
                                    if(id!=state.sourceId) { keyboard?.hide();query="";vm.switchSource(id) }
                                })
                        }
                    }
                }
            }
            // ---------- 搜索（书名 / 作者 切换） ----------
            SearchBar(
                query = query,
                onQueryChange = { query = it.take(256) },
                scope = state.scope,
                onToggleScope = {
                    vm.setScope(if (state.scope == SearchScope.TITLE) SearchScope.AUTHOR else SearchScope.TITLE)
                },
                onSearch = { keyboard?.hide(); vm.search(query) },
                onClear = { query = ""; vm.clearSearch() },
            )

            // ---------- 榜单 / 标签 / 完结 ----------
            val tabs=vm.availableTabs()
            androidx.activity.compose.BackHandler(enabled = state.selectedTag != null) { vm.closeTag() }
            TonalSegmented(
                labels = tabs.map { vm.tabLabel(it) },
                selectedIndex = tabs.indexOf(state.tab).coerceAtLeast(0),
                onSelect = { vm.selectTab(tabs[it]) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            )

            paneStates.SaveableStateProvider("${state.sourceId}:${state.tab.name}") {
                val resultState = rememberLazyListState()
                val cloudState = rememberLazyListState()
                val listState = if (state.tab == DiscoverTab.TAGS && state.selectedTag == null) cloudState else resultState
                val contentIdentity = listOf(state.query, state.scope.name, state.kind,
                    state.tagRoute.orEmpty(), state.tagFilter.name).joinToString("\u001f")
                var previousIdentity by rememberSaveable { mutableStateOf(contentIdentity) }
                LaunchedEffect(contentIdentity) {
                    if (previousIdentity != contentIdentity) resultState.scrollToItem(0)
                    previousIdentity = contentIdentity
                }
                GuardedPullToRefresh(
                    isRefreshing = state.refreshing, onRefresh = vm::refresh,
                    atTop = { !listState.canScrollBackward || (state.items.isEmpty() && state.sections.isEmpty() && state.tags.isEmpty()) },
                    blocked = state.loading || state.tagsLoading || state.error != null || state.tagsError != null,
                    modifier = Modifier.fillMaxSize(),
                ) {
                when (state.tab) {
                    DiscoverTab.HOME -> ComicHomePane(
                        state, listState, onOpenBook, onMore = { title, route -> vm.openTag(title, route, from = DiscoverTab.HOME) },
                        onRetry = vm::retry,
                    )
                    DiscoverTab.RANKING -> RankingPane(
                        state = state,
                        listState = listState,
                        kinds = vm.rankingKinds(),
                        query = query,
                        onKind = { vm.switchKind(it) },
                        onClearSearch = { query = ""; vm.clearSearch() },
                        onOpenBook = onOpenBook,
                        onRetry = { vm.retry() },
                        onWeb = { openSite(context, state.error) },
                        onLoadMore = { vm.loadMore() },
                    )

                    DiscoverTab.TAGS -> TagsPane(
                        state = state,
                        listState = listState,
                        onPickTag = { label, route -> vm.openTag(label, route) },
                        allowCompleted = DiscoverTab.COMPLETED in tabs,
                        onCloseTag = { vm.closeTag() },
                        onFilter = { vm.setTagFilter(it) },
                        onOpenBook = onOpenBook,
                        onRetry = { vm.retry() },
                        onWeb = { openSite(context, state.error ?: state.tagsError) },
                        onLoadMore = { vm.loadMore() },
                    )

                    DiscoverTab.COMPLETED -> CompletedPane(
                        state = state,
                        listState = listState,
                        onOpenBook = onOpenBook,
                        onRetry = { vm.retry() },
                        onWeb = { openSite(context, state.error) },
                        onLoadMore = { vm.loadMore() },
                    )
                }
                }
            }
        }
    }
}

// =====================================================================
// 搜索栏
// =====================================================================

@Composable
private fun ComicHomePane(state: DiscoverState, listState: LazyListState, onOpenBook: (BookBrief) -> Unit,
    onMore: (String, String) -> Unit, onRetry: () -> Unit) {
    when {
        state.error != null -> ErrorPane(state.error, onRetry, null)
        state.loading -> LoadingPane()
        state.sections.isEmpty() -> EmptyPane(R.drawable.watercolor_read, "暂时没有漫画推荐", "下拉刷新试试", null, null)
        else -> LazyColumn(state = listState, contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            items(state.sections) { section ->
                val heading=comicSectionHeading(section.title)
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(heading,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium.copy(
                            fontFamily=FontFamily.Serif,fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.Medium),
                            maxLines=2,overflow=TextOverflow.Ellipsis)
                        section.route?.let { route -> TextButton(onClick={ onMore(heading,route) },contentPadding=PaddingValues(horizontal=4.dp)) {
                            Text("更多",style=MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(4.dp))
                            Icon(AppIcons.Chevron,null,Modifier.size(16.dp).rotate(-90f))
                        } }
                    }
                    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.items, key = { it.bookId }) { book ->
                            Column(Modifier.width(104.dp).clickable { onOpenBook(book) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Cover(book.coverUrl, book.title, Modifier.fillMaxWidth().aspectRatio(.69f), radius = 9.dp)
                                Text(book.title,style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp,lineHeight=21.sp),minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    scope: SearchScope,
    onToggleScope: () -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .7f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Search, contentDescription = "搜索",
                modifier = Modifier.size(48.dp).clickable(onClickLabel = "搜索", onClick = onSearch).padding(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
                if (query.isEmpty()) {
                    Text(
                        "搜书名 / 作者…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 范围切换：点一下在 书名 / 作者 之间换
            Box(
                Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleScope,
                    )
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    scope.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    AppIcons.Close, contentDescription = "清除",
                    modifier = Modifier.size(48.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClear,
                    ).padding(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

// =====================================================================
// 榜单
// =====================================================================

@Composable
private fun RankingPane(
    state: DiscoverState,
    listState: LazyListState,
    kinds: List<Pair<String,String>>,
    query: String,
    onKind: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenBook: (BookBrief) -> Unit,
    onRetry: () -> Unit,
    onWeb: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 6.dp)) {
        if (state.query.isNotBlank()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "“${state.query}” 的搜索结果", maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TonalChip("清除搜索", active = true, onClick = onClearSearch)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                kinds.forEach { (kind, label) ->
                    TonalChip(label, active = state.kind == kind) { onKind(kind) }
                }
            }
        }

        when {
            state.error != null -> ErrorPane(
                error = state.error, onRetry = onRetry,
                onWeb = if (state.error is SourceError.ChallengeRequired || state.error is SourceError.AuthRequired) onWeb else null,
            )

            state.loading -> LoadingPane()

            state.items.isEmpty() -> EmptyPane(
                imageRes = R.drawable.watercolor_read,
                title = when {
                    state.query.isNotBlank() -> "没有找到相关书籍"
                    kinds.isNotEmpty() -> "本榜单暂无作品"
                    else -> "搜索漫画"
                },
                hint = when {
                    state.query.isNotBlank() -> "换个书名或作者试试"
                    kinds.isNotEmpty() -> "可以切换榜单或下拉刷新"
                    else -> "输入书名或作者开始搜索"
                },
                actionLabel = if (state.query.isNotBlank()) "返回浏览" else null,
                onAction = if (state.query.isNotBlank()) onClearSearch else null,
            )

            else -> BookList(
                items = state.items,
                listState = listState,
                hasMore = state.hasMore,
                loadingMore = state.loadingMore,
                onOpenBook = onOpenBook,
                onLoadMore = onLoadMore,
            )
        }
    }
}

// =====================================================================
// 标签云 / 标签结果
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsPane(
    state: DiscoverState,
    listState: LazyListState,
    onPickTag: (String, String?) -> Unit,
    allowCompleted: Boolean,
    onCloseTag: () -> Unit,
    onFilter: (TagFilter) -> Unit,
    onOpenBook: (BookBrief) -> Unit,
    onRetry: () -> Unit,
    onWeb: () -> Unit,
    onLoadMore: () -> Unit,
) {
    var tagQuery by rememberSaveable { mutableStateOf("") }
    var previousFilter by rememberSaveable { mutableStateOf(tagQuery) }
    LaunchedEffect(tagQuery) {
        if (previousFilter != tagQuery && state.selectedTag == null) listState.scrollToItem(0)
        previousFilter = tagQuery
    }
    val q = tagQuery.trim()
    val groups = remember(state.tags, q) {
        if (q.isEmpty()) state.tags else state.tags.mapNotNull { g ->
            val hits = g.tags.filter { it.contains(q, ignoreCase = true) }
            if (hits.isEmpty()) null else g.copy(tags = hits)
        }
    }
    when {
        state.selectedTag != null -> Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TonalChip("‹ ${state.tagReturnTab.label}", active = false, onClick = onCloseTag)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "「${state.selectedTag}」", maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagFilter.entries.filter { allowCompleted || it != TagFilter.COMPLETED }.forEach { f ->
                        TonalChip(f.label, active = state.tagFilter == f) { onFilter(f) }
                    }
                }
            }
            when {
                state.error != null -> ErrorPane(
                    error = state.error, onRetry = onRetry,
                    onWeb = if (state.error is SourceError.ChallengeRequired || state.error is SourceError.AuthRequired) onWeb else null,
                )

                state.loading -> LoadingPane()

                state.items.isEmpty() -> EmptyPane(
                    imageRes = R.drawable.watercolor_read,
                    title = "这个标签下暂时没有书",
                    hint = if (state.tagFilter == TagFilter.COMPLETED) "换一个标签，或者切回「按更新」试试"
                    else "换一个标签试试",
                    actionLabel = "看看别的标签",
                    onAction = onCloseTag,
                )

                else -> BookList(
                    items = state.items,
                    listState = listState,
                    hasMore = state.hasMore,
                    loadingMore = state.loadingMore,
                    onOpenBook = onOpenBook,
                    onLoadMore = onLoadMore,
                )
            }
        }

        state.tagsLoading && state.tags.isEmpty() -> LoadingPane()

        state.tagsError != null && state.tags.isEmpty() -> ErrorPane(
            error = state.tagsError, onRetry = onRetry,
            onWeb = if (state.tagsError is SourceError.ChallengeRequired || state.tagsError is SourceError.AuthRequired) onWeb else null,
        )

        else -> LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "tags-filter") {
                TagFilterField(tagQuery) { tagQuery = it }
            }
            if (groups.isEmpty()) {
                item(key = "tags-empty") {
                    Text(
                        "没有匹配「$q」的标签，换个词试试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                if (q.isEmpty()) {
                    item(key = "tags-hint") {
                        Text(
                            "站点把作品按属性打了标签：选一个，看看同类的书。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(groups, key = { it.name }) { group ->
                    TagGroupBlock(group, onPickTag)
                }
            }
        }
    }
}

/** 标签云本地筛选：按子串过滤全部分组的标签，不发请求。 */
@Composable
private fun TagFilterField(value: String, onValueChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Search, contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
                if (value.isEmpty()) {
                    Text(
                        "筛选标签（如：校园）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (value.isNotEmpty()) {
                Icon(
                    AppIcons.Close, contentDescription = "清除标签筛选",
                    modifier = Modifier.size(48.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onValueChange("") },
                    ).padding(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroupBlock(group: TagGroup, onPickTag: (String, String?) -> Unit) {
    Column {
        Text(
            group.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            group.tags.forEach { tag ->
                TonalChip(tag, active = false) { onPickTag(tag, group.routes[tag]) }
            }
        }
    }
}

// =====================================================================
// 完结全本
// =====================================================================

@Composable
private fun CompletedPane(
    state: DiscoverState,
    listState: LazyListState,
    onOpenBook: (BookBrief) -> Unit,
    onRetry: () -> Unit,
    onWeb: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 6.dp)) {
        Text(
            "完结全本 · 每页 20 本，滑到底自动续页",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        when {
            state.error != null -> ErrorPane(
                error = state.error, onRetry = onRetry,
                onWeb = if (state.error is SourceError.ChallengeRequired || state.error is SourceError.AuthRequired) onWeb else null,
            )

            state.loading -> LoadingPane()

            state.items.isEmpty() -> EmptyPane(
                imageRes = R.drawable.watercolor_read,
                title = "没有拿到完结列表",
                hint = "稍后重试，或先去榜单翻翻",
                actionLabel = "重试",
                onAction = onRetry,
            )

            else -> BookList(
                items = state.items,
                listState = listState,
                hasMore = state.hasMore,
                loadingMore = state.loadingMore,
                onOpenBook = onOpenBook,
                onLoadMore = onLoadMore,
            )
        }
    }
}

// =====================================================================
// 书籍列表（榜单 / 标签 / 完结共用）
// =====================================================================

@Composable
private fun BookList(
    items: List<BookBrief>,
    listState: LazyListState,
    hasMore: Boolean,
    loadingMore: Boolean,
    onOpenBook: (BookBrief) -> Unit,
    onLoadMore: () -> Unit,
) {
    // 滑到接近底部自动续页；Append 后条目数增长，条件自然复位。
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, hasMore) {
        if (nearEnd && hasMore) onLoadMore()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items, key = { _, b -> b.sourceId + b.bookId }) { _, book ->
            DiscoverBookCard(book) { onOpenBook(book) }
        }
        if (hasMore) {
            item(key = "load-more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    if (loadingMore) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onLoadMore) { Text("加载更多") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverBookCard(book: BookBrief, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    WatercolorPanel(
        modifier = Modifier.fillMaxWidth().paperFeedback(interaction).clickable(
            interactionSource = interaction, indication = null, onClick = onClick,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book.coverUrl, book.title, Modifier.size(64.dp, 92.dp), radius = 7.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title, maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        book.author.ifBlank { "未知作者" }, modifier = Modifier.weight(1f), maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.status.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(book.status)
                    }
                }
                if (book.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        book.tags.take(4).joinToString(" · "),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val done = status.contains("完结")
    Box(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(
                if (done) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            color = if (done) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =====================================================================
// 通用小件
// =====================================================================

/**
 * tonal 胶囊：固定 pill 形状，只用颜色区分选中。
 * 不做圆角形变、也不做按压缩放——这两者都会让胶囊进出裁剪层/缩放层，
 * 部分设备在四角出现暗边（看起来像四角阴影）。圆角恒定时不产生任何角部重采样。
 */
@Composable
private fun TonalChip(text: String, active: Boolean, onClick: () -> Unit) {
    com.will.lightreader.ui.components.WatercolorChoice(text, active, onClick = onClick)
}

@Composable
private fun LoadingPane() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.will.lightreader.ui.components.WatercolorCompanion(modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp)
                Text("正在找书…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyPane(
    imageRes: Int,
    title: String,
    hint: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(imageRes), contentDescription = null,
            modifier = Modifier.size(112.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorPane(error: SourceError, onRetry: () -> Unit, onWeb: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val illustrationSize = if (maxHeight < 320.dp) 80.dp else 128.dp
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.TopCenter) {
        TonalPanel(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.watercolor_read), contentDescription = null,
                    modifier = Modifier.size(illustrationSize),
                )
                Spacer(Modifier.height(10.dp))
                // 错误不道歉、不模糊：说清「哪出错 + 怎么修」
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
                    FilledTonalButton(onClick = onRetry) { Text("重试") }
                    if (onWeb != null) {
                        Button(onClick = onWeb) { Text(if (error is SourceError.ChallengeRequired && error.accessBlocked) "查看站点提示" else "打开站点验证") }
                    }
                }
            }
        }
    }
    }
}

private fun openSite(context: android.content.Context, error: SourceError?) {
    val url = when (error) {
        is SourceError.ChallengeRequired -> error.url
        is SourceError.AuthRequired -> error.url
        is SourceError.RateLimited -> error.url
        is SourceError.TakenDown -> error.url
        else -> "https://www.wenku8.net/"
    }
    context.startActivity(WebSessionActivity.intent(context, url, "站点登录 / 验证"))
}

/** 错误标题：一句话说清哪出错（不道歉、不模糊）。 */
internal fun errorHeadline(e: SourceError): String = when (e) {
    is SourceError.ContentUnavailable -> "当前没有可读章节"
    is SourceError.ComicRoute -> if (e.deniedHost == null) "漫画源线路暂不可用" else "漫画源域名待授权"
    is SourceError.Network -> "网络没连上"
    is SourceError.RateLimited -> "请求有点频繁"
    is SourceError.ChallengeRequired -> if (e.accessBlocked) "站点拒绝访问" else "站点想验证一下"
    is SourceError.AuthRequired -> "需要登录"
    is SourceError.TakenDown -> "这本书下架了"
    is SourceError.ParseChanged -> "页面未能完整解析"
    is SourceError.Unsupported -> "旧版书源已停用"
}

/** 错误修法：告诉用户下一步做什么。 */
internal fun errorHint(e: SourceError): String = when (e) {
    is SourceError.ContentUnavailable -> e.reason
    is SourceError.ComicRoute -> listOfNotNull(e.deniedHost, e.reason).joinToString("\n")
    is SourceError.Network -> "检查 Wi-Fi 或移动网络，连好后点重试"
    is SourceError.RateLimited -> "请求较频繁，请稍后再试"
    is SourceError.ChallengeRequired -> if (e.accessBlocked)
        "站点拦截了当前连接。可打开站点查看提示；若仍被拒绝，请稍后再试或联系站点。"
        else "点下面按钮打开站点，完成人机验证后回来"
    is SourceError.AuthRequired -> "点下面按钮登录站点后再来"
    is SourceError.TakenDown -> "来源站点已移除这本，去书源找找别的吧"
    is SourceError.ParseChanged -> e.detail + "；可重新加载后再试"
    is SourceError.Unsupported -> "现在只用 wenku8 书源，左滑书架可以把这本旧书移除"
}
