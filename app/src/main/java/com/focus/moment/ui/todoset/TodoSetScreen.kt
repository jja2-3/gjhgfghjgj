package com.focus.moment.ui.todoset

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
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
import com.focus.moment.data.db.SessionEntity
import com.focus.moment.data.db.TodoItemEntity
import com.focus.moment.data.db.TodoSetEntity
import com.focus.moment.service.TodoSetRunner
import com.focus.moment.service.TimerDraft
import com.focus.moment.data.model.TimerPhase
import com.focus.moment.ui.charts.PieChart
import com.focus.moment.ui.charts.PieSlice
import com.focus.moment.ui.todo.AddTodoDialog
import kotlinx.coroutines.launch
import java.util.UUID

private val SET_COLORS = listOf(
    Color(0xFF4C8DFF), Color(0xFFFF9F43), Color(0xFF2ED573),
    Color(0xFF9C6BFF), Color(0xFFFF6B81), Color(0xFF2AC3D4),
    Color(0xFFC77826), Color(0xFF7C5CD6)
)

// ---------------------------------------------------------------------------
// 待办集列表页
// ---------------------------------------------------------------------------

@Composable
fun TodoSetScreen(
    settings: com.focus.moment.data.AppSettings,
    startFocus: (TimerDraft) -> Unit,
    onOpenSet: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val scope = rememberCoroutineScope()
    val setDao = remember { AppDatabase.get(app).todoSetDao() }
    val itemDao = remember { AppDatabase.get(app).todoItemDao() }
    val sessionDao = remember { AppDatabase.get(app).sessionDao() }

    var sets by remember { mutableStateOf<List<TodoSetEntity>>(emptyList()) }
    var itemGroups by remember { mutableStateOf<Map<String, List<TodoItemEntity>>>(emptyMap()) }
    var allSessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var showNewSet by remember { mutableStateOf(false) }
    var startTarget by remember { mutableStateOf<TodoSetEntity?>(null) }

    LaunchedEffect(version) {
        sets = setDao.all()
        allSessions = sessionDao.since(0)
        val m = mutableMapOf<String, List<TodoItemEntity>>()
        sets.forEach { m[it.id] = itemDao.bySet(it.id) }
        itemGroups = m
    }

    val runner by TodoSetRunner.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewSet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建待办集")
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                "待办集",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            if (runner.setId != null && runner.phase != "IDLE") {
                RunnerStatusCard(
                    onStartFocus = startFocus,
                    onStop = { TodoSetRunner.stop(); version++ }
                )
            }
            if (sets.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 90.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有待办集", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "把一组待办打包成套，一键顺序执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                    items(sets, key = { it.id }) { s ->
                        val todos = itemGroups[s.id] ?: emptyList()
                        val doneMin = remember(allSessions, s.id) {
                            allSessions.filter { sess ->
                                itemGroups[s.id]?.any { it.id == sess.todoItemId } == true
                            }.sumOf { it.actualSeconds.toLong() } / 60
                        }
                        SetCard(
                            set = s,
                            todoCount = todos.size,
                            doneMinutes = doneMin.toInt(),
                            running = TodoSetRunner.isActiveFor(s.id),
                            onOpen = { onOpenSet(s.id) },
                            onStart = { startTarget = s },
                            onDelete = {
                                scope.launch {
                                    setDao.upsert(s.copy(deleted = true, updatedAt = System.currentTimeMillis()))
                                    version++
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    item(key = "all_stats_header") {
                        Text(
                            "全部数据（按待办集分类）",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )
                    }
                    item(key = "all_stats_chart") {
                        val slices = sets.mapIndexed { idx, s ->
                            val ids = itemGroups[s.id]?.map { it.id }?.toSet() ?: emptySet()
                            val min = allSessions
                                .filter { it.todoItemId != null && it.todoItemId in ids }
                                .sumOf { it.actualSeconds.toLong() } / 60f
                            PieSlice(s.name, min, SET_COLORS[idx % SET_COLORS.size])
                        }.filter { it.value > 0 }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                if (slices.isEmpty()) {
                                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    PieChart(slices)
                                }
                            }
                        }
                        Spacer(Modifier.height(60.dp))
                    }
                }
            }
        }
    }

    if (showNewSet) {
        NewSetDialog(
            onDismiss = { showNewSet = false },
            onSaved = { showNewSet = false; version++ }
        )
    }

    startTarget?.let { target ->
        val todos = itemGroups[target.id] ?: emptyList()
        AlertDialog(
            onDismissRequest = { startTarget = null },
            title = { Text("开始「${target.name}」") },
            text = {
                Text(
                    if (todos.isEmpty()) "该待办集还没有子待办，请先添加"
                    else "共 ${todos.size} 个待办。${if (target.autoContinue) "连续执行，每个完成后休息指定时长，全部完成后长休息 ${target.longRestMinutes} 分钟。" else "手动模式，每完成一个由你决定是否继续。"}"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = todos.isNotEmpty(),
                    onClick = {
                        TodoSetRunner.start(app, target, todos)
                        startTarget = null
                    }
                ) { Text("开始") }
            },
            dismissButton = { TextButton(onClick = { startTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun SetCard(
    set: TodoSetEntity,
    todoCount: Int,
    doneMinutes: Int,
    running: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    set.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append("$todoCount 个待办")
                        append(" · ${if (set.autoContinue) "连续执行" else "手动逐个"}")
                        append(" · 长休息 ${set.longRestMinutes} 分钟")
                        if (doneMinutes > 0) append(" · 已专注 ${TimeFmt.hmm(doneMinutes.toLong())}")
                        if (running) append(" · 进行中")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!running) {
                IconButton(onClick = onStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "开始", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** 顺序执行中的状态卡片 */
@Composable
private fun RunnerStatusCard(
    onStartFocus: (TimerDraft) -> Unit,
    onStop: () -> Unit
) {
    val ctx = LocalContext.current
    val runner by TodoSetRunner.state.collectAsState()
    val focus by com.focus.moment.service.FocusSessionState.state.collectAsState()
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("「${runner.setName}」", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "第 ${runner.index + 1}/${runner.titles.size} 个 · 已完成 ${runner.totalMinutes} 分钟",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            val statusText = when (runner.phase) {
                "FOCUS" -> "正在专注：${runner.titles.getOrElse(runner.index) { "" }}"
                "REST" -> "休息中，剩余 ${((runner.restEndAt - runner.nowTick) / 1000 / 60 + 1).coerceAtLeast(0)} 分钟"
                "WAIT_NEXT" -> "已完成一个，等你决定是否继续"
                "LONG_REST" -> "全部完成！长休息中，剩余 ${((runner.restEndAt - runner.nowTick) / 1000 / 60 + 1).coerceAtLeast(0)} 分钟"
                "DONE" -> "待办集已完成 🎉"
                else -> ""
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (runner.phase == "REST") {
                    OutlinedButton(onClick = { TodoSetRunner.skipRestNow() }) { Text("跳过休息") }
                }
                if (runner.phase == "WAIT_NEXT") {
                    Button(onClick = { TodoSetRunner.continueNext() }) { Text("继续下一个") }
                }
                if (runner.phase == "DONE") {
                    Button(onClick = onStop) { Text("完成") }
                }
                OutlinedButton(onClick = {
                    val running = focus.phase == TimerPhase.RUNNING
                    TodoSetRunner.stop()
                    if (running) {
                        com.focus.moment.service.TimerService.complete(
                            ctx, com.focus.moment.data.model.SessionStatus.COMPLETED
                        )
                    }
                    onStop()
                }) {
                    Text(if (runner.phase == "FOCUS") "结束当前并停止" else "停止整套")
                }
            }
        }
    }
}

@Composable
private fun NewSetDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var autoContinue by remember { mutableStateOf(true) }
    var longRestText by remember { mutableStateOf("15") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建待办集") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("待办集名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("连续执行子待办", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "完成后自动小憩并进入下一个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoContinue, onCheckedChange = { autoContinue = it })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = longRestText,
                    onValueChange = { longRestText = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("全部完成后的长休息（分钟）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        AppDatabase.get(ctx.applicationContext).todoSetDao().upsert(
                            TodoSetEntity(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                autoContinue = autoContinue,
                                longRestMinutes = (longRestText.toIntOrNull() ?: 15).coerceIn(0, 240),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        onSaved()
                    }
                }
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------------------------------------------------------------------
// 待办集详情页
// ---------------------------------------------------------------------------

@Composable
fun TodoSetDetailScreen(
    sid: String,
    startFocus: (TimerDraft) -> Unit,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val scope = rememberCoroutineScope()
    val setDao = remember { AppDatabase.get(app).todoSetDao() }
    val itemDao = remember { AppDatabase.get(app).todoItemDao() }
    val sessionDao = remember { AppDatabase.get(app).sessionDao() }

    var set by remember { mutableStateOf<TodoSetEntity?>(null) }
    var todos by remember { mutableStateOf<List<TodoItemEntity>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TodoItemEntity?>(null) }

    LaunchedEffect(version, sid) {
        set = setDao.byId(sid)
        todos = itemDao.bySet(sid)
        sessions = sessionDao.since(0).filter { s ->
            s.todoItemId != null && todos.any { it.id == s.todoItemId }
        }
    }

    set ?: run {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("加载中…")
            }
        }
        return
    }

    val runner by TodoSetRunner.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加子待办")
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                Column(Modifier.weight(1f)) {
                    Text(set!!.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${todos.size} 个子待办 · ${if (set!!.autoContinue) "连续执行" else "手动逐个"} · 长休息 ${set!!.longRestMinutes} 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 运行状态
            if (runner.setId == sid && runner.phase != "IDLE") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (runner.phase) {
                            "FOCUS" -> "进行中：第 ${runner.index + 1} 个 ${runner.titles.getOrElse(runner.index) { "" }}"
                            "REST" -> "休息中"
                            "WAIT_NEXT" -> "等待继续"
                            "LONG_REST" -> "长休息中"
                            "DONE" -> "已完成"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 开始整组
            Button(
                onClick = {
                    if (todos.isNotEmpty() && !TodoSetRunner.isActiveFor(sid)) {
                        TodoSetRunner.start(app, set!!, todos)
                        onBack()
                    }
                },
                enabled = todos.isNotEmpty() && !TodoSetRunner.isActiveFor(sid),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(46.dp)
            ) { Text(if (TodoSetRunner.isActiveFor(sid)) "该待办集正在执行" else "开始执行整套待办") }

            // 设置区：连续执行 / 长休息
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("连续执行子待办", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "关闭后每完成一个由你决定是否继续",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = set!!.autoContinue,
                            onCheckedChange = { v ->
                                scope.launch {
                                    setDao.upsert(set!!.copy(autoContinue = v, updatedAt = System.currentTimeMillis()))
                                    version++
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("长休息（分钟）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        var lr by remember(set!!.id, set!!.longRestMinutes) { mutableStateOf(set!!.longRestMinutes.toString()) }
                        OutlinedTextField(
                            value = lr,
                            onValueChange = { lr = it.filter { ch -> ch.isDigit() }.take(3) },
                            singleLine = true,
                            modifier = Modifier.width(90.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            scope.launch {
                                setDao.upsert(
                                    set!!.copy(longRestMinutes = (lr.toIntOrNull() ?: 15).coerceIn(0, 240), updatedAt = System.currentTimeMillis())
                                )
                                version++
                            }
                        }) { Text("保存") }
                    }
                }
            }

            // 子待办列表
            Text(
                "子待办（按顺序执行）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            todos.forEachIndexed { idx, t ->
                val timing = com.focus.moment.data.model.TodoTiming.from(t.timing)
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${idx + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    when (timing) {
                                        com.focus.moment.data.model.TodoTiming.COUNTDOWN -> append("倒计时 ${t.plannedMinutes ?: 25} 分钟")
                                        com.focus.moment.data.model.TodoTiming.COUNTUP -> append("正计时")
                                        com.focus.moment.data.model.TodoTiming.NONE -> append("不计时")
                                    }
                                    if (idx < todos.lastIndex) append(" · 完成后休息 ${t.restMinutes} 分钟")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            enabled = idx > 0,
                            onClick = { swapTodos(scope, itemDao, todos, idx, idx - 1) { version++ } }
                        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移", tint = MaterialTheme.colorScheme.outline) }
                        IconButton(
                            enabled = idx < todos.lastIndex,
                            onClick = { swapTodos(scope, itemDao, todos, idx, idx + 1) { version++ } }
                        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移", tint = MaterialTheme.colorScheme.outline) }
                        IconButton(onClick = { deleteTarget = t }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // 统计饼图
            Spacer(Modifier.height(14.dp))
            Text(
                "此待办集统计",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            val slices = todos.mapIndexed { idx, t ->
                val min = sessions.filter { it.todoItemId == t.id }
                    .sumOf { it.actualSeconds.toLong() } / 60f
                PieSlice(t.name, min, SET_COLORS[idx % SET_COLORS.size])
            }.filter { it.value > 0 }
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    if (slices.isEmpty()) {
                        Text("暂无专注数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        PieChart(slices)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "共 ${sessions.size} 次专注 · 累计 ${TimeFmt.hmm(sessions.sumOf { it.actualSeconds.toLong() / 60 })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showAdd) {
        AddTodoDialog(
            setId = sid,
            orderIdx = todos.size,
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false; version++ }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除子待办") },
            text = { Text("确定从待办集中移除「${target.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        itemDao.upsert(target.copy(deleted = true, updatedAt = System.currentTimeMillis()))
                        version++
                    }
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

private fun swapTodos(
    scope: kotlinx.coroutines.CoroutineScope,
    dao: com.focus.moment.data.db.TodoItemDao,
    todos: List<TodoItemEntity>,
    from: Int,
    to: Int,
    onDone: () -> Unit
) {
    if (from !in todos.indices || to !in todos.indices) return
    val a = todos[from]
    val b = todos[to]
    val now = System.currentTimeMillis()
    scope.launch {
        dao.upsert(a.copy(orderIdx = b.orderIdx, updatedAt = now))
        dao.upsert(b.copy(orderIdx = a.orderIdx, updatedAt = now))
        onDone()
    }
}
