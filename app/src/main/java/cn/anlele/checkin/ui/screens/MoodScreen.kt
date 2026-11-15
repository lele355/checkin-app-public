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
private val MOOD_NAMES = listOf("生气", "难过", "困", "平淡", "害羞", "开心")

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
    var members by remember { mutableStateOf<List<cn.anlele.checkin.data.MoodMemberDto>>(emptyList()) }
    var saveDay by remember { mutableStateOf<Int?>(null) }
    var saveOwner by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            val d = Repo.moods(month)
            items = d.items
            names = d.items.map { it.owner }.distinct()
            members = d.members
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(month) { load() }

    val byKey = remember(items) { items.associateBy { it.date to it.userId } }
    // 行归属固定（2026-11-02 站主定）：members 顺序=自己在前，永远第一行=自己、第二行=对方
    val owners = remember(members) { members.map { it.userId } }
    val ownerNames = remember(members) { members.associate { it.userId to it.name } }

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
            // 日历双行表情（大格子+固定行归属：上=自己圆脸 / 下=对方方脸）
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                    Column {
                        // 行归属图例（固定，不搞混）
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            for ((i, u) in owners.withIndex()) {
                                cn.anlele.checkin.ui.components.MoodSticker(mood = 2, round = i == 0, size = 18.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${if (i == 0) "上" else "下"}：${ownerNames[u] ?: "TA"}",
                                    fontSize = 11.sp, color = p.textWeak,
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                                Text(it, Modifier.weight(1f), fontSize = 12.sp, color = p.textWeak, textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier.heightIn(max = 760.dp),
                            userScrollEnabled = false,
                        ) {
                            items((1..(offset + daysInMonth)).toList()) { idx ->
                                if (idx <= offset) {
                                    Spacer(Modifier.height(104.dp))
                                } else {
                                    val day = idx - offset
                                    val dateStr = "$year-${mon.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                                    val isFuture = dateStr > java.time.LocalDate.now().toString()   // 不能给未来记心情（2026-11-02 站主定）
                                    Column(
                                        Modifier
                                            .height(104.dp)
                                            .fillMaxWidth()
                                            .then(if (isFuture) Modifier else Modifier.clickable { saveDay = day }),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            "$day",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isFuture) p.textWeak.copy(alpha = .4f) else p.text,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        // 固定两行：第一行=owners[0]（圆脸），第二行=owners[1]（方脸）
                                        for ((i, u) in owners.withIndex()) {
                                            Box(
                                                Modifier.height(34.dp).fillMaxWidth(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                val m = byKey[dateStr to u]?.mood
                                                if (m != null) {
                                                    cn.anlele.checkin.ui.components.MoodSticker(
                                                        mood = m,
                                                        round = i == 0,
                                                        size = 32.dp,
                                                    )
                                                } else {
                                                    Text(
                                                        "·",
                                                        fontSize = 16.sp,
                                                        color = p.textWeak.copy(alpha = if (isFuture) 0.25f else 0.3f),
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
            // 月度统计
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                    Column {
                        Text("本月心情分布", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        for (u in owners) {
                            val mine = items.filter { it.userId == u }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text((ownerNames[u] ?: "?") + "：", fontSize = 12.sp, color = p.textWeak)
                                for (m in 1..6) {
                                    val c = mine.count { it.mood == m }
                                    if (c > 0) {
                                        // 统一贴纸脸（2026-11-02 站主定）
                                        cn.anlele.checkin.ui.components.MoodSticker(mood = m, round = u == owners.firstOrNull(), size = 18.dp)
                                        Text("×$c  ", fontSize = 11.sp, color = p.textWeak)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 记录弹层（点日期格）——只能记自己的心情（2026-11-02 站主定）
        saveDay?.let { day ->
            val dateStr = "$year-${mon.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
            AppCard(Modifier.fillMaxWidth().padding(14.dp), padding = PaddingValues(14.dp)) {
                Column {
                    Text("记一个心情 · $mon 月 $day 日（自己的）", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        (1..6).forEach { m ->
                            // 统一贴纸脸（2026-11-02 站主定：风格统一不用 emoji）
                            Box(
                                Modifier
                                    .clickable {
                                        scope.launch {
                                            runCatching {
                                                Repo.setMood(Repo.MoodReq(date = dateStr, mood = m))
                                            }.onSuccess {
                                                vm.toast("记好啦")
                                                saveDay = null
                                                load()
                                            }.onFailure { vm.toast(it.message ?: "保存失败") }
                                        }
                                    }
                                    .padding(6.dp)
                            ) {
                                cn.anlele.checkin.ui.components.MoodSticker(mood = m, round = true, size = 38.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        TinyAction("收起", "chevron-down") { saveDay = null }
                        // 删除这天的心情（自己的）
                        val existing = byKey[dateStr to (owners.firstOrNull() ?: 0)]
                        if (existing != null) {
                            Spacer(Modifier.width(16.dp))
                            TinyAction("删除这天的心情", "trash") {
                                scope.launch {
                                    runCatching {
                                        Repo.deleteMood(dateStr)
                                    }.onSuccess {
                                        vm.toast("删掉了")
                                        saveDay = null
                                        load()
                                    }.onFailure { vm.toast(it.message ?: "删除失败") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
