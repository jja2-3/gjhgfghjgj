package com.focus.moment.data

import com.focus.moment.data.db.AppDatabase
import com.focus.moment.data.db.ScheduleEntity
import com.focus.moment.data.db.SessionEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScheduleDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("title") val title: String = "",
    @SerialName("category") val category: String = "OTHER",
    @SerialName("date") val date: String = "",
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("planned_minutes") val plannedMinutes: Int? = null,
    @SerialName("mode") val mode: String = "COUNTDOWN",
    @SerialName("repeat_rule") val repeatRule: String = "ONCE",
    @SerialName("repeat_days") val repeatDays: String? = null,
    @SerialName("archived") val archived: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("deleted") val deleted: Boolean = false
)

@Serializable
data class SessionDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("schedule_id") val scheduleId: String? = null,
    @SerialName("title") val title: String = "",
    @SerialName("category") val category: String = "OTHER",
    @SerialName("started_at") val startedAt: Long = 0,
    @SerialName("ended_at") val endedAt: Long = 0,
    @SerialName("planned_minutes") val plannedMinutes: Int = 0,
    @SerialName("actual_seconds") val actualSeconds: Int = 0,
    @SerialName("mode") val mode: String = "COUNTDOWN",
    @SerialName("status") val status: String = "COMPLETED",
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("deleted") val deleted: Boolean = false
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("user") val user: AuthUser? = null
)

@Serializable
data class AuthUser(
    @SerialName("id") val id: String = ""
)

class SyncManager(private val context: android.content.Context) {

    private val store = SettingsStore(context)
    private val db = AppDatabase.get(context)

