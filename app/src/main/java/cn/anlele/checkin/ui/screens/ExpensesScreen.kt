package cn.anlele.checkin.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.ExpenseDto
import cn.anlele.checkin.data.ExpenseGroupDto
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
import cn.anlele.checkin.ui.components.PickerSheetShell
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.components.SiteEmpty
import cn.anlele.checkin.ui.components.SiteRow
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.components.TimeField
import cn.anlele.checkin.ui.components.ViewSwitch
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 服务端不返回分类时的兜底（老版本后端 / 离线快照）。
 * 正常情况下这份列表来自 `User.ExpenseCategories`，用户可以在「管理分类」里改成自己的。
 */
private val DEFAULT_CATEGORIES = listOf("餐饮", "交通", "学习", "娱乐", "日用", "医疗", "其他")

/** 与后端 ExpenseCategoryCatalog 保持一致：最多 24 个、单个 8 字 */
private const val MAX_CATEGORIES = 24
private const val MAX_CATEGORY_LEN = 8

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
    // 分类跟着服务端走：换设备、换网页端都一致。加载失败时保留当前值，不会把选择器清空。
    var categories by remember { mutableStateOf(DEFAULT_CATEGORIES) }

    // 搜索：跨月按备注/分类匹配（与网页 ?q= 同口径）。搜索态下月份翻页与月度视图让位。
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchData by remember { mutableStateOf<ExpensesPageDto?>(null) }

    // ---------- 自定义时间范围（默认空 = 按月，行为与以前完全一致） ----------
    var rangeFrom by remember { mutableStateOf("") }
    var rangeTo by remember { mutableStateOf("") }
    var filterOpen by remember { mutableStateOf(false) }
    val isRange = rangeFrom.isNotBlank() && rangeTo.isNotBlank()

    // ---------- 整理（多选合并）----------
    // 长按任意一笔进入：只有未分组的那几笔能勾，勾够 2 笔就能合并成一个条目。
    var picking by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var mergeOpen by remember { mutableStateOf(false) }
    /** 正在管理的条目（长按组行打开：改名 / 加几笔 / 移出 / 解散） */
    var managing by remember { mutableStateOf<ExpenseGroupDto?>(null) }
    /** 追加模式：非 null 表示这次勾选是为了「加进这个条目」，而不是合并成新条目 */
    var pickTarget by remember { mutableStateOf<ExpenseGroupDto?>(null) }
    /** 已展开的条目 id。切月 / 搜索 / 刷新后清空，避免出现「凭空打开的组」 */
    var openGroups by remember { mutableStateOf(setOf<Int>()) }

    suspend fun load() {
        loading = true
        try {
            val q = query.trim()
            if (q.isEmpty()) {
                // 有范围按范围取，没有按月取（默认不变）
                val r = if (rangeFrom.isNotBlank() && rangeTo.isNotBlank())
                    Repo.expenses(from = rangeFrom, to = rangeTo)
                else
                    Repo.expenses(ym)
                data = r
                searchData = null
                if (r.categories.isNotEmpty()) categories = r.categories
            } else {
                searchData = Repo.expenses(ym, q)
            }
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(ym, query, rangeFrom, rangeTo) {
        if (query.isNotBlank()) delay(280)   // 打字防抖，别每个字符打一次接口
        // 换月/换搜索条件/换范围 = 换一批数据，选择与展开状态一起收掉
        picking = false
        picked = emptySet()
        pickTarget = null
        openGroups = emptySet()
        load()
    }

    // 底部操作条要显示「已选 N 笔 / 合计」，数据在外层先取好（LazyColumn 里拿不到）
    val shown = if (query.isNotBlank()) searchData else data
    val pickedTotal = shown?.items?.filter { picked.contains(it.id) }
        ?.sumOf { if (it.type == "in") -it.amount else it.amount } ?: 0.0
    // 合并弹层的预填名：取第一笔的备注（没备注就用分类），用户可改
    val pickSuggest = shown?.items?.filter { picked.contains(it.id) }?.firstOrNull()
        ?.let { it.note.ifBlank { it.category } } ?: ""

    /**
     * 整理态的提交：目标为空 = 合并成新条目（弹命名层）；有目标 = 把这批账加进那个条目。
     * 两条路都用同一套勾选 UI，用户不用学两遍。
     */
    fun submitPick() {
        if (picked.size < 2) { vm.toast("至少选 2 笔"); return }
        val target = pickTarget
        if (target == null) { mergeOpen = true; return }
        scope.launch {
            try {
                Repo.addToExpenseGroup(target.id, picked.toList())
                vm.toast("已加入「${target.name}」")
                picking = false
                picked = emptySet()
                pickTarget = null
                load()
            } catch (e: ApiException) {
                vm.toast(e.message)
            } catch (e: Exception) {
                vm.toast("加入失败，稍后再试")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                    if (query.isNotBlank()) {
                        Text(
                            "搜索「$query」",
                            fontSize = 15.5.sp,
                            color = p.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (isRange) {
                        // 自定义范围模式：标题就是起止日期，翻月箭头让位（范围和月份是两套语义）
                        Text(
                            Dates.rangeTitle(rangeFrom, rangeTo),
                            fontSize = 15.5.sp,
                            color = p.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
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
                    }
                },
                subRow = {
                    if (query.isBlank()) {
                        ViewSwitch(
                            options = listOf("list" to "列表", "month" to "月度"),
                            selected = view,
                            onSelect = { view = it },
                        )
                    }
                },
                actions = {
                    if (picking) {
                        // 整理态：页头只留「合并/加入 + 取消」，搜索和记一笔暂时让位，避免误触
                        HeadButton(if (pickTarget != null) "加入" else "合并", icon = "check") { submitPick() }
                        Spacer(Modifier.width(6.dp))
                        IconBtn("x", "取消") { picking = false; picked = emptySet(); pickTarget = null }
                    } else {
                        // 范围筛选只对「列表」有意义（月度视图是近 12 个月汇总，不吃范围）
                        if (view == "列表" && query.isBlank()) {
                            IconBtn("calendar", "时间范围", size = 34.dp) { filterOpen = true }
                            Spacer(Modifier.width(6.dp))
                        }
                        IconBtn("search", "搜索", size = 34.dp) {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        }
                        Spacer(Modifier.width(6.dp))
                        HeadButton("记一笔") { editing = null; editorOpen = true }
                    }
                },
            )
        }

        // ---------- 搜索行 ----------
        if (searchOpen || query.isNotBlank()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        AppField(
                            label = "",
                            value = query,
                            onValueChange = { query = it.take(50) },
                            placeholder = "搜备注或分类",
                            singleLine = true,
                        )
                    }
                    if (query.isNotBlank()) SecondaryButton("清除") { query = "" }
                }
            }
        }

        val d = if (query.isNotBlank()) searchData else data
        if (d == null) {
            if (loading) item { Text("正在加载…", fontSize = 12.5.sp, color = p.textWeak, modifier = Modifier.padding(8.dp)) }
            return@LazyColumn
        }

        if (view == "列表" || query.isNotBlank()) {
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
                            GroupHead(
                                if (query.isNotBlank()) "搜到 ${d.summary.count} 笔"
                                else "${Dates.fmt("$ym-01", "M月")}流水 · 共 ${d.summary.count} 笔"
                            )
                            if (d.items.isEmpty()) {
                                SiteEmpty("💰", if (query.isNotBlank()) "没有匹配的账" else "这个月还没有账") {
                                    PrimaryButton("记一笔", icon = "plus") { editing = null; editorOpen = true }
                                }
                            } else {
                                // 条目（合并容器）在列表里就是一个「组行」，展开才看到里面那几笔
                                buildExpenseLines(d, openGroups).forEachIndexed { i, line ->
                                    when (line) {
                                        is ExpenseLine.Single -> ExpenseSingleRow(
                                            e = line.e,
                                            showTopBorder = i > 0,
                                            picking = picking,
                                            selected = picked.contains(line.e.id),
                                            onOpen = { editing = line.e; editorOpen = true },
                                            onPick = {
                                                picked = if (picked.contains(line.e.id)) picked - line.e.id
                                                        else picked + line.e.id
                                            },
                                            // 长按 = 进入整理态，并顺手把这笔勾上（少一次点击）
                                            onLongPress = { picking = true; picked = setOf(line.e.id) },
                                        )
                                        is ExpenseLine.Group -> ExpenseGroupRow(
                                            g = line.g,
                                            open = openGroups.contains(line.g.id),
                                            showTopBorder = i > 0,
                                            dim = picking,
                                            onToggle = {
                                                if (!picking) openGroups =
                                                    if (openGroups.contains(line.g.id)) openGroups - line.g.id
                                                    else openGroups + line.g.id
                                            },
                                            onManage = { managing = line.g },
                                        )
                                        is ExpenseLine.Child -> ExpenseChildRow(
                                            e = line.e,
                                            showTopBorder = true,
                                        ) { editing = line.e; editorOpen = true }
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

        // ---------- 整理条：勾够 2 笔才亮「合并」 ----------
        if (picking) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.card)
                    .border(1.dp, p.borderStrong.copy(alpha = .45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val target = pickTarget
                    Text(
                        if (target != null) "加入「${target.name}」· 已选 ${picked.size} 笔"
                        else "已选 ${picked.size} 笔",
                        fontSize = 13.sp, color = p.text, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (picked.isEmpty()) "再点几下勾选要合并的账"
                        else "合计 ¥${Dates.money(kotlin.math.abs(pickedTotal))}",
                        fontSize = 11.sp, color = p.textWeak,
                    )
                }
                SecondaryButton("取消") { picking = false; picked = emptySet(); pickTarget = null }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    if (pickTarget != null) "加入" else "合并",
                    enabled = picked.size >= 2,
                ) { submitPick() }
            }
        }
    }

    if (editorOpen) {
        ExpenseEditor(
            vm, editing,
            categories = categories,
            onCategoriesChange = { categories = it },
            onClose = { editorOpen = false },
            onSaved = {
                editorOpen = false
                scope.launch { load() }
            },
        )
    }

    // ---------- 合并命名：勾了几笔 → 起个名字收进一个条目 ----------
    if (mergeOpen) {
        MergeSheet(
            count = picked.size,
            total = pickedTotal,
            suggest = pickSuggest,
            onClose = { mergeOpen = false },
            onSave = { name ->
                scope.launch {
                    try {
                        Repo.createExpenseGroup(name, picked.toList())
                        mergeOpen = false
                        picking = false
                        picked = emptySet()
                        vm.toast("已合并成条目「$name」")
                        load()
                    } catch (e: ApiException) {
                        vm.toast(e.message)
                    } catch (e: Exception) {
                        vm.toast("合并失败，稍后再试")
                    }
                }
            },
        )
    }

    // ---------- 时间范围筛选（默认空 = 按月） ----------
    if (filterOpen) {
        RangeSheet(
            from = rangeFrom,
            to = rangeTo,
            onClose = { filterOpen = false },
            onApply = { f, t ->
                rangeFrom = f
                rangeTo = t
                view = "列表"        // 范围只对流水有意义，定了范围就回到列表
                filterOpen = false
            },
            onClear = {
                rangeFrom = ""
                rangeTo = ""
                filterOpen = false
            },
        )
    }

    // ---------- 条目管理：长按组行打开（改名 / 加几笔 / 移出 / 解散）----------
    managing?.let { g ->
        GroupSheet(
            g = g,
            vm = vm,
            onClose = { managing = null },
            onChanged = { scope.launch { load() } },
            onAddMore = { gg ->
                // 复用整理态的勾选 UI：这次的目标不是「合并」而是「加进这个条目」
                managing = null
                pickTarget = gg
                picked = emptySet()
                picking = true
            },
        )
    }
}

// ==================== 列表行：普通一笔 / 条目 / 条目里的一笔 ====================

/** 列表里的一行：普通一笔、条目（合并容器）、条目展开后的明细 */
private sealed interface ExpenseLine {
    data class Single(val e: ExpenseDto) : ExpenseLine
    data class Group(val g: ExpenseGroupDto) : ExpenseLine
    data class Child(val e: ExpenseDto) : ExpenseLine
}

/**
 * 把服务端的两份数据（扁平 items + 当月 groups）拼成一条有序的行序列。
 *
 * 排序统一按「该行最新的发生时间」—— 条目没有自己的时间，用组内最新那笔顶上，
 * 于是它在列表里的位置和里面最新一笔一致，不会凭空跳到别的日期去。
 * 搜索态服务端不给 groups，这里自然退化成平平的一列明细。
 */
private fun buildExpenseLines(d: ExpensesPageDto, open: Set<Int>): List<ExpenseLine> {
    if (d.groups.isEmpty()) return d.items.map { ExpenseLine.Single(it) }

    val groups = d.groups.associateBy { it.id }
    val keyed = ArrayList<Pair<String, ExpenseLine>>(d.items.size + d.groups.size)
    d.items.forEach { e ->
        // 组内明细已经在 groups[].items 里，这里只留「未分组」和「组已不存在」的孤儿
        if (e.groupId == null || !groups.containsKey(e.groupId)) {
            keyed += sortKey(e.occurDate, e.id) to ExpenseLine.Single(e)
        }
    }
    d.groups.forEach { g -> keyed += sortKey(g.occurDate, g.id) to ExpenseLine.Group(g) }

    return keyed.sortedByDescending { it.first }.flatMap { (_, line) ->
        if (line is ExpenseLine.Group && open.contains(line.g.id)) {
            listOf(line) + line.g.items.map { ExpenseLine.Child(it) }
        } else {
            listOf(line)
        }
    }
}

/** ISO 时间字符串 + 补零 Id：同一天里也按「后记的在上」排 */
private fun sortKey(whenText: String, id: Int) = whenText + "|" + id.toString().padStart(9, '0')

/** 普通一笔（整理态下左侧变成勾选圈，点行 = 勾选） */
@Composable
private fun ExpenseSingleRow(
    e: ExpenseDto,
    showTopBorder: Boolean,
    picking: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onPick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val p = palette
    SiteRow(
        showTopBorder = showTopBorder,
        onClick = if (picking) onPick else onOpen,
        onLongClick = if (picking) null else onLongPress,
        meta = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (picking) {
                    PickMark(selected)
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    Modifier.size(40.dp).background(p.cardSoft, chipShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e.category.take(1), fontSize = 13.sp, color = p.textSub)
                }
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
                if (!picking) {
                    Spacer(Modifier.width(6.dp))
                    TinyAction("编辑", "pencil") { onOpen() }
                }
            }
        },
    ) {
        Text(
            e.note.ifBlank { e.category }, fontSize = 14.sp,
            color = p.text, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(Dates.expenseWhen(e.occurDate), fontSize = 11.5.sp, color = p.textWeak)
    }
}

