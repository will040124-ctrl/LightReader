package com.will.lightreader.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

val LocalAppDark = staticCompositionLocalOf { false }

class AppAppearance(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("app_appearance", Context.MODE_PRIVATE)
    var mode: AppearanceMode
        get() = AppearanceMode.stored(preferences.getString("mode", null))
        set(value) { preferences.edit().putString("mode", value.name).apply() }
}

/** Observe external changes too, and unregister when the composition leaves the activity. */
@Composable
fun rememberAppearanceMode(): AppearanceMode {
    val context = LocalContext.current
    val store = remember(context) { AppAppearance(context) }
    var mode by remember(store) { mutableStateOf(store.mode) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "mode") mode = store.mode
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        mode = store.mode
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode
}
