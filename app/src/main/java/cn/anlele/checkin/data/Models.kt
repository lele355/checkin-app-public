package cn.anlele.checkin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** 统一响应包：{"ok":true,"data":…} 或 {"ok":false,"error":"…"} */
@Serializable
data class Envelope<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: String? = null,
)

// ---------- 账号与启动包 ----------
@Serializable
data class NavItemDto(
    val code: String = "",
    val label: String = "",
    val icon: String = "",
    val group: String = "",
    val url: String = "",
)

@Serializable
data class MeDto(
    val id: Int = 0,
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    val signature: String = "",
    val roleName: String = "",
    val theme: String = "editorial",
    val accent: String = "",
    val tone: String = "",
    val mustChangePassword: Boolean = false,
    val createdAt: String = "",
    val lastLoginAt: String = "",
    val permissions: List<String> = emptyList(),
    val permissionLabels: List<String> = emptyList(),
    val counts: CountsDto = CountsDto(),
    val navHidden: List<String> = emptyList(),
    val dashboardLayout: JsonElement? = null,
    val nav: List<NavItemDto> = emptyList(),
    val navAll: List<NavItemDto> = emptyList(),
) {
    fun has(perm: String) = permissions.contains(perm)
}

@Serializable
data class LoginResult(
    val token: String = "",
    val expiresAt: String = "",
    val user: MeDto = MeDto(),
)

// ---------- 工作台 ----------
@Serializable
data class ScheduleDto(
    val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val startAt: String = "",
    val endAt: String? = null,
    val allDay: Boolean = false,
    val updatedAt: String = "",
)

