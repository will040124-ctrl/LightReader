package com.will.lightreader.source.net

import com.will.lightreader.source.SourceError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 统一 HTTP 出口：移动 UA + CookieJar + 同域限速 + 429 指数退避 + 错误分类。
 * charset：wenku8 是 GB18030，linovelib 是 UTF-8（按字节读入后一次性解码，避免二次转码损坏）。
 */
class HttpClient(
    private val jar: PersistentCookieJar,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val minIntervalMs: Long = 400,
    private val backgroundIntervalMs: Long = 800,
    private val quietAfterInteractiveMs: Long = 1200,
) : PageFetcher {
    companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private val limiters = ConcurrentHashMap<String, RateLimiter>()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", UA).build())
        }
        .build()

    override suspend fun getText(url: String, charset: Charset): FetchedPage = fetch(url, charset, null)
    override suspend fun postForm(url: String, fields: Map<String, String>, charset: Charset): FetchedPage = fetch(url, charset, fields)
    private suspend fun fetch(url: String, charset: Charset, fields: Map<String, String>?): FetchedPage = withContext(Dispatchers.IO) {
        val background = coroutineContext.isBackgroundRequest
        val httpUrl = url.toHttpUrl()
        limiters.getOrPut(httpUrl.host) {
            RateLimiter(
                minIntervalMs = minIntervalMs,
                backgroundIntervalMs = backgroundIntervalMs,
                quietAfterInteractiveMs = quietAfterInteractiveMs,
                sleep = sleep,
            )
        }.acquire(background)

        var backoff = 1500L
        for (attempt in 1..5) {
            val response = try {
                val request = Request.Builder().url(httpUrl)
                if (fields != null) request.post(okhttp3.FormBody.Builder().apply { fields.forEach { (key,value) -> add(key,value) } }.build())
                requestPage(request.build())
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                throw SourceError.Network(e)
            }
            response.let { resp ->
                val body = ResponseText.decode(resp.bytes, resp.contentType, charset)
                when {
                    resp.code == 429 && attempt < 5 -> { sleep(backoff); backoff *= 2 }
                    resp.code == 429 -> throw SourceError.RateLimited(url)
                    else -> {
                        PageResponseError.detect(url, body, resp.code)?.let { throw it }
                        return@withContext FetchedPage(body, browserProcessed = false)
                    }
                }
            }
        }
        throw SourceError.RateLimited(url) // 不可达：循环内必返回或抛出，kotlin 需要收尾
    }

    private data class RawPage(val code: Int, val contentType: String?, val bytes: ByteArray)

    /** Cancellation aborts the socket and body read, freeing rapid discovery switches. */
    private suspend fun requestPage(request: Request): RawPage = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val result = response.use { RawPage(it.code, it.header("Content-Type"), it.body?.bytes() ?: ByteArray(0)) }
                    if (cont.isActive) cont.resume(result)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

}
