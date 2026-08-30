package com.focus.moment.ui.report

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focus.moment.data.TimeFmt
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.SessionEntity
import com.focus.moment.data.model.Category
import com.focus.moment.data.model.SessionStatus
import com.focus.moment.ui.charts.BarChart
import com.focus.moment.ui.charts.HeatmapCalendar
import com.focus.moment.ui.charts.LineChart
import com.focus.moment.ui.charts.PieChart
import com.focus.moment.ui.charts.PieSlice
import com.focus.moment.ui.theme.categoryColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class ReportViewModel(app: Application) : AndroidViewModel(app) {
    private val sessionDao = AppDatabase.get(app).sessionDao()

    val rangeSessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val streak = MutableStateFlow(0)

    init {
        viewModelScope.launch { streak.value = computeStreak() }
    }

    fun load(start: Long, end: Long) {
        viewModelScope.launch {
            rangeSessions.value = sessionDao.between(start, end)
        }
    }

    private suspend fun computeStreak(): Int {
        val all = sessionDao.since(0)
        var day = LocalDate.now()
        var count = 0
        // 今天还没专注不打断连续记录，从今天开始往前数
        if (all.none { TimeFmt.dayOf(it.startedAt) == day && it.status != "ABANDONED" }) {
            day = day.minusDays(1)
        }
        while (all.any { TimeFmt.dayOf(it.startedAt) == day && it.status != "ABANDONED" && it.actualSeconds > 0 }) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}

@Composable
fun ReportScreen() {
    val app = LocalContext.current.applicationContext as Application
    val vm = remember { ReportViewModel(app) }
    val sessions by vm.rangeSessions.collectAsState()
    val streak by vm.streak.collectAsState()
    var tab by remember { mutableStateOf(0) }

    val today = LocalDate.now()
    val range: Pair<Long, Long> = when (tab) {
        0 -> TimeFmt.startOfDay(today) to TimeFmt.startOfNextDay(today)
        1 -> {
            val monday = today.with(DayOfWeek.MONDAY)
            TimeFmt.startOfDay(monday) to TimeFmt.startOfDay(monday.plusDays(7))
        }
        else -> {
            val month = YearMonth.now()
            TimeFmt.startOfDay(month.atDay(1)) to TimeFmt.startOfDay(month.atEndOfMonth().plusDays(1))
        }
    }
    LaunchedEffect(tab) { vm.load(range.first, range.second) }

    val totalMin = sessions.sumOf { it.actualSeconds.toLong() } / 60
    val doneCount = sessions.count { it.status != SessionStatus.ABANDONED.name }
    val giveUpCount = sessions.count { it.status == SessionStatus.ABANDONED.name }
    val rate = if (sessions.isEmpty()) 0 else doneCount * 100 / sessions.size

    // 分类时间（饼状图）
    val byCategory = sessions.groupBy { it.category }
        .map { (c, list) ->
            val cat = Category.from(c)
            PieSlice(cat.label, list.sumOf { it.actualSeconds.toLong() } / 60f, categoryColor(cat.colorHex))
        }
        .sortedByDescending { it.value }

    Column(Modifier.fillMaxSize()) {
        Text(
            "报告",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        TabRow(selectedTabIndex = tab) {
            listOf("日", "周", "月").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 统计卡片
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("专注时长", TimeFmt.hmm(totalMin), Modifier.weight(1f))
                StatCard("完成率", "$rate%", Modifier.weight(1f))
                StatCard("连续自律", "${streak}天", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("完成", "$doneCount 次", Modifier.weight(1f))
                StatCard("放弃", "$giveUpCount 次", Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("时间分布", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    if (byCategory.isEmpty()) {
                        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        PieChart(byCategory)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            when (tab) {
                1 -> {
                    val monday = today.with(DayOfWeek.MONDAY)
                    val labels = (0..6).map { weekDayLabel(it) }
                    val values = (0..6).map { d ->
                        val day = monday.plusDays(d.toLong())
                        sessions.filter { TimeFmt.dayOf(it.startedAt) == day }
                            .sumOf { it.actualSeconds.toLong() } / 60f
                    }
                    ChartCard("每日专注（分钟）") {
                        BarChart(labels, values, MaterialTheme.colorScheme.primary)
                    }
                }
                2 -> {
                    val month = YearMonth.now()
                    val labels = (1..month.lengthOfMonth()).map { it.toString() }
                    val values = (1..month.lengthOfMonth()).map { d ->
                        sessions.filter { TimeFmt.dayOf(it.startedAt) == month.atDay(d) }
                            .sumOf { it.actualSeconds.toLong() } / 60f
                    }
                    ChartCard("专注趋势（分钟）") {
                        LineChart(labels, values, MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    val byDay = sessions.associate { TimeFmt.dayOf(it.startedAt) to it.actualSeconds / 60f }
                    ChartCard("打卡日历") {
                        HeatmapCalendar(month, byDay, MaterialTheme.colorScheme.primary)
                    }
                }
                else -> {
                    ChartCard("今日记录") {
                        if (sessions.isEmpty()) {
                            Text("今天还没有专注记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column {
                                sessions.forEach { s ->
                                    SessionRow(s)
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SessionRow(s: SessionEntity) {
    val cat = Category.from(s.category)
    val statusText = when (SessionStatus.from(s.status)) {
        SessionStatus.COMPLETED -> "已完成"
        SessionStatus.EARLY_FINISH -> "提前完成"
        SessionStatus.ABANDONED -> "已放弃"
    }
    val statusColor = when (SessionStatus.from(s.status)) {
        SessionStatus.ABANDONED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Row(Modifier.fillMaxWidth()) {
        Canvas(Modifier.width(5.dp).height(40.dp)) {
            drawRect(Color(cat.colorHex))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${TimeFmt.hhmm(s.startedAt)} - ${TimeFmt.hhmm(s.endedAt)} · ${s.actualSeconds / 60}分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor)
    }
}

private fun weekDayLabel(offset: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(offset) { "" }
