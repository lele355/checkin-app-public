package cn.anlele.checkin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 5 套主题 —— 与网站 wwwroot/css/tokens.css 的 [data-theme] 一一对应。
 * 所有颜色/圆角/字体都从这里的 Palette 出，界面代码不许写死颜色，
 * 这样「换主题」跟网页端一样是一瞬间、全局生效的。
 */
enum class AppTheme(val slug: String, val label: String, val desc: String) {
    Editorial("editorial", "编辑杂志风", "暖米单色 · 衬线标题 · 无阴影"),
    Neumorphism("neumorphism", "新拟物派", "浅灰软塑 · 双光源阴影"),
    Natural("natural", "自然有机风", "米色大地色 · 有机圆角"),
    Macos("macos", "macOS 毛玻璃", "深灰系统风 · 暗色档"),
    Clay("clay", "粘土拟态", "糖果渐变 · 软糯按压");

    companion object {
        fun of(slug: String?): AppTheme = entries.firstOrNull { it.slug == slug } ?: Editorial
    }
}

/** 一套主题的全部设计令牌。字段名对齐 CSS 变量，方便两边对照维护。 */
data class Palette(
    val theme: AppTheme,
    val bg: Color,
    val bgBrush: Brush? = null,
    val card: Color,
    val cardBrush: Brush? = null,
    val cardSoft: Color,
    val topBar: Color,
    val sideBar: Color,
    val text: Color,
    val textSub: Color,
    val textWeak: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val radiusCard: Dp,
    val radiusBtn: Dp,
    val radiusField: Dp,
    val radiusChip: Dp,
    val serifTitle: Boolean,
    val neumorph: Boolean,
    val isDark: Boolean,
)

// alpha 传负数表示「沿用 hex 里自带的 alpha」（如 cardSoft=0x091C1C1C 的 3.5% 黑），
// 正数才是「把整块颜色按这个透明度压上去」。写死成 1f 会把半透明色画成纯色 —— 踩过一次。
private fun c(hex: Long, alpha: Float = -1f) =
    if (alpha < 0f) Color(hex) else Color(hex).copy(alpha = alpha)

private val Editorial = Palette(
    theme = AppTheme.Editorial,
    bg = c(0xFFF9F8F6),
    card = c(0xFFF9F8F6),
    cardSoft = c(0x091C1C1C),
    topBar = c(0xFFF9F8F6),
    sideBar = c(0xFFF9F8F6),
    text = c(0xFF1C1C1C),
    textSub = c(0xFF1C1C1C, .60f),
    textWeak = c(0xFF1C1C1C, .40f),
    border = c(0xFF1C1C1C, .10f),
    borderStrong = c(0xFF1C1C1C, .40f),
    accent = c(0xFF1C1C1C),
    accentStrong = c(0xFF1C1C1C),
    accentSoft = c(0xFF1C1C1C, .06f),
    onAccent = c(0xFFF9F8F6),
    radiusCard = 0.dp,
    radiusBtn = 0.dp,
    radiusField = 0.dp,
    radiusChip = 2.dp,
    serifTitle = true,
    neumorph = false,
    isDark = false,
)

private val Neumorphism = Palette(
    theme = AppTheme.Neumorphism,
    bg = c(0xFFE0E5EC),
    card = c(0xFFE0E5EC),
    cardSoft = c(0xFFE7EBF1),
    topBar = c(0xFFE0E5EC),
    sideBar = c(0xFFE0E5EC),
    text = c(0xFF333333),
    textSub = c(0xFF6B7280),
    textWeak = c(0xFF9CA3AF),
    border = Color.Transparent,
    borderStrong = c(0xFF6B7280, .25f),
    accent = c(0xFF6D5DFC),
    accentStrong = c(0xFF5648D6),
    accentSoft = c(0xFFD8D4FB),
    onAccent = Color.White,
    radiusCard = 20.dp,
    radiusBtn = 12.dp,
    radiusField = 12.dp,
    radiusChip = 10.dp,
    serifTitle = false,
    neumorph = true,
    isDark = false,
)

