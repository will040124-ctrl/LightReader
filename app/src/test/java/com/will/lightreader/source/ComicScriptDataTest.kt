package com.will.lightreader.source

import com.will.lightreader.source.script.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicScriptDataTest {
    @get:Rule val temp=TemporaryFolder()
    private fun state(value:JsonElement)=buildJsonObject {
        put("data",buildJsonObject { put("counter",value) });put("settings",JsonObject(emptyMap()))
    }
    @Test fun `source state survives restart and cannot read or erase a different source`() {
        val root=temp.newFolder();val store=ComicScriptDataStore(root)
        store.write("first",state(JsonPrimitive(1)));store.write("second",state(JsonPrimitive(2)))
        assertEquals(JsonPrimitive(1),ComicScriptDataStore(root).read("first")["data"]!!.jsonObject["counter"])
        store.remove("first")
        assertTrue(store.read("first")["data"]!!.jsonObject.isEmpty())
        assertEquals(JsonPrimitive(2),store.read("second")["data"]!!.jsonObject["counter"])
    }
    @Test fun `quota failure retains previous state and creates no partial file`() {
        val root=temp.newFolder();val store=ComicScriptDataStore(root);val old=state(JsonPrimitive(1))
        store.write("first",old)
        try { store.write("first",state(JsonPrimitive("猫".repeat(50000))));fail("must reject") } catch(_:IllegalArgumentException) { }
        assertEquals(old,store.read("first"));assertFalse(java.io.File(root,"first.tmp").exists())
    }
    @Test fun `bad namespace and control-character storage keys are rejected`() {
        val store=ComicScriptDataStore(temp.newFolder())
        try { store.write("../escape",state(JsonPrimitive(1)));fail("must reject") } catch(_:IllegalArgumentException) { }
        val bad=buildJsonObject { put("data",buildJsonObject { put("bad\u0000",1) });put("settings",JsonObject(emptyMap())) }
        try { ComicScriptDataStore.validate(bad);fail("must reject") } catch(_:IllegalArgumentException) { }
    }
    @Test fun `corrupt state is reported and retained instead of replaced with empty values`() {
        val root=temp.newFolder();val file=java.io.File(root,"first.json").apply { writeText("broken") }
        try { ComicScriptDataStore(root).read("first");fail("must reject") } catch(_:java.io.IOException) { }
        assertEquals("broken",file.readText())
    }
}
