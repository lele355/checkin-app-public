package cn.anlele.checkin.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.MeDto
import cn.anlele.checkin.data.NavItemDto
import cn.anlele.checkin.ui.components.SiteSwitch
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette

/**
 * 侧栏自定义面板：显示/隐藏 + 排序。
 *
 * 【为什么不碰服务端契约】
 * 显隐用服务端已有的 NavHidden（与网页端同一份数据），排序只存本机 SharedPreferences ——
 * 服务端没有 order 字段，为一个 App 偏好去加列/迁移/改 API 不值当。两条数据各管各的，
 * 互不覆盖：隐藏的项仍留在顺序表里，取消隐藏后位置还在。
 *
 * 【为什么不踩权限/情侣空间的雷】
 * 面板唯一数据源是 me.navAll —— 服务端下发的「有权限全集」，普通账号根本看不到情侣项
 * 和管理项；情侣项里 period 用 period.use、其余用 couples.view，逐 code 处理、不整组开关，
 * 所以给只有其中一种权限的账号也不会误开整组，更不会动 CoupleScope 的双方互见边界。
 *
 * 【拖拽】沿用工作台验证过的三件套：key(code) 固定组合身份 + rememberUpdatedState 取最新
 * 列表 + onSizeChanged 实测行高。少一样就会出现「只能拖一格 / 拖完跟丢」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavCustomSheet(vm: AppVm, me: MeDto, onClose: () -> Unit) {
    val p = palette
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val all = me.navAll
    val byCode = remember(all) { all.associateBy { it.code } }

    // 草稿：进来先装载本机顺序；只以 username 为 key —— me 保存后会刷新，
    // 但顺序/隐藏的草稿不能被刷新重置（否则拖到一半会跳回去）。
    val order = remember(me.username) {
        mutableStateListOf<String>().apply { addAll(vm.navOrder(me.username)) }
    }
    val hidden = remember(me.username) {
        mutableStateListOf<String>().apply { addAll(me.navHidden) }
    }

    // 账号权限变了 / 以后加了新模块：无权限的 code 清掉，新 code 补到末尾（默认显示）。
    LaunchedEffect(all) {
        val valid = all.map { it.code }.toSet()
        order.removeAll { it !in valid }
        all.forEach { if (it.code !in order) order.add(it.code) }
        hidden.removeAll { it !in valid }
    }

    fun commitOrder() = vm.setNavOrder(me.username, order.toList())
    fun commitHidden() = vm.saveNavHidden(hidden.toList())

    // 全局顺序 → 分组：LinkedHashMap 按首项出现顺序，所以组顺序就是「组的相对位置」。
    val orderedAll = order.mapNotNull { byCode[it] }
    val groups = orderedAll.groupBy { it.group }

    fun moveGroup(group: String, delta: Int) {
        val names = groups.keys.toList()
        val i = names.indexOf(group)
        val j = i + delta
        if (i < 0 || j !in names.indices) return
        val swapped = names.toMutableList()
        val tmp = swapped[i]; swapped[i] = swapped[j]; swapped[j] = tmp
        val rebuilt = swapped.flatMap { g -> order.filter { c -> byCode[c]?.group == g } }
        order.clear(); order.addAll(rebuilt); commitOrder()
    }

    fun moveItem(group: String, code: String, delta: Int) {
        val codes = groups[group]?.map { it.code } ?: return
        val i = codes.indexOf(code)
        val j = i + delta
        if (i < 0 || j !in codes.indices) return
        val ia = order.indexOf(codes[i])
        val ib = order.indexOf(codes[j])
        if (ia < 0 || ib < 0) return
        val tmp = order[ia]; order[ia] = order[ib]; order[ib] = tmp
        commitOrder()
    }

    fun toggleShown(code: String, show: Boolean) {
        if (show) hidden.remove(code) else if (code !in hidden) hidden.add(code)
        commitHidden()
    }

    fun restore() {
        order.clear(); order.addAll(all.map { it.code }); commitOrder()
        hidden.clear(); commitHidden()
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = p.card,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(p.borderStrong.copy(alpha = .35f), CircleShape))
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自定义侧栏", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                    Text("显示 / 隐藏 · 拖动排序", fontSize = 12.sp, color = p.textWeak)
                }
                TinyAction("恢复默认", "rotate-ccw") { restore() }
            }
            Spacer(Modifier.height(12.dp))

            groups.forEach { (group, items) ->
                val groupNames = groups.keys.toList()
                val gi = groupNames.indexOf(group)
                // 组标题：整组上移/下移（跨组拖拽太容易误触，箭头更稳）
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textWeak,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f),
                    )
                    ArrowBtn("chevron-up", "组上移", gi > 0) { moveGroup(group, -1) }
                    ArrowBtn("chevron-down", "组下移", gi < groupNames.size - 1) { moveGroup(group, 1) }
                }

                DraggableNavGroup(
                    items = items,
                    hidden = hidden,
                    fallbackRowPx = with(LocalDensity.current) { 45.dp.toPx() },
                    onMove = { code, delta -> moveItem(group, code, delta) },
                    onShown = { code, show -> toggleShown(code, show) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 一组内的长按拖拽列表：只在本组内换位，不跨组（跨组用标题的箭头）。 */
