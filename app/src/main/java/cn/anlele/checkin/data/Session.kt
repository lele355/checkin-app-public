package cn.anlele.checkin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "session")

data class SessionState(
    val token: String? = null,
    val me: MeDto? = null,
    val theme: String = "editorial",
    val accent: String = "",
    val lastSyncAt: Long = 0,
) {
    val loggedIn get() = !token.isNullOrBlank()
}

/**
 * 登录态与外观偏好的本地存储（App 私有目录，不进备份）。
 *
 * 除 token 外还存了一份「上次拿到的启动包」（MeDto JSON）：
 * 这样断网/弱网冷启动时也能直接进主界面、显示离线数据，而不是卡在登录页转圈。
 */
class SessionStore(private val ctx: Context) {

    private object K {
        val token = stringPreferencesKey("token")
        val me = stringPreferencesKey("me")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val lastSyncAt = stringPreferencesKey("lastSyncAt")
    }

    val flow: Flow<SessionState> = ctx.sessionStore.data.map { p ->
        SessionState(
            token = p[K.token],
            me = p[K.me]?.let { runCatching { Api.json.decodeFromString<MeDto>(it) }.getOrNull() },
            theme = p[K.theme] ?: "editorial",
            accent = p[K.accent] ?: "",
            lastSyncAt = p[K.lastSyncAt]?.toLongOrNull() ?: 0,
        )
    }

    suspend fun load(): SessionState = flow.first()

    suspend fun saveLogin(result: LoginResult) {
        ctx.sessionStore.edit { p ->
            p[K.token] = result.token
            p[K.me] = Api.json.encodeToString(MeDto.serializer(), result.user)
            p[K.theme] = result.user.theme
            p[K.accent] = result.user.accent
            p[K.lastSyncAt] = System.currentTimeMillis().toString()
        }
        Api.token = result.token
    }

    suspend fun saveMe(me: MeDto) {
        ctx.sessionStore.edit { p ->
            p[K.me] = Api.json.encodeToString(MeDto.serializer(), me)
            p[K.theme] = me.theme
            p[K.accent] = me.accent
            p[K.lastSyncAt] = System.currentTimeMillis().toString()
        }
    }

    suspend fun setTheme(theme: String, accent: String) {
        ctx.sessionStore.edit { p ->
            p[K.theme] = theme
            p[K.accent] = accent
        }
    }

    suspend fun touch() {
        ctx.sessionStore.edit { p -> p[K.lastSyncAt] = System.currentTimeMillis().toString() }
    }

    suspend fun clear() {
        ctx.sessionStore.edit { it.clear() }
        Api.token = null
        Api.clearSnapshots()
    }

    /** 冷启动时把 token 塞进 Api（Api 是单例，构造顺序不保证先读存储） */
    suspend fun restoreToken() {
        Api.token = load().token
    }
}
