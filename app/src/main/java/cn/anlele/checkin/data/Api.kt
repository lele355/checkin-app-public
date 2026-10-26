package cn.anlele.checkin.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** 业务异常：message 直接给用户看（后端返回的中文提示优先）。 */
class ApiException(val code: Int, override val message: String) : Exception(message)

/** 网络不可达（不是服务端拒绝）—— 上层据此显示「离线」而不是「服务器出错」。 */
class OfflineException(override val message: String = "现在没有网络") : Exception(message)

/**
 * App 的唯一出口：https://example.com/api/v1。
 *
 * 设计要点：
 * - 不走 Cookie，每个请求带 Authorization: Bearer（App 是原生客户端，没有同源概念）。
 * - GET 成功就落一份 JSON 快照；下次断网时读快照，做到「飞行模式也能翻看」。
 * - 读超时给到 45s：AI 对话和邮件同步本身就慢，别让它先被超时掐掉。
 */
object Api {
    val BASE: String = cn.anlele.checkin.BuildConfig.API_BASE
    private const val HOST = "https://example.com"

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // 读超时给到 120s：AI 带图片的那一轮要识别整张课表再吐几十条工具调用，
        // 后端自己也放到 120s 了，客户端比服务端短会导致「服务端还在算，App 先报超时」
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    var token: String? = null

    private lateinit var cacheDir: File

    /** 上一次 GET 是不是用的离线快照（界面据此显示「离线数据」提示条） */
    @Volatile
    var lastFromCache: Boolean = false

    fun init(context: Context) {
        cacheDir = File(context.filesDir, "snapshot").apply { mkdirs() }
    }

    fun absolute(path: String): String =
        if (path.startsWith("http")) path else HOST + if (path.startsWith("/")) path else "/$path"

    // ---------------- 通用请求 ----------------

    @PublishedApi
    internal suspend fun raw(method: String, path: String, bodyJson: String?): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(BASE + path).header("Accept", "application/json")
            token?.let { builder.header("Authorization", "Bearer $it") }

            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                else -> builder.method(method, (bodyJson ?: "{}").toRequestBody(JSON_TYPE))
            }

            val resp = try {
                client.newCall(builder.build()).execute()
            } catch (e: Exception) {
                throw OfflineException()
            }

            resp.use { r ->
                val text = r.body?.string().orEmpty()
                if (r.code == 401) throw ApiException(401, parseError(text, "登录已过期，请重新登录"))
                if (r.code == 403) throw ApiException(403, parseError(text, "没有权限"))
                if (!r.isSuccessful) throw ApiException(r.code, parseError(text, "操作失败（${r.code}）"))
                text
            }
        }

    private fun parseError(text: String, fallback: String): String =
        try {
            json.decodeFromString<ErrorDto>(text).error.ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }

    // ---------------- 便捷方法 ----------------

    suspend inline fun <reified T> get(path: String, cache: Boolean = true): T {
        return try {
            val text = raw("GET", path, null)
            lastFromCache = false
            if (cache) snapshotPut(path, text)
            unwrap<T>(text)
        } catch (e: OfflineException) {
            val cached = if (cache) snapshotGet(path) else null
            if (cached != null) {
                lastFromCache = true
                unwrap<T>(cached)
            } else throw e
        }
    }

    suspend inline fun <reified T> post(path: String, body: String = "{}"): T =
        unwrap<T>(raw("POST", path, body))

    suspend inline fun <reified T> put(path: String, body: String): T =
        unwrap<T>(raw("PUT", path, body))

    suspend inline fun <reified T> delete(path: String): T =
        unwrap<T>(raw("DELETE", path, null))

    /** 无返回体的写操作（DELETE 之类）用这个，只关心成功与否 */
    suspend fun exec(method: String, path: String, body: String? = null) {
        raw(method, path, body)
    }

    inline fun <reified T> unwrap(text: String): T {
        val env = json.decodeFromString<Envelope<T>>(text)
        if (!env.ok) throw ApiException(200, env.error ?: "操作失败")
        @Suppress("UNCHECKED_CAST")
        return env.data ?: (Unit as T)
    }

    // ---------------- 离线快照 ----------------

    private fun snapshotFile(path: String) = File(cacheDir, path.trim('/').replace('/', '_').replace('?', '_').replace('&', '_') + ".json")

    @PublishedApi
    internal fun snapshotPut(path: String, text: String) {
        try {
            snapshotFile(path).writeText(text, Charsets.UTF_8)
        } catch (_: Exception) { /* 缓存失败不影响主流程 */ }
    }

    @PublishedApi
    internal fun snapshotGet(path: String): String? {
        val f = snapshotFile(path)
        return try {
            if (f.exists() && f.length() > 0) f.readText(Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
    }

    fun clearSnapshots() {
        try { cacheDir.listFiles()?.forEach { it.delete() } } catch (_: Exception) { }
    }
}
