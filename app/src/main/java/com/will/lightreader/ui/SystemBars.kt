package com.will.lightreader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.WeakHashMap

private tailrec fun Context.activityWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> if (baseContext !== this) baseContext.activityWindow() else null
    else -> null
}

private fun View.hostWindow(): Window? {
    (this as? DialogWindowProvider)?.let { return it.window }
    var ancestor = parent
    while (ancestor != null) {
        if (ancestor is DialogWindowProvider) return ancestor.window
        ancestor = ancestor.parent
    }
    return context.activityWindow()
}

private class WindowBarState(window: Window) {
    val owners = SystemBarOwners()
    private val insets = ViewCompat.getRootWindowInsets(window.decorView)
    val original = SystemBarPolicy(
        !(insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true),
        !(insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true),
        window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
    )
    val behavior = WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior
}

private val windowBars = WeakHashMap<Window, WindowBarState>()

private fun applyWindowBars(window: Window, state: WindowBarState) {
    val active = state.owners.current
    val policy = active ?: state.original
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (policy.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    controller.systemBarsBehavior = if (active != null && (policy.hideNavigation || policy.hideStatus))
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE else state.behavior
    val navigation = WindowInsetsCompat.Type.navigationBars()
    val status = WindowInsetsCompat.Type.statusBars()
    val hidden = (if (policy.hideNavigation) navigation else 0) or (if (policy.hideStatus) status else 0)
    val shown = (navigation or status) and hidden.inv()
    if (shown != 0) controller.show(shown)
    if (hidden != 0) controller.hide(hidden)
}

/** A request owns only its actual window and yields when its navigation entry pauses. */
private class ReadingWindowRequest(
    private val view: View,
    private val lifecycle: Lifecycle,
    private var policy: SystemBarPolicy,
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnWindowFocusChangeListener {
    private val token = Any()
    private var window: Window? = null
    private var focusObserver: ViewTreeObserver? = null
    private val lifecycleObserver = LifecycleEventObserver { _, _ -> refresh() }

    fun start() {
        view.addOnAttachStateChangeListener(this)
        lifecycle.addObserver(lifecycleObserver)
        if (ViewCompat.isAttachedToWindow(view)) onViewAttachedToWindow(view)
    }

    fun update(value: SystemBarPolicy) {
        if (policy == value) return
        policy = value
        refresh()
    }

    fun refresh() {
        val target = window ?: return
        val state = windowBars.getOrPut(target) { WindowBarState(target) }
        state.owners.update(token, policy, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        applyWindowBars(target, state)
    }

    private fun releaseWindow() {
        val target = window ?: return
        window = null
        val state = windowBars[target] ?: return
        state.owners.remove(token)
        applyWindowBars(target, state)
        if (state.owners.isEmpty) windowBars.remove(target)
    }

    private fun removeFocusObserver() {
        focusObserver?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(this)
        focusObserver = null
    }

    override fun onViewAttachedToWindow(v: View) {
        val target = view.hostWindow()
        if (target !== window) releaseWindow()
        window = target
        removeFocusObserver()
        focusObserver = view.viewTreeObserver.also { it.addOnWindowFocusChangeListener(this) }
        refresh()
    }

    override fun onViewDetachedFromWindow(v: View) {
        removeFocusObserver()
        releaseWindow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) refresh()
    }

    fun dispose() {
        lifecycle.removeObserver(lifecycleObserver)
        view.removeOnAttachStateChangeListener(this)
        removeFocusObserver()
        releaseWindow()
    }
}

/** Apply to the reader and each reading dialog, keeping their independent windows in sync. */
@Composable
fun ReaderSystemBars(immersive: Boolean, hideStatusBar: Boolean = false, keepAwake: Boolean = true) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val policy = SystemBarPolicy(immersive, immersive && hideStatusBar, keepAwake)
    val request = remember(view, lifecycle) { ReadingWindowRequest(view, lifecycle, policy) }
    DisposableEffect(request) {
        request.start()
        onDispose { request.dispose() }
    }
    SideEffect { request.update(policy) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(request, keyboardVisible) {
        if (!keyboardVisible) request.refresh()
    }
}

@Composable
fun SystemBarsAppearance(lightContent: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.hostWindow() ?: return@SideEffect
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !lightContent
                isAppearanceLightNavigationBars = !lightContent
            }
        }
    }
}
