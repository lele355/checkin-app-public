package cn.anlele.checkin.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.buttonShape
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.fieldShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.delay

/**
 * 「网站同款」组件与动效。
 *
 * 这一层的目标不是好看，而是**和网页手机端长得一样**：
 * 组件名、尺寸、间距、圆角、动效曲线都对着 wwwroot/css/site.css 抄的，
 * 改这里等于同时改所有页面，别在页面里另起炉灶。
 */

// ==================== 页面骨架 ====================

/**
 * 模块页头卡：左边标题、右边操作按钮组（视图切换 / 新建），下面可带一行说明。
 * 对应网页的 `.card > .page-head` + `.hint`。
 */
@Composable
fun PageHeadCard(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    /** 标题区自定义（比如记账页的「‹ 2026年10月 ›」翻月组），传了就不用 title */
    titleSlot: (@Composable () -> Unit)? = null,
    /** 标题行下面的一行（视图切换等）；和 hint 同时给时先 subRow 后 hint */
    subRow: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = palette
    Box(
        modifier
            .fillMaxWidth()
            .clip(p.cardShape())
            .background(p.card)
            .then(if (p.border != Color.Transparent) Modifier.border(1.dp, p.border, p.cardShape()) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (titleSlot != null) {
                    Box(Modifier.weight(1f)) { titleSlot() }
                } else {
                    Text(
                        title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    actions()
                }
            }
            if (subRow != null) {
                Spacer(Modifier.height(9.dp))
                subRow()
            }
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(hint, fontSize = 12.5.sp, color = p.textWeak, lineHeight = 18.sp)
            }
        }
    }
}

/** 视图切换（列表 / 日历 / 卡片）—— 对应网页 `.view-switch` + `.vs-btn` */
@Composable
fun ViewSwitch(
    options: List<Pair<String, String>>,   // icon to label
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    Row(
        modifier
            .clip(p.fieldShape())
            .background(p.cardSoft)
            .border(1.dp, p.borderStrong.copy(alpha = .35f), p.fieldShape())
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (icon, label) ->
            val on = label == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(maxOf(p.radiusField - 4.dp, 2.dp)))
                    .background(if (on) p.accent else Color.Transparent)
                    .clickable { onSelect(label) }
                    .height(30.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    AppIcons[icon], null, Modifier.size(14.dp),
                    tint = if (on) p.onAccent else p.textSub,
                )
                Text(
                    label,
                    fontSize = 12.5.sp,
                    color = if (on) p.onAccent else p.textSub,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 紧凑主按钮（页头右侧的「+ 新建」）—— 网页 `.btn` 的矮版 */
@Composable
fun HeadButton(text: String, icon: String? = "plus", modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = palette
    Row(
        modifier
            .clip(p.buttonShape())
            .background(p.accent)
            .clickable(onClick = onClick)
            .height(36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(AppIcons[icon], null, Modifier.size(14.dp), tint = p.onAccent)
        Text(text, fontSize = 12.5.sp, color = p.onAccent, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** 分组标题条（日程「今天 / 明天 / 一周内」这种）—— 对应 `.group-head`，会吸顶 */
@Composable
fun GroupHead(text: String, modifier: Modifier = Modifier) {
    val p = palette
    Box(
        modifier
            .fillMaxWidth()
            .background(p.cardSoft)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.textWeak,
            letterSpacing = 1.6.sp,
        )
    }
}

/** 列表行 —— 对应 `.row-item`：左侧 meta、中间标题+说明、右侧操作 */
@Composable
fun SiteRow(
    onClick: (() -> Unit)? = null,
    showTopBorder: Boolean = true,
    meta: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
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
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (meta != null) {
                Box(Modifier.width(64.dp)) { meta() }
            }
            Column(Modifier.weight(1f)) { body() }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/** 卡尾链接「全部习惯 →」—— 对应 `.hint-foot` + 箭头 */
@Composable
fun CardFoot(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.5.sp, color = p.textSub, modifier = Modifier.weight(1f))
        Icon(AppIcons["chevron-right"], null, Modifier.size(13.dp), tint = p.textWeak)
    }
}

/** 空态 —— 对应 `.empty`：大 emoji 轻浮动 + 一行说明 */
@Composable
fun SiteEmpty(emoji: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val p = palette
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BobEmoji(emoji, 24.sp)
        Spacer(Modifier.height(6.dp))
        Text(text, fontSize = 12.5.sp, color = p.textWeak, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

// ==================== 动效 ====================

private val SiteEase = CubicBezierEasing(.22f, .61f, .36f, 1f)

/**
 * 卡片依次浮现 —— 对应 `@keyframes card-in`（只动透明度，错峰 50ms，最多第 6 张）。
 * 只改 alpha 不碰布局：网页那边也是「只动 opacity」，避免和滚动/测量打架。
 */
@Composable
fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 6) * 50).toLong())
        show = true
    }
    val a by animateFloatAsState(if (show) 1f else 0f, tween(450), label = "card-in")
    Box(Modifier.alpha(a)) { content() }
}

/**
 * LazyListScope 版本的 StaggerIn：直接当 item 用，省掉外面那层 `item { }`。
 * （`StaggerIn` 是 @Composable，不能直接写在 LazyColumn 的 scope 里）
 */
fun LazyListScope.staggerItem(index: Int, content: @Composable () -> Unit) {
    item { StaggerIn(index, content) }
}

/** 进度条从左生长 —— 对应 `bar-grow`（scaleX，原点在左） */
@Composable
fun GrowBar(
    ratio: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color? = null,
    track: Color? = null,
    animate: Boolean = true,
) {
    val p = palette
    val shape = chipShape()
    var run by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { if (animate) run = true }
    val s by animateFloatAsState(if (run) 1f else 0f, tween(700, easing = SiteEase), label = "bar-grow")

    Box(modifier.fillMaxWidth().height(height).clip(shape).background(track ?: p.cardSoft)) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(height)
                .graphicsLayer {
                    scaleX = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .clip(shape)
                .background(color ?: p.accent),
        )
    }
}