/**
 * 条目行：和普通一笔同一个骨架（行高一致），区别只有三处 ——
 * ① 行首是「多条」图标而不是分类字；② 副标题带笔数；③ 行尾是折叠箭头而不是「编辑」。
 * 点 = 展开/收起，长按 = 管理（改名 / 解散）。
 */
@Composable
private fun ExpenseGroupRow(
    g: ExpenseGroupDto,
    open: Boolean,
    showTopBorder: Boolean,
    dim: Boolean,
    onToggle: () -> Unit,
    onManage: () -> Unit,
) {
    val p = palette
    val rotate by animateFloatAsState(if (open) 180f else 0f, label = "group-caret")
    SiteRow(
        showTopBorder = showTopBorder,
        // 整理态下条目不能被选中（它已经是一组账了），置灰且不响应
        onClick = if (dim) null else onToggle,
        onLongClick = if (dim) null else onManage,
        meta = {
            Box(
                Modifier.size(40.dp).background(p.accentSoft, chipShape()).alpha(if (dim) .4f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons["list"], null, Modifier.size(18.dp), tint = p.accentStrong)
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    if (g.totalOut > 0) {
                        Text(
                            "-¥" + Dates.money(g.totalOut),
                            fontSize = 14.sp, color = OUT_RED, fontWeight = FontWeight.Medium,
                        )
                    }
                    if (g.totalIn > 0) {
                        Text(
                            "+¥" + Dates.money(g.totalIn),
                            fontSize = 14.sp, color = IN_GREEN, fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    AppIcons["chevron-down"], null,
                    Modifier.size(15.dp).graphicsLayer { rotationZ = rotate },
                    tint = p.textWeak,
                )
            }
        },
    ) {
        Text(
            g.name, fontSize = 14.sp, color = if (dim) p.textWeak else p.text,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val extra = if (g.otherCount > 0) " · 其它月份 ${g.otherCount} 笔" else ""
        Text(
            "${g.count} 笔 · 最近 ${Dates.expenseWhen(g.occurDate)}$extra",
            fontSize = 11.5.sp, color = p.textWeak,
        )
    }
}

/** 条目展开后的一笔：缩进一级、字号小一档，点进去还是原来那个编辑器 */
@Composable
private fun ExpenseChildRow(e: ExpenseDto, showTopBorder: Boolean, onOpen: () -> Unit) {
    val p = palette
    Column {
        if (showTopBorder) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (p.border == Color.Transparent) p.borderStrong.copy(alpha = .14f) else p.border)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 46.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp).background(p.card, chipShape()),
                contentAlignment = Alignment.Center,
            ) {
                Text(e.category.take(1), fontSize = 11.sp, color = p.textWeak)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.note.ifBlank { e.category }, fontSize = 13.5.sp, color = p.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(Dates.expenseWhen(e.occurDate), fontSize = 11.sp, color = p.textWeak)
            }
            Text(
                (if (e.type == "in") "+" else "-") + "¥" + Dates.money(e.amount),
                fontSize = 13.5.sp,
                color = if (e.type == "in") IN_GREEN else OUT_RED,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            TinyAction("编辑", "pencil") { onOpen() }
        }
    }
}

