package com.focus.moment.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.focus.moment.MainActivity
import com.focus.moment.R
import com.focus.moment.audio.AlarmPlayer
import com.focus.moment.audio.WhiteNoiseEngine
import com.focus.moment.data.SettingsStore
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.SessionEntity
import com.focus.moment.data.model.AlarmSound
import com.focus.moment.data.model.RemindMode
import com.focus.moment.data.model.SessionStatus
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.model.WhiteNoiseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * 计时前台服务：倒计时驱动、锁机控制、勿扰、白噪音播放、结束提醒、会话入库。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var wakelock: PowerManager.WakeLock? = null
    private var prevDndFilter = 0
    private var dndApplied = false
    private var noiseType: WhiteNoiseType = WhiteNoiseType.RAIN

    companion object {
        private const val CHANNEL_ID = "focus_timer"
        private const val NOTI_ID = 1001
        private const val PREFS = "focus_timer"

        private const val ACTION_START = "com.focus.moment.START"
        private const val ACTION_COMPLETE = "com.focus.moment.COMPLETE"
        private const val ACTION_NOISE_START = "com.focus.moment.NOISE_START"
        private const val ACTION_NOISE_STOP = "com.focus.moment.NOISE_STOP"
        private const val ACTION_NOISE_TYPE = "com.focus.moment.NOISE_TYPE"
        private const val ACTION_NOISE_VOLUME = "com.focus.moment.NOISE_VOLUME"

        private const val E_TITLE = "title"
        private const val E_CATEGORY = "category"
        private const val E_MODE = "mode"
        private const val E_MINUTES = "minutes"
        private const val E_LOCK = "lock"
        private const val E_SCHEDULE_ID = "schedule_id"
        private const val E_STATUS = "status"
        private const val E_NOISE = "noise"
        private const val E_VOLUME = "volume"

        fun start(context: Context, draft: TimerDraft) {
            val i = Intent(context, TimerService::class.java).setAction(ACTION_START)
                .putExtra(E_TITLE, draft.title)
                .putExtra(E_CATEGORY, draft.category.name)
                .putExtra(E_MODE, draft.mode.name)
                .putExtra(E_MINUTES, draft.plannedMinutes)
                .putExtra(E_LOCK, draft.lock)
                .putExtra(E_SCHEDULE_ID, draft.scheduleId)
            context.startForegroundService(i)
        }

        /** 结束会话并入库：status = COMPLETED / EARLY_FINISH / ABANDONED */
        fun complete(context: Context, status: SessionStatus) {
            context.startService(
                Intent(context, TimerService::class.java).setAction(ACTION_COMPLETE)
                    .putExtra(E_STATUS, status.name)
            )
        }

        fun noiseStart(context: Context, type: WhiteNoiseType, volume: Float) {
            context.startService(
                Intent(context, TimerService::class.java).setAction(ACTION_NOISE_START)
                    .putExtra(E_NOISE, type.name).putExtra(E_VOLUME, volume)
            )
        }

        fun noiseStop(context: Context) {
            context.startService(Intent(context, TimerService::class.java).setAction(ACTION_NOISE_STOP))
        }

        fun noiseChange(context: Context, type: WhiteNoiseType?, volume: Float?) {
            val i = Intent(context, TimerService::class.java)
            if (type != null) i.setAction(ACTION_NOISE_TYPE).putExtra(E_NOISE, type.name)
            else i.setAction(ACTION_NOISE_VOLUME).putExtra(E_VOLUME, volume ?: 0.7f)
            context.startService(i)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        createChannel()
        restoreIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_COMPLETE -> handleComplete(
                runCatching { SessionStatus.from(intent.getStringExtra(E_STATUS)) }.getOrDefault(SessionStatus.COMPLETED),
                manual = true
            )
            ACTION_NOISE_START -> {
                noiseType = WhiteNoiseType.from(intent.getStringExtra(E_NOISE))
                val vol = intent.getFloatExtra(E_VOLUME, 0.7f)
                WhiteNoiseEngine.start(noiseType, vol)
            }
            ACTION_NOISE_STOP -> WhiteNoiseEngine.stop()
            ACTION_NOISE_TYPE -> {
                noiseType = WhiteNoiseType.from(intent.getStringExtra(E_NOISE))
                WhiteNoiseEngine.setType(noiseType)
            }
            ACTION_NOISE_VOLUME -> WhiteNoiseEngine.setVolume(intent.getFloatExtra(E_VOLUME, 0.7f))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    // ---------- 流程 ----------

    private fun handleStart(i: Intent) {
        val settings = runBlocking { SettingsStore(this@TimerService).current() }
        val title = i.getStringExtra(E_TITLE) ?: "专注"
        val category = i.getStringExtra(E_CATEGORY) ?: "OTHER"
        val mode = TimerMode.from(i.getStringExtra(E_MODE))
        val minutes = i.getIntExtra(E_MINUTES, 25)
        val lock = i.getBooleanExtra(E_LOCK, false)
        val scheduleId = i.getStringExtra(E_SCHEDULE_ID)
        val now = System.currentTimeMillis()

        val state = TimerState(
            phase = TimerPhase_RUNNING(),
            scheduleId = scheduleId,
            title = title,
            category = com.focus.moment.data.model.Category.from(category),
            mode = mode,
            plannedMinutes = minutes,
            lock = lock,
            startedAt = now,
            endAt = if (mode == TimerMode.COUNTDOWN) now + minutes * 60_000L else 0L,
            nowTick = now
        )
        FocusSessionState.set(state)
        FocusLockController.lockActive = lock && settings.lockMode == "STRICT"

        startForegroundCompat(title)
        acquireWakelock()
        applyDnd(settings.lockMode == "STRICT" && lock)

        // 崩溃恢复持久化
        prefs.edit()
            .putBoolean("running", true)
            .putString("title", title).putString("category", category)
            .putString("mode", mode.name).putInt("minutes", minutes)
            .putBoolean("lock", lock).putString("schedule_id", scheduleId)
            .putLong("started_at", now).putLong("end_at", state.endAt)
            .apply()

        // 白噪音自动播放
        if (settings.noiseAutoPlay) {
            noiseType = WhiteNoiseType.from(settings.noiseDefault)
            WhiteNoiseEngine.start(noiseType, settings.noiseVolume)
        }

        tick()
    }

    private fun tick() {
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, 1000)
    }

    private val tickRunnable = Runnable {
        val s = FocusSessionState.state.value
        if (s.phase != TimerPhase_RUNNING()) return@Runnable
        val now = System.currentTimeMillis()
        FocusSessionState.update { it.copy(nowTick = now) }
        if (s.mode == TimerMode.COUNTDOWN && now >= s.endAt) {
            finishCountdown()
        } else {
            tick()
        }
    }

    /** 倒计时自然结束：解锁 + 铃声/震动提醒 */
    private fun finishCountdown() {
        val s = FocusSessionState.state.value
        val settings = runBlocking { SettingsStore(this@TimerService).current() }
        FocusSessionState.update {
            it.copy(
                phase = com.focus.moment.data.model.TimerPhase.FINISHED,
                finishedSeconds = it.plannedMinutes * 60
            )
        }
        prefs.edit().putBoolean("running", false).apply()
        releaseWakelock()
        restoreDnd()
        FocusLockController.lockActive = false
        WhiteNoiseEngine.stop()
        AlarmPlayer.start(
            this,
            AlarmSound.from(settings.alarmSound),
            RemindMode.from(settings.remindMode)
        )
        showFinishedNotification(s.title)
    }

    /** 手动完成/提前完成/放弃 */
    private fun handleComplete(status: SessionStatus, manual: Boolean) {
        val s = FocusSessionState.state.value
        if (s.phase == TimerPhase_IDLE()) { stopSelf(); return }
        val now = System.currentTimeMillis()
        val seconds = if (s.phase == com.focus.moment.data.model.TimerPhase.FINISHED) {
            s.finishedSeconds
        } else {
            ((now - s.startedAt) / 1000).toInt()
        }
        AlarmPlayer.stop()
        WhiteNoiseEngine.stop()
        releaseWakelock()
        restoreDnd()
        FocusLockController.lockActive = false
        prefs.edit().putBoolean("running", false).apply()

        if (seconds > 5) {
            scope.launch {
                AppDatabase.get(this@TimerService).sessionDao().upsert(
                    SessionEntity(
                        id = UUID.randomUUID().toString(),
                        scheduleId = s.scheduleId,
                        title = s.title,
                        category = s.category.name,
                        startedAt = s.startedAt,
                        endedAt = now,
                        plannedMinutes = s.plannedMinutes,
                        actualSeconds = seconds,
                        mode = s.mode.name,
                        status = status.name,
                        updatedAt = now
                    )
                )
            }
        }
        FocusSessionState.set(TimerState())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 进程被杀后重启恢复 */
    private fun restoreIfNeeded() {
        if (!prefs.getBoolean("running", false)) return
        val now = System.currentTimeMillis()
        val startedAt = prefs.getLong("started_at", now)
        val endAt = prefs.getLong("end_at", 0L)
        val mode = TimerMode.from(prefs.getString("mode", null))
        val minutes = prefs.getInt("minutes", 25)
        val lock = prefs.getBoolean("lock", false)
        val settings = runBlocking { SettingsStore(this@TimerService).current() }
        val state = TimerState(
            phase = TimerPhase_RUNNING(),
            scheduleId = prefs.getString("schedule_id", null),
            title = prefs.getString("title", "专注") ?: "专注",
            category = com.focus.moment.data.model.Category.from(prefs.getString("category", null)),
            mode = mode,
            plannedMinutes = minutes,
            lock = lock,
            startedAt = startedAt,
            endAt = endAt,
            nowTick = now
        )
        FocusSessionState.set(state)
        FocusLockController.lockActive = lock && settings.lockMode == "STRICT"
        startForegroundCompat(state.title)
        acquireWakelock()
        applyDnd(settings.lockMode == "STRICT" && lock)
        if (mode == TimerMode.COUNTDOWN && now >= endAt) {
            finishCountdown()
        } else {
            tick()
        }
    }

    // ---------- 系统能力 ----------

    private fun startForegroundCompat(title: String) {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("专注时刻 · 自律模式")
            .setContentText("正在专注：$title")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this, NOTI_ID, notif,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun showFinishedNotification(title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("专注完成！")
            .setContentText("「$title」已结束，点击停止提示音")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTI_ID + 1, notif) }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "专注计时", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun acquireWakelock() {
        if (wakelock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusMoment::timer").apply {
                acquire(4 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakelock() {
        runCatching { wakelock?.release() }
        wakelock = null
    }

    private fun applyDnd(strictLock: Boolean) {
        if (!strictLock) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        prevDndFilter = nm.currentInterruptionFilter
        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
        dndApplied = true
    }

    private fun restoreDnd() {
        if (!dndApplied) return
        dndApplied = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        runCatching { nm.setInterruptionFilter(prevDndFilter) }
    }

    // TimerPhase 简写，避免过长引用
    private fun TimerPhase_RUNNING() = com.focus.moment.data.model.TimerPhase.RUNNING
    private fun TimerPhase_IDLE() = com.focus.moment.data.model.TimerPhase.IDLE
}
