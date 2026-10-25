package cn.anlele.checkin.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette

/** 把「一天中的第几分钟」格式化成 08:00 */
fun minuteText(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

/**
 * 时分选择弹层。
 *
 * 不用 Material3 的 TimePicker：它强制 24 小时表盘，中文字号在小屏上会被裁切，
 * 而且不同 material3 版本 API 有变动。这里用两列滚动列表，直观且五套主题下都稳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuteOfDaySheet(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val p = palette
    val safe = ((initialMinute % 1440) + 1440) % 1440
    var hour by remember { mutableStateOf(safe / 60) }
    var minute by remember { mutableStateOf(safe % 60) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = (hour - 2).coerceAtLeast(0))
    val minState = rememberLazyListState(initialFirstVisibleItemIndex = (minute - 2).coerceAtLeast(0))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.card,
        dragHandle = {},
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.weight(1f))
                Text(
                    "%02d:%02d".format(hour, minute),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.accent,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LazyColumn(
                    Modifier.weight(1f).height(190.dp),
                    state = hourState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items((0..23).toList()) { h ->
                        NumCell("%02d".format(h), h == hour, "时") { hour = h }
                    }
                }
                Text(":", fontSize = 22.sp, color = p.textSub, modifier = Modifier.padding(horizontal = 6.dp))
                LazyColumn(
                    Modifier.weight(1f).height(190.dp),
                    state = minState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items((0..59).toList()) { m ->
                        NumCell("%02d".format(m), m == minute, "分") { minute = m }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("取消", Modifier.weight(1f)) { onDismiss() }
                PrimaryButton("确定", Modifier.weight(1f)) { onPick(hour * 60 + minute) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NumCell(text: String, selected: Boolean, unit: String, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (selected) p.accent else p.cardSoft, chipShape())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                fontSize = 16.sp,
                color = if (selected) p.onAccent else p.text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (selected) {
                Spacer(Modifier.width(3.dp))
                Text(unit, fontSize = 10.sp, color = p.onAccent.copy(alpha = .8f))
            }
        }
    }
}

/** 设置页里的一行时间（点开弹层） */
@Composable
fun TimeRow(
    label: String,
    minute: Int,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
) {
    val p = palette
    var open by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { open = true }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = if (enabled) p.textSub else p.textWeak, modifier = Modifier.weight(1f))
        Text(
            minuteText(minute),
            fontSize = 14.sp,
            color = if (enabled) p.accent else p.textWeak,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(p.cardSoft, CircleShape)
                .padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }

    if (open) {
        MinuteOfDaySheet(
            title = label,
            initialMinute = minute,
            onDismiss = { open = false },
            onPick = { open = false; onChange(it) },
        )
    }
}

/** 免打扰的起止时段（一行显示 22:00 — 08:00，点两端分别改） */
@Composable
fun TimeRangeRow(
    label: String,
    startMinute: Int,
    endMinute: Int,
    enabled: Boolean = true,
    onChange: (Int, Int) -> Unit,
) {
    val p = palette
    var editing by remember { mutableStateOf(0) }   // 0=不编辑 1=改开始 2=改结束

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = if (enabled) p.textSub else p.textWeak, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                minuteText(startMinute),
                fontSize = 14.sp,
                color = if (enabled) p.accent else p.textWeak,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(p.cardSoft, CircleShape)
                    .clickable(enabled = enabled) { editing = 1 }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Text(" — ", fontSize = 12.sp, color = p.textWeak)
            Text(
                minuteText(endMinute),
                fontSize = 14.sp,
                color = if (enabled) p.accent else p.textWeak,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(p.cardSoft, CircleShape)
                    .clickable(enabled = enabled) { editing = 2 }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }

    if (editing != 0) {
        MinuteOfDaySheet(
            title = if (editing == 1) "$label · 开始" else "$label · 结束",
            initialMinute = if (editing == 1) startMinute else endMinute,
            onDismiss = { editing = 0 },
            onPick = {
                if (editing == 1) onChange(it, endMinute) else onChange(startMinute, it)
                editing = 0
            },
        )
    }
}
