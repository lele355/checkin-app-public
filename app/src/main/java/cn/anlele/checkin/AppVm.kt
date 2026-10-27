package cn.anlele.checkin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.anlele.checkin.data.Api
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.DashLayout
import cn.anlele.checkin.data.MeDto
import cn.anlele.checkin.data.OfflineException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.SavedAccount
import cn.anlele.checkin.data.SessionStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class UiState(
    val booting: Boolean = true,
    val busy: Boolean = false,
    val me: MeDto? = null,
    val theme: String = "editorial",
    val accent: String = "",
    val tone: String = "",
    val offline: Boolean = false,
    val loginError: String? = null,
    val toast: String? = null,
    /** 登录页「下次自动进入」开关，默认开（自用单机 App，绝大多数人不想每次都输密码） */
    val keepLogin: Boolean = true,
    /** 登录页「记住密码」开关，默认关（默认不把密码落在磁盘上更稳妥） */
    val rememberPass: Boolean = false,
    /** 上次记住的账号密码，登录页"打开就填好" */
    val savedUser: String = "",
    val savedPass: String = "",
    /** 账号簿：切换账号面板的候选名单（按最近使用排序） */
    val accounts: List<SavedAccount> = emptyList(),
    /** 正在切换账号（侧栏/登录页点了某个号，等接口返回） */
    val switchingTo: String? = null,
    /** 服务端下发的 App 备案号；空 = 还没拉到，页面回落到 Beian.APP_ICP */
    val beiAn: String = "",
    /** 服务端下发的 App 公安联网备案号；空 = 号还没拿到，页面显示「办理中」 */
    val policeBeiAn: String = "",
)

/**
 * 全局状态：登录态 + 外观偏好 + 提示消息。
 * 只有一个 ViewModel，页面之间共享同一个实例（Activity 作用域）——
 * 换主题、改昵称、退出登录都能立刻反映到所有界面。
 */
