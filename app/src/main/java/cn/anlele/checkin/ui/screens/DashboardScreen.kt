package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.DashboardDto
import cn.anlele.checkin.data.HabitTodayDto
import cn.anlele.checkin.data.MeDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.ScheduleDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.BannerBar
import cn.anlele.checkin.ui.components.BannerTone
import cn.anlele.checkin.ui.components.CardFoot
import cn.anlele.checkin.ui.components.LoadingBlock
import cn.anlele.checkin.ui.components.NumIn
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/**
 * 工作台 —— 严格按网页手机端（390px）的卡片顺序与结构来做：
 *
 * 问候卡（含权限 chips）→ 提醒条 → 今日打卡 → 今日课程 → 未来 7 天日程 → 置顶备忘
 * → 最近纪念日 → 邮件 → 留言板 → 本月记账 → 情侣空间 → 经期助手 → 功能进展 → 管理后台
 *
 * 每张卡都带「卡尾链接」，卡片依次浮现（card-in，错峰 50ms）—— 与网页同一套节奏。
 */
@Composable
fun DashboardScreen(vm: AppVm, me: MeDto, go: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("notify_prefs", android.content.Context.MODE_PRIVATE) }
    var boardSeenAt by remember { mutableStateOf(prefs.getLong("board_seen_at", 0L)) }
    var data by remember { mutableStateOf<DashboardDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = data == null
        try {
            data = Repo.dashboard()
            error = null
            vm.setOffline(false)
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            vm.setOffline(true)
            if (data == null) error = "现在没有网络，连上 Wi-Fi 或流量再刷新"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    if (loading && data == null) {
        LoadingBlock("正在加载今天…")
        return
    }

    val d = data
    var idx = 0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 问候卡 ----------
        item {
            StaggerIn(idx++) {
                GreetingCard(me, d, onPrefs = { go("me") }, onRefresh = { scope.launch { load() } })
            }
        }

        if (error != null && d == null) {
            item { BannerBar(error!!, tone = BannerTone.Error) }
        }

        if (d == null) return@LazyColumn

        // ---------- 新消息提示（不弹系统通知，只在打开 App 时提示） ----------
        val unreadMail = d.mails?.unread ?: 0
        val newBoard = d.board.count {
            (Dates.parse(it.createdAt)?.time ?: 0L) > boardSeenAt
        }
        if (unreadMail > 0 || newBoard > 0) {
            item {
                StaggerIn(idx++) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.accentSoft, chipShape())
                            .clickable {
                                // 看过就记下时间，下次不再重复提示
                                if (newBoard > 0) {
                                    boardSeenAt = System.currentTimeMillis()
                                    prefs.edit().putLong("board_seen_at", boardSeenAt).apply()
                                    go("board")
                                } else {
                                    go("email")
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📬", fontSize = 15.sp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            buildList {
                                if (unreadMail > 0) add("$unreadMail 封未读邮件")
                                if (newBoard > 0) add("$newBoard 条新留言")
                            }.joinToString("、"),
                            fontSize = 13.sp,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        Text("去看看 →", fontSize = 12.sp, color = palette.accentStrong)
                    }
                }
            }
        }

        // ---------- 提醒条 ----------
        if (me.has("reminders.use") && d.reminderCount > 0) {
            item {
                StaggerIn(idx++) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.accentSoft, chipShape())
                            .clickable { go("reminders") }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚠️", fontSize = 15.sp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "近期有 ${d.reminderCount} 件事需要留意",
                            fontSize = 13.sp,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        Text("去提醒中心看看 →", fontSize = 12.sp, color = palette.accentStrong)
                    }
                }
            }
        }

        // ---------- 今日打卡 ----------
        if (me.has("habits.use") && d.habits.isNotEmpty()) {
            item {
                StaggerIn(idx++) {
                    DashCard("⏰", "今日打卡", "全部习惯 →", { go("habits") }) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            d.habits.forEach { h ->
                                HabitLine(h) {
                                    scope.launch {
                                        runCatching { Repo.dashboardCheckin(h.id) }
                                        load()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 今日课程 ----------
        if (me.has("courses.use")) {
            item {
                StaggerIn(idx++) {
                    DashCard(
                        "📚",
                        if (d.currentWeek > 0) "今日课程 · 第 ${d.currentWeek} 周" else "今日课程",
                        if (d.semesterConfigured) "完整课程表 →" else "去设置学期起始日 →",
                        { go("courses") },
                    ) {
                        if (d.todayCourses.isEmpty()) {
                            SiteEmpty("🎒", if (d.semesterConfigured) "今天没课，好好休息。" else "还没设置学期起始日，先设一下。")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                d.todayCourses.forEach { c ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            c.hours.ifBlank { "第${c.startPeriod}节" },
                                            fontSize = 11.5.sp,
                                            color = palette.accentStrong,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.width(78.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                c.name, fontSize = 14.sp, color = palette.text,
                                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                listOfNotNull(
                                                    c.location.ifBlank { null },
                                                    c.teacher.ifBlank { null },
                                                    c.weeksLabel.ifBlank { null },
                                                ).joinToString(" · "),
                                                fontSize = 11.5.sp,
                                                color = palette.textWeak,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 未来 7 天日程 ----------
        if (me.has("schedule.use")) {
            item {
                StaggerIn(idx++) {
                    DashCard("📅", "未来 7 天日程", "完整日程 →", { go("schedule") }) {
                        if (d.upcoming.isEmpty()) {
                            SiteEmpty("🗓", "这几天没有安排，去日历看一眼。")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                d.upcoming.forEach { s -> ScheduleLine(s) }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 置顶备忘 ----------
        if (me.has("memo.use")) {
            item {
                StaggerIn(idx++) {
                    DashCard("📌", "置顶备忘", "全部备忘 →", { go("memo") }) {
                        if (d.pinnedMemos.isEmpty()) {
                            SiteEmpty("📝", "还没有置顶的备忘，去备忘墙挑一条顶上来。")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                d.pinnedMemos.forEach { m ->
                                    Column {
                                        Text(m.title, fontSize = 13.5.sp, color = palette.text, fontWeight = FontWeight.Medium)
                                        if (m.content.isNotBlank())
                                            Text(
                                                m.content,
                                                fontSize = 12.5.sp,
                                                color = palette.textSub,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 19.sp,
                                            )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 最近纪念日 ----------
        d.nextAnniv?.let { a ->
            if (me.has("anniversary.use")) {
                item {
                    StaggerIn(idx++) {
                        DashCard("💛", "最近的纪念日", "全部纪念日 →", { go("anniversary") }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.title, fontSize = 13.5.sp, color = palette.textSub, modifier = Modifier.weight(1f))
                                Text(
                                    if (a.days == 0) "就是今天" else "还有 ${a.days} 天",
                                    fontSize = 17.sp,
                                    color = palette.accentStrong,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 邮件 ----------
        d.mails?.let { mail ->
            item {
                StaggerIn(idx++) {
                    DashCard("📬", "邮件", "打开收件箱 →", { go("email") }) {
                        if (!mail.hasAccounts) SiteEmpty("📮", "还没绑定邮箱，去「邮件 → 账号」添加一个（QQ / 163 / Gmail 都行）。")
                        else if (mail.recent.isEmpty()) SiteEmpty("📭", "收件箱是空的，点开邮件页同步一下。")
                        else Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            mail.recent.forEach { m ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!m.isRead) {
                                        Box(Modifier.size(6.dp).background(palette.accent, CircleShape))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(m.from, fontSize = 11.5.sp, color = palette.textWeak, maxLines = 1)
                                        Text(
                                            m.subject,
                                            fontSize = 13.sp,
                                            color = if (m.isRead) palette.textSub else palette.text,
                                            fontWeight = if (m.isRead) FontWeight.Normal else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(Dates.fmt(m.sentAt, "M月d日"), fontSize = 10.5.sp, color = palette.textWeak)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 留言板 ----------
        if (me.has("board.use")) {
            item {
                StaggerIn(idx++) {
                    DashCard("💬", "留言板", "去留言板 →", { go("board") }) {
                        if (d.board.isEmpty()) SiteEmpty("💭", "还没有人留言，说点什么吧。")
                        else Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            d.board.forEach { b ->
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(b.author, fontSize = 11.5.sp, color = palette.accentStrong)
                                        Spacer(Modifier.width(6.dp))
                                        Text(Dates.fmt(b.createdAt, "M月d日 HH:mm"), fontSize = 10.5.sp, color = palette.textWeak)
                                    }
                                    Text(
                                        b.content,
                                        fontSize = 12.5.sp,
                                        color = palette.textSub,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 19.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 本月记账 ----------
        d.expense?.let { e ->
            item {
                StaggerIn(idx++) {
                    DashCard("💰", "本月记账", "记账本 →", { go("expense") }) {
                        Row(Modifier.fillMaxWidth()) {
                            DashStat("支出", "¥${Dates.money(e.monthOut)}", Modifier.weight(1f))
                            DashStat("收入", "¥${Dates.money(e.monthIn)}", Modifier.weight(1f))
                            DashStat("笔数", "${e.entries}", Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ---------- 情侣空间 ----------
        d.couple?.let { c ->
            item {
                StaggerIn(idx++) {
                    DashCard("💗", c.title.ifBlank { "情侣空间" }, "进入情侣空间 →", { go("couples") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${c.days}", fontSize = 22.sp, color = palette.accentStrong, fontWeight = FontWeight.SemiBold)
                            Text(" 天", fontSize = 12.sp, color = palette.textSub)
                            Spacer(Modifier.weight(1f))
                        }
                        c.lastNote?.let { n ->
                            Text(
                                "「${n.content}」—— ${n.from}",
                                fontSize = 12.sp,
                                color = palette.textWeak,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        // ---------- 经期助手 ----------
        d.period?.let { pr ->
            item {
                StaggerIn(idx++) {
                    DashCard("🌸", "经期助手", "打开经期助手 →", { go("period") }) {
                        Text(
                            "最近记录：" + Dates.dayMonth(pr.startDate) +
                                (pr.endDate?.let { " – ${Dates.dayMonth(it)}" } ?: " 起"),
                            fontSize = 13.sp,
                            color = palette.textSub,
                        )
                    }
                }
            }
        }

        // ---------- 功能进展 ----------
        item {
            StaggerIn(idx++) {
                DashCard("🚀", "功能进展", null, {}) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8").forEach { label ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .background(palette.accent, chipShape())
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(label, fontSize = 9.sp, color = palette.textWeak, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("8 个模块全部上线，数据互通。", fontSize = 11.5.sp, color = palette.textWeak)
                    }
                }
            }
        }

        // ---------- 管理后台 ----------
        if (me.has("admin.users") || me.has("admin.roles")) {
            item {
                StaggerIn(idx) {
                    DashCard("🛠", "管理后台", null, {}) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (me.has("admin.users")) {
                                AdminLink("用户管理", "建号、改角色、重置密码、启停") { go("admin-users") }
                                AdminLink("邀请码", "生成一次性邀请码发给朋友") { go("admin-invites") }
                            }
                            if (me.has("admin.roles")) {
                                AdminLink("角色管理", "自定义角色与权限组合") { go("admin-roles") }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 测试数据提示 ----------
        if (d.showSampleBanner) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("检测到测试数据", fontSize = 14.sp, color = palette.text, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "账号里有 ${d.sampleRows} 条带【测试】标记的记录，可以一键清掉（真实数据不受影响）。",
                            fontSize = 12.5.sp,
                            color = palette.textSub,
                        )
                        Spacer(Modifier.height(10.dp))
                        TinyAction("一键删除测试数据", "trash-2", danger = true) {
                            scope.launch {
                                runCatching { Repo.clearSample() }
                                vm.toast("已清理")
                                load()
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

// ==================== 卡片与行 ====================

/** 工作台卡片：emoji 标题 + 右上小图标 + 内容 + 卡尾链接（对应网页 `.card > h2` + `.hint-foot`） */
@Composable
private fun DashCard(
    emoji: String,
    title: String,
    foot: String?,
    onOpen: () -> Unit,
    content: @Composable () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(emoji, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    fontSize = 13.5.sp,
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(AppIcons["settings-2"], null, Modifier.size(13.dp), tint = palette.textWeak.copy(alpha = .7f))
            }
            Spacer(Modifier.height(10.dp))
            content()
            if (foot != null) CardFoot(foot, onOpen)
        }
    }
}

@Composable
private fun DashStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = palette.textWeak)
        Spacer(Modifier.height(2.dp))
        NumIn(value, 16.sp, palette.text)
    }
}

/** 打卡行：emoji 方块 + 名字 + 累计/连续 + 右侧打卡按钮（与网页 `.list-line` 一致） */
@Composable
private fun HabitLine(h: HabitTodayDto, onCheck: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(palette.cardSoft, chipShape()),
            contentAlignment = Alignment.Center,
        ) { Text(h.icon, fontSize = 17.sp) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                h.name, fontSize = 14.5.sp, color = palette.text, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "累计 ${h.total} 天" + if (h.streak > 0) " · 连续 ${h.streak} 天" else "",
                fontSize = 11.5.sp,
                color = palette.textWeak,
            )
        }
        if (h.checked) {
            Row(
                Modifier.background(palette.accentSoft, chipShape()).padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons["check"], null, Modifier.size(12.dp), tint = palette.accent)
                Spacer(Modifier.width(4.dp))
                Text("今日已打卡", fontSize = 11.5.sp, color = palette.accent)
            }
        } else {
            Box(
                Modifier
                    .background(palette.accent, chipShape())
                    .clickable(onClick = onCheck)
                    .padding(horizontal = 15.dp, vertical = 7.dp),
            ) { Text("打卡", fontSize = 12.sp, color = palette.onAccent) }
        }
    }
}

@Composable
private fun ScheduleLine(s: ScheduleDto) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(
            Dates.relative(s.startAt) + " " + (if (s.allDay) "全天" else Dates.hhmm(s.startAt)),
            fontSize = 11.5.sp,
            color = palette.accentStrong,
            modifier = Modifier.width(88.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                s.title, fontSize = 13.5.sp, color = palette.text, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (s.description.isNotBlank())
                Text(s.description, fontSize = 11.5.sp, color = palette.textWeak, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AdminLink(title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.cardSoft, chipShape())
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = palette.text, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 11.sp, color = palette.textWeak)
        }
        Icon(AppIcons["chevron-right"], null, Modifier.size(13.dp), tint = palette.textWeak)
    }
}

// ==================== 问候卡 ====================

@Composable
private fun GreetingCard(me: MeDto, d: DashboardDto?, onPrefs: () -> Unit, onRefresh: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 15.dp)) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Dates.greeting()}，${me.displayName}",
                        fontSize = 19.sp,
                        color = palette.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        listOfNotNull(
                            d?.today ?: Dates.today(),
                            d?.weekday ?: Dates.weekdayCn(Dates.today()),
                            if ((d?.currentWeek ?: 0) > 0) "第 ${d?.currentWeek} 周" else null,
                        ).joinToString("　"),
                        fontSize = 11.5.sp,
                        color = palette.textWeak,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons["rotate-ccw"], "刷新", Modifier.size(16.dp).clickable(onClick = onRefresh),
                        tint = palette.textWeak,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        AppIcons["settings-2"], "偏好设置", Modifier.size(16.dp).clickable(onClick = onPrefs),
                        tint = palette.textWeak,
                    )
                }
            }
            if (me.permissionLabels.isNotEmpty()) {
                Spacer(Modifier.height(11.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    me.permissionLabels.forEach { label -> AppChip(label) }
                }
            }
        }
    }
}
