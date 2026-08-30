package com.focus.moment.ui.mine

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focus.moment.audio.AlarmPlayer
import com.focus.moment.audio.WhiteNoiseEngine
import com.focus.moment.data.AuthManager
import com.focus.moment.data.AppSettings
import com.focus.moment.data.SettingsStore
import com.focus.moment.data.SyncManager
import com.focus.moment.data.model.AlarmSound
import com.focus.moment.data.model.LockMode
import com.focus.moment.data.model.RemindMode
import com.focus.moment.data.model.WhiteNoiseType
import com.focus.moment.openUrl
import com.focus.moment.ui.theme.FONT_OPTIONS
import com.focus.moment.ui.theme.THEMES
import com.focus.moment.ui.theme.wallpaperOf
import com.focus.moment.update.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineScreen(
    settings: AppSettings,
    onOpenWallpapers: () -> Unit,
    onOpenSync: () -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val store = remember { SettingsStore(appCtx) }
    val scope = rememberCoroutineScope()
    val auth = remember { AuthManager(appCtx) }
    val sync = remember { SyncManager(appCtx) }

    var message by remember { mutableStateOf("") }
    var showPhoneLogin by remember { mutableStateOf(false) }
    var showNickEdit by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    fun set(transform: (AppSettings) -> AppSettings) {
        scope.launch { store.update(transform) }
    }

    // ---------- 头像选择 ----------
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = AuthManager.imageToBase64(appCtx, uri)
                if (b64 != null) {
                    store.update { it.copy(avatarB64 = b64) }
                    if (settings.loggedIn) {
                        message = auth.updateProfile(null, b64)
                    }
                } else message = "图片读取失败"
            }
        }
    }

    // ---------- 字体导入 ----------
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val dir = File(appCtx.filesDir, "fonts").apply { mkdirs() }
                    val f = File(dir, "custom_font.ttf")
                    appCtx.contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                    store.update { it.copy(customFontPath = f.absolutePath, fontFamily = "custom") }
                    message = "字体已应用"
                }.onFailure { message = "字体导入失败：${it.message}" }
            }
        }
    }

    // ---------- 自定义铃声（音乐库） ----------
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    var displayName = "自定义铃声"
                    runCatching {
                        appCtx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && c.moveToFirst()) displayName = c.getString(idx) ?: displayName
                        }
                    }
                    val ext = displayName.substringAfterLast('.', "mp3")
                    val f = File(appCtx.filesDir, "custom_ringtone.$ext")
                    appCtx.contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                    store.update {
                        it.copy(customRingtonePath = f.absolutePath, customRingtoneName = displayName)
                    }
                    AlarmPlayer.preview(appCtx, AlarmSound.from(settings.alarmSound), f.absolutePath)
                    message = "已选择：$displayName"
                }.onFailure { message = "铃声导入失败：${it.message}" }
            }
        }
    }

    val avatarBmp: Bitmap? = if (settings.avatarB64.isNotBlank()) {
        remember(settings.avatarB64) { AuthManager.base64ToBitmap(settings.avatarB64) }
    } else null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        // ---------- 个人资料 ----------
        ProfileCard(
            settings = settings,
            avatarBmp = avatarBmp,
            onPickAvatar = { avatarPicker.launch("image/*") },
            onEditNickname = { showNickEdit = true },
            onLogin = { showPhoneLogin = true },
            onLogout = {
                scope.launch {
                    sync.signOut()
                    message = "已退出登录"
                }
            }
        )
        Spacer(Modifier.height(14.dp))

        // ---------- 外观 ----------
        SectionCard("外观") {
            Text("主页配色", style = MaterialTheme.typography.titleSmall)
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
            Spacer(Modifier.height(8.dp))
            Text("字体大小：${(settings.fontScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.fontScale,
                onValueChange = { v -> set { it.copy(fontScale = (v * 100).toInt() / 100f) } },
                valueRange = 0.85f..1.35f
            )
            Text("字体样式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FONT_OPTIONS.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.fontFamily == id,
                        onClick = { set { it.copy(fontFamily = id) } },
                        label = { Text(label) }
                    )
                }
            }
            if (settings.fontFamily == "custom" && settings.customFontPath.isBlank()) {
                Text(
                    "尚未导入字体文件，点击下方按钮选择 .ttf/.otf 文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { fontPicker.launch("*/*") }, modifier = Modifier.fillMaxWidth().height(42.dp)) {
                Text("从手机导入字体文件")
            }
            Spacer(Modifier.height(12.dp))
            Text("语言 / Language", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("zh" to "中文", "en" to "English").forEach { (v, label) ->
                    FilterChip(
                        selected = settings.language == v,
                        onClick = {
                            set { it.copy(language = v) }
                            runCatching {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    val lm = ctx.getSystemService(android.app.LocaleManager::class.java)
                                    lm.applicationLocales =
                                        android.os.LocaleList.forLanguageTags(if (v == "en") "en" else "zh-CN")
                                }
                            }
                        },
                        label = { Text(label) }
                    )
                }
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
            Text("铃声（点击选中并试听）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlarmSound.entries.forEach { a ->
                    FilterChip(
                        selected = settings.alarmSound == a.name && settings.customRingtonePath.isBlank(),
                        onClick = {
                            set { it.copy(alarmSound = a.name) }
                            AlarmPlayer.preview(ctx, a, null)
                        },
                        label = { Text(a.label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自定义铃声（音乐库）", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (settings.customRingtonePath.isBlank()) "未设置，使用上方内置铃声"
                        else "当前：${settings.customRingtoneName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { ringtonePicker.launch("audio/*") }) { Text("选择") }
                if (settings.customRingtonePath.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { set { it.copy(customRingtonePath = "", customRingtoneName = "") } }) {
                        Text("清除")
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------- 白噪音 ----------
        SectionCard("白噪音") {
            Text("默认音效（点击选中并试听）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WhiteNoiseType.entries.forEach { t ->
                    FilterChip(
                        selected = settings.noiseDefault == t.name,
                        onClick = {
                            set { it.copy(noiseDefault = t.name) }
                            WhiteNoiseEngine.preview(t, settings.noiseVolume)
                        },
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
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndGranted = nm.isNotificationPolicyAccessGranted
        val a11yGranted = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains("com.focus.moment") == true
        } catch (_: Exception) { false }

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

        // ---------- 关于与更新 ----------
        val versionName = remember {
            runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0" }
                .getOrDefault("1.0.0")
        }
        SectionCard("关于") {
            Text("专注时刻 v$versionName", style = MaterialTheme.typography.titleSmall)
            Text(
                "待办 · 待办集 · 日历日程 · 锁机 · 报告\n放下的每一刻，都是为了更好的时刻。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = !checking,
                onClick = {
                    checking = true
                    updateMsg = ""
                    scope.launch {
                        val info = UpdateChecker.fetchLatest()
                        updateMsg = when {
                            info == null -> "检查失败，请稍后再试"
                            UpdateChecker.isNewer(info.version, versionName) -> {
                                openUrl(ctx, info.apkUrl.ifBlank { info.releaseUrl })
                                "发现新版本 v${info.version}，已打开下载页"
                            }
                            else -> "已是最新版本 v$versionName"
                        }
                        checking = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { Text(if (checking) "检查中…" else "检查更新") }
            if (updateMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(updateMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (message.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(40.dp))
    }

    // ---------- 手机号登录/注册弹窗 ----------
    if (showPhoneLogin) {
        PhoneLoginDialog(
            auth = auth,
            onDismiss = { showPhoneLogin = false },
            onResult = { msg ->
                message = msg
                if (msg.startsWith("登录成功") || msg.startsWith("注册成功")) showPhoneLogin = false
            }
        )
    }

    // ---------- 修改昵称 ----------
    if (showNickEdit) {
        var nick by remember { mutableStateOf(settings.nickname.ifBlank { "" }) }
        AlertDialog(
            onDismissRequest = { showNickEdit = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = nick,
                    onValueChange = { nick = it.take(20) },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        store.update { it.copy(nickname = nick.trim()) }
                        if (settings.loggedIn && nick.isNotBlank()) {
                            message = auth.updateProfile(nick.trim(), null)
                        }
                        showNickEdit = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showNickEdit = false }) { Text("取消") } }
        )
    }
}

// ---------------------------------------------------------------------------
// 子组件
// ---------------------------------------------------------------------------

@Composable
private fun ProfileCard(
    settings: AppSettings,
    avatarBmp: Bitmap?,
    onPickAvatar: () -> Unit,
    onEditNickname: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onPickAvatar),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBmp != null) {
                    Image(
                        bitmap = avatarBmp.asImageBitmap(),
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.Person, contentDescription = "选择头像", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (settings.loggedIn) {
                    Text(
                        settings.nickname.ifBlank { "专注者" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        settings.sbEmail.removeSuffix("@phone.focusmoment.app"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEditNickname, modifier = Modifier.height(36.dp)) { Text("改昵称") }
                        OutlinedButton(onClick = onPickAvatar, modifier = Modifier.height(36.dp)) { Text("换头像") }
                        TextButton(onClick = onLogout) { Text("退出") }
                    }
                } else {
                    Text("未登录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "登录后可多设备同步数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onLogin, modifier = Modifier.height(38.dp)) {
                        Text("手机号登录 / 注册")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneLoginDialog(
    auth: AuthManager,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) } // 0=登录 1=注册
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("手机号登录") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("登录") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("注册") })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() }.take(11) },
                    label = { Text("手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(32) },
                    label = { Text("密码（至少 6 位）") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mode == 1) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(20) },
                        label = { Text("昵称（非必选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "需先在「云同步高级设置」中配置 Supabase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (err.isNotEmpty()) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && phone.length == 11 && password.length >= 6,
                onClick = {
                    busy = true
                    err = ""
                    scope.launch {
                        val msg = if (mode == 0) auth.signInWithPhone(phone, password)
                                  else auth.signUpWithPhone(phone, password, nickname)
                        busy = false
                        if (msg.startsWith("登录成功") || msg.startsWith("注册成功")) {
                            onResult(msg)
                        } else {
                            err = msg
                        }
                    }
                }
            ) { Text(if (busy) "请稍候…" else if (mode == 0) "登录" else "注册") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } }
    )
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
