package com.focus.moment.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.focus.moment.audio.AlarmPlayer
import com.focus.moment.data.AppSettings
import com.focus.moment.data.SettingsStore
import com.focus.moment.data.model.AlarmSound
import com.focus.moment.data.model.LockMode
import com.focus.moment.data.model.RemindMode
import com.focus.moment.data.model.WhiteNoiseType
import com.focus.moment.ui.theme.THEMES
import com.focus.moment.ui.theme.wallpaperOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onOpenWallpapers: () -> Unit,
    onOpenSync: () -> Unit
) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    tick // 读取以触发权限状态刷新

    fun set(transform: (AppSettings) -> AppSettings) {
        scope.launch { store.update(transform) }
    }

    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val dndGranted = nm.isNotificationPolicyAccessGranted
    val a11yGranted = try {
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains("com.focus.moment") == true
    } catch (_: Exception) { false }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        // ---------- 外观 ----------
        SectionCard("主题外观") {
            Text("配色主题", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                THEMES.forEach { t ->
                    FilterChip(selected = settings.themeId == t.id, onClick = { set { it.copy(themeId = t.id) } }, label = { Text(t.label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("深色模式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (v, label) ->
                    FilterChip(selected = settings.darkMode == v, onClick = { set { it.copy(darkMode = v) } }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenWallpapers).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("壁纸", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "首页：${wallpaperOf(settings.homeWallpaper).label} · 计时：${wallpaperOf(settings.timerWallpaper).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------- 结束提醒 ----------
        SectionCard("结束提醒") {
            Text("提醒方式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemindMode.entries.forEach { m ->
                    FilterChip(
                        selected = settings.remindMode == m.name,
                        onClick = { set { it.copy(remindMode = m.name) } },
                        label = { Text(m.label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("铃声", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlarmSound.entries.forEach { a ->
                    FilterChip(
                        selected = settings.alarmSound == a.name,
                        onClick = {
                            set { it.copy(alarmSound = a.name) }
                            AlarmPlayer.preview(ctx, a)
                        },
                        label = { Text(a.label) }
                    )
                }
            }
            Text(
                "点击即选中并试听",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))

        // ---------- 白噪音 ----------
        SectionCard("白噪音") {
            Text("默认音效", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WhiteNoiseType.entries.forEach { t ->
                    FilterChip(
                        selected = settings.noiseDefault == t.name,
                        onClick = { set { it.copy(noiseDefault = t.name) } },
                        label = { Text(t.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("开始专注时自动播放", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = settings.noiseAutoPlay, onCheckedChange = { v -> set { it.copy(noiseAutoPlay = v) } })
            }
            Text("默认音量", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.noiseVolume,
                onValueChange = { v -> set { it.copy(noiseVolume = v) } }
            )
        }
        Spacer(Modifier.height(14.dp))

        // ---------- 自律模式 ----------
        SectionCard("自律模式（锁机）") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LockMode.entries.forEach { m ->
                    FilterChip(
                        selected = settings.lockMode == m.name,
                        onClick = { set { it.copy(lockMode = m.name) } },
                        label = { Text(m.label) }
                    )
                }
            }
            Text(
                LockMode.from(settings.lockMode).desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                title = "锁机服务（无障碍）",
                desc = if (a11yGranted) "已开启，严格模式可强制拉回" else "未开启，严格模式需要此权限",
                granted = a11yGranted
            ) {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PermissionRow(
                title = "勿扰权限",
                desc = if (dndGranted) "已开启，专注时将屏蔽通知弹窗" else "未开启，专注时将无法屏蔽通知",
                granted = dndGranted
            ) {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------- 云同步 ----------
        SectionCard("云同步") {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenSync).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (settings.loggedIn) "已登录：${settings.sbEmail}" else "未登录（数据仅保存在本机）",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (settings.loggedIn && settings.lastSync > 0)
                            "上次同步：${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(settings.lastSync))}"
                        else "配置 Supabase 后可多设备同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------- 关于 ----------
        SectionCard("关于") {
            Text("专注时刻 v1.0.0", style = MaterialTheme.typography.titleSmall)
            Text(
                "日程管理 · 自律锁机 · 专注报告\n放下的每一刻，都是为了更好的时刻。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, granted: Boolean, onGo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = onGo) { Text(if (granted) "管理" else "去开启") }
    }
}
