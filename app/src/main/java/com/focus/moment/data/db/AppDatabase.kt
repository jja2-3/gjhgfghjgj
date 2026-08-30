package com.focus.moment.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val date: String,           // yyyy-MM-dd，首次/锚定日期
    val startTime: String?,     // HH:mm，可空
    val plannedMinutes: Int?,   // 倒计时时长（分钟）
    val mode: String,           // TimerMode
    val repeatRule: String,     // RepeatRule
    val repeatDays: String?,    // 自定义重复："1,3,5"（1=周一）
    val archived: Boolean = false,
    val updatedAt: Long,
    val deleted: Boolean = false
)

@Entity(tableName = "focus_sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val scheduleId: String?,
    val title: String,
    val category: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val actualSeconds: Int,
    val mode: String,
    val status: String,         // SessionStatus
    val updatedAt: Long,
    val deleted: Boolean = false,
    val todoItemId: String? = null,   // 关联的待办
    val source: String? = null        // FOCUS / TODO / LOCK
)

@Entity(tableName = "todo_items")
data class TodoItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,               // TodoType：NORMAL / GOAL / HABIT
    val timing: String,             // TodoTiming：COUNTDOWN / COUNTUP / NONE
    val plannedMinutes: Int? = null,  // 倒计时时长
    val targetMinutes: Int? = null,   // 定目标：目标总时长
    val note: String? = null,
    val hideNextDay: Boolean = false, // 完成后第二天不再显示
    val restMinutes: Int = 5,         // 完成后休息（待办集顺序执行用）
    val setId: String? = null,        // 属于待办集；普通待办为 null
    val orderIdx: Int = 0,
    val lastDoneDate: String? = null, // yyyy-MM-dd 最后完成日期
    val archived: Boolean = false,
    val updatedAt: Long,
    val deleted: Boolean = false
)

@Entity(tableName = "todo_sets")
data class TodoSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val autoContinue: Boolean = true,  // 连续执行子待办
    val longRestMinutes: Int = 15,     // 全部完成后长休息
    val updatedAt: Long,
    val deleted: Boolean = false
)

@Entity(tableName = "lock_periods")
data class LockPeriodEntity(
    @PrimaryKey val id: String,
    val startHHmm: String,     // "22:00"
    val endHHmm: String,       // "23:00"
    val repeatRule: String,    // RepeatRule
    val repeatDays: String? = null,
    val anchorDate: String,    // yyyy-MM-dd（单次用）
    val enabled: Boolean = true,
    val updatedAt: Long,
    val deleted: Boolean = false
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE deleted = 0 ORDER BY date, startTime")
    suspend fun all(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules")
    suspend fun allIncludingDeleted(): List<ScheduleEntity>

    @Upsert
    suspend fun upsert(item: ScheduleEntity)

    @Upsert
    suspend fun upsertAll(items: List<ScheduleEntity>)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM focus_sessions WHERE deleted = 0 AND startedAt >= :start AND startedAt < :end ORDER BY startedAt")
    suspend fun between(start: Long, end: Long): List<SessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE deleted = 0 AND startedAt >= :start ORDER BY startedAt DESC")
    suspend fun since(start: Long): List<SessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE deleted = 0 AND todoItemId = :todoId ORDER BY startedAt")
    suspend fun byTodoItem(todoId: String): List<SessionEntity>

    @Query("SELECT * FROM focus_sessions")
    suspend fun allIncludingDeleted(): List<SessionEntity>

    @Upsert
    suspend fun upsert(item: SessionEntity)

    @Upsert
    suspend fun upsertAll(items: List<SessionEntity>)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface TodoItemDao {
    @Query("SELECT * FROM todo_items")
    suspend fun allIncludingDeleted(): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE deleted = 0 AND setId IS NULL AND archived = 0 ORDER BY updatedAt DESC")
    suspend fun allPersonal(): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE deleted = 0 AND setId = :setId ORDER BY orderIdx, updatedAt")
    suspend fun bySet(setId: String): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE deleted = 0 AND setId = :setId ORDER BY orderIdx, updatedAt")
    fun observeBySet(setId: String): kotlinx.coroutines.flow.Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun byId(id: String): TodoItemEntity?

    @Upsert
    suspend fun upsert(item: TodoItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<TodoItemEntity>)

    @Query("UPDATE todo_items SET lastDoneDate = :date, updatedAt = :now WHERE id = :id")
    suspend fun markDone(id: String, date: String, now: Long)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface TodoSetDao {
    @Query("SELECT * FROM todo_sets")
    suspend fun allIncludingDeleted(): List<TodoSetEntity>

    @Query("SELECT * FROM todo_sets WHERE deleted = 0 ORDER BY updatedAt DESC")
    suspend fun all(): List<TodoSetEntity>

    @Query("SELECT * FROM todo_sets WHERE id = :id")
    suspend fun byId(id: String): TodoSetEntity?

    @Upsert
    suspend fun upsert(item: TodoSetEntity)

    @Upsert
    suspend fun upsertAll(items: List<TodoSetEntity>)

    @Query("DELETE FROM todo_sets WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface LockPeriodDao {
    @Query("SELECT * FROM lock_periods")
    suspend fun allIncludingDeleted(): List<LockPeriodEntity>

    @Query("SELECT * FROM lock_periods WHERE deleted = 0 ORDER BY startHHmm")
    suspend fun all(): List<LockPeriodEntity>

    @Upsert
    suspend fun upsert(item: LockPeriodEntity)

    @Upsert
    suspend fun upsertAll(items: List<LockPeriodEntity>)

    @Query("DELETE FROM lock_periods WHERE id = :id")
    suspend fun hardDelete(id: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todo_items` (" +
                "`id` TEXT NOT NULL PRIMARY KEY, " +
                "`name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`timing` TEXT NOT NULL, " +
                "`plannedMinutes` INTEGER, " +
                "`targetMinutes` INTEGER, " +
                "`note` TEXT, " +
                "`hideNextDay` INTEGER NOT NULL, " +
                "`restMinutes` INTEGER NOT NULL, " +
                "`setId` TEXT, " +
                "`orderIdx` INTEGER NOT NULL, " +
                "`lastDoneDate` TEXT, " +
                "`archived` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todo_sets` (" +
                "`id` TEXT NOT NULL PRIMARY KEY, " +
                "`name` TEXT NOT NULL, " +
                "`autoContinue` INTEGER NOT NULL, " +
                "`longRestMinutes` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lock_periods` (" +
                "`id` TEXT NOT NULL PRIMARY KEY, " +
                "`startHHmm` TEXT NOT NULL, " +
                "`endHHmm` TEXT NOT NULL, " +
                "`repeatRule` TEXT NOT NULL, " +
                "`repeatDays` TEXT, " +
                "`anchorDate` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE focus_sessions ADD COLUMN todoItemId TEXT")
        db.execSQL("ALTER TABLE focus_sessions ADD COLUMN source TEXT")
    }
}

@Database(
    entities = [ScheduleEntity::class, SessionEntity::class, TodoItemEntity::class,
                TodoSetEntity::class, LockPeriodEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun sessionDao(): SessionDao
    abstract fun todoItemDao(): TodoItemDao
    abstract fun todoSetDao(): TodoSetDao
    abstract fun lockPeriodDao(): LockPeriodDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_moment.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
