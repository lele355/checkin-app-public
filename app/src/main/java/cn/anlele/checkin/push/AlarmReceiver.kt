package cn.anlele.checkin.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 闹钟到点。
 *
 * 这里只做两件事：发通知 / 续期。**不发网络请求** ——
 * BroadcastReceiver 只有 ~10 秒存活时间，联网一旦超时就会被系统打断，
 * 而通知必须在闹钟这一刻弹出来，不能依赖网络。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind").orEmpty()

        // 内务闹钟：只把后面 14 天续上，不打扰用户
        if (kind == AlarmScheduler.KIND_REFRESH) {
            NotifyResync.trigger(ctx)
            return
        }

        val key = intent.getStringExtra("key").orEmpty()
        val item = PlanItem(
            key = key,
            kind = kind.ifBlank { "schedule" },
            title = intent.getStringExtra("title").orEmpty(),
            body = intent.getStringExtra("body").orEmpty(),
            route = intent.getStringExtra("route").orEmpty().ifBlank { "workbench" },
        )
        val id = if (key.isNotBlank()) AlarmScheduler.requestCode(key) else (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val ok = NotifyHelper.show(ctx, item, id)
        android.util.Log.i("CheckinNotify", "闹钟触发 kind=$kind key=$key id=$id 通知结果=$ok")
    }
}
