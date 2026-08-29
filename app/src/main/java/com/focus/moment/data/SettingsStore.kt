package com.focus.moment.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "focus_settings")

/** 全部应用设置（含云同步凭据） */
data class AppSettings(
    val themeId: String = "mint",
    val darkMode: String = "system",        // system / light / dark
    val homeWallpaper: String = "aurora",
    val timerWallpaper: String = "aurora",
    val wallpaperDim: Float = 0.15f,        // 壁纸暗化 0~0.6
    val wallpaperBlur: Float = 0f,          // 壁纸模糊半径 dp（Android 12+ 生效）
    val alarmSound: String = "CHIME",
    val remindMode: String = "BOTH",
    val lockMode: String = "STRICT",
    val noiseDefault: String = "RAIN",
    val noiseAutoPlay: Boolean = false,
    val noiseVolume: Float = 0.7f,
    // Supabase 云同步
    val sbUrl: String = "",
    val sbKey: String = "",
    val sbEmail: String = "",
    val sbAccessToken: String = "",
    val sbRefreshToken: String = "",
    val sbUserId: String = "",
    val lastSync: Long = 0L
) {
    val loggedIn: Boolean get() = sbAccessToken.isNotEmpty() && sbUserId.isNotEmpty()
}

class SettingsStore(private val context: Context) {

    private object K {
        val THEME = stringPreferencesKey("theme_id")
        val DARK = stringPreferencesKey("dark_mode")
        val HOME_WP = stringPreferencesKey("home_wallpaper")
        val TIMER_WP = stringPreferencesKey("timer_wallpaper")
        val WP_DIM = floatPreferencesKey("wallpaper_dim")
        val WP_BLUR = floatPreferencesKey("wallpaper_blur")
        val ALARM = stringPreferencesKey("alarm_sound")
        val REMIND = stringPreferencesKey("remind_mode")
        val LOCK = stringPreferencesKey("lock_mode")
        val NOISE_DEF = stringPreferencesKey("noise_default")
        val NOISE_AUTO = booleanPreferencesKey("noise_auto_play")
        val NOISE_VOL = floatPreferencesKey("noise_volume")
        val SB_URL = stringPreferencesKey("sb_url")
        val SB_KEY = stringPreferencesKey("sb_key")
        val SB_EMAIL = stringPreferencesKey("sb_email")
        val SB_AT = stringPreferencesKey("sb_access_token")
        val SB_RT = stringPreferencesKey("sb_refresh_token")
        val SB_UID = stringPreferencesKey("sb_user_id")
        val LAST_SYNC = longPreferencesKey("last_sync")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeId = p[K.THEME] ?: "mint",
            darkMode = p[K.DARK] ?: "system",
            homeWallpaper = p[K.HOME_WP] ?: "aurora",
            timerWallpaper = p[K.TIMER_WP] ?: "aurora",
            wallpaperDim = p[K.WP_DIM] ?: 0.15f,
            wallpaperBlur = p[K.WP_BLUR] ?: 0f,
            alarmSound = p[K.ALARM] ?: "CHIME",
            remindMode = p[K.REMIND] ?: "BOTH",
            lockMode = p[K.LOCK] ?: "STRICT",
            noiseDefault = p[K.NOISE_DEF] ?: "RAIN",
            noiseAutoPlay = p[K.NOISE_AUTO] ?: false,
            noiseVolume = p[K.NOISE_VOL] ?: 0.7f,
            sbUrl = p[K.SB_URL] ?: "",
            sbKey = p[K.SB_KEY] ?: "",
            sbEmail = p[K.SB_EMAIL] ?: "",
            sbAccessToken = p[K.SB_AT] ?: "",
            sbRefreshToken = p[K.SB_RT] ?: "",
            sbUserId = p[K.SB_UID] ?: "",
            lastSync = p[K.LAST_SYNC] ?: 0L
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = AppSettings(
                themeId = p[K.THEME] ?: "mint",
                darkMode = p[K.DARK] ?: "system",
                homeWallpaper = p[K.HOME_WP] ?: "aurora",
                timerWallpaper = p[K.TIMER_WP] ?: "aurora",
                wallpaperDim = p[K.WP_DIM] ?: 0.15f,
                wallpaperBlur = p[K.WP_BLUR] ?: 0f,
                alarmSound = p[K.ALARM] ?: "CHIME",
                remindMode = p[K.REMIND] ?: "BOTH",
                lockMode = p[K.LOCK] ?: "STRICT",
                noiseDefault = p[K.NOISE_DEF] ?: "RAIN",
                noiseAutoPlay = p[K.NOISE_AUTO] ?: false,
                noiseVolume = p[K.NOISE_VOL] ?: 0.7f,
                sbUrl = p[K.SB_URL] ?: "",
                sbKey = p[K.SB_KEY] ?: "",
                sbEmail = p[K.SB_EMAIL] ?: "",
                sbAccessToken = p[K.SB_AT] ?: "",
                sbRefreshToken = p[K.SB_RT] ?: "",
                sbUserId = p[K.SB_UID] ?: "",
                lastSync = p[K.LAST_SYNC] ?: 0L
            )
            val n = transform(cur)
            p[K.THEME] = n.themeId
            p[K.DARK] = n.darkMode
            p[K.HOME_WP] = n.homeWallpaper
            p[K.TIMER_WP] = n.timerWallpaper
            p[K.WP_DIM] = n.wallpaperDim
            p[K.WP_BLUR] = n.wallpaperBlur
            p[K.ALARM] = n.alarmSound
            p[K.REMIND] = n.remindMode
            p[K.LOCK] = n.lockMode
            p[K.NOISE_DEF] = n.noiseDefault
            p[K.NOISE_AUTO] = n.noiseAutoPlay
            p[K.NOISE_VOL] = n.noiseVolume
            p[K.SB_URL] = n.sbUrl
            p[K.SB_KEY] = n.sbKey
            p[K.SB_EMAIL] = n.sbEmail
            p[K.SB_AT] = n.sbAccessToken
            p[K.SB_RT] = n.sbRefreshToken
            p[K.SB_UID] = n.sbUserId
            p[K.LAST_SYNC] = n.lastSync
        }
    }
}
