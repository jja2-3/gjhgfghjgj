package com.focus.moment.ui.todo

import android.app.Application
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focus.moment.data.TimeFmt
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.TodoItemEntity
import com.focus.moment.data.model.Category
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.model.TodoTiming
import com.focus.moment.data.model.TodoType
import com.focus.moment.service.TodoSetRunner
import com.focus.moment.service.TimerDraft
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 待办类型主色 */
private fun typeColor(t: TodoType): Color = when (t) {
    TodoType.NORMAL -> Color(0xFF4C8DFF)
    TodoType.GOAL -> Color(0xFFFF9F43)
    TodoType.HABIT -> Color(0xFF2ED573)
}

@Composable
fun TodoScreen(
    settings: com.focus.moment.data.AppSettings,
    onStartFocus: (TimerDraft) -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(app).todoItemDao() }
    var todos by remember { mutableStateOf<List<TodoItemEntity>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }

    LaunchedEffect(version) { todos = dao.allPersonal() }

    val today = LocalDate.now().toString()

    fun refresh() { version++ }

    fun startTodo(t: TodoItemEntity) {
        val timing = TodoTiming.from(t.timing)
        val mode = when (timing) {
            TodoTiming.COUNTDOWN -> TimerMode.COUNTDOWN
            else -> TimerMode.COUNTUP
        }
        onStartFocus(
            TimerDraft(
                title = t.name,
                category = Category.OTHER,
                mode = mode,
                plannedMinutes = if (mode == TimerMode.COUNTDOWN) t.plannedMinutes ?: 25 else 0,
                lock = true,
                todoItemId = t.id,
                source = "TODO"
            )
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加待办")
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                "代办",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            if (todos.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 90.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有待办", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点右下角 + 新建一个吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                val undone = todos.filter { it.lastDoneDate != today }
                val done = todos.filter { it.lastDoneDate == today }
                LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                    items(undone, key = { it.id }) { t ->
                        TodoCard(
                            t = t,
                            done = false,
                            onStart = { startTodo(t) },
                            onDone = {
                                scope.launch {
                                    TodoSetRunner.markTodoDone(app, t.id)
                                    refresh()
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    dao.upsert(t.copy(deleted = true, updatedAt = System.currentTimeMillis()))
                                    refresh()
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (done.isNotEmpty()) {
                        item(key = "done_header") {
                            Text(
                                "今日已完成",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                            )
                        }
                        items(done, key = { "d_" + it.id }) { t ->
                            TodoCard(
                                t = t,
                                done = true,
                                onStart = { startTodo(t) },
                                onDone = {},
                                onDelete = {
                                    scope.launch {
                                        dao.upsert(t.copy(deleted = true, updatedAt = System.currentTimeMillis()))
                                        refresh()
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTodoDialog(
            setId = null,
            orderIdx = 0,
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false; refresh() }
        )
    }
}

@Composable
private fun TodoCard(
    t: TodoItemEntity,
    done: Boolean,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val type = TodoType.from(t.type)
    val timing = TodoTiming.from(t.timing)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(typeColor(type))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                val desc = buildString {
                    append(type.label)
                    when (timing) {
                        TodoTiming.COUNTDOWN -> append(" · 倒计时 ${t.plannedMinutes ?: 25} 分钟")
                        TodoTiming.COUNTUP -> append(" · 正计时")
                        TodoTiming.NONE -> append(" · 不计时")
                    }
                    if (!t.note.isNullOrBlank()) append("  📝")
                }
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!done) {
                if (timing == TodoTiming.NONE) {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Check, contentDescription = "完成", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = onStart) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "开始", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * 新建待办弹窗：
 * 名称输入 → 类型（普通/定目标/养习惯）→ 普通显示计时方式（倒计时/正计时/不计时）
 * 倒计时可选 25/35/自定义 → 底部"高级设置"蓝色小字 → 二级弹窗（第二天不再显示/备注/休息时间）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddTodoDialog(
    setId: String?,
    orderIdx: Int,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TodoType.NORMAL) }
    var timing by remember { mutableStateOf(TodoTiming.COUNTDOWN) }
    var minutes by remember { mutableStateOf(25) }
    var customMinutes by remember { mutableStateOf(false) }
    var minutesText by remember { mutableStateOf("25") }
    var targetText by remember { mutableStateOf("60") }
    // 高级设置
    var showAdvanced by remember { mutableStateOf(false) }
    var hideNextDay by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var restText by remember { mutableStateOf("") }

    fun save() {
        if (name.isBlank()) return
        val planned = if (timing == TodoTiming.COUNTDOWN) {
            (if (customMinutes) minutesText.toIntOrNull() ?: minutes else minutes).coerceIn(1, 999)
        } else null
        val target = if (type == TodoType.GOAL) (targetText.toIntOrNull() ?: 60).coerceIn(1, 9999) else null
        val entity = TodoItemEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            type = type.name,
            timing = timing.name,
            plannedMinutes = planned,
            targetMinutes = target,
            note = note.trim().ifBlank { null },
            hideNextDay = hideNextDay,
            restMinutes = restText.toIntOrNull()?.coerceIn(0, 120) ?: 5,
            setId = setId,
            orderIdx = orderIdx,
            updatedAt = System.currentTimeMillis()
        )
        scope.launch {
            AppDatabase.get(ctx.applicationContext).todoItemDao().upsert(entity)
            onSaved()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (setId == null) "新建待办" else "添加子待办") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("请输入待办名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodoType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }

                if (type == TodoType.GOAL) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("目标总时长（分钟）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (type == TodoType.NORMAL || type == TodoType.HABIT) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TodoTiming.entries.forEach { tg ->
                            FilterChip(selected = timing == tg, onClick = { timing = tg }, label = { Text(tg.label) })
                        }
                    }
                    if (timing == TodoTiming.COUNTDOWN) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !customMinutes && minutes == 25,
                                onClick = { customMinutes = false; minutes = 25 },
                                label = { Text("25分钟") }
                            )
                            FilterChip(
                                selected = !customMinutes && minutes == 35,
                                onClick = { customMinutes = false; minutes = 35 },
                                label = { Text("35分钟") }
                            )
                            FilterChip(
                                selected = customMinutes,
                                onClick = { customMinutes = true },
                                label = { Text("自定义") }
                            )
                        }
                        if (customMinutes) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = minutesText,
                                onValueChange = { minutesText = it.filter { ch -> ch.isDigit() }.take(3) },
                                label = { Text("自定义时长（分钟）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "高级设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2C6E9B),
                    modifier = Modifier
                        .clickable(onClick = { showAdvanced = true })
                        .padding(vertical = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { save() }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showAdvanced) {
        AlertDialog(
            onDismissRequest = { showAdvanced = false },
            title = { Text("高级设置") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("完成后第二天不再显示", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "适合一次性任务，完成次日自动隐藏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = hideNextDay, onCheckedChange = { hideNextDay = it })
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("任务备注（非必选）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restText,
                        onValueChange = { restText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("自定义休息时间（分钟，非必选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (restText.isBlank()) {
                        Text(
                            "不填则默认休息 5 分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdvanced = false }) { Text("确定") } }
        )
    }
}
