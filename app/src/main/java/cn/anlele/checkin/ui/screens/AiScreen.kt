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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
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
import cn.anlele.checkin.data.AiActionDto
import cn.anlele.checkin.data.AiMsgDto
import cn.anlele.checkin.data.AiPageDto
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * AI 助手 —— 对话 + 建议单。
 *
 * 与网页同一套规则：AI 只「提建议」，用户点确认才真正写库。
 * 所以助手消息下面会跟一张建议卡，确认按钮点完变成「已写入」。
 */
@Composable
fun AiScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<AiPageDto?>(null) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var showClear by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try {
            page = Repo.aiMessages()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    fun send() {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        text = ""
        sending = true
        scope.launch {
            try {
                page = Repo.aiSend(t)
            } catch (e: ApiException) {
                vm.toast(e.message)
            } catch (e: Exception) {
                vm.toast("发送失败，检查网络后再试")
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部：与网页同款页头卡（标题 + 说明 + tokens 用量 + 清空）
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            PageHeadCard(
                title = "AI 助手",
                hint = "可以问问题，也可以直接说「明天下午三点开会」「记一笔午饭 15 元」—— AI 会给建议单，你点了确认才真正写入。",
                actions = { TinyAction("清空历史", "trash-2") { showClear = true } },
            )
            val q = page?.quota
            if (q != null) {
                Text(
                    "今日已用 ${q.usedTokens} / ${q.dailyTokens} tokens",
                    fontSize = 11.sp,
                    color = p.textWeak,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
        }

        val msgs = page?.items.orEmpty()
        if (msgs.isEmpty() && !loading) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    icon = "spark",
                    title = "问我点什么吧",
                    hint = "「明天下午三点开会」我就能帮你排进日程",
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(msgs.size) { i ->
                    val m = msgs[i]
                    if (m.role == "user") UserBubble(m) else AssistantBubble(m) { idx ->
                        scope.launch {
                            try {
                                val r = Repo.aiExecute(m.id, idx)
                                vm.toast(r.message.ifBlank { "已写入" })
                                load()
                            } catch (e: ApiException) {
                                vm.toast(e.message)
                            } catch (e: Exception) {
                                vm.toast("执行失败")
                            }
                        }
                    }
                }
                if (sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = p.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在想…", fontSize = 12.sp, color = p.textWeak)
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(p.card)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f)) {
                AppField("", text, { text = it }, placeholder = "说点什么…", singleLine = false, minLines = 1, maxLines = 4)
            }
            Spacer(Modifier.width(6.dp))
            IconBtn("send", "发送", size = 44.dp, tint = if (text.isBlank() || sending) p.textWeak else p.accent) { send() }
        }
    }

    if (showClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClear = false },
            containerColor = p.card,
            title = { Text("清空对话", fontSize = 16.sp, color = p.text) },
            text = { Text("会删掉和 AI 的全部聊天记录，删了找不回来。", fontSize = 13.5.sp, color = p.textSub) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showClear = false
                    scope.launch {
                        runCatching { Repo.aiClear() }
                        load()
                    }
                }) { Text("清空", color = androidx.compose.ui.graphics.Color(0xFFD93025)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClear = false }) { Text("取消", color = p.textSub) }
            },
        )
    }
}

@Composable
private fun UserBubble(m: AiMsgDto) {
    val p = palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .fillMaxWidth(0.84f)
                .background(p.cardSoft, p.cardShape())
                .padding(12.dp),
        ) {
            Text(m.content.ifBlank { if (m.hasImage) "[图片]" else "" }, fontSize = 13.5.sp, color = p.text, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun AssistantBubble(m: AiMsgDto, onExecute: (Int) -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(26.dp).background(p.accentSoft, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons["spark"], null, Modifier.size(14.dp), tint = p.accent) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.fillMaxWidth(0.9f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(p.card, p.cardShape())
                    .padding(12.dp),
            ) {
                Text(m.content.ifBlank { "（没有文字回复）" }, fontSize = 13.5.sp, color = p.text, lineHeight = 21.sp)
            }
            m.actions.filterNot { it.done }.forEach { a ->
                Spacer(Modifier.height(7.dp))
                ActionCard(a) { onExecute(a.index) }
            }
        }
    }
}

@Composable
private fun ActionCard(a: AiActionDto, onConfirm: () -> Unit) {
    val p = palette
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(11.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.background(p.accentSoft, chipShape()).padding(horizontal = 7.dp, vertical = 2.dp),
                ) { Text(a.label, fontSize = 10.5.sp, color = p.accent) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(p.accent, chipShape())
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    Text("确认写入", fontSize = 11.5.sp, color = p.onAccent)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(a.summary, fontSize = 12.5.sp, color = p.textSub)
        }
    }
}
