package com.will.lightreader.source.script

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ComicRepoEntry(val name: String,val url: String,val version: String)

class ComicScriptManager(context: Context, allowLocalTest: Boolean = false) {
    val store=ComicScriptStore(File(context.filesDir,"comic-sources"))
    val sourceData=ComicScriptDataStore(File(context.filesDir,"comic-source-data"))
    val network=ComicScriptNetwork(allowLocalTest)
    val engine=ComicScriptEngine(context,network)
    private val imageTransform = ComicImageTransform(engine)
    internal val imageCache=ComicImageCache(File(context.cacheDir,"comic-images"),validate={ bytes ->
        val b=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,b);b.outWidth>0 && b.outHeight>0
    })
    private val thumbnailLock=kotlinx.coroutines.sync.Mutex()
    private val thumbnails=object:LinkedHashMap<String,Thumbnail>() {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Thumbnail>)=size>256
    }
    internal data class Thumbnail(val url:String,val headers:Map<String,String>)
    val revision=MutableStateFlow(0)
    var onChanged: () -> Unit = {}
    private fun changed() { synchronized(thumbnails) { thumbnails.clear() };revision.value++;onChanged() }
    suspend fun stage(script: String, origin: String)=engine.inspect(script.removePrefix("\uFEFF"),origin)
    internal suspend fun checkJmLines(value: ComicScriptPackage): JmLineReport = withContext(Dispatchers.IO) {
        require(JmLineCheck.supports(value)) { "此版本的漫画源不支持线路检测" }
        val stream = (sourceData.read(value.key)["settings"] as? JsonObject)?.get("imageStream")?.jsonPrimitive?.contentOrNull ?: "1"
        JmLineCheck({ url, domains, headers ->
            val result = network.request(url, domains, headers = headers, limit = 65_536)
            require(result.status == 200) { "线路检测返回 HTTP ${result.status}" }
            result.bytes
        }).check(stream)
    }
    @Synchronized fun install(value:ComicScriptPackage) { store.install(value);changed() }
    @Synchronized fun enable(key:String,enabled:Boolean) { store.setEnabled(key,enabled);changed() }
    @Synchronized fun remove(key:String) { sourceData.remove(key);store.remove(key);changed() }
    @Synchronized fun rollback(key:String) { store.rollback(key);changed() }
    @Synchronized fun permissions(key:String,domains:List<String>) { store.setDomains(key,domains);changed() }
    suspend fun execute(value:ComicScriptPackage,expression:String,initialize:Boolean=false):JsonElement = evaluateInstalled(value,expression,true,true,initialize)
    internal suspend fun inspectInstalled(value: ComicScriptPackage, expression: String): JsonElement =
        evaluateInstalled(value, expression, commit = false, activeOnly = true)
    private suspend fun evaluateInstalled(value:ComicScriptPackage,expression:String,commit:Boolean,activeOnly:Boolean,initialize:Boolean=false):JsonElement = engine.evaluate(
        value.script,value.className,expression,value.domains,
        initialize=initialize,
        stateProvider={ kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(this@ComicScriptManager) { requireInstalled(value,activeOnly);sourceData.read(value.key) }
        } },
        commitState=if(!commit)null else { state -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            synchronized(this@ComicScriptManager) { requireInstalled(value,activeOnly);sourceData.write(value.key,state) }
        } })
    suspend fun settings(value:ComicScriptPackage):ComicSettingsSnapshot = parseComicSettings(evaluateInstalled(value,
        "({definitions:__lrSource.settings||{},values:__lrSettings})",false,false).jsonObject)
    suspend fun saveSettings(value:ComicScriptPackage,values:JsonObject,reset:Boolean=false) {
        validateComicSettingValues(settings(value).fields,values)
        evaluateInstalled(value,"__lrApplySettings(JSON.parse(${JsonPrimitive(values.toString())}),$reset)",true,false)
        synchronized(this) { changed() }
    }
    suspend fun resetSettings(value:ComicScriptPackage) {
        evaluateInstalled(value,"__lrApplySettings({},true)",true,false)
        synchronized(this) { changed() }
    }
    fun activeSources()=store.all().filter { it.enabled }.map { ScriptComicSource(this,it) }
    fun active(sourceId:String): ComicScriptPackage = store.all().firstOrNull { it.sourceId==sourceId && it.enabled } ?: error("漫画源已停用或移除")
    fun requireCurrent(value:ComicScriptPackage) {
        requireInstalled(value,true)
    }
    internal suspend fun thumbnail(pkg:ComicScriptPackage,url:String):Thumbnail {
        requireCurrent(pkg);network.validate(url,pkg.domains)
        // Only this complete, audited source has a URL-independent thumbnail callback.
        val audited=pkg.key=="jm" && java.security.MessageDigest.getInstance("SHA-256")
            .digest(pkg.script.replace("\r\n","\n").trim().removePrefix("\uFEFF").toByteArray()).joinToString("") { "%02x".format(it) }=="995a1c65e773d3485942898ea22be97491228ab5df8866575a94468d629878b4"
        val key="${revision.value}:${pkg.sourceId}:${if(audited)"*" else url}"
        thumbnailLock.lock()
        try {
            synchronized(thumbnails) { thumbnails[key] }?.let { return it.copy(url=if(audited)url else it.url) }
            val config=inspectInstalled(pkg,"""(async()=>{
                const c=(await __lrSource.comic?.onThumbnailLoad?.(${JsonPrimitive(url)}))||{};
                if(c.method&&c.method!=='GET'||c.data||c.onResponse||c.onLoadFailed||c.modifyImage)
                    throw Error('此源需要尚未支持的封面加载接口');
                return {url:c.url||${JsonPrimitive(url)},headers:c.headers||{}};
            })()""").jsonObject
            val target=config.getValue("url").jsonPrimitive.content
            val headers=config["headers"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
            require(headers.size<=32 && headers.keys.none { it.lowercase() in setOf("cookie","host","content-length") })
            network.validate(target,pkg.domains);requireCurrent(pkg)
            val result=Thumbnail(target,scriptRequestHeaders(headers))
            synchronized(thumbnails) { thumbnails[key]=result };return result
        } finally { thumbnailLock.unlock() }
    }
    internal suspend fun fetchImage(pkg:ComicScriptPackage,url:String,headers:Map<String,String>,transform:String?):ByteArray =
        cachedImage(pkg,url,headers,transform).bytes
    internal suspend fun cachedImage(pkg:ComicScriptPackage,url:String,headers:Map<String,String>,transform:String?,
        read:Boolean=true,write:Boolean=true,allowNetwork:Boolean=true):ComicImageCache.Hit = withContext(Dispatchers.IO) {
        requireCurrent(pkg);network.validate(url,pkg.domains)
        val actual=if(headers.keys.any { it.equals("User-Agent",true) })headers
            else headers+mapOf("User-Agent" to com.will.lightreader.source.net.HttpClient.UA)
        val key=comicImageIdentity(pkg.sourceId,url,actual,transform)
        val hit=imageCache.get(key,read,write,allowNetwork) {
            var attempt=0
            while(true) {
                try {
                    val result=network.request(url,pkg.domains,headers=actual,limit=MaxComicImageBytes.toLong(),image=true)
                    if(result.status!=200)throw ComicImageFailure("漫画图片返回 HTTP ${result.status}",result.status in listOf(408,429) || result.status>=500)
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeByteArray(result.bytes,0,result.bytes.size,bounds)
                    if(bounds.outWidth<=0 || bounds.outHeight<=0)throw ComicImageFailure("服务器返回的不是有效图片",false)
                    val bytes=if(transform==null)result.bytes else imageTransform.apply(result.bytes,transform)
                    requireCurrent(pkg);return@get bytes
                } catch(e:kotlinx.coroutines.CancellationException) { throw e }
                catch(e:Exception) {
                    val retry=when(e) { is ComicImageFailure -> e.transient;is java.io.IOException -> true;else -> false }
                    if(!retry || ++attempt>=3)throw ComicImageFailure(e.message ?: "漫画图片加载失败",false,e)
                    kotlinx.coroutines.delay(300L*attempt)
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        requireCurrent(pkg);hit
    }
    private fun requireInstalled(value:ComicScriptPackage,activeOnly:Boolean) {
        val current=store.get(value.key) ?: error("漫画源已移除")
        require(!activeOnly || current.enabled) { "漫画源已停用" }
        require(current.script==value.script && current.domains==value.domains) { "源已更新，请重新打开作品" }
    }
    suspend fun download(url:String):String {
        val host=URI(url).host ?: error("链接无效")
        val response=network.request(url,listOf(host),limit=1_048_576)
        require(response.status==200) { "源链接返回 HTTP ${response.status}" }
        return String(response.bytes,Charsets.UTF_8).removePrefix("\uFEFF")
    }
    fun repository(text:String,url:String)=parseComicRepository(text,url,network)
}
