package com.focus.moment.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

@Serializable
data class ProfileDto(
    @kotlinx.serialization.SerialName("id") val id: String = "",
    @kotlinx.serialization.SerialName("nickname") val nickname: String = "",
    @kotlinx.serialization.SerialName("avatar") val avatar: String = "",
    @kotlinx.serialization.SerialName("phone") val phone: String = ""
)

@Serializable
private data class AuthResp(
    @kotlinx.serialization.SerialName("access_token") val accessToken: String = "",
    @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String = "",
    @kotlinx.serialization.SerialName("user") val user: AuthUserDto? = null
)

@Serializable
private data class AuthUserDto(
    @kotlinx.serialization.SerialName("id") val id: String = ""
)

data class Profile(
    val nickname: String = "",
    val avatarB64: String = "",
    val phone: String = ""
)

/**
 * 手机号 + 密码登录（基于 Supabase Auth）。
 * 说明：Supabase 手机验证码登录需要配置短信服务商，这里将手机号映射为内部账号：
 * <手机号>@phone.focusmoment.app，配合关闭"邮箱确认"后即可直接注册登录。
 */
class AuthManager(private val context: android.content.Context) {

    private val store = SettingsStore(context)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }

    private fun phoneEmail(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return "$digits@phone.focusmoment.app"
    }

    suspend fun signUpWithPhone(phone: String, password: String, nickname: String): String {
        val s = store.current()
        if (s.sbUrl.isBlank() || s.sbKey.isBlank()) return "请先在「云同步高级设置」中填写 Supabase URL 和 anon key"
        if (phone.filter { it.isDigit() }.length < 11) return "请输入 11 位手机号"
        if (password.length < 6) return "密码至少 6 位"
        return try {
            val resp: HttpResponse = client.post("${s.sbUrl.trimEnd('/')}/auth/v1/signup") {
                header("apikey", s.sbKey)
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "email" to phoneEmail(phone),
                    "password" to password,
                    "data" to mapOf("nickname" to nickname.ifBlank { "专注者" }, "phone" to phone)
                ))
            }
            if (!resp.status.isSuccess()) return "注册失败（${resp.status.value}），手机号可能已被注册或密码太短"
            val body = resp.body<AuthResp>()
            if (body.accessToken.isNotEmpty()) {
                saveAuth(s.sbUrl, s.sbKey, phoneEmail(phone), body, nickname.ifBlank { "专注者" })
                ensureProfile(body.user?.id ?: "", nickname.ifBlank { "专注者" }, "", phone)
                SyncManager(context).syncNow()
                "注册成功，已自动登录"
            } else {
                "注册成功。请到 Supabase 控制台关闭「邮箱确认」后重新登录（见说明）"
            }
        } catch (e: Exception) {
            "注册失败：${e.message}"
        }
    }

    suspend fun signInWithPhone(phone: String, password: String): String {
        val s = store.current()
        if (s.sbUrl.isBlank() || s.sbKey.isBlank()) return "请先在「云同步高级设置」中填写 Supabase URL 和 anon key"
        return try {
            val resp: HttpResponse = client.post("${s.sbUrl.trimEnd('/')}/auth/v1/token?grant_type=password") {
                header("apikey", s.sbKey)
                contentType(ContentType.Application.Json)
                setBody(mapOf("email" to phoneEmail(phone), "password" to password))
            }
            if (!resp.status.isSuccess()) return "登录失败（${resp.status.value}），请检查手机号和密码"
            val body = resp.body<AuthResp>()
            // 尝试拉取昵称
            val profile = fetchProfile(s.sbUrl, s.sbKey, body.accessToken, body.user?.id ?: "")
            saveAuth(s.sbUrl, s.sbKey, phoneEmail(phone), body, profile?.nickname ?: phone)
            if (profile != null) {
                store.update { it.copy(avatarB64 = profile.avatarB64) }
            }
            SyncManager(context).syncNow()
            "登录成功，数据已同步"
        } catch (e: Exception) {
            "登录失败：${e.message}"
        }
    }

    private suspend fun saveAuth(url: String, key: String, email: String, body: AuthResp, nickname: String) {
        store.update {
            it.copy(
                sbUrl = url, sbKey = key, sbEmail = email, nickname = nickname,
                sbAccessToken = body.accessToken,
                sbRefreshToken = body.refreshToken,
                sbUserId = body.user?.id ?: ""
            )
        }
    }

    private suspend fun ensureProfile(uid: String, nickname: String, avatar: String, phone: String) {
        val s = store.current()
        if (uid.isBlank()) return
        runCatching {
            client.post("${s.sbUrl.trimEnd('/')}/rest/v1/profiles") {
                header("apikey", s.sbKey)
                header(HttpHeaders.Authorization, "Bearer ${s.sbAccessToken}")
                header("Prefer", "resolution=merge-duplicates")
                contentType(ContentType.Application.Json)
                setBody(listOf(mapOf("id" to uid, "nickname" to nickname, "avatar" to avatar, "phone" to phone)))
            }
        }
    }

    private suspend fun fetchProfile(url: String, key: String, token: String, uid: String): Profile? {
        if (uid.isBlank()) return null
        return runCatching {
            val rows: List<ProfileDto> = client.get("$url/rest/v1/profiles?id=eq.$uid&select=*") {
                header("apikey", key)
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
            rows.firstOrNull()?.let { Profile(it.nickname, it.avatar, it.phone) }
        }.getOrNull()
    }

    /** 更新昵称/头像（base64），同时写入本地 */
    suspend fun updateProfile(nickname: String?, avatarB64: String?): String {
        val s = store.current()
        if (!s.loggedIn) return "请先登录"
        return try {
            refreshIfNeeded()
            val cur = store.current()
            val bodyMap = buildMap<String, String> {
                if (nickname != null) put("nickname", nickname)
                if (avatarB64 != null) put("avatar", avatarB64)
            }
            val resp: HttpResponse = client.patch("${cur.sbUrl.trimEnd('/')}/rest/v1/profiles?id=eq.${cur.sbUserId}") {
                header("apikey", cur.sbKey)
                header(HttpHeaders.Authorization, "Bearer ${cur.sbAccessToken}")
                contentType(ContentType.Application.Json)
                setBody(bodyMap)
            }
            if (!resp.status.isSuccess()) return "保存失败（${resp.status.value}）"
            store.update {
                it.copy(
                    nickname = nickname ?: it.nickname,
                    avatarB64 = avatarB64 ?: it.avatarB64
                )
            }
            "已保存"
        } catch (e: Exception) {
            "保存失败：${e.message}"
        }
    }

    private suspend fun refreshIfNeeded() {
        val s = store.current()
        if (s.sbRefreshToken.isBlank()) return
        runCatching {
            val resp: HttpResponse = client.post("${s.sbUrl.trimEnd('/')}/auth/v1/token?grant_type=refresh_token") {
                header("apikey", s.sbKey)
                contentType(ContentType.Application.Json)
                setBody(mapOf("refresh_token" to s.sbRefreshToken))
            }
            if (resp.status.isSuccess()) {
                val body = resp.body<AuthResp>()
                store.update { it.copy(sbAccessToken = body.accessToken, sbRefreshToken = body.refreshToken) }
            }
        }
    }

    companion object {
        /** 头像压缩为 base64（最长边 256px，JPEG 82%） */
        fun imageToBase64(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            input.close()
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > 512) sample *= 2
            val input2 = context.contentResolver.openInputStream(uri) ?: return null
            val bmp = BitmapFactory.decodeStream(input2, null, BitmapFactory.Options().apply { inSampleSize = sample })
            input2.close()
            bmp ?: return null
            val scaled = if (maxOf(bmp.width, bmp.height) > 256) {
                val ratio = 256f / maxOf(bmp.width, bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
            } else bmp
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()

        fun base64ToBitmap(b64: String): Bitmap? = runCatching {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}
