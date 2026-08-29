package com.focus.moment.ui.schedule

import android.app.Application
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.ScheduleEntity
import com.focus.moment.data.model.Category
import com.focus.moment.data.model.RepeatRule
import com.focus.moment.data.model.TimerMode
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScheduleScreen(sid: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = sid == "new"

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.STUDY) }
    var mode by remember { mutableStateOf(TimerMode.COUNTDOWN) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf<String?>(null) }
    var planned by remember { mutableStateOf("25") }
    var rule by remember { mutableStateOf(RepeatRule.ONCE) }
    var days by remember { mutableStateOf(setOf(1)) }
    var loaded by remember { mutableStateOf(isNew) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    LaunchedEffect(sid) {
        if (!isNew) {
            val s = AppDatabase.get(ctx.applicationContext as Application).scheduleDao().all()
                .firstOrNull { it.id == sid } ?: return@LaunchedEffect
            title = s.title
            category = Category.from(s.category)
            mode = TimerMode.from(s.mode)
            date = LocalDate.parse(s.date)
            startTime = s.startTime
            planned = (s.plannedMinutes ?: 25).toString()
            rule = RepeatRule.from(s.repeatRule)
            days = s.repeatDays?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: setOf()
            loaded = true
        }
    }

    if (!loaded) return

    fun save() {
        if (title.isBlank()) { err = "请填写日程名称"; return }
        val minutes = planned.toIntOrNull() ?: 25
        val entity = ScheduleEntity(
            id = if (isNew) UUID.randomUUID().toString() else sid,
            title = title.trim(),
            category = category.name,
            date = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            startTime = startTime,
            plannedMinutes = if (mode == TimerMode.COUNTDOWN) minutes.coerceIn(1, 999) else null,
            mode = mode.name,
            repeatRule = rule.name,
            repeatDays = if (rule == RepeatRule.CUSTOM) days.sorted().joinToString(",") else null,
            updatedAt = System.currentTimeMillis()
        )
        scope.launch {
            AppDatabase.get(ctx.applicationContext as Application).scheduleDao().upsert(entity)
            onDone()
        }
    }

    fun doDelete() {
        scope.launch {
            val dao = AppDatabase.get(ctx.applicationContext as Application).scheduleDao()
            dao.all().firstOrNull { it.id == sid }?.let {
                dao.upsert(it.copy(deleted = true, updatedAt = System.currentTimeMillis()))
            }
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(if (isNew) "新建日程" else "编辑日程", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it; err = "" },
            label = { Text("日程名称") },
            modifier = Modifier.fillMaxWidth()
        )
        if (err.isNotEmpty()) {
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))

        Text("分类", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
            }
        }
        Spacer(Modifier.height(14.dp))

        Text("计时方式", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
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
            OutlinedTextField(
                value = planned,
                onValueChange = { planned = it.filter { ch -> ch.isDigit() }.take(3) },
                label = { Text("时长（分钟）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
        }

        Text("日期与时间", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                Text("${date.monthValue}月${date.dayOfMonth}日")
            }
            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                Text(startTime ?: "设置开始时间")
            }
        }
        Spacer(Modifier.height(14.dp))

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
        Spacer(Modifier.height(24.dp))

        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(if (isNew) "创建日程" else "保存修改")
        }
        if (!isNew) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.padding(start = 4.dp))
                Text("删除日程", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }
        ) {
            DatePicker(state = st)
        }
    }

    if (showTime) {
        val init = runCatching { LocalTime.parse(startTime ?: "") }.getOrDefault(LocalTime.of(9, 0))
        val ts = rememberTimePickerState(initialHour = init.hour, initialMinute = init.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("开始时间") },
            text = { TimePicker(state = ts) },
            confirmButton = {
                TextButton(onClick = {
                    startTime = "%02d:%02d".format(ts.hour, ts.minute)
                    showTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("清除") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除日程") },
            text = { Text("确定删除该日程吗？") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; doDelete() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
