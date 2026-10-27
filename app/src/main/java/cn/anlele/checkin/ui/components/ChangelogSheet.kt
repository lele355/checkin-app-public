package cn.anlele.checkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.data.ChangelogItemDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.update.UpdateKit

/**
 * 完整更新日志：从服务端 `App:Changelog` 拉，按版本倒序展示。
 *
 * 【为什么放服务端而不是写死在 APK 里】日志要能补写（比如某个版本当时漏记了细节），
 * 写死就得为了改一行字再发一版。现在发版时往 appsettings.json 的数组里补一条即可。
 *
 * 每条显示：版本号 + 发布日期 + 说明；当前运行的版本打「当前」标记。
 */
@Composable
fun ChangelogSheet(onClose: () -> Unit) {
    val p = palette
    var items by remember { mutableStateOf<List<ChangelogItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            items = Repo.changelog().items
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    PickerSheetShell(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("更新日志", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "当前版本 ${UpdateKit.localName}（${UpdateKit.localCode}）",
                fontSize = 12.sp, color = p.textWeak,
            )
            Spacer(Modifier.height(14.dp))

            when {
                loading -> Text("正在加载…", fontSize = 13.sp, color = p.textSub)
                error != null -> Text(error!!, fontSize = 13.sp, color = p.textSub)
                items.isEmpty() -> Text("还没有更新记录", fontSize = 13.sp, color = p.textSub)
                else -> items.forEach { it ->
                    val isCurrent = it.versionCode == UpdateKit.localCode
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .background(p.cardSoft, chipShape())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "v${it.version}",
                                fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.SemiBold,
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier.background(p.accent, chipShape())
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text("当前", fontSize = 9.5.sp, color = p.onAccent)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(it.date, fontSize = 11.sp, color = p.textWeak)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            it.notes,
                            fontSize = 12.5.sp, color = p.textSub, lineHeight = 19.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 设置页里的圆点标记用（当前版本左侧） */
@Composable
private fun Dot() {
    Box(Modifier.size(6.dp).background(palette.accent, CircleShape))
}
