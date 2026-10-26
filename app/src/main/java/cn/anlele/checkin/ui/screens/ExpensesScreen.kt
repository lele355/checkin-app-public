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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.ExpenseDto
import cn.anlele.checkin.data.ExpensesPageDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.DateField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.GrowBar
import cn.anlele.checkin.ui.components.GroupHead
import cn.anlele.checkin.ui.components.HeadButton
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.NumIn
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.SiteRow
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.components.ViewSwitch
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

private val CATEGORIES = listOf("餐饮", "交通", "学习", "娱乐", "日用", "医疗", "其他")

/** 记账里的红绿：支出红、收入绿（与网页一致，别套股票那套涨红跌绿） */
private val OUT_RED = Color(0xFFC0392B)
private val IN_GREEN = Color(0xFF0F6E56)

/**
 * 记账 —— 与网页手机端一致：
 * 页头（月份翻页 + 列表/月度 + 记一笔）→ 支出/收入/结余 三栏 → 支出分类条（带金额与占比）
 * → 流水行（分类标签 + 标题/日期 + 红绿金额 + 编辑）。
 */
@Composable
fun ExpensesScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf("列表") }
    var ym by remember { mutableStateOf(Dates.thisMonth()) }
    var data by remember { mutableStateOf<ExpensesPageDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseDto?>(null) }

    suspend fun load() {
        loading = true
        try {
            data = Repo.expenses(ym)
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(ym) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 页头：月份 + 视图 + 记一笔 ----------
        item {
            PageHeadCard(
                title = "",
                titleSlot = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBtn("chevron-left", "上个月", size = 32.dp) { ym = Dates.addMonths(ym, -1) }
                        Text(
                            Dates.monthTitle(ym),
                            fontSize = 15.5.sp,
                            color = p.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        IconBtn("chevron-right", "下个月", size = 32.dp) { ym = Dates.addMonths(ym, 1) }
                    }
                },
                subRow = {
                    ViewSwitch(
                        options = listOf("list" to "列表", "month" to "月度"),
                        selected = view,
                        onSelect = { view = it },
                    )
                },
                actions = {
                    HeadButton("记一笔") { editing = null; editorOpen = true }
                },
            )
        }

        val d = data
        if (d == null) {
            if (loading) item { Text("正在加载…", fontSize = 12.5.sp, color = p.textWeak, modifier = Modifier.padding(8.dp)) }
            return@LazyColumn
        }

        if (view == "列表") {
            // ---------- 三栏汇总 ----------
            item {
                StaggerIn(0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SumTile("支出", d.summary.totalOut, OUT_RED, Modifier.weight(1f))
                        SumTile("收入", d.summary.totalIn, IN_GREEN, Modifier.weight(1f))
                        SumTile("结余", d.summary.totalIn - d.summary.totalOut, p.text, Modifier.weight(1f), prefix = "")
                    }
                }
            }

            // ---------- 分类条 ----------
            val cats = d.summary.byCategory
            if (cats.isNotEmpty()) {
                item {
                    StaggerIn(1) {
                        AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                Text("支出分类", fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                                val total = cats.sumOf { it.amount }.coerceAtLeast(0.01)
                                cats.forEach { c ->
                                    val pct = (c.amount / total * 100).toInt()
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(c.name, fontSize = 12.5.sp, color = p.text, modifier = Modifier.weight(1f))
                                            Text(
                                                "¥${Dates.money(c.amount)}　$pct%",
                                                fontSize = 11.5.sp,
                                                color = p.textSub,
                                            )
                                        }
                                        Spacer(Modifier.height(5.dp))
                                        GrowBar((c.amount / total).toFloat())
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 流水 ----------
            item {
                StaggerIn(2) {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                        Column {
                            GroupHead("${Dates.fmt("$ym-01", "M月")}流水 · 共 ${d.summary.count} 笔")
                            if (d.items.isEmpty()) {
                                SiteEmpty("💰", "这个月还没有账，点右上角记一笔。") {
                                    PrimaryButton("记一笔", icon = "plus") { editing = null; editorOpen = true }
                                }
                            } else {
                                d.items.forEachIndexed { i, e ->
                                    SiteRow(
                                        showTopBorder = i > 0,
                                        onClick = { editing = e; editorOpen = true },
                                        meta = {
                                            Box(
                                                Modifier.size(40.dp).background(p.cardSoft, chipShape()),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(e.category.take(1), fontSize = 13.sp, color = p.textSub)
                                            }
                                        },
                                        trailing = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    (if (e.type == "in") "+" else "-") + "¥" + Dates.money(e.amount),
                                                    fontSize = 14.sp,
                                                    color = if (e.type == "in") IN_GREEN else OUT_RED,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                TinyAction("编辑", "pencil") { editing = e; editorOpen = true }
                                            }
                                        },
                                    ) {
                                        Text(
                                            e.note.ifBlank { e.category }, fontSize = 14.sp,
                                            color = p.text, fontWeight = FontWeight.Medium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(Dates.relative(e.occurDate), fontSize = 11.5.sp, color = p.textWeak)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ---------- 月度视图：近 12 个月 ----------
            item {
                StaggerIn(0) {
                    AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                        Column {
                            GroupHead("近 12 个月")
                            d.months.reversed().forEachIndexed { i, m ->
                                SiteRow(showTopBorder = i > 0, onClick = { ym = m.month }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(m.label, fontSize = 13.5.sp, color = p.text, modifier = Modifier.weight(1f))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("支出 ¥${Dates.money(m.`out`)}", fontSize = 12.5.sp, color = OUT_RED)
                                            Text("收入 ¥${Dates.money(m.`in`)}", fontSize = 11.5.sp, color = IN_GREEN)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text("${m.count} 笔", fontSize = 11.sp, color = p.textWeak)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }

    if (editorOpen) {
        ExpenseEditor(vm, editing, onClose = { editorOpen = false }, onSaved = {
            editorOpen = false
            scope.launch { load() }
        })
    }
}

@Composable
private fun SumTile(label: String, value: Double, color: Color, modifier: Modifier = Modifier, prefix: String = "¥") {
    val p = palette
    AppCard(modifier, PaddingValues(horizontal = 12.dp, vertical = 11.dp)) {
        Column {
            Text(label, fontSize = 11.sp, color = p.textWeak)
            Spacer(Modifier.height(3.dp))
            NumIn("$prefix${Dates.money(value)}", 16.sp, color)
        }
    }
}

@Composable
private fun ExpenseEditor(vm: AppVm, initial: ExpenseDto?, onClose: () -> Unit, onSaved: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(initial?.let { Dates.money(it.amount) } ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "out") }
    var category by remember { mutableStateOf(initial?.category ?: "餐饮") }
    var date by remember { mutableStateOf(initial?.occurDate ?: Dates.today()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = if (initial == null) "记一笔" else "改这一笔",
        onClose = onClose,
        saving = saving,
        destructive = if (initial == null) null else ({
            SheetDelete {
                scope.launch {
                    runCatching { Repo.deleteExpense(initial.id) }
                    onSaved()
                }
            }
        }),
        onSave = {
            val v = amount.toDoubleOrNull()
            if (v == null || v <= 0) { vm.toast("金额要填个大于 0 的数"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    val req = Repo.ExpenseReq(v, type, category, date, note.trim())
                    if (initial == null) Repo.createExpense(req) else Repo.updateExpense(initial.id, req)
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
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AppChip("支出", selected = type == "out") { type = "out" }
            AppChip("收入", selected = type == "in") { type = "in" }
        }
        Spacer(Modifier.height(14.dp))
        AppField(
            "金额", amount, { amount = it }, prefix = "¥", placeholder = "0.00",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
        )
        Spacer(Modifier.height(14.dp))
        Text("分类", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CATEGORIES.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { c -> AppChip(c, selected = c == category) { category = c } }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DateField("日期", date, { date = it })
        Spacer(Modifier.height(12.dp))
        AppField("备注", note, { note = it }, placeholder = "买了什么（可不填）")
    }
}
