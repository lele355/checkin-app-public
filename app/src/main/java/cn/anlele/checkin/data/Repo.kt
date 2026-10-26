package cn.anlele.checkin.data

import kotlinx.serialization.Serializable
import cn.anlele.checkin.push.NotifyPlanDto
import cn.anlele.checkin.push.NotifySettingsDto
import cn.anlele.checkin.push.NotifySettingsReq

/**
 * 接口门面：路径与请求体都集中在这里，界面层只调函数、不拼 URL。
 * 新增一个功能时，先在这里加一行，再写界面 —— 避免路径字符串散落各处。
 */
object Repo {

    // ---------- 请求体 ----------
    @Serializable data class LoginReq(val username: String, val password: String, val device: String = "android")
    @Serializable data class PrefsReq(
        val theme: String? = null,
        val accent: String? = null,
        val tone: String? = null,
        val navHidden: List<String>? = null,
        /** 工作台布局：{pinned,order,hidden} 序列化后的字符串，与网页端同一个字段、同一份数据 */
        val dashboardLayout: String? = null,
        val displayName: String? = null,
        val email: String? = null,
        val signature: String? = null,
    )
    @Serializable data class PasswordReq(val oldPassword: String, val newPassword: String)
    @Serializable data class ScheduleReq(
        val title: String,
        val description: String = "",
        val startAt: String,
        val endAt: String? = null,
        val allDay: Boolean = false,
    )
    @Serializable data class MemoReq(val title: String, val content: String = "", val pinned: Boolean = false)
    @Serializable data class HabitReq(val name: String, val icon: String = "🌱")
    @Serializable data class ExpenseReq(
        val amount: Double,
        val type: String = "out",
        val category: String = "其他",
        val occurDate: String,
        val note: String = "",
    )
    @Serializable data class CourseReq(
        val name: String,
        val teacher: String = "",
        val location: String = "",
        val weekday: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val weeksText: String = "1-16",
        val category: String = "other",
        /** 色板键；空串 = 改回「自动按课程名分配」（服务端把空串当作合法意图，不是「没传」） */
        val color: String = "",
    )
    @Serializable data class SlotReq(val s: String, val e: String)
    @Serializable data class SemesterReq(val name: String = "", val startDate: String = "")
    @Serializable data class CourseSettingsReq(val semesterStart: String? = null, val periods: List<SlotReq>? = null)
    @Serializable data class AnnivReq(val title: String, val date: String, val repeatYearly: Boolean = true, val note: String = "")
    @Serializable data class ContentReq(val content: String)
    @Serializable data class CoupleReq(val title: String, val startDate: String)
    @Serializable data class PeriodReq(val startDate: String, val endDate: String? = null, val note: String = "")
    @Serializable data class AiSendReq(val text: String = "", val image: String? = null)
    @Serializable data class ExecReq(val msgId: Int, val actIndex: Int)
    @Serializable data class AccountReq(
        val displayName: String = "",
        val address: String,
        val provider: String = "custom",
        val imapHost: String = "",
        val imapPort: Int = 0,
        val smtpHost: String = "",
        val smtpPort: Int = 0,
        val authCode: String = "",
    )
    @Serializable data class AdminUserReq(
        val username: String = "",
        val displayName: String = "",
        val email: String = "",
        val roleId: Int,
        val isActive: Boolean = true,
        val password: String = "",
    )
    @Serializable data class ResetReq(val newPassword: String = "")
    @Serializable data class RoleReq(val name: String = "", val description: String = "", val permissions: List<String> = emptyList())
    @Serializable data class InviteReq(val note: String = "", val roleId: Int, val validDays: Int = 7)

    private fun body(v: kotlinx.serialization.json.JsonElement) = v.toString()

    private inline fun <reified T> enc(value: T, serializer: kotlinx.serialization.KSerializer<T>) =
        Api.json.encodeToString(serializer, value)

