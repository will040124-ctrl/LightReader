package com.will.lightreader.qa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.will.lightreader.LightReaderApp
import com.will.lightreader.MainActivity
import com.will.lightreader.R
import com.will.lightreader.domain.*
import com.will.lightreader.source.local.*
import kotlinx.coroutines.launch

/** Synthetic library in the independent benchmark package; never included in a user APK. */
class PaperlightQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val repo = (application as LightReaderApp).container.library
            val titles = listOf("月光图书馆", "潮汐来信", "猫与月亮的手札", "银河邮差", "迟到的春天", "雨停以后", "旅途拾光", "星屑与猫", "风中的灯塔", "夏日的回声", "纸上花园", "最后一封信")
            titles.forEachIndexed { index, title ->
                val id = "paperlight-qa-$index"
                val cover = if (index == 0) "android.resource://$packageName/${R.drawable.watercolor_reading_room}" else null
                val brief = BookBrief("local", id, LocalIds.bookUrl(id), title, listOf("白川 栞", "森川 遥", "朝雾 结衣")[index % 3], cover)
                val refs = (0..5).map { ChapterRef(LocalIds.chapterUrl(id, it), "第${it + 1}章 · 夜晚的来信", "第一卷 · 月光落下以前") }
                repo.saveImportedBook(brief, refs, refs.map { ref -> ChapterContent(ref.chapterUrl, ref.title,
                    List(20) { Block.Paragraph("窗外的风掠过树梢，将一片细碎的月光送到桌边。她把书推到我面前，笑着说，今天就读到这里吧。那些藏在书页里的故事，会在明天继续。") }) })
                LocalMetaStore.write(java.io.File(filesDir, "imports/$id"), LocalMeta("原创排版测试文本，用于验证书架转场、目录与阅读体验。", "中文", "TXT"))
            }
            repo.savePosition("local:paperlight-qa-0", LocalIds.chapterUrl("paperlight-qa-0", 1), "第二章 · 夜晚的来信", 2, .3f)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            startActivity(Intent(this@PaperlightQaActivity, MainActivity::class.java))
            finish()
        }
    }
}
