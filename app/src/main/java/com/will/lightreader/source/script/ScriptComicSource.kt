package com.will.lightreader.source.script

import com.will.lightreader.domain.*
import com.will.lightreader.source.ComicSource
import com.will.lightreader.source.SourceDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class ScriptComicSource(private val manager:ComicScriptManager, private val pkg:ComicScriptPackage):ComicSource {
    override val id=pkg.sourceId
    override val displayName=pkg.name
    private data class Discovery(val homeIndex: Int?, val ranks: List<Pair<String, String>>, val tags: List<TagGroup>)
    @Volatile private var browseConfig: Discovery? = null
    override val discovery: SourceDiscovery get() = browseConfig?.let {
        SourceDiscovery(it.ranks, it.tags.isNotEmpty(), false, if (it.ranks.isEmpty() && it.homeIndex == null) "漫画" else "榜单",
            supportsHome = it.homeIndex != null)
    } ?: SourceDiscovery(emptyList(), false, false, "漫画")
    private fun quote(value:String)=JsonPrimitive(value).toString()
    private suspend fun invoke(expression:String):JsonElement {
        manager.requireCurrent(pkg)
        val result=try { manager.execute(pkg,expression,initialize=true) }
        catch (e: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive || pkg.key != "jm") throw e
            throw jmRouteError()
        }
        catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(e:Exception) {
            val first=e.message.orEmpty().lineSequence().firstOrNull().orEmpty()
            val deniedHost = Regex("漫画源未授权域名：([a-z0-9.-]+)", RegexOption.IGNORE_CASE).find(e.message.orEmpty())?.groupValues?.get(1)
            if (deniedHost != null) throw com.will.lightreader.source.SourceError.ComicRoute(
                if (pkg.key == "jm") "在设置的漫画源管理中打开「访问域名 → 检测当前线路」，保存检测出的域名后重试。"
                else "请在漫画源管理的「访问域名」中核对此域名。", deniedHost)
            if(first.contains("LR_CONTENT_UNAVAILABLE:"))throw com.will.lightreader.source.SourceError.ContentUnavailable(first.substringAfter("LR_CONTENT_UNAVAILABLE:").take(600))
            if(pkg.key=="mangadex_public" && first.contains("当前语言没有可在轻阅阅读的章节"))
                throw com.will.lightreader.source.SourceError.ContentUnavailable("当前语言没有可直接阅读的章节，可能未收录或已被站点标为不可用。可更新 MangaDex 公开源后在源设置查看其他可用语言；作品信息存在不代表章节仍可阅读。")
            if (pkg.key == "jm" && listOf("LR_COMIC_NETWORK:", "Invalid Status Code", "Invalid Data",
                    "Cannot read properties of undefined").any { first.contains(it, ignoreCase = true) }) throw jmRouteError()
            throw e
        }
        manager.requireCurrent(pkg)
        return result
    }
    private fun jmRouteError() = com.will.lightreader.source.SourceError.ComicRoute(
        "在漫画源管理中检测当前线路，并在源设置里选一条通过检测的 Api Domain。")
    private fun JsonObject.text(key:String)=this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonElement?.strings(): List<String> = (this as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
    }
    private fun cover(url:String):String?=url.takeIf { it.isNotBlank() }?.let {
        // A source can rotate its artwork host independently of its API host.
        runCatching { manager.network.validate(it,pkg.domains); ComicScriptRoutes.image(id, it, thumbnail=true) }.getOrNull()
    }
    override suspend fun prepareDiscovery(): SourceDiscovery {
        if (browseConfig != null) return discovery
        val data = withContext(Dispatchers.IO) { manager.inspectInstalled(pkg, """
            ({home: Array.isArray(__lrSource.explore) ? __lrSource.explore.findIndex(p => p?.type === 'multiPartPage' && typeof p.load === 'function') : -1,
              ranks: __lrSource.category?.enableRankingPage !== false && typeof __lrSource.categoryComics?.ranking?.load === 'function'
                ? __lrSource.categoryComics.ranking.options : [],
              parts: Array.isArray(__lrSource.category?.parts) ? __lrSource.category.parts.filter(p => p?.type === 'fixed' &&
                (p.itemType === 'search' || p.itemType === 'category' && typeof __lrSource.categoryComics?.load === 'function')) : []})
        """.trimIndent()) }.jsonObject
        val ranks = data["ranks"].strings().take(32).mapNotNull { option ->
            val key = option.substringBefore('-')
            key.takeIf { it.isNotBlank() && it.length <= 100 }?.let { it to option.substringAfter('-', it).take(80) }
        }.distinctBy { it.first }
        val groups = (data["parts"] as? JsonArray).orEmpty().take(32).mapNotNull { entry ->
            val part = entry as? JsonObject ?: return@mapNotNull null
            val name = part.text("name").ifBlank { "标签" }
            val type = part.text("itemType")
            val params = part["categoryParams"] as? JsonArray
            val routes = part["categories"].strings().take(150).mapIndexedNotNull { index, label ->
                if (label.isBlank()) return@mapIndexedNotNull null
                val param = (params?.getOrNull(index) as? JsonPrimitive)?.contentOrNull
                label to encodeBrowseRoute(type, label, param)
            }.toMap()
            TagGroup(name, routes.keys.toList(), routes).takeIf { routes.isNotEmpty() }
        }.groupBy { it.name }.map { (name, parts) ->
            TagGroup(name, parts.flatMap { it.tags }.distinct(), parts.fold(emptyMap()) { routes, part -> routes + part.routes })
        }
        browseConfig = Discovery(data["home"]?.jsonPrimitive?.intOrNull?.takeIf { it >= 0 }, ranks, groups)
        return discovery
    }

    override suspend fun discoveryHome(): BrowsePage {
        prepareDiscovery()
        val home = browseConfig?.homeIndex ?: return BrowsePage(emptyList(), false)
        val sections = invoke("""(async()=>{
            const parts = await __lrSource.explore[$home].load();
            if (!Array.isArray(parts)) throw Error('漫画首页格式无效');
            return parts.slice(0, 32).map(p => ({title:p.title, viewMore:p.viewMore, comics:__lrBrowseCards(p.comics, 32)}));
        })()""").jsonArray.map { value ->
            val section = value.jsonObject
            val title = section.text("title").ifBlank { "漫画推荐" }
            BrowseSection(title, parseBriefs(section), browseRoute(section["viewMore"]))
        }.filter { it.items.isNotEmpty() }
        return BrowsePage(emptyList(), false, sections)
    }

    override suspend fun popularPage(kind: String, page: Int): BrowsePage {
        require(page in 1..500)
        prepareDiscovery()
        if (browseConfig?.ranks.orEmpty().none { it.first == kind }) return BrowsePage(emptyList(), false)
        return parseBrowsePage(invoke("__lrSource.categoryComics.ranking.load(${quote(kind)},$page)").jsonObject, page)
    }

    override suspend fun tags(): List<TagGroup> {
        prepareDiscovery()
        return browseConfig?.tags.orEmpty()
    }

    override fun detailTagRoute(tag: String): String = encodeBrowseRoute("detail", tag, null)

    override suspend fun tagBooks(route: String, page: Int, popular: Boolean): BrowsePage {
        require(page in 1..500)
        val fields = Json.parseToJsonElement(ComicScriptRoutes.decode(route)).jsonArray
        val type = fields[0].jsonPrimitive.content
        val label = fields[1].toString()
        val param = fields[2].toString()
        if (type == "detail") {
            val target = withContext(Dispatchers.IO) {
                manager.inspectInstalled(pkg, "__lrSource.comic.onClickTag?.('Tag',$label) ?? null")
            }
            val next = browseRoute(target) ?: encodeBrowseRoute("search", fields[1].jsonPrimitive.content, null)
            return tagBooks(next, page, popular)
        }
        val expression = when (type) {
            "search" -> "__lrSource.search.load($label,__lrBrowseOptions(__lrSource.search.optionList,$popular),$page)"
            "category" -> """(async()=>{
                const category=__lrSource.categoryComics;
                const fields=category.optionList ?? await category.optionLoader?.($label,$param);
                return category.load($label,$param,__lrBrowseOptions(fields,$popular),$page);
            })()"""
            else -> error("此标签入口尚未支持")
        }
        return parseBrowsePage(invoke(expression).jsonObject, page)
    }

    private fun encodeBrowseRoute(type: String, label: String, param: String?): String = ComicScriptRoutes.encode(buildJsonArray {
        add(JsonPrimitive(type)); add(JsonPrimitive(label)); add(JsonPrimitive(param))
    }.toString())

    private fun browseRoute(value: JsonElement?): String? {
        val type: String
        val label: String
        val param: String?
        if (value is JsonObject) {
            type = value.text("page").ifBlank { value.text("action") }
            val attrs = value["attributes"] as? JsonObject
            label = attrs?.text(if (type == "search") "text" else "category") ?: value.text("keyword")
            param = (attrs ?: value).text("param").ifBlank { null }
        } else {
            val action = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
            type = action.substringBefore(':')
            val target = action.substringAfter(':', "")
            label = if (type == "category") target.substringBefore('@') else target
            param = if (type == "category") target.substringAfter('@', "").ifBlank { null } else null
        }
        return if (type in setOf("category", "search") && label.isNotBlank()) encodeBrowseRoute(type, label, param) else null
    }

    override suspend fun search(query:String):List<BookBrief> = searchPage(query,1).items
    override suspend fun searchPage(query:String,page:Int):BrowsePage {
        require(page in 1..500) { "搜索页码超出范围" }
        if(query.isBlank())return BrowsePage(emptyList(),false)
        return parseBrowsePage(invoke("__lrSource.search.load(${quote(query)},__lrSearchOptions(__lrSource.search),$page)").jsonObject, page)
    }

    private fun parseBriefs(result: JsonObject): List<BookBrief> {
        val list=result["comics"] as? JsonArray ?: error("源未返回搜索列表")
        require(list.size<=500) { "源搜索条目过多" }
        return list.map { item ->
            val c=item.jsonObject;val raw=c.text("id");val title=c.text("title")
            require(raw.isNotBlank() && raw.length<=1000 && title.isNotBlank()) { "漫画条目缺少标识或书名" }
            BookBrief(id,ComicScriptRoutes.encode(raw),ComicScriptRoutes.book(id,raw),title,
                c.text("subtitle").ifBlank { c.text("subTitle") },cover(c.text("cover")), tags = c["tags"].strings())
        }.distinctBy { it.bookId }
    }

    private fun parseBrowsePage(result: JsonObject, page: Int): BrowsePage {
        val items = parseBriefs(result)
        val maxPage=result["maxPage"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
        return BrowsePage(items,items.isNotEmpty() && page<500 && (maxPage==null || page<maxPage))
    }
    private suspend fun info(book:String)=invoke("""(async()=>{const info=await __lrSource.comic.loadInfo(${quote(book)});return {...info,chapters:__lrChapterEntries(info.chapters)};})()""").jsonObject
    override suspend fun detail(bookUrl:String):BookDetail {
        val raw=ComicScriptRoutes.bookId(bookUrl,id);val c=info(raw)
        require(c.text("title").isNotBlank()) { "漫画详情缺少标题" }
        val tagGroups = c["tags"] as? JsonObject
        val authors = tagGroups?.get("Author").strings()
        return BookDetail(id,ComicScriptRoutes.encode(raw),bookUrl,c.text("title"),
            c.text("subtitle").ifBlank{c.text("subTitle")}.ifBlank { authors.joinToString("、") },cover(c.text("cover")),
            intro=c.text("description"),tags=tagGroups?.get("Tag").strings().ifEmpty { c["tags"].strings() },status=c.text("status"))
    }
    override suspend fun episodes(bookUrl:String):List<ChapterRef> {
        val raw=ComicScriptRoutes.bookId(bookUrl,id);val c=info(raw)
        val chapters=c["chapters"]
        if(chapters==null || chapters is JsonNull) return listOf(ChapterRef(ComicScriptRoutes.episode(id,raw,"__single__"),"正文"))
        val flat=chapters as? JsonArray ?: error("此目录格式尚未支持")
        val entries=flat.map { item -> val entry=item.jsonObject
            ChapterRef(ComicScriptRoutes.episode(id,raw,entry.text("id")),entry.text("title"),entry.text("volume")) }
        require(entries.isNotEmpty() && entries.size<=10_000 && entries.all { it.title.isNotBlank() } && entries.distinctBy { it.chapterUrl }.size==entries.size) { "漫画话目录无效或为空" }
        return entries
    }
    override suspend fun pages(episodeUrl:String):List<ComicPage> {
        val (raw,episode)=ComicScriptRoutes.episodeIds(episodeUrl,id)
        val ep=if(episode=="__single__")"null" else quote(episode)
        val expression="""(async()=>{
            const result=await __lrSource.comic.loadEp(${quote(raw)},$ep);
            if(!Array.isArray(result.images)||result.images.length<1||result.images.length>2000)throw Error('漫画页列表无效');
            const pages=[];
            for(const original of result.images){
                if(typeof original!=='string')throw Error('此图片列表格式尚不支持');
                const config=(await __lrSource.comic.onImageLoad?.(original,${quote(raw)},$ep))||{};
                if(config.method&&config.method!=='GET'||config.data||config.onResponse||config.onLoadFailed)throw Error('此源需要尚未支持的图片加载接口');
                const transform = config.modifyImage ?? null;
                if(transform!==null && (typeof transform!=='string'||Convert.encodeUtf8(transform).byteLength>4096))throw Error('图片处理脚本无效');
                pages.push({id:original,url:config.url||original,headers:config.headers||{},transform});
            }
            return pages;
        })()"""
        val records=invoke(expression).jsonArray
        val pages=records.map { item ->
            val c=item.jsonObject;val url=c.text("url")
            manager.network.validate(url,pkg.domains)
            val h=(c["headers"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.content }
            require(h.size<=32 && h.keys.none { it.lowercase() in setOf("cookie","host","content-length") }) { "源图片请求头不支持" }
            ComicPage(c.text("id"),url,requestHeaders=scriptRequestHeaders(h),imageTransform=c.text("transform").ifBlank { null })
        }
        validateComicPages(pages)
        return pages
    }
    override fun imageHeaders(episodeUrl:String,page:ComicPage)=page.requestHeaders
    suspend fun downloadImage(page:ComicPage,headers:Map<String,String>):ByteArray =
        manager.fetchImage(pkg,page.url,headers,page.imageTransform)
}
