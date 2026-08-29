package com.focus.moment.ui.schedule

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.focus.moment.ui.home.weekLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val vm = remember { ScheduleViewModel(LocalContext.current.applicationContext as Application) }
    val all by vm.all.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<ScheduleEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditSchedule("new") }) {
                Icon(Icons.Filled.Add, contentDescription = "添加日程")
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                "日程",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            TabRow(selectedTabIndex = tab) {
                listOf("今天", "本周", "全部").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            val today = LocalDate.now()
            when (tab) {
                0 -> {
                    val list = all.filter { it.occursOn(today) }.sortedBy { it.startTime ?: "99:99" }
                    if (list.isEmpty()) EmptyHint("今天没有日程") else LazyColumn(Modifier.padding(16.dp)) {
                        items(list, key = { it.id }) { s ->
                            ScheduleCard(s, onStartFocus, { onEditSchedule(s.id) }, { deleteTarget = s })
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                1 -> {
                    val days = (0..6).map { today.plusDays(it.toLong()) }
                    LazyColumn(Modifier.padding(16.dp)) {
                        days.forEach { d ->
                            val list = all.filter { it.occursOn(d) }.sortedBy { it.startTime ?: "99:99" }
                            item(key = "h_${d}") {
                                Text(
                                    "${d.monthValue}月${d.dayOfMonth}日 ${weekLabel(d.dayOfWeek.value)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                                )
                            }
                            if (list.isEmpty()) {
                                item(key = "e_${d}") { Text("—", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall) }
                            } else {
                                items(list, key = { it.id + "_${d}" }) { s ->
                                    ScheduleCard(s, onStartFocus, { onEditSchedule(s.id) }, { deleteTarget = s })
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (all.isEmpty()) EmptyHint("还没有日程，点 + 新建")
                    else LazyColumn(Modifier.padding(16.dp)) {
                        items(all, key = { it.id }) { s ->
                            ScheduleCard(s, onStartFocus, { onEditSchedule(s.id) }, { deleteTarget = s }, showRepeat = true)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
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
private fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleCard(
    s: ScheduleEntity,
    onStartFocus: (TimerDraft) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showRepeat: Boolean = false
) {
    val cat = Category.from(s.category)
    val mode = TimerMode.from(s.mode)
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
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
                        if (showRepeat) append(" · " + s.repeatLabel())
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
