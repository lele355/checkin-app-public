package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.AnnivCardDto
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.CoupleCardDto
import cn.anlele.checkin.data.CourseTodayDto
import cn.anlele.checkin.data.DashLayout
import cn.anlele.checkin.data.DashboardDto
import cn.anlele.checkin.data.ExpenseCardDto
import cn.anlele.checkin.data.HabitTodayDto
import cn.anlele.checkin.data.MailsCardDto
import cn.anlele.checkin.data.MeDto
import cn.anlele.checkin.data.PeriodCardDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.ScheduleDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.BannerBar
import cn.anlele.checkin.ui.components.BannerTone
import cn.anlele.checkin.ui.components.CardFoot
import cn.anlele.checkin.ui.components.LoadingBlock
import cn.anlele.checkin.ui.components.NumIn
import cn.anlele.checkin.ui.components.PickerSheetShell
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.SiteSwitch
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/**
 * 工作台 —— 结构照网页手机端（390px）来，但**卡片顺序由用户决定**：
 *
 * 问候 → 提醒条 → 今日打卡 → 今日课程 → 未来 7 天日程 → 置顶备忘 → 最近纪念日
 * → 邮件 → 留言板 → 本月记账 → 情侣空间 → 经期助手（默认顺序，之后再跟功能进展 / 管理后台）
 *
 * 【两种操作，两套语义】
 * - 每张卡右上角的图钉：**点一下立刻生效**（置顶 / 取消置顶），不等网络、不弹确认。
 *   落库走 AppVm.saveDashboardLayout：先改内存 + 本地缓存，网络在后台补。
 * - 问候卡右上角的齿轮：打开「自定义工作台」面板，勾显隐、上下排序，点「保存」才落库。
 *
 * 【与网页端是同一份数据】布局存在 Users.DashboardLayout，是 `{pinned, order, hidden}` 三组 id；
 * 手机上调好的顺序，网页上打开就是同一个。
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
    var customOpen by remember { mutableStateOf(false) }

    // 布局从 MeDto 里解。AppVm 保存后换掉 ui.me，这里跟着重组，所以不必再存一份本地 state
    // （存了反而要把两条更新路径对齐，容易出现「点了图钉但界面没动」）。
    val layout = remember(me.dashboardLayout) { DashLayout.from(me.dashboardLayout) }

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

    // 面板里用的中文名。卡片因权限没开 / 条件不满足而不渲染时，也得认得出这是哪一张。
    val names = remember {
        mapOf(
            "hello" to "问候", "reminders" to "提醒", "habits" to "今日打卡",
            "courses" to "今日课程", "schedule" to "未来 7 天日程", "memo" to "置顶备忘",
            "anniversary" to "最近的纪念日", "email" to "邮件", "board" to "留言板",
            "expense" to "本月记账", "couples" to "情侣空间", "period" to "经期助手",
        )
    }

    val available = availableCards(me, d)
    val ordered = layout.arrange(available)

    val listState = rememberLazyListState()
    // 置顶时把这块滚到眼前：置顶是「我要一直看到它」，只把它挪到上面用户还得自己滑上去找，
    // 所以顺手滚过去 —— 让「置顶这件事」被看见。
    fun onPin(id: String) {
        val wasPinned = layout.isPinned(id)
        vm.saveDashboardLayout(layout.togglePin(id))
        if (!wasPinned) {
            scope.launch {
                kotlinx.coroutines.delay(160)   // 等这一帧重组完，位置才排定
                // 问候块常驻第一（index 0），置顶区从它后面开始
                listState.animateScrollToItem(if ("hello" in ordered) 1 else 0)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (error != null && d == null) {
            item { BannerBar(error!!, tone = BannerTone.Error) }
        }

        // 问候卡被隐藏时，齿轮也跟着没了 —— 这里补一个入口，
        // 否则「自定义工作台」面板再也打不开，等于把用户锁在门外。
        if ("hello" !in ordered) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(chipShape())
                        .background(palette.cardSoft)
                        .clickable { customOpen = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons["settings-2"], null, Modifier.size(14.dp), tint = palette.textSub)
                    Spacer(Modifier.width(8.dp))
                    Text("自定义工作台", fontSize = 13.sp, color = palette.textSub, modifier = Modifier.weight(1f))
                    Icon(AppIcons["chevron-right"], null, Modifier.size(13.dp), tint = palette.textWeak)
                }
            }
        }

        // ---------- 新消息提示（不弹系统通知，只在对打开 App 时提示；不属于卡片布局） ----------
        if (d != null) {
            val unreadMail = d.mails?.unread ?: 0
            val newBoard = d.board.count { (Dates.parse(it.createdAt)?.time ?: 0L) > boardSeenAt }
            if (unreadMail > 0 || newBoard > 0) {
                item {
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

        // ---------- 可自定义的卡片 ----------
        items(ordered.size, key = { ordered[it] }) { i ->
            val id = ordered[i]
            // animateItem：卡片换位置时做补间，置顶/取消置顶能看见它「挪上去」的过程，
            // 而不是「刷新一下突然出现在最上面」。
            Box(Modifier.animateItem()) {
            StaggerIn(i) {
                when (id) {
                    "hello" -> GreetingCard(
                        me, d,
                        pinned = true,
                        onTogglePin = null,
                        onCustom = { customOpen = true },
                        onRefresh = { scope.launch { load() } },
                    )

                    "reminders" -> if (d != null) ReminderCard(
                        d.reminderCount,
                        pinned = layout.isPinned(id),
                        onTogglePin = { onPin(id) },
                        onOpen = { go("reminders") },
                    )

                    "habits" -> if (d != null) HabitsCard(
                        d,
                        pinned = layout.isPinned(id),
                        onTogglePin = { onPin(id) },
                        onOpen = { go("habits") },
                        onReload = { scope.launch { load() } },
                    )

                    "courses" -> if (d != null) CoursesCard(
                        d,
                        pinned = layout.isPinned(id),
                        onTogglePin = { onPin(id) },
                        onOpen = { go("courses") },
                    )

                    "schedule" -> if (d != null) ScheduleCard(
                        d,
                        pinned = layout.isPinned(id),
                        onTogglePin = { onPin(id) },
                        onOpen = { go("schedule") },
                    )

                    "memo" -> if (d != null) MemoCard(
                        d,
                        pinned = layout.isPinned(id),
                        onTogglePin = { onPin(id) },
                        onOpen = { go("memo") },
                    )

                    "anniversary" -> d?.nextAnniv?.let { a ->
                        AnnivCard(
                            a,
                            pinned = layout.isPinned(id),
                            onTogglePin = { onPin(id) },
                            onOpen = { go("anniversary") },
                        )
                    }

                    "email" -> d?.mails?.let { mail ->
                        MailCard(
                            mail,
                            pinned = layout.isPinned(id),
                            onTogglePin = { onPin(id) },
                            onOpen = { go("email") },
                        )
                    }

                    "board" -> if (d != null) BoardCard(
                        d,
                        pinned = layout.isPinned(id),
                        onTogglePin = { onPin(id) },
                        onOpen = { go("board") },
                    )

                    "expense" -> d?.expense?.let { e ->
                        ExpenseCard(
                            e,
                            pinned = layout.isPinned(id),
                            onTogglePin = { onPin(id) },
                            onOpen = { go("expense") },
                        )
                    }

                    "couples" -> d?.couple?.let { c ->
                        CoupleCard(
                            c,
                            pinned = layout.isPinned(id),
                            onTogglePin = { onPin(id) },
                            onOpen = { go("couples") },
                        )
                    }

                    "period" -> d?.period?.let { pr ->
                        PeriodCard(
                            pr,
                            pinned = layout.isPinned(id),
                            onTogglePin = { onPin(id) },
                            onOpen = { go("period") },
                        )
                    }
                }
            }
            }   // Box(animateItem)
        }

        if (d == null) return@LazyColumn

        // ---------- 功能进展（不参与自定义布局：它是说明卡，不是数据模块） ----------
        item {
            StaggerIn(7) {
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
                StaggerIn(7) {
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

    if (customOpen) {
        DashCustomSheet(
            // fill：把「能渲染但布局里没提到」的卡补进普通区，否则面板里只有被点过的那几张；
            // prune：只列当前真能渲染的卡，避免条件卡（没数据就不渲染）以「开着」的开关出现在面板里。
            layout = layout.fill(available).prune(available),
            defaults = available,
            names = names,
            onDismiss = { customOpen = false },
            onSave = {
                customOpen = false
                vm.saveDashboardLayout(it)
            },
        )
    }
}

/**
 * 这台设备当前「能渲染哪些卡片」，按默认顺序。
 *
 * 这张表就是默认布局的基准 —— 面板里的「恢复默认」等于把三组清空，靠它重新排。
 * 不能拿「当前渲染顺序」当默认：那样恢复默认就等于什么都没做。
 *
 * build / admin 两张卡网页端有、App 里没有（不渲染），所以这里也不列，
 * 但它们仍留在 DashLayout.Known 白名单里，保存时不会被剔掉。
 */
