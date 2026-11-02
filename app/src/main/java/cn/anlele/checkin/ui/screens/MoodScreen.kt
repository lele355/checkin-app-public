package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.MoodItemDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

private val MOOD_EMOJIS = listOf("😊", "😐", "😔", "😡", "😴", "🥰")
private val MOOD_NAMES = listOf("开心", "平淡", "难过", "生气", "困", "害羞")

/**
 * 心情记录 —— 情侣大类（2026-11-02 站主规划）：双方每日一个心情，日历双行表情视图。
 * 双方互见可记；点日期格选表情即记录（每人每日一行 upsert）。
 */
@Composable
fun MoodScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(java.time.LocalDate.now().toString().take(7)) }   // yyyy-MM
    var items by remember { mutableStateOf<List<MoodItemDto>>(emptyList()) }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var saveDay by remember { mutableStateOf<Int?>(null) }
    var saveOwner by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            val d = Repo.moods(month)
            items = d.items
            names = d.items.map { it.owner }.distinct()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(month) { load() }

    val byKey = remember(items) { items.associateBy { it.date to it.userId } }
    val owners = remember(items) { items.map { it.userId }.distinct() }
    val ownerNames = remember(items) { items.associate { it.userId to it.owner } }
    val myUid = items.firstOrNull()?.userId ?: 0   // 归属展示顺序（自己的在上）

    // 月历参数
    val (year, mon) = month.split("-").map { it.toInt() }
    val daysInMonth = java.time.YearMonth.of(year, mon).lengthOfMonth()
    val offset = (java.time.LocalDate.of(year, mon, 1).dayOfWeek.value % 7)   // 周一开头

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PageHeadCard(title = "心情记录", hint = "情侣互见 · 每天一个心情") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton("← 上月") {
                        month = java.time.YearMonth.of(year, mon).minusMonths(1).toString().take(7)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("$year 年 $mon 月", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                    Spacer(Modifier.weight(1f))
                    SecondaryButton("下月 →") {
                        month = java.time.YearMonth.of(year, mon).plusMonths(1).toString().take(7)
                    }
                }
            }
            // 日历双行表情（每格：日期 + 各成员一行表情）
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(10.dp)) {
                    Column {
                        Row {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                                Text(it, Modifier.weight(1f), fontSize = 11.sp, color = p.textWeak, textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier.heightIn(max = 420.dp),
                            userScrollEnabled = false,
                        ) {
                            items((1..(offset + daysInMonth)).toList()) { idx ->
                                if (idx <= offset) {
                                    Spacer(Modifier.height(52.dp))
                                } else {
                                    val day = idx - offset
                                    val dateStr = "$year-${mon.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                                    Column(
                                        Modifier
                                            .height(52.dp)
                                            .fillMaxWidth()
                                            .clickable { saveDay = day },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text("$day", fontSize = 10.sp, color = p.textWeak)
                                        for (u in owners) {
                                            val m = byKey[dateStr to u]?.mood
                                            Text(
                                                m?.let { MOOD_EMOJIS.getOrElse(it - 1) { "·" } } ?: "·",
                                                fontSize = 15.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // 月度统计
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                    Column {
                        Text("本月心情分布", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        for (u in owners) {
                            val mine = items.filter { it.userId == u }
                            val line = (1..6).mapNotNull { m -> mine.count { it.mood == m }.takeIf { it > 0 }?.let { "${MOOD_EMOJIS[m - 1]} $it" } }
                            Row {
                                Text(
                                    (ownerNames[u] ?: "?") + "：",
                                    fontSize = 12.sp, color = p.textWeak,
                                )
                                Text(line.joinToString("  "), fontSize = 12.sp, color = p.text)
                            }
                        }
                    }
                }
            }
        }

        // 记录弹层（点日期格）
        saveDay?.let { day ->
            val dateStr = "$year-${mon.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
            AppCard(Modifier.fillMaxWidth().padding(14.dp), padding = PaddingValues(14.dp)) {
                Column {
                    Text("记一个心情 · $mon 月 $day 日", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    // 归属选择（双方互见可记）
                    val ownerIds = owners.ifEmpty { listOf(0) }
                    if (ownerIds.size > 1) {
                        Row {
                            ownerIds.forEach { u ->
                                Text(
                                    (ownerNames[u] ?: "TA"),
                                    Modifier
                                        .clickable { saveOwner = u }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    color = if (saveOwner == u) p.accent else p.textWeak,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        MOOD_EMOJIS.forEachIndexed { i, e ->
                            Text(
                                e,
                                Modifier
                                    .clickable {
                                        scope.launch {
                                            runCatching {
                                                Repo.setMood(Repo.MoodReq(date = dateStr, mood = i + 1, userId = saveOwner.takeIf { it != 0 }))
                                            }.onSuccess {
                                                vm.toast("记好啦")
                                                saveDay = null
                                                load()
                                            }.onFailure { vm.toast(it.message ?: "保存失败") }
                                        }
                                    }
                                    .padding(6.dp),
                                fontSize = 30.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TinyAction("收起", "chevron-down") { saveDay = null }
                }
            }
        }
    }
}
