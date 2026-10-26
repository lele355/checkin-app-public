package cn.anlele.checkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.fieldShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import cn.anlele.checkin.util.Weeks

/**
 * 日期 / 时间选择组件 —— 全 App 唯一入口。
 *
 * 【为什么要有它】
 * 之前每个编辑表单都用 `AppField("日期", date, …, placeholder = "2026-10-24",
 * supporting = "格式：年-月-日")` 让用户手打日期：要记格式、要打 10 个字符、
 * 打错了保存时才报错、还不知道今天是几号。日期是选择题不是填空题，
 * 一律改成点选。
 *
 * 【形态】
 * [DatePickerSheet]  月历弹层：周一开头、今天描边、选中实心、底部三个快捷项。
 *                    点某一天即完成并关闭 —— 不设「确定」按钮，少一次判断。
 * [DateField]        表单里的一行日期，外观与 [AppField] 完全对齐（同高同圆角），
 *                    点开月历。替代原来的手打输入框。
 * [TimeField]        同上，点开已有的 [MinuteOfDaySheet] 选时分。
 *
 * 列表里内联改名之类的场景仍然用 AppField —— 那是文本，不是日期。
 */

/** 弹层顶部的小横条（与 EditSheet 同一个形态，抽出来避免两处写不一样） */
@Composable
private fun SheetGrip() {
    val p = palette
    Box(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(p.borderStrong.copy(alpha = .35f))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    initial: String? = null,
    title: String = "选择日期",
    /** 允许清空（「结束日期」这类可留空的字段用） */
    allowClear: Boolean = false,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val p = palette
    val today = Dates.today()
    val picked = remember {
        initial?.take(10)?.takeIf { it.length == 10 && it[4] == '-' } ?: today
    }
    var ym by remember { mutableStateOf(picked.take(7)) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.card,
        dragHandle = { SheetGrip() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(10.dp))

            // ---- 标题行：当前选中 + 翻月 ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthNavButton("chevron-left") { ym = Dates.addMonths(ym, -1) }
                Text(
                    Dates.monthTitle(ym),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.text,
                )
                MonthNavButton("chevron-right") { ym = Dates.addMonths(ym, 1) }
            }

            Spacer(Modifier.height(6.dp))

            // ---- 星期表头 ----
            Row(Modifier.fillMaxWidth()) {
                Dates.WEEK_HEAD.forEach { w ->
                    Text(
                        w,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = p.textWeak,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // ---- 日期格子：6 行 × 7 列，空格补位 ----
            val cells = remember(ym) { Dates.monthGridMon(ym) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        week.forEach { key ->
                            DayCell(
                                key = key,
                                selected = key != null && key == picked,
                                isToday = key != null && key == today,
                                modifier = Modifier.weight(1f),
                                onClick = { if (key != null) { onPick(key); onDismiss() } },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 快捷：三个最常用的日子，省掉翻月份 ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChip("今天", Modifier.weight(1f)) { onPick(today); onDismiss() }
                QuickChip("明天", Modifier.weight(1f)) { onPick(Dates.addDays(today, 1)); onDismiss() }
                QuickChip("下周同一天", Modifier.weight(1f)) { onPick(Dates.addDays(picked, 7)); onDismiss() }
            }
            if (allowClear) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(chipShape())
                        .clickable { onPick(""); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("清除日期", fontSize = 12.sp, color = p.textWeak)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun MonthNavButton(icon: String, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[icon], null, Modifier.size(17.dp), tint = p.textSub)
    }
}

@Composable
private fun DayCell(
    key: String?,
    selected: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = palette
    // 空格与日期同高，否则整张月历会随月份天数上下跳
    if (key == null) { Box(modifier.height(40.dp)); return }

    val shape = chipShape()
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(p.accent) else Modifier)
            .then(
                if (isToday && !selected)
                    Modifier.border(1.5.dp, p.accent, shape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Dates.dayOfMonth(key).toString(),
            fontSize = 14.sp,
            color = when {
                selected -> p.onAccent
                isToday -> p.accentStrong
                else -> p.text
            },
            fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun QuickChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = palette
    Box(
        modifier
            .height(34.dp)
            .clip(chipShape())
            .background(p.cardSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, color = p.textSub, maxLines = 1)
    }
}

// ==================== 表单里的日期 / 时间行 ====================

/**
 * 表单里的一行日期。外观与 [AppField] 逐像素对齐（同高、同圆角、同边框），
 * 只是点击后弹月历而不是弹键盘。
 */
@Composable
fun DateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 值为空时显示的占位文字 */
    placeholder: String = "选择日期",
    /** 允许清空（可留空的字段用） */
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    PickerShell(
        label = label,
        text = if (value.isBlank()) placeholder else Dates.cnDate(value),
        empty = value.isBlank(),
        icon = "calendar",
        modifier = modifier,
        onClick = { open = true },
    )
    if (open) {
        DatePickerSheet(
            initial = value,
            title = label.ifBlank { "选择日期" },
            allowClear = clearable,
            onDismiss = { open = false },
            onPick = { onChange(it) },
        )
    }
}

/** 表单里的一行时间（09:00），点开时分选择 */
@Composable
fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val minute = remember(value) {
        val m = Regex("""^(\d{1,2}):(\d{2})""").find(value)
        if (m == null) 9 * 60 else m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }
    PickerShell(
        label = label,
        text = if (value.isBlank()) "选择时间" else value,
        empty = value.isBlank(),
        icon = "bell",
        modifier = modifier,
        onClick = { open = true },
    )
    if (open) {
        MinuteOfDaySheet(
            title = label.ifBlank { "选择时间" },
            initialMinute = minute,
            onDismiss = { open = false },
            onPick = { onChange(minuteText(it)) },
        )
    }
}

/**
 * 日期 + 时间并排（日程的「开始 / 结束」这种）。两个独立入口，各一步完成，
 * 不做成一整个复合弹层 —— 用户要改的通常只是其中一个。
 */
@Composable
fun DateTimeField(
    dateLabel: String,
    date: String,
    onDateChange: (String) -> Unit,
    timeLabel: String,
    time: String,
    onTimeChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateField(dateLabel, date, onDateChange, Modifier.weight(1.15f))
        TimeField(timeLabel, time, onTimeChange, Modifier.weight(1f))
    }
}

/** 三者共用的外壳：保证日期、时间、文本三类输入在表单里长得一样 */
@Composable
private fun PickerShell(
    label: String,
    text: String,
    empty: Boolean,
    icon: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = palette
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(label, fontSize = 12.5.sp, color = p.textSub, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(fieldShape())
                .background(p.card)
                .border(
                    1.dp,
                    if (p.border == Color.Transparent) p.borderStrong.copy(alpha = .5f) else p.border,
                    fieldShape(),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                color = if (empty) p.textWeak else p.text,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            // 文字与图标基线对齐靠 Row 的 CenterVertically + 同尺寸图标，
            // 不用 padding 硬凑 —— 各主题字号不同，硬凑只对一套主题准。
            Icon(AppIcons[icon], null, Modifier.size(17.dp), tint = p.textWeak)
        }
    }
}

// ==================== 数字 / 周次选择 ====================

/**
 * 数字选择弹层（第几节课这种）。范围小的选择题，不该让用户手打数字 ——
 * 手打会出现 0、99、空，还要在保存时才报错。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberSheet(
    title: String,
    initial: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val p = palette
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.card,
        dragHandle = { SheetGrip() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(14.dp))
            range.toList().chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { n ->
                        val on = n == initial
                        Box(
                            Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(chipShape())
                                .background(if (on) p.accent else p.cardSoft)
                                .clickable { onPick(n); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                n.toString(),
                                fontSize = 15.sp,
                                color = if (on) p.onAccent else p.text,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                    // 最后一行不足 6 个时补空位，否则剩下的格子会被拉宽、与上一行不对齐
                    repeat(6 - row.size) { Box(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 表单里的一行数字（第几节课） */
@Composable
fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    PickerShell(
        label = label,
        text = value.toString(),
        empty = false,
        icon = "list",
        modifier = modifier,
        onClick = { open = true },
    )
    if (open) {
        NumberSheet(label, value, range, { open = false }) { onChange(it) }
    }
}

/**
 * 周次选择：把「1-16 周」画成一格一周，点哪周就是哪周。
 *
 * 以前这里是个文本框，占位符写着「1-16 / 单周 / 1-8,10-16」——
 * 等于要求用户先学会一套私有语法，填错了课就整学期不显示。
 * 现在随便点，生成出来的还是后端认的老格式（Weeks.format），接口不用动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPickerSheet(
    initial: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val p = palette
    var sel by remember { mutableStateOf(Weeks.parse(initial)) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.card,
        dragHandle = { SheetGrip() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("上课周次", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.weight(1f))
                Text(
                    if (sel.size == Weeks.MAX) "每周" else "${sel.size} 周",
                    fontSize = 12.sp,
                    color = p.textWeak,
                )
            }
            Spacer(Modifier.height(12.dp))

            (1..Weeks.MAX).toList().chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { w ->
                        val on = w in sel
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(chipShape())
                                .background(if (on) p.accent else p.cardSoft)
                                .clickable {
                                    sel = if (on) sel - w else sel + w
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                w.toString(),
                                fontSize = 14.sp,
                                color = if (on) p.onAccent else p.textSub,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                    repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChip("全选", Modifier.weight(1f)) { sel = (1..Weeks.MAX).toSet() }
                QuickChip("单周", Modifier.weight(1f)) { sel = (1..Weeks.MAX).filter { it % 2 == 1 }.toSet() }
                QuickChip("双周", Modifier.weight(1f)) { sel = (1..Weeks.MAX).filter { it % 2 == 0 }.toSet() }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("取消", Modifier.weight(1f)) { onDismiss() }
                PrimaryButton("确定", Modifier.weight(1f)) {
                    onPick(Weeks.format(sel))
                    onDismiss()
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

/** 表单里的一行周次 */
@Composable
fun WeekField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    PickerShell(
        label = label,
        text = Weeks.display(value),
        empty = false,
        icon = "calendar",
        modifier = modifier,
        onClick = { open = true },
    )
    if (open) {
        WeekPickerSheet(value, { open = false }) { onChange(it) }
    }
}
