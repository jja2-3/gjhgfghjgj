package com.focus.moment.data

import com.focus.moment.data.db.ScheduleEntity
import com.focus.moment.data.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimeFmt {
    val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    fun dayOf(epochMillis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun startOfNextDay(d: LocalDate): Long = startOfDay(d.plusDays(1))
    fun hhmm(epochMillis: Long): String {
        val t = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return t.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    fun mmss(totalSeconds: Long): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%02d:%02d".format(m, s)
    }
    fun hmm(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}小时${m}分钟" else "${m}分钟"
    }
}

/** 日程在某天是否发生 */
fun ScheduleEntity.occursOn(date: LocalDate): Boolean {
    val anchor = runCatching { LocalDate.parse(this.date, TimeFmt.DAY) }.getOrNull() ?: return false
    if (date.isBefore(anchor)) return false
    return when (RepeatRule.from(this.repeatRule)) {
        RepeatRule.ONCE -> date == anchor
        RepeatRule.DAILY -> true
        RepeatRule.WEEKLY -> date.dayOfWeek == anchor.dayOfWeek
        RepeatRule.MONTHLY -> date.dayOfMonth == anchor.dayOfMonth
        RepeatRule.YEARLY -> date.monthValue == anchor.monthValue && date.dayOfMonth == anchor.dayOfMonth
        RepeatRule.WORKDAYS -> date.dayOfWeek.value in 1..5
        RepeatRule.CUSTOM -> this.repeatDays
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.contains(date.dayOfWeek.value) == true
    }
}

/** 日程在某天的开始时间（毫秒），无开始时间返回 null */
fun ScheduleEntity.startMillisOn(date: LocalDate): Long? {
    val st = this.startTime ?: return null
    return runCatching {
        date.atTime(LocalTime.parse(st)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

/** 日程展示文本，如 "09:00 · 每天" */
fun ScheduleEntity.repeatLabel(): String = when (RepeatRule.from(this.repeatRule)) {
    RepeatRule.ONCE -> "单次"
    RepeatRule.DAILY -> "每天"
    RepeatRule.WEEKLY -> "每周"
    RepeatRule.MONTHLY -> "每月"
    RepeatRule.YEARLY -> "每年"
    RepeatRule.WORKDAYS -> "工作日"
    RepeatRule.CUSTOM -> "自定义"
}
