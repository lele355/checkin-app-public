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
            "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd"
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
        return ((b.time - a.time) / 86_400_000L).toInt()
    }

    fun weekdayIndex(dateStr: String?): Int {
        val d = parse(dateStr) ?: return 1
        val dow = Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1     // 1=周一 … 7=周日
    }

    /** 当前第几周（学期起始周一为第 1 周） */
    fun weekOf(semesterStart: String?, dateStr: String = today()): Int {
        val start = parse(semesterStart) ?: return 1
        val target = parse(dateStr) ?: return 1
        val diff = ((target.time - start.time) / 86_400_000L).toInt()
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
}