    // ---------- 认证 ----------
    suspend fun login(username: String, password: String): LoginResult =
        Api.post("/auth/login", enc(LoginReq(username, password), LoginReq.serializer()))

    suspend fun logout() {
        runCatching { Api.exec("POST", "/auth/logout") }
    }

    suspend fun me(): MeDto = Api.get("/me")

    @Serializable private data class MeWrap(val user: MeDto = MeDto())
    @Serializable private data class RemovedDto(val removed: Int = 0)

    suspend fun savePrefs(req: PrefsReq): MeDto =
        Api.put<MeWrap>("/me/prefs", enc(req, PrefsReq.serializer())).user

    /// 单独存工作台布局。别复用 savePrefs 传一堆 null：
    /// 服务端对 `null` 的语义是「这一项不改」，只传 dashboardLayout 才是干净的局部更新。
    suspend fun saveDashboardLayout(json: String): MeDto =
        savePrefs(PrefsReq(dashboardLayout = json))

    suspend fun changePassword(old: String, new: String): MeDto =
        Api.post<MeWrap>("/me/password", enc(PasswordReq(old, new), PasswordReq.serializer())).user

    // ---------- 工作台 ----------
    suspend fun dashboard(): DashboardDto = Api.get("/dashboard")
    suspend fun clearSample(): Int = Api.post<RemovedDto>("/dashboard/clear-sample").removed
    suspend fun dashboardCheckin(id: Int) { Api.exec("POST", "/dashboard/checkin/$id") }

    // ---------- 日程 ----------
    suspend fun schedules(from: String, to: String): ListDto<ScheduleDto> =
        Api.get("/schedules?from=$from&to=$to")

    suspend fun createSchedule(req: ScheduleReq): IdDto =
        Api.post("/schedules", enc(req, ScheduleReq.serializer()))

    suspend fun updateSchedule(id: Int, req: ScheduleReq): IdDto =
        Api.put("/schedules/$id", enc(req, ScheduleReq.serializer()))

    suspend fun deleteSchedule(id: Int) { Api.exec("DELETE", "/schedules/$id") }

    // ---------- 备忘 ----------
    suspend fun memos(q: String = ""): ListDto<MemoDto> =
        Api.get(if (q.isBlank()) "/memos" else "/memos?q=$q")

    suspend fun createMemo(req: MemoReq): IdDto = Api.post("/memos", enc(req, MemoReq.serializer()))
    suspend fun updateMemo(id: Int, req: MemoReq): IdDto = Api.put("/memos/$id", enc(req, MemoReq.serializer()))
    suspend fun togglePin(id: Int) { Api.exec("POST", "/memos/$id/pin") }
    suspend fun deleteMemo(id: Int) { Api.exec("DELETE", "/memos/$id") }

    // ---------- 打卡 ----------
    suspend fun habits(): ListDto<HabitDto> = Api.get("/habits")
    suspend fun createHabit(req: HabitReq): IdDto = Api.post("/habits", enc(req, HabitReq.serializer()))
    suspend fun updateHabit(id: Int, req: HabitReq): IdDto = Api.put("/habits/$id", enc(req, HabitReq.serializer()))
    suspend fun deleteHabit(id: Int) { Api.exec("DELETE", "/habits/$id") }
    suspend fun checkin(id: Int): HabitStatDto = Api.post("/habits/$id/checkin")
    suspend fun undoCheckin(id: Int): HabitStatDto = Api.delete("/habits/$id/checkin")

    // ---------- 记账 ----------
    suspend fun expenses(month: String): ExpensesPageDto = Api.get("/expenses?month=$month")
    suspend fun createExpense(req: ExpenseReq): IdDto = Api.post("/expenses", enc(req, ExpenseReq.serializer()))
    suspend fun updateExpense(id: Int, req: ExpenseReq): IdDto = Api.put("/expenses/$id", enc(req, ExpenseReq.serializer()))
    suspend fun deleteExpense(id: Int) { Api.exec("DELETE", "/expenses/$id") }

