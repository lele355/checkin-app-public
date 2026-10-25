package cn.anlele.checkin.push

import android.content.Context
import cn.anlele.checkin.data.Api
import kotlinx.serialization.Serializable
import java.io.File

/** 一条待排程的提醒（服务端算好、客户端只负责按时弹） */
@Serializable
data class PlanItem(
    val key: String = "",
    val kind: String = "schedule",
    /** 本地墙钟时间：yyyy-MM-ddTHH:mm */
    val dueAt: String = "",
    val title: String = "",
    val body: String = "",
    /** 点击通知后跳哪个页面（路由码，如 schedule / habits / anniversary） */
    val route: String = "workbench",
)

@Serializable
data class NotifyPlanDto(
    val serverTime: String = "",
    val items: List<PlanItem> = emptyList(),
)

@Serializable
data class NotifyDndDto(val enabled: Boolean = false, val startMinute: Int = 1320, val endMinute: Int = 480)

@Serializable
data class NotifyScheduleDto(val enabled: Boolean = true, val leads: List<Int> = listOf(10), val allDayMinute: Int = 480)

@Serializable
data class NotifyOneDto(val enabled: Boolean = true, val minute: Int = 480)

@Serializable
data class NotifyPeriodDto(val enabled: Boolean = true, val daysBefore: Int = 2)

@Serializable
data class NotifySettingsDto(
    val enabled: Boolean = true,
    val dnd: NotifyDndDto = NotifyDndDto(),
    val schedule: NotifyScheduleDto = NotifyScheduleDto(),
    val digest: NotifyOneDto = NotifyOneDto(minute = 480),
    val habit: NotifyOneDto = NotifyOneDto(minute = 1260),
    val anniversary: NotifyOneDto = NotifyOneDto(minute = 540),
    val period: NotifyPeriodDto = NotifyPeriodDto(),
    val unreadHint: Boolean = true,
)

@Serializable
data class NotifySettingsReq(
    val enabled: Boolean,
    val dnd: NotifyDndDto,
    val schedule: NotifyScheduleDto,
    val digest: NotifyOneDto,
    val habit: NotifyOneDto,
    val anniversary: NotifyOneDto,
    val period: NotifyPeriodDto,
    val unreadHint: Boolean,
)

/**
 * 提醒计划本地缓存。
 *
 * 为什么必须缓存：手机重启 / 断网时 App 拉不到接口，但如果本地有上次的计划，
 * 重启后能先把闹钟排回去 —— 否则「重启一次就再也不响」。
 */
object NotifyPlanCache {
    private const val FILE = "notify_plan.json"

    fun read(ctx: Context): NotifyPlanDto? = try {
        val f = File(ctx.filesDir, FILE)
        if (f.exists() && f.length() > 0) Api.json.decodeFromString<NotifyPlanDto>(f.readText()) else null
    } catch (_: Exception) {
        null
    }

    fun write(ctx: Context, plan: NotifyPlanDto) {
        try {
            File(ctx.filesDir, FILE).writeText(Api.json.encodeToString(NotifyPlanDto.serializer(), plan))
        } catch (_: Exception) { }
    }

    fun clear(ctx: Context) {
        try { File(ctx.filesDir, FILE).delete() } catch (_: Exception) { }
    }
}
