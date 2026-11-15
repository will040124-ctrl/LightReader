package com.will.lightreader.source.web

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.will.lightreader.source.SourceError
import com.will.lightreader.source.net.FetchedPage
import com.will.lightreader.source.net.PageFetcher
import com.will.lightreader.source.net.PersistentCookieJar
import com.will.lightreader.source.net.isBackgroundRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 隐藏 WebView 取页引擎：用真实浏览器栈（UA/TLS/JS 全套）过 Cloudflare，
 * 页面就绪后抽 documentElement.outerHTML 交给纯解析器。返回文本是 DOM 解码后的 Unicode，charset 参数被忽略。
 *
 * 稳定性要点（真机实测踩坑总结）：
 * - 每个 WebView 只跑一个页面（Mutex）+ 开新加载前 stopLoading：防并发取页串扰；
 * - 就绪判定校验「当前路径 == 目标路径」：loadUrl 刚调用时还停在上一页，只看 readyState 会抽走旧页；
 * - 轮询就绪而非只等 onPageFinished：悬挂的追踪脚本会把 onPageFinished 拖到超时之后；
 * - 超时时看是否被重定向到别的路径（登录墙）：是则抛 AuthRequired 引导用户登录。
 *
 * 并发模型：用户请求独占一个 WebView，离线下载用另两个后备 WebView —— 下载既不顶掉用户正在看的页面，
 * 也不用排队等它加载完。站点视角仍是正常的多标签浏览。
 */