    // ---------- 课程表 ----------
    suspend fun courses(): CoursesPageDto = Api.get("/courses")
    suspend fun createCourse(req: CourseReq): IdDto = Api.post("/courses", enc(req, CourseReq.serializer()))
    suspend fun updateCourse(id: Int, req: CourseReq): IdDto = Api.put("/courses/$id", enc(req, CourseReq.serializer()))
    suspend fun deleteCourse(id: Int) { Api.exec("DELETE", "/courses/$id") }
    // ---------- 学期 ----------
    suspend fun semesters(): SemesterListDto = Api.get("/courses/semesters")
    suspend fun createSemester(name: String, startDate: String): IdDto =
        Api.post("/courses/semesters", enc(SemesterReq(name, startDate), SemesterReq.serializer()))
    suspend fun updateSemester(id: Int, name: String, startDate: String): IdDto =
        Api.put("/courses/semesters/$id", enc(SemesterReq(name, startDate), SemesterReq.serializer()))
    suspend fun switchSemester(id: Int) { Api.exec("POST", "/courses/semesters/$id/switch") }
    suspend fun deleteSemester(id: Int) { Api.exec("DELETE", "/courses/semesters/$id") }

    suspend fun saveCourseSettings(req: CourseSettingsReq) {
        Api.exec("PUT", "/courses/settings", enc(req, CourseSettingsReq.serializer()))
    }

    // ---------- 统计 ----------
    suspend fun stats(): StatsDto = Api.get("/stats")

    // ---------- 纪念日 ----------
    suspend fun anniversaries(): ListDto<AnnivDto> = Api.get("/anniversaries")
    suspend fun createAnniv(req: AnnivReq): IdDto = Api.post("/anniversaries", enc(req, AnnivReq.serializer()))
    suspend fun updateAnniv(id: Int, req: AnnivReq): IdDto = Api.put("/anniversaries/$id", enc(req, AnnivReq.serializer()))
    suspend fun deleteAnniv(id: Int) { Api.exec("DELETE", "/anniversaries/$id") }

    // ---------- 留言板 ----------
    suspend fun board(before: Int = 0): BoardPageDto =
        Api.get(if (before > 0) "/board?before=$before" else "/board")

    suspend fun postBoard(text: String): IdDto = Api.post("/board", enc(ContentReq(text), ContentReq.serializer()))
    suspend fun deleteBoard(id: Int) { Api.exec("DELETE", "/board/$id") }

    // ---------- 情侣空间 ----------
    suspend fun couple(): CouplePageDto = Api.get("/couples")
    suspend fun saveCouple(req: CoupleReq) { Api.exec("PUT", "/couples/setting", enc(req, CoupleReq.serializer())) }
    suspend fun postNote(text: String): IdDto = Api.post("/couples/notes", enc(ContentReq(text), ContentReq.serializer()))
    suspend fun deleteNote(id: Int) { Api.exec("DELETE", "/couples/notes/$id") }

    // ---------- 经期 ----------
    suspend fun period(): PeriodPageDto = Api.get("/period")
    suspend fun createPeriod(req: PeriodReq): IdDto = Api.post("/period", enc(req, PeriodReq.serializer()))
    suspend fun updatePeriod(id: Int, req: PeriodReq): IdDto = Api.put("/period/$id", enc(req, PeriodReq.serializer()))
    suspend fun deletePeriod(id: Int) { Api.exec("DELETE", "/period/$id") }

    // ---------- 提醒 ----------
    suspend fun reminders(): RemindersDto = Api.get("/reminders")

