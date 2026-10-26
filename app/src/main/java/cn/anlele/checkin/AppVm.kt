package cn.anlele.checkin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.anlele.checkin.data.Api
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.MeDto
import cn.anlele.checkin.data.OfflineException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.SessionStore
import kotlinx.coroutines.launch

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
            ui = ui.copy(theme = saved.theme, accent = saved.accent, tone = saved.tone)

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

    fun login(username: String, password: String) {
        if (ui.busy) return
        ui = ui.copy(busy = true, loginError = null)
        viewModelScope.launch {
            try {
                val r = Repo.login(username.trim(), password)
                store.saveLogin(r)
                ui = ui.copy(
                    busy = false, me = r.user, theme = r.user.theme,
                    accent = r.user.accent, tone = r.user.tone, loginError = null, offline = false,
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

    fun logout() {
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            runCatching { cn.anlele.checkin.push.NotifyResync.clearAll(app) }
            Repo.logout()
            store.clear()
            ui = UiState(booting = false)
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