class WebViewFetcher(
    private val cookieJar: PersistentCookieJar,
    private val captureDiagnostics: Boolean = true,
    private val activityProvider: () -> Activity?,
) : PageFetcher {

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val SETTLE_MS = 200L
        private const val POLL_MS = 300L
        private const val BACKGROUND_SLOTS = 1
        private const val JS_FETCH_TIMEOUT_MS = 6_000L
        private const val BRIDGE_NAME = "__lrFetch"
        private const val FETCH_GAP_MS = 700L
        private const val MAX_FETCH_GAP_MS = 1_600L
        private const val GAP_DOWN_MS = 100L
        private const val GAP_UP_MS = 300L
    }

    /** 页内 fetch 的回传口：JS 拿到正文后按 id 交回来（Binder 线程，协程恢复本身线程安全）。 */
    class FetchBridge(private val pending: ConcurrentHashMap<String, Continuation<String>>) {
        @android.webkit.JavascriptInterface
        fun post(id: String, payload: String) {
            pending.remove(id)?.let { runCatching { it.resume(payload) } }
        }
    }

    /** 一个隐藏 WebView + 它的串行锁。 */
    private inner class Slot {
        var webView: WebView? = null
            private set
        val mutex = Mutex()
        val pending = ConcurrentHashMap<String, Continuation<String>>()

        /** 最近一次导航成功的主机；页内 fetch 只在同主机时可用（跨域会被 CORS 挡）。 */
        var host: String? = null

        @SuppressLint("SetJavaScriptEnabled")
        fun ensure(activity: Activity): WebView {
            webView?.takeIf { it.context === activity }?.let { return it }
            // 换 Activity 时销毁旧 WebView（否则旧 Activity 引用泄漏）
            destroy()
            return WebView(activity).also { new ->
                new.settings.javaScriptEnabled = true
                new.settings.domStorageEnabled = true
                new.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                new.settings.userAgentString = com.will.lightreader.source.net.HttpClient.UA
                new.addJavascriptInterface(FetchBridge(pending), BRIDGE_NAME)
                // Turnstile 第三方 iframe 需第三方 cookie，否则挑战无限转圈
                val cm = android.webkit.CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(new, true)
                webView = new
            }
        }

        fun destroy() {
            webView?.let {
                runCatching { it.stopLoading() }
                runCatching { it.destroy() }
            }
            webView = null
            host = null
            pending.clear()
        }
    }

    private val interactive = Slot()
    private val backgroundSlots = List(BACKGROUND_SLOTS) { Slot() }
    private val freeBackgroundSlots = Channel<Slot>(BACKGROUND_SLOTS).apply {
        backgroundSlots.forEach { trySend(it) }
    }

    /**
     * 两次取页之间的间隔。站点按时间窗限流，连续取快了会连着回 429；
     * 撞到一次就拉大间隔慢慢退，顺利几章再慢慢收回默认节奏。
     */
    private var fetchGapMs = FETCH_GAP_MS

    /** 显式释放缓存 WebView（Activity onDestroy 时调用），防 Activity/WebView 泄漏。 */
    fun release() {
        interactive.destroy()
        backgroundSlots.forEach { it.destroy() }
    }

    override suspend fun getText(url: String, charset: Charset): FetchedPage = withContext(Dispatchers.Main) {
        val activity = activityProvider()
            ?: throw SourceError.Network(IllegalStateException("WebView 取页需要前台 Activity"))
        if (coroutineContext.isBackgroundRequest) {
            val slot = freeBackgroundSlots.receive()
            try {
                fetchInBackground(activity, slot, url, charset)
            } finally {
                freeBackgroundSlots.trySend(slot)
            }
        } else {
            fetch(activity, interactive, url)
        }
    }

    /**
     * 后台取页：先试页内 fetch —— 已经有同站点页面载入时，直接用浏览器会话取 HTML，
     * 不渲染、不跑广告/统计脚本，单章从 ~2.8s 掉到几百毫秒。
     * 页面还没载入、跨域、或被挑战页挡住时退回整页导航（那条路一定拿得到 DOM）。
     */
    private suspend fun fetchInBackground(activity: Activity, slot: Slot, url: String, charset: Charset): FetchedPage {
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        val t0 = android.os.SystemClock.uptimeMillis()
        val html = slot.mutex.withLock {
            val wv = slot.webView ?: return@withLock null
            if (slot.host == null || slot.host != host) return@withLock null
            fetchViaPage(wv, slot, url, charset)
        }
        if (html != null) {
            if (captureDiagnostics && com.will.lightreader.BuildConfig.DEBUG) android.util.Log.d("LRWV", "js-hit ${android.os.SystemClock.uptimeMillis() - t0}ms $url")
            syncCookies(url)
            delay(FETCH_GAP_MS)
            return FetchedPage(html, browserProcessed = false)
        }
        if (captureDiagnostics && com.will.lightreader.BuildConfig.DEBUG) android.util.Log.d("LRWV", "js-miss ${android.os.SystemClock.uptimeMillis() - t0}ms $url")
        return fetch(activity, slot, url)
    }

    /** 在当前页里 fetch 目标地址，返回原始 HTML；429 退让后重试一次，其余失败/超时/疑似挑战页返回 null。 */
    private suspend fun fetchViaPage(wv: WebView, slot: Slot, url: String, charset: Charset): String? {
        var last = fetchViaPageOnce(wv, slot, url, charset)
        if (last.first == 429) {
            delay(1_200)
            last = fetchViaPageOnce(wv, slot, url, charset)
        }
        return if (last.first == 200) last.third else null
    }

    private suspend fun fetchViaPageOnce(
        wv: WebView,
        slot: Slot,
        url: String,
        charset: Charset,
    ): Triple<Int, String, String?> =
        withTimeoutOrNull(JS_FETCH_TIMEOUT_MS) {
            val id = java.util.UUID.randomUUID().toString()
            val raw = suspendCancellableCoroutine<String> { cont ->
                slot.pending[id] = cont
                cont.invokeOnCancellation { slot.pending.remove(id) }
                val js = "(function(){var id=${org.json.JSONObject.quote(id)},u=${org.json.JSONObject.quote(url)};" +
                    "fetch(u,{credentials:'include',cache:'no-store'}).then(function(r){return (" +
                    com.will.lightreader.source.net.BrowserResponseText.decoder(charset) + ")(r).then(" +
                    "function(t){window.$BRIDGE_NAME.post(id,JSON.stringify({s:r.status,t:t}));});})" +
                    ".catch(function(e){window.$BRIDGE_NAME.post(id,JSON.stringify({s:-1,t:String(e)}));});})();"
                wv.evaluateJavascript(js, null)
            }
            val obj = runCatching { org.json.JSONObject(raw) }.getOrNull()
                ?: return@withTimeoutOrNull Triple(0, "", null)
            val status = obj.optInt("s")
            val text = obj.optString("t")
            if (captureDiagnostics && com.will.lightreader.BuildConfig.DEBUG) android.util.Log.d("LRWV", "js status=$status len=${text.length} $url")
            val ok = status == 200 && text.length > 300 &&
                com.will.lightreader.source.net.PageResponseError.detect(url, text, status) == null
            Triple(status, text, if (ok) text else null)
        } ?: Triple(0, "", null)

    private suspend fun fetch(activity: Activity, slot: Slot, url: String): FetchedPage =
        slot.mutex.withLock {
            val wv = slot.ensure(activity)
            val t0 = android.os.SystemClock.uptimeMillis()
            val text = fetchHtml(activity, wv, url)
            if (captureDiagnostics && com.will.lightreader.BuildConfig.DEBUG) android.util.Log.d("LRWV", "nav ${android.os.SystemClock.uptimeMillis() - t0}ms $url")
            slot.host = runCatching { java.net.URI(wv.url ?: url).host }.getOrNull()
                ?: runCatching { java.net.URI(url).host }.getOrNull()
            FetchedPage(
                text = text.also { syncCookies(url) },
                browserProcessed = true,
            )
        }

    private suspend fun fetchHtml(activity: Activity, wv: WebView, url: String): String =
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            var settled = false
            var mainDocumentStatus = 200
            val target = WebDocumentTarget(url)
            val documentToken = java.util.UUID.randomUUID().toString()

            // 跨域重定向只换域名不换路径，故只比 pathname
            val reqPath = (runCatching { java.net.URI(url).path }.getOrNull() ?: url).trimEnd('/')
            val wenkuChapter = url.contains("wenku8.") && Regex("/novel/(?:\\d+/)?\\d+/\\d+\\.htm$").containsMatchIn(reqPath)
            val bodyReady = if (wenkuChapter) "(document.querySelector('#content') && (document.querySelector('#content').innerText.trim().length > 0 || document.querySelector('#content img')))"
                else "(document.body && document.body.innerHTML.length > 1200 && (document.readyState !== 'loading' || (document.querySelector('#content') && document.querySelector('.m_foot, #pagelink'))))"

            fun samePage(u: String?): Boolean =
                target.matches(u)

            var timeoutRef: Runnable? = null

            fun extract(finishedUrl: String) {
                if (settled) return
                wv.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                    if (settled) return@evaluateJavascript
                    settled = true
                    timeoutRef?.let { main.removeCallbacks(it) }
                    val text = runCatching {
                        org.json.JSONTokener(raw).nextValue() as? String
                    }.getOrNull().orEmpty()
                    if (cont.isActive) {
                        if (text.isBlank()) cont.resumeWithException(SourceError.Network(IOException("WebView 取到空页面：$finishedUrl")))
                        else {
                            val failure = com.will.lightreader.source.net.PageResponseError.detect(finishedUrl, text, mainDocumentStatus)
                            if (failure != null) {
                                cont.resumeWithException(failure)
                                return@evaluateJavascript
                            }
                            if (captureDiagnostics && com.will.lightreader.BuildConfig.DEBUG) dumpHtml(activity, finishedUrl, text)
                            cont.resume(text)
                        }
                    }
                }
            }

            // 超时不再一死了之：路径没跑偏就按现状抽取（登录页/残页由解析器裁决），跑偏了才抛 AuthRequired
            val timeout = Runnable {
                if (settled) return@Runnable
                wv.evaluateJavascript(
                    "location.href + '|' + (document.body ? document.body.innerHTML.length : 0) + '|' + (window.__lrPreviousDocument === ${org.json.JSONObject.quote(documentToken)} ? 1 : 0)"
                ) { raw ->
                    if (settled) return@evaluateJavascript
                    val parts = (raw ?: "").trim('"').split('|')
                    val cur = parts.getOrElse(0) { "" }
                    val len = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                    when {
                        cur.isNotEmpty() && !target.matches(cur) -> {
                            settled = true
                            if (cont.isActive) cont.resumeWithException(SourceError.AuthRequired(url))
                        }
                        len > 500 && !wenkuChapter && parts.getOrElse(2) { "1" } == "0" -> extract(url)
                        else -> {
                            settled = true
                            if (cont.isActive) cont.resumeWithException(SourceError.Network(IOException("WebView 取页超时：$url")))
                        }
                    }
                }
            }
            timeoutRef = timeout

            val poll = object : Runnable {
                override fun run() {
                    if (settled) return
                    // 不看 readyState：解析期悬挂的脚本会让它永远停在 loading
                    wv.evaluateJavascript(
                        target.readyScript(documentToken, bodyReady)
                    ) { ready ->
                        if (settled) return@evaluateJavascript
                        if (ready == "1") extract(url)
                        else main.postDelayed(this, POLL_MS)
                    }
                }
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                    if (request?.isForMainFrame == true && samePage(request.url.toString())) {
                        mainDocumentStatus = errorResponse?.statusCode ?: 200
                    }
                }
                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                    val resource = request?.url ?: return null
                    // These ad scripts can suspend HTML parsing before the chapter node. Keep site/challenge JS intact.
                    val ad = url.contains("wenku8.") && (Regex("/style_\\d+_\\d+_\\d+\\.js$").matches(resource.path.orEmpty()) ||
                        resource.host == "609999.xyz" && resource.path == "/ai.js")
                    return if (ad) android.webkit.WebResourceResponse("application/javascript", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0))) else null
                }

                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    if (samePage(finishedUrl)) main.postDelayed(poll, SETTLE_MS)
                }
            }

            wv.stopLoading()
            // location can change before the previous DOM is replaced. Mark that DOM
            // before navigating, including when reloading the exact same URL.
            wv.evaluateJavascript("window.__lrPreviousDocument = ${org.json.JSONObject.quote(documentToken)}") {
                if (!settled && cont.isActive) {
                    wv.loadUrl(url)
                    main.post(poll)
                    main.postDelayed(timeout, TIMEOUT_MS)
                }
            }
            // 取消时清掉本 handler 全部待执行回调，防止取消后仍每 300ms 对已冻结 WebView evaluateJavascript
            cont.invokeOnCancellation {
                settled = true
                main.removeCallbacksAndMessages(null)
            }
        }

    private fun dumpHtml(activity: Activity, url: String, text: String) {
        runCatching {
            val dir = java.io.File(activity.filesDir, "webview-cache")
            dir.mkdirs()
            val name = url.replace(Regex("[^\\w]"), "_").take(80) + ".html"
            java.io.File(dir, name).writeText(text)
        }
    }

    private fun syncCookies(url: String) {
        runCatching {
            val raw = android.webkit.CookieManager.getInstance().getCookie(url) ?: return
            cookieJar.importRaw(java.net.URI(url).host, raw)
        }
    }
}
