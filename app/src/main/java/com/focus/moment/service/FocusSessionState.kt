package com.focus.moment.service

import com.focus.moment.data.model.Category
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.model.TimerPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 一次专注的启动参数 */
data class TimerDraft(
    val scheduleId: String? = null,
    val title: String = "专注",
    val category: Category = Category.OTHER,
    val mode: TimerMode = TimerMode.COUNTDOWN,
    val plannedMinutes: Int = 25,
    val lock: Boolean = false
)

/** 计时页共享状态（服务与 UI 之间的桥梁） */
data class TimerState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val scheduleId: String? = null,
    val title: String = "",
    val category: Category = Category.OTHER,
    val mode: TimerMode = TimerMode.COUNTDOWN,
    val plannedMinutes: Int = 25,
    val lock: Boolean = false,
    val startedAt: Long = 0L,
    val endAt: Long = 0L,
    val nowTick: Long = 0L,       // 每秒刷新，驱动 UI
    val finishedSeconds: Int = 0  // 结束时实际专注秒数
)

object FocusSessionState {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state

    fun set(s: TimerState) { _state.value = s }

    fun update(f: (TimerState) -> TimerState) {
        _state.value = f(_state.value)
    }
}

/** 锁机开关：无障碍服务据此决定是否拉回前台 */
object FocusLockController {
    @Volatile
    var lockActive: Boolean = false
}
