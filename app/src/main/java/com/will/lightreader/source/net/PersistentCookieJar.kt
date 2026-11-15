package com.will.lightreader.source.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File

@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val host: String,
    val path: String = "/",
    val expiresAt: Long = 0L,   // 0 = 会话 cookie，重启仍保留（M1 简化）
    val secure: Boolean = false,
)

/**
 * Cookie 持久化到单个 JSON 文件（原子写）。
 * 用途：WebView 登录/过验证后把 cookie 带进 OkHttp；wenku8 登录态重启不丢。
 */
class PersistentCookieJar(private val file: File) : CookieJar {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredCookie.serializer())
    private val cookies = LinkedHashSet<StoredCookie>()

    init { load() }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.host == url.host }
            this.cookies.add(
                StoredCookie(
                    name = c.name,
                    value = c.value,
                    host = url.host,
                    path = c.path,
                    expiresAt = if (c.expiresAt > 0L) c.expiresAt else 0L,
                    secure = c.secure,
                )
            )
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return cookies
            .filter { it.host == url.host && url.encodedPath.startsWith(it.path) }
            .filter { it.expiresAt == 0L || it.expiresAt > now }
            .filter { !it.secure || url.isHttps }
            .map { c ->
                Cookie.Builder().name(c.name).value(c.value).hostOnlyDomain(c.host).path(c.path)
                    .apply { if (c.secure) secure(); if (c.expiresAt > 0L) expiresAt(c.expiresAt) }
                    .build()
            }
    }

    /** 粘贴浏览器 Cookie 串（"a=1; b=2"）的备用导入入口。 */
    @Synchronized
    fun importRaw(host: String, raw: String) {
        for (pair in raw.split(";")) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isEmpty()) continue
            cookies.removeAll { it.name == name && it.host == host }
            cookies.add(StoredCookie(name = name, value = value, host = host))
        }
        persist()
    }

    @Synchronized
    fun persist() {
        val text = json.encodeToString(serializer, cookies.toList())
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching { json.decodeFromString(serializer, file.readText()) }
            .onSuccess { cookies.addAll(it) }
    }
}
