package com.will.lightreader.source.script

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.will.lightreader.source.net.isBackgroundRequest
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun scriptRequestHeaders(headers: Map<String, String>) = headers.filterKeys {
    !it.equals("connection", true) && !it.equals("accept-encoding", true)
}

class ComicScriptNetwork(private val allowLocalTest: Boolean = false) {
    private val limiters=java.util.concurrent.ConcurrentHashMap<String,com.will.lightreader.source.net.RateLimiter>()
    private val foregroundImages=Semaphore(4)
    private val backgroundImages=Semaphore(3)
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10,java.util.concurrent.TimeUnit.SECONDS).readTimeout(15,java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(25,java.util.concurrent.TimeUnit.SECONDS)
        .dns(object: Dns { override fun lookup(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList().also { addresses ->
            if(!(allowLocalTest && host=="127.0.0.1") && addresses.any(::privateAddress))
                throw java.net.UnknownHostException("源不能访问本机或私有网络")
        } }).build()
    private val imageClient=client.newBuilder().dispatcher(Dispatcher().apply { maxRequests=8;maxRequestsPerHost=5 }).build()
    private fun privateAddress(a: InetAddress)=a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress ||
        a.address.size==4 && ((a.address[0].toInt() and 255)==0 || (a.address[0].toInt() and 255)==100 && (a.address[1].toInt() and 255) in 64..127) ||
        a.address.size==16 && (a.address[0].toInt() and 0xfe)==0xfc
    fun validate(url: String, domains: List<String>) {
        require(url.length<=8192) { "源地址过长" }
        val uri=url.toHttpUrl()
        require(uri.isHttps || allowLocalTest && uri.host=="127.0.0.1") { "漫画源仅允许 HTTPS" }
        require(uri.username.isEmpty() && uri.password.isEmpty() && (uri.port==443 || allowLocalTest && uri.host=="127.0.0.1")) { "源地址的端口或用户信息不受支持" }
        require(comicDomainAllowed(uri.host,domains)) { "漫画源未授权域名：${uri.host}" }
        require(uri.host!="localhost" && (allowLocalTest && uri.host=="127.0.0.1" || !uri.host.matches(Regex("[0-9.]+")) && !uri.host.contains(':'))) { "不允许此服务器地址" }
    }
    data class Result(val status: Int,val headers: Map<String,String>,val bytes: ByteArray)
    suspend fun request(url: String, domains: List<String>, method: String="GET", headers: Map<String,String> = emptyMap(), body: String?=null, limit: Long=2L*1024*1024, bodyBytes:ByteArray?=null,image:Boolean=false): Result {
        require(method in setOf("GET","POST")) { "仅支持 GET / POST" }
        require(headers.size<=32 && headers.keys.none { it.lowercase() in setOf("host","content-length","cookie") }) { "请求头不支持" }
        // OkHttp owns connection negotiation and transparent content decompression.
        var current=url;var currentHeaders=scriptRequestHeaders(headers);var verb=method;var data=body;var binary=bodyBytes
        repeat(4) {
            validate(current,domains)
            limiters.getOrPut((if(image)"image:" else "api:")+current.toHttpUrl().host) {
                if(image) com.will.lightreader.source.net.RateLimiter(60,100,150,30) else com.will.lightreader.source.net.RateLimiter()
            }.acquire(currentCoroutineContext().isBackgroundRequest)
            val builder=Request.Builder().url(current)
            currentHeaders.forEach { (key,value)->builder.header(key,value) }
            if(verb=="POST") builder.post((binary ?: (data ?: "").toByteArray()).toRequestBody(null))
            val result=if(image) {
                val slots=if(currentCoroutineContext().isBackgroundRequest)backgroundImages else foregroundImages
                slots.withPermit { execute(builder.build(),limit,true) }
            } else execute(builder.build(),limit)
            if(result.status in setOf(301,302,303,307,308)) {
                val next=result.headers.entries.firstOrNull { it.key.equals("Location",true) }?.value
                    ?.let { current.toHttpUrl().resolve(it)?.toString() } ?: error("重定向无地址")
                validate(next,domains)
                if(next.toHttpUrl().host!=current.toHttpUrl().host)currentHeaders=currentHeaders.filterKeys { it.lowercase() !in setOf("authorization","referer") }
                if(result.status==303 || result.status in setOf(301,302) && verb=="POST") { verb="GET";data=null;binary=null }
                current=next
            } else return result
        }
        error("重定向次数过多")
    }
    private suspend fun execute(request: Request,limit:Long,image:Boolean=false): Result = suspendCancellableCoroutine { continuation ->
        val call=(if(image)imageClient else client).newCall(request);continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:java.io.IOException) { if(continuation.isActive)continuation.resumeWithException(e) }
            override fun onResponse(call:Call,response:Response) {
                try {
                    val result=response.use { r ->
                        val buffer=okio.Buffer();var size=0L
                        if(r.code !in setOf(301,302,303,307,308)) {
                            val source=r.body?.source() ?: error("空响应")
                            while(true) {
                                val n=source.read(buffer,minOf(65536L,limit+1-size));if(n<0)break
                                size+=n;require(size<=limit) { "源响应超过大小限制" }
                            }
                        }
                        Result(r.code,r.headers.toMultimap().mapValues { it.value.joinToString(",") }
                            .filterKeys { !it.equals("set-cookie",true) },buffer.readByteArray())
                    }
                    if(continuation.isActive)continuation.resume(result)
                } catch(e:Exception) { if(continuation.isActive)continuation.resumeWithException(e) }
            }
        })
    }
}
