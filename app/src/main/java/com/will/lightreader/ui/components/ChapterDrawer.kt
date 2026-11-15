package com.will.lightreader.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.will.lightreader.data.BookmarkEntity
import com.will.lightreader.data.ExcerptEntity
import com.will.lightreader.domain.ChapterRef
import com.will.lightreader.ui.theme.AppIcons
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * 阅读导航抽屉：目录 / 书签 / 摘抄三段切换，配色跟随纸色（夜间不闪白），当前章高亮。
 * 触发入口在阅读页控制栏，不在这里浮一个按钮（会被正文盖住）。
 */
@Composable
fun ChapterDrawer(
    chapters: List<ChapterRef>,
    currentChapterUrl: String,
    bookmarks: List<BookmarkEntity>,
    excerpts: List<ExcerptEntity>,
    drawerState: DrawerState,
    containerColor: Color,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    downloadedChapters: Set<String> = emptySet(),
    onChapterClick: (String) -> Unit,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkDelete: (Long) -> Unit,
    onExcerptClick: (ExcerptEntity) -> Unit,
    onExcerptDelete: (Long) -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val drawerWidth = (windowWidth * .94f).coerceAtMost(440.dp)
    val reducedMotion = com.will.lightreader.ui.theme.Motion.reducedMotion()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var locatedChapter by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    BackHandler(drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) {
        scope.launch { drawerState.close() }
    }
    val tocState = rememberLazyListState()
    // 书签/摘抄列表状态提升到抽屉级：切换标签后保留各自滚动位置
    val bookmarkState = rememberLazyListState()
    val excerptState = rememberLazyListState()
    var sheetWidth by remember { mutableIntStateOf(0) }
    val populate by remember(drawerState) {
        derivedStateOf {
            drawerContentVisible(drawerState.currentValue == DrawerValue.Open,
                drawerState.targetValue == DrawerValue.Open, sheetWidth, drawerState.currentOffset)
        }
    }

    LaunchedEffect(drawerState.isOpen, chapters, currentChapterUrl) {
        if (drawerState.isOpen && locatedChapter != currentChapterUrl) {
            tab = 0
            val idx = chapters.indexOfFirst { it.chapterUrl == currentChapterUrl }
            if (idx >= 0) {
                tocState.scrollToItem((idx - 1).coerceAtLeast(0))
                locatedChapter = currentChapterUrl
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth).onSizeChanged { sheetWidth = it.width },
                drawerContainerColor = containerColor,
                drawerContentColor = contentColor,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
            ) {
                // Preserve native sheet geometry/edge gestures without measuring a hidden
                // catalogue, bookmarks and tab controls on every entry into the reader.
                if (populate) {
                val chapterTitles = remember(chapters) { chapters.associate { it.chapterUrl to it.title } }
                Column(Modifier.fillMaxSize().watercolorSurface(containerColor, accentColor, .07f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 26.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "阅读导航",
                                modifier = Modifier.watercolorWash(accentColor),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 23.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.01.em,
                                ),
                                color = contentColor,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when (tab) {
                                    1 -> "共 ${bookmarks.size} 条书签"
                                    2 -> "共 ${excerpts.size} 条摘抄"
                                    else -> "共 ${chapters.size} 章"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = mutedColor,
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            tab = 0
                            val index = chapters.indexOfFirst { it.chapterUrl == currentChapterUrl }
                            if (index >= 0) scope.launch { tocState.scrollToItem((index - 1).coerceAtLeast(0)) }
                        }) { Text("当前章", color = accentColor) }
                        Box(
                            Modifier.minimumInteractiveComponentSize().size(36.dp)
                                .clip(CircleShape)
                                .background(mutedColor.copy(alpha = 0.10f))
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                AppIcons.Close,
                                contentDescription = "关闭阅读导航",
                                tint = mutedColor,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    TonalSegmented(
                        labels = listOf("目录", "书签", "摘抄"),
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        height = 48.dp,
                        trackColor = mutedColor.copy(alpha = 0.10f),
                        thumbColor = accentColor.copy(alpha = 0.16f),
                        activeContentColor = accentColor,
                        inactiveContentColor = mutedColor,
                        textStyle = MaterialTheme.typography.titleSmall,
                        badge = { i ->
                            when (i) {
                                1 -> bookmarks.size.takeIf { it > 0 }?.toString()
                                2 -> excerpts.size.takeIf { it > 0 }?.toString()
                                else -> null
                            }
                        },
                    )

                    if (tab == 0) {
                        val current = remember(chapters, currentChapterUrl) {
                            chapters.indexOfFirst { it.chapterUrl == currentChapterUrl }
                        }
                        if (current >= 0) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)
                                .watercolorWash(accentColor, .10f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("正在阅读 · ${current + 1} / ${chapters.size}", color = accentColor,
                                    style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(5.dp))
                                Text(chapters[current].title, color = contentColor, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))
                            }
                        }
                    } else Spacer(Modifier.height(10.dp))

                    AnimatedContent(
                        modifier = Modifier.weight(1f),
                        targetState = tab,
                        transitionSpec = {
                            fadeIn(tween(if (reducedMotion) 0 else 160)) togetherWith fadeOut(tween(if (reducedMotion) 0 else 120))
                        },
                        label = "drawer-tab",
                    ) { current ->
                        when (current) {
                            0 -> TocList(
                                chapters = chapters,
                                currentChapterUrl = currentChapterUrl,
                                downloadedChapters = downloadedChapters,
                                listState = tocState,
                                contentColor = contentColor,
                                mutedColor = mutedColor,
                                accentColor = accentColor,
                                onChapterClick = onChapterClick,
                            )
                            1 -> BookmarkList(
                                bookmarks = bookmarks,
                                chapterTitles = chapterTitles,
                                listState = bookmarkState,
                                contentColor = contentColor,
                                mutedColor = mutedColor,
                                accentColor = accentColor,
                                onClick = onBookmarkClick,
                                onDelete = onBookmarkDelete,
                            )
                            else -> ExcerptList(
                                excerpts = excerpts,
                                listState = excerptState,
                                contentColor = contentColor,
                                mutedColor = mutedColor,
                                accentColor = accentColor,
                                onClick = onExcerptClick,
                                onDelete = onExcerptDelete,
                            )
                        }
                    }
                }
                } else Spacer(Modifier.fillMaxSize())
            }
        },
    ) {
        content()
    }
}

