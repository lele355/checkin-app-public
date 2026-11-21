package cn.anlele.checkin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.CategoryAmountDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.StatsDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.GrowBar
import cn.anlele.checkin.ui.components.LoadingBlock
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.ViewSwitch
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/** 热力图的绿：网页用的是固定绿色系（不跟主题走），深浅表示当天打卡次数 */
private val HEAT = Color(0xFF3E9E5B)

/**
 * 统计 —— 三个视图：概览（计数/热力图/排行）、趋势（月度收支、近 30 天）、分布（支出分类饼图/条形）。
 * 每个模块可折叠，折叠状态存本地 prefs（跨启动记忆）。
 */
@Composable
fun StatsScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<StatsDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var view by remember { mutableStateOf("概览") }

    val ctx = LocalContext.current
    val uiPrefs = remember(ctx) { ctx.getSharedPreferences("stats_ui", android.content.Context.MODE_PRIVATE) }
    val collapsed = remember { mutableStateOf(setOf<String>()) }

    fun toggleFold(id: String) {
        collapsed.value =
            if (id in collapsed.value) collapsed.value - id else collapsed.value + id
        uiPrefs.edit().putString("fold", collapsed.value.joinToString(",")).apply()
    }

    LaunchedEffect(Unit) {
        collapsed.value = (uiPrefs.getString("fold", "") ?: "").split(',').filter { it.isNotBlank() }.toSet()
    }

    suspend fun load() {
        loading = true
        try {
            d = Repo.stats()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val data = d
    if (data == null) {
        if (loading) LoadingBlock("正在统计…") else SiteEmpty("📊", "暂无统计数据。")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeadCard(
                title = "统计",
                subRow = {
                    ViewSwitch(
                        options = listOf("overview" to "概览", "trend" to "趋势", "dist" to "分布"),
                        selected = view,
                        onSelect = { view = it },
                        fullWidth = true,
                    )
                },
            )
        }

        when (view) {
            "趋势" -> {
                // ---------- 月度收支 ----------
                item {
                    StaggerIn(0) {
                        StatsModule("trend-month", "📈 月度收支（近 6 个月）", collapsed.value, ::toggleFold) {
                            val max = maxOf(data.maxMonthOut, data.maxMonthIn).coerceAtLeast(1.0)
                            Row(
                                Modifier.fillMaxWidth().height(140.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                data.months.forEachIndexed { i, m ->
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(
                                            Modifier.fillMaxWidth().height(110.dp),
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Bar((m.`out` / max).toFloat(), OUT_RED, Modifier.weight(1f), i * 40)
                                            Bar((m.`in` / max).toFloat(), IN_GREEN, Modifier.weight(1f), i * 40 + 20)
                                        }
                                        Spacer(Modifier.height(5.dp))
                                        Text(m.label, fontSize = 10.sp, color = p.textSub)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LegendDot(OUT_RED, "支出")
                                LegendDot(IN_GREEN, "收入")
                            }
                        }
                    }
                }
                // ---------- 近 30 天每日收支 ----------
                item {
                    StaggerIn(1) {
                        StatsModule("trend-daily", "🗓️ 近 30 天每日支出", collapsed.value, ::toggleFold) {
                            if (data.daily.isEmpty()) {
                                Text("还没有记账数据", fontSize = 12.sp, color = p.textWeak)
                            } else {
                                val max = (data.daily.maxOfOrNull { it.`out` } ?: 0.0).coerceAtLeast(1.0)
                                Row(
                                    Modifier.fillMaxWidth().height(96.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    data.daily.forEachIndexed { i, day ->
                                        Bar(
                                            (day.`out` / max).toFloat(), OUT_RED.copy(alpha = .8f),
                                            Modifier.weight(1f), if (i % 5 == 0) i * 8 else 0,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(Dates.expenseWhen(data.daily.first().date).takeLast(5), fontSize = 9.5.sp, color = p.textWeak)
                                    Text("今天", fontSize = 9.5.sp, color = p.textWeak)
                                }
                            }
                        }
                    }
                }
            }

            "分布" -> {
                // ---------- 支出分类饼图 ----------
                item {
                    StaggerIn(0) {
                        StatsModule("dist-pie", "🥧 本月支出分类", collapsed.value, ::toggleFold) {
                            if (data.byCategory.isEmpty()) {
                                Text("本月还没有支出", fontSize = 12.sp, color = p.textWeak)
                            } else {
                                DonutChart(data.byCategory)
                            }
                        }
                    }
                }
                // ---------- 分类条形 ----------
                item {
                    StaggerIn(1) {
                        StatsModule("dist-bar", "📊 分类排行（本月）", collapsed.value, ::toggleFold) {
                            if (data.byCategory.isEmpty()) {
                                Text("本月还没有支出", fontSize = 12.sp, color = p.textWeak)
                            } else {
                                val max = data.byCategory.first().amount.coerceAtLeast(0.01)
                                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                    data.byCategory.forEach { c ->
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(c.name, fontSize = 12.5.sp, color = p.text, modifier = Modifier.weight(1f))
                                                Text("¥${Dates.money(c.amount)}", fontSize = 11.5.sp, color = p.textSub)
                                            }
                                            Spacer(Modifier.height(5.dp))
                                            GrowBar((c.amount / max).toFloat())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // ---------- 概览：计数 ----------
                item {
                    StaggerIn(0) {
                        StatsModule("ov-counts", "🔢 计数总览", collapsed.value, ::toggleFold) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    AppChip("日程 ${data.counts.schedules}")
                                    AppChip("备忘 ${data.counts.memos}")
                                    AppChip("课程 ${data.counts.courses}")
                                    AppChip("留言 ${data.counts.board}")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    AppChip("打卡 ${data.counts.checkins} 次")
                                    AppChip("记账 ${data.counts.expenses} 笔")
                                    AppChip("纪念日 ${data.counts.anniversaries}")
                                }
                            }
                        }
                    }
                }
                // ---------- 热力图 ----------
                item {
                    StaggerIn(1) {
                        StatsModule("ov-heat", "🔥 打卡热力图（近 90 天）", collapsed.value, ::toggleFold) {
                            val max = (data.heat.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
                            val weeks = data.heat.chunked(7)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                weeks.forEach { week ->
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        week.forEach { day ->
                                            val ratio = day.count.toFloat() / max
                                            val alpha = when {
                                                day.count == 0 -> 0.10f
                                                ratio <= 0.34f -> 0.35f
                                                ratio <= 0.67f -> 0.62f
                                                else -> 1f
                                            }
                                            Box(
                                                Modifier
                                                    .size(12.dp)
                                                    .background(HEAT.copy(alpha = alpha), chipShape())
                                            )
                                        }
                                        // 不足 7 天的最后一周补空格，保持方块对齐
                                        repeat(7 - week.size) {
                                            Box(Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("少", fontSize = 10.5.sp, color = p.textWeak)
                                Spacer(Modifier.width(5.dp))
                                listOf(0.10f, 0.35f, 0.62f, 1f).forEach { a ->
                                    Box(
                                        Modifier
                                            .size(11.dp)
                                            .background(HEAT.copy(alpha = a), chipShape())
                                    )
                                    Spacer(Modifier.width(3.dp))
                                }
                                Spacer(Modifier.width(3.dp))
                                Text("多", fontSize = 10.5.sp, color = p.textWeak)
                            }
                        }
                    }
                }
                // ---------- 打卡排行 ----------
                if (data.topHabits.isNotEmpty()) {
                    item {
                        StaggerIn(2) {
                            StatsModule("ov-habits", "🏆 打卡排行", collapsed.value, ::toggleFold) {
                                val top = (data.topHabits.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
                                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                    data.topHabits.forEach { h ->
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(h.icon, fontSize = 13.sp)
                                                Spacer(Modifier.width(7.dp))
                                                Text(
                                                    h.name, fontSize = 13.sp, color = p.text,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                )
                                                Text("${h.total} 次", fontSize = 12.sp, color = p.textSub)
                                            }
                                            Spacer(Modifier.height(5.dp))
                                            GrowBar(h.total.toFloat() / top)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

/** 记账红绿：与记账页一致（支出红、收入绿） */
private val OUT_RED = Color(0xFFC0392B)
private val IN_GREEN = Color(0xFF0F6E56)

/** 可折叠模块外壳：标题 + 折叠箭头，内容收起带动画，状态存 prefs */
@Composable
private fun StatsModule(
    id: String,
    title: String,
    collapsed: Set<String>,
    onToggle: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val p = palette
    val isCollapsed = id in collapsed
    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title, fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val rot by animateFloatAsState(if (isCollapsed) -90f else 0f, label = "fold-caret")
                Icon(
                    AppIcons["chevron-down"], if (isCollapsed) "展开" else "收起",
                    Modifier.size(15.dp).rotate(rot), tint = p.textWeak,
                )
            }
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = expandVertically(tween(240)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
                Column(Modifier.padding(top = 11.dp)) { content() }
            }
        }
    }
}

/** 双色小圆点图例 */
@Composable
private fun LegendDot(color: Color, label: String) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = p.textSub)
    }
}

/** 单色柱（趋势视图用）：从底长出，带进场延迟 */
@Composable
private fun Bar(ratio: Float, color: Color, modifier: Modifier = Modifier, delayMs: Int = 0) {
    var run by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        run = true
    }
    val s by animateFloatAsState(if (run) 1f else 0f, tween(550), label = "bar")
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((96 * ratio.coerceIn(0f, 1f)).dp.coerceAtLeast(2.dp))
                .clip(RoundedCornerShape(4.dp))
                .graphicsLayer {
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .background(color)
        )
    }
}

/**
 * 支出分类环形图（分布视图）：扇区按金额占比，点扇区高亮（再点恢复），
 * 圈心显示总支出。扇区色从主题强调色色相散开，全主题可辨。
 */
@Composable
private fun DonutChart(cats: List<CategoryAmountDto>) {
    val p = palette
    val total = cats.sumOf { it.amount }.coerceAtLeast(0.01)
    val colors = remember(cats.size, p.accent, p.isDark) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(p.accent.toArgb(), hsv)
        val baseHue = hsv[0]
        val sat = if (p.isDark) 0.55f else 0.52f
        val value = if (p.isDark) 0.92f else 0.72f
        cats.indices.map { i -> Color.hsv((baseHue + i * 137.508f) % 360f, sat, value) }
    }
    var highlight by remember { mutableStateOf<String?>(null) }

    Column {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .size(158.dp)
                        .pointerInput(cats) {
                            detectTapGestures { pos ->
                                val dx = pos.x - size.width / 2f
                                val dy = pos.y - size.height / 2f
                                var deg = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat() + 90f
                                if (deg < 0f) deg += 360f
                                var acc = 0f
                                for (c in cats) {
                                    acc += (c.amount / total * 360f).toFloat()
                                    if (deg <= acc) {
                                        highlight = if (highlight == c.name) null else c.name
                                        return@detectTapGestures
                                    }
                                }
                            }
                        },
                ) {
                    val ring = 26.dp.toPx()
                    val side = size.minDimension - ring
                    val topLeft = androidx.compose.ui.geometry.Offset((size.width - side) / 2f, (size.height - side) / 2f)
                    val arcSize = androidx.compose.ui.geometry.Size(side, side)
                    drawArc(p.cardSoft, 0f, 360f, useCenter = false, style = Stroke(ring), topLeft = topLeft, size = arcSize)
                    var start = -90f
                    cats.forEachIndexed { i, c ->
                        val full = (c.amount / total * 360f).toFloat()
                        drawArc(
                            colors[i].copy(alpha = if (highlight == null || highlight == c.name) 1f else .22f),
                            startAngle = start,
                            sweepAngle = full,
                            useCenter = false,
                            style = Stroke(ring),
                            topLeft = topLeft,
                            size = arcSize,
                        )
                        start += full
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val one = highlight?.let { h -> cats.firstOrNull { it.name == h } }
                    if (one != null) {
                        Text("${(one.amount / total * 100).toInt()}%", fontSize = 19.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                        Text("¥${Dates.money(one.amount)}", fontSize = 10.5.sp, color = p.textWeak)
                    } else {
                        Text("总支出", fontSize = 10.5.sp, color = p.textWeak)
                        Text("¥${Dates.money(total)}", fontSize = 15.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        // 图例两列网格
        cats.withIndex().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEachIndexed { j, (i, c) ->
                    val pct = (c.amount / total * 100).toInt()
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(chipShape())
                            .clickable { highlight = if (highlight == c.name) null else c.name }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i]))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            c.name, fontSize = 11.5.sp,
                            color = if (highlight == c.name) p.text else p.textSub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "¥${Dates.money(c.amount)} $pct%",
                            fontSize = 10.5.sp,
                            color = if (highlight == c.name) p.text else p.textWeak,
                            maxLines = 1,
                        )
                    }
                    if (j == 0) Spacer(Modifier.width(6.dp))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
