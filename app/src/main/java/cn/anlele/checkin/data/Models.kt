package cn.anlele.checkin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
)

@Serializable
data class PeriodSlotDto(val start: String = "", val end: String = "")

@Serializable
data class CoursesPageDto(
    val items: List<CourseDto> = emptyList(),
    val periods: List<PeriodSlotDto> = emptyList(),
    val semesterStart: String = "",
    val semesterConfigured: Boolean = false,
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
