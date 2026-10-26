package cn.anlele.checkin.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日期工具 —— 全部走 java.util.Calendar / SimpleDateFormat。
 *
 * 为什么不用 java.time：minSdk 24，java.time 要开 core library desugaring 才可用；
 * 这里的需求（格式化、加减天、月历网格）用 Calendar 完全够，少一个构建配置就少一个坑。
 *
 * 约定：日期一律是「本地墙钟时间」字符串，与后端下发格式一致：
 *   日期 yyyy-MM-dd ／ 时间点 yyyy-MM-ddTHH:mm ／ 带秒 yyyy-MM-ddTHH:mm:ss
 */
object Dates {

    private val CN_WEEK = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    fun nowHm(): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())

    fun parse(s: String?): Date? {
        if (s.isNullOrBlank()) return null
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            // 后端偶有带毫秒 / 带时区后缀的写法（如 2026-10-26T09:00:00.000Z），
            // 不解就整条成空 —— 日程时间直接不显示。多备两个格式兜住。
            "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (p in patterns) {
            try {
                val f = SimpleDateFormat(p, Locale.CHINA)
                f.isLenient = false
                return f.parse(s)
            } catch (_: Exception) { }
        }
        return null
    }

    fun fmt(date: Date?, pattern: String): String =
        if (date == null) "" else SimpleDateFormat(pattern, Locale.CHINA).format(date)

    fun fmt(s: String?, pattern: String): String = fmt(parse(s), pattern)

    /** 2026-10-23 → 10月23日 */
    fun dayMonth(s: String?): String = fmt(s, "M月d日")

    /** 2026-10-23T09:00 → 09:00 */
    fun hhmm(s: String?): String = fmt(s, "HH:mm")

    /** 2026-10-23T09:00 → 10月23日 09:00 */
    fun dayTime(s: String?): String = fmt(s, "M月d日 HH:mm")

    fun weekdayCn(s: String?): String {
        val d = parse(s) ?: return ""
        val c = Calendar.getInstance().apply { time = d }
        return CN_WEEK[c.get(Calendar.DAY_OF_WEEK) - 1]
    }

    fun addDays(dateStr: String, n: Int): String {
        val c = Calendar.getInstance().apply { time = parse(dateStr) ?: Date() }
        c.add(Calendar.DAY_OF_MONTH, n)
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(c.time)
    }

    fun addMonths(ym: String, n: Int): String {
        val c = Calendar.getInstance().apply { time = parse("$ym-01") ?: Date() }
        c.add(Calendar.MONTH, n)
        return SimpleDateFormat("yyyy-MM", Locale.CHINA).format(c.time)
    }

    fun thisMonth(): String = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date())

    fun monthTitle(ym: String): String = fmt("$ym-01", "yyyy年M月")

    /** 相对日期：今天 / 明天 / 昨天 / 10月24日 */
    fun relative(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val d = dateStr.take(10)
        val t = today()
        return when (d) {
            t -> "今天"
            addDays(t, 1) -> "明天"
            addDays(t, -1) -> "昨天"
            else -> dayMonth(d)
        }
    }

    /** 日程时间文案：全天 / 09:00 / 09:00 ~ 10:00（跨天会自动带日期） */
    fun range(startAt: String?, endAt: String?, allDay: Boolean, withDate: Boolean = false): String {
        if (allDay) return if (withDate) "${relative(startAt)} 全天" else "全天"
        val s = parse(startAt) ?: return ""
        val head = if (withDate) "${relative(startAt)} ${fmt(s, "HH:mm")}" else fmt(s, "HH:mm")
        val e = parse(endAt) ?: return head
        val sameDay = fmt(s, "yyyy-MM-dd") == fmt(e, "yyyy-MM-dd")
        return if (sameDay) "$head ~ ${fmt(e, "HH:mm")}" else "$head ~ ${fmt(e, "M月d日 HH:mm")}"
    }

    /** 月历网格：周日开头，6 行 × 7 列，返回 null 表示补位空格 */
    fun monthGrid(ym: String): List<String?> {
        val first = parse("$ym-01") ?: return emptyList()
        val c = Calendar.getInstance().apply {
            time = first
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDow = c.get(Calendar.DAY_OF_WEEK) - 1        // 0 = 周日
        val cells = ArrayList<String?>(42)
        repeat(firstDow) { cells.add(null) }
        for (d in 1..daysInMonth) {
            cells.add(String.format(Locale.CHINA, "%s-%02d", ym, d))
        }
        while (cells.size < 42) cells.add(null)
        return cells
    }

    fun dayOfMonth(dateStr: String?): Int {
        val d = parse(dateStr) ?: return 0
        return Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_MONTH)
    }

    fun daysBetween(from: String?, to: String?): Int {
        val a = parse(from) ?: return 0
        val b = parse(to) ?: return 0
        // 不能用 (b - a) / 86_400_000：夏令时切换那天只有 23 小时，
        // 除下来会差一天（海外时区的用户会看到「还剩 2 天」变成 1 天）。
        // 改成按「公历日序号」相减，纯整数运算，与时区、夏令时无关。
        val ca = Calendar.getInstance().apply { time = a }
        val cb = Calendar.getInstance().apply { time = b }
        return (jdn(cb) - jdn(ca)).toInt()
    }

    /** 公历 → 儒略日序号（只取年月日，不含时分秒，与时区无关） */
    private fun jdn(c: Calendar): Long {
        val y = c.get(Calendar.YEAR).toLong()
        val m = (c.get(Calendar.MONTH) + 1).toLong()
        val d = c.get(Calendar.DAY_OF_MONTH).toLong()
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045
    }

    fun weekdayIndex(dateStr: String?): Int {
        val d = parse(dateStr) ?: return 1
        val dow = Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1     // 1=周一 … 7=周日
    }

    /** 当前第几周（学期起始周一为第 1 周）。
     *  开学前（diff < 0）一律返回 1 —— 以前负数整除会算出第 0 周甚至第 -1 周，
     *  课程表标题就会出现「第 0 教学周」。 */
    fun weekOf(semesterStart: String?, dateStr: String = today()): Int {
        if (semesterStart.isNullOrBlank()) return 1
        val diff = daysBetween(semesterStart, dateStr)
        if (diff < 0) return 1
        return (diff / 7) + 1
    }

    fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 6 -> "夜深了"
            h < 11 -> "早上好"
            h < 14 -> "中午好"
            h < 18 -> "下午好"
            else -> "晚上好"
        }
    }

    fun money(v: Double): String =
        if (v == v.toLong().toDouble()) "%.0f".format(v) else "%.2f".format(v)

    // ==================== 日期选择器用的辅助 ====================

    /** 2026-10-26 → 「2026年10月26日 周一」（选择器触发器上显示用） */
    fun cnDate(dateStr: String?): String {
        val d = parse(dateStr) ?: return ""
        val base = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(d)
        return "$base ${weekdayCn(dateStr)}"
    }

    /**
     * 月历网格（周一开头，6 行 × 7 列）：元素是 yyyy-MM-dd，null 表示补位空格。
     * 和 [monthGrid] 的区别是周起点：这里周一开头，跟着国内日历习惯走
     * （日程页那张月历用周日开头，两处并存，别互相替换）。
     */
    fun monthGridMon(ym: String): List<String?> {
        val first = parse("$ym-01") ?: return emptyList()
        val c = Calendar.getInstance().apply { time = first }
        val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dow = c.get(Calendar.DAY_OF_WEEK)          // 1=周日 … 7=周六
        val offset = (dow + 5) % 7                      // 周一=0 … 周日=6
        val cells = ArrayList<String?>(42)
        repeat(offset) { cells.add(null) }
        for (d in 1..daysInMonth) cells.add(String.format(Locale.CHINA, "%s-%02d", ym, d))
        while (cells.size < 42) cells.add(null)
        return cells
    }

    /** 月历表头，与 [monthGridMon] 的列顺序对应 */
    val WEEK_HEAD: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /**
     * 取该日期所在周的周一。
     * 学期起始日只认「第一周的周一」，让用户随手选一天、系统自动吸附过去，
     * 比要求用户自己算「那天是周几」可靠得多。
     */
    fun mondayOf(dateStr: String): String {
        val d = parse(dateStr) ?: return dateStr
        val dow = Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_WEEK)
        return addDays(dateStr, -((dow + 5) % 7))
    }

}
