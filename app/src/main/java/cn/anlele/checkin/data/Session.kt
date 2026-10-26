package cn.anlele.checkin.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private val Context.sessionStore by preferencesDataStore(name = "session")

/** 账号簿最多存几个 —— 自用邀请制小站（上限 10 人），一台设备够用还留了余量 */
private const val MAX_ACCOUNTS = 8

/**
 * 账号簿里的一条 —— 只放「能一键重新登进去」的最小集合。
 *
 * 【为什么存密码而不是 token】
 * token 可以在服务端被吊销（改密码、点「退出所有设备」、过期），存一堆 token 等于要维护
 * N 份会失效的凭据 + N 份过期逻辑。而切换账号本来就是「用这个号的密码登一次」，
 * 存密码最直接，也只有一个失效分支（密码被改 → 报错 → 用户手输一次）。
 *
 * 密码与 [SessionState.savedPass] 同一套取舍：Base64 混淆防肩窥，不是加密。
 */
@Serializable
data class SavedAccount(
    val username: String = "",
    val displayName: String = "",
    val roleName: String = "",
    /** Base64 混淆的密码；解出来要给 [SessionStore.plainPassword] */
    val password: String = "",
    val lastUsedAt: Long = 0,
)

data class SessionState(
    val token: String? = null,
    val me: MeDto? = null,
    val theme: String = "editorial",
    val accent: String = "",
    val lastSyncAt: Long = 0,
    val tone: String = "",
    /** 保持登录：关掉后每次冷启动都要重新登录（等价网页端「自动进入」不勾） */
    val keepLogin: Boolean = true,
    /** 记住密码：下次把账号密码预填好（等价网页端「仅填充密码」） */
    val rememberPass: Boolean = false,
    val savedUser: String = "",
    val savedPass: String = "",
    /** 账号簿：登录页 / 侧栏「切换账号」的候选名单，按最近使用排在前面 */
    val accounts: List<SavedAccount> = emptyList(),
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
        // 底色档（网页端叫 data-tone）。老版本没这个键 → 读出来是 null → 当作「默认底色」
        val tone = stringPreferencesKey("tone")
        // 登录页两个开关（对应网页端「记住密码」的三个单选）：
        //   keepLogin = 下次自动进入；rememberPass = 仅填充密码。两者互斥又独立，
        //   因为 App 没有"会话 cookie"这层，只能靠「留不留 token」来表达。
        val keepLogin = booleanPreferencesKey("keepLogin")
        val rememberPass = booleanPreferencesKey("rememberPass")
        val savedUser = stringPreferencesKey("savedUser")
        // 密码用 Base64 存：只为防肩窥，不是加密。和网页端 localStorage 的做法一致
        //（自用邀请制小站，token 本身已经是更长命的东西，这里不额外引 Keystore）
        val savedPass = stringPreferencesKey("savedPass")
        // 账号簿（JSON 数组）。老版本没有这个键 → 读出来是 null → 空列表，行为不变
        val accounts = stringPreferencesKey("accounts")
    }

    val flow: Flow<SessionState> = ctx.sessionStore.data.map { p ->
        SessionState(
            token = p[K.token],
            me = p[K.me]?.let { runCatching { Api.json.decodeFromString<MeDto>(it) }.getOrNull() },
            theme = p[K.theme] ?: "editorial",
            accent = p[K.accent] ?: "",
            lastSyncAt = p[K.lastSyncAt]?.toLongOrNull() ?: 0,
            tone = p[K.tone] ?: "",
            keepLogin = p[K.keepLogin] ?: true,
            rememberPass = p[K.rememberPass] ?: false,
            savedUser = p[K.savedUser] ?: "",
            savedPass = decode(p[K.savedPass]),
            accounts = readAccounts(p),
        )
    }

    /** 账号簿解码：任何一条坏掉就整组当空，不让一个脏数据把登录页搞崩 */
    private fun readAccounts(p: Preferences): List<SavedAccount> =
        p[K.accounts]?.let {
            runCatching { Api.json.decodeFromString(ListSerializer(SavedAccount.serializer()), it) }.getOrNull()
        }.orEmpty().filter { it.username.isNotBlank() }

    suspend fun load(): SessionState = flow.first()

    /**
     * 登录成功落盘。
     *
     * [keepLogin] = false 时**只写内存 token、不落盘**：下次冷启动读不到 token，
     * 自然回到登录页。这对应网页端的「会话级 cookie」（关浏览器即失效）。
     */
    suspend fun saveLogin(
        result: LoginResult,
        keepLogin: Boolean = true,
        rememberPass: Boolean = false,
        username: String = "",
        password: String = "",
    ) {
        ctx.sessionStore.edit { p ->
            if (keepLogin) {
                p[K.token] = result.token
            } else {
                p.remove(K.token)
                p.remove(K.me)
            }
            p[K.theme] = result.user.theme
            p[K.accent] = result.user.accent
            p[K.tone] = result.user.tone
            p[K.lastSyncAt] = System.currentTimeMillis().toString()
            p[K.keepLogin] = keepLogin
            p[K.rememberPass] = rememberPass

            // 记住密码：连账号一起存；不记住就把旧存档清掉（避免关掉开关后还被填上）
            if (rememberPass && username.isNotBlank()) {
                p[K.savedUser] = username
                p[K.savedPass] = encode(password)
            } else {
                p.remove(K.savedUser)
                p.remove(K.savedPass)
            }

            // 账号簿：只要这次是真输入了密码，就记住这个号 —— 与「记住密码」开关无关。
            // 因为「切换账号」的前提是能一键登回去，开关一关就没法切了，功能等于摆设。
            if (username.isNotBlank() && password.isNotBlank()) {
                p[K.accounts] = mergeAccount(p, username, password, result.user)
            }

            // 只有 keepLogin 才存启动包；否则下次启动不该有任何身份残留
            if (keepLogin) p[K.me] = Api.json.encodeToString(MeDto.serializer(), result.user)
        }
        Api.token = result.token
    }

    /** 把这次登录的号写进账号簿：同名的覆盖（密码/昵称可能变了），排到最前，超出上限丢最旧 */
    private fun mergeAccount(
        p: Preferences,
        username: String,
        password: String,
        me: MeDto,
    ): String {
        val list = readAccounts(p).toMutableList()
        list.removeAll { it.username.equals(username, ignoreCase = true) }
        list.add(
            0,
            SavedAccount(
                username = username,
                displayName = me.displayName,
                roleName = me.roleName,
                password = encode(password),
                lastUsedAt = System.currentTimeMillis(),
            ),
        )
        val trimmed = list.take(MAX_ACCOUNTS)
        return Api.json.encodeToString(ListSerializer(SavedAccount.serializer()), trimmed)
    }

    /** 从账号簿里删掉一个号（「切换账号」面板上的叉号） */
    suspend fun removeAccount(username: String) {
        ctx.sessionStore.edit { p ->
            val list = readAccounts(p).filterNot { it.username.equals(username, ignoreCase = true) }
            if (list.isEmpty()) p.remove(K.accounts)
            else p[K.accounts] = Api.json.encodeToString(ListSerializer(SavedAccount.serializer()), list)
        }
    }

    /** 账号簿里存的密码是 Base64 混淆的，用之前解一次 */
    fun plainPassword(encoded: String): String = decode(encoded)

    suspend fun saveMe(me: MeDto) {
        ctx.sessionStore.edit { p ->
            p[K.me] = Api.json.encodeToString(MeDto.serializer(), me)
            p[K.theme] = me.theme
            p[K.accent] = me.accent
            p[K.tone] = me.tone
            p[K.lastSyncAt] = System.currentTimeMillis().toString()
        }
    }

    suspend fun setTheme(theme: String, accent: String, tone: String) {
        ctx.sessionStore.edit { p ->
            p[K.theme] = theme
            p[K.accent] = accent
            p[K.tone] = tone
        }
    }

    suspend fun touch() {
        ctx.sessionStore.edit { p -> p[K.lastSyncAt] = System.currentTimeMillis().toString() }
    }

    suspend fun setKeepLogin(keep: Boolean) {
        ctx.sessionStore.edit { p -> p[K.keepLogin] = keep }
    }

    suspend fun setRememberPass(remember: Boolean, username: String = "", password: String = "") {
        ctx.sessionStore.edit { p ->
            p[K.rememberPass] = remember
            if (remember && username.isNotBlank()) {
                p[K.savedUser] = username
                p[K.savedPass] = encode(password)
            } else {
                p.remove(K.savedUser)
                p.remove(K.savedPass)
            }
        }
    }

    /** 退出登录：只清身份，保留外观偏好和「记住密码」的存档（和网页端退出一致） */
    suspend fun clear() {
        val keep = flow.first()
        ctx.sessionStore.edit { p ->
            p.remove(K.token)
            p.remove(K.me)
            p.remove(K.lastSyncAt)
            // 外观偏好（theme/accent/tone）故意留着：退出登录不该把App变回默认主题
            if (!keep.rememberPass) {
                p.remove(K.savedUser)
                p.remove(K.savedPass)
            }
        }
        Api.token = null
        Api.clearSnapshots()
    }

    /** 彻底清空（换账号等场景），连偏好一起清 */
    suspend fun clearAll() {
        ctx.sessionStore.edit { it.clear() }
        Api.token = null
        Api.clearSnapshots()
    }

    /** 冷启动时把 token 塞进 Api（Api 是单例，构造顺序不保证先读存储） */
    suspend fun restoreToken() {
        Api.token = load().token
    }

    // 密码存档只用 Base64 混淆（防肩窥），不是加密 —— 与网页端 localStorage 的做法一致
    private fun encode(s: String): String =
        if (s.isEmpty()) "" else Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)

    private fun decode(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return runCatching { String(Base64.decode(s, Base64.NO_WRAP)) }.getOrDefault("")
    }
}
