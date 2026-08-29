package com.focus.moment.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

data class PieSlice(val label: String, val value: Float, val color: Color)

/** 分类时间饼状图（环形） */
@Composable
fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val ringColor = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.size(190.dp)) {
                if (total <= 0f) {
                    drawCircle(color = ringColor.copy(alpha = 0.3f), radius = size.minDimension / 2 - 20f, style = Stroke(width = 40f))
                } else {
                    var start = -90f
                    val stroke = 44f
                    val d = size.minDimension - stroke
                    val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                    slices.filter { it.value > 0 }.forEach { s ->
                        val sweep = 360f * (s.value / total)
                        drawArc(
                            color = s.color,
                            startAngle = start,
                            sweepAngle = sweep - 1.5f,
                            useCenter = false,
                            topLeft = tl,
                            size = Size(d, d),
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("总计", style = MaterialTheme.typography.labelMedium)
                Text(
                    if (total >= 60) "${(total / 60).toInt()}h${(total % 60).toInt()}m" else "${total.toInt()}m",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            slices.forEach { LegendItem(it) }
        }
    }
}

@Composable
private fun LegendItem(slice: PieSlice) {
    if (slice.value <= 0f) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
        Spacer(Modifier.width(4.dp))
        Text("${slice.label} ${slice.value.toInt()}m", style = MaterialTheme.typography.labelSmall)
    }
}

/** 每日时长柱状图 */
@Composable
fun BarChart(labels: List<String>, values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val maxV = max(values.maxOrNull() ?: 1f, 1f)
    val barColor = color
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Canvas(modifier.height(170.dp)) {
        val chartH = size.height * 0.82f
        val labelH = size.height - chartH
        paint.textSize = labelH * 0.62f
        paint.color = labelColor
        val slot = size.width / max(labels.size, 1)
        val barW = slot * 0.5f
        labels.forEachIndexed { i, label ->
            val v = values.getOrElse(i) { 0f }
            val h = chartH * (v / maxV)
            val x = slot * i + (slot - barW) / 2
            drawRoundRect(
                color = barColor.copy(alpha = if (v > 0f) 0.9f else 0.15f),
                topLeft = Offset(x, chartH - h),
                size = Size(barW, max(h, 4f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
            )
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(label, slot * i + slot / 2, size.height - 4f, paint)
            }
        }
    }
}

/** 专注趋势折线图 */
@Composable
fun LineChart(labels: List<String>, values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val maxV = max(values.maxOrNull() ?: 1f, 1f)
    val lineColor = color
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Canvas(modifier.height(170.dp)) {
        val chartH = size.height * 0.8f
        val labelH = size.height - chartH
        paint.textSize = labelH * 0.6f
        paint.color = labelColor
        val n = labels.size
        if (n < 2) return@Canvas
        val stepX = size.width / (n - 1)
        fun pt(i: Int): Offset = Offset(stepX * i, chartH - (chartH - 10f) * (values.getOrElse(i) { 0f } / maxV))
        val path = Path()
        for (i in 0 until n) {
            val p = pt(i)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
        for (i in 0 until n) {
            val p = pt(i)
            drawCircle(color = lineColor, radius = 7f, center = p)
            if (n <= 10 || i % ceil(n / 8.0).toInt() == 0) {
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText(labels[i], p.x, size.height - 2f, paint)
                }
            }
        }
        drawLine(axisColor, Offset(0f, chartH), Offset(size.width, chartH), strokeWidth = 2f)
    }
}

/** 打卡日历热力图（一个月） */
@Composable
fun HeatmapCalendar(month: YearMonth, minutesByDay: Map<LocalDate, Float>, color: Color, modifier: Modifier = Modifier) {
    val maxV = max((minutesByDay.values.maxOrNull() ?: 1f), 1f)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val weekHeader = listOf("一", "二", "三", "四", "五", "六", "日")
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekHeader.forEach {
                Text(
                    it, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val firstDay = month.atDay(1)
        val leadingEmpty = firstDay.dayOfWeek.value - 1
        val totalCells = leadingEmpty + month.lengthOfMonth()
        val rows = (totalCells + 6) / 7
        repeat(rows) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { cIdx ->
                    val cellIndex = r * 7 + cIdx
                    val day = cellIndex - leadingEmpty + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        val v = minutesByDay[date] ?: 0f
                        val alpha = if (v <= 0f) 0f else (0.25f + 0.75f * (v / maxV)).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .width(30.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (v <= 0f) empty else color.copy(alpha = alpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (v > 0f && alpha > 0.55f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.width(30.dp).height(30.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 星期几名称（缩写） */
fun dayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW, Locale.CHINESE)
