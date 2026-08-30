package com.focus.moment.service

import android.content.Context
import android.content.Intent
import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.TodoItemEntity
import com.focus.moment.data.db.TodoSetEntity
import com.focus.moment.data.model.Category
import com.focus.moment.data.model.SessionStatus
import com.focus.moment.data.model.TodoTiming
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.model.TimerPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 待办集连续执行引擎：
 * 顺序执行子待办 → 完成后按每个待办的休息时间小憩 → 全部完成后长休息。
 */
object TodoSetRunner {

    data class RunnerState(
        val setId: String? = null,
        val setName: String = "",
        val titles: List<String> = emptyList(),
        val index: Int = 0,             // 当前执行到第几个
        val phase: String = "IDLE",     // IDLE / FOCUS / REST / WAIT_NEXT / LONG_REST / DONE
        val restEndAt: Long = 0L,
        val nowTick: Long = 0L,
        val autoContinue: Boolean = true,
        val totalMinutes: Int = 0       // 已完成累计分钟
    )

    private val _state = MutableStateFlow(RunnerState())
    val state: StateFlow<RunnerState> = _state

    private var scope: CoroutineScope? = null
    @Volatile private var skipRest = false
    private val proceedSignal = MutableStateFlow(0)

    fun isActive(): Boolean = _state.value.setId != null && _state.value.phase != "IDLE"
    fun isActiveFor(setId: String): Boolean = _state.value.setId == setId && isActive()

    fun currentSetId(): String? = if (isActive()) _state.value.setId else null

    /** 启动一套待办（autoContinue 时自动顺序执行） */
    fun start(context: Context, set: TodoSetEntity, todos: List<TodoItemEntity>) {
        if (FocusSessionState.state.value.phase == TimerPhase.RUNNING) return
        stop()
        val app = context.applicationContext
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        _state.value = RunnerState(
            setId = set.id, setName = set.name,
            titles = todos.map { it.name },
            autoContinue = set.autoContinue,
            phase = "FOCUS"
        )
        if (todos.isNotEmpty()) {
            s.launch { runSequence(app, todos) }
        }
    }

    fun skipRestNow() { skipRest = true }

    /** 非连续模式下，用户点击"继续下一个" */
    fun continueNext() { proceedSignal.value++ }

    fun stop() {
        scope?.cancel()
        scope = null
        skipRest = false
        proceedSignal.value = 0
        _state.value = RunnerState()
    }

    fun finishAndReset() { stop() }

    private suspend fun runSequence(app: Context, todos: List<TodoItemEntity>) {
        for ((i, todo) in todos.withIndex()) {
            if (_state.value.setId == null) return
            _state.value = _state.value.copy(index = i, phase = "FOCUS")
            launchTodo(app, todo)

            // 等待本次专注结束（RUNNING → FINISHED 或 IDLE）
            FocusSessionState.state.first { it.phase != TimerPhase.RUNNING }
            val endState = FocusSessionState.state.value
            if (endState.phase == TimerPhase.FINISHED) {
                // 倒计时自然结束：自动入库并停掉提示音
                TimerService.complete(app, SessionStatus.COMPLETED)
                _state.value = _state.value.copy(totalMinutes = _state.value.totalMinutes + (todo.plannedMinutes ?: 0))
            } else {
                // 手动结束（提前完成/完成/放弃），状态已被服务重置为 IDLE
                _state.value = _state.value.copy(totalMinutes = _state.value.totalMinutes + (todo.plannedMinutes ?: 0))
            }
            if (_state.value.setId == null) return

            if (i < todos.lastIndex) {
                if (!_state.value.autoContinue) {
                    // 非连续执行：等待用户点击"继续下一个"
                    _state.value = _state.value.copy(phase = "WAIT_NEXT", restEndAt = 0L)
                    val before = proceedSignal.value
                    proceedSignal.first { it != before || _state.value.setId == null }
                    if (_state.value.setId == null) return
                } else {
                    val restMin = todo.restMinutes.coerceAtLeast(0)
                    if (restMin > 0) {
                        skipRest = false
                        _state.value = _state.value.copy(
                            phase = "REST",
                            restEndAt = System.currentTimeMillis() + restMin * 60_000L
                        )
                        waitRest()
                        if (_state.value.setId == null) return
                    }
                }
            }
        }
        // 全部完成：长休息
        val set = runCatching { AppDatabase.get(app).todoSetDao().byId(_state.value.setId ?: "") }.getOrNull()
        val longRest = (set?.longRestMinutes ?: 15).coerceAtLeast(0)
        if (longRest > 0) {
            skipRest = false
            _state.value = _state.value.copy(
                phase = "LONG_REST",
                restEndAt = System.currentTimeMillis() + longRest * 60_000L
            )
            waitRest()
        }
        if (_state.value.setId != null) {
            _state.value = _state.value.copy(phase = "DONE", restEndAt = 0L)
        }
    }

