package com.focus.moment.ui.lock

import android.app.AlarmManager
import android.app.Application
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.LockPeriodEntity
import com.focus.moment.data.model.Category
import com.focus.moment.data.model.RepeatRule
import com.focus.moment.data.model.TimerMode
import com.focus.moment.service.LockScheduler
import com.focus.moment.service.TimerDraft
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockScreen(
    settings: com.focus.moment.data.AppSettings,
    startFocus: (TimerDraft) -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(app).lockPeriodDao() }
    var periods by remember { mutableStateOf<List<LockPeriodEntity>>(emptyList()) }
    var version by remember { mutableStateOf(0) }

    var showBreak by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var showAddPeriod by remember { mutableStateOf(false) }

    LaunchedEffect(version) { periods = dao.all() }

    Scaffold(
        floatingActionButton = {
            if (!showAddPeriod) {
                FloatingActionButton(onClick = { showAddPeriod = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加自定义锁机时段")
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
        ) {
            Text(
                "锁机",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )

            // ---- 小憩 ----
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("小憩", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "不锁机的放松计时，适合待办集之间的休息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showBreak = true }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        Text("开始小憩")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- 快速锁机 ----
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("立刻开始快速锁机", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "选择锁机时长，立即进入严格锁机",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showQuick = true }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        Text("选择时长并锁机")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- 自定义锁机时段 ----
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("自定义锁机时段", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "到开始时间自动锁机，结束时间自动解锁",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (periods.isEmpty()) {
                        Text(
                            "还没有时段，点右下角 + 添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        periods.forEach { p ->
                            PeriodRow(
                                p = p,
                                onToggle = { v ->
                                    scope.launch {
                                        dao.upsert(p.copy(enabled = v, updatedAt = System.currentTimeMillis()))
                                        LockScheduler.armAllAsync(app)
                                        version++
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        dao.upsert(p.copy(deleted = true, updatedAt = System.currentTimeMillis()))
                                        LockScheduler.armAllAsync(app)
                                        version++
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showBreak) {
        DurationPickDialog(
            title = "小憩",
            desc = "选择小憩时长（不锁机）",
            defaultMinutes = 5,
            onDismiss = { showBreak = false },
            onConfirm = { min ->
                showBreak = false
                startFocus(
                    TimerDraft(title = "小憩", category = Category.OTHER,
                        mode = TimerMode.COUNTDOWN, plannedMinutes = min, lock = false)
                )
            }
        )
    }

    if (showQuick) {
        DurationPickDialog(
            title = "快速锁机",
            desc = "选择锁机时长，开始后严格锁机",
            defaultMinutes = 30,
            onDismiss = { showQuick = false },
            onConfirm = { min ->
                showQuick = false
                startFocus(
                    TimerDraft(title = "快速锁机", category = Category.OTHER,
                        mode = TimerMode.COUNTDOWN, plannedMinutes = min, lock = true)
                )
            }
        )
    }

    if (showAddPeriod) {
        AddPeriodDialog(
            onDismiss = { showAddPeriod = false },
            onSaved = { showAddPeriod = false; version++ }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPickDialog(
    title: String,
    desc: String,
    defaultMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var picked by remember { mutableStateOf(defaultMinutes) }
    var custom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 25, 30, 45, 60).forEach { m ->
                        FilterChip(
                            selected = !custom && picked == m,
                            onClick = { custom = false; picked = m },
                            label = { Text("${m}分钟") }
                        )
                    }
                    FilterChip(selected = custom, onClick = { custom = true }, label = { Text("自定义") })
                }
                if (custom) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("自定义时长（分钟）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val min = (if (custom) customText.toIntOrNull() ?: 0 else picked).coerceIn(1, 720)
                onConfirm(min)
            }) { Text("开始") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PeriodRow(p: LockPeriodEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${p.startHHmm} - ${p.endHHmm}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    repeatLabelOf(p.repeatRule, p.repeatDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = p.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun repeatLabelOf(rule: String, days: String?): String = when (RepeatRule.from(rule)) {
    RepeatRule.ONCE -> "单次"
    RepeatRule.DAILY -> "每天"
    RepeatRule.WEEKLY -> "每周"
    RepeatRule.MONTHLY -> "每月"
    RepeatRule.YEARLY -> "每年"
    RepeatRule.WORKDAYS -> "工作日"
    RepeatRule.CUSTOM -> "自定义（" + (days?.split(',')?.joinToString("、") { d ->
        listOf("一", "二", "三", "四", "五", "六", "日").getOrElse((d.trim().toIntOrNull() ?: 1) - 1) { "一" }
    } ?: "") + "）"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPeriodDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var startTime by remember { mutableStateOf("22:00") }
    var endTime by remember { mutableStateOf("23:00") }
    var rule by remember { mutableStateOf(RepeatRule.DAILY) }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var showStartPick by remember { mutableStateOf(false) }
    var showEndPick by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义锁机时段") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showStartPick = true }, modifier = Modifier.weight(1f)) {
                        Text("开始 $startTime")
                    }
                    OutlinedButton(onClick = { showEndPick = true }, modifier = Modifier.weight(1f)) {
                        Text("结束 $endTime")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("重复", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatRule.entries.forEach { r ->
                        FilterChip(selected = rule == r, onClick = { rule = r }, label = { Text(r.label) })
                    }
                }
                if (rule == RepeatRule.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日").forEach { (num, label) ->
                            FilterChip(
                                selected = num in days,
                                onClick = { days = if (num in days) days - num else days + num },
                                label = { Text("周$label") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    AppDatabase.get(ctx.applicationContext).lockPeriodDao().upsert(
                        LockPeriodEntity(
                            id = UUID.randomUUID().toString(),
                            startHHmm = startTime,
                            endHHmm = endTime,
                            repeatRule = rule.name,
                            repeatDays = if (rule == RepeatRule.CUSTOM) days.sorted().joinToString(",") else null,
                            anchorDate = java.time.LocalDate.now().toString(),
                            enabled = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    LockScheduler.armAllAsync(ctx)
                    onSaved()
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showStartPick) {
        TimePickSideEffect("开始时间", startTime) { t -> startTime = t; showStartPick = false }
    }
    if (showEndPick) {
        TimePickSideEffect("结束时间", endTime) { t -> endTime = t; showEndPick = false }
    }
}

@Composable
private fun TimePickSideEffect(label: String, initial: String, onPicked: (String) -> Unit) {
    val ctx = LocalContext.current
    SideEffect {
        val init = runCatching { LocalTime.parse(initial) }.getOrDefault(LocalTime.of(22, 0))
        TimePickerDialog(
            ctx,
            { _, h, m ->
                onPicked("%02d:%02d".format(h, m))
            },
            init.hour, init.minute, true
        ).apply { show() }
    }
}
