package com.will.lightreader.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.will.lightreader.source.script.*
import com.will.lightreader.ui.theme.LightReaderTheme
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

class ComicCompatQaActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val manager=ComicScriptManager(this,true)
        var report by mutableStateOf("验证扩展接口中…")
        lifecycleScope.launch {
                if (intent.getBooleanExtra("probeJm", false)) {
                    report = try {
                        probeJm(manager)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "FAIL ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}"
                    }
                    android.util.Log.i("ComicCompatQA", "RUN=${intent.getStringExtra("qaRunId")} $report")
                    return@launch
                }
                val script="""class Compat extends ComicSource { key='compat_qa';name='Compat QA';version='1.0.0';lightreaderApiVersion=2;domains=['127.0.0.1'];settings={language:{type:'select',options:[{value:'cn'},{value:'tw'}],default:'cn'},enabled:{type:'switch',default:true},name:{type:'input',validator:'^[a-z]+$',default:'reader'}};search={load:async(q,options,page)=>{if(!Array.isArray(options)||page!==1)throw Error('搜索参数顺序错误');return {comics:[{id:'sample',title:APP.locale,cover:''}]}}};comic={loadInfo:async()=>({title:'QA',chapters:new Map([['卷一',new Map([['10','第十话'],['2','第二话']])]])}),loadEp:async()=>({images:[]})}; }"""
                try {
                    require(manager.store.get("compat_qa")==null && manager.store.get("compat_other")==null) { "QA 标识已存在，拒绝覆盖" }
                    manager.stage(script.replace("key='compat_qa';","key='compat_qa';preview=this.saveData('preview',true);"),"QA")
                    require(manager.sourceData.read("compat_qa")["data"]!!.jsonObject.isEmpty())
                    val pkg=manager.stage(script,"QA");manager.install(pkg)
                    val other=pkg.copy(key="compat_other",name="Other QA");manager.install(other)
                    suspend fun run(expression:String)=manager.execute(pkg,expression)
                    val html=run("""(()=>{const doc=new HtmlDocument('<div id="card"><a><amp-img src="a&amp;b"></amp-img></a><h3>轻阅 &amp; 猫娘</h3></div><script>globalThis.htmlRan=true</script>');const el=doc.getElementById('card');return el.querySelector('a > amp-img').attributes.src==='a&b'&&el.querySelector('h3').text==='轻阅 & 猫娘'&&el.children.length===2&&!globalThis.htmlRan&&__lrSource.loadSetting('language')==='cn';})()""")
                    require(html.jsonPrimitive.boolean)
                    require(run("Convert.decodeUtf8(Convert.decodeBase64(Convert.encodeBase64(Convert.encodeUtf8('轻阅🌙'))))").jsonPrimitive.content=="轻阅🌙")
                    report="PASS HTML、默认设置与中文编码转换"
                    run("(()=>{__lrSource.saveData('counter',1);__lrSource.saveData('__proto__',{safe:true});return true})()")
                    require(run("__lrSource.loadData('__proto__').safe").jsonPrimitive.boolean)
                    require(manager.execute(other,"__lrSource.loadData('counter')") is JsonNull)
                    require(ComicScriptDataStore(java.io.File(filesDir,"comic-source-data")).read(pkg.key)["data"]!!.jsonObject["counter"]!!.jsonPrimitive.int==1)
                    var failed=false
                    try { run("(()=>{__lrSource.saveData('counter',99);throw Error('expected failure')})()") } catch(e:CancellationException) { throw e } catch(_:Exception) { failed=true }
                    require(failed && run("__lrSource.loadData('counter')").jsonPrimitive.int==1)
                    var timeout=false
                    try { run("(()=>{__lrSource.saveData('counter',99);while(true){}})()") } catch(_:TimeoutCancellationException) { timeout=true }
                    require(timeout && run("__lrSource.loadData('counter')").jsonPrimitive.int==1)
                    coroutineScope { (1..3).map { async { run("(()=>{const n=__lrSource.loadData('counter');__lrSource.saveData('counter',n+1);return n+1})()") } }.awaitAll() }
                    require(run("__lrSource.loadData('counter')").jsonPrimitive.int==4)
                    report+="\nPASS 预览不写入、隔离存储、重建恢复、异常/超时回滚、并发串行提交"
                    require(run("(async()=>Convert.hexEncode((await Network.fetchBytes('GET','http://127.0.0.1:18767/binary')).body))()").jsonPrimitive.content=="00ff8041")
                    require(run("(async()=>Convert.hexEncode(await (await fetch('http://127.0.0.1:18767/binary-echo',{method:'POST',body:new Uint8Array([0,255,128,65])})).arrayBuffer()))()").jsonPrimitive.content=="00ff8041")
                    require(run("(async()=>new HtmlDocument((await Network.get('http://127.0.0.1:18767/legacy')).body).querySelector('h3').text)()").jsonPrimitive.content=="中文正文")
                    report+="\nPASS 二进制 GET/POST、fetch 与声明编码的旧网页"
                    val source=ScriptComicSource(manager,pkg)
                    require(source.search("qa").single().title.isNotBlank())
                    val chapters=source.episodes(ComicScriptRoutes.book(pkg.sourceId,"sample"))
                    require(chapters.map { it.title }==listOf("第十话","第二话"))
                    require(chapters.all { it.volume=="卷一" })
                    require(manager.settings(pkg).fields.size==3)
                    manager.saveSettings(pkg,buildJsonObject { put("language","tw");put("enabled",false) })
                    require(run("__lrSource.loadSetting('language')").jsonPrimitive.content=="tw")
                    var invalid=false
                    try { manager.saveSettings(pkg,buildJsonObject { put("name","123") }) } catch(e:CancellationException) { throw e } catch(_:Exception) { invalid=true }
                    require(invalid && run("__lrSource.loadSetting('name')").jsonPrimitive.content=="reader")
                    manager.enable(pkg.key,false)
                    manager.saveSettings(pkg,buildJsonObject { put("language","cn") })
                    require(manager.settings(pkg).values["language"]!!.jsonPrimitive.content=="cn")
                    manager.enable(pkg.key,true)
                    manager.resetSettings(pkg)
                    require(run("__lrSource.loadSetting('enabled')").jsonPrimitive.boolean)
                    require(run("__lrSource.loadData('counter')").jsonPrimitive.int==4)
                    report+="\nPASS 搜索参数、Map 目录顺序、设置保存/校验/停用编辑/恢复默认"
                    manager.execute(other,"__lrSource.saveData('keep',true)")
                    manager.remove(pkg.key)
                    require(manager.sourceData.read(pkg.key)["data"]!!.jsonObject.isEmpty())
                    require(manager.sourceData.read(other.key)["data"]!!.jsonObject["keep"]!!.jsonPrimitive.boolean)
                    report+="\nPASS 移除仅清除本源数据\n全部通过"
                } catch(e:CancellationException) { throw e }
                catch(e:Exception) { report="FAIL ${e.javaClass.simpleName}: ${e.message?.take(160)}" }
                finally {
                    withContext(NonCancellable+Dispatchers.IO) {
                        for(key in listOf("compat_qa","compat_other"))if(manager.store.get(key)?.origin=="QA")manager.remove(key)
                    }
                }
            android.util.Log.i("ComicCompatQA", "RUN=" + intent.getStringExtra("qaRunId").orEmpty() + " " + report.replace("\n", " | "))
        }
        setContent { LightReaderTheme {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(32.dp)) { Text(report) }
        } }
    }

    private suspend fun probeJm(manager: ComicScriptManager): String {
        val origin = "Venera JM QA"
        suspend fun stage(file: String) = withContext(Dispatchers.IO) {
            manager.stage(manager.download("http://127.0.0.1:18767/$file"), origin)
        }
        val original = stage("venera-jm.js")
        require(original.key == "jm" && original.domains.size >= 2)
        intent.getStringExtra("jmLiveDomains")?.let { hosts ->
            val live = original.copy(domains = hosts.split(',').map(String::trim))
            require(manager.store.get(live.key) == null) { "QA 标识已存在，拒绝覆盖" }
            manager.install(live)
            try {
                val query = "LightReaderQA${java.util.UUID.randomUUID().toString().replace("-", "")}"
                val source = ScriptComicSource(manager, live)
                val result = source.search(query)
                val browseReport = verifyJmDiscovery(source)
                return "PASS jm ${live.version} 线上搜索 ${result.size} 条｜$browseReport｜全部通过"
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { if (manager.store.get(live.key)?.origin == origin) manager.remove(live.key) }
            }
        }
        val pkg = stage("fixture-jm.js").copy(domains = listOf("127.0.0.1"))
        require(manager.store.get(pkg.key) == null) { "QA 标识已存在，拒绝覆盖" }
        manager.install(pkg)
        try {
            val source = ScriptComicSource(manager, pkg)
            val browseReport = verifyJmDiscovery(source)
            val book = source.search("QA synthetic").single()
            require(book.title == "QA synthetic")
            val coverConfig=manager.thumbnail(pkg,ComicScriptRoutes.decode(android.net.Uri.parse(book.coverUrl).path!!.trimStart('/')))
            require(coverConfig.headers["Referer"]=="https://localhost/")
            val coverRequest=ImageRequest.Builder(this).data(book.coverUrl).allowHardware(false).build()
            repeat(2) {
                val coverResult=imageLoader.execute(coverRequest)
                if(coverResult is coil.request.ErrorResult)throw coverResult.throwable
                require(coverResult is SuccessResult)
            }

            require(source.detail(book.bookUrl).intro == "QA introduction")
            val chapter = source.episodes(book.bookUrl).single()
            require(chapter.title == "QA chapter")
            val page = source.pages(chapter.chapterUrl).single()
            require(page.imageTransform != null)
            fun verify(bitmap: android.graphics.Bitmap) {
                require(bitmap.width == 13 && bitmap.height == 53)
                require(bitmap.getPixel(0, 0) == android.graphics.Color.RED)
                require(bitmap.getPixel(0, bitmap.height - 1) == android.graphics.Color.BLUE)
            }
            val offlineBytes = source.downloadImage(page, page.requestHeaders)
            val offline = android.graphics.BitmapFactory.decodeByteArray(offlineBytes, 0, offlineBytes.size)
            try { verify(offline) } finally { offline.recycle() }
            val request = ImageRequest.Builder(this)
                .data(ComicScriptRoutes.image(pkg.sourceId, page.url, page.imageTransform))
                .allowHardware(false)
                .apply { page.requestHeaders.forEach { (key,value) -> addHeader(key,value) } }
                .build()
            val result = imageLoader.execute(request)
            if (result is coil.request.ErrorResult) throw result.throwable
            verify((result as SuccessResult).drawable.let { (it as android.graphics.drawable.BitmapDrawable).bitmap })
            return "PASS jm ${original.version} $browseReport｜搜索、详情、目录、线上及离线图片重排｜全部通过"
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { if (manager.store.get(pkg.key)?.origin == origin) manager.remove(pkg.key) }
        }
    }

    private suspend fun verifyJmDiscovery(source: ScriptComicSource): String {
        val options = source.prepareDiscovery()
        require(options.supportsHome && options.supportsTags)
        require(options.ranks.map { it.first } == listOf("mv", "mv_m", "mv_w", "mv_t"))
        val sections = source.discoveryHome().sections
        require(sections.isNotEmpty() && sections.all { it.items.isNotEmpty() }) { "漫画首页没有推荐区块" }
        val more = sections.firstNotNullOf { it.route }
        require(source.tagBooks(more, 1, false).items.isNotEmpty()) { "推荐区的查看更多没有结果" }
        val total = source.popularPage("mv", 1)
        require(total.items.isNotEmpty() && total.hasMore) { "总榜没有可翻页结果" }
        val second = source.popularPage("mv", 2)
        require(second.items.any { item -> total.items.none { it.bookId == item.bookId } }) { "总榜第二页重复第一页" }
        val periodCounts = listOf("mv_t", "mv_w", "mv_m").map { kind -> source.popularPage(kind, 1).items.size }
        val tags = source.tags()
        val category = tags.firstNotNullOf { it.routes["最新A漫"] }
        val search = tags.firstNotNullOf { it.routes["純愛"] }
        require(source.tagBooks(category, 1, false).items.isNotEmpty()) { "分类标签没有结果" }
        require(source.tagBooks(search, 1, true).items.isNotEmpty()) { "搜索标签没有结果" }
        require(source.tagBooks(source.detailTagRoute("純愛"), 1, false).items.isNotEmpty()) { "详情标签没有结果" }
        return "首页 ${sections.size} 区、查看更多、总榜两页、日周月榜 ${periodCounts.joinToString("/")} 条、${tags.size} 组标签及详情标签通过"
    }
}
