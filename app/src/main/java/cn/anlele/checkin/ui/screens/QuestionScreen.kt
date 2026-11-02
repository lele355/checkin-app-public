package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.QuestionItemDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * 互相提问 —— 情侣大类（2026-11-02 站主规划）：双方互问互答。
 * 未答/已答筛选 + 分页（控条数）。双方可见。
 */
@Composable
fun QuestionScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("all") }
    var page by remember { mutableStateOf(1) }
    var data by remember { mutableStateOf(cn.anlele.checkin.data.QuestionListDto()) }
    var askText by remember { mutableStateOf("") }
    var answerText by remember { mutableStateOf("") }
    var answerId by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            data = Repo.questions(status = status, page = page)
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(status, page) { load() }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PageHeadCard(title = "互相提问", hint = "互问互答 · 双方可见") }

            // 提问框
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                    Column {
                        Text("问对方一个问题", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            cn.anlele.checkin.ui.components.AppField("", askText, { askText = it }, placeholder = "想问 TA 什么…", singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            PrimaryButton("提问") {
                                val t = askText.trim()
                                if (t.isEmpty()) return@PrimaryButton
                                scope.launch {
                                    runCatching { Repo.ask(Repo.AskReq(content = t)) }
                                        .onSuccess {
                                            vm.toast("提问成功")
                                            askText = ""
                                            load()
                                        }
                                        .onFailure { vm.toast(it.message ?: "提问失败") }
                                }
                            }
                        }
                    }
                }
            }

            // 筛选 tabs
            item {
                Row {
                    listOf("all" to "全部", "unanswered" to "未答", "answered" to "已答").forEach { (k, label) ->
                        Text(
                            "$label${if (k == "unanswered" && data.unanswered > 0) " ${data.unanswered}" else ""}",
                            Modifier
                                .clickable { status = k; page = 1 }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 13.sp,
                            color = if (status == k) p.accent else p.textWeak,
                            fontWeight = if (status == k) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (data.items.isEmpty() && !loading) {
                item { Text("还没有问题，问 TA 一个吧", fontSize = 12.5.sp, color = p.textWeak) }
            } else {
                items(data.items) { q ->
                    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                        Column {
                            Text("${q.from} → ${q.to} · ${q.askedAt}", fontSize = 10.5.sp, color = p.textWeak)
                            Spacer(Modifier.height(4.dp))
                            Text(q.content, fontSize = 14.sp, color = p.text, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            if (q.answer != null) {
                                Text("💬 ${q.answer}", fontSize = 12.5.sp, color = p.accent)
                                Text(q.answeredAt ?: "", fontSize = 10.sp, color = p.textWeak)
                            } else if (q.mineToAnswer) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    cn.anlele.checkin.ui.components.AppField("", if (answerId == q.id) answerText else "", { answerId = q.id; answerText = it }, placeholder = "写下你的回答…", singleLine = true)
                                    Spacer(Modifier.width(8.dp))
                                    SecondaryButton("回答") {
                                        val t = answerText.trim()
                                        if (t.isEmpty() || answerId != q.id) return@SecondaryButton
                                        scope.launch {
                                            runCatching { Repo.answerQuestion(q.id, Repo.AnswerReq(content = t)) }
                                                .onSuccess {
                                                    vm.toast("回答成功")
                                                    answerText = ""
                                                    answerId = 0
                                                    load()
                                                }
                                                .onFailure { vm.toast(it.message ?: "回答失败") }
                                        }
                                    }
                                }
                            } else {
                                Text("等 ${q.to} 回答…", fontSize = 11.5.sp, color = p.textWeak)
                            }
                        }
                    }
                }
            }

            // 分页
            if (data.pages > 1) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (page > 1) SecondaryButton("上一页") { page-- }
                        Spacer(Modifier.width(12.dp))
                        Text("$page / ${data.pages}", fontSize = 12.sp, color = p.textWeak)
                        Spacer(Modifier.width(12.dp))
                        if (page < data.pages) SecondaryButton("下一页") { page++ }
                    }
                }
            }
        }
    }
}
