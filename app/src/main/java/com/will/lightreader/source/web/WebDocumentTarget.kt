package com.will.lightreader.source.web

import java.net.URI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Mirrors may change host; query bytes identify the actual search/ranking/tag page. */
internal class WebDocumentTarget(url: String) {
    private val request = URI(url)
    private fun identity(uri: URI): String = (uri.rawPath.trimEnd('/') +
        (uri.rawQuery?.let { "?$it" } ?: "")).replace(Regex("%[0-9a-fA-F]{2}")) { it.value.uppercase() }
    private val key = identity(request)
    private val search = request.path.endsWith("/search.php")
    fun matches(url: String?): Boolean = runCatching {
        val uri = URI(url.orEmpty())
        identity(uri) == key || search && Regex("/book/\\d+\\.htm").matches(uri.path.orEmpty()) &&
            uri.host?.substringAfter("www.") == request.host?.substringAfter("www.")
    }.getOrDefault(false)
    fun readyScript(token: String, bodyReady: String): String {
        val target = "(location.pathname.replace(/\\/+$/,'')+location.search).replace(/%[a-f0-9]{2}/gi,function(x){return x.toUpperCase();})"
        val detail = if (search) " || (/^\\/book\\/\\d+\\.htm$/.test(location.pathname) && location.hostname.replace(/^www\\./,'') === ${Json.encodeToString(request.host.substringAfter("www."))})" else ""
        return "(window.__lrPreviousDocument !== ${Json.encodeToString(token)} && (($target === ${Json.encodeToString(key)})$detail) && $bodyReady) ? 1 : 0"
    }
}