@Serializable
data class MemoDto(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class HabitTodayDto(
    val id: Int = 0,
    val name: String = "",
    val icon: String = "🌱",
    val checked: Boolean = false,
    val total: Int = 0,
    val streak: Int = 0,
)

@Serializable
data class CourseTodayDto(
    val id: Int = 0,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val startPeriod: Int = 1,
    val endPeriod: Int = 2,
    val category: String = "other",
    val hours: String = "",
    val weeksLabel: String = "",
)

@Serializable
data class MailBriefDto(
    val id: Int = 0,
    val from: String = "",
    val subject: String = "",
    val sentAt: String = "",
    val isRead: Boolean = false,
    val hasAttachment: Boolean = false,
)

@Serializable
data class MailsCardDto(
    val hasAccounts: Boolean = false,
    val unread: Int = 0,
    val recent: List<MailBriefDto> = emptyList(),
)

@Serializable
data class BoardBriefDto(
    val id: Int = 0,
    val content: String = "",
    val author: String = "",
    val createdAt: String = "",
)

@Serializable
data class ExpenseCardDto(
    val monthIn: Double = 0.0,
    val monthOut: Double = 0.0,
    val entries: Int = 0,
    val month: String = "",
)

@Serializable
data class LoveNoteDto(
    val id: Int = 0,
    val content: String = "",
    val from: String = "",
    val fromId: Int = 0,
    val mine: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class CoupleCardDto(
    val title: String = "",
    val days: Int = 0,
    val startDate: String? = null,
    val lastNote: LoveNoteDto? = null,
)

@Serializable
data class PeriodCardDto(
    val startDate: String = "",
    val endDate: String? = null,
    val note: String = "",
)

@Serializable
data class AnnivCardDto(val title: String = "", val days: Int = 0)

@Serializable
data class DashboardDto(
    val today: String = "",
    val weekday: String = "",
    val greeting: String = "",
    val upcoming: List<ScheduleDto> = emptyList(),
    val pinnedMemos: List<MemoDto> = emptyList(),
    val habits: List<HabitTodayDto> = emptyList(),
    val nextAnniv: AnnivCardDto? = null,
    val reminderCount: Int = 0,
    val todayCourses: List<CourseTodayDto> = emptyList(),
    val currentWeek: Int = 0,
    val semesterConfigured: Boolean = false,
    val mails: MailsCardDto? = null,
    val board: List<BoardBriefDto> = emptyList(),
    val expense: ExpenseCardDto? = null,
    val couple: CoupleCardDto? = null,
    val period: PeriodCardDto? = null,
    val sampleRows: Int = 0,
    val showSampleBanner: Boolean = false,
)

/**
 * 工作台布局。三组 id 互斥，与网页端 `dashboard.js` 的 `{pinned, order, hidden}` 同一份数据
 * （存在 Users.DashboardLayout 这一个字段里），所以手机上调好的布局在网页上打开也是同一个。
 *
 * 【为什么是三个数组而不是每条卡片一个属性】
 * 顺序是「用户意图」，不是由某个属性派生的：置顶区内部、普通区内部都各自有序，
 * 用数组下标表达最直接；也不需要为不存在的卡片留占位，网页端 normalize 会再补一遍
 * （权限没开的卡片在 App 上渲染不出来，但布局里留着，回到网页还在）。
 */
@Serializable
data class DashLayout(
    val pinned: List<String> = emptyList(),
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
) {
    fun isPinned(id: String) = pinned.contains(id)
    fun isHidden(id: String) = hidden.contains(id)

    /**
     * 按布局排出「该渲染哪些卡片、什么顺序」。
     * @param defaults 本机当前能渲染的卡片 id（按默认顺序）。布局里没有的一律追加到普通区末尾 ——
     *   新加的卡片（或换了权限后新出现的卡片）不该因为布局是旧的就被藏起来。
     */
    fun arrange(defaults: List<String>): List<String> {
        val out = ArrayList<String>(defaults.size)
        // 问候块永远第一：它是「今天概览」的入口，不该被任何置顶卡片挤到下面去。
        // 且不可隐藏：打开「自定义工作台」的齿轮在它身上，藏了它用户就再也进不了面板，
        // 所以 hidden 里的 hello 一律无视（老数据兼容）。
        if ("hello" in defaults) out.add("hello")
        for (id in pinned + order) if (id in defaults && id !in out && id !in hidden) out.add(id)
        for (id in defaults) if (id !in out && id !in hidden) out.add(id)
        return out
    }

    /** 置顶 / 取消置顶：置顶进置顶区最前，取消后回普通区最前（与网页端 togglePin 一致） */
    fun togglePin(id: String): DashLayout {
        val wasPinned = pinned.contains(id)
        val p = pinned.toMutableList().also { it.remove(id) }
        val o = order.toMutableList().also { it.remove(id) }
        val h = hidden.toMutableList().also { it.remove(id) }
        if (wasPinned) o.add(0, id) else p.add(0, id)
        return copy(pinned = p, order = o, hidden = h)
    }

    /** 显示 / 隐藏：恢复显示进普通区末尾，隐藏进隐藏区末尾 */
    fun setVisible(id: String, visible: Boolean): DashLayout {
        // 问候块不可隐藏：面板唯一入口（它的齿轮）会随卡消失，藏了就把用户锁在门外
        if (id == "hello" && !visible) return this
        val p = pinned.toMutableList().also { it.remove(id) }
        val o = order.toMutableList().also { it.remove(id) }
        val h = hidden.toMutableList().also { it.remove(id) }
        if (visible) o.add(id) else h.add(id)
        return copy(pinned = p, order = o, hidden = h)
    }

    /** 上移 / 下移（只在自己所属的那一组里挪，不会跨区串位） */
    fun move(id: String, delta: Int): DashLayout {
        val inPinned = pinned.contains(id)
        val list = if (inPinned) pinned else order
        val i = list.indexOf(id)
        if (i < 0) return this
        val j = i + delta
        if (j < 0 || j >= list.size) return this
        val moved = list.toMutableList()
        val t = moved[i]; moved[i] = moved[j]; moved[j] = t
        return if (inPinned) copy(pinned = moved) else copy(order = moved)
    }

    /** 只留白名单内的 id，并保证三组互斥（同一条 id 出现在两组里会让排序逻辑打结） */
    fun clean(): DashLayout {
        val seen = HashSet<String>()
        fun one(a: List<String>) = a.filter { it in Known && seen.add(it) }
        // 问候块不可隐藏：老数据里 hidden=hello 的保存时一律清掉
        return DashLayout(one(pinned), one(order), one(hidden).filter { it != "hello" })
    }

    /**
     * 把「这台设备能渲染、但布局里没提到」的卡片补进普通区末尾。
     *
     * 为什么必须补：布局是稀疏存储 —— 用户只点过两张卡，存的就只有那两张。
     * 剩下十来张靠 [arrange] 在渲染时兜底追加；但**面板是直接读这三个数组的**，
     * 不补的话面板里就只有被点过的那几张，用户再也没法把别的卡排序或隐藏。
     * （网页端 dashboard.js 的 normalize() 做的也是这件事。）
     */
    fun fill(defaults: List<String>): DashLayout {
        val seen = HashSet<String>(pinned.size + order.size + hidden.size)
        seen.addAll(pinned); seen.addAll(order); seen.addAll(hidden)
        val extra = defaults.filter { it !in seen }
        return if (extra.isEmpty()) this else copy(order = order + extra)
    }

    /**
     * 只留「当前能渲染」的 id。
     *
     * 用途：面板只应列出用户看得见的卡片 —— 否则「情侣空间」这类条件卡（没数据时不渲染）
     * 会以「开着」的开关出现在面板里，而屏幕上根本没有它，看着像 bug。
     * 副作用：一张当时渲染不出来的卡如果被隐藏过，这次保存后就不再是隐藏状态（等它有数据了
     * 会以普通卡片出现）。这比「面板里列一堆看不见的东西」代价小，而且能自愈。
     */
    fun prune(keep: List<String>): DashLayout {
        if (keep.isEmpty()) return DashLayout()
        val allow = keep.toHashSet()
        return DashLayout(
            pinned = pinned.filter { it in allow },
            order = order.filter { it in allow },
            hidden = hidden.filter { it in allow },
        )
    }

    companion object {
        /**
         * 卡片 id 白名单：与网页端 `Preferences.DashboardCards` 一一对应。
         * build / admin 这两张卡 App 里没有（网页端才渲染），但**必须留在白名单里** ——
         * 否则 App 存一次布局就把它们从数组里剔掉了，回到网页端会被 normalize 追加到普通区末尾，
         * 用户排好的位置就丢了。
         */
        val Known = listOf(
            "hello", "reminders", "habits", "courses", "schedule", "memo",
            "anniversary", "email", "board", "expense", "couples", "period",
            "build", "admin",
        )

        /**
         * 从 MeDto.dashboardLayout（JsonElement?）解析。
         * 服务端正常情况下给的是对象，但历史数据/中转层可能把它当字符串塞回来，
         * 两种都得认，否则用户会看到「布局突然回到默认」。
         */
        fun from(el: JsonElement?): DashLayout = when {
            el == null || el is JsonNull -> DashLayout()
            el is JsonPrimitive -> parse(el.content)
            else -> parse(el.toString())
        }

        /** 解析服务端存的 JSON（坏数据 / 空串 = 全默认布局，不能因此让工作台白屏） */
        fun parse(json: String?): DashLayout {
            if (json.isNullOrBlank()) return DashLayout()
            return runCatching {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<DashLayout>(json).clean()
            }.getOrElse { DashLayout() }
        }

        fun encode(l: DashLayout): String = Json.encodeToString(serializer(), l)
    }
}

// ---------- 列表页 ----------
@Serializable
data class ListDto<T>(val items: List<T> = emptyList(), val hasMore: Boolean = false)

@Serializable
data class HabitDto(
    val id: Int = 0,
    val name: String = "",
    val icon: String = "🌱",
    val checkedToday: Boolean = false,
    val streak: Int = 0,
    val total: Int = 0,
    val week: List<Boolean> = emptyList(),
)

@Serializable
data class HabitStatDto(
    val checked: Boolean = false,
    val streak: Int = 0,
    val total: Int = 0,
    val week: List<Boolean> = emptyList(),
)

@Serializable
data class ExpenseDto(
    val id: Int = 0,
    val amount: Double = 0.0,
    val type: String = "out",
    val category: String = "其他",
    val occurDate: String = "",
    val note: String = "",
)

@Serializable
data class CategorySumDto(val name: String = "", val amount: Double = 0.0)

@Serializable
data class ExpenseSummaryDto(
    val totalOut: Double = 0.0,
    val totalIn: Double = 0.0,
    val count: Int = 0,
    val byCategory: List<CategorySumDto> = emptyList(),
)

@Serializable
data class MonthRowDto(
    val month: String = "",
    val label: String = "",
    val `in`: Double = 0.0,
    val `out`: Double = 0.0,
    val count: Int = 0,
)

@Serializable
data class ExpensesPageDto(
    val month: String = "",
    val items: List<ExpenseDto> = emptyList(),
    /** 记账分类（用户自定义；服务端没配过就是内置 7 个）。空 = 老版本服务端，App 走本地兜底 */
    val categories: List<String> = emptyList(),
    val summary: ExpenseSummaryDto = ExpenseSummaryDto(),
    val months: List<MonthRowDto> = emptyList(),
)

@Serializable
data class CourseDto(
    val id: Int = 0,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val weekday: Int = 1,
    val startPeriod: Int = 1,
    val endPeriod: Int = 2,
    val weeksText: String = "",
    val weeksLabel: String = "",
    val category: String = "other",
    /** 色板键（violet / blue / …）。空串 = 按课程名自动分配，同名自动同色 */
    val color: String = "",
)

@Serializable
data class PeriodSlotDto(val start: String = "", val end: String = "")

/** 一个学期（列表项） */
@Serializable
data class SemesterDto(
    val id: Int = 0,
    val name: String = "",
    val startDate: String = "",
    val courseCount: Int = 0,
    val isCurrent: Boolean = false,
)

@Serializable
data class SemesterListDto(
    val items: List<SemesterDto> = emptyList(),
    val currentId: Int = 0,
)

@Serializable
data class CoursesPageDto(
    val items: List<CourseDto> = emptyList(),
    val periods: List<PeriodSlotDto> = emptyList(),
    val semesterStart: String = "",
    val semesterConfigured: Boolean = false,
    /** 当前学期 id / 名字（顶部要显示，学期弹层也要知道自己是谁） */
    val semesterId: Int = 0,
    val semesterName: String = "",
    val currentWeek: Int = 1,
    val todayWeekday: Int = 1,
)

@Serializable
data class HeatDto(val date: String = "", val count: Int = 0)

@Serializable
data class TopHabitDto(val name: String = "", val icon: String = "", val total: Int = 0)

@Serializable
data class MonthAmountDto(val label: String = "", val amount: Double = 0.0)

@Serializable
data class CountsDto(
    val schedules: Int = 0,
    val memos: Int = 0,
    val board: Int = 0,
    val courses: Int = 0,
    val checkins: Int = 0,
    val anniversaries: Int = 0,
    val expenses: Int = 0,
    val emailAccounts: Int = 0,
)

@Serializable
data class StatsDto(
    val heat: List<HeatDto> = emptyList(),
    val topHabits: List<TopHabitDto> = emptyList(),
    val months: List<MonthAmountDto> = emptyList(),
    val maxMonthOut: Double = 0.0,
    val counts: CountsDto = CountsDto(),
)

@Serializable
data class AnnivDto(
    val id: Int = 0,
    val title: String = "",
    val date: String = "",
    val repeatYearly: Boolean = true,
    val note: String = "",
    val nextDate: String = "",
    val daysLeft: Int = 0,
    val passedDays: Int = 0,
)

@Serializable
data class BoardPageDto(val items: List<BoardMsgDto> = emptyList(), val hasMore: Boolean = false)

@Serializable
data class BoardMsgDto(
    val id: Int = 0,
    val content: String = "",
    val author: String = "",
    val authorId: Int = 0,
    val mine: Boolean = false,
    /** 服务端算好的删除权限：自己的留言，或有 board.moderate（管理员可删任意） */
    val canDelete: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class CouplePageDto(
    val title: String = "",
    val startDate: String = "",
    val daysTogether: Int = 0,
    val notes: List<LoveNoteDto> = emptyList(),
    val nextAnniv: AnnivCardDto? = null,
)

@Serializable
data class PeriodRecordDto(
    val id: Int = 0,
    val startDate: String = "",
    val endDate: String? = null,
    val note: String = "",
    val length: Int? = null,
)

@Serializable
data class PeriodPredictDto(
    val nextDate: String = "",
    val daysLeft: Int = 0,
    val avgCycle: Int = 0,
    val avgLength: Int = 0,
)

@Serializable
data class PeriodPageDto(
    val items: List<PeriodRecordDto> = emptyList(),
    val predict: PeriodPredictDto? = null,
    val today: String = "",
)

@Serializable
data class ReminderItemDto(
    val kind: String = "",
    val title: String = "",
    @SerialName("when") val whenText: String = "",
    val url: String = "",
)

@Serializable
data class RemindersDto(
    val items: List<ReminderItemDto> = emptyList(),
    val todayHabitDone: Int = 0,
    val todayHabitTotal: Int = 0,
)

// ---------- AI ----------
@Serializable
data class AiActionDto(
    val index: Int = 0,
    val type: String = "",
    val label: String = "",
    val summary: String = "",
    val done: Boolean = false,
)

@Serializable
data class AiMsgDto(
    val id: Int = 0,
    val role: String = "",
    val content: String = "",
    val imageMime: String = "",
    val hasImage: Boolean = false,
    val createdAt: String = "",
    val actions: List<AiActionDto> = emptyList(),
)

@Serializable
data class AiQuotaDto(
    val usedRequests: Int = 0,
    val dailyRequests: Int = 60,
    val usedTokens: Long = 0,
    val dailyTokens: Long = 1_000_000,
)

@Serializable
data class AiPageDto(
    val items: List<AiMsgDto> = emptyList(),
    val quota: AiQuotaDto = AiQuotaDto(),
)

// ---------- 邮件 ----------
@Serializable
data class EmailAccountDto(
    val id: Int = 0,
    val displayName: String = "",
    val address: String = "",
    val provider: String = "custom",
    val providerLabel: String = "",
    val imapHost: String = "",
    val imapPort: Int = 993,
    val smtpHost: String = "",
    val smtpPort: Int = 465,
    val hasAuthCode: Boolean = false,
    val lastSyncAt: String = "",
    val lastSyncError: String = "",
    // 同步范围（天 / 封）。0 = 跟全局默认
    val syncDays: Int = 0,
    val syncCount: Int = 0,
    val unread: Int = 0,
)

@Serializable
data class EmailAccountsDto(val items: List<EmailAccountDto> = emptyList(), val unreadTotal: Int = 0)

@Serializable
data class ProviderDto(
    val key: String = "",
    val label: String = "",
    val imapHost: String = "",
    val imapPort: Int = 993,
    val smtpHost: String = "",
    val smtpPort: Int = 465,
)

@Serializable
data class ProvidersDto(val items: List<ProviderDto> = emptyList())

@Serializable
data class ChangelogItemDto(
    val version: String = "",
    val versionCode: Int = 0,
    val date: String = "",
    val notes: String = "",
)

@Serializable
data class ChangelogDto(val items: List<ChangelogItemDto> = emptyList())

@Serializable
data class EmailGroupDto(
    val fromName: String = "",
    val fromAddress: String = "",
    val unread: Int = 0,
    val total: Int = 0,
    val lastSubject: String = "",
    val lastSnippet: String = "",
    val lastAt: String = "",
    val lastId: Int = 0,
    val groupKey: String = "",
)

@Serializable
data class EmailGroupListDto(val items: List<EmailGroupDto> = emptyList(), val hasMore: Boolean = false)

@Serializable
data class MailListItemDto(
    val id: Int = 0,
    val account: String = "",
    val fromName: String = "",
    val fromAddress: String = "",
    val subject: String = "",
    val snippet: String = "",
    val sentAt: String = "",
    val isRead: Boolean = false,
    val hasAttachment: Boolean = false,
    val sizeBytes: Long = 0,
)

@Serializable
data class MailListDto(val items: List<MailListItemDto> = emptyList(), val hasMore: Boolean = false)

@Serializable
data class MailDetailDto(
    val id: Int = 0,
    val fromName: String = "",
    val fromAddress: String = "",
    val toAddress: String = "",
    val subject: String = "",
    val sentAt: String = "",
    val hasAttachment: Boolean = false,
    val body: String = "",
    val bodyError: String = "",
)

@Serializable
data class SyncResultDto(
    val id: Int = 0,
    val name: String = "",
    val ok: Boolean = false,
    val added: Int = 0,
    val error: String = "",
)

@Serializable
data class SyncAllDto(val results: List<SyncResultDto> = emptyList())

// ---------- 管理 ----------
@Serializable
data class AdminUserDto(
    val id: Int = 0,
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    val roleId: Int = 0,
    val roleName: String = "",
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = false,
    val isSelf: Boolean = false,
    val createdAt: String = "",
    val lastLoginAt: String = "",
)

@Serializable
data class RoleBriefDto(val id: Int = 0, val name: String = "")

@Serializable
data class AdminUsersDto(val items: List<AdminUserDto> = emptyList(), val roles: List<RoleBriefDto> = emptyList())

@Serializable
data class RoleDto(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val isBuiltin: Boolean = false,
    val userCount: Int = 0,
    val codes: List<String> = emptyList(),
)

@Serializable
data class PermDto(
    val id: Int = 0,
    val code: String = "",
    val module: String = "",
    val description: String = "",
    val moduleLabel: String = "",
)

@Serializable
data class RolesDto(val roles: List<RoleDto> = emptyList(), val permissions: List<PermDto> = emptyList())

@Serializable
data class InviteDto(
    val id: Int = 0,
    val code: String = "",
    val note: String = "",
    val roleId: Int = 0,
    val roleName: String = "",
    val createdAt: String = "",
    val expiresAt: String = "",
    val used: Boolean = false,
    val usedBy: String = "",
    val usedAt: String = "",
    val revoked: Boolean = false,
    val expired: Boolean = false,
    val usable: Boolean = false,
)

@Serializable
data class InvitesDto(
    val items: List<InviteDto> = emptyList(),
    val roles: List<RoleBriefDto> = emptyList(),
    val siteUrl: String = "",
)

// ---------- 通用 ----------
@Serializable
data class IdDto(val id: Int = 0)

@Serializable
data class VersionDto(
    val version: String = "",
    val versionCode: Int = 0,
    val url: String = "",
    val notes: String = "",
    /** App 备案号（陕ICP备…号-2A）。服务端下发而不是写死在 APK 里：补正换号时不用重新发版 */
    val beiAn: String = "",
    /** App 的公安联网备案号。与网站的号是两套，空串 = 还没拿到号 */
    val policeBeiAn: String = "",
)

@Serializable
data class ExportDto(val filename: String = "", val content: String = "")

@Serializable
data class MsgDto(val message: String = "")

@Serializable
data class PasswordDto(val password: String = "", val username: String = "")

/** 错误响应（非 2xx 时也尽量解析出中文提示） */
@Serializable
data class ErrorDto(val ok: Boolean = false, val error: String = "")

@Serializable
data class DateOnlyDto(val today: String = "", @SerialName("from") val fromDate: String = "")

// ==================== 小游戏（五子棋） ====================

/** 一手棋：x 列 / y 行（从 0 开始）/ s 落子方（1 黑、2 白） */
@Serializable
data class GameStoneDto(val x: Int = 0, val y: Int = 0, val s: Int = 0)

/**
 * 我的默认对局设置 —— 与 /api/v1/games/settings 一一对应。
 *
 * 两类字段要分清：
 *  · size / forbidden / moveTimeoutSec / totalTimeoutSec —— 影响对局，**建房时快照进房间**
 *  · tapConfirm / showCoord / lastMark / vibrate —— 只影响自己画面，不上服务端
 */
/** 游戏总设置（显示偏好）：不进房间、不上服务端判定，纯前端表现 */
@Serializable
data class GameDisplayDto(
    val tapConfirm: Boolean = false,
    val showCoord: Boolean = true,
    val lastMark: Boolean = true,
    val vibrate: Boolean = true,
)

/** 五子棋杂项设置（规则默认）：建房那一刻快照进房间，之后双方共享 */
@Serializable
data class GameRuleDefaultsDto(
    val size: Int = 15,
    val forbidden: Boolean = false,
    val moveTimeoutSec: Int = 0,
    val totalTimeoutSec: Int = 0,
)

/** 游戏目录里的一条 —— /Games 入口第一屏的卡片 */
@Serializable
data class GameItemDto(
    val key: String = "",
    val name: String = "",
    val sub: String = "",
    val minPlayers: Int = 2,
    val maxPlayers: Int = 2,
    val available: Boolean = true,
    val hasAi: Boolean = false,
)

@Serializable
data class GameCatalogDto(val games: List<GameItemDto> = emptyList())


/** 一局的全部状态 —— 与 /api/v1/games/{id} 的 data 一一对应，也用于大厅行 */
@Serializable
data class GameRoomDto(
    val id: Int = 0,
    val code: String = "",
    val gameKey: String = "gomoku",
    val status: String = "",
    val hostSide: Int = 1,
    val hostId: Int = 0,
    val hostName: String = "",
    val guestId: Int = 0,
    val guestName: String = "",
    val turn: Int = 1,
    val winner: Int = 0,
    val resultReason: String = "",
    val revision: Int = 0,
    val size: Int = 15,
    /** 建房时定下的规则，双方共享（中途改设置不影响这一局） */
    val forbidden: Boolean = false,
    val moveTimeoutSec: Int = 0,
    val totalTimeoutSec: Int = 0,
    /** 剩余毫秒；null = 这一项不限 */
    val moveRemainMs: Long? = null,
    val myRemainMs: Long? = null,
    val oppRemainMs: Long? = null,
    val hostUsedMs: Long = 0,
    val guestUsedMs: Long = 0,
    val moves: List<GameStoneDto> = emptyList(),
    val moveCount: Int = 0,
    val lastMove: GameStoneDto? = null,
    /** 我执哪一方：0 = 旁观 */
    val mySide: Int = 0,
    val myName: String = "",
    val opponentName: String = "",
    val isAi: Boolean = false,
    val opponentOnline: Boolean = false,
    val myTurn: Boolean = false,
    val canJoin: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class GameLobbyItemDto(
    val id: Int = 0,
    val code: String = "",
    val status: String = "",
    val hostId: Int = 0,
    val hostName: String = "",
    val guestId: Int = 0,
    val guestName: String = "",
    val hostSide: Int = 1,
    val size: Int = 15,
    val forbidden: Boolean = false,
    val moveCount: Int = 0,
    val winner: Int = 0,
    val mySide: Int = 0,
    val mine: Boolean = false,
    val myTurn: Boolean = false,
    val opponentOnline: Boolean = false,
    val updatedAt: String = "",
)

@Serializable
data class GameLobbyDto(
    val items: List<GameLobbyItemDto> = emptyList(),
    /** 服务端顺手带回来的我的设置，省一次往返 */
    val display: GameDisplayDto = GameDisplayDto(),
    val rules: GameRuleDefaultsDto = GameRuleDefaultsDto(),
    val sizeOptions: List<Int> = emptyList(),
    val moveTimeoutOptions: List<Int> = emptyList(),
    val totalTimeoutOptions: List<Int> = emptyList(),
)

@Serializable
data class GameCreateDto(val id: Int = 0, val code: String = "", val hostSide: Int = 1)
