package com.will.lightreader.source.web

import com.will.lightreader.source.SourceError
import com.will.lightreader.source.net.FetchedPage
import com.will.lightreader.source.net.PageFetcher
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * 取页降级链：OkHttp 快路径优先（原始 HTML）；被 Cloudflare / 登录拦截时切 WebView 引擎（浏览器已处理）。
 * 其它错误（网络/限流/解析）照常上抛，不吞。dump 钩子供 DEBUG 构建留存现场 DOM。
 *
 * 熔断：站点一旦把 OkHttp 打成挑战页，该主机在冷却期内不再逐请求试探。
 * 实测 wenku8 即便带着浏览器同步来的 cf_clearance，非浏览器 TLS 指纹仍然 403，
 * 每章白付一次 ~0.7s 往返；直接走 WebView 反而更快。
 */
class FallbackPageFetcher(
    private val primary: PageFetcher,
    private val fallback: PageFetcher,
    private val dump: ((url: String, html: String) -> Unit)? = null,
    private val blockedTtlMs: Long = 300_000L,
    private val now: () -> Long = System::currentTimeMillis,
) : PageFetcher {
    private val blockedUntil = ConcurrentHashMap<String, Long>()
    // Do not replay POST as a browser GET or silently change its semantics.
    override suspend fun postForm(url: String, fields: Map<String, String>, charset: Charset): FetchedPage =
        primary.postForm(url, fields, charset)

    override suspend fun getText(url: String, charset: Charset): FetchedPage {
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        val skipPrimary = host != null && (blockedUntil[host] ?: 0L) > now()
        val page = if (skipPrimary) {
            fallback.getText(url, charset)
        } else try {
            primary.getText(url, charset).also { if (host != null) blockedUntil.remove(host) }
        } catch (e: SourceError.ChallengeRequired) {
            if (host != null) blockedUntil[host] = now() + blockedTtlMs
            fallback.getText(url, charset)
        } catch (e: SourceError.AuthRequired) {
            if (host != null) blockedUntil[host] = now() + blockedTtlMs
            fallback.getText(url, charset)
        }
        dump?.invoke(url, page.text)
        return page
    }
}
