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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.AnnivDto
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/** 纪念日 —— 倒计时列表，支持每年重复（生日、在一起纪念日这类）。 */
@Composable
fun AnniversaryScreen(vm: AppVm) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<AnnivDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AnnivDto?>(null) }

    suspend fun load() {
        loading = true
        try {
            rows = Repo.anniversaries().items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    if (rows.isEmpty() && !loading) {
        EmptyState(icon = "heart", title = "还没有纪念日", hint = "记住重要的日子") {
            PrimaryButton("添加纪念日", icon = "plus") { editing = null; editorOpen = true }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),

        ) {

            item { PageHeadCard(title = "纪念日", hint = "重要的日子都有倒计时，每年重复的会自动滚到下一年。") }
            items(rows.size) { i ->
                val a = rows[i]
                AnnivCard(a) { editing = a; editorOpen = true }
            }
            item {
                PrimaryButton("添加纪念日", Modifier.fillMaxWidth(), icon = "plus") {
                    editing = null
                    editorOpen = true
                }
            }
        }
    }

    if (editorOpen) {
        AnnivEditor(vm, editing, onClose = { editorOpen = false }, onSaved = {
            editorOpen = false
            scope.launch { load() }
        })
    }
}

@Composable
private fun AnnivCard(a: AnnivDto, onClick: () -> Unit) {
    val p = palette
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(15.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(p.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons["heart"], null, Modifier.size(19.dp), tint = p.accent) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, fontSize = 15.sp, color = p.text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Dates.fmt(a.date, "yyyy年M月d日")}" +
                        if (a.repeatYearly) " · 每年" else "",
                    fontSize = 11.5.sp,
                    color = p.textWeak,
                )
                if (a.note.isNotBlank())
                    Text(a.note, fontSize = 11.5.sp, color = p.textSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        a.daysLeft == 0 -> "今天"
                        a.daysLeft > 0 -> "${a.daysLeft}"
                        else -> "${-a.daysLeft}"
                    },
                    fontSize = 22.sp,
                    color = p.accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        a.daysLeft == 0 -> "就是今天"
                        a.daysLeft > 0 -> "天后"
                        else -> "天前"
                    },
                    fontSize = 10.5.sp,
                    color = p.textWeak,
                )
            }
        }
    }
}

@Composable
private fun AnnivEditor(vm: AppVm, initial: AnnivDto?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: Dates.today()) }
    var repeat by remember { mutableStateOf(initial?.repeatYearly ?: true) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = if (initial == null) "添加纪念日" else "编辑纪念日",
        onClose = onClose,
        saving = saving,
        destructive = if (initial == null) null else ({
            SheetDelete { scope.launch { runCatching { Repo.deleteAnniv(initial.id) }; onSaved() } }
        }),
        onSave = {
            if (title.isBlank()) { vm.toast("名称不能为空"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    val req = Repo.AnnivReq(title.trim(), date, repeat, note.trim())
                    if (initial == null) Repo.createAnniv(req) else Repo.updateAnniv(initial.id, req)
                    onSaved()
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("保存失败")
                } finally {
                    saving = false
                }
            }
        },
    ) {
        AppField("名称", title, { title = it }, placeholder = "在一起纪念日 / 妈妈生日")
        Spacer(Modifier.height(12.dp))
        AppField("日期", date, { date = it }, placeholder = "2026-10-24", supporting = "格式：年-月-日")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AppChip("每年重复", selected = repeat) { repeat = true }
            AppChip("只算这一次", selected = !repeat) { repeat = false }
        }
        Spacer(Modifier.height(12.dp))
        AppField("备注", note, { note = it }, placeholder = "可不填")
    }
}
