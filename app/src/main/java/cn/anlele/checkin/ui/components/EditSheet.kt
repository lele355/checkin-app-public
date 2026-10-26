package cn.anlele.checkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.delay

/**
 * 通用「弹出卡片」编辑层 —— 对应网页里点「新建/编辑」弹出的那个 dialog。
 *
 * 为什么用底部弹层而不是新页面：新建一条日程不该把整个列表推走再拉回来，
 * 弹层停在原地、保存即关，手感和网页版一致，也是原生 App 更常见的形式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(
    title: String,
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveText: String = "保存",
    saving: Boolean = false,
    destructive: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = p.card,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(p.borderStrong.copy(alpha = .35f)))
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.weight(1f))
                if (destructive != null) destructive()
            }
            Spacer(Modifier.height(16.dp))
            content()
            if (onSave != null) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("取消", Modifier.weight(1f)) { onClose() }
                    PrimaryButton(saveText, Modifier.weight(1f), loading = saving) { onSave() }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

/**
 * 弹层底部的小号删除按钮。
 *
 * 一次点击不会直接删：先就地变成「确认删除」，3.5 秒内没有再点就退回。
 * 与网页端 `data-confirm` 是同一套做法 —— 不弹对话框、不打断流程，
 * 确认放在用户本来就要点的地方。
 */
@Composable
fun SheetDelete(onClick: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3500)
            armed = false
        }
    }
    TinyAction(
        text = if (armed) "确认删除" else "删除",
        icon = "trash-2",
        danger = true,
        onClick = { if (armed) onClick() else armed = true },
    )
}
