package com.will.lightreader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.will.lightreader.di.AppContainer
import com.will.lightreader.source.net.HttpClient
import okhttp3.OkHttpClient

class LightReaderApp : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }
    internal val solidShelfTextures by lazy { com.will.lightreader.ui.shelf.SolidShelfTextures.load(this) }
    override fun onCreate() {
        super.onCreate()
        com.will.lightreader.widget.ReadingWidgetUpdates.startIfPresent(this)
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.will.lightreader.widget.ReadingWidgetUpdates.refreshIfPresent(this)
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.will.lightreader.ui.reader.PdfPageImages.trim()
            com.will.lightreader.ui.reader.readingLayouts.clear()
        }
    }

    /** Coil 全局图片加载器：图床防盗链按 Referer 放行，按图片域名给对应书站 Referer。
     *  性能：显式配内存+磁盘缓存（封面/插图不重下载、离线秒开），封面按需解码。 */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(com.will.lightreader.source.script.ComicImageFetcher.Factory { container.comicScripts }) }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request()
                        val host = req.url.host
                        val referer = when {
                            host.contains("wenku8") -> "https://www.wenku8.net/"
                            else -> null
                        }
                        val builder = req.newBuilder().header("User-Agent", HttpClient.UA)
                        if (referer != null) builder.header("Referer", referer)
                        chain.proceed(builder.build())
                    }
                    .build()
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizeBytes(com.will.lightreader.media.imageMemoryCacheBytes(
                        getSystemService(android.app.ActivityManager::class.java)?.memoryClass ?: 128))
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(java.io.File(cacheDir, "image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)   // 250MB 磁盘缓存
                    .build()
            }
            .crossfade(true)   // 图片加载淡入，不闪
            .build()
}
