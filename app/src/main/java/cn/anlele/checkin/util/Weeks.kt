package cn.anlele.checkin.util

/**
 * 课程周次的解析与生成 —— 与后端 Services/Weeks.cs 保持同一套语法。
 *
 * 为什么需要它：后端把周次存成一个字符串（"1-16" / "1-8,10-16" / "单周"），
 * 以前界面上让用户在输入框里直接敲这个字符串，等于要求他先学会语法。
 * 现在界面改用「把周次画成格子、点哪周就是哪周」，这个文件负责
 *   旧数据（字符串）→ 勾选状态
 *   勾选状态 → 字符串（仍然存成后端认的格式，接口不用改）
 */
object Weeks {

    /** 一学期最多按 20 周算（超过的周次在界面上不显示，但字符串原样保留） */
    const val MAX = 20

    /**
     * "1-16" / "1-8,10-16" / "单周" / "双周" / "1,3,5" → 命中周集合。
     * 空文本按「每周都有」处理（与后端 Weeks.Includes 一致）。
     */
    fun parse(text: String?, max: Int = MAX): Set<Int> {
        val t = clean(text)
        if (t.isEmpty()) return (1..max).toSet()

        val hit = sortedSetOf<Int>()
        t.split(',', '，', '、').forEach { raw ->
            val s = raw.trim().trimStart('第').trimEnd('周').trim()
            if (s.isEmpty()) return@forEach
            if (s == "单周" || s == "单") { (1..max).filter { it % 2 == 1 }.forEach { hit.add(it) }; return@forEach }
            if (s == "双周" || s == "双") { (1..max).filter { it % 2 == 0 }.forEach { hit.add(it) }; return@forEach }

            val m = Regex("""^(\d+)\s*[-~～—]\s*(\d+)$""").find(s)
            if (m != null) {
                val a = m.groupValues[1].toIntOrNull() ?: return@forEach
                val b = m.groupValues[2].toIntOrNull() ?: return@forEach
                (minOf(a, b)..maxOf(a, b)).forEach { if (it in 1..max) hit.add(it) }
                return@forEach
            }
            s.toIntOrNull()?.let { if (it in 1..max) hit.add(it) }
        }
        return hit
    }

    /**
     * 周集合 → 紧凑字符串。连续 3 周以上压成区间（1-16），零散的单点用逗号（1,3,5）。
     * 全选时输出 "1-max"，与旧数据格式一致。
     */
    fun format(weeks: Set<Int>, max: Int = MAX): String {
        val list = weeks.filter { it in 1..max }.sorted()
        if (list.isEmpty()) return ""
        if (list.size == max) return "1-$max"

        val parts = mutableListOf<String>()
        var i = 0
        while (i < list.size) {
            var j = i
            while (j + 1 < list.size && list[j + 1] == list[j] + 1) j++
            if (j - i >= 1) parts.add("${list[i]}-${list[j]}") else parts.add(list[i].toString())
            i = j + 1
        }
        return parts.joinToString(",")
    }

    /** 展示用文案："第 1-16 周" / "单周" / "双周" */
    fun display(text: String?): String {
        val t = clean(text)
        if (t.isEmpty()) return "第 1-${MAX} 周"
        if (t == "单周" || t == "单") return "单周"
        if (t == "双周" || t == "双") return "双周"
        return "第 $t 周"
    }

    private fun clean(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return s.trim().trimStart('第').trimEnd('周').trim()
    }
}
