package com.focus.moment.ui.schedule

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focus.moment.data.TimeFmt
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.ScheduleEntity
import com.focus.moment.data.model.Category
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.occursOn
import com.focus.moment.data.repeatLabel
import com.focus.moment.service.TimerDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).scheduleDao()

    val all = MutableStateFlow<List<ScheduleEntity>>(emptyList())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { all.value = dao.all() }
    }

    fun delete(s: ScheduleEntity) {
        viewModelScope.launch {
            dao.upsert(s.copy(deleted = true, updatedAt = System.currentTimeMillis()))
            com.focus.moment.service.LockScheduler.armAllAsync(getApplication())
            refresh()
        }
    }
}

@Composable
fun ScheduleScreen(
    settings: com.focus.moment.data.AppSettings,
    onStartFocus: (TimerDraft) -> Unit,
    onEditSchedule: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val vm = remember { ScheduleViewModel(app) }
    val all by vm.all.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var deleteTarget by remember { mutableStateOf<ScheduleEntity?>(null) }

    LaunchedEffect(all) { /* 保持引用以驱动刷新 */ }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditSchedule("new") }) {
                Icon(Icons.Filled.Add, contentDescription = "添加日程")
            }
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item(key = "title") {
                Text(
                    "日程",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // ---- 月份切换 ----
            item(key = "month_header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上月")
                    }
                    Text(
                        "${month.year}年${month.monthValue}月",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        month = YearMonth.now()
                        selected = LocalDate.now()
                    }) { Text("今天") }
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下月")
                    }
                }
            }

            // ---- 星期头 ----
            item(key = "week_header") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // ---- 日历格子 ----
            val firstDay = month.atDay(1)
            val leadingEmpty = firstDay.dayOfWeek.value - 1
            val totalCells = leadingEmpty + month.lengthOfMonth()
            val rows = (totalCells + 6) / 7
            items(rows, key = { "row_$it" }) { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    repeat(7) { cIdx ->
                        val cellIndex = r * 7 + cIdx
                        val day = cellIndex - leadingEmpty + 1
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val daySchedules = all.filter { it.occursOn(date) }
                            CalendarCell(
                                date = date,
                                selected = date == selected,
                                today = date == LocalDate.now(),
                                dotColors = daySchedules.take(3)
                                    .map { Category.from(it.category).colorHex }.distinct(),
                                modifier = Modifier.weight(1f),
                                onClick = { selected = date }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // ---- 选中日期的日程 ----
            item(key = "selected_header") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${selected.monthValue}月${selected.dayOfMonth}日 · ${weekLabel(selected.dayOfWeek.value)}" +
                        "（${all.count { it.occursOn(selected) }} 项）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            val list = all.filter { it.occursOn(selected) }.sortedBy { it.startTime ?: "99:99" }
            if (list.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("这一天没有日程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点 + 新建，到点自动进入锁机专注",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                items(list, key = { it.id + "_" + selected }) { s ->
                    ScheduleCard(
                        s = s,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                        onStartFocus = onStartFocus,
                        onEdit = { onEditSchedule(s.id) },
                        onDelete = { deleteTarget = s }
                    )
                }
            }
            item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除日程") },
            text = { Text("确定删除「${target.title}」吗？已完成的历史记录不受影响。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(target); deleteTarget = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun CalendarCell(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    dotColors: List<Long>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary
                    today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                dotColors.forEach { hex ->
                    Box(
                        Modifier.size(4.dp).clip(CircleShape).background(Color(hex))
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    s: ScheduleEntity,
    modifier: Modifier = Modifier,
    onStartFocus: (TimerDraft) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cat = Category.from(s.category)
    val mode = TimerMode.from(s.mode)
    Card(modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp, 44.dp).clip(RoundedCornerShape(3.dp)).background(Color(cat.colorHex)))
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
                        append(" · " + s.repeatLabel())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
            }
            OutlinedButton(onClick = {
                onStartFocus(
                    TimerDraft(
                        scheduleId = s.id, title = s.title, category = cat,
                        mode = mode, plannedMinutes = s.plannedMinutes ?: 25, lock = true
                    )
                )
            }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text("开始")
            }
        }
    }
}

private fun weekLabel(dow: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(dow - 1) { "" }
