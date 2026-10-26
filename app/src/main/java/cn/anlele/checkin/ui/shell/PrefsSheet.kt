package cn.anlele.checkin.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.MeDto
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.AppTheme
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.ui.theme.paletteOf
import cn.anlele.checkin.ui.theme.tonesOf
import cn.anlele.checkin.ui.theme.ToneOption

/**
 * 外观面板 —— 顶栏调色板点出来的那个底部弹层。
 * 5 套主题各带三色小样，点一下立刻全局换肤（和网页端一样即时）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefsSheet(vm: AppVm, me: MeDto, onClose: () -> Unit) {
    val p = palette
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 18.dp)
                .navigationBarsPadding(),
        ) {
            Text("外观与主题", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text("换一套，整个 App 立刻变样", fontSize = 12.5.sp, color = p.textWeak)
            Spacer(Modifier.height(16.dp))

            AppTheme.entries.forEach { t ->
                val sameTheme = AppTheme.of(vm.ui.theme) == t
                ThemeRow(
                    theme = t,
                    current = sameTheme,
                    accent = vm.ui.accent,
                    tone = vm.ui.tone,
                    // 换主题时底色一律清零：各套主题的底色档互不通用
                    // （clay 的 blush 到 editorial 根本不存在），留着会变成脏值。
                    onClick = { vm.setTheme(t.slug, if (sameTheme) vm.ui.accent else "", "") },
                )
                Spacer(Modifier.height(9.dp))
            }

            val cur = AppTheme.of(vm.ui.theme)

            // 底色：每套主题 5 档备选 + 1 档默认，换的是「纸面」（背景/卡片/顶栏），
            // 与强调色正交 —— 网页端就是这么分的（底色管纸、强调色管笔）。
            // 2.3.4 之前这块在 App 里完全没有，所以切到 editorial / natural 时只能换主题，
            // 换不了底色，看起来就是「网站能换色、App 只能换强调色」。
            Spacer(Modifier.height(8.dp))
            Text("底色", fontSize = 12.5.sp, color = p.textSub)
            Spacer(Modifier.height(9.dp))
            val toneOptions = listOf(ToneOption("", "默认")) + tonesOf(cur)
            // 底色小圆点用 3 列网格排：6 个选项一行放不下（每格还要带文字）
            toneOptions.chunked(3).forEach { rowItems ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    rowItems.forEach { opt ->
                        val sample = paletteOf(cur, vm.ui.accent, opt.slug)
                        val selected = vm.ui.tone == opt.slug
                        ToneChip(
                            label = opt.label,
                            brush = sample.bgBrush ?: Brush.linearGradient(listOf(sample.bg, sample.bg)),
                            card = sample.card,
                            accent = sample.accent,
                            selected = selected,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setTheme(cur.slug, vm.ui.accent, opt.slug) },
                        )
                    }
                    // 不满 3 个时用空白占位，避免最后一行的格子被拉宽
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // 强调色：只在这三套主题下有意义（与网站同规则）
            val accents = when (cur) {
                AppTheme.Neumorphism -> listOf("" to "紫", "coral" to "珊瑚", "mint" to "薄荷", "lemon" to "柠檬")
                AppTheme.Macos -> listOf("" to "蓝", "violet" to "紫", "rose" to "玫红", "green" to "绿")
                AppTheme.Clay -> listOf("" to "粉", "mint" to "薄荷", "lilac" to "淡紫", "lemon" to "柠檬")
                else -> emptyList()
            }
            if (accents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("强调色", fontSize = 12.5.sp, color = p.textSub)
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    accents.forEach { (slug, label) ->
                        val sample = paletteOf(cur, slug)
                        val selected = vm.ui.accent == slug
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { vm.setTheme(cur.slug, slug) },
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .background(sample.accent, CircleShape)
                                    .then(
                                        if (selected) Modifier.border(2.dp, p.text, CircleShape)
                                        else Modifier
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 11.sp, color = if (selected) p.text else p.textWeak)
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(me.displayName, fontSize = 14.5.sp, color = p.text, fontWeight = FontWeight.Medium)
                    Text("@${me.username} · ${me.roleName}", fontSize = 12.sp, color = p.textWeak)
                }
                TinyAction("退出登录", "log-out", danger = true) {
                    onClose()
                    vm.logout()
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun ThemeRow(theme: AppTheme, current: Boolean, accent: String, tone: String, onClick: () -> Unit) {
    val p = palette
    // 预览跟着当前的底色/强调色走：点过「雾蓝」之后，那张主题小样也该是雾蓝的，
    // 否则切回来看见小样又变回默认色，会让人以为没生效。
    // 但未选中的行一律按「默认底色」画 —— 每行代表的是那套主题本来的样子，
    // 全染成当前底色就分不出区别了。
    val preview = paletteOf(theme, if (current) accent else "", if (current) tone else "")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(minOf(p.radiusCard, 18.dp)))
            // 选中行用细边框而不是 accentSoft 底：clay 主题下 accentSoft 是粉色，
            // 配上薄荷底色会变成一块突兀的粉卡，看起来像没生效。
            .background(p.cardSoft)
            .then(if (current) Modifier.border(1.5.dp, p.accent, RoundedCornerShape(minOf(p.radiusCard, 18.dp))) else Modifier)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 三色小样（底 / 卡 / 强调）
        Box(Modifier.size(46.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(
                        preview.bgBrush ?: Brush.linearGradient(listOf(preview.bg, preview.bg)),
                        RoundedCornerShape(9.dp),
                    ),
            ) {
                Box(
                    Modifier
                        .padding(6.dp)
                        .size(15.dp)
                        .background(preview.card, RoundedCornerShape(4.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(9.dp)
                        .background(preview.accent, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(theme.label, fontSize = 14.5.sp, color = p.text, fontWeight = FontWeight.Medium)
            Text(theme.desc, fontSize = 11.5.sp, color = p.textWeak)
        }
        if (current) {
            Icon(AppIcons["check"], null, Modifier.size(18.dp), tint = p.accent)
        }
    }
}

/**
 * 底色选项 —— 一块「纸 + 卡 + 笔」的小样，点了立刻换。
 *
 * 用小样而不是纯色圆点：底色档的差别就在于「纸面」和「卡面」的层次关系，
 * 只给一个圆点是看不出 clay 那种渐变的，也不容易分辨 mist / stone 这种近色。
 */
@Composable
private fun ToneChip(
    label: String,
    brush: Brush,
    card: Color,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = palette
    Column(
        modifier
            .clip(RoundedCornerShape(minOf(p.radiusChip, 12.dp)))
            .background(if (selected) p.accentSoft else p.cardSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(brush)
                .then(if (selected) Modifier.border(2.dp, p.accent, RoundedCornerShape(7.dp)) else Modifier),
        ) {
            // 卡片小样：让「纸」和「卡」的层次一眼能看出来
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(.62f)
                    .height(15.dp)
                    .background(card, RoundedCornerShape(4.dp)),
            )
            // 笔色一点：底色和强调色是两回事，放个点在角上提醒
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(6.dp)
                    .background(accent, CircleShape),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            fontSize = 10.5.sp,
            color = if (selected) p.text else p.textWeak,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
