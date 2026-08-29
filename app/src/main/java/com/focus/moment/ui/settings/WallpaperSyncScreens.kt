package com.focus.moment.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.focus.moment.data.AppSettings
import com.focus.moment.data.SettingsStore
import com.focus.moment.data.SyncManager
import com.focus.moment.ui.theme.WALLPAPERS
import com.focus.moment.ui.theme.wallpaperOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(settings: AppSettings, onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(0) } // 0=首页 1=计时页
    val currentId = if (target == 0) settings.homeWallpaper else settings.timerWallpaper

    fun set(transform: (AppSettings) -> AppSettings) {
        scope.launch { store.update(transform) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("壁纸", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = target == 0,
                onClick = { target = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("首页壁纸") }
            SegmentedButton(
                selected = target == 1,
                onClick = { target = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("计时页壁纸") }
        }
        Spacer(Modifier.height(12.dp))

        Text(
            "选择插画风壁纸（点击应用）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(WALLPAPERS, key = { it.id }) { wp ->
                val selected = wp.id == currentId
                Card(
                    shape = RoundedCornerShape(14.dp),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    onClick = {
                        set {
                            if (target == 0) it.copy(homeWallpaper = wp.id) else it.copy(timerWallpaper = wp.id)
                        }
                    }
                ) {
                    Column {
                        Image(
                            painter = painterResource(wp.res),
                            contentDescription = wp.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.62f)
                        )
                        Text(
                            wp.label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("暗化：${(settings.wallpaperDim * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = settings.wallpaperDim,
            onValueChange = { v -> set { it.copy(wallpaperDim = v) } },
            valueRange = 0f..0.7f
        )
        Text("模糊（Android 12+ 生效）：${(settings.wallpaperBlur).toInt()}dp", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = settings.wallpaperBlur,
            onValueChange = { v -> set { it.copy(wallpaperBlur = v) } },
            valueRange = 0f..24f
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun SyncScreen(settings: AppSettings, onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx.applicationContext) }
    val sync = remember { SyncManager(ctx.applicationContext) }
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(settings.sbUrl) }
    var key by remember { mutableStateOf(settings.sbKey) }
    var email by remember { mutableStateOf(settings.sbEmail) }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("云同步", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        if (settings.loggedIn) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("已登录：${settings.sbEmail}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (settings.lastSync > 0)
                            "上次同步：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(settings.lastSync))}"
                        else "尚未同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        message = sync.syncNow()
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(if (busy) "同步中…" else "立即同步") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { scope.launch { sync.signOut() } },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("退出登录") }
        } else {
            Text(
                "1. 在 supabase.com 创建项目\n2. 复制 Project URL 和 anon key 填到下面\n3. 在 SQL Editor 执行建表 SQL（见使用说明）\n4. 注册邮箱账号并登录，数据自动同步",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = url, onValueChange = { url = it.trim() }, label = { Text("Supabase Project URL") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = key, onValueChange = { key = it.trim() }, label = { Text("anon key") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text("邮箱") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码（至少 6 位）") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (url.isBlank() || key.isBlank() || email.isBlank() || password.length < 6) {
                            message = "请完整填写所有信息，密码至少 6 位"
                            return@Button
                        }
                        // 先保存 URL/Key
                        scope.launch {
                            store.update { it.copy(sbUrl = url, sbKey = key) }
                            busy = true
                            message = sync.signIn(email, password)
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("登录") }
                OutlinedButton(
                    onClick = {
                        if (url.isBlank() || key.isBlank() || email.isBlank() || password.length < 6) {
                            message = "请完整填写所有信息，密码至少 6 位"
                            return@OutlinedButton
                        }
                        scope.launch {
                            store.update { it.copy(sbUrl = url, sbKey = key) }
                            busy = true
                            message = sync.signUp(email, password)
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("注册") }
            }
        }

        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(30.dp))
    }
}