private fun availableCards(me: MeDto, d: DashboardDto?): List<String> = buildList {
    add("hello")
    if (d == null) return@buildList
    if (me.has("reminders.use") && d.reminderCount > 0) add("reminders")
    if (me.has("habits.use") && d.habits.isNotEmpty()) add("habits")
    if (me.has("courses.use")) add("courses")
    if (me.has("schedule.use")) add("schedule")
    if (me.has("memo.use")) add("memo")
    if (me.has("anniversary.use") && d.nextAnniv != null) add("anniversary")
    if (d.mails != null) add("email")
    if (me.has("board.use")) add("board")
    if (d.expense != null) add("expense")
    if (d.couple != null) add("couples")
    if (d.period != null) add("period")
}

// ==================== 卡片与行 ====================

/** 工作台卡片：emoji 标题 + 右上角图钉 + 内容 + 卡尾链接（对应网页 `.card > h2` + `.hint-foot`） */
@Composable
private fun DashCard(
    emoji: String,
    title: String,
    foot: String?,
    onOpen: () -> Unit,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
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
                if (onTogglePin != null) PinButton(pinned, onTogglePin)
            }
            Spacer(Modifier.height(10.dp))
            content()
            if (foot != null) CardFoot(foot, onOpen)
        }
    }
}