@Composable
private fun TocList(
    chapters: List<ChapterRef>,
    currentChapterUrl: String,
    downloadedChapters: Set<String>,
    listState: LazyListState,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onChapterClick: (String) -> Unit,
) {
    if (chapters.isEmpty()) {
        LoadingHint(mutedColor)
        return
    }
    val volumeCounts = remember(chapters) { chapters.groupingBy { it.volume }.eachCount() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(chapters, key = { _, chapter -> chapter.chapterUrl }, contentType = { _, _ -> "chapter" }) { index, chapter ->
            if (chapter.volume.isNotBlank() && chapter.volume != chapters.getOrNull(index - 1)?.volume) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 12.dp)
                    .semantics { heading() }, verticalAlignment = Alignment.Top) {
                    Text(chapter.volume, color = contentColor,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium, lineHeight = 26.sp),
                        modifier = Modifier.weight(1f).watercolorWash(accentColor, .14f))
                    Spacer(Modifier.width(12.dp))
                    Text("${volumeCounts[chapter.volume]} 章", color = mutedColor,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 5.dp))
                }
            }
            ChapterRow(
                chapter = chapter,
                ordinal = index + 1,
                isCurrent = chapter.chapterUrl == currentChapterUrl,
                isDownloaded = chapter.chapterUrl in downloadedChapters,
                contentColor = contentColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                onClick = { onChapterClick(chapter.chapterUrl) },
            )
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<BookmarkEntity>,
    chapterTitles: Map<String, String>,
    listState: LazyListState,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: (BookmarkEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        EmptyHint(
            icon = AppIcons.Bookmark,
            title = "还没有书签",
            hint = "阅读页点书签按钮，标记当前页",
            mutedColor = mutedColor,
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(bookmarks, key = { it.id }) { mark ->
            NoteRow(
                label = chapterTitles[mark.chapterUrl].orEmpty(),
                text = mark.text.ifBlank { "第 ${mark.blockIndex + 1} 段" },
                time = formatTime(mark.createdAt),
                contentColor = contentColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                onClick = { onClick(mark) },
                onDelete = { onDelete(mark.id) },
            )
        }
    }
}

