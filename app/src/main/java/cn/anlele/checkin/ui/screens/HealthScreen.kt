package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.HealthBindingDto
import cn.anlele.checkin.data.HealthDailyItem
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.pressable
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 健康主页：模块卡（步数/睡眠/心率/血氧/压力/体重）→ 点进二级详情。
 * 数据口径：日汇总 + 小时聚合；原始逐点留 bridge。
 */
@Composable
fun HealthScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var bindings by remember { mutableStateOf<List<HealthBindingDto>>(emptyList()) }
    var daily by remember { mutableStateOf<List<HealthDailyItem>>(emptyList()) }
    var filter by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showBind by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    suspend fun load() {
        try {
            bindings = Repo.healthBindings()
            val to = LocalDate.now()
            val from = to.minusDays(13)   // 详情页看两周
            daily = Repo.healthDaily(from.toString(), to.toString()).items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(refreshTick) {
        load()
        if (refreshTick > 0) {   // 绑定刚成功：首采在后台跑，等一会再刷一次
            kotlinx.coroutines.delay(15000)
            load()
        }
    }

    val rows = remember(daily, filter) {
        if (filter == 0) daily else daily.filter { it.bindingId == filter }
    }
    val byDate = remember(rows) { rows.groupBy { it.date } }
    val today = LocalDate.now().toString()

    if (showBind) {
        HealthBindSheet(
            bindings = bindings,
            onClose = {
                showBind = false
                refreshTick++
            },
            onToast = { vm.toast(it) },
        )
    }

    // ---- 二级详情 ----
    val d = detail
    if (d != null) {
        androidx.activity.compose.BackHandler(enabled = true) { detail = null }   // 系统返回先回健康主页
        HealthDetail(
            kind = d, byDate = byDate, today = today,
            filter = filter, onBack = { detail = null },
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("健康", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Spacer(Modifier.weight(1f))
                Text(
                    "绑定管理",
                    fontSize = 12.5.sp, color = p.accentStrong,
                    modifier = Modifier.pressable { showBind = true }.padding(4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HFilterChip(filter == 0, "全部") { filter = 0 }
                bindings.forEach { b ->
                    val label = b.ownerName.ifBlank { b.displayName.ifBlank { "绑定 ${b.id}" } }
                    HFilterChip(filter == b.id, label) { filter = b.id }
                }
            }
        }

        // ---- 模块卡（两列）----
        val todayRows = byDate[today] ?: emptyList()
        val steps = todayRows.sumOf { it.steps }
        val sleep = todayRows.sumOf { it.sleepMinutes }
        val hr = todayRows.filter { it.hrAvg > 0 }.map { it.hrAvg }.average().takeIf { !it.isNaN() } ?: 0.0
        val spo2 = todayRows.filter { it.spo2Avg > 0 }.map { it.spo2Avg }.average().takeIf { !it.isNaN() } ?: 0.0
        val stress = todayRows.filter { it.stressAvg > 0 }.map { it.stressAvg }.average().takeIf { !it.isNaN() } ?: 0.0
        val weight = rows.mapNotNull { it.weightKg.takeIf { w -> w > 0 } }.lastOrNull() ?: 0.0

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("👣", "步数", if (steps > 0) "%,d".format(steps) else "—", "步", Modifier.weight(1f)) { detail = "steps" }
                MetricCard("😴", "睡眠", if (sleep > 0) "${sleep / 60}时${sleep % 60}分" else "—", "", Modifier.weight(1f)) { detail = "sleep" }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("❤️", "心率", if (hr > 0) hr.toInt().toString() else "—", "bpm", Modifier.weight(1f)) { detail = "hr" }
                MetricCard("🩸", "血氧", if (spo2 > 0) spo2.toInt().toString() else "—", "%", Modifier.weight(1f)) { detail = "spo2" }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("🧘", "压力", if (stress > 0) stress.toInt().toString() else "—", "", Modifier.weight(1f)) { detail = "stress" }
                MetricCard("⚖️", "体重", if (weight > 0) weight.toString() else "—", "kg", Modifier.weight(1f)) { detail = "weight" }
            }
        }

        // ---- 近 7 天概览 ----
        item {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Column {
                    Text("近 7 天", fontSize = 12.5.sp, color = p.textSub)
                    Spacer(Modifier.height(8.dp))
                    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
                    days.forEach { day ->
                        val rs = byDate[day.toString()] ?: emptyList()
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(day.toString().substring(5), fontSize = 11.sp, color = p.textWeak, modifier = Modifier.width(52.dp))
                            Text(
                                if (rs.isEmpty()) "—" else "%,d 步".format(rs.sumOf { it.steps }),
                                fontSize = 12.sp, color = p.text, modifier = Modifier.width(78.dp),
                            )
                            Text(
                                rs.sumOf { it.sleepMinutes }.let { if (it > 0) "睡 ${it / 60}时${it % 60}分" else "睡 —" },
                                fontSize = 11.5.sp, color = p.textSub, modifier = Modifier.width(96.dp),
                            )
                            Text(
                                rs.filter { x -> x.hrAvg > 0 }.map { x -> x.hrAvg }.average()
                                    .takeIf { !it.isNaN() }?.let { "心率 ${it.toInt()}" } ?: "心率 —",
                                fontSize = 11.5.sp, color = p.textSub,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 二级详情 ====================

@Composable
private fun HealthDetail(
    kind: String,
    byDate: Map<String, List<HealthDailyItem>>,
    today: String,
    filter: Int,
    onBack: () -> Unit,
) {
    val p = palette
    val title = mapOf(
        "steps" to "步数", "sleep" to "睡眠", "hr" to "心率",
        "spo2" to "血氧", "stress" to "压力", "weight" to "体重",
    )[kind] ?: ""
    val days = (13 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "←", fontSize = 17.sp, color = p.accentStrong,
                    modifier = Modifier.pressable(onClick = onBack).padding(end = 10.dp, top = 2.dp, bottom = 2.dp),
                )
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            }
        }

        when (kind) {
            "steps" -> {
                // 今日大卡：突出当天
                val t = (byDate[today] ?: emptyList())
                val tSteps = t.sumOf { it.steps }
                val tDist = t.sumOf { it.distanceM }
                val tKcal = t.sumOf { it.caloriesActive }
                val tAct = t.sumOf { it.activityMinutes }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Column {
                            Text("今天", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "%,d".format(tSteps), fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold, color = p.accent,
                                )
                                Text(" 步", fontSize = 14.sp, color = p.textSub, modifier = Modifier.padding(bottom = 7.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth()) {
                                SmallStat("距离", "%.2f".format(tDist / 1000.0), "km", Modifier.weight(1f))
                                SmallStat("活动热量", tKcal.toInt().toString(), "kcal", Modifier.weight(1f))
                                SmallStat("活动时长", tAct.toString(), "分钟", Modifier.weight(1f))
                            }
                        }
                    }
                }
                // 近 14 天列表（替代柱状图）
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Column {
                            Text("近 14 天", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(6.dp))
                            days.reversed().forEach { day ->
                                val rs = byDate[day.toString()] ?: emptyList()
                                val s = rs.sumOf { it.steps }
                                val km = rs.sumOf { it.distanceM } / 1000.0
                                val kcal = rs.sumOf { it.caloriesActive }.toInt()
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        day.toString().substring(5),
                                        fontSize = 11.sp, color = p.textWeak, modifier = Modifier.width(46.dp),
                                    )
                                    // 细进度条：按当期最大值
                                    val maxS = days.maxOf { dd -> (byDate[dd.toString()] ?: emptyList()).sumOf { it.steps } }.coerceAtLeast(1)
                                    Box(
                                        Modifier.weight(1f).height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(p.cardSoft),
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth(s.toFloat() / maxS).height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (day.toString() == today) p.accent else p.accentSoft),
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "%,d".format(s), fontSize = 12.5.sp, color = p.text,
                                        modifier = Modifier.width(62.dp), textAlign = TextAlign.End,
                                    )
                                    Text(
                                        "%.1fkm".format(km), fontSize = 10.5.sp, color = p.textWeak,
                                        modifier = Modifier.width(52.dp), textAlign = TextAlign.End,
                                    )
                                    Text(
                                        "${kcal}kcal", fontSize = 10.5.sp, color = p.textWeak,
                                        modifier = Modifier.width(54.dp), textAlign = TextAlign.End,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "sleep" -> {
                // 今日睡眠：时长 + 起止时刻 + 分期
                val t = (byDate[today] ?: emptyList())
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Column {
                            Text("昨晚", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(4.dp))
                            val mins = t.sumOf { it.sleepMinutes }
                            val start = t.mapNotNull { it.sleepStart }.firstOrNull()
                            val end = t.mapNotNull { it.sleepEnd }.firstOrNull()
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    if (mins > 0) "${mins / 60}时${mins % 60}分" else "—",
                                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = p.accent,
                                )
                            }
                            if (start != null && end != null) {
                                Spacer(Modifier.height(6.dp))
                                Text("入睡 $start · 醒来 $end", fontSize = 13.sp, color = p.textSub)
                            }
                            Spacer(Modifier.height(10.dp))
                            val deep = t.sumOf { it.sleepDeepMin }
                            val light = t.sumOf { it.sleepLightMin }
                            val rem = t.sumOf { it.sleepRemMin }
                            val awake = t.sumOf { it.sleepAwakeMin }
                            Row(Modifier.fillMaxWidth()) {
                                SmallStat("深睡", (deep / 60).toString(), "时${deep % 60}分", Modifier.weight(1f))
                                SmallStat("浅睡", (light / 60).toString(), "时${light % 60}分", Modifier.weight(1f))
                                SmallStat("REM", (rem / 60).toString(), "时${rem % 60}分", Modifier.weight(1f))
                                SmallStat("清醒", (awake / 60).toString(), "时${awake % 60}分", Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Column {
                            Text("近 7 天", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(8.dp))
                            days.takeLast(7).reversed().forEach { d ->
                                val rs = byDate[d.toString()] ?: emptyList()
                                val deep = rs.sumOf { it.sleepDeepMin }
                                val light = rs.sumOf { it.sleepLightMin }
                                val rem = rs.sumOf { it.sleepRemMin }
                                val awake = rs.sumOf { it.sleepAwakeMin }
                                val total = (deep + light + rem + awake).coerceAtLeast(1)
                                val s = rs.mapNotNull { it.sleepStart }.firstOrNull()
                                val e = rs.mapNotNull { it.sleepEnd }.firstOrNull()
                                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Row {
                                        Text(d.toString().substring(5), fontSize = 10.5.sp, color = p.textWeak, modifier = Modifier.width(44.dp))
                                        Text(
                                            if (deep + light + rem > 0) "${(deep + light + rem) / 60}时${(deep + light + rem) % 60}分" else "—",
                                            fontSize = 12.sp, color = p.text, modifier = Modifier.width(72.dp),
                                        )
                                        Text(
                                            if (s != null && e != null) "$s ~ $e" else "",
                                            fontSize = 10.5.sp, color = p.textWeak,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        Modifier.fillMaxWidth().height(10.dp)
                                            .clip(RoundedCornerShape(5.dp)),
                                    ) {
                                        if (deep + light + rem + awake > 0) {
                                            StageSeg(deep, total, p.accent)
                                            StageSeg(light, total, p.accentSoft)
                                            StageSeg(rem, total, p.cardSoft)
                                            StageSeg(awake, total, p.textWeak.copy(alpha = .4f))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LegendDot(p.accent, "深睡"); LegendDot(p.accentSoft, "浅睡")
                                LegendDot(p.cardSoft, "REM"); LegendDot(p.textWeak.copy(alpha = .4f), "清醒")
                            }
                        }
                    }
                }
            }
            "hr" -> {
                val t = (byDate[today] ?: emptyList()).filter { it.hrAvg > 0 }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Column {
                            Text("今天", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    t.map { it.hrAvg }.average().takeIf { !it.isNaN() }?.toInt()?.toString() ?: "—",
                                    fontSize = 36.sp, fontWeight = FontWeight.Bold, color = p.accent,
                                )
                                Text(" bpm", fontSize = 14.sp, color = p.textSub, modifier = Modifier.padding(bottom = 6.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                SmallStat("最高", t.maxOfOrNull { it.hrMax }?.toInt()?.toString() ?: "—", "bpm", Modifier.weight(1f))
                                SmallStat("最低", t.minOfOrNull { it.hrMin }?.toInt()?.toString() ?: "—", "bpm", Modifier.weight(1f))
                                SmallStat("静息", t.map { it.hrResting }.average().takeIf { !it.isNaN() }?.toInt()?.toString() ?: "—", "bpm", Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        HrCurveCard(byDate[today] ?: emptyList(), filter)
                    }
                }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Column {
                            Text("近 14 天 平均/静息", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(8.dp))
                            days.forEach { d ->
                                val rs = (byDate[d.toString()] ?: emptyList()).filter { it.hrAvg > 0 }
                                if (rs.isNotEmpty()) {
                                    val avg = rs.map { it.hrAvg }.average()
                                    val rest = rs.map { it.hrResting }.average()
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Text(d.toString().substring(5), fontSize = 10.5.sp, color = p.textWeak, modifier = Modifier.width(44.dp))
                                        Text("均 ${avg.toInt()}", fontSize = 11.5.sp, color = p.text, modifier = Modifier.width(58.dp))
                                        Text("静息 ${rest.toInt()}", fontSize = 11.5.sp, color = p.textSub)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "spo2" -> {
                val t = (byDate[today] ?: emptyList()).filter { it.spo2Avg > 0 }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Column {
                            Text("今天", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    t.map { it.spo2Avg }.average().takeIf { !it.isNaN() }?.toInt()?.toString() ?: "—",
                                    fontSize = 36.sp, fontWeight = FontWeight.Bold, color = p.accent,
                                )
                                Text(" %", fontSize = 14.sp, color = p.textSub, modifier = Modifier.padding(bottom = 6.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                SmallStat("均值", t.map { it.spo2Avg }.average().takeIf { !it.isNaN() }?.toInt()?.toString() ?: "—", "%", Modifier.weight(1f))
                                SmallStat("最高", t.map { it.spo2Avg }.maxOrNull()?.toInt()?.toString() ?: "—", "%", Modifier.weight(1f))
                                SmallStat("最低", t.map { it.spo2Avg }.minOrNull()?.toInt()?.toString() ?: "—", "%", Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Spo2CurveCard(filter)
                    }
                }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Column {
                            Text("近 7 天", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(6.dp))
                            days.takeLast(7).reversed().forEach { d ->
                                val rs = (byDate[d.toString()] ?: emptyList()).filter { it.spo2Avg > 0 }
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Text(d.toString().substring(5), fontSize = 11.sp, color = p.textWeak, modifier = Modifier.width(46.dp))
                                    Text(
                                        rs.map { it.spo2Avg }.average().takeIf { !it.isNaN() }?.let { "均 ${it.toInt()}%" } ?: "—",
                                        fontSize = 12.sp, color = p.text, modifier = Modifier.width(70.dp),
                                    )
                                    Text(
                                        rs.maxOfOrNull { it.spo2Avg }?.let { "高 ${it.toInt()}%" } ?: "",
                                        fontSize = 11.sp, color = p.textWeak, modifier = Modifier.width(62.dp),
                                    )
                                    Text(
                                        rs.minOfOrNull { it.spo2Avg }?.let { "低 ${it.toInt()}%" } ?: "",
                                        fontSize = 11.sp, color = p.textWeak,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "stress" -> {
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Column {
                            Text("近 7 天压力分档（时）", fontSize = 12.5.sp, color = p.textSub)
                            Spacer(Modifier.height(10.dp))
                            days.takeLast(7).forEach { d ->
                                val rs = byDate[d.toString()] ?: emptyList()
                                val relaxed = rs.sumOf { it.stressRelaxedMin }
                                val mild = rs.sumOf { it.stressMildMin }
                                val moderate = rs.sumOf { it.stressModerateMin }
                                val severe = rs.sumOf { it.stressSevereMin }
                                val total = (relaxed + mild + moderate + severe).coerceAtLeast(1)
                                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Row {
                                        Text(d.toString().substring(5), fontSize = 10.5.sp, color = p.textWeak, modifier = Modifier.width(44.dp))
                                        Text(
                                            if (relaxed + mild + moderate + severe > 0) "均 ${rs.filter { it.stressAvg > 0 }.map { it.stressAvg }.average().takeIf { !it.isNaN() }?.toInt() ?: "—"}" else "—",
                                            fontSize = 11.sp, color = p.text,
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                                        if (total > 0) {
                                            StageSeg(relaxed, total, p.accentSoft)
                                            StageSeg(mild, total, p.accent)
                                            StageSeg(moderate, total, p.textSub.copy(alpha = .5f))
                                            StageSeg(severe, total, p.textWeak.copy(alpha = .5f))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LegendDot(p.accentSoft, "放松"); LegendDot(p.accent, "轻度")
                                LegendDot(p.textSub.copy(alpha = .5f), "中度"); LegendDot(p.textWeak.copy(alpha = .5f), "重度")
                            }
                        }
                    }
                }
            }
            "weight" -> {
                item { WeightInputCard(today) }
                item {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        WeightTrendCard()
                    }
                }
            }
        }
    }
}

// ==================== 图表与小件 ====================

/** 近 N 天竖条图：数值标顶、日期标底，末条高亮 */
@Composable
private fun HBarChart(values: List<Double>, labels: List<String>, highlightLast: Boolean) {
    val p = palette
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Column {
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (v > 0) (v / 1000.0).let { String.format("%.1f", it) } else "",
                        fontSize = 7.5.sp, color = p.textWeak, maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .height((4 + 88.0 * v / max).dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (highlightLast && i == values.lastIndex) p.accent else p.accentSoft),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEach { l ->
                Text(
                    l, fontSize = 7.sp, color = p.textWeak, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f), maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StageSeg(min: Int, total: Int, color: androidx.compose.ui.graphics.Color) {
    if (min > 0) {
        Box(
            Modifier.weight(min.toFloat() / total)
                .height(10.dp)
                .background(color),
        )
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 9.5.sp, color = p.textWeak)
    }
}

/** 当日心率曲线（按小时聚合，多绑定取并集均值） */
@Composable
private fun HrCurveCard(todayRows: List<HealthDailyItem>, filter: Int) {
    val p = palette
    var hourly by remember { mutableStateOf<List<cn.anlele.checkin.data.HealthHourlyItem>>(emptyList()) }
    LaunchedEffect(filter) {
        try { hourly = Repo.healthHourly(LocalDate.now().toString(), if (filter == 0) null else filter).items }
        catch (_: Exception) { }
    }
    val byHour = hourly.groupBy { it.hour }
    val pts = (0..23).map { h ->
        byHour[h]?.filter { it.hrPoints > 0 }?.map { it.hrAvg }?.average()?.takeIf { !it.isNaN() }
    }
    val today = todayRows.filter { it.hrAvg > 0 }
    Column {
        Row {
            Text("今日心率曲线", fontSize = 12.5.sp, color = p.textSub)
            Spacer(Modifier.weight(1f))
            Text(
                today.map { it.hrAvg }.average().takeIf { !it.isNaN() }
                    ?.let { "均 ${it.toInt()} · 静息 ${today.map { x -> x.hrResting }.average().toInt()}" } ?: "今天还没有数据",
                fontSize = 10.5.sp, color = p.textWeak,
            )
        }
        Spacer(Modifier.height(10.dp))
        val valid = pts.withIndex().filter { it.value != null }
        if (valid.isEmpty()) {
            Text("今日暂无心率采样", fontSize = 11.sp, color = p.textWeak, modifier = Modifier.padding(12.dp))
        } else {
            val vmin = valid.minOf { it.value!! }.toInt() - 5
            val vmax = valid.maxOf { it.value!! }.toInt() + 5
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width; val h = size.height
                val path = Path()
                valid.forEachIndexed { i, iv ->
                    val x = w * iv.index / 23f
                    val y = h - h * ((iv.value!! - vmin).toFloat() / (vmax - vmin).coerceAtLeast(1))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, p.accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
                valid.forEach { iv ->
                    val x = w * iv.index / 23f
                    val y = h - h * ((iv.value!! - vmin).toFloat() / (vmax - vmin).coerceAtLeast(1))
                    drawCircle(p.accent, radius = 3.5f, center = Offset(x, y))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("0时", fontSize = 8.sp, color = p.textWeak)
                Spacer(Modifier.weight(1f))
                Text("12时", fontSize = 8.sp, color = p.textWeak)
                Spacer(Modifier.weight(1f))
                Text("23时", fontSize = 8.sp, color = p.textWeak)
            }
        }
    }
}

/** 小数值块（今日大卡里的并排数据） */
@Composable
private fun SmallStat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val p = palette
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = p.textWeak)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(unit, fontSize = 9.sp, color = p.textWeak, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

/** 当日血氧曲线（按小时聚合） */
@Composable
private fun Spo2CurveCard(filter: Int) {
    val p = palette
    var hourly by remember { mutableStateOf<List<cn.anlele.checkin.data.HealthHourlyItem>>(emptyList()) }
    LaunchedEffect(filter) {
        try { hourly = Repo.healthHourly(LocalDate.now().toString(), if (filter == 0) null else filter).items }
        catch (_: Exception) { }
    }
    val byHour = hourly.filter { it.spo2Points > 0 }.groupBy { it.hour }
    val pts = (0..23).map { h -> byHour[h]?.map { it.spo2Avg }?.average()?.takeIf { !it.isNaN() } }
    Column {
        Text("今日血氧曲线", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(10.dp))
        val valid = pts.withIndex().filter { it.value != null }
        if (valid.isEmpty()) {
            Text("今日暂无血氧采样", fontSize = 11.sp, color = p.textWeak, modifier = Modifier.padding(12.dp))
        } else {
            val vmin = valid.minOf { it.value!! }.toInt() - 2
            val vmax = valid.maxOf { it.value!! }.toInt() + 2
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width; val h = size.height
                val path = Path()
                valid.forEachIndexed { i, iv ->
                    val x = w * iv.index / 23f
                    val y = h - h * ((iv.value!! - vmin).toFloat() / (vmax - vmin).coerceAtLeast(1))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, p.accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
                valid.forEach { iv ->
                    val x = w * iv.index / 23f
                    val y = h - h * ((iv.value!! - vmin).toFloat() / (vmax - vmin).coerceAtLeast(1))
                    drawCircle(p.accent, radius = 3.5f, center = Offset(x, y))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("0时", fontSize = 8.sp, color = p.textWeak)
                Spacer(Modifier.weight(1f))
                Text("12时", fontSize = 8.sp, color = p.textWeak)
                Spacer(Modifier.weight(1f))
                Text("23时", fontSize = 8.sp, color = p.textWeak)
            }
        }
    }
}

/** 体重手填卡 */
@Composable
private fun WeightInputCard(today: String) {
    val p = palette
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column {
            Text("记体重（$today）", fontSize = 12.5.sp, color = p.textSub)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() || c == '.' }.take(6); saved = false },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = p.text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
                    modifier = Modifier.width(90.dp)
                        .clip(chipShape())
                        .background(p.cardSoft)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("kg", fontSize = 12.sp, color = p.textWeak)
                Spacer(Modifier.weight(1f))
                Text(
                    if (saved) "已保存" else "保存",
                    fontSize = 13.sp, color = p.accentStrong, fontWeight = FontWeight.Medium,
                    modifier = Modifier.pressable {
                        val v = input.toDoubleOrNull()
                        if (v == null || v < 20 || v > 400) return@pressable
                        scope.launch {
                            try {
                                Repo.healthWeightSave(today, v)
                                saved = true
                            } catch (e: Exception) { }
                        }
                    }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** 体重趋势（手填 + 设备合并，手填标记） */
@Composable
private fun WeightTrendCard() {
    val p = palette
    var items by remember { mutableStateOf<List<cn.anlele.checkin.data.HealthWeightItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            items = Repo.healthWeights(LocalDate.now().minusDays(29).toString(), LocalDate.now().toString()).items
        } catch (_: Exception) { }
    }
    Column {
        Text("近 30 天体重", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("还没有体重记录", fontSize = 11.5.sp, color = p.textWeak, modifier = Modifier.padding(8.dp))
        } else {
            items.sortedByDescending { it.date }.take(30).forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(it.date, fontSize = 11.sp, color = p.textWeak, modifier = Modifier.width(80.dp))
                    Text("${it.weightKg} kg", fontSize = 12.5.sp, color = p.text)
                    if (it.manual) {
                        Spacer(Modifier.width(6.dp))
                        Text("手填", fontSize = 9.sp, color = p.accentSoft, modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(p.accentSoft.copy(alpha = .3f))
                            .padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HFilterChip(on: Boolean, label: String, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier
            .clip(chipShape())
            .background(if (on) p.accentSoft else p.cardSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label, fontSize = 12.sp,
            color = if (on) p.accentStrong else p.textSub,
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 模块卡：图标 + 名称 + 当日值，点进详情 */
@Composable
private fun MetricCard(
    icon: String, label: String, value: String, unit: String,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val p = palette
    AppCard(modifier.pressable(onClick = onClick), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 14.sp)
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 11.5.sp, color = p.textSub)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, fontSize = 9.5.sp, color = p.textWeak, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("详情 ›", fontSize = 10.sp, color = p.accentStrong)
        }
    }
}
