package cn.anlele.checkin.push

import android.content.Context
import cn.anlele.checkin.data.Api
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 「拉计划 → 排闹钟」的唯一入口。
 *
 * 两段式：
 *   1. 先用本地缓存排一遍（断网 / 刚重启也能立刻有闹钟）
 *   2. 再拉服务端最新计划，覆盖重排
 *
 * 被广播唤醒时 AppVm 不会创建，所以这里要自己把 token 从 DataStore 里捞出来。
 */
object NotifyResync {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun trigger(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            // 8 秒封顶：广播场景活不了太久，宁可不排也别被系统中途掐断
            val n = withTimeoutOrNull(8_000) { run(app) } ?: 0
            // 冷启动时 token / 网络常常还没就绪，只会用上旧缓存（旧计划里今天的条目都已过期）。
            // 排上的条数明显偏少就再试一次，避免「重启后今天那条没排上」。
            if (n <= 1) {
                kotlinx.coroutines.delay(8_000)
                withTimeoutOrNull(8_000) { run(app) }
            }
        }
    }

    /** 同步执行一次（返回实际排上的条数） */
    suspend fun run(ctx: Context): Int {
        if (Api.token.isNullOrBlank()) {
            Api.token = runCatching { SessionStore(ctx).load().token }.getOrNull()
        }
        if (Api.token.isNullOrBlank()) return 0

        // 1) 缓存兜底
        NotifyPlanCache.read(ctx)?.let { cached ->
            runCatching { AlarmScheduler.apply(ctx, cached.items) }
        }

        // 2) 拉最新
        return try {
            val fresh = Repo.notifyPlan(14)
            NotifyPlanCache.write(ctx, fresh)
            AlarmScheduler.apply(ctx, fresh.items)
        } catch (_: Exception) {
            0
        }
    }

    /** 登出 / 关闭总开关：清掉所有闹钟和缓存 */
    suspend fun clearAll(ctx: Context) {
        runCatching { AlarmScheduler.cancelAll(ctx) }
        NotifyPlanCache.clear(ctx)
    }
}
