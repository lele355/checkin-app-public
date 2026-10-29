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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ReminderItemDto
import cn.anlele.checkin.data.RemindersDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/** 提醒 —— 把近期事项聚成一张清单，点一条直接跳到对应模块。 */
@Composable
fun RemindersScreen(vm: AppVm, go: (String) -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<RemindersDto?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            d = Repo.reminders()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val data = d
    if (data == null) {
        Text(if (loading) "正在加载…" else "暂无提醒", fontSize = 12.5.sp, color = p.textWeak, modifier = Modifier.padding(20.dp))
        return
    }

    val routeOf = { url: String ->
        when {
            url.contains("Schedule", true) -> "schedule"
            url.contains("Anniversar", true) -> "anniversary"
            url.contains("Habits", true) -> "habits"
            url.contains("Period", true) -> "period"
            url.contains("Memo", true) -> "memo"
            else -> "workbench"
        }
    }

    if (data.items.isEmpty() && data.todayHabitTotal == 0) {
        EmptyState(icon = "bell", title = "近期没有要提醒的事")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),

    ) {

        item { PageHeadCard(title = "提醒", hint = "点一条直达对应模块") }
        if (data.todayHabitTotal > 0) {
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(15.dp), onClick = { go("habits") }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons["check"], null, Modifier.size(15.dp), tint = p.accent)
                            Spacer(Modifier.width(7.dp))
                            Text("今日打卡", fontSize = 13.sp, color = p.textSub, modifier = Modifier.weight(1f))
                            Text("${data.todayHabitDone}/${data.todayHabitTotal}", fontSize = 13.sp, color = p.accent)
                        }
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.fillMaxWidth().height(7.dp).background(p.cardSoft, chipShape())) {
                            Box(
                                Modifier
                                    .fillMaxWidth((data.todayHabitDone.toFloat() / data.todayHabitTotal).coerceIn(0f, 1f))
                                    .height(7.dp)
                                    .background(p.accent, chipShape())
                            )
                        }
                    }
                }
            }
        }

        items(data.items.size) { i ->
            val it = data.items[i]
            ReminderRow(it) { go(routeOf(it.url)) }
        }
    }
}

@Composable
private fun ReminderRow(item: ReminderItemDto, onClick: () -> Unit) {
    val p = palette
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(p.accentSoft, chipShape())
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(item.kind, fontSize = 10.5.sp, color = p.accent, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(10.dp))
            Text(item.title, fontSize = 13.5.sp, color = p.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(item.whenText, fontSize = 11.5.sp, color = p.textWeak)
        }
    }
}
