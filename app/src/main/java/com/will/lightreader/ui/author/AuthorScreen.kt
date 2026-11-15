package com.will.lightreader.ui.author

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.will.lightreader.ui.components.GuardedPullToRefresh
import com.will.lightreader.ui.components.watercolorWash
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.will.lightreader.R
import com.will.lightreader.di.AppContainer
import com.will.lightreader.domain.BookBrief
import com.will.lightreader.ui.components.TonalPanel
import com.will.lightreader.ui.components.pressScale
import com.will.lightreader.ui.discover.errorHeadline
import com.will.lightreader.ui.discover.errorHint
import com.will.lightreader.ui.shelf.Cover

@OptIn(ExperimentalLayoutApi::class)
/** 作者作品集页（M3E tonal）：详情页点作者名进入，列出该作者的其他小说。 */
@Composable
fun AuthorScreen(
    container: AppContainer,
    sourceId: String,
    authorName: String,
    authorUrl: String?,
    onBack: () -> Unit,
    onOpenBook: (BookBrief) -> Unit,
) {
    val vm: AuthorViewModel = viewModel(
        factory = AuthorViewModel.factory(container.sources, sourceId, authorName, authorUrl)
    )
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    DisposableEffect(vm) { onDispose { vm.leavePage() } }
    LaunchedEffect(vm) { vm.ensureLoaded() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        com.will.lightreader.ui.SystemBarsAppearance(
            lightContent = com.will.lightreader.ui.theme.LocalAppDark.current
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 顶栏：返回 + 作者名
            Row(
                Modifier.widthIn(max = 980.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 16.dp).heightIn(min = 72.dp),
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
                Column(Modifier.weight(1f)) {
                    Text(
                        state.authorName,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.watercolorWash(MaterialTheme.colorScheme.primary, .14f),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "的作品 · ${state.items.size} 本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                when {
                    state.error != null && state.items.isEmpty() -> AuthorError(
                        message = errorHeadline(state.error!!),
                        hint = errorHint(state.error!!),
                        onRetry = { vm.load() },
                    )
                    state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                    state.items.isEmpty() -> EmptyAuthor(onBack)
                    else -> Column(Modifier.fillMaxSize()) {
                        if (state.error != null) Row(
                            Modifier.widthIn(max = 980.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .watercolorWash(MaterialTheme.colorScheme.tertiary, .15f).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("刷新未完成，已保留原有作品", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = vm::refresh, enabled = !state.refreshing) { Text("重试") }
                        }
                        GuardedPullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = { vm.refresh() },
                        atTop = { !listState.canScrollBackward },
                        blocked = state.loading,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        LazyColumn(
                            Modifier.align(Alignment.TopCenter).widthIn(max = 980.dp).fillMaxSize(), state = listState,
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.items, key = { it.sourceId + it.bookId }) { book ->
                                AuthorBookCard(book) { onOpenBook(book) }
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
private fun AuthorBookCard(book: BookBrief, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    TonalPanel(
        modifier = Modifier.fillMaxWidth().pressScale(interaction).clickable(
            interactionSource = interaction, indication = null, onClick = onClick,
        ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book.coverUrl, book.title, Modifier.size(64.dp, 92.dp), radius = 10.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title, maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        book.author.ifBlank { "未知作者" }, maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (book.status.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        val done = book.status.contains("完结")
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (done) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                book.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (done) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
private fun EmptyAuthor(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Image(
            painter = painterResource(R.drawable.watercolor_read), contentDescription = null,
            modifier = Modifier.size(170.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "暂未收录其他作品",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "可以返回书籍，或在发现页按作者搜索。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onBack) { Text("返回书籍") }
    }
}

@Composable
private fun AuthorError(message: String, hint: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.TopCenter) {
        TonalPanel(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.watercolor_read), contentDescription = null,
                    modifier = Modifier.size(112.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(message, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(hint, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}
