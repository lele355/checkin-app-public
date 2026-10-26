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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.BoardMsgDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/** 留言板 —— 小圈子的公开留言，输入行常驻底部（跟聊天 App 一个手感）。 */
@Composable
fun BoardScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<BoardMsgDto>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            rows = Repo.board().items
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
        sending = true
        scope.launch {
            try {
                Repo.postBoard(t)
                text = ""
                load()
            } catch (e: ApiException) {
                vm.toast(e.message)
            } catch (e: Exception) {
                vm.toast("发送失败")
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (rows.isEmpty() && !loading) {
            Box(Modifier.weight(1f)) {
                EmptyState(icon = "chat", title = "还没有人留言")
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),

            ) {

                item { PageHeadCard(title = "留言板", hint = "进来的人都能看到") }
                items(rows.size) { i ->
                    val m = rows[i]
                    BoardCard(m, onDelete = {
                        scope.launch {
                            runCatching { Repo.deleteBoard(m.id) }
                            load()
                        }
                    })
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                AppField("", text, { text = it }, placeholder = "说点什么…", singleLine = false, minLines = 1, maxLines = 4)
            }
            Spacer(Modifier.width(6.dp))
            IconBtn("send", "发送", size = 44.dp, tint = if (text.isBlank()) p.textWeak else p.accent) { send() }
        }
    }
}

@Composable
private fun BoardCard(m: BoardMsgDto, onDelete: () -> Unit) {
    val p = palette
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(13.dp)) {
        Row {
            Box(
                Modifier.size(32.dp).background(p.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(m.author.take(1).ifBlank { "?" }, fontSize = 13.sp, color = p.accent, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.author, fontSize = 12.5.sp, color = p.accent, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(7.dp))
                    Text(Dates.fmt(m.createdAt, "M月d日 HH:mm"), fontSize = 10.5.sp, color = p.textWeak)
                    Spacer(Modifier.weight(1f))
                    if (m.mine) IconBtn("trash-2", "删除", size = 24.dp, tint = p.textWeak, onClick = onDelete)
                }
                Spacer(Modifier.height(5.dp))
                Text(m.content, fontSize = 13.5.sp, color = p.text, lineHeight = 21.sp)
            }
        }
    }
}
