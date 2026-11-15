package com.will.lightreader.source.script

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.Base64
import kotlinx.serialization.json.*

fun comicImageIdentity(sourceId:String,url:String,headers:Map<String,String>,transform:String?=null):String {
    val canonical=buildJsonObject {
        put("source",sourceId);put("url",url)
        put("headers",JsonObject(headers.entries.associate { it.key.lowercase(java.util.Locale.ROOT) to it.value }.toSortedMap().mapValues { JsonPrimitive(it.value) }))
        if (transform != null) put("transform",transform)
    }.toString()
    return java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
}

@Serializable
data class ComicScriptPackage(val key: String, val name: String, val version: String,
    val className: String, val script: String, val domains: List<String> = emptyList(),
    val origin: String = "本地文件", val enabled: Boolean = true, val api: Int = 1) {
    val sourceId get() = "comic-js-${key.lowercase()}"
    fun validate() {
        require(key.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{1,63}"))) { "源标识无效" }
        require(name.isNotBlank() && name.length <= 80) { "源名称无效" }
        require(version.matches(Regex("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}"))) { "版本须为三段数字" }
        require(api in 1..2) { "此源需要尚未支持的轻阅接口版本" }
        require(className.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]{0,79}"))) { "源类名无效" }
        require(script.toByteArray().size in 1..1_048_576) { "源脚本超过 1 MB" }
        require(domains.size in 1..16 && domains.distinct().size == domains.size) { "请填写 1–16 个允许访问的域名" }
        domains.forEach { domain -> require(validComicDomainPermission(domain)) { "域名授权格式无效：$domain" } }
    }
}

fun newerComicVersion(candidate: String, current: String): Boolean {
    val a=candidate.split('.').map { it.toInt() };val b=current.split('.').map { it.toInt() }
    return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
}

object ComicScriptRoutes {
    fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
    fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    fun image(sourceId: String, url: String, transform: String? = null, thumbnail: Boolean = false) =
        "lrcomic://$sourceId/${encode(url)}" + (if(thumbnail)"?thumbnail=1" else "") + (if (transform == null) "" else "#${encode(transform)}")
    fun book(sourceId: String, id: String)="https://lightreader.invalid/$sourceId/${encode(id)}"
    fun bookId(url: String, sourceId: String): String {
        val uri=URI(url)
        require(uri.scheme=="https" && uri.host=="lightreader.invalid" && uri.path.startsWith("/$sourceId/") && uri.rawQuery==null) { "漫画作品地址无效" }
        val encoded=uri.path.removePrefix("/$sourceId/")
        require(encoded.matches(Regex("[A-Za-z0-9_-]{1,2048}"))) { "漫画作品标识无效" }
        return decode(encoded)
    }
    fun episode(sourceId: String, book: String, episode: String)="https://lightreader.invalid/$sourceId/${encode(book)}/${encode(episode)}"
    fun episodeIds(url: String, sourceId: String): Pair<String,String> {
        val uri=URI(url);val parts=uri.path.trim('/').split('/')
        require(uri.scheme=="https" && uri.host=="lightreader.invalid" && uri.rawQuery==null && parts.size==3 && parts[0]==sourceId) { "漫画话地址无效" }
        return decode(parts[1]) to decode(parts[2])
    }
}
