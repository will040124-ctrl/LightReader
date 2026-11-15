package com.will.lightreader.source.linovelib

import java.io.File

/**
 * golden fixture 的快照基线工具：解析结果渲染成确定性文本，与 expected 目录下的 txt 基线比对。
 * 基线不存在时写出基线并报错（人工核对后重跑即绿）；样本重新采集后基线更新走同一流程。
 */
object FixtureBaselines {
    private val dir = File("src/test/resources/fixtures/linovelib")
    private val expected = File("src/test/resources/fixtures/linovelib/expected")

    fun fixture(name: String): String = File(dir, name).readText()
    fun fixtureNames(prefix: String): List<String> =
        (dir.listFiles()?.map { it.name } ?: emptyList()).filter { it.startsWith(prefix) && it.endsWith(".html") }.sorted()
    fun firstFixtureName(prefix: String): String = fixtureNames(prefix).first()
    fun optionalFixtureName(prefix: String): String? = fixtureNames(prefix).firstOrNull()

    /** nameOrPrefix 可以是精确文件名，也可以是 "catalog-*.html" 这样的前缀通配。 */
    fun optionalFixture(nameOrPrefix: String): String? {
        val exact = File(dir, nameOrPrefix)
        if (exact.exists()) return exact.readText()
        val prefix = nameOrPrefix.removeSuffix("*").removeSuffix(".html")
        val hit = (dir.listFiles()?.map { it.name } ?: emptyList())
            .filter { it.startsWith(prefix) && it.endsWith(".html") }.sorted().firstOrNull()
        return hit?.let { File(dir, it).readText() }
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
