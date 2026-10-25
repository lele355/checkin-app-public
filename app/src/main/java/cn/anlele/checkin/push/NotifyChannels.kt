package cn.anlele.checkin.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * 三个通知渠道 —— Android 8.0+ 没有渠道就发不出通知。
 *
 * ⚠️ 渠道的重要性/声音创建后**只能降不能升**，改错了只能让用户卸载重装。
 * 所以第一次就按用途定死：日程要能吵醒人（HIGH），日常汇总正常提示（DEFAULT），系统提示安静（LOW）。
 */
object NotifyChannels {

    /** 日程到点：弹横幅 + 响铃 + 震动（唯一要求"一定要看见"的类别） */
    const val SCHEDULE = "schedule"

    /** 每日早报 / 打卡提醒 / 纪念日：正常提示音，不弹横幅打扰 */
    const val DAILY = "daily"

    /** 测试、维护类：静音，不打扰 */
    const val SYSTEM = "system"

    fun create(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(SCHEDULE, "日程提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "日程开始前按你设置的提前量提醒"
                enableVibration(true)
                setShowBadge(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(DAILY, "每日提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每日早报、打卡提醒、纪念日"
                enableVibration(true)
                setShowBadge(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(SYSTEM, "系统通知", NotificationManager.IMPORTANCE_LOW).apply {
                description = "测试通知、维护提示（静音）"
                setShowBadge(false)
            }
        )
    }

    /** 调试用：把某个渠道的重要性读出来核对 */
    fun importanceOf(ctx: Context, id: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return -1
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return -1
        return nm.getNotificationChannel(id)?.importance ?: -1
    }
}
