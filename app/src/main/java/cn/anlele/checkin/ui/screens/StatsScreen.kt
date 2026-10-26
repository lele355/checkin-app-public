package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
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
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.StatsDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.GrowBar
import cn.anlele.checkin.ui.components.LoadingBlock
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.RiseBar
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/** 热力图的绿：网页用的是固定绿色系（不跟主题走），深浅表示当天打卡次数 */
private val HEAT = Color(0xFF3E9E5B)

/**
 * 统计年报 —— 与网页手机端一致：
 * 计数 chips → 近 90 天打卡热力图（带「少·多」图例）→ 打卡排行（条状）→ 近 6 月支出（柱状）。
 */
@Composable
fun StatsScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<StatsDto?>(null) }
    var loading by remember { mutableStateOf(true) }

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
            PageHeadCard(title = "统计")
        }

        // ---------- 计数 chips ----------
        item {
            StaggerIn(0) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            AppChip("日程 ${data.counts.schedules}")
                            AppChip("便签 ${data.counts.memos}")
                            AppChip("课程 ${data.counts.courses}")
                            AppChip("留言 ${data.counts.board}")
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("累计打卡 ${data.counts.checkins} 次", fontSize = 12.5.sp, color = p.textSub)
                    }
                }
            }
        }

        // ---------- 热力图 ----------
        item {
            StaggerIn(1) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    Column {
                        Text("🔥 打卡热力图（近 90 天）", fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(11.dp))
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
        }

        // ---------- 打卡排行 ----------
        if (data.topHabits.isNotEmpty()) {
            item {
                StaggerIn(2) {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        Column {
                            Text("🏆 打卡排行", fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
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

        // ---------- 月度支出 ----------
        item {
            StaggerIn(3) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    Column {
                        Text("📊 月度支出（近 6 个月）", fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth().height(120.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val max = data.maxMonthOut.coerceAtLeast(1.0)
                            data.months.forEachIndexed { i, m ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (m.amount > 0) Dates.money(m.amount) else "",
                                        fontSize = 9.5.sp,
                                        color = p.textWeak,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    RiseBar((m.amount / max).toFloat(), delayMs = i * 40)
                                    Spacer(Modifier.height(5.dp))
                                    Text(m.label, fontSize = 10.sp, color = p.textSub, textAlign = TextAlign.Center)
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
