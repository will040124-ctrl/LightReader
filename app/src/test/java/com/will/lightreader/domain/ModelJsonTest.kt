package com.will.lightreader.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelJsonTest {
    private val json = Json { classDiscriminator = "type" }

    @Test
    fun `ChapterContent 序列化往返保留三种 Block`() {
        val content = ChapterContent(
            chapterUrl = "https://www.linovelib.com/novel/3032/151118.html",
            title = "序章",
            blocks = listOf(
                Block.Paragraph("第一段。"),
                Block.Image(ref = "https://img.linovelib.com/a.jpg", caption = "彩页", fullWidth = true),
                Block.Divider,
                Block.Paragraph("第二段。"),
            ),
        )
        val decoded = json.decodeFromString<ChapterContent>(json.encodeToString(content))
        assertEquals(content, decoded)
    }

    @Test
    fun `黄金 JSON 锁定线上格式（判别值与字段名稳定）`() {
        val golden = """
            {"chapterUrl":"https://www.linovelib.com/novel/3032/151118.html","title":"序章","blocks":[
              {"type":"paragraph","text":"第一段。"},
              {"type":"image","ref":"https://img.linovelib.com/a.jpg","caption":"彩页","fullWidth":true},
              {"type":"divider"},
              {"type":"paragraph","text":"第二段。","ruby":[{"base":"魔女","ruby":"まじょ"}]}
            ]}
        """.trimIndent()
        val decoded = json.decodeFromString<ChapterContent>(golden)
        assertEquals(4, decoded.blocks.size)
        assertTrue(decoded.blocks[0] is Block.Paragraph)
        assertEquals("まじょ", (decoded.blocks[3] as Block.Paragraph).ruby.single().ruby)

        // 编码方向同样钉死：判别值必须是稳定短名，而不是全限定类名
        val encoded = json.encodeToString(decoded)
        for (tag in listOf("\"type\":\"paragraph\"", "\"type\":\"image\"", "\"type\":\"divider\"")) {
            assertTrue("判别值不稳定，缺少 $tag：$encoded", encoded.contains(tag))
        }
        assertTrue("判别值泄漏全限定类名：$encoded", !encoded.contains("com.will.lightreader"))
    }
}
