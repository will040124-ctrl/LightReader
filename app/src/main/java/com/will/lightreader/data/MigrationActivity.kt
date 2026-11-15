package com.will.lightreader.data

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.security.MessageDigest

/** A narrow, certificate-pinned transfer between the two installed packages. No public backup. */
class MigrationActivity:Activity() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var status:TextView
    private lateinit var action:Button
    private var running=false
    private val legacy="com.will.lightreader"
    private val stable="$legacy.stable"
    private val legacyCert="4604f70b88a5c335c096d18aed6072400d0665210077a36655c05a31d3744dcc"
    private val stableCert="2a77017e42c4d291e18801e0f686f31f3de2f565de72731540515606342dc7df"
    @Suppress("DEPRECATION") private fun verified(name:String,certificate:String):Boolean=runCatching {
        val info=packageManager.getPackageInfo(name,android.content.pm.PackageManager.GET_SIGNATURES)
        val signatures=info.signatures.orEmpty()
        signatures.size==1 && MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
            .joinToString("") { "%02x".format(it) }==certificate
    }.getOrDefault(false)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val receiving=packageName==stable
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(32,56,32,32);setBackgroundColor(0xfff5f2eb.toInt())
        }
        layout.addView(TextView(this).apply { text="轻阅 · 正式版迁移";textSize=27f;setTextColor(0xff39425a.toInt()) })
        status=TextView(this).apply { textSize=17f;setTextColor(0xff53566a.toInt());setPadding(0,32,0,24) }
        layout.addView(status)
        action=Button(this);layout.addView(action);setContentView(layout)
        if(receiving) {
            val uri=intent.data
            val trusted=callingActivity?.packageName==legacy && verified(legacy,legacyCert) &&
                uri?.scheme=="content" && uri.authority=="$legacy.migration"
            if(!trusted) { status.text="请从旧版「设置 → 备份与存储 → 迁移到正式版」发起。";action.text="关闭";action.setOnClickListener { finish() };return }
            status.text="将在空的正式版中恢复书籍、离线文件、记录、偏好及已安装源。旧版保持完整。迁移数据仅在设备内传递。\n\n迁移时请保持屏幕亮着，暂停其他下载。"
            action.text="校验并迁移"
            action.setOnClickListener { work {
                val result=FullMigration(this).import(requireNotNull(uri),::progress)
                status.text=result;action.isEnabled=true;action.text="完成并关闭正式版"
                action.setOnClickListener {
                    setResult(RESULT_OK);finish()
                    android.os.Handler(mainLooper).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) },300)
                }
            } }
        } else {
            status.text="先安装独立签名的「轻阅」，再点击下方按钮。原书架、本地书籍及下载都会保留在旧版。\n\n请先暂停下载并返回书架；迁移结束前不要阅读或修改设置。正式版已有数据时会拒绝覆盖。"
            action.text="迁移到正式版"
            action.setOnClickListener { work {
                require(packageName==legacy && verified(stable,stableCert)) { "未安装匹配签名的新版轻阅，请先安装正式 APK" }
                val file=FullMigration(this).export(::progress)
                val uri=FileProvider.getUriForFile(this,"$legacy.migration",file)
                val next=Intent().setClassName(stable,MigrationActivity::class.java.name).setDataAndType(uri,"application/zip")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                next.clipData=android.content.ClipData.newRawUri("LightReader private transfer",uri)
                @Suppress("DEPRECATION") startActivityForResult(next,71)
                status.text="迁移包已传给正式版，请在正式版中完成校验。旧版数据仍完整保留。"
                action.text="重新生成迁移包";action.isEnabled=true
            } }
        }
    }
    private fun progress(message:String) { runOnUiThread { status.text=message } }
    private fun work(block:suspend ()->Unit) {
        if(running)return;running=true;action.isEnabled=false
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scope.launch {
            try { block() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { status.text="迁移未完成：${e.message?.take(180) ?: "请重试"}\n旧版数据仍保留。";action.isEnabled=true }
            finally { running=false;window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }
    @Deprecated("Migration result") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==71 && resultCode==RESULT_OK) {
            java.io.File(cacheDir,"migration-transfer/library.zip").delete()
            status.text="迁移已完成。旧版仍可作为回退，请重新打开新版轻阅核对书架和阅读位置。"
        }
    }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
