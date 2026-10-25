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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.MemoDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.HeadButton
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.SiteRow
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.components.ViewSwitch
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/**
 * 备忘 —— 与网页手机端一致：默认列表（标题 + 摘要 + 时间 + 图钉 + 编辑），
 * 可切「卡片」视图（两列便签墙）；顶部带搜索。
 */
@Composable
fun MemoScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf("列表") }
    var rows by remember { mutableStateOf<List<MemoDto>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoDto?>(null) }

    suspend fun load() {
        loading = true
        try {
            rows = Repo.memos(q).items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(q) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeadCard(
                title = "备忘记事",
                hint = "置顶的便签会出现在工作台首页。",
                actions = {
                    ViewSwitch(
                        options = listOf("list" to "列表", "grid" to "卡片"),
                        selected = view,
                        onSelect = { view = it },
                    )
                    HeadButton("新建") { editing = null; editorOpen = true }
                },
            )
            Spacer(Modifier.height(10.dp))
            AppField("", q, { q = it }, placeholder = "搜索标题或正文…")
        }

        if (rows.isEmpty() && !loading) {
            item {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                    SiteEmpty("📝", if (q.isBlank()) "还没有便签，随手记下想到的事。" else "没搜到相关便签。") {
                        if (q.isBlank()) PrimaryButton("写第一条", icon = "plus") { editing = null; editorOpen = true }
                    }
                }
            }
        } else if (view == "列表") {
            item {
                StaggerIn(0) {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                        Column {
                            rows.forEachIndexed { i, m ->
                                SiteRow(
                                    showTopBorder = i > 0,
                                    onClick = { editing = m; editorOpen = true },
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (m.pinned)
                                                Icon(AppIcons["pin"], null, Modifier.size(13.dp), tint = p.accent)
                                            Spacer(Modifier.width(8.dp))
                                            TinyAction("编辑", "pencil") { editing = m; editorOpen = true }
                                        }
                                    },
                                ) {
                                    Text(
                                        m.title, fontSize = 14.5.sp, color = p.text,
                                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (m.content.isNotBlank()) {
                                        Text(
                                            m.content, fontSize = 12.5.sp, color = p.textSub,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            lineHeight = 19.sp,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    Text(
                                        Dates.fmt(m.updatedAt, "MM-dd HH:mm"),
                                        fontSize = 10.5.sp,
                                        color = p.textWeak,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                StaggerIn(0) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { m ->
                                    AppCard(
                                        Modifier.weight(1f).heightIn(min = 96.dp),
                                        PaddingValues(13.dp),
                                        onClick = { editing = m; editorOpen = true },
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (m.pinned) {
                                                    Icon(AppIcons["pin"], null, Modifier.size(11.dp), tint = p.accent)
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                Text(
                                                    m.title, fontSize = 13.5.sp, color = p.text,
                                                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (m.content.isNotBlank()) {
                                                Spacer(Modifier.height(5.dp))
                                                Text(
                                                    m.content, fontSize = 12.sp, color = p.textSub,
                                                    maxLines = 5, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
                                                )
                                            }
                                            Spacer(Modifier.height(7.dp))
                                            Text(Dates.fmt(m.updatedAt, "M月d日 HH:mm"), fontSize = 10.sp, color = p.textWeak)
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }

    if (editorOpen) {
        MemoEditor(vm, editing, onClose = { editorOpen = false }, onSaved = {
            editorOpen = false
            scope.launch { load() }
        })
    }
}

@Composable
private fun MemoEditor(vm: AppVm, initial: MemoDto?, onClose: () -> Unit, onSaved: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var pinned by remember { mutableStateOf(initial?.pinned ?: false) }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = if (initial == null) "新建便签" else "编辑便签",
        onClose = onClose,
        saving = saving,
        destructive = if (initial == null) null else ({
            SheetDelete {
                scope.launch {
                    runCatching { Repo.deleteMemo(initial.id) }
                    onSaved()
                }
            }
        }),
        onSave = {
            if (title.isBlank()) { vm.toast("标题不能为空"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    val req = Repo.MemoReq(title.trim(), content.trim(), pinned)
                    if (initial == null) Repo.createMemo(req) else Repo.updateMemo(initial.id, req)
                    onSaved()
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("保存失败，稍后再试")
                } finally {
                    saving = false
                }
            }
        },
    ) {
        AppField("标题", title, { title = it }, placeholder = "一句话说清")
        Spacer(Modifier.height(12.dp))
        AppField("内容", content, { content = it }, singleLine = false, minLines = 6, maxLines = 14, placeholder = "详细一点…")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.background(if (pinned) p.accentSoft else p.cardSoft, chipShape()).padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons["pin"], null, Modifier.size(13.dp), tint = if (pinned) p.accent else p.textWeak)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (pinned) "已置顶" else "置顶到首页",
                        fontSize = 12.5.sp,
                        color = if (pinned) p.accent else p.textSub,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            TinyAction(if (pinned) "取消置顶" else "置顶", null) { pinned = !pinned }
        }
    }
}
