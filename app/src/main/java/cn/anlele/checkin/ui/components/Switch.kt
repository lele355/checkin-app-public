package cn.anlele.checkin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.anlele.checkin.ui.theme.palette

/**
 * 自绘胶囊开关。
 *
 * 为什么不用 Material3 的 Switch：它在 editorial（米白）和 neumorphism（浅灰软塑）两套主题下
 * 轨道/把手颜色会发脏，而且默认 41dp 高，与本站「紧凑列表行」的节奏不搭。
 * 这里的尺寸、圆角、颜色全部跟主题 token 走，五套主题下都干净。
 */
@Composable
fun SiteSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // ⚠️ onChange 必须是最后一个参数：Compose 调用习惯写 `SiteSwitch(x) { ... }`，
    // Kotlin 的尾随 lambda 只能匹配最后一个函数类型参数，放中间就会「No value passed for parameter 'onChange'」
    onChange: (Boolean) -> Unit,
) {
    val p = palette
    val trackW = 50.dp
    val trackH = 29.dp
    val thumb = 23.dp
    val pad = 3.dp

    val offsetX by animateDpAsState(
        targetValue = if (checked) trackW - thumb - pad else pad,
        animationSpec = tween(180),
        label = "switch-thumb",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) p.accent else p.borderStrong.copy(alpha = .30f),
        animationSpec = tween(180),
        label = "switch-track",
    )

    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .alpha(if (enabled) 1f else .45f)
            .size(trackW, trackH)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = offsetX)
                .size(thumb)
                .clip(CircleShape)
                .background(if (p.isDark) Color(0xFFEDEDED) else Color.White)
        )
    }
}
