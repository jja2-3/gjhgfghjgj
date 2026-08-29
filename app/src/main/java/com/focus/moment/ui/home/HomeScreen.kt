package com.focus.moment.ui.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import com.focus.moment.data.AppSettings
import com.focus.moment.data.TimeFmt
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.ScheduleEntity
import com.focus.moment.data.db.SessionEntity
import com.focus.moment.data.model.Category
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.occursOn
import com.focus.moment.service.TimerDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).scheduleDao()
    private val sessionDao = AppDatabase.get(app).sessionDao()

    val todaySchedules = MutableStateFlow<List<ScheduleEntity>>(emptyList())
    val todaySessions = MutableStateFlow<List<SessionEntity>>(emptyList())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            todaySchedules.value = dao.all().filter { it.occursOn(today) }
                .sortedBy { it.startTime ?: "99:99" }
            todaySessions.value = sessionDao.between(TimeFmt.startOfDay(today), TimeFmt.startOfNextDay(today))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: AppSettings,
    onStartFocus: (TimerDraft) -> Unit,
    onEditSchedule: (String) -> Unit
) {
    val vm = remember { HomeViewModel(androidx.compose.ui.platform.LocalContext.current.applicationContext as Application) }
    val schedules by vm.todaySchedules.collectAsState()
    val sessions by vm.todaySessions.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var showQuick by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuick = true }) {
                Icon(Icons.Filled.Add, contentDescription = "快速开始")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("专注时刻", style = MaterialTheme.typography.headlineMedium)
            Text(
                LocalDate.now().let { "${it.monthValue}月${it.dayOfMonth}日 · ${weekLabel(it.dayOfWeek.value)}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // 今日统计卡片
            val focusMin = sessions.sumOf { it.actualSeconds.toLong() } / 60
            val done = sessions.count { it.status != "ABANDONED" }
            val rate = if (schedules.isEmpty()) 0 else (done * 100 / schedules.size)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("今日专注", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                        Text(
                            TimeFmt.hmm(focusMin),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("日程完成率", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                        Text("$rate%", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("今日日程", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            if (schedules.isEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今天还没有安排日程")
                        Spacer(Modifier.height(6.dp))
                        Text("点右下角 + 可立即开始专注", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                schedules.forEach { s ->
                    ScheduleRow(s, onStartFocus) { onEditSchedule(s.id) }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showQuick) {
        QuickStartDialog(
            onDismiss = { showQuick = false },
            onStart = { draft ->
                showQuick = false
                onStartFocus(draft)
            }
        )
    }
}

@Composable
private fun ScheduleRow(s: ScheduleEntity, onStartFocus: (TimerDraft) -> Unit, onEdit: () -> Unit) {
    val cat = Category.from(s.category)
    val mode = TimerMode.from(s.mode)
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp, 40.dp).clip(RoundedCornerShape(3.dp)).background(Color(cat.colorHex)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(cat.label)
                        if (s.startTime != null) append(" · " + s.startTime)
                        if (mode == TimerMode.COUNTDOWN && s.plannedMinutes != null) append(" · ${s.plannedMinutes}分钟")
                        if (mode == TimerMode.COUNTUP) append(" · 正计时")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = {
                    onStartFocus(
                        TimerDraft(
                            scheduleId = s.id,
                            title = s.title,
                            category = cat,
                            mode = mode,
                            plannedMinutes = s.plannedMinutes ?: 25,
                            lock = true
                        )
                    )
                }
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("开始")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickStartDialog(onDismiss: () -> Unit, onStart: (TimerDraft) -> Unit) {
    var mode by remember { mutableStateOf(TimerMode.COUNTDOWN) }
    var preset by remember { mutableStateOf(25) }
    var custom by remember { mutableStateOf("") }
    val minutes = custom.toIntOrNull() ?: preset

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速开始") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == TimerMode.COUNTDOWN,
                        onClick = { mode = TimerMode.COUNTDOWN },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("倒计时") }
                    SegmentedButton(
                        selected = mode == TimerMode.COUNTUP,
                        onClick = { mode = TimerMode.COUNTUP },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("正计时") }
                }
                Spacer(Modifier.height(14.dp))
                if (mode == TimerMode.COUNTDOWN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 45, 60).forEach { p ->
                            FilterChip(
                                selected = custom.isEmpty() && preset == p,
                                onClick = { preset = p; custom = "" },
                                label = { Text("${p}分钟") }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("自定义分钟") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("正计时不限时长，完成后手动结束。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(TimerDraft(title = "快速专注", category = Category.OTHER, mode = mode, plannedMinutes = if (mode == TimerMode.COUNTDOWN) minutes.coerceIn(1, 999) else 0, lock = true)) }) {
                Text("开始")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

internal fun weekLabel(dow: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(dow - 1) { "" }
