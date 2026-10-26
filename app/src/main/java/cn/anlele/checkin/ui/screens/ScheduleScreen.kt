package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.push.NotifyResync
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.ScheduleDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.DateField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.TimeField
import cn.anlele.checkin.ui.components.GroupHead
import cn.anlele.checkin.ui.components.HeadButton
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.SiteRow
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.components.ViewSwitch
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

private val GROUP_ORDER = listOf("今天", "明天", "一周内", "更远", "已过")

/**
 * 日程 —— 与网页手机端一致：默认「列表视图」，按 今天 / 明天 / 一周内 / 更远 / 已过 分组，
 * 分组标题吸在卡片顶部；右上角可切「日历视图」（照片式月历）。
 */
@Composable
fun ScheduleScreen(vm: AppVm) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var view by remember { mutableStateOf("列表") }
    var ym by remember { mutableStateOf(Dates.thisMonth()) }
    var selected by remember { mutableStateOf(Dates.today()) }
    var list by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduleDto?>(null) }
    var presetDate by remember { mutableStateOf(Dates.today()) }

    suspend fun load() {
        loading = true
        try {
            // 与网页一致：保留最近 7 天已过的便于回看，往后拉 4 个月够日历翻
            val from = Dates.addDays(Dates.today(), -7)
            val to = Dates.addDays(Dates.today(), 120)
            list = Repo.schedules(from, to).items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val today = Dates.today()

    fun groupOf(s: ScheduleDto): String {
        val day = s.startAt.take(10)
        return when {
            day == today -> "今天"
            day == Dates.addDays(today, 1) -> "明天"
            day > Dates.addDays(today, 1) && day <= Dates.addDays(today, 7) -> "一周内"
            day > Dates.addDays(today, 7) -> "更远"
            else -> "已过"
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PageHeadCard(
                    title = "日程",
                    actions = {
                        ViewSwitch(
                            options = listOf("list" to "列表", "calendar" to "日历"),
                            selected = view,
                            onSelect = { view = it },
                        )
                        HeadButton("新建日程") {
                            editing = null
                            presetDate = if (view == "日历") selected else Dates.today()
                            editorOpen = true
                        }
                    },
                )
            }

            if (view == "列表") {
                val groups = list.groupBy { groupOf(it) }
                if (list.isEmpty() && !loading) {
                    item { AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) { SiteEmpty("📅", "还没有日程") } }
                }
                GROUP_ORDER.forEach { name ->
                    val rows = groups[name].orEmpty().sortedBy { it.startAt }
                    if (rows.isNotEmpty()) {
                        item {
                            StaggerIn(GROUP_ORDER.indexOf(name)) {
                                AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                                    Column {
                                        GroupHead(name)
                                        rows.forEachIndexed { i, s ->
                                            SiteRow(
                                                showTopBorder = i > 0,
                                                onClick = { editing = s; presetDate = s.startAt.take(10); editorOpen = true },
                                                meta = {
                                                    Column {
                                                        Text(
                                                            if (s.allDay) "${Dates.dayMonth(s.startAt)} 全天"
                                                            else Dates.dayTime(s.startAt),
                                                            fontSize = 11.5.sp,
                                                            color = p.textSub,
                                                            lineHeight = 16.sp,
                                                        )
                                                    }
                                                },
                                                trailing = {
                                                    TinyAction("编辑", "pencil") {
                                                        editing = s
                                                        presetDate = s.startAt.take(10)
                                                        editorOpen = true
                                                    }
                                                },
                                            ) {
                                                Text(
                                                    s.title, fontSize = 14.5.sp, color = p.text,
                                                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                )
                                                if (s.description.isNotBlank())
                                                    Text(
                                                        s.description, fontSize = 12.sp, color = p.textWeak,
                                                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(top = 2.dp),
                                                    )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ---------- 日历视图 ----------
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
                        Column {
                            // 翻月头：‹ 2026 年 10 月 ›
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconBtn("chevron-left", "上个月", size = 40.dp) { ym = Dates.addMonths(ym, -1) }
                                Text(
                                    Dates.monthTitle(ym),
                                    fontSize = 17.sp,
                                    color = p.text,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f).clickable {
                                        ym = Dates.thisMonth()
                                        selected = today
                                    },
                                )
                                IconBtn("chevron-right", "下个月", size = 40.dp) { ym = Dates.addMonths(ym, 1) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                                    Text(
                                        d, fontSize = 11.sp, color = p.textWeak, textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                                    )
                                }
                            }
                            val cells = mondayFirstGrid(ym)
                            val byDay = list.groupBy { it.startAt.take(10) }
                            cells.chunked(7).forEach { week ->
                                Row(Modifier.fillMaxWidth()) {
                                    week.forEach { day ->
                                        DayCell(
                                            day = day,
                                            selected = day == selected,
                                            today = day == today,
                                            dots = byDay[day].orEmpty().size,
                                            modifier = Modifier.weight(1f),
                                        ) { if (day != null) selected = day }
                                    }
                                }
                            }
                        }
                    }
                }

                val dayItems = list.filter { it.startAt.take(10) == selected }.sortedBy { it.startAt }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                        Column {
                            GroupHead("${Dates.relative(selected)} · ${Dates.weekdayCn(selected)}")
                            if (dayItems.isEmpty()) {
                                SiteEmpty("🗓", "这一天还没有安排。")
                            } else {
                                dayItems.forEachIndexed { i, s ->
                                    SiteRow(
                                        showTopBorder = i > 0,
                                        onClick = { editing = s; presetDate = selected; editorOpen = true },
                                        meta = {
                                            Text(
                                                if (s.allDay) "全天" else Dates.hhmm(s.startAt),
                                                fontSize = 12.sp, color = p.accentStrong, fontWeight = FontWeight.Medium,
                                            )
                                        },
                                    ) {
                                        Text(s.title, fontSize = 14.sp, color = p.text, fontWeight = FontWeight.Medium)
                                        if (s.description.isNotBlank())
                                            Text(s.description, fontSize = 11.5.sp, color = p.textWeak, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        ScheduleEditor(
            vm = vm,
            initial = editing,
            presetDate = presetDate,
            onClose = { editorOpen = false },
            onSaved = {
                editorOpen = false
                scope.launch { load() }
                // 日程变了，提醒计划也要跟着重排（否则新日程不会响、删掉的还会响）
                NotifyResync.trigger(ctx)
            },
        )
    }
}

/** 周一开始的月历网格（网页 cal-grid 是「一」开头，不是周日） */
private fun mondayFirstGrid(ym: String): List<String?> {
    val sunday = Dates.monthGrid(ym)          // 周日开头 42 格
    if (sunday.size < 42) return sunday
    val out = ArrayList<String?>(42)
    // 把每周的第一格（周日）挪到末尾
    sunday.chunked(7).forEach { week ->
        for (i in 1..6) out.add(week.getOrNull(i))
        out.add(week.getOrNull(0))
    }
    return out
}

@Composable
private fun DayCell(
    day: String?,
    selected: Boolean,
    today: Boolean,
    dots: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = palette
    Box(modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        if (day == null) return@Box
        val bg = when {
            selected -> p.accent
            today -> p.accentSoft
            else -> Color.Transparent
        }
        Column(
            Modifier.fillMaxSize().background(bg, chipShape()).clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                Dates.dayOfMonth(day).toString(),
                fontSize = 13.sp,
                color = if (selected) p.onAccent else if (today) p.accent else p.text,
                fontWeight = if (selected || today) FontWeight.Medium else FontWeight.Normal,
            )
            if (dots > 0) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(minOf(dots, 3)) {
                        Box(
                            Modifier.size(3.dp).background(if (selected) p.onAccent else p.accent, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditor(
    vm: AppVm,
    initial: ScheduleDto?,
    presetDate: String,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var allDay by remember { mutableStateOf(initial?.allDay ?: false) }
    var date by remember { mutableStateOf(initial?.startAt?.take(10) ?: presetDate) }
    var startTime by remember { mutableStateOf(initial?.startAt?.let { Dates.hhmm(it) } ?: "09:00") }
    var endTime by remember { mutableStateOf(initial?.endAt?.let { Dates.hhmm(it) } ?: "10:00") }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = if (initial == null) "新建日程" else "编辑日程",
        onClose = onClose,
        saving = saving,
        destructive = if (initial != null) ({ SheetDelete { scope.launch { runCatching { Repo.deleteSchedule(initial.id) }; onSaved() } } }) else null,
        onSave = {
            if (title.isBlank()) { vm.toast("标题不能为空"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    val startAt = if (allDay) "${date}T00:00" else "${date}T${startTime}"
                    val endAt = if (allDay) null else "${date}T${endTime}"
                    val req = Repo.ScheduleReq(title.trim(), desc.trim(), startAt, endAt, allDay)
                    if (initial == null) Repo.createSchedule(req) else Repo.updateSchedule(initial.id, req)
                    onSaved()
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("保存失败，稍后再试")
                } finally {
                    saving = false
                }
            }
        },
    ) {
        AppField("标题", title, { title = it }, placeholder = "要做什么")
        Spacer(Modifier.height(12.dp))
        DateField("日期", date, { date = it })
        Spacer(Modifier.height(12.dp))
        AppChip("全天", selected = allDay) { allDay = !allDay }
        if (!allDay) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("开始", startTime, { startTime = it }, Modifier.weight(1f))
                TimeField("结束", endTime, { endTime = it }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        AppField("备注", desc, { desc = it }, singleLine = false, minLines = 3, maxLines = 5, placeholder = "地点、要做的事…")
    }
}