@Composable
private fun ExcerptList(
    excerpts: List<ExcerptEntity>,
    listState: LazyListState,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: (ExcerptEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (excerpts.isEmpty()) {
        EmptyHint(
            icon = AppIcons.Sparkle,
            title = "还没有摘抄",
            hint = "长按正文段落，加入摘抄",
            mutedColor = mutedColor,
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(excerpts, key = { it.id }) { excerpt ->
            NoteRow(
                label = excerpt.chapterTitle,
                text = excerpt.text,
                time = formatTime(excerpt.createdAt),
                contentColor = contentColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                onClick = { onClick(excerpt) },
                onDelete = { onDelete(excerpt.id) },
            )
        }
    }
}

/** 书签/摘抄行：内容 → 章节标签 + 时间；右侧删除。 */
@Composable
private fun NoteRow(
    label: String,
    text: String,
    time: String,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.width(3.dp).height(34.dp).clip(CircleShape)
                .background(accentColor.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = contentColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (label.isNotBlank()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (time.isNotBlank()) {
                        Text(" · ", style = MaterialTheme.typography.labelSmall, color = mutedColor)
                    }
                }
                if (time.isNotBlank()) {
                    Text(time, style = MaterialTheme.typography.labelSmall, color = mutedColor)
                }
            }
        }
        Box(
            Modifier.minimumInteractiveComponentSize().size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.Close,
                contentDescription = "删除",
                tint = mutedColor.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun EmptyHint(
    icon: ImageVector,
    title: String,
    hint: String,
    mutedColor: Color,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = mutedColor.copy(alpha = 0.45f),
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = mutedColor)
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = mutedColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun LoadingHint(mutedColor: Color) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("(=^･ω･^=)", style = MaterialTheme.typography.titleMedium, color = mutedColor)
            Spacer(Modifier.height(8.dp))
            Text("正在加载章节列表…", style = MaterialTheme.typography.bodyMedium, color = mutedColor)
        }
    }
}

private fun formatTime(ts: Long): String {
    if (ts <= 0L) return ""
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val now = Calendar.getInstance()
    val two = { v: Int -> if (v < 10) "0$v" else "$v" }
    return if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        "${then.get(Calendar.MONTH) + 1}月${then.get(Calendar.DAY_OF_MONTH)}日 " +
            "${two(then.get(Calendar.HOUR_OF_DAY))}:${two(then.get(Calendar.MINUTE))}"
    } else {
        "${then.get(Calendar.YEAR)}年${then.get(Calendar.MONTH) + 1}月${then.get(Calendar.DAY_OF_MONTH)}日"
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterRef,
    ordinal: Int,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (isCurrent) Modifier.watercolorWash(accentColor, .18f) else Modifier)
            .semantics {
                selected = isCurrent
                if (isCurrent) stateDescription = "当前阅读章节"
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(ordinal.toString().padStart(3, '0'),
            modifier = Modifier.width(40.dp).padding(top = 3.dp),
            color = if (isCurrent) accentColor else mutedColor,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Serif))
        Column(Modifier.weight(1f)) {
            Text(chapter.title.ifBlank { "未命名章节" },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isCurrent) accentColor else contentColor)
            if (isCurrent || isDownloaded) {
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) Text("正在读", color = accentColor,
                        style = MaterialTheme.typography.labelSmall)
                    if (isDownloaded) {
                        Icon(AppIcons.Check, contentDescription = "已下载", tint = mutedColor,
                            modifier = Modifier.size(13.dp))
                        Text("已离线", color = mutedColor, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