    private suspend fun waitRest() {
        while (true) {
            val s = _state.value
            if (s.setId == null) return
            if (s.phase != "REST" && s.phase != "LONG_REST") return
            if (skipRest) { skipRest = false; return }
            if (System.currentTimeMillis() >= s.restEndAt) return
            _state.value = s.copy(nowTick = System.currentTimeMillis())
            delay(500)
        }
    }

    /** 启动单个待办专注，并跳到计时页 */
    fun launchTodo(app: Context, todo: TodoItemEntity) {
        val timing = TodoTiming.from(todo.timing)
        val now = System.currentTimeMillis()
        val minutes = todo.plannedMinutes ?: 25
        val mode = when (timing) {
            TodoTiming.COUNTDOWN -> TimerMode.COUNTDOWN
            else -> TimerMode.COUNTUP
        }
        FocusSessionState.set(
            TimerState(
                phase = TimerPhase.RUNNING,
                scheduleId = null,
                title = todo.name,
                category = Category.OTHER,
                mode = mode,
                plannedMinutes = if (mode == TimerMode.COUNTDOWN) minutes else 0,
                lock = true,
                startedAt = now,
                endAt = if (mode == TimerMode.COUNTDOWN) now + minutes * 60_000L else 0L,
                nowTick = now,
                todoItemId = todo.id,
                source = "TODO"
            )
        )
        TimerService.start(
            app,
            TimerDraft(
                title = todo.name,
                category = Category.OTHER,
                mode = mode,
                plannedMinutes = if (mode == TimerMode.COUNTDOWN) minutes else 0,
                lock = true,
                todoItemId = todo.id,
                source = "TODO"
            )
        )
        // 跳转到计时页
        app.startActivity(
            Intent(app, com.focus.moment.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    /** 记录一次不计时待办的完成 */
    suspend fun markTodoDone(app: Context, todoId: String) {
        runCatching {
            val db = AppDatabase.get(app)
            val today = com.focus.moment.data.TimeFmt.dayOf(System.currentTimeMillis()).toString()
            db.todoItemDao().markDone(todoId, today, System.currentTimeMillis())
            val todo = db.todoItemDao().byId(todoId)
            if (todo != null) {
                db.sessionDao().upsert(
                    com.focus.moment.data.db.SessionEntity(
                        id = UUID.randomUUID().toString(),
                        scheduleId = null,
                        title = todo.name,
                        category = Category.OTHER.name,
                        startedAt = System.currentTimeMillis(),
                        endedAt = System.currentTimeMillis(),
                        plannedMinutes = 0,
                        actualSeconds = 0,
                        mode = TimerMode.COUNTUP.name,
                        status = SessionStatus.COMPLETED.name,
                        updatedAt = System.currentTimeMillis(),
                        todoItemId = todoId,
                        source = "TODO"
                    )
                )
            }
        }
    }
}
