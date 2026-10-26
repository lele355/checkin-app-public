package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.CourseDto
import cn.anlele.checkin.data.CoursesPageDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.DateField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.GroupHead
import cn.anlele.checkin.ui.components.HeadButton
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.NumberField
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.components.SemesterSheet
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.components.WeekField
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.CoursePalette
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

private val WEEK_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private data class CourseColors(val bg: Color, val fg: Color)

/** 课程配色 —— 颜色跟着「哪门课」走，不再跟着类别走（与网页端同一套色板） */
private fun colorsOf(c: CourseDto, isDark: Boolean): CourseColors {
    val key = CoursePalette.of(c)
    return CourseColors(CoursePalette.bg(key, isDark), CoursePalette.fg(key, isDark))
}

/**
 * 课程表 —— 与网页手机端一致：
 * 顶部工具条（‹ 第 N 周 日期区间 › + 设置 + 添加）→ 节次 × 星期 表格（左右横滑、跨节次整块）。
 * 学期起始日 / 节次时间 / 非本周显示都收进「课表设置」二级弹层，课表页不再摊表单。
 */
@Composable
fun CoursesScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<CoursesPageDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CourseDto?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var semesterOpen by remember { mutableStateOf(false) }
    var week by remember { mutableStateOf(0) }
    var semStart by remember { mutableStateOf("") }

    suspend fun load() {
        loading = true
        try {
            val d = Repo.courses()
            data = d
            if (week == 0) week = d.currentWeek
            semStart = d.semesterStart
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val d = data
    Box(Modifier.fillMaxSize()) {
        LazyColumnish(
            d = d,
            week = week,
            loading = loading,
            semStart = semStart,
            onPrevWeek = { if (week > 1) week-- },
            onNextWeek = { week++ },
            onSettings = { settingsOpen = true },
            onSemester = { semesterOpen = true },
            onAdd = { editing = null; editorOpen = true },
            onEdit = { editing = it; editorOpen = true },
            vm = vm,
        )

        // 右下角浮动「+」（网页 .cal-fab / 课表的 FAB 同款）
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 20.dp)
                .size(54.dp)
                .background(p.accent, CircleShape)
                .clickable { editing = null; editorOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons["plus"], "添加课程", Modifier.size(24.dp), tint = p.onAccent)
        }
    }

    if (editorOpen) {
        CourseEditor(vm, editing, data?.periods?.size ?: 12, onClose = { editorOpen = false }, onSaved = {
            editorOpen = false
            scope.launch { load() }
        })
    }
    if (settingsOpen) {
        CourseSettingsSheet(vm, d, onClose = { settingsOpen = false }, onSaved = {
            settingsOpen = false
            scope.launch { load() }
        })
    }
    if (semesterOpen) {
        // 切换/新建/删除之后整个课表要重来：周次、课程、学期名全都变了
        SemesterSheet(
            vm = vm,
            currentId = d?.semesterId ?: 0,
            onClose = { semesterOpen = false },
            onChanged = { scope.launch { load() } },
        )
    }
}

