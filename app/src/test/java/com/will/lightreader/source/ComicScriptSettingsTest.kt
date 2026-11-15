package com.will.lightreader.source

import com.will.lightreader.source.script.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ComicScriptSettingsTest {
    private fun parse(text:String)=parseComicSettings(Json.parseToJsonElement(text).jsonObject)
    @Test fun `typed settings preserve false and empty values and expose unsupported callbacks`() {
        val snapshot=parse("""{"definitions":{"quality":{"title":"画质","type":"select","default":"high","options":[{"value":"","text":"自动"},{"value":"high"}]},"enabled":{"type":"switch","default":true},"path":{"type":"input","default":"abc"},"button":{"type":"callback"}},"values":{"quality":"","enabled":false}}""")
        assertEquals(4,snapshot.fields.size)
        assertEquals(JsonPrimitive(""),snapshot.values["quality"])
        assertEquals(JsonPrimitive(false),snapshot.values["enabled"])
        assertEquals(JsonPrimitive("abc"),snapshot.values["path"])
        assertNotNull(snapshot.fields.last().unsupported)
    }
    @Test fun `malformed options do not prevent other settings from rendering`() {
        val snapshot=parse("""{"definitions":{"bad":{"type":"select","options":[{"value":"x"},{"value":"x"}]},"good":{"type":"switch","default":false}}}""")
        assertNotNull(snapshot.fields.first().unsupported)
        assertNull(snapshot.fields.last().unsupported)
    }
    @Test fun `reject unknown fields invalid choices and string booleans before commit`() {
        val fields=parse("""{"definitions":{"quality":{"type":"select","default":"x","options":[{"value":"x"}]},"toggle":{"type":"switch","default":false}}}""").fields
        for(text in listOf("""{"missing":true}""","""{"quality":"y"}""","""{"toggle":"false"}""")) {
            try { validateComicSettingValues(fields,Json.parseToJsonElement(text).jsonObject);fail("must reject") } catch(_:IllegalArgumentException) { } catch(_:IllegalStateException) { }
        }
    }
    @Test fun `inputs and option counts are bounded`() {
        val fields=parse("""{"definitions":{"name":{"type":"input","default":""}}}""").fields
        try { validateComicSettingValues(fields,buildJsonObject { put("name","x".repeat(2049)) });fail("must reject") } catch(_:IllegalArgumentException) { }
        val many=JsonArray((1..65).map { buildJsonObject { put("value",it) } })
        val snapshot=parseComicSettings(buildJsonObject { put("definitions",buildJsonObject { put("many",buildJsonObject { put("type","select");put("options",many) }) }) })
        assertNotNull(snapshot.fields.single().unsupported)
    }
}
