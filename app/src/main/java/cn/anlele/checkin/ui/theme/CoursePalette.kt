package cn.anlele.checkin.ui.theme

import androidx.compose.ui.graphics.Color
import cn.anlele.checkin.data.CourseDto

/**
 * 课程配色 —— 与网页端 `Services/CoursePalette.cs` 同一套规则，两端出来的颜色必须一样。
 *
 * 【为什么按课程而不是按类别】以前颜色 = 必修/院选/校选/其它 四档，
 * 一学期六七门必修全是一个紫，整片课表糊成一块色，反而看不出「哪门课」。
 *
 * 【同名课程同色】一门课一周上三次是常态（周一理论、周三实验），本来就该一个颜色。
 * 所以默认色由归一化后的课程名做哈希算出来 —— 不落库也稳定，重装、换设备都一样。
 *
 * 【何时才落库】用户手动改色才写 Course.Color（服务端字段）。默认色不写库的好处：
 * 改色板不用迁移数据，已有课程自动跟着变。
 */
object CoursePalette {

    /** 色板键，顺序即「自动分配撞色时的错开顺序」—— 把对比强的排在相邻位置 */
    val Keys = listOf("violet", "blue", "teal", "amber", "rose", "green", "indigo", "orange")

    /** 色板键 → 中文名（色块本身才是主体，名字只是给读屏 / 无障碍用） */
    val Labels = mapOf(
        "violet" to "紫", "blue" to "蓝", "teal" to "青", "amber" to "琥珀",
        "rose" to "玫红", "green" to "绿", "indigo" to "靛蓝", "orange" to "橙",
    )

    /**
     * 前景色。浅色主题用 600~700 档（压在浅底上才够沉），
     * 深色主题（macos 暗色）用 300 档 —— 同一串色号在深底上会糊成一团黑。
     */
    private val lightFg = listOf(
        Color(0xFF6D28D9), // violet
        Color(0xFF1D4ED8), // blue
        Color(0xFF0F766E), // teal
        Color(0xFFB45309), // amber
        Color(0xFFBE123C), // rose
        Color(0xFF15803D), // green
        Color(0xFF4338CA), // indigo
        Color(0xFFC2410C), // orange
    )
    private val darkFg = listOf(
        Color(0xFFC4B5FD), Color(0xFF93C5FD), Color(0xFF5EEAD4), Color(0xFFFCD34D),
        Color(0xFFFDA4AF), Color(0xFF86EFAC), Color(0xFFA5B4FC), Color(0xFFFDBA74),
    )

    private fun indexOf(key: String): Int = Keys.indexOf(key).let { if (it < 0) 0 else it }

    fun fg(key: String, isDark: Boolean): Color =
        (if (isDark) darkFg else lightFg)[indexOf(key)]

    /**
     * 底色不由色板写死，而是「前景色 + 低透明度」叠在卡片上。
     * 好处是五套主题（含 clay 渐变、neusp 软塑）都自动适配，
     * 不必像网页那样为每套主题各写一份 --cc-bg。
     */
    /**
     * 课程块底色 —— **不透明**的亮色。
     *
     * 【为什么不再是「前景色 + 透明度」】半透明色叠在卡片底上会被底色拉灰：
     * 七门课放在一起看，全是同一片灰蓝灰粉，颜色等于没起作用。
     * 参考课程格子类 App 的做法：一块课就是一块实色，宁可亮一点。
     * 深色主题用低明度同色系（不能直接搬浅色那组，白底亮色在暗底上会刺眼）。
     */
    private val lightBg = listOf(
        Color(0xFFEDE4FF), // violet
        Color(0xFFDCEBFF), // blue
        Color(0xFFD7F4EC), // teal
        Color(0xFFFFF0CC), // amber
        Color(0xFFFFE1EA), // rose
        Color(0xFFDDF5E4), // green
        Color(0xFFE4E7FF), // indigo
        Color(0xFFFFE7D6), // orange
    )
    private val darkBg = listOf(
        Color(0xFF3B2E5C), Color(0xFF1E3A5F), Color(0xFF12463F), Color(0xFF4A3708),
        Color(0xFF5A1F2E), Color(0xFF14432A), Color(0xFF2B2C5E), Color(0xFF5A2C10),
    )

    fun bg(key: String, isDark: Boolean): Color =
        (if (isDark) darkBg else lightBg)[indexOf(key)]

    /** 非本周课程：灰块 + 灰字（与参考 App 一致 —— 明确「这周不上」，但还看得见排课） */
    fun offBg(isDark: Boolean): Color = if (isDark) Color(0xFF34343A) else Color(0xFFEDEBE8)
    fun offFg(isDark: Boolean): Color = if (isDark) Color(0xFF9C9CA2) else Color(0xFF86837E)

    /**
     * 课程名归一键：忽略大小写、空白、全角半角和常见分隔符的差异。
     * 「高等数学(上)」「高等数学（上）」「高等数学 上」应当同色。
     */
    fun nameKey(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val sb = StringBuilder(name.length)
        for (raw in name) {
            val c = when (raw) {
                '（' -> '('
                '）' -> ')'
                '　' -> ' '
                '，', '、' -> ','
                '：' -> ':'
                else -> raw
            }
            // .NET 的 char.IsLetterOrDigit 与 Kotlin 的 isLetterOrDigit 对汉字判定一致（都算 letter）
            if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }

    /**
     * 课程名 → 默认色。用 FNV-1a 而不是 hashCode()：
     * Kotlin/JVM 的 String.hashCode 虽然稳定，但和 C# 的算法不一样 ——
     * 两端算出来必须是同一个颜色，所以统一用 FNV-1a 手算。
     */
    fun defaultFor(name: String?): String {
        val key = nameKey(name)
        if (key.isEmpty()) return Keys[0]
        var h = 2166136261u
        for (ch in key) {
            h = h xor ch.code.toUInt()
            h *= 16777619u
        }
        return Keys[(h % Keys.size.toUInt()).toInt()]
    }

    /** 落库值优先，没设过就按名字算 */
    fun of(c: CourseDto): String =
        normalize(c.color).ifEmpty { defaultFor(c.name) }

    /** 规范化：脏值一律当「没设」，交给默认色 */
    fun normalize(key: String?): String {
        val k = key?.trim()?.lowercase() ?: return ""
        return if (k in Keys) k else ""
    }
}
