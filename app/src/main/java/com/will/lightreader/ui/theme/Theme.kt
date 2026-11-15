package com.will.lightreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/**
 * 文库版：纸白 / 墨蓝 / 朱红，夜间使用低亮度的墨绿纸面。
 * 固定品牌色保持书封与正文稳定；动态取色仍可显式启用。
 */
private val LightTonal = lightColorScheme(
    primary = Color(0xFF626F92), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E5F1), onPrimaryContainer = Color(0xFF3E4867),
    secondary = Color(0xFF7C7C91), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8EB), onSecondaryContainer = Color(0xFF484455),
    tertiary = Color(0xFF9B665D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2E3DC), onTertiaryContainer = Color(0xFF6B453E),
    background = Color(0xFFF6F3ED), onBackground = Color(0xFF3C435A),
    surface = Color(0xFFF6F3ED), onSurface = Color(0xFF3C435A),
    surfaceVariant = Color(0xFFEFEAE7), onSurfaceVariant = Color(0xFF6A6774),
    surfaceContainerLowest = Color(0xFFFCFAF5), surfaceContainerLow = Color(0xFFF0ECE7),
    surfaceContainer = Color(0xFFEFEAE7), surfaceContainerHigh = Color(0xFFE8E4E7),
    surfaceContainerHighest = Color(0xFFDDD9E1),
    outline = Color(0xFF8C8793), outlineVariant = Color(0xFFDFDADF),
    inverseSurface = Color(0xFF30372F), inverseOnSurface = Color(0xFFE6E1DC),
    inversePrimary = Color(0xFFBAC4EF),
)

private val DarkTonal = darkColorScheme(
    primary = Color(0xFFBAC4EF), onPrimary = Color(0xFF23316F),
    primaryContainer = Color(0xFF3A4569), onPrimaryContainer = Color(0xFFDFE4FC),
    secondary = Color(0xFFC7BED2), onSecondary = Color(0xFF343146),
    secondaryContainer = Color(0xFF47465B), onSecondaryContainer = Color(0xFFE8DFEF),
    tertiary = Color(0xFFE8AB87), onTertiary = Color(0xFF522C1D),
    tertiaryContainer = Color(0xFF654233), onTertiaryContainer = Color(0xFFFFDCC4),
    background = Color(0xFF262935), onBackground = Color(0xFFE6E1DC),
    surface = Color(0xFF262935), onSurface = Color(0xFFE6E1DC),
    surfaceVariant = Color(0xFF373B47), onSurfaceVariant = Color(0xFFBCB9C7),
    surfaceContainerLowest = Color(0xFF20232D), surfaceContainerLow = Color(0xFF2B2E3A),
    surfaceContainer = Color(0xFF30333F), surfaceContainerHigh = Color(0xFF373B49),
    surfaceContainerHighest = Color(0xFF444856),
    outline = Color(0xFF9AA58F), outlineVariant = Color(0xFF494B5B),
    inverseSurface = Color(0xFFE6E1DC), inverseOnSurface = Color(0xFF30372F),
    inversePrimary = Color(0xFF626F92),
)

/** Expressive 排版：标题加粗拉开层级（正文保持默认安静）。 */
private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Medium),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Medium),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * 应用主题：M3 Expressive tonal + 弹性动效方案。
 * [sepia] 仅保持签名兼容——羊皮纸由阅读页纸色系统独立处理，不再进全局主题。
 */
@Composable
fun LightReaderTheme(
    darkTheme: Boolean? = null,
    // 默认用设计好的蓝色 tonal 方案；壁纸取色在部分设备上会把整屏冲成灰调，
    // 需要 Material You 时可显式传 true。
    dynamicColor: Boolean = false,
    @Suppress("UNUSED_PARAMETER") sepia: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = darkTheme ?: rememberAppearanceMode().isDark(isSystemInDarkTheme())
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkTonal
        else -> LightTonal
    }
    val reducedMotion = rememberReducedMotion()
    CompositionLocalProvider(LocalAppDark provides dark, LocalReducedMotion provides reducedMotion) {
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.standard(),
        shapes = ExpressiveShapes,
        typography = AppTypography,
        content = content,
    )
    }
}
