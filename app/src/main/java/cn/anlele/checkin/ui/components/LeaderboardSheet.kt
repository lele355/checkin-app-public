package cn.anlele.checkin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.data.LeaderboardDto
import cn.anlele.checkin.ui.theme.palette

/**
 * 排行榜弹窗（2026-10-31 站主定：内联面板 → 弹窗）。
 * 复用 PickerSheetShell 底部卡片形态，与 App 其它弹层视觉一致（同圆角/同底色/点遮罩关闭）。
 * fish / melon 两个单机游戏共用。
 */
@Composable
fun LeaderboardSheet(
    lb: LeaderboardDto,
    onDismiss: () -> Unit,
    title: String = "排行榜",
) {
    val p = palette
    PickerSheetShell(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 22.dp)) {
            Text(title, fontSize = 16.sp, color = p.text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (lb.items.isEmpty()) {
                Text("还没有记录", fontSize = 13.sp, color = p.textSub)
            } else {
                lb.items.forEach {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${it.rank}", Modifier.width(30.dp), fontSize = 13.sp, color = p.textSub)
                        Text(it.name, Modifier.weight(1f), fontSize = 14.sp, color = p.text)
                        Text("${it.bestScore}", fontSize = 14.sp, color = p.accentStrong, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (lb.mine.bestScore > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "我的最高 ${lb.mine.bestScore} · 第 ${lb.mine.rank} 名",
                    fontSize = 13.sp, color = p.textSub,
                )
            }
        }
    }
}
