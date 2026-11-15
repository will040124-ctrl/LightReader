package com.will.lightreader.source.script

import android.content.Context
import androidx.javascriptengine.*
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ComicScriptEngine(private val context: Context, val network: ComicScriptNetwork) {
    private val mutex=Mutex()
    private val host by lazy { listOf("comic-dom-vendor.js","comic-crypto-vendor.js","comic-codecs.js","comic-host.js","comic-html.js","comic-image-plan.js").joinToString("\n") {
        context.assets.open(it).bufferedReader().use { stream->stream.readText() }
    } }
    fun supported()=JavaScriptSandbox.isSupported()
    private suspend fun <T> ListenableFuture<T>.awaitValue(): T = suspendCancellableCoroutine { c ->
        c.invokeOnCancellation { cancel(true) }
        addListener({ if(c.isActive)try { c.resume(get()) } catch(e:Exception) { c.resumeWithException(e.cause ?: e) } },Executor { it.run() })
    }
    suspend fun evaluate(script: String, className: String, expression: String, domains: List<String> = emptyList(),
        stateProvider:(suspend ()->JsonObject)?=null,commitState:(suspend (JsonObject)->Unit)?=null,
        initialize:Boolean=false,timeoutMs:Long=15_000): JsonElement = mutex.withLock {
        require(supported()) { "当前 Android WebView 不支持安全脚本引擎，请更新系统 WebView 后重试" }
        require(script.toByteArray().size<=1_048_576)
        withTimeout(timeoutMs) {
            val initialState=stateProvider?.invoke() ?: buildJsonObject { put("data",JsonObject(emptyMap()));put("settings",JsonObject(emptyMap())) }
            ComicScriptDataStore.validate(initialState)
            val sandbox=JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext).awaitValue()
            try {
                val required=listOf(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN,JavaScriptSandbox.JS_FEATURE_CONSOLE_MESSAGING,
                    JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION,JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)
                require(required.all(sandbox::isFeatureSupported)) { "系统脚本引擎缺少必要的隔离能力，请更新 WebView" }
                val parameters=IsolateStartupParameters().apply {
                    if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE))
                        setMaxHeapSizeBytes(32L*1024*1024)
                    if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT))
                        setMaxEvaluationReturnSizeBytes(1_048_576)
                }
                val isolate=sandbox.createIsolate(parameters)
                try {
                    coroutineScope {
                        val operation=this;val count=AtomicInteger();val consoleCount=AtomicInteger()
                        if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_CONSOLE_MESSAGING)) isolate.setConsoleCallback(Executor { it.run() },object:JavaScriptConsoleCallback {
                            override fun onConsoleMessage(message:JavaScriptConsoleCallback.ConsoleMessage) {
                                val value=message.message
                                if(value.length>65536 || consoleCount.incrementAndGet()>64) { isolate.close();return }
                                if(!value.startsWith("LR_COMIC_RPC:"))return
                                if(value.length>65536 || count.incrementAndGet()>32) { isolate.close();return }
                                operation.launch(Dispatchers.IO) {
                                    try {
                                        val request=Json.parseToJsonElement(value.removePrefix("LR_COMIC_RPC:")).jsonObject
                                        val id=request.getValue("id").jsonPrimitive.int
                                        val packet=try {
                                            val url=request.getValue("url").jsonPrimitive.content
                                            val headers=(request["headers"] as? JsonObject).orEmpty().mapValues { it.value.jsonPrimitive.content }
                                            val body=request["data"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                                            val binary=if(request["bodyEncoding"]?.jsonPrimitive?.content=="base64")java.util.Base64.getDecoder().decode(body.orEmpty()) else null
                                            val result=network.request(url,domains,request.getValue("method").jsonPrimitive.content,headers,body,bodyBytes=binary)
                                            val contentType=result.headers.entries.firstOrNull { it.key.equals("Content-Type",true) }?.value
                                            val payload=if(request["responseType"]?.jsonPrimitive?.content=="bytes")java.util.Base64.getEncoder().encodeToString(result.bytes)
                                                else com.will.lightreader.source.net.ResponseText.decode(result.bytes,contentType)
                                            buildJsonObject { put("status",result.status);put("body",payload);put("headers",JsonObject(result.headers.mapValues { JsonPrimitive(it.value) })) }
                                        } catch(e:CancellationException) { throw e } catch(e:Exception) {
                                            val message = e.message ?: "源网络请求失败"
                                            buildJsonObject { put("error", if (e is java.io.IOException) "LR_COMIC_NETWORK: $message" else message) }
                                        }
                                        isolate.evaluateJavaScriptAsync("__lrResolve($id,$packet); 'ok'").awaitValue()
                                    } catch(e:CancellationException) { throw e } catch(_:Exception) { isolate.close() }
                                }
                            }
                        })
                        val locale=java.util.Locale.getDefault().toString()
                        val init = if (initialize) "await __lrSource.init?.();" else ""
                        val code="const __lrLocale=${JsonPrimitive(locale)};const __lrInitialState=JSON.parse(${JsonPrimitive(initialState.toString())});\n"+host+"\n"+script+"\nconst __lrSource = new $className();\n(async()=>{$init const result=await ($expression);return JSON.stringify({result,state:__lrExportState()},__lrJsonReplacer);})()"
                        val result=isolate.evaluateJavaScriptAsync(code).awaitValue()
                        val envelope=Json.parseToJsonElement(result).jsonObject
                        val state=envelope.getValue("state").jsonObject.also(ComicScriptDataStore::validate)
                        commitState?.invoke(state)
                        envelope["result"] ?: JsonNull
                    }
                } finally { isolate.close() }
            } finally { sandbox.close() }
        }
    }
    suspend fun inspect(script: String, origin: String): ComicScriptPackage {
        require(script.toByteArray().size in 1..1_048_576) { "源文件须小于 1 MB" }
        val classes=Regex("class\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+extends\\s+ComicSource\\b").findAll(script).toList()
        require(classes.size==1) { "需要一个继承 ComicSource 的 JavaScript 类" }
        val name=classes.single().groupValues[1]
        val metadata=evaluate(script,name,"""({key:__lrSource.key,name:__lrSource.name,version:__lrSource.version,
            domains:__lrSource.domains||[],fallbackServers:__lrSource.constructor.fallbackServers||[],imageUrl:__lrSource.constructor.imageUrl,
            api:__lrSource.lightreaderApiVersion??1,valid:typeof __lrSource.search?.load==='function'&&typeof __lrSource.comic?.loadInfo==='function'&&typeof __lrSource.comic?.loadEp==='function'})""").jsonObject
        require(metadata["valid"]?.jsonPrimitive?.boolean==true) { "源必须提供搜索、详情和章节图片接口" }
        fun text(key:String)=metadata[key]?.jsonPrimitive?.content.orEmpty()
        val declared = (metadata["domains"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content.lowercase() }
        val suggested = if (declared.isEmpty()) {
            val fallbacks = (metadata["fallbackServers"] as? JsonArray).orEmpty().take(16)
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val imageHost = (metadata["imageUrl"] as? JsonPrimitive)?.contentOrNull?.let {
                runCatching { java.net.URI(it).takeIf { url -> url.scheme == "https" }?.host }.getOrNull()
            }
            (fallbacks + listOfNotNull(imageHost)).map { it.lowercase() }.distinct().filter(::validComicDomainPermission).take(16)
        } else declared
        return ComicScriptPackage(text("key"),text("name"),text("version"),name,script,suggested,origin,
            api=metadata["api"]?.jsonPrimitive?.int ?: 1)
    }
}
