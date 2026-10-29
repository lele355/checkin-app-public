package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import cn.anlele.checkin.data.PeriodPageDto
import cn.anlele.checkin.data.PeriodRecordDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.DateField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/** 经期助手 —— 记录 + 周期预测（预测算法与提醒一致：近 3 次平均）。 */
@Composable
fun PeriodScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<PeriodPageDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PeriodRecordDto?>(null) }

    suspend fun load() {
        loading = true
        try {
            d = Repo.period()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val data = d
    if (data == null) {
        if (loading) Text("正在加载…", fontSize = 12.5.sp, color = p.textWeak, modifier = Modifier.padding(20.dp))
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),

        ) {

            item { PageHeadCard(title = "经期助手", hint = "仅自己可见") }
            data.predict?.let { pr ->
                item {
                    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                        Column {
                            Text("预计下次", fontSize = 12.sp, color = p.textWeak)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    if (pr.daysLeft >= 0) "${pr.daysLeft}" else "已过 ${-pr.daysLeft}",
                                    fontSize = 30.sp,
                                    color = p.accent,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(0.dp))
                                Text(" 天", fontSize = 13.sp, color = p.textSub, modifier = Modifier.padding(bottom = 5.dp))
                            }
                            Text(
                                "预计 ${Dates.dayMonth(pr.nextDate)} · 平均周期 ${pr.avgCycle} 天 · 平均经期 ${pr.avgLength} 天",
                                fontSize = 11.5.sp,
                                color = p.textWeak,
                            )
                        }
                    }
                }
            }

            if (data.items.isEmpty()) {
                item {
                    EmptyState(icon = "drop", title = "还没有记录") {
                        PrimaryButton("记录一次", icon = "plus") { editing = null; editorOpen = true }
                    }
                }
            } else {
                item {
                    Text("历史记录", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                }
                items(data.items.size) { i ->
                    val r = data.items[i]
                    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), onClick = {
                        editing = r
                        editorOpen = true
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Dates.dayMonth(r.startDate) + (r.endDate?.let { " ~ ${Dates.dayMonth(it)}" } ?: " 起"),
                                    fontSize = 14.sp,
                                    color = p.text,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (r.note.isNotBlank())
                                    Text(r.note, fontSize = 11.5.sp, color = p.textWeak)
                            }
                            Text(
                                r.length?.let { "持续 $it 天" } ?: "进行中",
                                fontSize = 12.sp,
                                color = if (r.length == null) p.accent else p.textSub,
                                modifier = Modifier
                                    .background(if (r.length == null) p.accentSoft else androidx.compose.ui.graphics.Color.Transparent, chipShape())
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                item {
                    PrimaryButton("记录一次", Modifier.fillMaxWidth(), icon = "plus") {
                        editing = null
                        editorOpen = true
                    }
                }
            }
        }
    }

    if (editorOpen) {
        PeriodEditor(vm, editing, onClose = { editorOpen = false }, onSaved = {
            editorOpen = false
            scope.launch { load() }
        })
    }
}

@Composable
private fun PeriodEditor(vm: AppVm, initial: PeriodRecordDto?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(initial?.startDate ?: Dates.today()) }
    var end by remember { mutableStateOf(initial?.endDate ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = if (initial == null) "记录一次" else "编辑记录",
        onClose = onClose,
        saving = saving,
        destructive = if (initial == null) null else ({
            SheetDelete { scope.launch { runCatching { Repo.deletePeriod(initial.id) }; onSaved() } }
        }),
        onSave = {
            saving = true
            scope.launch {
                try {
                    val req = Repo.PeriodReq(start, end.ifBlank { null }, note.trim())
                    if (initial == null) Repo.createPeriod(req) else Repo.updatePeriod(initial.id, req)
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
        DateField("开始日期", start, { start = it })
        Spacer(Modifier.height(12.dp))
        DateField("结束日期（可后补）", end, { end = it }, placeholder = "还在进行", clearable = true)
        Spacer(Modifier.height(12.dp))
        AppField("备注", note, { note = it }, placeholder = "可不填")
    }
}
