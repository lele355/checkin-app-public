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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.HabitDto
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
import kotlinx.coroutines.launch

/**
 * 打卡 —— 与网页手机端一致：默认列表，每行「大 emoji + 名字 + 累计/连续 + 打卡按钮 + 编辑」，
 * 已打卡的换成「✓ 今日已打卡」；右上角可切「卡片」视图。
 */
@Composable
fun HabitsScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf("列表") }
    var rows by remember { mutableStateOf<List<HabitDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HabitDto?>(null) }
    val busy = remember { mutableStateMapOf<Int, Boolean>() }

    suspend fun load() {
        loading = true
        try {
            rows = Repo.habits().items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    fun toggle(h: HabitDto) {
        if (busy[h.id] == true) return
        busy[h.id] = true
        scope.launch {
            try {
                val stat = if (h.checkedToday) Repo.undoCheckin(h.id) else Repo.checkin(h.id)
                rows = rows.map {
                    if (it.id == h.id) it.copy(
                        checkedToday = stat.checked, streak = stat.streak,
                        total = stat.total, week = stat.week,
                    ) else it
                }
            } catch (e: ApiException) {
                vm.toast(e.message)
            } catch (e: Exception) {
                vm.toast("操作失败，稍后再试")
            } finally {
                busy[h.id] = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeadCard(
                title = "习惯打卡",
                hint = "每天点亮一个好习惯，连续天数就是你的坚持勋章。",
                actions = {
                    ViewSwitch(
                        options = listOf("list" to "列表", "grid" to "卡片"),
                        selected = view,
                        onSelect = { view = it },
                    )
                    HeadButton("新建习惯") { editing = null; editorOpen = true }
                },
            )
        }

        if (rows.isEmpty() && !loading) {
            item {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                    SiteEmpty("🌱", "还没有习惯，点右上角新建一个吧。") {
                        PrimaryButton("新建习惯", icon = "plus") { editing = null; editorOpen = true }
                    }
                }
            }
        } else if (view == "列表") {
            item {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                    Column {
                        rows.forEachIndexed { i, h ->
                            SiteRow(
                                showTopBorder = i > 0,
                                onClick = { editing = h; editorOpen = true },
                                meta = {
                                    Box(
                                        Modifier.size(46.dp).background(p.cardSoft, chipShape()),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(h.icon, fontSize = 22.sp) }
                                },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (h.checkedToday) {
                                            Row(
                                                Modifier
                                                    .background(p.cardSoft, chipShape())
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(AppIcons["check"], null, Modifier.size(12.dp), tint = p.accent)
                                                Spacer(Modifier.width(4.dp))
                                                Text("今日已打卡", fontSize = 11.5.sp, color = p.accent)
                                            }
                                        } else {
                                            Box(
                                                Modifier
                                                    .background(p.accent, chipShape())
                                                    .clickable { toggle(h) }
                                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                            ) { Text("打卡", fontSize = 12.5.sp, color = p.onAccent, fontWeight = FontWeight.Medium) }
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        TinyAction("编辑", "pencil") { editing = h; editorOpen = true }
                                    }
                                },
                            ) {
                                Text(
                                    h.name, fontSize = 15.sp, color = p.text,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (h.streak >= 2) {
                                        Icon(AppIcons["flame"], null, Modifier.size(11.dp), tint = p.accent)
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    Text(
                                        "累计 ${h.total} 天 · 连续 ${h.streak} 天",
                                        fontSize = 12.sp,
                                        color = p.textWeak,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ---------- 卡片视图：两列 ----------
            item {
                StaggerIn(0) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { h ->
                                    AppCard(
                                        Modifier.weight(1f).heightIn(min = 132.dp),
                                        PaddingValues(14.dp),
                                        onClick = { editing = h; editorOpen = true },
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Box(
                                                Modifier.size(44.dp).background(p.cardSoft, chipShape()),
                                                contentAlignment = Alignment.Center,
                                            ) { Text(h.icon, fontSize = 21.sp) }
                                            Spacer(Modifier.height(9.dp))
                                            Text(
                                                h.name, fontSize = 14.sp, color = p.text,
                                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text("累计 ${h.total} · 连续 ${h.streak}", fontSize = 11.sp, color = p.textWeak)
                                            Spacer(Modifier.height(8.dp))
                                            if (h.checkedToday) {
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .background(p.accentSoft, chipShape())
                                                        .padding(vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(AppIcons["check"], null, Modifier.size(11.dp), tint = p.accent)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("已打卡", fontSize = 11.5.sp, color = p.accent)
                                                }
                                            } else {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .background(p.accent, chipShape())
                                                        .clickable { toggle(h) }
                                                        .padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) { Text("打卡", fontSize = 11.5.sp, color = p.onAccent) }
                                            }
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
        HabitEditor(vm, editing, onClose = { editorOpen = false }, onSaved = {
            editorOpen = false
            scope.launch { load() }
        })
    }
}

@Composable
private fun HabitEditor(vm: AppVm, initial: HabitDto?, onClose: () -> Unit, onSaved: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "🌱") }
    var saving by remember { mutableStateOf(false) }
    val emojis = listOf("🌱", "📚", "🏃", "💧", "🧘", "💪", "🌙", "🎵", "✍️", "🍎", "☀️", "⏰")

    EditSheet(
        title = if (initial == null) "新建习惯" else "编辑习惯",
        onClose = onClose,
        saving = saving,
        destructive = if (initial == null) null else ({
            SheetDelete {
                scope.launch {
                    runCatching { Repo.deleteHabit(initial.id) }
                    onSaved()
                }
            }
        }),
        onSave = {
            if (name.isBlank()) { vm.toast("习惯名不能为空"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    val req = Repo.HabitReq(name.trim(), icon)
                    if (initial == null) Repo.createHabit(req) else Repo.updateHabit(initial.id, req)
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
        AppField("习惯名", name, { name = it }, placeholder = "比如：早起、背单词")
        Spacer(Modifier.height(14.dp))
        Text("图标", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            emojis.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { e ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(if (e == icon) p.accentSoft else p.cardSoft, chipShape())
                                .clickable { icon = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}
