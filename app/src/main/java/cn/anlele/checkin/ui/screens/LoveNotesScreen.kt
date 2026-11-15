package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.CouplePageDto
import cn.anlele.checkin.data.LoveNoteDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.theme.cardBackground
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * 小纸条 —— 情侣大类独立页（2026-11-02 站主定：从情侣空间摘出）。
 * 双方互见，只能删自己的。
 */
@Composable
fun LoveNotesScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<CouplePageDto?>(null) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    // 点纸条弹窗删除（2026-11-02 站主定：取消按钮，点纸条弹确认删除）
    var confirmDelete by remember { mutableStateOf<LoveNoteDto?>(null) }

    suspend fun load() {
        loading = true
        try {
            d = Repo.couple()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    fun send() {
        val t = text.trim()
        if (t.isEmpty()) return
        scope.launch {
            try {
                Repo.postNote(t)
                text = ""
                load()
            } catch (e: Exception) {
                vm.toast(e.message ?: "发送失败")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PageHeadCard(title = "小纸条", hint = "双方可见 · 只能删自己的") }
            if (d?.notes.isNullOrEmpty() && !loading) {
                item { Text("还没有小纸条，写一张给 TA 吧", fontSize = 12.5.sp, color = p.textWeak) }
            } else {
                val notes = d?.notes.orEmpty()
                items(notes.size) { i ->
                    val n = notes[i]
                    AppCard(
                        Modifier.fillMaxWidth(),
                        padding = PaddingValues(14.dp),
                        // 点纸条弹确认删除（只有自己的纸条可删）
                        onClick = { if (n.mine) confirmDelete = n },
                    ) {
                        Column {
                            Text("${n.from} · ${n.createdAt}", fontSize = 10.5.sp, color = p.textWeak)
                            Spacer(Modifier.height(4.dp))
                            Text(n.content, fontSize = 13.5.sp, color = p.text, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        // 输入栏
        Row(
            Modifier
                .fillMaxWidth()
                .cardBackground(p)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                AppField("", text, { text = it }, placeholder = "写张小纸条…", singleLine = false, minLines = 1, maxLines = 4)
            }
            Spacer(Modifier.width(6.dp))
            IconBtn("send", "发送", size = 44.dp, tint = if (text.isBlank()) p.textWeak else p.accent) { send() }
        }
    }

    // 删除确认弹窗（2026-11-02 站主定：点纸条弹窗删除，二次确认）
    confirmDelete?.let { n ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除小纸条") },
            text = { Text("确定删除这张小纸条吗？删除后不能恢复。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        runCatching { Repo.deleteNote(n.id) }
                        confirmDelete = null
                        load()
                    }
                }) { Text("删除", color = androidx.compose.ui.graphics.Color(0xFFC62828)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }
}
