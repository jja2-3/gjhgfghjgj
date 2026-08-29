package com.focus.moment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focus.moment.data.AppSettings
import com.focus.moment.data.SettingsStore
import com.focus.moment.data.model.TimerMode
import com.focus.moment.service.FocusSessionState
import com.focus.moment.service.TimerDraft
import com.focus.moment.service.TimerPhase
import com.focus.moment.service.TimerService
import com.focus.moment.ui.home.HomeScreen
import com.focus.moment.ui.report.ReportScreen
import com.focus.moment.ui.schedule.EditScheduleScreen
import com.focus.moment.ui.schedule.ScheduleScreen
import com.focus.moment.ui.settings.SettingsScreen
import com.focus.moment.ui.settings.SyncScreen
import com.focus.moment.ui.settings.WallpaperScreen
import com.focus.moment.ui.theme.FocusMomentTheme
import com.focus.moment.ui.timer.TimerScreen
import java.util.function.Consumer

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
            FocusMomentTheme(settings.themeId, settings.darkMode) {
                AppRoot(settings)
            }
        }
    }
}

private val MAIN_TABS = listOf("home", "schedule", "report", "settings")

@Composable
fun AppRoot(settings: AppSettings) {
    val nav = rememberNavController()
    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity

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
                    nowTick = now
                )
            )
            TimerService.start(act, draft)
            nav.navigate("timer") { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in MAIN_TABS) {
                NavigationBar {
                    TabItem(currentRoute == "home", "首页", Icons.Filled.Home) { go(nav, "home") }
                    TabItem(currentRoute == "schedule", "日程", Icons.Filled.CalendarMonth) { go(nav, "schedule") }
                    TabItem(currentRoute == "report", "报告", Icons.Filled.BarChart) { go(nav, "report") }
                    TabItem(currentRoute == "settings", "设置", Icons.Filled.Settings) { go(nav, "settings") }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(settings, startFocus, onEditSchedule = { nav.navigate("edit/$it") }) }
            composable("schedule") { ScheduleScreen(settings, startFocus, onEditSchedule = { nav.navigate("edit/$it") }) }
            composable("report") { ReportScreen() }
            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    onOpenWallpapers = { nav.navigate("wallpapers") },
                    onOpenSync = { nav.navigate("sync") }
                )
            }
            composable("edit/{sid}") { entry ->
                val sid = entry.arguments?.getString("sid") ?: "new"
                EditScheduleScreen(sid = sid, onDone = { nav.popBackStack() })
            }
            composable("timer") { TimerScreen(settings, onExit = { nav.popBackStack("home", false) }) }
            composable("wallpapers") { WallpaperScreen(settings, onBack = { nav.popBackStack() }) }
            composable("sync") { SyncScreen(settings, onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun TabItem(selected: Boolean, label: String, icon: ImageVector, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}

private fun go(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