@Composable
private fun LazyColumnish(
    d: CoursesPageDto?,
    week: Int,
    loading: Boolean,
    semStart: String,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSettings: () -> Unit,
    onSemester: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CourseDto) -> Unit,
    vm: AppVm,
) {
    val p = palette
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 顶部工具条：‹ 第 N 周 › + 设置 + 添加 ----------
        // 学期起始日、节次时间原先直接摊在这张卡里，一进课程表先看到两行表单。
        // 那些是一学期改一次的东西，收进「课表设置」二级页；首屏只留换周和两个动作。
        StaggerIn(1) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WeekArrow("‹", "上一周") { onPrevWeek() }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("第 $week 周", fontSize = 15.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                        val startOffset = ((week - 1) * 7)
                        val start = if (semStart.isBlank()) "" else Dates.addDays(semStart, startOffset)
                        val end = if (start.isBlank()) "" else Dates.addDays(start, 6)
                        Text(
                            if (start.isBlank()) "在设置里填学期起始日" else "${Dates.fmt(start, "MM-dd")} ～ ${Dates.fmt(end, "MM-dd")}",
                            fontSize = 11.sp, color = p.textWeak,
                        )
                        // 学期名可点：切换 / 新建 / 删除都在弹层里
                        Text(
                            (d?.semesterName?.ifBlank { null } ?: "选择学期") + " ›",
                            fontSize = 11.sp, color = p.accentStrong,
                            modifier = Modifier.clickable { onSemester() },
                        )
                    }
                    WeekArrow("›", "下一周") { onNextWeek() }
                    Spacer(Modifier.width(6.dp))
                    CircleIconBtn("settings-2", "课表设置") { onSettings() }
                }
            }
        }

        if (d == null) {
            if (loading) Text("正在加载…", fontSize = 12.5.sp, color = p.textWeak)
            return@Column
        }

        // 图例已删除：颜色现在区分的是「哪门课」，一门课一个色，
        // 列一排色块等于把课程名再抄一遍，白白占掉一屏里最贵的一行。

        // ---------- 表格 ----------
        if (d.items.isEmpty()) {
            StaggerIn(3) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                    SiteEmpty("📚", "还没有课程，点右下角「+」录一门。") {
                        PrimaryButton("添加课程", icon = "plus") { onAdd() }
                    }
                }
            }
        } else {
            val periods = d.periods
            val cellH = 54.dp
            val colW = 76.dp
            val gutterW = 40.dp
            val scroll = rememberScrollState()
            val semesterStart = if (semStart.isBlank()) d.semesterStart else semStart
            val weekStart = if (semesterStart.isBlank()) "" else Dates.addDays(semesterStart, (week - 1) * 7)

            // 打开就滚到「今天」那列，并在左边留一列（通常是昨天）。
            // 手机上 7 列是横滑的，默认停在周一 —— 周三有课也要先往右划两下，
            // 而「今天上什么」才是打开课表最想看的。
            val density = androidx.compose.ui.platform.LocalDensity.current
            LaunchedEffect(week, d.todayWeekday) {
                if (week != d.currentWeek) { scroll.scrollTo(0); return@LaunchedEffect }
                val colPx = with(density) { colW.toPx() }
                val keepLeft = (d.todayWeekday - 2).coerceAtLeast(0)
                scroll.animateScrollTo((colPx * keepLeft).toInt())
            }

            StaggerIn(3) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 0.dp, vertical = 12.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(gutterW))
                            Row(Modifier.horizontalScroll(scroll)) {
                                WEEK_LABELS.forEachIndexed { i, label ->
                                    val dateLabel = if (weekStart.isBlank()) "" else Dates.fmt(Dates.addDays(weekStart, i), "MM-dd")
                                    val isToday = week == d.currentWeek && d.todayWeekday == i + 1
                                    Column(
                                        Modifier
                                            .width(colW)
                                            .padding(vertical = 4.dp)
                                            .then(if (isToday) Modifier.background(p.accentSoft, chipShape()) else Modifier),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            label, fontSize = 11.5.sp,
                                            color = if (isToday) p.text else p.textSub,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        )
                                        if (dateLabel.isNotBlank())
                                            Text(
                                                dateLabel,
                                                fontSize = 9.5.sp,
                                                // 今天用「反白小胶囊」：只靠淡底色在深色主题下几乎看不出
                                                // （强调色本身可能就是深墨色，和正文同色）。
                                                color = if (isToday) p.onAccent else p.textWeak,
                                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = if (isToday)
                                                    Modifier
                                                        .background(p.accent, chipShape())
                                                        .padding(horizontal = 5.dp)
                                                else Modifier,
                                            )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth()) {
                            // 左侧节次
                            Column(Modifier.width(gutterW)) {
                                periods.forEachIndexed { idx, slot ->
                                    Column(
                                        Modifier.height(cellH).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        // lineHeight 必须钉死：默认行高按中文字体算接近 1.6 倍，
                                        // 三行（节号 + 起 + 止）在 52dp 的格子里放不下，结束时间会被裁掉。
                                        Text("${idx + 1}", fontSize = 10.5.sp, lineHeight = 12.sp, color = p.textSub,
                                            fontWeight = FontWeight.Medium)
                                        Text(slot.start, fontSize = 7.5.sp, lineHeight = 9.sp, color = p.textWeak)
                                        Text(slot.end, fontSize = 7.5.sp, lineHeight = 9.sp, color = p.textWeak)
                                    }
                                }
                            }
                            Row(Modifier.horizontalScroll(scroll)) {
                                (1..7).forEach { weekday ->
                                    Box(Modifier.width(colW).height(cellH * periods.size)) {
                                        // 底纹
                                        periods.forEachIndexed { idx, _ ->
                                            Box(
                                                Modifier
                                                    .offset(y = cellH * idx)
                                                    .width(colW)
                                                    .height(cellH)
                                                    .padding(1.dp)
                                                    .background(p.cardSoft.copy(alpha = .30f), chipShape())
                                            )
                                        }
                                        // 课程块
                                        d.items.filter { it.weekday == weekday && weeksInclude(it.weeksText, week) }
                                            .forEach { c ->
                                                val span = (c.endPeriod - c.startPeriod + 1).coerceAtLeast(1)
                                                if (c.startPeriod in 1..periods.size) {
                                                    val col = colorsOf(c, p.isDark)
                                                    Box(
                                                        Modifier
                                                            .offset(y = cellH * (c.startPeriod - 1))
                                                            .width(colW)
                                                            .height(cellH * span)
                                                            .padding(1.dp)
                                                            // 半透明底 + 同色细边：底色压薄一点，
                                                            // 「今天列」的淡底才能透出来（实色会把整列高亮盖掉）
                                                            .background(col.bg, chipShape())
                                                            .border(1.dp, col.fg.copy(alpha = .22f), chipShape())
                                                            .clickable { onEdit(c) }
                                                            .padding(4.dp),
                                                    ) {
                                                        Column {
                                                            Text(
                                                                c.name,
                                                                fontSize = 10.sp,
                                                                color = col.fg,
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 3,
                                                                overflow = TextOverflow.Ellipsis,
                                                                lineHeight = 13.sp,
                                                            )
                                                            if (c.location.isNotBlank())
                                                                Text(
                                                                    c.location,
                                                                    fontSize = 8.5.sp,
                                                                    color = col.fg.copy(alpha = .75f),
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    lineHeight = 11.sp,
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
                }
            }
        }
    }
}

/** 周切换箭头：圆形底 + 大号 ‹ ›，比文字按钮更像个可点的开关 */
@Composable
private fun WeekArrow(glyph: String, desc: String, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(p.cardSoft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 20.sp, color = p.textSub)
    }
}

/** 工具条右侧的圆形图标按钮；primary = 主色实心（添加） */
@Composable
private fun CircleIconBtn(icon: String, desc: String, primary: Boolean = false, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (primary) p.accent else p.cardSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons[icon], desc, Modifier.size(17.dp),
            tint = if (primary) p.onAccent else p.textSub,
        )
    }
}

/** 周次文本解析 —— 与后端 Services/Weeks.cs 同规则："1-16" / "单周" / "双周" / "1-8,10-16" */
internal fun weeksInclude(text: String?, week: Int): Boolean {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return true
    t.split(',', '，', '、').forEach { raw ->
        var seg = raw.trim().removePrefix("第").removeSuffix("周").trim()
        if (seg.isEmpty()) return@forEach
        if (seg == "单周" || seg == "单") { if (week % 2 == 1) return true; return@forEach }
        if (seg == "双周" || seg == "双") { if (week % 2 == 0) return true; return@forEach }
        val range = Regex("^(\\d+)\\s*[-~～—]\\s*(\\d+)$").find(seg)
        if (range != null) {
            val a = range.groupValues[1].toIntOrNull() ?: return@forEach
            val b = range.groupValues[2].toIntOrNull() ?: return@forEach
            if (week in minOf(a, b)..maxOf(a, b)) return true
            return@forEach
        }
        val single = seg.toIntOrNull()
        if (single != null && single == week) return true
    }
    return false
}

@Composable
private fun CourseEditor(vm: AppVm, initial: CourseDto?, periodCount: Int, onClose: () -> Unit, onSaved: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var weekday by remember { mutableStateOf(initial?.weekday ?: Dates.weekdayIndex(Dates.today())) }
    var start by remember { mutableStateOf(initial?.startPeriod ?: 1) }
    var end by remember { mutableStateOf(initial?.endPeriod ?: 2) }
    var weeks by remember { mutableStateOf(initial?.weeksText ?: "1-16") }
    var category by remember { mutableStateOf(initial?.category ?: "required") }
    // 空串 = 自动（按课程名分配）。编辑已有课程时回显它落库的色，没设过就还是自动。
    var color by remember { mutableStateOf(CoursePalette.normalize(initial?.color)) }
    var saving by remember { mutableStateOf(false) }

    // 自动色的预览：跟随当前输入的名字实时变，用户能看到「改名后颜色也跟着换」
    val autoKey = CoursePalette.defaultFor(name)

    EditSheet(
        title = if (initial == null) "添加课程" else "编辑课程",
        onClose = onClose,
        saving = saving,
        destructive = if (initial == null) null else ({
            SheetDelete {
                scope.launch { runCatching { Repo.deleteCourse(initial.id) }; onSaved() }
            }
        }),
        onSave = {
            if (name.isBlank()) { vm.toast("课程名不能为空"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    val req = Repo.CourseReq(
                        name.trim(), teacher.trim(), location.trim(), weekday, start, end,
                        weeks.trim(), category, color,
                    )
                    if (initial == null) Repo.createCourse(req) else Repo.updateCourse(initial.id, req)
                    onSaved()
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("保存失败")
                } finally {
                    saving = false
                }
            }
        },
    ) {
        AppField("课程名", name, { name = it }, placeholder = "机械CAD")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField("老师", teacher, { teacher = it }, modifier = Modifier.weight(1f), placeholder = "可不填")
            AppField("地点", location, { location = it }, modifier = Modifier.weight(1f), placeholder = "教1-201")
        }
        Spacer(Modifier.height(14.dp))
        Text("星期", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WEEK_LABELS.forEachIndexed { i, label ->
                AppChip(label.removePrefix("周"), selected = weekday == i + 1) { weekday = i + 1 }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("开始节", start, 1..periodCount.coerceAtLeast(1), { start = it }, Modifier.weight(1f))
            NumberField("结束节", end, 1..periodCount.coerceAtLeast(1), { end = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        WeekField("周次", weeks, { weeks = it })
        Spacer(Modifier.height(14.dp))
        Text("类别", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("required" to "必修课", "college" to "院级选修", "school" to "校级选修", "other" to "其它")
                .forEach { (k, label) ->
                    AppChip(label, selected = category == k) { category = k }
                }
        }

        // ---------- 颜色 ----------
        // 颜色现在区分的是「哪门课」，跟类别无关（类别只作说明）。
        // 默认「自动」：同名课程自动同色，所以一门课上三次不用一个个去挑。
        Spacer(Modifier.height(14.dp))
        Text("颜色", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 自动
            SwatchDot(
                key = null,
                autoPreview = autoKey,
                selected = color.isEmpty(),
                onClick = { color = "" },
            )
            CoursePalette.Keys.forEach { k ->
                SwatchDot(key = k, autoPreview = null, selected = color == k, onClick = { color = k })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (color.isEmpty()) "自动：同名课程同色（「高等数学（上）」和「高等数学 上」算同一门）"
            else "已固定为「${CoursePalette.Labels[color] ?: color}」，同一门课的所有小节都会跟着变",
            fontSize = 11.sp, color = p.textWeak, lineHeight = 17.sp,
        )
    }
}

/**
 * 色板圆点。
 *
 * `key == null` 是「自动」：中间画一小片双色斜纹，暗示「按名字算出来的」。
 * 选中态用外圈描边而不是打勾 —— 打勾会挡住色块本身，色板上打勾反而看不清颜色。
 */
@Composable
private fun SwatchDot(key: String?, autoPreview: String?, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    val shown = key ?: autoPreview ?: CoursePalette.Keys[0]
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (key == null) p.cardSoft else CoursePalette.bg(shown, p.isDark))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CoursePalette.fg(shown, p.isDark) else p.borderStrong.copy(alpha = .35f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (key == null) {
            // 自动：半透明色点 + 虚线感（用一圈浅描边近似），表示「由名字决定」
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(CoursePalette.fg(shown, p.isDark).copy(alpha = .45f)),
            )
        } else {
            Box(Modifier.size(18.dp).clip(CircleShape).background(CoursePalette.fg(shown, p.isDark)))
        }
    }
}

@Composable
private fun CourseSettingsSheet(vm: AppVm, data: CoursesPageDto?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var startDate by remember { mutableStateOf(data?.semesterStart ?: Dates.today()) }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = "学期与节次",
        onClose = onClose,
        saving = saving,
        onSave = {
            saving = true
            scope.launch {
                try {
                    Repo.saveCourseSettings(Repo.CourseSettingsReq(semesterStart = startDate))
                    onSaved()
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("保存失败")
                } finally {
                    saving = false
                }
            }
        },
    ) {
        Text(
            "当前 ${data?.periods?.size ?: 12} 节",
            fontSize = 12.5.sp,
            color = palette.textSub,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(12.dp))
        DateField("学期起始日", startDate, { picked ->
            // 学期起始只认周一：随手选哪天都行，这里自动吸附到所在周的周一
            val mon = Dates.mondayOf(picked)
            startDate = mon
            if (mon != picked) vm.toast("已对齐到该周周一")
        })
        Spacer(Modifier.height(14.dp))
        GroupHead("节次时间")
        val periods = data?.periods.orEmpty()
        periods.forEachIndexed { i, slot ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("第 ${i + 1} 节", fontSize = 12.5.sp, color = palette.textSub, modifier = Modifier.width(64.dp))
                Text("${slot.start} – ${slot.end}", fontSize = 12.5.sp, color = palette.text)
            }
        }
    }
}
