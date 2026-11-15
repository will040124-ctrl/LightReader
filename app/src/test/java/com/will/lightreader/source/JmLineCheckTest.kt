package com.will.lightreader.source

import com.will.lightreader.source.script.JmLineCheck
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class JmLineCheckTest {
    @Test fun `line suggestions keep source numbering and include the responsive image host`() = runTest {
        val bootstrap = "rup4a04-c02.tos-cn-hongkong.bytepluses.com"
        val calls = mutableListOf<String>()
        val checker = JmLineCheck({ url, hosts, headers ->
            calls += url
            when {
                url.contains("/newsvr-2025.txt") -> {
                    assertEquals(listOf(bootstrap), hosts)
                    val config = encrypt("""{"Server":["dead.example","working.example","*.bad.example","127.0.0.1"]}""", "diosfjckwpqpdfjkvnqQjsik")
                    "\uFEFF$config".toByteArray()
                }
                url.startsWith("https://dead.example/") -> throw java.net.SocketTimeoutException()
                else -> {
                    assertEquals("https://working.example/setting?app_img_shunt=3&express=", url)
                    assertEquals(listOf("working.example"), hosts)
                    assertEquals(md5("12318comicAPPContent"), headers["token"])
                    val data = encrypt("""{"img_host":"https://images.example"}""", "123185Hcomic3PAPP7R")
                    """{"data":"$data"}""".toByteArray()
                }
            }
        }, nowSeconds = { 123L })
        val report = checker.check("3")
        assertEquals(listOf(2), report.available)
        assertTrue(report.domains.containsAll(listOf(bootstrap, "working.example", "images.example")))
        assertEquals(3, calls.size)
    }

    private fun encrypt(value: String, secret: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(md5(secret).toByteArray(), "AES"))
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray()))
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
