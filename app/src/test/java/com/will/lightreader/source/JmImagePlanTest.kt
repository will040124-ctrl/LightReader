package com.will.lightreader.source
import com.will.lightreader.source.script.JmImagePlan
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class JmImagePlanTest {
    private val template=javaClass.getResource("/jm-image-template.js")!!.readText()
    @Test fun `native strips cover every source row once and reverse bands with remainder`() {
        for(num in 2..20 step 2) for(height in listOf(37,101,240)) {
            val plan=JmImagePlan.resolve(template.replace("__BANDS__",num.toString()),5,height)!!
            val copies=plan.getValue("copies").jsonArray.map { it.jsonArray.map { it.jsonPrimitive.int } }
            val actual=copies.flatMap { r -> (r[3] until r[3]+r[5]).toList() }
            val size=height/num
            val expected=(num-1 downTo 0).flatMap { band -> (band*size until band*size+size+if(band==num-1)height%num else 0).toList() }
            assertEquals(expected,actual);assertEquals((0 until height).toList(),actual.sorted())
            var y=0;copies.forEach { assertEquals(y,it[1]);y+=it[5] };assertEquals(height,y)
        }
    }
    @Test fun `changed program or unsafe count falls back to sandbox`() {
        val valid=template.replace("__BANDS__","10")
        assertNull(JmImagePlan.resolve(valid+";throw Error('changed')",5,101))
        assertNull(JmImagePlan.resolve(valid.replace("Math.floor","Math.ceil"),5,101))
        for(num in listOf(0,1,3,22,99))assertNull(JmImagePlan.resolve(template.replace("__BANDS__",num.toString()),5,101))
        assertNull(JmImagePlan.resolve(valid,5,3))
    }
}
