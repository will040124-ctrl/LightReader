package com.will.lightreader.source.script

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.File

/** Custom scheme prevents script images escaping the source network policy through Coil redirects. */
class ComicImageFetcher(private val uri:Uri,private val options:Options,private val manager:()->ComicScriptManager):Fetcher {
    override suspend fun fetch()=withContext(Dispatchers.IO) {
        val owner=manager();val pkg=owner.active(requireNotNull(uri.host))
        val url=ComicScriptRoutes.decode(uri.path.orEmpty().trimStart('/'))
        val transform=uri.fragment?.let {
            require(it.length<=5462) { "图片处理脚本无效" }
            ComicScriptRoutes.decode(it)
        }
        owner.network.validate(url,pkg.domains)
        val thumbnail=if(uri.getQueryParameter("thumbnail")=="1")owner.thumbnail(pkg,url) else null
        val headers=thumbnail?.headers ?: options.headers.toMultimap().mapValues { it.value.joinToString(",") }
        val hit=owner.cachedImage(pkg,thumbnail?.url ?: url,headers,transform,
            read=options.diskCachePolicy.readEnabled,write=options.diskCachePolicy.writeEnabled,
            allowNetwork=options.networkCachePolicy.readEnabled)
        val bytes=hit.bytes;val fromDisk=hit.disk
        SourceResult(ImageSource(Buffer().write(bytes),options.context),null,if(fromDisk) DataSource.DISK else DataSource.NETWORK)
    }
    class Factory(private val manager:()->ComicScriptManager):Fetcher.Factory<Uri> {
        override fun create(data:Uri,options:Options,imageLoader:ImageLoader):Fetcher? =
            if(data.scheme=="lrcomic")ComicImageFetcher(data,options,manager) else null
    }
}
