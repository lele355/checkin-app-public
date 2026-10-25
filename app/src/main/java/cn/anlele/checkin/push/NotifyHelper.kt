package cn.anlele.checkin.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.anlele.checkin.MainActivity
import cn.anlele.checkin.R

/** 组装并发出通知（含点击跳转）。 */
object NotifyHelper {

    /** 通知权限是否已授予（Android 13+ 为运行时权限；12 及以下恒为已授予） */
    fun hasPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channelOf(kind: String): String = when (kind) {
        "schedule" -> NotifyChannels.SCHEDULE
        "digest", "habit", "anniv", "period" -> NotifyChannels.DAILY
        else -> NotifyChannels.SYSTEM
    }

    /**
     * 发一条通知。
     * @param id 同一条提醒重复触发时用同一个 id（会覆盖，不堆叠）
     */
    fun show(ctx: Context, item: PlanItem, id: Int): Boolean {
        if (!hasPermission(ctx)) return false
        val channel = channelOf(item.kind)

        // 点击通知 → 打开 App 并跳到对应页面（MainActivity 是 singleTop，热启动走 onNewIntent）
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("route", item.route)
            putExtra("from_notify", true)
        }
        val pi = PendingIntent.getActivity(
            ctx, id, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle(item.title.ifBlank { "日常管理" })
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(
                if (channel == NotifyChannels.SCHEDULE) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(
                if (channel == NotifyChannels.SCHEDULE) NotificationCompat.CATEGORY_REMINDER
                else NotificationCompat.CATEGORY_STATUS
            )

        return try {
            val nm = NotificationManagerCompat.from(ctx)
            val allowed = nm.areNotificationsEnabled()
            nm.notify(id, builder.build())
            android.util.Log.i("CheckinNotify", "notify id=$id channel=$channel allowed=$allowed perm=${hasPermission(ctx)}")
            true
        } catch (e: SecurityException) {
            android.util.Log.w("CheckinNotify", "notify 被拒：${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.e("CheckinNotify", "notify 出错", e)
            false
        }
    }

    /** 测试通知：走完整链路，验证渠道 + 权限都正常 */
    fun showTest(ctx: Context) {
        val item = PlanItem(
            key = "test",
            kind = "test",
            title = "测试通知 · 通知已就绪",
            body = "看到这条说明权限、渠道、闹钟都正常。日程到点时会按你设置的提前量提醒。",
            route = "notify",
        )
        show(ctx, item, 0x7000)
    }
}
