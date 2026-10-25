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
                ThemeRow(
                    theme = t,
                    current = AppTheme.of(vm.ui.theme) == t,
                    accent = vm.ui.accent,
                    onClick = { vm.setTheme(t.slug, if (t == AppTheme.of(vm.ui.theme)) vm.ui.accent else "") },
                )
                Spacer(Modifier.height(9.dp))
            }

            // 强调色：只在这三套主题下有意义（与网站同规则）
            val cur = AppTheme.of(vm.ui.theme)
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
private fun ThemeRow(theme: AppTheme, current: Boolean, accent: String, onClick: () -> Unit) {
    val p = palette
    val preview = paletteOf(theme, if (current) accent else "")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(minOf(p.radiusCard, 18.dp)))
            .background(if (current) p.accentSoft else p.cardSoft)
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
