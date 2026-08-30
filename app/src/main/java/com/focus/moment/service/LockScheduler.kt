package com.focus.moment.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focus.moment.data.occursOn
import com.focus.moment.data.startMillisOn
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.LockPeriodEntity
import com.focus.moment.data.db.ScheduleEntity
import com.focus.moment.data.model.Category
import com.focus.moment.data.model.RepeatRule
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.model.TimerPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * 定时锁机调度器：
 * - 有开始时间的日程：到点自动进入锁机专注（严格模式）；
 * - 自定义锁机时段：开始时间自动锁机，结束时间自动解锁；
 * - 每次触发/开机后重新布防下一次。
 */
object LockScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    const val ACTION_SCHEDULE_LOCK = "com.focus.moment.SCHEDULE_LOCK"
    const val ACTION_PERIOD_START = "com.focus.moment.PERIOD_START"
    const val ACTION_PERIOD_END = "com.focus.moment.PERIOD_END"

    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_MODE = "extra_mode"
    const val EXTRA_MINUTES = "extra_minutes"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    const val EXTRA_PERIOD_ID = "extra_period_id"

    fun armAllAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { armAll(app) } }
    }

    suspend fun armAll(context: Context) {
        val db = AppDatabase.get(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        // 1. 日程
        db.scheduleDao().all().forEach { s ->
            val req = 2000 + (s.id.hashCode().toLong() and 0x7FFFFFFFL) % 1_000_000L
            val pi = pendingLock(context, req, s)
            am.cancel(pi)
            if (s.startTime != null) {
                val next = nextScheduleMillis(s, now)
                if (next != null) setAlarm(am, next, pi)
            }
        }

        // 2. 锁机时段
        db.lockPeriodDao().all().filter { it.enabled }.forEach { p ->
            val startReq = 3000 + (p.id.hashCode().toLong() and 0x7FFFFFFFL) % 1_000_000L
            val endReq = 4000 + (p.id.hashCode().toLong() and 0x7FFFFFFFL) % 1_000_000L
            val startPi = pendingPeriodStart(context, startReq, p)
            val endPi = pendingPeriodEnd(context, endReq, p)
            am.cancel(startPi); am.cancel(endPi)
            val nextStart = nextPeriodMillis(p, true, now)
            if (nextStart != null) setAlarm(am, nextStart, startPi)
            val nextEnd = nextPeriodMillis(p, false, now)
            if (nextEnd != null) setAlarm(am, nextEnd, endPi)
        }
    }

    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingLock(context: Context, req: Long, s: ScheduleEntity): PendingIntent {
        val i = Intent(context, LockAlarmReceiver::class.java).setAction(ACTION_SCHEDULE_LOCK)
            .putExtra(EXTRA_TITLE, s.title)
            .putExtra(EXTRA_CATEGORY, s.category)
            .putExtra(EXTRA_MODE, s.mode)
            .putExtra(EXTRA_MINUTES, s.plannedMinutes ?: 25)
            .putExtra(EXTRA_SCHEDULE_ID, s.id)
        return PendingIntent.getBroadcast(
            context, req.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingPeriodStart(context: Context, req: Long, p: LockPeriodEntity): PendingIntent {
        val i = Intent(context, LockAlarmReceiver::class.java)
            .setAction(ACTION_PERIOD_START)
            .putExtra(EXTRA_PERIOD_ID, p.id)
        return PendingIntent.getBroadcast(
            context, req.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingPeriodEnd(context: Context, req: Long, p: LockPeriodEntity): PendingIntent {
        val i = Intent(context, LockAlarmReceiver::class.java)
            .setAction(ACTION_PERIOD_END)
            .putExtra(EXTRA_PERIOD_ID, p.id)
        return PendingIntent.getBroadcast(
            context, req.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 日程下一次开始时间（毫秒），最多向后找 400 天 */
    private fun nextScheduleMillis(s: ScheduleEntity, after: Long): Long? {
        if (s.startTime == null) return null
        val anchor = runCatching { LocalDate.parse(s.date) }.getOrNull() ?: return null
        var d = anchor
        val today = LocalDate.now()
        if (d.isBefore(today.minusDays(1))) d = today.minusDays(1)
        repeat(400) {
            if (!d.isBefore(anchor) && s.occursOn(d)) {
                val ms = s.startMillisOn(d)
                if (ms != null && ms > after) return ms
            }
            d = d.plusDays(1)
        }
        return null
    }

    /** 锁机时段下一次开始/结束时间 */
    private fun nextPeriodMillis(p: LockPeriodEntity, isStart: Boolean, after: Long): Long? {
        val anchor = runCatching { LocalDate.parse(p.anchorDate) }.getOrNull() ?: return null
        val hhmm = if (isStart) p.startHHmm else p.endHHmm
        val t = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: return null
        var d = anchor
        val today = LocalDate.now()
        if (d.isBefore(today)) d = today
        repeat(400) {
            if (!d.isBefore(anchor) && periodOccursOn(p, d, anchor)) {
                val ms = d.atTime(t).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (ms > after) return ms
            }
            d = d.plusDays(1)
        }
        return null
    }

    private fun periodOccursOn(p: LockPeriodEntity, date: LocalDate, anchor: LocalDate): Boolean {
        if (date.isBefore(anchor)) return false
        return when (RepeatRule.from(p.repeatRule)) {
            RepeatRule.ONCE -> date == anchor
            RepeatRule.DAILY -> true
            RepeatRule.WEEKLY -> date.dayOfWeek == anchor.dayOfWeek
            RepeatRule.MONTHLY -> date.dayOfMonth == anchor.dayOfMonth
            RepeatRule.YEARLY -> date.monthValue == anchor.monthValue && date.dayOfMonth == anchor.dayOfMonth
            RepeatRule.WORKDAYS -> date.dayOfWeek.value in 1..5
            RepeatRule.CUSTOM -> p.repeatDays
                ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                ?.contains(date.dayOfWeek.value) == true
        }
    }

    /** 锁机时段时长（分钟，跨夜自动 +24h） */
    fun periodMinutes(p: LockPeriodEntity): Int {
        val st = runCatching { LocalTime.parse(p.startHHmm) }.getOrDefault(LocalTime.of(0, 0))
        val en = runCatching { LocalTime.parse(p.endHHmm) }.getOrDefault(LocalTime.of(1, 0))
        var mins = (en.toSecondOfDay() - st.toSecondOfDay()) / 60
        if (mins <= 0) mins += 24 * 60
        return mins
    }
}

/** 到点触发：启动锁机专注 / 结束锁机时段 */
class LockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val cur = FocusSessionState.state.value
                when (intent.action) {
                    LockScheduler.ACTION_SCHEDULE_LOCK -> {
                        if (cur.phase == TimerPhase.IDLE) {
                            val now = System.currentTimeMillis()
                            val mode = TimerMode.from(intent.getStringExtra(LockScheduler.EXTRA_MODE))
                            val minutes = intent.getIntExtra(LockScheduler.EXTRA_MINUTES, 25)
                            val scheduleId = intent.getStringExtra(LockScheduler.EXTRA_SCHEDULE_ID)
                            val title = intent.getStringExtra(LockScheduler.EXTRA_TITLE) ?: "专注"
                            FocusSessionState.set(
                                TimerState(
                                    phase = TimerPhase.RUNNING,
                                    scheduleId = scheduleId,
                                    title = title,
                                    category = Category.from(intent.getStringExtra(LockScheduler.EXTRA_CATEGORY)),
                                    mode = mode,
                                    plannedMinutes = minutes,
                                    lock = true,
                                    startedAt = now,
                                    endAt = if (mode == TimerMode.COUNTDOWN) now + minutes * 60_000L else 0L,
                                    nowTick = now
                                )
                            )
                            TimerService.start(
                                app,
                                TimerDraft(
                                    scheduleId = scheduleId, title = title,
                                    category = Category.from(intent.getStringExtra(LockScheduler.EXTRA_CATEGORY)),
                                    mode = mode, plannedMinutes = minutes, lock = true
                                )
                            )
                        }
                    }
                    LockScheduler.ACTION_PERIOD_START -> {
                        if (cur.phase == TimerPhase.IDLE) {
                            val periodId = intent.getStringExtra(LockScheduler.EXTRA_PERIOD_ID) ?: return@launch
                            val db = AppDatabase.get(app)
                            val p = db.lockPeriodDao().all().firstOrNull { it.id == periodId } ?: return@launch
                            val minutes = LockScheduler.periodMinutes(p)
                            val now = System.currentTimeMillis()
                            FocusSessionState.set(
                                TimerState(
                                    phase = TimerPhase.RUNNING,
                                    scheduleId = "period:$periodId",
                                    title = "锁机时段",
                                    category = Category.OTHER,
                                    mode = TimerMode.COUNTDOWN,
                                    plannedMinutes = minutes,
                                    lock = true,
                                    startedAt = now,
                                    endAt = now + minutes * 60_000L,
                                    nowTick = now,
                                    source = "LOCK"
                                )
                            )
                            TimerService.start(
                                app,
                                TimerDraft(
                                    scheduleId = "period:$periodId", title = "锁机时段",
                                    category = Category.OTHER, mode = TimerMode.COUNTDOWN,
                                    plannedMinutes = minutes, lock = true, source = "LOCK"
                                )
                            )
                        }
                    }
                    LockScheduler.ACTION_PERIOD_END -> {
                        val periodId = intent.getStringExtra(LockScheduler.EXTRA_PERIOD_ID)
                        if (cur.phase == TimerPhase.RUNNING && periodId != null &&
                            cur.scheduleId == "period:$periodId"
                        ) {
                            TimerService.complete(app, com.focus.moment.data.model.SessionStatus.COMPLETED)
                        }
                    }
                }
                // 重新布防下一次
                LockScheduler.armAll(app)
            } catch (_: Exception) {
            } finally {
                result.finish()
            }
        }
    }
}

/** 开机后重新布防所有定时锁机 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            LockScheduler.armAllAsync(context)
        }
    }
}
