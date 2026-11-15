package com.will.lightreader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.*
import android.os.Bundle
import android.widget.RemoteViews
import androidx.room.withTransaction
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.will.lightreader.MainActivity
import com.will.lightreader.R
import com.will.lightreader.data.LightReaderDb
import com.will.lightreader.ui.ReadingRequests
import com.will.lightreader.ui.reader.ReaderPrefs
import com.will.lightreader.ui.shelf.ShelfArtwork
import com.will.lightreader.ui.theme.AppAppearance
import com.will.lightreader.ui.theme.AppearanceMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import java.util.concurrent.atomic.AtomicBoolean

class ContinueReadingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
    override fun onEnabled(context: Context) { ReadingWidgetUpdates.startIfPresent(context); refresh(context) }
    override fun onDisabled(context: Context) { ReadingWidgetUpdates.stop() }
    private fun refresh(context: Context) {
        ReadingWidgetUpdates.startIfPresent(context)
        val pending = goAsync()
        ReadingWidgetUpdates.scope.launch {
            try { withTimeout(7_000) { ReadingWidgetUpdates.render(context.applicationContext) } }
            catch(_: Exception) { /* Preserve the last valid widget if an update cannot complete. */ }
            finally { pending.finish() }
        }
    }
}

/** Event driven: no polling/network fetches from the launcher, no permanent background service. */
object ReadingWidgetUpdates {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val lock = kotlinx.coroutines.sync.Mutex()
    private var job: Job? = null
    private var readerStore: SharedPreferences? = null
    private var appearanceStore: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startIfPresent(context: Context) {
        val app = context.applicationContext
        if (ids(app).isEmpty() || !started.compareAndSet(false, true)) return
        readerStore = app.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        appearanceStore = app.getSharedPreferences("app_appearance", Context.MODE_PRIVATE)
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf("shelf_artwork", "widget_details", "mode")) scope.launch { safelyRender(app) }
        }.also { l -> readerStore!!.registerOnSharedPreferenceChangeListener(l); appearanceStore!!.registerOnSharedPreferenceChangeListener(l) }
        job = scope.launch {
            try {
                val db = LightReaderDb.get(app)
                combine(db.bookDao().observeAll(), db.positionDao().observeAll()) { _, _ -> Unit }
                    .debounce(350).collect { safelyRender(app) }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { stop() }
        }
    }
    fun refreshIfPresent(context: Context) {
        if (ids(context).isNotEmpty()) scope.launch { safelyRender(context.applicationContext) }
    }
    fun stop() {
        job?.cancel(); job = null
        listener?.let { readerStore?.unregisterOnSharedPreferenceChangeListener(it); appearanceStore?.unregisterOnSharedPreferenceChangeListener(it) }
        listener = null; readerStore = null; appearanceStore = null; started.set(false)
    }
    private suspend fun safelyRender(context: Context) {
        try { render(context) } catch(e: CancellationException) { throw e } catch(_: Exception) { /* Keep the previous valid widget on an interrupted update. */ }
    }
    suspend fun render(context: Context) {
        // Serialize observer/resize updates so an older render cannot win a race.
        lock.lock()
        try {
            val ids = ids(context); if (ids.isEmpty()) return
            val prefs = ReaderPrefs(context); val db = LightReaderDb.get(context)
            val state = db.withTransaction {
                val books = db.bookDao().snapshot(); val positions = db.positionDao().snapshot()
                val keys = books.map { it.key }.toSet()
                val latest = positions.filter { it.bookKey in keys }.maxByOrNull { it.updatedAt }
                continueWidgetState(books, positions, latest?.let { db.chapterDao().byBook(it.bookKey) }.orEmpty(), prefs.widgetShowDetails)
            }
            val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val dark = AppAppearance(context).mode.isDark(systemDark)
            val cover = if (prefs.shelfArtwork == ShelfArtwork.LAST_COVER && prefs.widgetShowDetails) state.coverUrl else null
            // Cache only: reading an already cached cover must not trigger login or networking.
            val drawable = if (!cover.isNullOrBlank()) Coil.imageLoader(context).execute(ImageRequest.Builder(context)
                .data(cover).size(240, 320).allowHardware(false).networkCachePolicy(CachePolicy.DISABLED).build()).drawable else null
            val bitmap = thumbnail(drawable ?: context.getDrawable(R.drawable.watercolor_read)!!)
            val intent = Intent(context, MainActivity::class.java).setAction(ReadingRequests.ACTION)
                .putExtra(ReadingRequests.EXTRA_BOOK, state.bookKey.orEmpty())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_continue)
                views.setImageViewBitmap(R.id.widget_background, background(dark))
                views.setImageViewBitmap(R.id.widget_cover, bitmap)
                val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
                val spacious = width >= 340 && height >= 180
                views.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, if (spacious) 22f else 17f)
                views.setTextViewTextSize(R.id.widget_chapter, android.util.TypedValue.COMPLEX_UNIT_SP, if (spacious) 14f else 12f)
                views.setTextViewTextSize(R.id.widget_action, android.util.TypedValue.COMPLEX_UNIT_SP, if (spacious) 14f else 12f)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    views.setViewLayoutWidth(R.id.widget_cover, if (spacious) 104f else 76f, android.util.TypedValue.COMPLEX_UNIT_DIP)
                }
                views.setViewVisibility(R.id.widget_cover, if (width in 1..239) android.view.View.GONE else android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_brand, if (height in 1..139) android.view.View.GONE else android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_chapter, if (height in 1..139) android.view.View.GONE else android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_title, state.title)
                views.setTextViewText(R.id.widget_chapter, state.chapter)
                views.setTextViewText(R.id.widget_action, if (state.bookKey == null) "打开书架  ↗" else "继续阅读  ↗")
                views.setTextViewText(R.id.widget_progress, state.progress?.let { "$it%" }.orEmpty())
                views.setTextColor(R.id.widget_title, Color.parseColor(if (dark) "#E8E1D6" else "#3D455E"))
                views.setTextColor(R.id.widget_chapter, Color.parseColor(if (dark) "#B8B2BE" else "#817B8D"))
                views.setTextColor(R.id.widget_action, Color.parseColor(if (dark) "#C8B2BD" else "#8B6371"))
                views.setTextColor(R.id.widget_progress, Color.parseColor(if (dark) "#B8B2BE" else "#817B8D"))
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                AppWidgetManager.getInstance(context).updateAppWidget(id, views)
            }
        } finally { lock.unlock() }
    }
    private fun thumbnail(drawable: android.graphics.drawable.Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        val width = drawable.intrinsicWidth.coerceAtLeast(1); val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = minOf(240f / width, 320f / height)
        val w = (width * scale).toInt(); val h = (height * scale).toInt()
        drawable.setBounds((240-w)/2,(320-h)/2,(240+w)/2,(320+h)/2)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
    private fun background(dark: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(480, 200, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        canvas.scale(.5f, .5f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor(if (dark) "#252630" else "#F5F1E9")
        canvas.drawRoundRect(0f, 0f, 960f, 400f, 52f, 52f, paint)
        paint.shader = RadialGradient(75f, 210f, 400f,
            Color.parseColor(if (dark) "#303C3A57" else "#209999BD"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, 960f, 400f, 52f, 52f, paint)
        return bitmap
    }
}
