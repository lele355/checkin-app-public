package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.SummaryItemDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * 月度总结 —— 情侣大类（2026-11-02 站主规划）：mimo AI 生成。
 * 每日阶段版 + 月末定稿，全部留存可查；可手动「生成截至今天」。
 */
@Composable
fun SummaryScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SummaryItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var generating by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try {
            items = Repo.summaries().items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PageHeadCard(title = "月度总结", hint = "AI 生成 · 每日阶段 + 月末定稿") }
            item {
                PrimaryButton(if (generating) "生成中…" else "✨ 生成截至今天的总结", enabled = !generating) {
                    generating = true
                    scope.launch {
                        runCatching { Repo.generateSummary() }
                            .onSuccess {
                                vm.toast("生成好啦")
                                load()
                            }
                            .onFailure { vm.toast(it.message ?: "生成失败") }
                        generating = false
                    }
                }
            }
            if (items.isEmpty() && !loading) {
                item { Text("还没有总结。每天 23:30 自动生成，也可以点上面按钮立即生成。", fontSize = 12.5.sp, color = p.textWeak) }
            } else {
                items(items) { s ->
                    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                        Column {
                            Row {
                                Text(s.month, fontSize = 14.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (s.isFinal) "当月定稿" else "截至 ${s.scopeDays} 天",
                                    fontSize = 11.sp,
                                    color = if (s.isFinal) p.accent else p.textWeak,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(s.generatedAt, fontSize = 10.sp, color = p.textWeak)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(s.content, fontSize = 12.5.sp, color = p.textSub, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
    }
}