    // ---------- AI ----------
    suspend fun aiMessages(): AiPageDto = Api.get("/ai/messages", cache = false)
    suspend fun aiSend(text: String, image: String? = null): AiPageDto =
        Api.post("/ai/send", enc(AiSendReq(text, image), AiSendReq.serializer()))
    suspend fun aiExecute(msgId: Int, index: Int): MsgDto =
        Api.post("/ai/execute", enc(ExecReq(msgId, index), ExecReq.serializer()))
    suspend fun aiDismiss(msgId: Int) { Api.exec("POST", "/ai/dismiss", enc(ExecReq(msgId, 0), ExecReq.serializer())) }
    suspend fun aiClear() { Api.exec("POST", "/ai/clear") }

    // ---------- 邮件 ----------
    suspend fun emailAccounts(): EmailAccountsDto = Api.get("/email/accounts")
    suspend fun emailProviders(): ProvidersDto = Api.get("/email/providers")
    suspend fun saveEmailAccount(req: AccountReq, id: Int = 0): IdDto =
        if (id == 0) Api.post("/email/accounts", enc(req, AccountReq.serializer()))
        else Api.put("/email/accounts/$id", enc(req, AccountReq.serializer()))
    suspend fun deleteEmailAccount(id: Int) { Api.exec("DELETE", "/email/accounts/$id") }
    suspend fun emails(accountId: Int = 0, before: Int = 0): MailListDto {
        val qs = buildList {
            if (accountId > 0) add("account=$accountId")
            if (before > 0) add("before=$before")
        }
        return Api.get("/email/messages" + if (qs.isEmpty()) "" else "?" + qs.joinToString("&"))
    }
    suspend fun emailDetail(id: Int): MailDetailDto = Api.get("/email/messages/$id")
    suspend fun syncEmail(accountId: Int = 0): SyncAllDto =
        Api.post(if (accountId > 0) "/email/sync?account=$accountId" else "/email/sync")

    // ---------- 管理 ----------
    suspend fun adminUsers(): AdminUsersDto = Api.get("/admin/users")
    suspend fun createUser(req: AdminUserReq): IdDto = Api.post("/admin/users", enc(req, AdminUserReq.serializer()))
    suspend fun updateUser(id: Int, req: AdminUserReq): IdDto = Api.put("/admin/users/$id", enc(req, AdminUserReq.serializer()))
    suspend fun resetPassword(id: Int): PasswordDto =
        Api.post("/admin/users/$id/reset", enc(ResetReq(), ResetReq.serializer()))
    suspend fun deleteUser(id: Int) { Api.exec("DELETE", "/admin/users/$id") }

    suspend fun roles(): RolesDto = Api.get("/admin/roles")
    suspend fun createRole(req: RoleReq): IdDto = Api.post("/admin/roles", enc(req, RoleReq.serializer()))
    suspend fun updateRole(id: Int, req: RoleReq): IdDto = Api.put("/admin/roles/$id", enc(req, RoleReq.serializer()))
    suspend fun deleteRole(id: Int) { Api.exec("DELETE", "/admin/roles/$id") }

    suspend fun invites(): InvitesDto = Api.get("/admin/invitations")
    suspend fun createInvite(req: InviteReq): IdDto = Api.post("/admin/invitations", enc(req, InviteReq.serializer()))
    suspend fun revokeInvite(id: Int) { Api.exec("POST", "/admin/invitations/$id/revoke") }

    // ---------- 通知（本地提醒计划 + 设置） ----------
    suspend fun notifyPlan(days: Int = 14): NotifyPlanDto =
        Api.get("/notify/plan?days=$days", cache = false)

    suspend fun notifySettings(): NotifySettingsDto = Api.get("/notify/settings")

    suspend fun saveNotifySettings(req: NotifySettingsReq): NotifySettingsDto =
        Api.put("/notify/settings", Api.json.encodeToString(NotifySettingsReq.serializer(), req))

    // ---------- 导出 / 版本 ----------
    suspend fun exportAll(): ExportDto = Api.get("/export/all", cache = false)
    suspend fun version(): VersionDto = Api.get("/app/version")
}
