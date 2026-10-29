package cn.anlele.checkin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.buttonShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.fieldShape
import cn.anlele.checkin.ui.theme.palette

/**
 * 卡片 —— 全站唯一的「一块内容」容器。
 * 5 套主题的差异全部在这里吃掉：editorial 直角细边框、neumorphism 同色软凸起、
 * natural 大圆角、macos 深灰半透、clay 粉白渐变。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val p = palette
    val shape = p.cardShape()
    var m = modifier
    if (p.neumorph) {
        m = m.shadow(10.dp, shape, clip = false, ambientColor = p.borderStrong, spotColor = Color.White)
    } else if (p.isDark) {
        m = m.shadow(0.dp, shape, clip = false)
    }
    m = m.clip(shape)
    m = if (p.cardBrush != null && containerColor == null)
        m.background(p.cardBrush!!, shape)
    else
        m.background(containerColor ?: p.card, shape)

    if (p.border != Color.Transparent) {
        m = m.border(1.dp, p.border, shape)
    }
    if (onClick != null) m = m.pressable(onClick)

    Box(m.padding(padding)) { content() }
}

/** 按压缩放：clay 需要回弹感，其他主题轻微即可 */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val p = palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = if (pressed) 0.975f else 1f
    val scale by animateFloatAsState(target, label = "press")
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val p = palette
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.textSub,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val p = palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                color = p.text,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 13.sp, color = p.textSub)
            }
        }
        action?.invoke()
    }
}

// ---------------- 按钮 ----------------

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val p = palette
    val shape = p.buttonShape()
    Row(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(if (enabled && !loading) p.accent else p.accent.copy(alpha = .45f), shape)
            .then(if (enabled && !loading) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = p.onAccent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(AppIcons[icon], null, Modifier.size(17.dp), tint = p.onAccent)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = p.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val p = palette
    val shape = p.buttonShape()
    val tint = if (danger) Color(0xFFD93025) else p.text
    Row(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(p.cardSoft, shape)
            .then(if (p.border != Color.Transparent) Modifier.border(1.dp, p.border, shape) else Modifier)
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(AppIcons[icon], null, Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = tint, fontSize = 14.sp)
    }
}

/** 小圆角文字按钮（卡片右上「编辑」「删除」） */
@Composable
fun TinyAction(text: String, icon: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val p = palette
    val tint = if (danger) Color(0xFFD93025) else p.textSub
    Row(
        Modifier
            .clip(chipShape())
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(AppIcons[icon], null, Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 12.5.sp, color = tint)
    }
}

@Composable
fun IconBtn(icon: String, contentDesc: String, size: Dp = 40.dp, tint: Color? = null, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[icon], contentDesc, Modifier.size(size * 0.52f), tint = tint ?: p.text)
    }
}

// ---------------- 输入 ----------------

@Composable
fun AppField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    val p = palette
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(label, fontSize = 12.5.sp, color = p.textSub, modifier = Modifier.padding(bottom = 6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let { { Text(it, color = p.textWeak, fontSize = 14.sp) } },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            enabled = enabled,
            shape = fieldShape(),
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = p.text),
            prefix = prefix?.let { { Text(it, color = p.textSub, fontSize = 14.sp) } },
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            supportingText = supporting?.let { { Text(it, fontSize = 11.5.sp, color = p.textWeak) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.accent,
                unfocusedBorderColor = if (p.border == Color.Transparent) p.borderStrong.copy(alpha = .5f) else p.border,
                focusedContainerColor = p.card,
                unfocusedContainerColor = p.card,
                disabledContainerColor = p.cardSoft,
                cursorColor = p.accent,
                focusedTextColor = p.text,
                unfocusedTextColor = p.text,
            ),
        )
    }
}

// ---------------- 空态 / 载入 ----------------