/**
 * 卡片右上角的图钉 —— 点一下置顶 / 取消置顶。
 *
 * 这里以前放的是 settings-2 装饰图标，看着像「置顶」但点不动，
 * 用户按了没反应只能改用状态栏入口 —— 现在它真的是按钮：
 * 置顶后变强调色 + 倾斜 45°（图钉扎进卡片的感觉），与网页端 `.card-pin.on` 一致。
 */
@Composable
private fun PinButton(pinned: Boolean, onToggle: () -> Unit) {
    val p = palette
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (pinned) p.accentSoft else Color.Transparent)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons["pin"],
            contentDescription = if (pinned) "取消置顶" else "置顶到最上面",
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer { rotationZ = if (pinned) -45f else 0f },
            tint = if (pinned) p.accentStrong else p.textWeak.copy(alpha = .55f),
        )
    }
}

/** 问候卡右上角的图标按钮（刷新 / 自定义），与图钉同尺寸，排一行不显拥挤 */
@Composable
private fun HeaderIcon(icon: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[icon], contentDescription = desc, modifier = Modifier.size(15.dp), tint = palette.textWeak)
    }
}

@Composable
private fun GreetingCard(
    me: MeDto,
    d: DashboardDto?,
    pinned: Boolean,
    // 传 null = 不显示置顶按钮。问候块永远在第一块，没有「置顶/取消」的意义。
    onTogglePin: (() -> Unit)?,
    onCustom: () -> Unit,
    onRefresh: () -> Unit,
) {
    // 右边三个按钮各 30dp，卡片右侧内边距收到 10dp 才不至于把标题挤得太窄
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 10.dp, top = 15.dp, bottom = 15.dp)) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
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
                    HeaderIcon("rotate-ccw", "刷新", onRefresh)
                    HeaderIcon("settings-2", "自定义工作台", onCustom)
                    if (onTogglePin != null) PinButton(pinned, onTogglePin)
                }
            }
            if (me.permissionLabels.isNotEmpty()) {
                Spacer(Modifier.height(11.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    me.permissionLabels.forEach { label -> AppChip(label) }
                }
            }
        }
    }
}

/** 提醒条：内容一行 + 图钉。左右内边距比普通卡小，因为这行本身就是一条窄横幅 */
@Composable
private fun ReminderCard(count: Int, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", fontSize = 15.sp)
            Spacer(Modifier.width(9.dp))
            Text(
                "近期有 $count 件事需要留意",
                fontSize = 13.sp,
                color = palette.text,
                modifier = Modifier.weight(1f).clickable(onClick = onOpen),
            )
            Text(
                "查看 →",
                fontSize = 12.sp,
                color = palette.accentStrong,
                modifier = Modifier.clickable(onClick = onOpen),
            )
            Spacer(Modifier.width(2.dp))
            PinButton(pinned, onTogglePin)
        }
    }
}