/** 整理态的勾选圈（不引 Material Checkbox，尺寸和配色跟着主题走） */
@Composable
private fun PickMark(on: Boolean) {
    val p = palette
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (on) p.accent else Color.Transparent)
            .border(1.5.dp, if (on) p.accent else p.borderStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(AppIcons["check"], null, Modifier.size(11.dp), tint = p.onAccent)
    }
}

/** 时间范围筛选：快捷范围 + 自定义起止（复用 DatePickerSheet），空着不生效 */
@Composable
private fun RangeSheet(
    from: String,
    to: String,
    onClose: () -> Unit,
    onApply: (String, String) -> Unit,
    onClear: () -> Unit,
) {
    val p = palette
    var f by remember { mutableStateOf(from) }
    var t by remember { mutableStateOf(to) }

    fun pick(ym: String) { val b = Dates.monthBounds(ym); f = b.first; t = b.second }

    EditSheet(
        title = "时间范围",
        onClose = onClose,
        saveText = "应用",
        onSave = {
            if (f.isNotBlank() && t.isNotBlank() && f <= t) onApply(f, t)
        },
    ) {
        Text("快捷", fontSize = 12.sp, color = p.textWeak)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("本月") { pick(Dates.thisMonth()) }
            AppChip("上月") { pick(Dates.addMonths(Dates.thisMonth(), -1)) }
            AppChip("近 7 天") { f = Dates.addDays(Dates.today(), -6); t = Dates.today() }
            AppChip("近 30 天") { f = Dates.addDays(Dates.today(), -29); t = Dates.today() }
        }
        Spacer(Modifier.height(16.dp))
        DateField("开始日期", f, { f = it }, clearable = true)
        Spacer(Modifier.height(12.dp))
        DateField("结束日期", t, { t = it }, clearable = true)
        Spacer(Modifier.height(16.dp))
        SecondaryButton("回到本月", Modifier.fillMaxWidth()) { onClear() }
        Spacer(Modifier.height(6.dp))
        Text(
            "不选就是按月看；日期都是「含当天」。",
            fontSize = 11.5.sp, color = p.textWeak, lineHeight = 17.sp,
        )
    }
}

