package com.will.lightreader.source.net

import java.nio.charset.Charset

/** 取页结果：browserProcessed = 浏览器引擎（WebView）取回、站点 JS 已跑过。 */
data class FetchedPage(val text: String, val browserProcessed: Boolean)

/**
 * 取网页的统一出口。实现：OkHttp 快路径（[HttpClient]，原始 HTML，站点乱序需还原）；
 * 被 Cloudflare/登录拦截时由 [com.will.lightreader.source.web.FallbackPageFetcher] 降级到 WebView 引擎（JS 已处理，顺序已修好）。
 */
interface PageFetcher {
    suspend fun getText(url: String, charset: Charset = Charsets.UTF_8): FetchedPage
    /** Public form endpoints, e.g. a source's official chapter directory API. */
    suspend fun postForm(url: String, fields: Map<String, String>, charset: Charset = Charsets.UTF_8): FetchedPage =
        throw com.will.lightreader.source.SourceError.Unsupported(url, "此取页方式不支持表单请求")
}