    private val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }

    private suspend fun authHeaders(): Triple<String, String, String> {
        val s = store.current()
        if (s.sbUrl.isBlank() || s.sbKey.isBlank()) throw Exception("请先在设置中填写 Supabase URL 和 anon key")
        if (!s.loggedIn) throw Exception("请先登录云同步账号")
        return Triple(s.sbUrl.trimEnd('/'), s.sbKey, s.sbAccessToken)
    }

    suspend fun signUp(email: String, password: String): String {
        val s = store.current()
        if (s.sbUrl.isBlank() || s.sbKey.isBlank()) return "请先填写 Supabase URL 和 anon key"
        try {
            val resp: HttpResponse = client.post("${s.sbUrl.trimEnd('/')}/auth/v1/signup") {
                header("apikey", s.sbKey)
                contentType(ContentType.Application.Json)
                setBody(mapOf("email" to email, "password" to password))
            }
            if (!resp.status.isSuccess()) return "注册失败（${resp.status.value}），邮箱可能已被注册或密码太短"
            val body = resp.body<AuthResponse>()
            if (body.accessToken.isNotEmpty()) {
                saveAuth(s.sbUrl, s.sbKey, email, body)
                syncNow()
                return "注册并登录成功"
            }
            return "注册成功，请前往邮箱确认后登录"
        } catch (e: Exception) {
            return "注册失败：${e.message}"
        }
    }

    suspend fun signIn(email: String, password: String): String {
        val s = store.current()
        if (s.sbUrl.isBlank() || s.sbKey.isBlank()) return "请先填写 Supabase URL 和 anon key"
        try {
            val resp: HttpResponse = client.post("${s.sbUrl.trimEnd('/')}/auth/v1/token?grant_type=password") {
                header("apikey", s.sbKey)
                contentType(ContentType.Application.Json)
                setBody(mapOf("email" to email, "password" to password))
            }
            if (!resp.status.isSuccess()) return "登录失败（${resp.status.value}），请检查邮箱和密码"
            val body = resp.body<AuthResponse>()
            saveAuth(s.sbUrl, s.sbKey, email, body)
            syncNow()
            return "登录成功，数据已同步"
        } catch (e: Exception) {
            return "登录失败：${e.message}"
        }
    }

    private suspend fun saveAuth(url: String, key: String, email: String, body: AuthResponse) {
        store.update { it.copy(
            sbUrl = url, sbKey = key, sbEmail = email,
            sbAccessToken = body.accessToken,
            sbRefreshToken = body.refreshToken,
            sbUserId = body.user?.id ?: ""
        ) }
    }

    suspend fun signOut() {
        store.update { it.copy(sbAccessToken = "", sbRefreshToken = "", sbUserId = "") }
    }

    /** 双向全量合并：按 updatedAt 新者胜 */
    suspend fun syncNow(): String {
        val (url, key, token) = authHeaders()
        try {
            // 1. 刷新 token，避免过期
            refreshIfNeeded(url, key)

            val headers: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
                header("apikey", key)
                header(HttpHeaders.Authorization, "Bearer ${store.current().sbAccessToken}")
            }

            // 2. 拉取远端
            val remoteSchedules: List<ScheduleDto> =
                client.get("$url/rest/v1/schedules?select=*") { headers() }.body()
            val remoteSessions: List<SessionDto> =
                client.get("$url/rest/v1/focus_sessions?select=*") { headers() }.body()

            // 3. 与本地合并（新者胜）
            val localSchedules = db.scheduleDao().allIncludingDeleted()
            val localSessions = db.sessionDao().allIncludingDeleted()
            val uid = store.current().sbUserId

            val mergedSchedules = mergeSchedules(localSchedules, remoteSchedules.map { it.toEntity() })
            val mergedSessions = mergeSessions(localSessions, remoteSessions.map { it.toEntity() })

            // 4. 写回本地
            db.scheduleDao().upsertAll(mergedSchedules)
            db.sessionDao().upsertAll(mergedSessions)

            // 5. 推送到远端（整表 upsert，数据量小）
            val scheduleBody = mergedSchedules.map { it.toDto(uid) }
            client.post("$url/rest/v1/schedules") {
                headers()
                header("Prefer", "resolution=merge-duplicates")
                contentType(ContentType.Application.Json)
                setBody(scheduleBody)
            }
            val sessionBody = mergedSessions.map { it.toDto(uid) }
            client.post("$url/rest/v1/focus_sessions") {
                headers()
                header("Prefer", "resolution=merge-duplicates")
                contentType(ContentType.Application.Json)
                setBody(sessionBody)
            }

            val now = System.currentTimeMillis()
            store.update { it.copy(lastSync = now) }
            return "同步成功"
        } catch (e: Exception) {
            return "同步失败：${e.message}"
        }
    }

    private suspend fun refreshIfNeeded(url: String, key: String) {
        val s = store.current()
        if (s.sbRefreshToken.isBlank()) throw Exception("登录状态无效，请重新登录")
        try {
            val resp: HttpResponse = client.post("$url/auth/v1/token?grant_type=refresh_token") {
                header("apikey", key)
                contentType(ContentType.Application.Json)
                setBody(mapOf("refresh_token" to s.sbRefreshToken))
            }
            if (resp.status.isSuccess()) {
                val body = resp.body<AuthResponse>()
                store.update { it.copy(sbAccessToken = body.accessToken, sbRefreshToken = body.refreshToken) }
            }
        } catch (_: Exception) {
            // 刷新失败则继续用旧 token 尝试
        }
    }

    private fun mergeSchedules(local: List<ScheduleEntity>, remote: List<ScheduleEntity>): List<ScheduleEntity> {
        val map = HashMap<String, ScheduleEntity>()
        local.forEach { map[it.id] = it }
        remote.forEach { r ->
            val l = map[r.id]
            if (l == null || r.updatedAt > l.updatedAt) map[r.id] = r
        }
        return map.values.toList()
    }

    private fun mergeSessions(local: List<SessionEntity>, remote: List<SessionEntity>): List<SessionEntity> {
        val map = HashMap<String, SessionEntity>()
        local.forEach { map[it.id] = it }
        remote.forEach { r ->
            val l = map[r.id]
            if (l == null || r.updatedAt > l.updatedAt) map[r.id] = r
        }
        return map.values.toList()
    }

    private fun ScheduleDto.toEntity() = ScheduleEntity(
        id = id, title = title, category = category, date = date, startTime = startTime,
        plannedMinutes = plannedMinutes, mode = mode, repeatRule = repeatRule,
        repeatDays = repeatDays, archived = archived, updatedAt = updatedAt, deleted = deleted
    )

    private fun ScheduleEntity.toDto(uid: String) = ScheduleDto(
        id = id, userId = uid, title = title, category = category, date = date, startTime = startTime,
        plannedMinutes = plannedMinutes, mode = mode, repeatRule = repeatRule,
        repeatDays = repeatDays, archived = archived, updatedAt = updatedAt, deleted = deleted
    )

    private fun SessionDto.toEntity() = SessionEntity(
        id = id, scheduleId = scheduleId, title = title, category = category,
        startedAt = startedAt, endedAt = endedAt, plannedMinutes = plannedMinutes,
        actualSeconds = actualSeconds, mode = mode, status = status,
        updatedAt = updatedAt, deleted = deleted
    )

    private fun SessionEntity.toDto(uid: String) = SessionDto(
        id = id, userId = uid, scheduleId = scheduleId, title = title, category = category,
        startedAt = startedAt, endedAt = endedAt, plannedMinutes = plannedMinutes,
        actualSeconds = actualSeconds, mode = mode, status = status,
        updatedAt = updatedAt, deleted = deleted
    )
}