/** 合并命名弹层：预填一个建议名（第一条的备注 / 分类），可改 */
@Composable
private fun MergeSheet(
    count: Int,
    total: Double,
    suggest: String,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(suggest.take(20)) }
    EditSheet(
        title = "合并成条目",
        onClose = onClose,
        saveText = "合并",
        onSave = {
            val v = name.trim()
            if (v.isEmpty()) return@EditSheet
            onSave(v)
        },
    ) {
        Text(
            "把选中的 $count 笔收进一个条目，合计 ¥${Dates.money(kotlin.math.abs(total))}。",
            fontSize = 12.5.sp, color = palette.textWeak, lineHeight = 19.sp,
        )
        Spacer(Modifier.height(12.dp))
        AppField("条目名", name, { name = it.take(20) }, placeholder = "如：超市购物、早饭")
        Spacer(Modifier.height(10.dp))
        Text(
            "条目只是把这几天同类的小账收在一起，每笔账自己的分类、时间、金额都不变。",
            fontSize = 11.5.sp, color = palette.textWeak, lineHeight = 17.sp,
        )
    }
}

/**
 * 条目管理弹层：改名 + 明细列表（每条可移出）+ 加几笔进来 + 解散。
 *
 * 明细走 `GET /expenses/groups/{id}` 拉**全量**（不限当月）—— 只在列表里看到「本月 2 笔」时，
 * 用户想移出的可能是上个月那笔，所以这里的清单必须比列表行里的更全。
 */
