package com.will.lightreader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.will.lightreader.di.AppContainer
import com.will.lightreader.ui.discover.DiscoverRequests
import com.will.lightreader.ui.discover.DiscoverScreen
import com.will.lightreader.ui.discover.DiscoverViewModel
import com.will.lightreader.ui.settings.SettingsScreen
import com.will.lightreader.ui.shelf.ShelfScreen
import com.will.lightreader.ui.theme.AppIcons
import com.will.lightreader.ui.theme.Motion

/** 一级导航目标（与编排预览一致：书架 · 发现 · 设置）。 */
enum class HomeTab(val label: String, val icon: ImageVector) {
    Shelf("书架", AppIcons.Book),
    Discover("发现", AppIcons.Compass),
    Settings("设置", AppIcons.Tune),
}

/**
 * 主屏骨架：手机 = 底部 NavigationBar，平板（≥600dp）= 左侧 NavigationRail。
 * 三个一级目标常驻切换；书籍详情/阅读页仍是 push 路由（在 Nav.kt）。
 * 屏幕自身的 statusBarsPadding 保留；底部安全区由 NavigationBar/Rail 承担，
 * 内容区 consume 掉 navigationBars insets，避免旧屏内部重复避让。
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenBook: (sourceId: String, bookUrl: String) -> Unit,
    onContinue: (sourceId: String, bookKey: String, chapterUrl: String) -> Unit,
) {
    val pageStates = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableStateOf(HomeTab.Shelf) }
    androidx.activity.compose.BackHandler(tab != HomeTab.Shelf) { tab = HomeTab.Shelf }
    val dark = com.will.lightreader.ui.theme.LocalAppDark.current
    SystemBarsAppearance(lightContent = dark)

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    // Keep the VM in this route's store, but create it only when discovery is requested.
    // Constructing it on the shelf starts browsing and can initialize WebView mid-transition.
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val discoverVm = remember(owner, container) {
        lazy(LazyThreadSafetyMode.NONE) {
            ViewModelProvider(owner, DiscoverViewModel.factory(container.sources))[DiscoverViewModel::class.java]
        }
    }

    // 详情页点标签的跨页请求：切到发现页并打开该标签
    val pendingTag by DiscoverRequests.pendingTag.collectAsState()
    LaunchedEffect(pendingTag) {
        val tag = pendingTag ?: return@LaunchedEffect
        DiscoverRequests.pendingTag.value = null
        tab = HomeTab.Discover
        discoverVm.value.openBookTag(tag.sourceId, tag.label)
    }

    // 文件管理器「用轻阅打开 epub/txt」：切到书架，由书架页接手导入
    val pendingImport by ImportRequests.pending.collectAsState()
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) tab = HomeTab.Shelf
    }

    val tabContent: @Composable () -> Unit = {
        // tab 切换：淡入 + 2% 上浮进场，旧页快速淡出（fade-through，颜色/透明度动效）
        val reduced = Motion.reducedMotion()
        Box(
            Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars)
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                    else {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(180, delayMillis = 20)) +
                            slideInHorizontally(tween(220, easing = Motion.Decelerate)) { it / 60 * direction })
                            .togetherWith(fadeOut(tween(140)) +
                                slideOutHorizontally(tween(180, easing = Motion.Decelerate)) { -it / 90 * direction })
                    }
                },
                label = "home-tab",
            ) { t ->
                pageStates.SaveableStateProvider(t.name) {
                when (t) {
                    HomeTab.Shelf -> ShelfScreen(
                        container = container,
                        onFindBook = { tab = HomeTab.Discover },
                        onOpenBook = onOpenBook,
                        onContinue = onContinue,
                    )
                    HomeTab.Discover -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        DiscoverScreen(
                            vm = discoverVm.value,
                            onOpenBook = { book -> com.will.lightreader.ui.book.BookOpening.prime(book); onOpenBook(book.sourceId, book.bookUrl) },
                        )
                    }
                    HomeTab.Settings -> SettingsScreen(container = container, onBack = null)
                }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            BunkoNavigation(selected = tab, rail = true, onSelect = { tab = it })
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f)) { tabContent() }
        }
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.weight(1f)) { tabContent() }
            com.will.lightreader.ui.components.WatercolorRule()
            BunkoNavigation(selected = tab, rail = false, onSelect = { tab = it })
        }
    }
}