/** 月柱从底升起 —— 对应 `bar-rise`（scaleY，原点在底） */
@Composable
fun RiseBar(
    ratio: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    animate: Boolean = true,
    delayMs: Int = 0,
) {
    val p = palette
    val shape = chipShape()
    var run by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(delayMs.toLong())
            run = true
        }
    }
    val s by animateFloatAsState(if (run) 1f else 0f, tween(550, easing = SiteEase), label = "bar-rise")
    Box(modifier.height((84 * ratio.coerceIn(0f, 1f)).dp.coerceAtLeast(2.dp))) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((84 * ratio.coerceIn(0f, 1f)).dp.coerceAtLeast(2.dp))
                .graphicsLayer {
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(shape)
                .background(color ?: p.accent),
        )
    }
}

/** 空态大 emoji 轻浮动 —— 对应 `@keyframes bob`（上下 4dp，3.2s 循环） */
@Composable
fun BobEmoji(emoji: String, size: TextUnit = 24.sp) {
    val t = rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(1600, easing = SiteEase), RepeatMode.Reverse),
        label = "bobY",
    )
    Text(emoji, fontSize = size, modifier = Modifier.graphicsLayer { translationY = y })
}

/** 数字进场 —— 对应 `num-in`（淡入 + 上移 5dp） */
@Composable
fun NumIn(value: String, fontSize: TextUnit, color: Color? = null, weight: FontWeight = FontWeight.SemiBold) {
    val p = palette
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val a by animateFloatAsState(if (show) 1f else 0f, tween(500), label = "num-in")
    val dy by animateFloatAsState(if (show) 0f else 5f, tween(500), label = "num-dy")
    Text(
        value,
        fontSize = fontSize,
        color = color ?: p.text,
        fontWeight = weight,
        modifier = Modifier.graphicsLayer {
            alpha = a
            translationY = dy
        },
    )
}

/** 弹层/内容切换的短淡入 —— 对应 `swap-in`（模糊在低版本不可用，降级为透明+位移） */
@Composable
fun SwapIn(content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val a by animateFloatAsState(if (show) 1f else 0f, tween(280), label = "swap-a")
    val dy by animateFloatAsState(if (show) 0f else 6f, tween(280), label = "swap-y")
    Box(Modifier.graphicsLayer { alpha = a; translationY = dy }) { content() }
}