@Composable
private fun GroupSheet(
    g: ExpenseGroupDto,
    vm: AppVm,
    onClose: () -> Unit,
    onChanged: () -> Unit,
    onAddMore: (ExpenseGroupDto) -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(g.name) }
    var detail by remember { mutableStateOf<Repo.ExpenseGroupDetailDto?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        runCatching { Repo.expenseGroupDetail(g.id) }.onSuccess { detail = it }
    }
    LaunchedEffect(g.id) { reload() }

    val count = detail?.count ?: g.count
    val out = detail?.totalOut ?: g.totalOut
    val inSum = detail?.totalIn ?: g.totalIn

    EditSheet(
        title = "条目「${g.name}」",
        onClose = onClose,
        saving = busy,
        onSave = {
            val v = name.trim()
            if (v.isEmpty()) return@EditSheet
            scope.launch {
                busy = true
                try {
                    Repo.renameExpenseGroup(g.id, v)
                    onChanged()
                    onClose()
                    vm.toast("条目已改名")
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("改名失败，稍后再试")
                } finally {
                    busy = false
                }
            }
        },
        destructive = {
            SheetDelete {
                scope.launch {
                    try {
                        Repo.dissolveExpenseGroup(g.id)
                        onChanged()
                        onClose()
                        vm.toast("条目已解散，账都还在")
                    } catch (e: ApiException) {
                        vm.toast(e.message)
                    } catch (e: Exception) {
                        vm.toast("解散失败，稍后再试")
                    }
                }
            }
        },
    ) {
        Text(
            "$count 笔 · 支出 ¥${Dates.money(out)}" + if (inSum > 0) " / 收入 ¥${Dates.money(inSum)}" else "",
            fontSize = 12.5.sp, color = p.textWeak,
        )
        Spacer(Modifier.height(12.dp))
        AppField("条目名", name, { name = it.take(20) })
        Spacer(Modifier.height(12.dp))
        SecondaryButton("加几笔进来", icon = "plus", modifier = Modifier.fillMaxWidth()) { onAddMore(g) }

        if (count > 0) {
            Spacer(Modifier.height(16.dp))
            Text("里面的账", fontSize = 12.sp, color = p.textWeak)
            Spacer(Modifier.height(6.dp))
            detail?.items?.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(28.dp).background(p.cardSoft, chipShape()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e.category.take(1), fontSize = 11.sp, color = p.textSub)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.note.ifBlank { e.category }, fontSize = 13.sp, color = p.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(Dates.expenseWhen(e.occurDate), fontSize = 11.sp, color = p.textWeak)
                    }
                    Text(
                        (if (e.type == "in") "+" else "-") + "¥" + Dates.money(e.amount),
                        fontSize = 13.sp,
                        color = if (e.type == "in") IN_GREEN else OUT_RED,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(4.dp))
                    TinyAction("移出", "x") {
                        scope.launch {
                            try {
                                Repo.removeFromExpenseGroup(g.id, listOf(e.id))
                                reload()
                                onChanged()
                                vm.toast("已移出这一笔")
                            } catch (ex: ApiException) {
                                vm.toast(ex.message)
                            } catch (ex: Exception) {
                                vm.toast("移出失败，稍后再试")
                            }
                        }
                    }
                }
            }
            if (detail == null) {
                Text("正在加载…", fontSize = 11.5.sp, color = p.textWeak)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "解散只是把这组账拆回单笔，一笔都不会丢。",
            fontSize = 11.5.sp, color = p.textWeak, lineHeight = 17.sp,
        )
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
private fun ExpenseEditor(
    vm: AppVm,
    initial: ExpenseDto?,
    categories: List<String>,
    onCategoriesChange: (List<String>) -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(initial?.let { Dates.money(it.amount) } ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "out") }
    var category by remember { mutableStateOf(initial?.category ?: categories.firstOrNull() ?: "其他") }
    // 发生时间精确到分钟：日期和时刻分开选（与网页 datetime-local 同一档精度）。
    // 老账只有日期 → 时刻留空；新记一笔默认「此刻」。
    var date by remember { mutableStateOf(initial?.occurDate?.take(10) ?: Dates.today()) }
    var time by remember { mutableStateOf(if (initial == null) Dates.nowHm() else Dates.minuteOf(initial.occurDate)) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var saving by remember { mutableStateOf(false) }
    var manageOpen by remember { mutableStateOf(false) }

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
                    // 选了时刻 → yyyy-MM-ddTHH:mm（精确到分钟）；没选 → 只发日期，
                    // 服务端按 00:00 存，等于「这一笔只知道是哪天」，与老数据同口径
                    val occur = if (time.isBlank()) date else "${date}T$time"
                    val req = Repo.ExpenseReq(v, type, category, occur, note.trim())
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
        // 分类标题行带「管理分类」入口：加分类/删分类是低频动作，
        // 但必须就在选择的地方，不能让用户去设置页里翻。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("分类", fontSize = 12.5.sp, color = p.textSub, modifier = Modifier.weight(1f))
            TinyAction("管理分类", "settings-2") { manageOpen = true }
        }
        Spacer(Modifier.height(8.dp))
        if (manageOpen) {
            CategoryManageSheet(
                initial = categories,
                onDismiss = { manageOpen = false },
                onSaved = { list ->
                    manageOpen = false
                    onCategoriesChange(list)
                    // 保存成功的可感知反馈（与网页分类页保存后的提示对齐，0.1）
                    vm.toast("分类已保存")
                    // 当前选中的分类如果被删掉了，落到列表第一个，别让 chip 全部不高亮
                    if (category !in list) category = list.firstOrNull() ?: "其他"
                },
            )
        }
        ExpenseCategoryChips(
            categories = categories,
            selected = category,
            onSelect = { category = it },
        )
        Spacer(Modifier.height(14.dp))
        // 日期独占一行（"2026年11月15日 周日" 挤在半行里会被截断，与日程页排版保持一致）
        DateField("日期", date, { date = it })
        Spacer(Modifier.height(12.dp))
        TimeField("时间", time, { time = it })
        Spacer(Modifier.height(12.dp))
        AppField("备注", note, { note = it }, placeholder = "买了什么（可不填）")
    }
}

