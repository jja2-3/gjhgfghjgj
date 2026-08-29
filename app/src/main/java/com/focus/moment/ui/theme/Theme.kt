package com.focus.moment.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
    ThemeDef("ink", "深夜黑", Color(0xFF5B8DEF), Color(0xFF9AA7BD), Color(0xFFF5F6F8), Color(0xFF0C0D10), Color(0xFFFFFFFF), Color(0xFF181A1F))
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
    WallpaperDef("rainy", "雨夜窗景", R.drawable.wp_rainy)
)

fun wallpaperOf(id: String): WallpaperDef = WALLPAPERS.firstOrNull { it.id == id } ?: WALLPAPERS[0]

@Composable
fun FocusMomentTheme(themeId: String, darkMode: String, content: @Composable () -> Unit) {
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
    MaterialTheme(colorScheme = colors, content = content)
}

fun categoryColor(hex: Long): Color = Color(hex)