private val Natural = Palette(
    theme = AppTheme.Natural,
    bg = c(0xFFFAF6F1),
    card = c(0xFFFFFDF9),
    cardSoft = c(0xFFF3ECE2),
    topBar = c(0xFFFAF6F1),
    sideBar = c(0xFFFAF6F1),
    text = c(0xFF44403C),
    textSub = c(0xFF78716C),
    textWeak = c(0xFFA8A29E),
    border = c(0xFFE7DDD0),
    borderStrong = c(0xFFD6C8B6),
    accent = c(0xFF5C4033),
    accentStrong = c(0xFF3D2B22),
    accentSoft = c(0xFFE9E0D3),
    onAccent = c(0xFFFAF6F1),
    radiusCard = 26.dp,
    radiusBtn = 999.dp,
    radiusField = 18.dp,
    radiusChip = 999.dp,
    serifTitle = false,
    neumorph = false,
    isDark = false,
)

private val Macos = Palette(
    theme = AppTheme.Macos,
    bg = c(0xFF1C1C1E),
    card = c(0xFF2C2C2E),
    cardSoft = c(0xFF3A3A3C),
    topBar = c(0xFF1C1C1E),
    sideBar = c(0xFF242426),
    text = c(0xFFFFFFFF, .95f),
    textSub = c(0xFFFFFFFF, .70f),
    textWeak = c(0xFFFFFFFF, .40f),
    border = c(0xFFFFFFFF, .08f),
    borderStrong = c(0xFFFFFFFF, .25f),
    accent = c(0xFF0A84FF),
    accentStrong = c(0xFF409CFF),
    accentSoft = c(0xFF0A84FF, .18f),
    onAccent = Color.White,
    radiusCard = 12.dp,
    radiusBtn = 9.dp,
    radiusField = 9.dp,
    radiusChip = 8.dp,
    serifTitle = false,
    neumorph = false,
    isDark = true,
)

private val Clay = Palette(
    theme = AppTheme.Clay,
    bg = c(0xFFFDF2F5),
    bgBrush = Brush.linearGradient(listOf(c(0xFFFEF3C7), c(0xFFFCE7F3), c(0xFFEDE9FE))),
    card = c(0xFFFFFFFF),
    cardBrush = Brush.linearGradient(listOf(c(0xFFFFFFFF), c(0xFFFDF1F6))),
    cardSoft = c(0xFFFBE7F0),
    topBar = c(0xFFFFFFFF, .78f),
    sideBar = c(0xFFFFFFFF, .70f),
    text = c(0xFF831843),
    textSub = c(0xFF9D4869),
    textWeak = c(0xFFC68AA4),
    border = Color.Transparent,
    borderStrong = c(0xFFBE185D, .18f),
    accent = c(0xFFF472B6),
    accentStrong = c(0xFFDB2777),
    accentSoft = c(0xFFFBD3E4),
    onAccent = Color.White,
    radiusCard = 32.dp,
    radiusBtn = 20.dp,
    radiusField = 20.dp,
    radiusChip = 16.dp,
    serifTitle = false,
    neumorph = false,
    isDark = false,
)

