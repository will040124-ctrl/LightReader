@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.will.lightreader.ui

import android.net.Uri
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import com.will.lightreader.ui.components.LocalBookTransition
import com.will.lightreader.ui.components.bunkoDestination
import com.will.lightreader.ui.components.BookCoverMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.will.lightreader.di.AppContainer
import com.will.lightreader.ui.author.AuthorScreen
import com.will.lightreader.ui.book.BookScreen
import com.will.lightreader.ui.discover.DiscoverRequests
import com.will.lightreader.ui.discover.DiscoverScreen
import com.will.lightreader.ui.discover.DiscoverViewModel
import com.will.lightreader.ui.theme.Motion

/**
 * 导航：页面级 fade-through 转场（旧页淡出微缩，新页淡入上移 12dp，320ms 减速曲线）。
 */
@Composable
fun LightReaderNav(container: AppContainer) {
    val nav = rememberNavController()
    val currentEntry by nav.currentBackStackEntryAsState()
    // Non-reading routes explicitly own their normal system bars. A transiently hidden
    // bar must never become the restored baseline when leaving an immersive reader.
    if (currentEntry?.destination?.route?.startsWith("reader/") != true) {
        ReaderSystemBars(immersive = false, keepAwake = false)
    }
    val reduced = Motion.reducedMotion()
    val request by ReadingRequests.pending.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(request) {
        val target = request ?: return@LaunchedEffect
        var handled = false
        try {
            val (book, position) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                container.library.book(target.bookKey) to container.library.position(target.bookKey)
            }
            val route = when {
                book == null -> "home"
                position == null -> "book/${book.sourceId}/${Uri.encode(book.bookUrl)}"
                else -> "reader/${book.sourceId}/${Uri.encode(book.key)}/${Uri.encode(position.chapterUrl)}"
            }
            nav.navigate(route) { popUpTo("home"); launchSingleTop = true }
            if (book == null && target.bookKey.isNotBlank()) android.widget.Toast.makeText(context,
                "这本书已不在书架，先选一本喜欢的书吧", android.widget.Toast.LENGTH_SHORT).show()
            handled = true
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(_: Exception) {
            handled = true
            android.widget.Toast.makeText(context, "暂时无法续读，请从书架重新打开", android.widget.Toast.LENGTH_SHORT).show()
        } finally {
            if (handled && ReadingRequests.pending.value?.token == target.token) ReadingRequests.pending.value = null
        }
    }
    SharedTransitionLayout {
    CompositionLocalProvider(LocalBookTransition provides this) {
            NavHost(
                navController = nav,
                startDestination = "home",
                // 转场轻量化：去掉 scale（最贵的全屏重采样），只留 fade，杜绝掉帧。
                // book/ 详情页只淡入（封面是共享元素独立飞行，整屏位移会拖着封面跑）；
                // 内容的明显上浮+错峰进场在 BookScreen 内按块做。
                enterTransition = {
                    if (!reduced && targetState.destination.route?.startsWith("book/") == true)
                        fadeIn(tween(300, easing = Motion.Decelerate))
                    else fadeIn(tween(if (reduced) 100 else 180))
                },
                exitTransition = {
                    if (!reduced && targetState.destination.route?.startsWith("book/") == true)
                        fadeOut(keyframes { durationMillis = BookCoverMotion.FlightMs; 0f at 260 })
                    else fadeOut(Motion.PageExit)
                },
                popEnterTransition = {
                    if (!reduced && initialState.destination.route?.startsWith("book/") == true && targetState.destination.route == "home")
                        fadeIn(tween(150, delayMillis = 110))
                    else if (!reduced && targetState.destination.route?.startsWith("book/") == true)
                        fadeIn(tween(300, easing = Motion.Decelerate))
                    else fadeIn(tween(if (reduced) 100 else 180))
                },
                popExitTransition = {
                    if (!reduced && initialState.destination.route?.startsWith("book/") == true) {
                        val contentFadeEnd = if (targetState.destination.route == "home") 110 else 260
                        fadeOut(keyframes { durationMillis = BookCoverMotion.FlightMs; 0f at contentFadeEnd })
                    }
                    else fadeOut(Motion.PageExit)
                },
            ) {
                bunkoDestination("home") {
                    HomeScreen(
                        container = container,
                        onOpenBook = { sourceId, bookUrl ->
                            nav.navigate("book/$sourceId/${Uri.encode(bookUrl)}")
                        },
                        onContinue = { sourceId, bookKey, chapterUrl ->
                            nav.navigate("reader/$sourceId/${Uri.encode(bookKey)}/${Uri.encode(chapterUrl)}")
                        },
                    )
                }
                bunkoDestination(
                    route = "book/{sourceId}/{bookUrl}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("bookUrl") { type = NavType.StringType },
                    ),
                ) { entry ->
                    BookScreen(
                        container = container,
                        sourceId = entry.arguments!!.getString("sourceId")!!,
                        bookUrl = Uri.decode(entry.arguments!!.getString("bookUrl")!!),
                        onBack = { nav.popBackStack() },
                        onOpenChapter = { bookKey, chapterUrl ->
                            nav.navigate("reader/${entry.arguments!!.getString("sourceId")!!}/${Uri.encode(bookKey)}/${Uri.encode(chapterUrl)}")
                        },
                        onOpenAuthor = { authorName, authorUrl ->
                            val src = entry.arguments!!.getString("sourceId")!!
                            nav.navigate("author/$src/${Uri.encode(authorName)}/${Uri.encode(authorUrl ?: "")}")
                        },
                        // 点标签 → 回主屏发现页并直接打开该标签结果（HomeScreen 消费请求）
                        onOpenTag = { tag ->
                            DiscoverRequests.pendingTag.value = DiscoverRequests.Tag(entry.arguments!!.getString("sourceId")!!, tag)
                            nav.popBackStack("home", inclusive = false)
                        },
                    )
                }
                bunkoDestination(
                    route = "author/{sourceId}/{authorName}/{authorUrl}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("authorName") { type = NavType.StringType },
                        navArgument("authorUrl") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val authorUrl = Uri.decode(entry.arguments!!.getString("authorUrl")!!).ifBlank { null }
                    AuthorScreen(
                        container = container,
                        sourceId = entry.arguments!!.getString("sourceId")!!,
                        authorName = Uri.decode(entry.arguments!!.getString("authorName")!!),
                        authorUrl = authorUrl,
                        onBack = { nav.popBackStack() },
                        onOpenBook = { book ->
                            com.will.lightreader.ui.book.BookOpening.prime(book)
                            nav.navigate("book/${book.sourceId}/${Uri.encode(book.bookUrl)}")
                        },
                    )
                }
                bunkoDestination(
                    route = "reader/{sourceId}/{bookKey}/{chapterUrl}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("bookKey") { type = NavType.StringType },
                        navArgument("chapterUrl") { type = NavType.StringType },
                    ),
                ) { entry ->
                    com.will.lightreader.ui.reader.ReaderScreen(
                        container = container,
                        sourceId = entry.arguments!!.getString("sourceId")!!,
                        bookKey = Uri.decode(entry.arguments!!.getString("bookKey")!!),
                        chapterUrl = Uri.decode(entry.arguments!!.getString("chapterUrl")!!),
                        onBack = { nav.popBackStack() },
                        onOpenSettings = { nav.navigate("reading-settings") { launchSingleTop = true } },
                    )
                }
                bunkoDestination("reading-settings") {
                    com.will.lightreader.ui.settings.SettingsScreen(container, onBack = { nav.popBackStack() }, focusReading = true)
                }
            }
    }
    }
}
