package com.will.lightreader.qa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.will.lightreader.LightReaderApp
import com.will.lightreader.ui.ReadingRequests
import com.will.lightreader.ui.reader.ReaderPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Local original media for filming. Exists only in the isolated benchmark APK. */
class PromoCaptureQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = (application as LightReaderApp).container
        val store = getSharedPreferences("promo_capture", MODE_PRIVATE)
        lifecycleScope.launch {
            try {
                val operation = intent.getStringExtra("operation") ?: "prepare"
                val format = intent.getStringExtra("format") ?: "novel"
                when(operation) {
                    "prepare" -> {
                        withContext(Dispatchers.IO) {
                            val dir = File(cacheDir, "promo-capture-original").apply { mkdirs() }
                            val paths = linkedMapOf("novel" to "/page-journey/assets/source/promo.epub",
                                "comic" to "/page-journey/assets/source/promo.cbz",
                                "pdf" to "/watercolor-preview-v2/assets/earth-at-night.pdf")
                            for(i in 1..5) paths["novel-$i"]="/page-journey/assets/source/novel-$i.epub"
                            for ((type,path) in paths) {
                                val target = File(dir, path.substringAfterLast('/'))
                                java.net.URL("http://127.0.0.1:8781$path").openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                                val result = app.localImporter.import(Uri.fromFile(target))
                                store.edit().putString(type, result.bookKey).commit()
                                val refs = app.library.chapters(result.bookKey)
                                val chapter = refs[if(type=="pdf") 40.coerceAtMost(refs.lastIndex) else 0]
                                app.library.savePosition(result.bookKey,chapter.key,chapter.title,0,0f)
                                getSharedPreferences("reader_prefs",MODE_PRIVATE).edit().putString("comic-mode:${result.bookKey}","paged").commit()
                            }
                            val demo=getSharedPreferences("reader_formats_qa",MODE_PRIVATE).edit()
                            listOf("epub3","txt","html","md","epub2").forEachIndexed { index,name ->
                                demo.putString(name,store.getString(if(index==0)"novel" else "novel-$index",null))
                            }
                            demo.commit()
                            app.library.touchRead(requireNotNull(store.getString("novel", null)))
                        }
                        ReaderPrefs(this@PromoCaptureQaActivity).apply {
                            fontSize=18;lineHeight=1.7f;paper="sepia";immersive=true;hideStatusBar=true;twoPage=false;shelfStartsClosed=true
                        }
                        getSharedPreferences("reader_prefs",MODE_PRIVATE).edit().putString("turnStyle","slide").commit()
                        result("PASS original novel, comic and PDF ready")
                    }
                    "open", "reset" -> {
                        val key=requireNotNull(store.getString(format,null))
                        if(operation=="reset") withContext(Dispatchers.IO) {
                            val refs=app.library.chapters(key)
                            val chapter=refs[intent.getIntExtra("chapter",if(format=="pdf")40 else 0).coerceIn(refs.indices)]
                            app.library.savePosition(key,chapter.key,chapter.title,0,0f)
                        }
                        startActivity(Intent(this@PromoCaptureQaActivity,com.will.lightreader.MainActivity::class.java)
                            .setAction(ReadingRequests.ACTION).putExtra(ReadingRequests.EXTRA_BOOK,key))
                        finish()
                    }
                    else -> result("FAIL unknown operation")
                }
            } catch(e:Exception) {android.util.Log.e("PromoCaptureQA","FAIL",e);result("FAIL ${e.message}")}
        }
    }
    private fun result(value:String) {
        android.util.Log.i("PromoCaptureQA",value)
        setContentView(android.widget.TextView(this).apply{text=value;textSize=20f})
    }
}
