package com.will.lightreader.source.script

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal data class JmLineReport(val available: List<Int>, val domains: List<String>)

/** Read-only route diagnostics for the public configuration protocol in Venera's jm.js 1.4.0. */
internal class JmLineCheck(
    private val request: suspend (String, List<String>, Map<String, String>) -> ByteArray,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {
    suspend fun check(imageStream: String): JmLineReport = coroutineScope {
        val config = withTimeoutOrNull(7_000) {
            request("https://$Bootstrap/newsvr-2025.txt", listOf(Bootstrap), emptyMap())
        } ?: error("动态线路列表连接超时")
        val encoded = config.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val servers = decrypt(encoded, "diosfjckwpqpdfjkvnqQjsik")["Server"] as? JsonArray
            ?: error("动态线路列表格式无效")
        val stream = imageStream.takeIf { it in listOf("1", "2", "3", "4") } ?: "1"
        val live = servers.take(4).mapIndexedNotNull { index, value ->
            val host = exactHost((value as? JsonPrimitive)?.contentOrNull) ?: return@mapIndexedNotNull null
            async {
                withTimeoutOrNull(6_500) {
                    try {
                        val time = nowSeconds().toString()
                        val headers = mapOf(
                            "token" to md5(time + "18comicAPPContent"), "tokenparam" to "$time,2.0.16",
                            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36",
                            "Authorization" to "Bearer", "Origin" to "https://localhost",
                            "Referer" to "https://localhost/", "X-Requested-With" to "com.example.app",
                        )
                        val url = "https://$host/setting?app_img_shunt=$stream&express="
                        val response = Json.parseToJsonElement(request(url, listOf(host), headers).toString(Charsets.UTF_8)).jsonObject
                        val settings = decrypt(response.getValue("data").jsonPrimitive.content, time + "185Hcomic3PAPP7R")
                        val image = (settings["img_host"] as? JsonPrimitive)?.contentOrNull?.let { URI(it) }
                        val cdn = image?.takeIf { it.scheme == "https" && it.userInfo == null }?.host?.let(::exactHost)
                        Line(index + 1, host, cdn)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
        require(live.isNotEmpty()) { "线路列表已读取，但当前网络下没有线路通过检测" }
        JmLineReport(live.map { it.number }, (listOf(Bootstrap, DefaultImage) + live.flatMap { listOfNotNull(it.api, it.cdn) }).distinct())
    }

    private data class Line(val number: Int, val api: String, val cdn: String?)

    private fun exactHost(value: String?): String? = value?.lowercase()?.takeIf {
        '.' in it && !it.startsWith("*.") && !it.matches(Regex("[0-9.]+")) && validComicDomainPermission(it)
    }

    private fun decrypt(encoded: String, secret: String): JsonObject {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(md5(secret).toByteArray(), "AES"))
        val text = cipher.doFinal(Base64.getDecoder().decode(encoded.trim())).toString(Charsets.UTF_8)
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end >= start) { "线路配置响应格式无效" }
        return Json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val Bootstrap = "rup4a04-c02.tos-cn-hongkong.bytepluses.com"
        private const val DefaultImage = "cdn-msp.jmapinodeudzn.net"
        fun supports(pkg: ComicScriptPackage) = pkg.key == "jm" && pkg.className == "JM" && pkg.version == "1.4.0"
    }
}