/** 强调色变体 —— 只有 neumorphism / macos / clay 支持（与网站同规则） */
fun paletteOf(theme: AppTheme, accent: String): Palette {
    val base = when (theme) {
        AppTheme.Editorial -> Editorial
        AppTheme.Neumorphism -> Neumorphism
        AppTheme.Natural -> Natural
        AppTheme.Macos -> Macos
        AppTheme.Clay -> Clay
    }
    if (accent.isBlank()) return base

    val triple = when (theme) {
        AppTheme.Neumorphism -> when (accent) {
            "coral" -> Triple(c(0xFFFF6B6B), c(0xFFE85555), c(0xFFFFD9D9))
            "mint" -> Triple(c(0xFF4ECDC4), c(0xFF35B3AB), c(0xFFD3F5F2))
            "lemon" -> Triple(c(0xFFFFE66D), c(0xFFF2D649), c(0xFFFFF6C9))
            else -> null
        }
        AppTheme.Macos -> when (accent) {
            "violet" -> Triple(c(0xFFBF5AF2), c(0xFFD18CF7), c(0xFFBF5AF2, .20f))
            "rose" -> Triple(c(0xFFFF375F), c(0xFFFF6482), c(0xFFFF375F, .20f))
            "green" -> Triple(c(0xFF30D158), c(0xFF5FDD7F), c(0xFF30D158, .20f))
            else -> null
        }
        AppTheme.Clay -> when (accent) {
            "mint" -> Triple(c(0xFF5EEAD4), c(0xFF14B8A6), c(0xFFCFFAF1))
            "lilac" -> Triple(c(0xFFC4B5FD), c(0xFF8B5CF6), c(0xFFEDE9FE))
            "lemon" -> Triple(c(0xFFFDE68A), c(0xFFF59E0B), c(0xFFFEF3C7))
            else -> null
        }
        else -> null
    } ?: return base

    return base.copy(
        accent = triple.first,
        accentStrong = triple.second,
        accentSoft = triple.third,
        onAccent = if (theme == AppTheme.Neumorphism && accent == "lemon") c(0xFF4A4318) else base.onAccent,
    )
}

val LocalPalette = staticCompositionLocalOf { Editorial }

/** 卡片圆角（支持每套主题不同，clay 有意做成「软糯」的大圆角） */
fun Palette.cardShape() = RoundedCornerShape(radiusCard)
fun Palette.buttonShape() = RoundedCornerShape(radiusBtn)
fun Palette.fieldShape() = RoundedCornerShape(radiusField)
fun Palette.chipShape() = RoundedCornerShape(radiusChip)

// 无接收者版本：界面里直接写 chipShape() 即可（内部读当前主题）。
// 有这两个重载，Composable 里就不必到处 `val p = palette` 再 `p.chipShape()`。
@Composable fun cardShape(): RoundedCornerShape = LocalPalette.current.cardShape()
@Composable fun buttonShape(): RoundedCornerShape = LocalPalette.current.buttonShape()
@Composable fun fieldShape(): RoundedCornerShape = LocalPalette.current.fieldShape()
@Composable fun chipShape(): RoundedCornerShape = LocalPalette.current.chipShape()

private fun typographyFor(p: Palette): Typography {
    val head = if (p.serifTitle) FontFamily.Serif else FontFamily.Default
    val base = Typography()
    return base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = head, fontWeight = if (p.serifTitle) FontWeight.Normal else FontWeight.Bold, fontSize = 28.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = head, fontWeight = if (p.serifTitle) FontWeight.Normal else FontWeight.Bold, fontSize = 22.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = head, fontWeight = if (p.serifTitle) FontWeight.Normal else FontWeight.SemiBold, fontSize = 19.sp),
        titleLarge = base.titleLarge.copy(fontFamily = head, fontWeight = if (p.serifTitle) FontWeight.Normal else FontWeight.SemiBold, fontSize = 17.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp),
    )
}

@Composable
fun CheckinTheme(theme: AppTheme, accent: String = "", content: @Composable () -> Unit) {
    val p = paletteOf(theme, accent)
    val scheme = if (p.isDark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.cardSoft, onSurfaceVariant = p.textSub,
            outline = p.borderStrong, error = c(0xFFFF453A),
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.cardSoft, onSurfaceVariant = p.textSub,
            outline = p.borderStrong, error = c(0xFFD93025),
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typographyFor(p),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(p.radiusChip),
                small = RoundedCornerShape(p.radiusBtn),
                medium = RoundedCornerShape(p.radiusCard),
                large = RoundedCornerShape(p.radiusCard),
                extraLarge = RoundedCornerShape(p.radiusCard),
            ),
            content = content,
        )
    }
}

/** 便捷取色：界面里一律 val p = LocalPalette.current */
val palette: Palette
    @Composable get() = LocalPalette.current
