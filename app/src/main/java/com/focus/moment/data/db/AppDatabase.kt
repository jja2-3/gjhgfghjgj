package com.focus.moment.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Database
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

    @Query("SELECT * FROM focus_sessions")
    suspend fun allIncludingDeleted(): List<SessionEntity>

    @Upsert
    suspend fun upsert(item: SessionEntity)

    @Upsert
    suspend fun upsertAll(items: List<SessionEntity>)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Database(
    entities = [ScheduleEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_moment.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
