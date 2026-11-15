package com.will.lightreader.ui.settings

import com.will.lightreader.ui.components.paperFeedback
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.will.lightreader.ui.components.watercolorSurface
import com.will.lightreader.ui.components.watercolorPaper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.text.font.FontFamily
import com.will.lightreader.ui.components.watercolorWash
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.lightreader.R
import com.will.lightreader.di.AppContainer
import com.will.lightreader.ui.components.TonalPanel
import com.will.lightreader.ui.components.pressScale
import com.will.lightreader.ui.reader.ReaderPrefs
import com.will.lightreader.ui.reader.ShelfLayoutMode
import com.will.lightreader.ui.theme.AppIcons
import com.will.lightreader.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * 设置：猫娘头图 + 阅读偏好 / 自定义背景 / 存储 / 关于。
 * 分组卡依次错峰进场，头图用方形裁切，左侧文字区与插画底自然衔接。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: (() -> Unit)? = null, focusReading: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { ReaderPrefs(context) }
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(if (focusReading) "reading" else null) }
    val sectionStates = rememberSaveableStateHolder()
    BackHandler(section != null && !focusReading) { section = null }
    val dark = com.will.lightreader.ui.theme.LocalAppDark.current
    val appearance = remember { com.will.lightreader.ui.theme.AppAppearance(context) }
    val appearanceMode = com.will.lightreader.ui.theme.rememberAppearanceMode()
    var bgPath by remember { mutableStateOf(prefs.bgUri) }
    var paper by remember { mutableStateOf(prefs.paper) }
    var fontSize by remember { mutableStateOf(prefs.fontSize) }
    var layout by remember { mutableStateOf(prefs.shelfLayoutMode) }
    var serif by remember { mutableStateOf(prefs.serif) }
    var lineHeight by remember { mutableStateOf(prefs.lineHeight) }
    var twoPage by remember { mutableStateOf(prefs.twoPage) }
    var turnStyle by remember { mutableStateOf(prefs.turnStyle) }
    var comicDefaultMode by remember { mutableStateOf(prefs.defaultComicMode) }
    DisposableEffect(context) {
        val store=context.getSharedPreferences("reader_prefs",android.content.Context.MODE_PRIVATE)
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
            if(key=="comic_mode_default")comicDefaultMode=prefs.defaultComicMode
        }
        store.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var comicRtl by remember { mutableStateOf(prefs.comicRtl) }
    var immersive by remember { mutableStateOf(prefs.immersive) }
    var hideStatusBar by remember { mutableStateOf(prefs.hideStatusBar) }
    var shelfArtwork by remember { mutableStateOf(prefs.shelfArtwork) }
    var shelfWheel by remember { mutableStateOf(prefs.shelfWheelEnabled) }
    var shelfClosed by remember { mutableStateOf(prefs.shelfStartsClosed) }
    var widgetDetails by remember { mutableStateOf(prefs.widgetShowDetails) }
    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var downloadsTick by remember { mutableStateOf(0) }   // 清理后刷新下载概览

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val file = java.io.File(context.filesDir, "bg.jpg")
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
                prefs.bgUri = file.absolutePath
                bgPath = file.absolutePath
                Toast.makeText(context, "已更换阅读背景", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "背景设置失败，请重新选择", Toast.LENGTH_SHORT).show()
            }
        }
    }

    com.will.lightreader.ui.SystemBarsAppearance(lightContent = dark)
    val reduced = Motion.reducedMotion()
    AnimatedContent(targetState = section, modifier = Modifier.fillMaxSize(), label = "settings-category",
        transitionSpec = {
            val entering = if (targetState != null) 1 else -1
            (fadeIn(tween(if (reduced) 0 else 180, delayMillis = if (reduced) 0 else 40)) +
                slideInHorizontally(tween(if (reduced) 0 else 240, easing = Motion.Decelerate)) { if (reduced) 0 else entering * (it / 24) }) togetherWith
                (fadeOut(tween(if (reduced) 0 else 90)) +
                    slideOutHorizontally(tween(if (reduced) 0 else 180, easing = Motion.Decelerate)) { if (reduced) 0 else -entering * (it / 40) })
        }) { displayedSection ->
    sectionStates.SaveableStateProvider(displayedSection ?: "overview") {
    Column(Modifier.fillMaxSize().statusBarsPadding()
        .verticalScroll(rememberScrollState())) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()
            .align(Alignment.CenterHorizontally).padding(horizontal = 16.dp)) {
            if (displayedSection != null || onBack != null) {
                androidx.compose.material3.TextButton(onClick = {
                    if (focusReading && onBack != null) onBack()
                    else if (displayedSection != null) section = null
                    else onBack?.invoke()
                }, modifier = Modifier.padding(top = 6.dp)) {
                    Text(if (focusReading && onBack != null) "‹ 返回阅读" else "‹ 全部设置")
                }
            }
            val category = SettingsCategory.entries.firstOrNull { it.key == displayedSection }
            MascotBanner(category?.title ?: "设置", category?.hint ?: "外观、阅读与内容管理", displayedSection == null)
            if (displayedSection == null) {
                SettingsCategory.entries.forEach { item ->
                    val summary = when (item.key) {
                        "appearance" -> "${appearanceMode.label} · ${if (shelfClosed) "实体书架" else "直接浏览"}"
                        "reading" -> "${fontSize} 号 · ${if (serif) "宋体" else "黑体"} · ${if (immersive) "沉浸阅读" else "显示导航栏"}"
                        "comic" -> "${if (comicDefaultMode == "scroll") "连续下滑" else "翻页阅读"} · ${if (comicRtl) "从右向左" else "从左向右"}"
                        else -> item.hint
                    }
                    SettingsCategoryRow(item, summary) { section = item.key }
                }
            }

            if (displayedSection == "appearance") Group("外观", AppIcons.Sparkle) {
                PreferenceRow("应用主题", "阅读纸色可独立设置") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.will.lightreader.ui.theme.AppearanceMode.entries.forEach { mode ->
                            CatBtn(mode.label, active = appearanceMode == mode) { appearance.mode = mode }
                        }
                    }
                }
            }
            if (displayedSection == "appearance") Group("书架个性化", AppIcons.Sparkle) {
                PreferenceRow("打开书架时", "点一次抽出书籍，再点进入详情；也可展开为网格或列表") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("实体书架", active = shelfClosed) { shelfClosed = true; prefs.shelfStartsClosed = true }
                        CatBtn("直接浏览", active = !shelfClosed) { shelfClosed = false; prefs.shelfStartsClosed = false }
                    }
                }
                PreferenceRow("续读卡片配图", "封面随最近阅读更新；封面缺失时显示水彩插画") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.will.lightreader.ui.shelf.ShelfArtwork.entries.forEach { mode ->
                            CatBtn(mode.label, active = shelfArtwork == mode) { shelfArtwork = mode; prefs.shelfArtwork = mode }
                        }
                    }
                }
                PreferenceRow("列表滚轮动效", "系统减少动画设置优先") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("开启", active = shelfWheel) { shelfWheel = true; prefs.shelfWheelEnabled = true }
                        CatBtn("关闭", active = !shelfWheel) { shelfWheel = false; prefs.shelfWheelEnabled = false }
                    }
                }
                PreferenceRow("书架布局", "自动：手机列表，平板书封") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShelfLayoutMode.entries.forEach { mode ->
                            CatBtn(when(mode) { ShelfLayoutMode.AUTO -> "自动"; ShelfLayoutMode.LIST -> "列表模式"; else -> "网格模式" },
                                active = layout == mode) { layout = mode; prefs.shelfLayoutMode = mode }
                        }
                    }
                }
            }
            if (displayedSection == "appearance") Group("桌面小组件", AppIcons.Bookmark) {
                PreferenceRow("桌面阅读信息", "小组件配图跟随续读卡片；隐藏信息后不展示书名、章节、封面或进度") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("显示阅读信息", active = widgetDetails) { widgetDetails = true; prefs.widgetShowDetails = true }
                        CatBtn("隐藏阅读信息", active = !widgetDetails) { widgetDetails = false; prefs.widgetShowDetails = false }
                    }
                }
                Spacer(Modifier.height(10.dp))
                CatBtn("添加续读小组件") {
                    val manager = android.appwidget.AppWidgetManager.getInstance(context)
                    if (manager.isRequestPinAppWidgetSupported) {
                        val accepted = manager.requestPinAppWidget(android.content.ComponentName(context,
                            com.will.lightreader.widget.ContinueReadingWidget::class.java), null, null)
                        Toast.makeText(context, if (accepted) "请在桌面弹窗中确认添加" else "请长按桌面，从小组件中选择轻阅", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(context, "请长按桌面，从小组件中选择轻阅", Toast.LENGTH_LONG).show()
                }
            }
            if (displayedSection == "reading") Box(Modifier.fillMaxWidth()) {
            Group("阅读", AppIcons.Book) {
                ReadingSample(fontSize, lineHeight, serif, paper, dark)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    com.will.lightreader.ui.reader.ReaderPreferenceSlider("字号",fontSize.toFloat(),13f..32f,18,
                        MaterialTheme.colorScheme.onSurface,MaterialTheme.colorScheme.primary,
                        format={ "${kotlin.math.round(it).toInt()}" },onCommit={fontSize=kotlin.math.round(it).toInt();prefs.fontSize=fontSize})
                }
                PreferenceRow("正文字体") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("宋体", active = serif) { serif = true; prefs.serif = true }
                        CatBtn("黑体", active = !serif) { serif = false; prefs.serif = false }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    com.will.lightreader.ui.reader.ReaderPreferenceSlider("行距",lineHeight,1.4f..2.4f,19,
                        MaterialTheme.colorScheme.onSurface,MaterialTheme.colorScheme.primary,
                        format={String.format(java.util.Locale.ROOT,"%.2f 倍",it)},
                        onCommit={lineHeight=kotlin.math.round(it*20f)/20f;prefs.lineHeight=lineHeight})
                }
                PreferenceRow("纸色", "跟随外观会使用应用主题") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("follow" to "跟随外观", "light" to "纯白", "sepia" to "米黄", "dark" to "夜读").forEach { (id, label) ->
                            CatBtn(label, active = paper == id) { paper = id; prefs.paper = id }
                        }
                    }
                }
                PreferenceRow("沉浸阅读", "小说、漫画与 PDF 共用；隐藏系统导航栏，边缘滑动可临时唤出") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("开启", active = immersive) { immersive = true; prefs.immersive = true }
                        CatBtn("关闭", active = !immersive) { immersive = false; prefs.immersive = false }
                    }
                }
                if (immersive) PreferenceRow("顶部状态栏", "沉浸阅读时可一并隐藏时间与系统图标") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("显示", active = !hideStatusBar) { hideStatusBar = false; prefs.hideStatusBar = false }
                        CatBtn("隐藏", active = hideStatusBar) { hideStatusBar = true; prefs.hideStatusBar = true }
                    }
                }
                PreferenceRow("小说排版", "宽屏下可用双页显示，保持当前文字位置") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("单页", active = !twoPage) { twoPage = false; prefs.twoPage = false }
                        CatBtn("双页", active = twoPage) { twoPage = true; prefs.twoPage = true }
                    }
                }
                PreferenceRow("翻页效果", "跟随手势滑动，系统减少动画设置优先") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("平移", active = turnStyle == "slide") { turnStyle = "slide"; prefs.turnStyle = "slide" }
                        CatBtn("覆盖", active = turnStyle == "cover") { turnStyle = "cover"; prefs.turnStyle = "cover" }
                    }
                }

            }

            }
            if (displayedSection == "comic") Group("漫画阅读", AppIcons.Book) {
                PreferenceRow("默认阅读方式", "每本漫画会优先使用在阅读时选择的模式") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("翻页阅读", active = comicDefaultMode == "paged") { comicDefaultMode = "paged"; prefs.defaultComicMode = "paged" }
                        CatBtn("连续下滑", active = comicDefaultMode == "scroll") { comicDefaultMode = "scroll"; prefs.defaultComicMode = "scroll" }
                    }
                }
                PreferenceRow("翻页方向") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatBtn("从左向右", active = !comicRtl) { comicRtl = false; prefs.comicRtl = false }
                        CatBtn("从右向左", active = comicRtl) { comicRtl = true; prefs.comicRtl = true }
                    }
                }

            }

            // ---------- 背景 ----------
            if (displayedSection == "appearance") Group("背景", AppIcons.Sparkle, order = 1) {
                Text("选择阅读背景，正文会自动叠加底色以保证可读性。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatBtn("选一张背景图") {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    CatBtn("恢复默认", active = bgPath.isNotBlank()) {
                        prefs.bgUri = ""
                        bgPath = ""
                        java.io.File(context.filesDir, "bg.jpg").delete()
                    }
                }
            }

            if (displayedSection == "sources") Group("漫画源管理", AppIcons.Compass) { ComicSourcesPanel(container.comicScripts) }
            if (displayedSection == "sources") Group("网站书架导入", AppIcons.Compass) {
                AccountLibraryPanel(container.accountSources)
            }
            if (displayedSection == "data") Group("备份与恢复", AppIcons.Bookmark) {
                if(com.will.lightreader.BuildConfig.APPLICATION_ID=="com.will.lightreader") {
                    com.will.lightreader.ui.components.WatercolorAction("迁移到正式版",AppIcons.ImportBook) {
                        context.startActivity(android.content.Intent(context,com.will.lightreader.data.MigrationActivity::class.java))
                    }
                    Text("独立签名迁移会保留旧版，并在设备内校验和转移完整数据。",style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
                RecordsBackupPanel {
                    fontSize=prefs.fontSize;lineHeight=prefs.lineHeight;serif=prefs.serif
                    paper=prefs.paper;layout=prefs.shelfLayoutMode;twoPage=prefs.twoPage;turnStyle=prefs.turnStyle
                    immersive=prefs.immersive;hideStatusBar=prefs.hideStatusBar
                    comicDefaultMode=prefs.defaultComicMode;comicRtl=prefs.comicRtl;shelfClosed=prefs.shelfStartsClosed
                    shelfArtwork=prefs.shelfArtwork;shelfWheel=prefs.shelfWheelEnabled;widgetDetails=prefs.widgetShowDetails
                }
            }

            // ---------- 存储（全局下载管理） ----------
            if (displayedSection == "data") Group("存储", AppIcons.Download, order = 2) {
                // 每书下载占用一览
                var stats by remember { mutableStateOf<List<com.will.lightreader.data.LibraryRepository.BookDownloadStat>>(emptyList()) }
                LaunchedEffect(downloadsTick) {
                    stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        container.library.downloadsOverview()
                    }
                }
                if (stats.isEmpty()) {
                    Text("还没有离线内容。在书籍详情页下载后，就能离线阅读。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    stats.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                Text("${s.chapters} 章 · ${formatBytes(s.bytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            CatBtn("清理") {
                                if (!actionBusy) confirmation = SettingsConfirmation("清理这本书的下载？", "仅清理这本书的在线下载缓存，书架、进度和本地导入文件保留。") {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        container.library.clearBookDownloads(s.bookKey)
                                    }
                                    downloadsTick++
                                    Toast.makeText(context, "《${s.title}》的离线内容已清除", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("清除在线章节缓存", style = MaterialTheme.typography.bodyLarge)
                        Text("删除在线正文，封面和图片缓存保留（书架、进度与本地书保留）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CatBtn("清除") {
                        if (!actionBusy) confirmation = SettingsConfirmation("清除在线章节缓存？", "在线下载的正文将被删除；封面和图片缓存、书架、阅读进度与本地导入书籍保留。") {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                container.library.clearDownloads()
                            }
                            downloadsTick++
                            Toast.makeText(context, "已清除缓存", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("清除阅读进度", style = MaterialTheme.typography.bodyLarge)
                        Text("清掉每本书的阅读位置（书架与下载保留）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CatBtn("清除") {
                        if (!actionBusy) confirmation = SettingsConfirmation("清除全部阅读记录？", "将删除所有书籍的已保存阅读位置。建议先导出记录备份；书架和下载保留。") {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                container.library.clearHistory()
                            }
                            Toast.makeText(context, "已清除阅读进度", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("清空书架", style = MaterialTheme.typography.bodyLarge)
                        Text("移除全部收藏书（下载缓存保留）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CatBtn("清空") {
                        if (!actionBusy) confirmation = SettingsConfirmation("清空整个书架？", "将移除全部书架条目；原下载文件保留。建议先导出记录备份，以便重新找回书籍。") {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                container.library.clearShelf()
                            }
                            downloadsTick++
                            Toast.makeText(context, "已清空书架", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // ---------- 关于 ----------
            if (displayedSection == "about") Group("关于", AppIcons.Person, order = 3) {
                Text("轻阅 LightReader", style = MaterialTheme.typography.titleMedium)
                Text("版本 ${com.will.lightreader.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("仅供个人阅读学习使用，请尊重来源站点条款与版权。不绕过付费与防护。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    }
    }
    confirmation?.let { pending ->
        androidx.compose.material3.AlertDialog(onDismissRequest={if(!actionBusy)confirmation=null},
            title={Text(pending.title)},text={Text(pending.detail)},
            confirmButton={androidx.compose.material3.TextButton(enabled=!actionBusy,onClick={
                scope.launch {actionBusy=true
                    try {pending.action();confirmation=null}
                    catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(e:Exception){confirmation=null;actionMessage="操作未完成，请重试。${e.message?.take(80) ?: ""}"}
                    finally{actionBusy=false}
                }
            }){Text(if(actionBusy)"处理中…" else "确认操作",color=MaterialTheme.colorScheme.error)}},
            dismissButton={androidx.compose.material3.TextButton(enabled=!actionBusy,onClick={confirmation=null}){Text("取消")}})
    }
    actionMessage?.let { message -> androidx.compose.material3.AlertDialog(onDismissRequest={actionMessage=null},
        title={Text("操作结果")},text={Text(message)},confirmButton={androidx.compose.material3.TextButton(onClick={actionMessage=null}){Text("知道了")}}) }

}

/**
 * 猫娘头图：右侧方形裁切常驻，左侧标题区与面板同色。
 * 图片左缘叠一层同色横向渐变，抹掉插画底与面板色的接缝。
 */
@Composable
private fun MascotBanner(title: String, subtitle: String, companion: Boolean) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    Row(Modifier.fillMaxWidth().padding(top = if (companion) 16.dp else 8.dp, bottom = if (companion) 20.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (companion) com.will.lightreader.ui.components.WatercolorHeading(title, subtitle)
            else {
                Text(title, Modifier.watercolorWash(MaterialTheme.colorScheme.primary, .16f),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif))
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

        }
        if (companion) com.will.lightreader.ui.components.WatercolorCompanion(waving = !dark, modifier = Modifier.size(60.dp))
    }
}

/** 字节数人性化（下载占用显示）。 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * 分组卡：图标 + 标题，首次组合时按 [order] 错峰淡入上浮。
 * 减少动态效果时只保留淡入，不去位移、不排延迟。
 */
@Composable
private fun Group(
    title: String,
    icon: ImageVector,
    order: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)
        .watercolorPaper(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primary).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(10.dp))
        com.will.lightreader.ui.components.WatercolorRule()
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun CatBtn(text: String, active: Boolean = false, onClick: () -> Unit) {
    com.will.lightreader.ui.components.WatercolorChoice(text, active, onClick = onClick)
}

/** Actual preferences drive the sample, including independent night paper. */
@Composable
private fun ReadingSample(size: Int, spacing: Float, serif: Boolean, paper: String, appDark: Boolean) {
    val night = paper == "dark" || (paper == "follow" && appDark)
    val base = when { night -> Color(0xFF292D38); paper == "sepia" -> Color(0xFFF0E6D6); else -> Color(0xFFFCFAF5) }
    val ink = if (night) Color(0xFFDFDCD5) else Color(0xFF414454)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 28.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
        .watercolorSurface(base, ink, .035f).padding(20.dp)) {
        Text("此刻的阅读模样", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .7f))
        Spacer(Modifier.height(12.dp))
        Text("窗外的风掠过树梢，将一片细碎的月光送到桌边。故事，就从这一页继续。",
            maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = ink, fontSize = size.sp, lineHeight = (size * spacing).sp,
            fontFamily = if (serif) androidx.compose.ui.text.font.FontFamily.Serif else androidx.compose.ui.text.font.FontFamily.SansSerif)
    }
}

/** One alignment system: tablet label column, phone stacked controls; all choices share an origin. */
@Composable
private fun PreferenceRow(label: String, description: String? = null, controls: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        val labelContent: @Composable () -> Unit = {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
        if (maxWidth >= 560.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.width(180.dp).padding(top = 8.dp, end = 20.dp)) { labelContent() }
                Box(Modifier.weight(1f)) { controls() }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                labelContent()
                Spacer(Modifier.height(10.dp))
                controls()
            }
        }
    }
}

private data class SettingsConfirmation(val title: String, val detail: String, val action: suspend () -> Unit)

private enum class SettingsCategory(val key: String, val title: String, val hint: String, val icon: ImageVector) {
    APPEARANCE("appearance", "外观与书架", "主题、书架、看板娘与桌面小组件", AppIcons.Sparkle),
    READING("reading", "阅读与显示", "排版、纸色、翻页与沉浸阅读", AppIcons.Book),
    COMIC("comic", "漫画阅读", "默认阅读方式与翻页方向", AppIcons.Book),
    SOURCES("sources", "内容来源", "漫画源管理与网站书架导入", AppIcons.Compass),
    DATA("data", "备份与存储", "阅读记录备份、下载与缓存管理", AppIcons.Download),
    ABOUT("about", "关于轻阅", "应用信息与使用说明", AppIcons.Person),
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory, summary: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = com.will.lightreader.ui.anim.rememberHapticTap()
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(22.dp))
        .paperFeedback(interaction, .992f)
        .clickable(interactionSource = interaction, indication = null) { haptic(); onClick() }
        .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.watercolorWash(MaterialTheme.colorScheme.primary, .16f).padding(8.dp).size(23.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(category.title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurface)
            Text(summary, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
    }
}
