package cn.anlele.checkin.push

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.anlele.checkin.MainActivity
import cn.anlele.checkin.util.Dates
import java.util.Calendar

/**
 * 闹钟排程。
 *
 * 用 `setAlarmClock()` 而不是 `setExactAndAllowWhileIdle()`：
 * 前者被系统视为「用户级闹钟」，Doze 会提前退出低电耗来交付它，是 AlarmManager 里可靠性最高的；
 * 后者在 Doze 下仍会被节流（每个 App 约 9 分钟一次，国产 ROM 更严）。
 * 代价是状态栏会出现小闹钟图标 —— 对提醒类 App 反而是「这条提醒靠得住」的信号。
 */
object AlarmScheduler {

    private const val PREFS = "notify_alarms"
    private const val KEY_KEYS = "scheduled_keys"

    /** 内务闹钟：每天 03:00 触发一次，只为把后面 14 天的计划续上（不弹通知） */
    private const val REFRESH_CODE = 0x7001
    const val KIND_REFRESH = "refresh"

    /** 一次最多排这么多条，避免极端情况下把系统闹钟表塞满 */
    private const val MAX_ALARMS = 100

    fun requestCode(key: String): Int = key.hashCode() and 0x7FFFFFFF

    /** 是否允许精确闹钟（Android 12+ 需要用户手动授予「闹钟和提醒」） */
    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /**
     * 全量重排：先取消上一轮排的，再按新计划排。
     * 返回实际排上的条数。权限不足时返回 0（调用方据此提示用户）。
     */
    fun apply(ctx: Context, items: List<PlanItem>): Int {
        if (!canExact(ctx)) return 0
        cancelAll(ctx)

        val now = System.currentTimeMillis()
        val planned = items
            .mapNotNull { it to (Dates.parse(it.dueAt)?.time ?: return@mapNotNull null) }
            .filter { it.second > now + 1_500 }
            .sortedBy { it.second }
            .take(MAX_ALARMS)

        var ok = 0
        for ((item, ms) in planned) {
            if (arm(ctx, item, ms)) ok++
        }

        scheduleRefresh(ctx)
        saveKeys(ctx, planned.map { it.first.key })
        return ok
    }

    /** 排一条真实闹钟 */
    private fun arm(ctx: Context, item: PlanItem, atMs: Long): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        val code = requestCode(item.key)

        val fire = Intent(ctx, AlarmReceiver::class.java).apply {
            putExtra("key", item.key)
            putExtra("kind", item.kind)
            putExtra("title", item.title)
            putExtra("body", item.body)
            putExtra("route", item.route)
        }
        val op = PendingIntent.getBroadcast(
            ctx, code, fire,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // 点状态栏的闹钟图标时打开 App（对应页面）
        val show = PendingIntent.getActivity(
            ctx, code,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("route", item.route)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), op)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** 每天 03:00 的内务闹钟：只为续期，不打扰用户 */
    private fun scheduleRefresh(ctx: Context) {
        if (!canExact(ctx)) return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1)

        val fire = Intent(ctx, AlarmReceiver::class.java).apply {
            putExtra("kind", KIND_REFRESH)
            putExtra("key", "refresh")
        }
        val op = PendingIntent.getBroadcast(
            ctx, REFRESH_CODE, fire,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, null), op) }
    }

    /**
     * 自检闹钟：N 秒后响一次，走的是和真实提醒完全相同的链路
     * （PendingIntent → AlarmReceiver → 通知），用来当场验证「闹钟能不能唤醒 App」。
     */
    fun scheduleSelfTest(ctx: Context, delaySeconds: Long = 5): Boolean {
        if (!canExact(ctx)) return false
        val item = PlanItem(
            key = "selftest-${System.currentTimeMillis()}",
            kind = "test",
            title = "闹钟链路自检",
            body = "看到这条说明闹钟能准时唤醒 App，到点提醒没问题。",
            route = "notify",
        )
        return arm(ctx, item, System.currentTimeMillis() + delaySeconds * 1000)
    }

    /** 取消上一轮排过的全部闹钟 */
    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = sp.getStringSet(KEY_KEYS, emptySet()).orEmpty()

        for (key in keys) {
            val code = requestCode(key)
            val fire = Intent(ctx, AlarmReceiver::class.java)
            val op = PendingIntent.getBroadcast(
                ctx, code, fire,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
            if (op != null) {
                am?.cancel(op)
                op.cancel()
            }
        }
        // 内务闹钟也一起清掉（随后 apply 会重新排）
        val refreshPi = PendingIntent.getBroadcast(
            ctx, REFRESH_CODE, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (refreshPi != null) {
            am?.cancel(refreshPi)
            refreshPi.cancel()
        }
        sp.edit().remove(KEY_KEYS).apply()
    }

    private fun saveKeys(ctx: Context, keys: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_KEYS, keys.toSet()).apply()
    }

    fun scheduledCount(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_KEYS, emptySet())?.size ?: 0
}
