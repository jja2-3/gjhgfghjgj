package com.focus.moment.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.focus.moment.R

/** 内置主题 */
data class ThemeDef(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val bgLight: Color,
    val bgDark: Color,
    val surfaceLight: Color,
    val surfaceDark: Color,
    val onBgLight: Color = Color(0xFF1B1F1E),
    val onBgDark: Color = Color(0xFFE8ECEA)
)

val THEMES = listOf(
    ThemeDef("mint", "薄荷绿", Color(0xFF2E7D6B), Color(0xFF8FD3C7), Color(0xFFF4FAF8), Color(0xFF101614), Color(0xFFFFFFFF), Color(0xFF1B2421)),
    ThemeDef("ocean", "海洋蓝", Color(0xFF2C6E9B), Color(0xFF8EC5E8), Color(0xFFF3F8FC), Color(0xFF0E1418), Color(0xFFFFFFFF), Color(0xFF18222B)),
    ThemeDef("sakura", "樱花粉", Color(0xFFC25B78), Color(0xFFF3B8C9), Color(0xFFFDF6F7), Color(0xFF1A1214), Color(0xFFFFFFFF), Color(0xFF271B1F)),
    ThemeDef("ink", "深夜黑", Color(0xFF5B8DEF), Color(0xFF9AA7BD), Color(0xFFF5F6F8), Color(0xFF0C0D10), Color(0xFFFFFFFF), Color(0xFF181A1F)),
    ThemeDef("amber", "暖阳橙", Color(0xFFC77826), Color(0xFFF2C186), Color(0xFFFCF8F2), Color(0xFF171208), Color(0xFFFFFFFF), Color(0xFF231B10)),
    ThemeDef("violet", "薰衣草", Color(0xFF7C5CD6), Color(0xFFC6B4F2), Color(0xFFF8F6FD), Color(0xFF120F1A), Color(0xFFFFFFFF), Color(0xFF1C1728)),
    ThemeDef("cyan", "天空青", Color(0xFF2296A6), Color(0xFF9ED8E0), Color(0xFFF2FAFB), Color(0xFF0B1618), Color(0xFFFFFFFF), Color(0xFF142326)),
    ThemeDef("rose", "珊瑚红", Color(0xFFD0605E), Color(0xFFF3B5AE), Color(0xFFFDF6F5), Color(0xFF1A1010), Color(0xFFFFFFFF), Color(0xFF271A19))
)

/** 内置插画风壁纸（矢量绘制，离线可用） */
data class WallpaperDef(val id: String, val label: String, @DrawableRes val res: Int)

val WALLPAPERS = listOf(
    WallpaperDef("aurora", "极光", R.drawable.wp_aurora),
    WallpaperDef("stars", "星空", R.drawable.wp_stars),
    WallpaperDef("mountains", "山峦晨雾", R.drawable.wp_mountains),
    WallpaperDef("forest", "森林", R.drawable.wp_forest),
    WallpaperDef("waves", "海浪", R.drawable.wp_waves),
    WallpaperDef("moonlight", "月夜", R.drawable.wp_moonlight),
    WallpaperDef("campfire", "篝火", R.drawable.wp_campfire),
    WallpaperDef("rainy", "雨夜窗景", R.drawable.wp_rainy),
    WallpaperDef("sunset", "黄昏湖畔", R.drawable.wp_sunset),
    WallpaperDef("bamboo", "竹林听雨", R.drawable.wp_bamboo),
    WallpaperDef("desert", "大漠孤烟", R.drawable.wp_desert),
    WallpaperDef("cherry", "落樱小径", R.drawable.wp_cherry)
)

fun wallpaperOf(id: String): WallpaperDef = WALLPAPERS.firstOrNull { it.id == id } ?: WALLPAPERS[0]

/** 字体选项 */
val FONT_OPTIONS = listOf(
    "default" to "默认黑体",
    "serif" to "宋体",
    "kai" to "楷体",
    "xing" to "行书",
    "mono" to "等宽",
    "custom" to "自定义"
)

fun fontFamilyOf(id: String, customPath: String): FontFamily = when (id) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "kai" -> systemFontOf("kaiti", "KaiTi", "STKaiti", "sans-serif-serif")
    "xing" -> systemFontOf("xingkai", "XingKai", "STXingkai")
    "custom" -> customFontOf(customPath)
    else -> FontFamily.Default
}

private fun systemFontOf(vararg names: String): FontFamily {
    val tf = runCatching {
        android.graphics.Typeface.create(names[0], android.graphics.Typeface.NORMAL)
    }.getOrNull() ?: return FontFamily.Default
    return FontFamily(tf)
}

private fun customFontOf(path: String): FontFamily {
    if (path.isBlank()) return FontFamily.Default
    val tf = runCatching { android.graphics.Typeface.createFromFile(path) }.getOrNull()
        ?: return FontFamily.Default
    return FontFamily(tf)
}

/** 将字体族应用到全部文字样式 */
fun Typography.withFontFamily(f: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

@Composable
fun FocusMomentTheme(
    themeId: String,
    darkMode: String,
    fontId: String = "default",
    fontPath: String = "",
    content: @Composable () -> Unit
) {
    val theme = THEMES.firstOrNull { it.id == themeId } ?: THEMES[0]
    val dark = when (darkMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors: ColorScheme = if (dark) {
        darkColorScheme(
            primary = theme.secondary,
            onPrimary = Color(0xFF10201B),
            secondary = theme.secondary,
            onSecondary = Color(0xFF10201B),
            background = theme.bgDark,
            onBackground = theme.onBgDark,
            surface = theme.surfaceDark,
            onSurface = theme.onBgDark,
            surfaceVariant = theme.surfaceDark,
            onSurfaceVariant = theme.onBgDark.copy(alpha = 0.7f),
            outline = theme.onBgDark.copy(alpha = 0.25f)
        )
    } else {
        lightColorScheme(
            primary = theme.primary,
            onPrimary = Color.White,
            secondary = theme.primary,
            onSecondary = Color.White,
            background = theme.bgLight,
            onBackground = theme.onBgLight,
            surface = theme.surfaceLight,
            onSurface = theme.onBgLight,
            surfaceVariant = theme.bgLight,
            onSurfaceVariant = theme.onBgLight.copy(alpha = 0.65f),
            outline = theme.onBgLight.copy(alpha = 0.25f)
        )
    }
    val base = MaterialTheme.typography
    val typography = androidx.compose.runtime.remember(base) {
        base.withFontFamily(fontFamilyOf(fontId, fontPath))
    }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

fun categoryColor(hex: Long): Color = Color(hex)
