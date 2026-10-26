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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.components.WeekField
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

private val WEEK_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 课程类别配色 —— 与网页图例一致：必选紫、院选蓝、校选绿、其它浅紫 */
private data class CourseColors(val bg: Color, val fg: Color)
private fun colorsOf(category: String): CourseColors = when (category) {
    "required" -> CourseColors(Color(0xFFEDE7F6), Color(0xFF5E35B1))
    "college" -> CourseColors(Color(0xFFE3F2FD), Color(0xFF1565C0))
    "school" -> CourseColors(Color(0xFFE8F5E9), Color(0xFF2E7D32))
    else -> CourseColors(Color(0xFFF3E5F5), Color(0xFF7B1FA2))
}

/**
 * 课程表 —— 与网页手机端一致：
 * 页头（添加课程）→ 周切换（‹ 第 N 周 日期区间 ›）+ 学期起始日 + 保存 + 节次时间
 * → 图例 → 节次 × 星期 表格（左右横滑、跨节次整块）→ 右下角浮动「+」。
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
            onSemStart = { semStart = it },
            onPrevWeek = { if (week > 1) week-- },
            onNextWeek = { week++ },
            onSaveSemStart = {
                scope.launch {
                    try {
                        Repo.saveCourseSettings(Repo.CourseSettingsReq(semesterStart = semStart))
                        vm.toast("学期起始日已保存")
                        load()
                    } catch (e: Exception) {
                        vm.toast("保存失败")
                    }
                }
            },
            onSettings = { settingsOpen = true },
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
}

@Composable
private fun LazyColumnish(
    d: CoursesPageDto?,
    week: Int,
    loading: Boolean,
    semStart: String,
    onSemStart: (String) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSaveSemStart: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CourseDto) -> Unit,
    vm: AppVm,
) {
    val p = palette
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StaggerIn(0) {
            PageHeadCard(
                title = "课程表",
                actions = { HeadButton("添加课程") { onAdd() } },
            )
        }

        // ---------- 周切换 + 学期起始 ----------
        StaggerIn(1) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TinyAction("‹ 上一周", null) { onPrevWeek() }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("第 $week 周", fontSize = 15.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                            val startOffset = ((week - 1) * 7)
                            val start = if (semStart.isBlank()) "" else Dates.addDays(semStart, startOffset)
                            val end = if (start.isBlank()) "" else Dates.addDays(start, 6)
                            Text(
                                if (start.isBlank()) "先设学期起始日" else "${Dates.fmt(start, "MM-dd")} ~ ${Dates.fmt(end, "MM-dd")}",
                                fontSize = 11.sp, color = p.textWeak,
                            )
                        }
                        TinyAction("下一周 ›", null) { onNextWeek() }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("学期起始（第 1 周的周一）", fontSize = 11.5.sp, color = p.textWeak, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            AppField("", semStart, onSemStart, placeholder = "2026-09-01")
                        }
                        Spacer(Modifier.width(8.dp))
                        HeadButton("保存", icon = null) { onSaveSemStart() }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clickable { onSettings() }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons["settings-2"], null, Modifier.size(14.dp), tint = p.textSub)
                        Spacer(Modifier.width(6.dp))
                        Text("节次时间（${d?.periods?.size ?: 12} 节）", fontSize = 12.5.sp, color = p.textSub)
                        Spacer(Modifier.weight(1f))
                        Icon(AppIcons["chevron-right"], null, Modifier.size(13.dp), tint = p.textWeak)
                    }
                }
            }
        }

        if (d == null) {
            if (loading) Text("正在加载…", fontSize = 12.5.sp, color = p.textWeak)
            return@Column
        }

        // ---------- 图例 ----------
        StaggerIn(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                listOf(
                    "required" to "必修课",
                    "college" to "院级选修课",
                    "school" to "校级选修课",
                    "other" to "其它",
                ).forEach { (k, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(colorsOf(k).fg, chipShape()))
                        Spacer(Modifier.width(4.dp))
                        Text(label, fontSize = 10.5.sp, color = p.textSub, maxLines = 1)
                    }
                }
            }
        }

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
            val cellH = 52.dp
            val colW = 76.dp
            val gutterW = 40.dp
            val scroll = rememberScrollState()
            val semesterStart = if (semStart.isBlank()) d.semesterStart else semStart
            val weekStart = if (semesterStart.isBlank()) "" else Dates.addDays(semesterStart, (week - 1) * 7)

            StaggerIn(3) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 0.dp, vertical = 12.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(gutterW))
                            Row(Modifier.horizontalScroll(scroll)) {
                                WEEK_LABELS.forEachIndexed { i, label ->
                                    val dateLabel = if (weekStart.isBlank()) "" else Dates.fmt(Dates.addDays(weekStart, i), "MM-dd")
                                    Column(
                                        Modifier.width(colW).padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            label, fontSize = 11.5.sp,
                                            color = if (d.todayWeekday == i + 1 && week == d.currentWeek) p.accent else p.textSub,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        if (dateLabel.isNotBlank())
                                            Text(dateLabel, fontSize = 9.5.sp, color = p.textWeak)
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
                                        Text("${idx + 1}", fontSize = 11.sp, color = p.textWeak)
                                        Text(slot.start, fontSize = 8.sp, color = p.textWeak)
                                        Text(slot.end, fontSize = 8.sp, color = p.textWeak)
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
                                                    val col = colorsOf(c.category)
                                                    Box(
                                                        Modifier
                                                            .offset(y = cellH * (c.startPeriod - 1))
                                                            .width(colW)
                                                            .height(cellH * span)
                                                            .padding(1.dp)
                                                            .background(col.bg, chipShape())
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "← 左右滑动看完整一周 →",
                            fontSize = 10.5.sp,
                            color = p.textWeak,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
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
    var saving by remember { mutableStateOf(false) }

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
                    val req = Repo.CourseReq(name.trim(), teacher.trim(), location.trim(), weekday, start, end, weeks.trim(), category)
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
