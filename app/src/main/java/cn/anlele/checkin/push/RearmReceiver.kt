package cn.anlele.checkin.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 重排触发器。
 *
 * 手机重启、App 升级、系统时间/时区变化、精确闹钟权限被授予或撤销 ——
 * 这几种情况下系统会把已排的闹钟清掉或让它们失真，必须重新排一遍。
 * 漏掉这里就会出现「重启一次手机，从此再也不响」。
 */
class RearmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        // 网络请求 + 排程，用 goAsync 争取存活时间（超时就放弃，明天内务闹钟还会再来）
        val pending = goAsync()
        val app = ctx.applicationContext
        NotifyResync.scope.launch {
            try {
                withTimeoutOrNull(8_000) { NotifyResync.run(app) }
            } catch (_: Exception) {
                // 静默失败：不能让广播抛异常
            } finally {
                pending.finish()
            }
        }
    }
}
