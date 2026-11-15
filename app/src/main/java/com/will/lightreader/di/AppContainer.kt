package com.will.lightreader.di

import android.app.Activity
import android.content.Context
import com.will.lightreader.source.SourceRegistry
import com.will.lightreader.source.net.HttpClient
import com.will.lightreader.source.net.PageFetcher
import com.will.lightreader.source.net.PersistentCookieJar
import com.will.lightreader.source.web.FallbackPageFetcher
import com.will.lightreader.source.web.WebViewFetcher
import com.will.lightreader.source.wenku8.Wenku8Source
import kotlinx.coroutines.launch
import java.io.File

class AppContainer(context: Context, comicSources: List<com.will.lightreader.source.ComicSource> = emptyList()) {
    /** 应用级协程域：离线下载等长任务在此运行，离开页面不中断。 */
    val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    val cookieJar = PersistentCookieJar(File(context.filesDir, "cookies.json"))
    private val http = HttpClient(cookieJar)
    val comicScripts = com.will.lightreader.source.script.ComicScriptManager(context,
        allowLocalTest = com.will.lightreader.BuildConfig.BUILD_TYPE == "benchmark")

    /** WebView 引擎需要前台 Activity，由 MainActivity 注入（lambda 持有，随生命周期置空）。 */
    @Volatile
    var activityProvider: () -> Activity? = { null }

    /** WebView 取页引擎引用：Activity onDestroy 时调 [WebViewFetcher.release] 防泄漏。 */
    val webFetcher = WebViewFetcher(cookieJar) { activityProvider() }
    /** Account pages are private: neither fetcher writes DOM snapshots or account URLs to diagnostics. */
    val accountWebFetcher = WebViewFetcher(cookieJar, captureDiagnostics = false) { activityProvider() }
    private val accountPages: PageFetcher = FallbackPageFetcher(primary = http, fallback = accountWebFetcher)
    val accountSources: List<com.will.lightreader.source.account.AccountLibrarySource> = listOf(
        com.will.lightreader.source.account.Wenku8AccountSource(accountPages) { shape ->
            if(com.will.lightreader.BuildConfig.DEBUG) android.util.Log.d("LRAccountShape",shape)
        })

    /** 取页降级链：OkHttp 快路径 → Cloudflare/登录拦截时 WebView 引擎。DEBUG 构建统一留存现场 DOM。 */
    val pages: PageFetcher = FallbackPageFetcher(
        primary = http,
        fallback = webFetcher,
        dump = if (com.will.lightreader.BuildConfig.DEBUG) { url, html ->
            runCatching {
                val dir = java.io.File(context.filesDir, "fetch-cache")
                dir.mkdirs()
                val name = url.replace(Regex("[^\\w]"), "_").take(80) + ".html"
                java.io.File(dir, name).writeText(html)
            }
        } else null,
    )

    // 当前唯一主力源：轻小说文库（wenku8）。linovelib 因站点重度混淆反爬已停用。
    // 本地导入（epub/txt/…）作为第二个书源注册，详情/目录/正文与站点书走同一套契约。
    val library = com.will.lightreader.data.LibraryRepository(context)
    val sources = SourceRegistry(
        listOf(
            Wenku8Source(pages),
            com.will.lightreader.source.local.LocalSource(
                library,
                File(context.filesDir, com.will.lightreader.source.local.LocalIds.IMPORT_DIR),
            ),
        ) + (comicSources + comicScripts.activeSources()).map { com.will.lightreader.source.ComicSourceBridge(it) }
    )

    /** 本地格式导入（书架「导入」入口用）。 */
    val localImporter = com.will.lightreader.source.local.LocalImporter(context, library)

    val shelfCache by lazy {
        val directSource = Wenku8Source(http)
        com.will.lightreader.data.ShelfCache(context.applicationContext, library) { book ->
            // Warmup is silent. Browser login and challenge handling belong to an explicit book open.
            val source = directSource.takeIf { it.id == book.sourceId }
                ?: sources.find(book.sourceId)?.takeIf { it is com.will.lightreader.source.ComicSourceBridge }
            source?.bookDetail(book.bookUrl)
        }
    }

    init {
        comicScripts.onChanged = {
            sources.replaceScripts(comicScripts.activeSources().map { com.will.lightreader.source.ComicSourceBridge(it) })
            appScope.launch { library.expireComicDetails() }
        }
        // 启动自愈：把断了指针但文件还在的下载记录重新链接
        appScope.launch { runCatching { library.relinkDownloads() } }
    }

    /** 插图预取入 Coil 磁盘缓存（离线可读）。只写盘、不进内存，避免下载期白白解码占内存。 */
    val prefetchImage: suspend (String) -> Unit = { url ->
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            loader.execute(
                coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .size(1280)
                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                    .build()
            )
        }
        Unit
    }
}