@Composable
private fun HabitsCard(
    d: DashboardDto,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onOpen: () -> Unit,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    DashCard("⏰", "今日打卡", "全部习惯 →", onOpen, pinned, onTogglePin) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            d.habits.forEach { h ->
                HabitLine(h) {
                    scope.launch {
                        runCatching { Repo.dashboardCheckin(h.id) }
                        onReload()
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursesCard(d: DashboardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard(
        "📚",
        if (d.currentWeek > 0) "今日课程 · 第 ${d.currentWeek} 周" else "今日课程",
        if (d.semesterConfigured) "完整课程表 →" else "去设置学期起始日 →",
        onOpen, pinned, onTogglePin,
    ) {
        if (d.todayCourses.isEmpty()) {
            SiteEmpty("🎒", if (d.semesterConfigured) "今天没课，好好休息。" else "还没设置学期起始日，先设一下。")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                d.todayCourses.forEach { c -> CourseLine(c) }
            }
        }
    }
}

@Composable
private fun CourseLine(c: CourseTodayDto) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
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

@Composable
private fun ScheduleCard(d: DashboardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard("📅", "未来 7 天日程", "完整日程 →", onOpen, pinned, onTogglePin) {
        if (d.upcoming.isEmpty()) {
            SiteEmpty("🗓", "这几天没有安排，去日历看一眼。")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                d.upcoming.forEach { s -> ScheduleLine(s) }
            }
        }
    }
}

@Composable
private fun MemoCard(d: DashboardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard("📌", "置顶备忘", "全部备忘 →", onOpen, pinned, onTogglePin) {
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

@Composable
private fun AnnivCard(a: AnnivCardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard("💛", "最近的纪念日", "全部纪念日 →", onOpen, pinned, onTogglePin) {
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

@Composable
private fun MailCard(mail: MailsCardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard("📬", "邮件", "打开收件箱 →", onOpen, pinned, onTogglePin) {
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

@Composable
private fun BoardCard(d: DashboardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard("💬", "留言板", "去留言板 →", onOpen, pinned, onTogglePin) {
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

@Composable
private fun ExpenseCard(e: ExpenseCardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard("💰", "本月记账", "记账本 →", onOpen, pinned, onTogglePin) {
        Row(Modifier.fillMaxWidth()) {
            DashStat("支出", "¥${Dates.money(e.monthOut)}", Modifier.weight(1f))
            DashStat("收入", "¥${Dates.money(e.monthIn)}", Modifier.weight(1f))
            DashStat("笔数", "${e.entries}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CoupleCard(c: CoupleCardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard("💗", c.title.ifBlank { "情侣空间" }, "进入情侣空间 →", onOpen, pinned, onTogglePin) {
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

@Composable
private fun PeriodCard(pr: PeriodCardDto, pinned: Boolean, onTogglePin: () -> Unit, onOpen: () -> Unit) {
    DashCard("🌸", "经期助手", "打开经期助手 →", onOpen, pinned, onTogglePin) {
        Text(
            "最近记录：" + Dates.dayMonth(pr.startDate) +
                (pr.endDate?.let { " – ${Dates.dayMonth(it)}" } ?: " 起"),
            fontSize = 13.sp,
            color = palette.textSub,
        )
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
                Text(
                    s.description, fontSize = 11.5.sp, color = palette.textWeak,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
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

// ==================== 自定义工作台面板 ====================

/**
 * 「自定义工作台」面板。
 *
 * 形态用 [PickerSheetShell]（Dialog）而不是 ModalBottomSheet：全 App 只允许 EditSheet / PrefsSheet
 * 两个 ModalBottomSheet，再叠一个会出现「后弹的那层被压在前一层底下」「返回键一次关两层」
 * 这类各家 ROM 表现不一致的问题。
 *
 * 【为什么不做实时预览】网页上关掉面板还能看到网格在重排；手机上弹出的是一张贴底的整面板，
 * 卡片在它背后根本看不见，实时重排只会显得像卡顿。所以这里改的是草稿，点「保存」才生效，
 * 关掉面板 = 丢弃（与网页端「关面板不保存 = 丢掉草稿」的语义一致）。
 */
@Composable
private fun DashCustomSheet(
    layout: DashLayout,
    defaults: List<String>,
    names: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (DashLayout) -> Unit,
) {
    val p = palette
    var draft by remember { mutableStateOf(layout) }

    PickerSheetShell(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("自定义工作台", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "置顶区的卡片永远排在最上面；取消「显示」就收起这张卡（数据不会丢，随时能勾回来）。",
                fontSize = 12.sp, color = p.textWeak, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))

            DashSection(
                "置顶区", draft.pinned, names, draft,
                onPin = { id -> draft = draft.togglePin(id) },
                onVisible = { id, v -> draft = draft.setVisible(id, v) },
                onMove = { id, d -> draft = draft.move(id, d) },
            )
            DashSection(
                "普通区", draft.order, names, draft,
                onPin = { id -> draft = draft.togglePin(id) },
                onVisible = { id, v -> draft = draft.setVisible(id, v) },
                onMove = { id, d -> draft = draft.move(id, d) },
            )
            // 隐藏区不给排序按钮：看不见的卡片排位置没意义，也容易让人以为「挪出来就显示了」
            DashSection(
                "已隐藏", draft.hidden, names, draft,
                onPin = { id -> draft = draft.togglePin(id) },
                onVisible = { id, v -> draft = draft.setVisible(id, v) },
                onMove = { _, _ -> },
            )

            Spacer(Modifier.height(6.dp))
            PrimaryButton("保存", Modifier.fillMaxWidth()) { onSave(draft.clean()) }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("恢复默认", Modifier.fillMaxWidth()) {
                // 不能直接给 DashLayout()：那样三组全空，面板会当场变空（看着像把卡片全删了）。
                // 空布局的语义是「按默认顺序排」，所以这里把默认顺序显式写进普通区。
                draft = DashLayout(order = defaults)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DashSection(
    title: String,
    ids: List<String>,
    names: Map<String, String>,
    draft: DashLayout,
    onPin: (String) -> Unit,
    onVisible: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    val p = palette
    Text(
        "$title（${ids.size}）",
        fontSize = 11.sp, color = p.textSub, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
    )
    if (ids.isEmpty()) {
        Text(
            if (title == "置顶区") "还没有置顶的卡片，点卡片右上角的图钉试试" else "（空）",
            fontSize = 12.sp, color = p.textWeak,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
    ids.forEachIndexed { i, id ->
        DashRow(
            name = names[id] ?: id,
            pinned = draft.isPinned(id),
            visible = !draft.isHidden(id),
            isHidden = title == "已隐藏",
            canUp = i > 0,
            canDown = i < ids.size - 1,
            onPin = { onPin(id) },
            onVisible = { onVisible(id, it) },
            onUp = { onMove(id, -1) },
            onDown = { onMove(id, 1) },
        )
    }
}

@Composable
private fun DashRow(
    name: String,
    pinned: Boolean,
    visible: Boolean,
    isHidden: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onPin: () -> Unit,
    onVisible: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(chipShape())
            .background(p.cardSoft)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniIconButton(
            "pin", !isHidden,
            if (pinned) "取消置顶" else "置顶到最上面",
            tint = when {
                isHidden -> p.textWeak.copy(alpha = .25f)
                pinned -> p.accentStrong
                else -> p.textSub
            },
            rotate = if (pinned) -45f else 0f,
            onClick = onPin,
        )
        Text(
            name,
            fontSize = 13.sp,
            color = if (visible) p.text else p.textWeak,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        MiniIconButton("chevron-up", visible && canUp, "上移", onClick = onUp)
        MiniIconButton("chevron-down", visible && canDown, "下移", onClick = onDown)
        Spacer(Modifier.width(4.dp))
        // 问候卡也允许隐藏（网页端同样允许）。它藏着面板入口，所以 DashboardScreen 里
        // 额外留了一条「自定义工作台」入口 —— 隐藏了也进得来，不会把自己锁在门外。
        SiteSwitch(checked = visible, onChange = { onVisible(it) })
    }
}

@Composable
private fun MiniIconButton(
    icon: String,
    enabled: Boolean,
    desc: String,
    tint: Color = palette.textSub,
    rotate: Float = 0f,
    onClick: () -> Unit,
) {
    val p = palette
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons[icon],
            contentDescription = desc,
            modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = rotate },
            tint = if (enabled) tint else p.textWeak.copy(alpha = .25f),
        )
    }
}
