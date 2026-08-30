package com.focus.moment.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReleaseDto(
    @kotlinx.serialization.SerialName("tag_name") val tagName: String = "",
    @kotlinx.serialization.SerialName("name") val name: String = "",
    @kotlinx.serialization.SerialName("body") val body: String = "",
    @kotlinx.serialization.SerialName("html_url") val htmlUrl: String = "",
    @kotlinx.serialization.SerialName("assets") val assets: List<AssetDto> = emptyList()
)

@Serializable
data class AssetDto(
    @kotlinx.serialization.SerialName("name") val name: String = "",
    @kotlinx.serialization.SerialName("browser_download_url") val url: String = ""
)

data class UpdateInfo(
    val version: String,
    val versionName: String,
    val changelog: String,
    val apkUrl: String,
    val releaseUrl: String
)

/** 通过 GitHub Releases 检查新版本（仓库为公开仓库，无需登录） */
object UpdateChecker {

    private const val REPO = "jja2-3/gjhgfghjgj"

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    suspend fun fetchLatest(): UpdateInfo? = runCatching {
        val r: ReleaseDto = client.get("https://api.github.com/repos/$REPO/releases/latest").body()
        val apk = r.assets.firstOrNull { it.name.endsWith(".apk") }?.url ?: ""
        UpdateInfo(
            version = r.tagName.removePrefix("v"),
            versionName = r.name.ifBlank { r.tagName },
            changelog = r.body,
            apkUrl = apk,
            releaseUrl = r.htmlUrl
        )
    }.getOrNull()

    /** latest 是否比 current 新（按 vX.Y.Z 数字比较） */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest) ?: return false
        val c = parseVersion(current) ?: return true
        for (i in 0 until 3) {
            if (l[i] > c[i]) return true
            if (l[i] < c[i]) return false
        }
        return false
    }

    private fun parseVersion(v: String): List<Int>? {
        val nums = Regex("\\d+").findAll(v).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) return null
        return listOf(
            nums.getOrElse(0) { 0 },
            nums.getOrElse(1) { 0 },
            nums.getOrElse(2) { 0 }
        )
    }
}