class AppVm(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)

    var ui by mutableStateOf(UiState())
        private set

    // ---------- 本机偏好：更新检查节奏 / 权限是否已问过 ----------
    // 放 SharedPreferences 而不是服务端：这些是「这台手机」的事，换设备重来一次也无所谓。
    private val health = app.getSharedPreferences("app_health", android.content.Context.MODE_PRIVATE)

    /** 自动检查更新（设置页可关，默认开） */
    var autoUpdateCheck: Boolean
        get() = health.getBoolean("auto_update", true)
        set(v) { health.edit().putBoolean("auto_update", v).apply() }

    /** 上次自动检查更新的时间戳（毫秒）。用来做「每天最多一次」 */
    var lastUpdateCheck: Long
        get() = health.getLong("last_update_check", 0L)
        set(v) { health.edit().putLong("last_update_check", v).apply() }

    /** 是否已经主动请求过通知权限（首次进 App 问一次，之后不再骚扰） */
    var permAsked: Boolean
        get() = health.getBoolean("perm_asked", false)
        set(v) { health.edit().putBoolean("perm_asked", v).apply() }

    /** 顶部权限提醒被忽略到什么时候（毫秒）。忽略后 7 天内不再顶 */
    var permSnoozeUntil: Long
        get() = health.getLong("perm_snooze_until", 0L)
        set(v) { health.edit().putLong("perm_snooze_until", v).apply() }

    /** 通知点进来要跳的页面（由 MainActivity 从 intent 写入，MainShell 消费） */
    var pendingRoute: String? by mutableStateOf(null)
        private set

    fun openRoute(code: String) { pendingRoute = code }
    fun consumeRoute() { pendingRoute = null }

    init {
        Api.init(app)
    }

    /** 登录态就绪后排一次提醒闹钟（失败静默，不能影响登录） */
    private fun resyncAlarms() {
        runCatching { cn.anlele.checkin.push.NotifyResync.trigger(getApplication()) }
    }

    // ---------------- 启动 ----------------

    fun bootstrap() {
        viewModelScope.launch {
            val saved = store.load()
            Api.token = saved.token
            ui = ui.copy(
                theme = saved.theme, accent = saved.accent, tone = saved.tone,
                keepLogin = saved.keepLogin, rememberPass = saved.rememberPass,
                savedUser = saved.savedUser, savedPass = saved.savedPass,
                accounts = saved.accounts,
            )

            // 备案号单独拉：匿名接口，登录页也要用，所以不等登录态、也不挂进启动路径 ——
            // 拉不到就用 Beian 里的兜底值，绝不能让页面上「关于」里空着一行。
            viewModelScope.launch {
                runCatching { Repo.version() }
                    .onSuccess {
                        ui = ui.copy(
                            beiAn = it.beiAn.trim().ifBlank { ui.beiAn },
                            policeBeiAn = it.policeBeiAn.trim().ifBlank { ui.policeBeiAn },
                        )
                    }
            }

            if (saved.token.isNullOrBlank()) {
                ui = ui.copy(booting = false)
                return@launch
            }

            // 先用本地缓存的启动包进界面（秒开），再静默拉最新
            ui = ui.copy(me = saved.me, booting = false)

            try {
                val fresh = Repo.me()
                store.saveMe(fresh)
                ui = ui.copy(me = fresh, theme = fresh.theme, accent = fresh.accent, tone = fresh.tone, offline = false)
                resyncAlarms()
            } catch (e: ApiException) {
                if (e.code == 401) {
                    // 令牌过期/被吊销：回登录页
                    store.clear()
                    ui = ui.copy(me = null, loginError = "登录已过期，请重新登录")
                } else {
                    ui = ui.copy(loginError = e.message)
                }
            } catch (e: OfflineException) {
                // 断网：保留缓存身份，进主界面看离线数据
                ui = ui.copy(offline = saved.me != null)
            }
        }
    }

    // ---------------- 登录 / 登出 ----------------

    fun login(username: String, password: String, keepLogin: Boolean = ui.keepLogin, rememberPass: Boolean = ui.rememberPass) {
        if (ui.busy) return
        ui = ui.copy(busy = true, loginError = null, keepLogin = keepLogin, rememberPass = rememberPass)
        viewModelScope.launch {
            try {
                val r = Repo.login(username.trim(), password)
                store.saveLogin(
                    r, keepLogin = keepLogin, rememberPass = rememberPass,
                    username = username.trim(), password = password,
                )
                ui = ui.copy(
                    busy = false, me = r.user, theme = r.user.theme,
                    accent = r.user.accent, tone = r.user.tone, loginError = null, offline = false,
                    savedUser = if (rememberPass) username.trim() else "",
                    savedPass = if (rememberPass) password else "",
                    // 账号簿可能刚被这次登录写了一条，重读一次让「切换账号」立刻能看到自己
                    accounts = store.load().accounts,
                )
                resyncAlarms()
            } catch (e: ApiException) {
                ui = ui.copy(busy = false, loginError = e.message)
            } catch (e: OfflineException) {
                ui = ui.copy(busy = false, loginError = "现在没有网络，连上再试")
            } catch (e: Exception) {
                ui = ui.copy(busy = false, loginError = "登录失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /** 登录页上直接切开关：立刻持久化，不用等点登录 */
    fun setLoginOptions(keepLogin: Boolean, rememberPass: Boolean) {
        ui = ui.copy(keepLogin = keepLogin, rememberPass = rememberPass)
        viewModelScope.launch {
            store.setKeepLogin(keepLogin)
            // 关掉「记住密码」时顺手清掉存档；开着但还没输过账号，就等登录成功再存
            store.setRememberPass(rememberPass, ui.savedUser, ui.savedPass)
            if (!rememberPass) ui = ui.copy(savedUser = "", savedPass = "")
        }
    }

    fun logout() {
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            runCatching { cn.anlele.checkin.push.NotifyResync.clearAll(app) }
            Repo.logout()
            store.clear()
            // 退出后仍留在登录页，主题/开关/记住的账号都还在（store.clear 只清身份）
            ui = ui.copy(me = null, booting = false, busy = false, loginError = null, switchingTo = null)
        }
    }

    // ---------------- 切换账号 ----------------

    /**
     * 一键切到账号簿里的另一个号。
     *
     * 为什么不是「多 token 并存」：服务端一个 token 只认一个用户，App 里同时挂两个身份
     * 等于每个请求都要带上「这次用谁」的选择，缓存/快照/闹钟也都要各存一份 ——
     * 自用 App 完全不需要这种复杂度。切换就是「退掉现在这个，用存好的密码登那个」。
     *
     * 失败时不静默：把错误写到 loginError，界面自然落回登录页并把原因显示出来，
     * 用户手输一次密码就能进去（账号簿里那条不会自动删，留着下次再试）。
     */
    fun switchAccount(acc: SavedAccount) {
        if (ui.switchingTo != null) return
        val pwd = store.plainPassword(acc.password)
        if (pwd.isBlank()) {
            toast("这个号没有存密码，请手动登录一次")
            return
        }
        ui = ui.copy(switchingTo = acc.username, loginError = null)
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            // 先把旧身份的闹钟和会话都清掉，避免换号后收到别人的提醒
            runCatching { cn.anlele.checkin.push.NotifyResync.clearAll(app) }
            runCatching { Repo.logout() }
            store.clear()

            try {
                val r = Repo.login(acc.username, pwd)
                // 沿用当前的「保持登录 / 记住密码」偏好：切换不该顺手把用户的开关重置
                store.saveLogin(
                    r,
                    keepLogin = ui.keepLogin,
                    rememberPass = ui.rememberPass,
                    username = acc.username,
                    password = pwd,
                )
                ui = ui.copy(
                    me = r.user,
                    theme = r.user.theme,
                    accent = r.user.accent,
                    tone = r.user.tone,
                    offline = false,
                    busy = false,
                    loginError = null,
                    switchingTo = null,
                    savedUser = if (ui.rememberPass) acc.username else "",
                    savedPass = if (ui.rememberPass) pwd else "",
                )
                resyncAlarms()
                toast("已切换到 ${r.user.displayName}")
            } catch (e: ApiException) {
                diverge()
                ui = ui.copy(loginError = "切换到 ${acc.displayName.ifBlank { acc.username }} 失败：${e.message}")
            } catch (e: OfflineException) {
                diverge()
                ui = ui.copy(loginError = "现在没有网络，切成没成功，请连上再试")
            } catch (e: Exception) {
                diverge()
                ui = ui.copy(loginError = "切换失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /** 切换失败后统一收口：旧身份已退掉，只能停在登录页 */
    private fun diverge() {
        ui = ui.copy(me = null, booting = false, busy = false, switchingTo = null)
    }

    /** 从账号簿里删掉一个号 */
    fun forgetAccount(username: String) {
        viewModelScope.launch {
            store.removeAccount(username)
            ui = ui.copy(accounts = store.load().accounts)
        }
    }

    // ---------------- 外观 ----------------

    fun setTheme(theme: String, accent: String, tone: String = "") {
        ui = ui.copy(theme = theme, accent = accent, tone = tone)
        viewModelScope.launch {
            store.setTheme(theme, accent, tone)
            runCatching {
                val me = Repo.savePrefs(Repo.PrefsReq(theme = theme, accent = accent, tone = tone))
                store.saveMe(me)
                ui = ui.copy(me = me)
            }
        }
    }

    // ---------------- 资料 ----------------

    fun updateMe(me: MeDto) {
        ui = ui.copy(me = me, theme = me.theme, accent = me.accent, tone = me.tone)
        viewModelScope.launch { store.saveMe(me) }
    }

    /**
     * 存工作台布局。
     * 先写内存 + 本地缓存、再发网络：置顶这种操作要「点一下就变」，
     * 不能等一趟网络往返；失败只提示、不回滚（下一次保存会带上完整布局覆盖掉）。
     */
    fun saveDashboardLayout(layout: DashLayout) {
        val me = ui.me ?: return
        val updated = me.copy(dashboardLayout = Json.parseToJsonElement(DashLayout.encode(layout)))
        ui = ui.copy(me = updated)
        viewModelScope.launch {
            store.saveMe(updated)
            runCatching { Repo.saveDashboardLayout(DashLayout.encode(layout)) }
                .onSuccess { toast("工作台布局已保存") }
                .onFailure { toast("布局没能同步到服务器，下次联网再存一次") }
        }
    }

    fun refreshMe() {
        viewModelScope.launch {
            runCatching {
                val me = Repo.me()
                store.saveMe(me)
                ui = ui.copy(me = me, theme = me.theme, accent = me.accent, offline = false)
            }
        }
    }

    // ---------------- 提示 ----------------

    fun toast(msg: String?) {
        ui = ui.copy(toast = msg)
    }

    fun consumeToast() {
        if (ui.toast != null) ui = ui.copy(toast = null)
    }

    fun setOffline(v: Boolean) {
        if (ui.offline != v) ui = ui.copy(offline = v)
    }
}
