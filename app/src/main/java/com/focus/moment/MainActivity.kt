package com.focus.moment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focus.moment.data.AppSettings
import com.focus.moment.data.SettingsStore
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.model.TimerPhase
import com.focus.moment.service.FocusSessionState
import com.focus.moment.service.TimerDraft
import com.focus.moment.service.TimerService
import com.focus.moment.ui.lock.LockScreen
import com.focus.moment.ui.mine.MineScreen
import com.focus.moment.ui.report.ReportScreen
import com.focus.moment.ui.schedule.EditScheduleScreen
import com.focus.moment.ui.schedule.ScheduleScreen
import com.focus.moment.ui.theme.FocusMomentTheme
import com.focus.moment.ui.timer.TimerScreen
import com.focus.moment.ui.todo.TodoScreen
import com.focus.moment.ui.todoset.TodoSetDetailScreen
import com.focus.moment.ui.todoset.TodoSetScreen
import com.focus.moment.update.UpdateChecker
import com.focus.moment.update.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            val store = remember { SettingsStore(applicationContext) }
            val settings by store.flow.collectAsState(initial = AppSettings())
            val density = LocalDensity.current
            FocusMomentTheme(settings.themeId, settings.darkMode, settings.fontFamily, settings.customFontPath) {
                // 全局字体大小调节
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, density.fontScale * settings.fontScale)
                ) {
                    AppRoot(settings)
                }
            }
        }
    }
}

private val MAIN_TABS = listOf("todo", "todoset", "schedule", "lock", "report", "mine")

@Composable
fun AppRoot(settings: AppSettings) {
    val nav = rememberNavController()
    val activity = LocalContext.current as? ComponentActivity
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(ctx.applicationContext) }

    // ---------- 启动页 ----------
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1100); showSplash = false }

    // ---------- 版本检查 ----------
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var changelogOnce by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val ver = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
        val info = UpdateChecker.fetchLatest() ?: return@LaunchedEffect
        when {
            UpdateChecker.isNewer(info.version, ver) -> updateInfo = info
            info.version == ver && settings.shownVersion != ver -> {
                changelogOnce = info.changelog.ifBlank { "本次更新带来了大量新功能与优化" }
                scope.launch { store.update { it.copy(shownVersion = ver) } }
            }
        }
    }

    // 锁机拉回后若不在计时页，自动跳转到计时页
    DisposableEffect(Unit) {
        val listener = Consumer<Intent> {
            if (FocusSessionState.state.value.phase == TimerPhase.RUNNING) {
                val current = nav.currentBackStackEntry?.destination?.route
                if (current != "timer") {
                    nav.navigate("timer") { launchSingleTop = true }
                }
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val startFocus: (TimerDraft) -> Unit = { draft ->
        val act = activity
        if (act != null) {
            // 先写入占位状态，避免计时页因短暂 IDLE 而退出
            val now = System.currentTimeMillis()
            FocusSessionState.set(
                com.focus.moment.service.TimerState(
                    phase = TimerPhase.RUNNING,
                    scheduleId = draft.scheduleId,
                    title = draft.title,
                    category = draft.category,
                    mode = draft.mode,
                    plannedMinutes = draft.plannedMinutes,
                    lock = draft.lock,
                    startedAt = now,
                    endAt = if (draft.mode == TimerMode.COUNTDOWN) now + draft.plannedMinutes * 60_000L else 0L,
                    nowTick = now,
                    todoItemId = draft.todoItemId,
                    source = draft.source
                )
            )
            TimerService.start(act, draft)
            nav.navigate("timer") { launchSingleTop = true }
        }
    }

    if (showSplash) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.height(18.dp))
                Text("专注时刻", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "放下的每一刻，都是为了更好的时刻。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in MAIN_TABS) {
                NavigationBar {
                    TabItem(currentRoute == "todo", "代办", Icons.Filled.TaskAlt) { go(nav, "todo") }
                    TabItem(currentRoute == "todoset", "待办集", Icons.Filled.Checklist) { go(nav, "todoset") }
                    TabItem(currentRoute == "schedule", "日程", Icons.Filled.CalendarMonth) { go(nav, "schedule") }
                    TabItem(currentRoute == "lock", "锁机", Icons.Filled.Lock) { go(nav, "lock") }
                    TabItem(currentRoute == "report", "报告", Icons.Filled.BarChart) { go(nav, "report") }
                    TabItem(currentRoute == "mine", "我的", Icons.Filled.Person) { go(nav, "mine") }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "todo", modifier = Modifier.padding(pad)) {
            composable("todo") { TodoScreen(settings, startFocus) }
            composable("todoset") {
                TodoSetScreen(
                    settings = settings,
                    startFocus = startFocus,
                    onOpenSet = { nav.navigate("todoset/$it") }
                )
            }
            composable("todoset/{sid}") { entry ->
                val sid = entry.arguments?.getString("sid") ?: ""
                TodoSetDetailScreen(
                    sid = sid,
                    startFocus = startFocus,
                    onBack = { nav.popBackStack() }
                )
            }
            composable("schedule") { ScheduleScreen(settings, startFocus, onEditSchedule = { nav.navigate("edit/$it") }) }
            composable("lock") { LockScreen(settings, startFocus) }
            composable("report") { ReportScreen() }
            composable("mine") {
                MineScreen(
                    settings = settings,
                    onOpenWallpapers = { nav.navigate("wallpapers") },
                    onOpenSync = { nav.navigate("sync") }
                )
            }
            composable("edit/{sid}") { entry ->
                val sid = entry.arguments?.getString("sid") ?: "new"
                EditScheduleScreen(sid = sid, onDone = { nav.popBackStack() })
            }
            composable("timer") { TimerScreen(settings, onExit = { nav.popBackStack("todo", false) }) }
            composable("wallpapers") { com.focus.moment.ui.settings.WallpaperScreen(settings, onBack = { nav.popBackStack() }) }
            composable("sync") { com.focus.moment.ui.settings.SyncScreen(settings, onBack = { nav.popBackStack() }) }
        }
    }

    // 发现新版本
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本 v${info.version}") },
            text = {
                Column {
                    Text(info.versionName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(info.changelog.ifBlank { "性能优化与问题修复" }, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        openUrl(ctx, "https://ghproxy.net/${info.apkUrl.ifBlank { info.releaseUrl }}")
                    }) { Text("加速下载") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = {
                        openUrl(ctx, info.apkUrl.ifBlank { info.releaseUrl })
                    }) { Text("浏览器下载") }
                }
            },
            dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("暂不更新") } }
        )
    }

    // 更新内容（每个版本仅一次）
    changelogOnce?.let { log ->
        AlertDialog(
            onDismissRequest = { changelogOnce = null },
            title = { Text("什么是新的") },
            text = { Text(log) },
            confirmButton = { TextButton(onClick = { changelogOnce = null }) { Text("知道了") } }
        )
    }
}

internal fun openUrl(ctx: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun RowScope.TabItem(selected: Boolean, label: String, icon: ImageVector, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

private fun go(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