/**
 * 分类 chip 区。用 FlowRow 自动换行 —— 分类是用户自定义的，数量不定，
 * 以前 `chunked(4)` 固定四列，多出来的分类会被挤成一行超宽或者行数爆掉。
 *
 * 另外：编辑一笔老账时，它的分类可能已经从「分类管理」里被删掉了。
 * 这种分类也照显示（排在最后）—— 否则打开编辑页看不到「这一笔现在是什么分类」，
 * 顺手保存就等于把它改成了别的分类。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseCategoryChips(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val shown = if (selected.isNotBlank() && selected !in categories) categories + selected else categories
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        shown.forEach { c -> AppChip(c, selected = c == selected) { onSelect(c) } }
    }
}

/**
 * 分类管理弹层（自定义分类的增删）。
 *
 * 形态用 [PickerSheetShell]（Dialog）而不是 ModalBottomSheet：它是在 EditSheet
 * 上面弹出来的，两个 ModalBottomSheet 相叠在各家 ROM 上会互相压住。
 *
 * 【草稿语义】改动只落在本地 [draft]，点「保存」才 PUT 到服务端；
 * 点空白/返回 = 丢弃。所以删分类不做二次确认 —— 有「恢复默认」兜底，
 * 而且删掉的分类不会影响已有账目（它们仍挂在原来的名字下）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryManageSheet(
    initial: List<String>,
    onDismiss: () -> Unit,
    onSaved: (List<String>) -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(initial) }
    var input by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun add() {
        val v = input.trim()
        when {
            v.isEmpty() -> return
            draft.size >= MAX_CATEGORIES -> { error = "最多 $MAX_CATEGORIES 个分类"; return }
            draft.any { it.equals(v, ignoreCase = true) } -> { error = "「$v」已经在列表里了"; return }
        }
        draft = draft + v
        input = ""
        error = null
    }

    PickerSheetShell(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text("管理分类", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "点一个分类就把它删掉（不影响已有账目，那些账还挂在原来的名字下）。保存后网页端也会跟着变。",
                fontSize = 12.sp, color = p.textWeak, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                draft.forEach { c ->
                    Row(
                        Modifier
                            .clip(chipShape())
                            .background(p.cardSoft)
                            .clickable {
                                if (draft.size <= 1) error = "至少留一个分类，不然记一笔时没得选"
                                else { draft = draft - c; error = null }
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c, fontSize = 12.sp, color = p.textSub, maxLines = 1)
                        Spacer(Modifier.width(5.dp))
                        Icon(AppIcons["x"], "删除", Modifier.size(10.dp), tint = p.textWeak)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(
                    label = "",
                    value = input,
                    onValueChange = { input = it.take(MAX_CATEGORY_LEN) },
                    modifier = Modifier.weight(1f),
                    placeholder = "新分类，如：宠物",
                )
                PrimaryButton("添加", icon = "plus", enabled = input.isNotBlank()) { add() }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, fontSize = 12.sp, color = Color(0xFFD93025))
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("恢复默认", Modifier.weight(1f)) {
                    draft = DEFAULT_CATEGORIES
                    error = null
                }
                PrimaryButton("保存", Modifier.weight(1f), loading = saving) {
                    saving = true
                    scope.launch {
                        try {
                            val r = Repo.saveExpenseCategories(draft)
                            onSaved(r.categories.ifEmpty { draft })
                        } catch (e: ApiException) {
                            error = e.message
                            saving = false
                        } catch (e: Exception) {
                            error = "保存失败，检查一下网络"
                            saving = false
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