@Composable
private fun DraggableNavGroup(
    items: List<NavItemDto>,
    hidden: List<String>,
    fallbackRowPx: Float,
    onMove: (String, Int) -> Unit,
    onShown: (String, Boolean) -> Unit,
) {
    var dragCode by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    // 普通 HashMap 就够：行高只在手势协程里读，不需要触发重组（与工作台一致）。
    val rowHeights = remember { HashMap<String, Float>() }
    val codesState = rememberUpdatedState(items.map { it.code })

    items.forEachIndexed { i, item ->
        val dragging = dragCode == item.code
        val isShown = item.code !in hidden
        key(item.code) {
            NavCustomRow(
                item = item,
                shown = isShown,
                canUp = i > 0,
                canDown = i < items.size - 1,
                dragging = dragging,
                onUp = { onMove(item.code, -1) },
                onDown = { onMove(item.code, 1) },
                onShown = { onShown(item.code, it) },
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .onSizeChanged { rowHeights[item.code] = it.height.toFloat() }
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        val s = if (dragging) 1.02f else 1f
                        scaleX = s; scaleY = s
                    }
                    .pointerInput(item.code) {
                        var cur = -1
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragCode = item.code
                                dragOffset = 0f
                                cur = codesState.value.indexOf(item.code)
                            },
                            onDragEnd = { dragCode = null; dragOffset = 0f; cur = -1 },
                            onDragCancel = { dragCode = null; dragOffset = 0f; cur = -1 },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val last = codesState.value.size - 1
                                if (cur in 0..last) {
                                    val step = (rowHeights[item.code] ?: fallbackRowPx).coerceAtLeast(1f)
                                    if (dragOffset > step * .55f && cur < last) {
                                        onMove(item.code, 1); cur += 1; dragOffset -= step
                                    } else if (dragOffset < -step * .55f && cur > 0) {
                                        onMove(item.code, -1); cur -= 1; dragOffset += step
                                    }
                                }
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun NavCustomRow(
    item: NavItemDto,
    shown: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    dragging: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onShown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val lift by animateFloatAsState(if (dragging) 1f else .55f, tween(160), label = "nav-handle")
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(minOf(p.radiusChip, 11.dp)))
            .background(if (dragging) p.accentSoft else p.cardSoft)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 拖拽手柄（视觉提示，实际是整行长按拖拽）
        Icon(
            AppIcons["menu"], null,
            Modifier.size(15.dp).graphicsLayer { alpha = lift },
            tint = p.textWeak,
        )
        Spacer(Modifier.width(9.dp))
        Icon(
            AppIcons[item.icon], null, Modifier.size(17.dp),
            tint = if (shown) p.textSub else p.textWeak.copy(alpha = .45f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            item.label,
            fontSize = 13.5.sp,
            color = if (shown) p.text else p.textWeak,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ArrowBtn("chevron-up", "上移", canUp, onUp)
        ArrowBtn("chevron-down", "下移", canDown, onDown)
        Spacer(Modifier.width(6.dp))
        SiteSwitch(shown, onChange = onShown)
    }
}

@Composable
private fun ArrowBtn(icon: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons[icon], desc, Modifier.size(16.dp),
            tint = if (enabled) p.textSub else Color.Transparent,
        )
    }
}
