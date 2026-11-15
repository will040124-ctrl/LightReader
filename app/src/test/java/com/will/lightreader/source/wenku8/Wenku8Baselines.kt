package com.will.lightreader.source.wenku8

import java.io.File
import java.nio.charset.Charset

/**
 * wenku8 golden fixture 基线工具。样本是 GB18030/GBK（大赏页有 Big5 变体），
 * 读取时嗅探 meta charset 后按其解码，避免 UTF-8 误读成乱码。
 */
object Wenku8Baselines {
    private val dir = File("src/test/resources/fixtures/wenku8")
    private val expected = File("src/test/resources/fixtures/wenku8/expected")

    fun fixtureNames(prefix: String): List<String> =
        (dir.listFiles()?.map { it.name } ?: emptyList()).filter { it.startsWith(prefix) }.sorted()

    fun firstFixtureName(prefix: String): String = fixtureNames(prefix).first()

    fun optionalFixtureName(prefix: String): String? = fixtureNames(prefix).firstOrNull()

    /** nameOrPrefix 可以是精确文件名，也可以是 "sugoi-*" 这样的前缀通配。 */
    fun optionalFixture(nameOrPrefix: String): String? {
        val exact = File(dir, nameOrPrefix)
        if (exact.exists()) return read(exact)
        val prefix = nameOrPrefix.removeSuffix("*").substringBeforeLast('.')
        val hit = fixtureNames(prefix).firstOrNull()
        return hit?.let { read(File(dir, it)) }
    }

    fun fixture(name: String): String = read(File(dir, name))

    private fun read(f: File): String {
        val bytes = f.readBytes()
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val charsetName = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        val charset = runCatching { Charset.forName(charsetName ?: "") }.getOrDefault(Wenku8Routes.GB18030)
        return String(bytes, charset)
    }

    fun snapshot(label: String, body: String): String =
        "== $label ==\n" + body.lineSequence().joinToString("\n") {
            it.trimEnd(' ', '\t', '\r')
        }.trimEnd('\n') + "\n"

    fun assertMatchesBaseline(testName: String, actual: String) {
        val base = File(expected, "$testName.txt")
        if (!base.exists()) {
            base.parentFile.mkdirs()
            base.writeText(actual)
            throw AssertionError("基线不存在，已生成 ${base.absolutePath}。请人工核对后重跑测试并提交基线。")
        }
        val want = base.readText()
        if (want != actual) {
            throw AssertionError("与基线不一致：${base.absolutePath}\n--- 基线 ---\n$want\n--- 实际 ---\n$actual")
        }
    }
}