@Composable
fun EmptyState(icon: String, title: String, hint: String? = null, action: (@Composable () -> Unit)? = null) {
    val p = palette
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(p.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons[icon], null, Modifier.size(26.dp), tint = p.accent)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 15.sp, color = p.text, fontWeight = FontWeight.Medium)
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(hint, fontSize = 12.5.sp, color = p.textWeak, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun LoadingBlock(text: String = "正在加载…") {
    val p = palette
    Column(
        Modifier
            .fillMaxSize()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), color = p.accent, strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, fontSize = 12.5.sp, color = p.textWeak)
    }
}

/** 骨架屏：比转圈更「像 App」，冷启动那 0.3 秒不闪白 */
@Composable
fun SkeletonCard(height: Dp = 88.dp, modifier: Modifier = Modifier) {
    val p = palette
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(p.cardShape())
            .background(p.cardSoft),
    )
}

// ---------------- 徽标 / 分隔 ----------------

@Composable
fun AppChip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val p = palette
    val bg = if (selected) p.accent else p.cardSoft
    val fg = if (selected) p.onAccent else p.textSub
    Box(
        Modifier
            .clip(chipShape())
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 小圆点徽标（未读数） */
@Composable
fun DotBadge(count: Int) {
    if (count <= 0) return
    val p = palette
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0xFFE5484D))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(if (count > 99) "99+" else count.toString(), fontSize = 10.sp, color = Color.White)
    }
}

@Composable
fun HDivider() {
    val p = palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(if (p.border == Color.Transparent) p.borderStrong.copy(alpha = .18f) else p.border),
    )
}

/** 卡片内的「图标 + 标题 + 副标题」行，侧栏/列表通用 */
@Composable
fun IconTitleRow(icon: String, title: String, subtitle: String? = null, tint: Color? = null) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(p.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons[icon], null, Modifier.size(17.dp), tint = tint ?: p.accent)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontSize = 14.5.sp, color = p.text, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank())
                Text(subtitle, fontSize = 12.sp, color = p.textWeak)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        titleContentColor = p.text,
        textContentColor = p.textSub,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 13.5.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) Color(0xFFD93025) else p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = p.textSub) }
        },
    )
}

/** 顶部细条提示（离线/错误），会自动消失由调用方控制 */
@Composable
fun BannerBar(text: String, modifier: Modifier = Modifier, tone: BannerTone = BannerTone.Info) {
    val p = palette
    val (bg, fg) = when (tone) {
        BannerTone.Info -> p.accentSoft to p.text
        BannerTone.Warn -> Color(0xFFFFF4E5) to Color(0xFF8A5A11)
        BannerTone.Error -> Color(0xFFFFEBEA) to Color(0xFFB3261E)
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(p.chipShape())
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(text, fontSize = 12.5.sp, color = fg)
    }
}

enum class BannerTone { Info, Warn, Error }

/**
 * 可关闭的顶部提醒条：一句话 + 一个动作 + 一个 × 。
 *
 * 与 [BannerBar] 的区别：BannerBar 是「状态说明」（离线、出错），没得关；
 * 这个是「要你处理但不强制」的事（没开权限、有新版本），必须能一键忽略 ——
 * 否则用户不想开权限就得天天看它，反而是骚扰。
 */
@Composable
fun NoticeBar(
    text: String,
    action: String,
    onAction: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
) {
    val p = palette
    val (bg, fg) = when (tone) {
        BannerTone.Info -> p.accentSoft to p.text
        BannerTone.Warn -> Color(0xFFFFF4E5) to Color(0xFF8A5A11)
        BannerTone.Error -> Color(0xFFFFEBEA) to Color(0xFFB3261E)
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(p.chipShape())
            .background(bg)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            color = fg,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            action,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier
                .clip(p.chipShape())
                .clickable(onClick = onAction)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Box(
            Modifier
                .size(24.dp)
                .clip(p.chipShape())
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons["x"], "忽略", Modifier.size(13.dp), tint = fg.copy(alpha = .65f))
        }
    }
}

/** Toast 风格的轻提示（走 Scaffold 的 SnackbarHost） */
suspend fun SnackbarHostState.toast(msg: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(msg)
}

@Composable
fun AnimatedFadeIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) { content() }
}
