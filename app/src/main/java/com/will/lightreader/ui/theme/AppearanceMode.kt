package com.will.lightreader.ui.theme

enum class AppearanceMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun stored(value: String?): AppearanceMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
