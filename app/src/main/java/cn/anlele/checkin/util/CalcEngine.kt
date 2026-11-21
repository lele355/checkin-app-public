package cn.anlele.checkin.util

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * 表达式求值引擎（简单/科学计算器共用），功能面贴近卡西欧 82MS：
 *
 * - 数字：小数与科学计数法（1.2e3）
 * - 运算：+ - × ÷（* / 同义）、^ 幂、V 开方（a V b = a 的 b 次方根）、
 *   P 排列（nPr）、C 组合（nCr）
 * - 后缀：% 百分号、! 阶乘、⁻¹ 倒数
 * - 函数：sin cos tan / asin acos atan / sinh cosh tanh / asinh acosh atanh /
 *   ln log sqrt cbrt abs floor ceil exp
 * - 常量：pi（π）、e、Ans（上一次结果，由 [eval] 的 ans 参数传入）
 * - 角度：DEG/RAD 切换（三角函数入参与反三角出参都跟着走）
 * - 括号与一元负号
 *
 * 实现：分词 → 调度场算法 → RPN 求值。抛 [CalcException]，消息是给用户看的短句。
 */
object CalcEngine {

    class CalcException(message: String) : Exception(message)

    private sealed interface Token {
        data class Num(val v: Double) : Token
        data class Op(val ch: Char) : Token          // + - * / ^ V P C
        data class Fn(val name: String) : Token
        data object Lp : Token
        data object Rp : Token
        data class Postfix(val ch: Char) : Token     // % 百分号 / ! 阶乘 / r 倒数（⁻¹）
        data class Unary(val ch: Char) : Token       // 一元负号/正号
    }

    private val FUNCS = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "ln", "log", "sqrt", "cbrt", "abs", "floor", "ceil", "exp",
    )

    /** 弧度/角度开关：三角函数入参、反三角出参都跟着 [deg] 走 */
    fun eval(expr: String, ans: Double = 0.0, deg: Boolean = true): Double {
        val src = expr.replace('×', '*').replace('÷', '/').replace('−', '-')
        if (src.isBlank()) throw CalcException("空表达式")
        val rpn = toRpn(tokenize(src, ans))
        return evalRpn(rpn, deg)
    }

    // ---------- 分词 ----------
    private fun tokenize(s: String, ans: Double): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { out += Token.Lp; i++ }
                c == ')' -> { out += Token.Rp; i++ }
                c == '+' || c == '-' -> {
                    // 表达式开头、左括号后、运算符后 = 一元符号
                    val unary = out.isEmpty() || out.last() is Token.Lp || out.last() is Token.Op
                    out += if (unary) Token.Unary(c) else Token.Op(c)
                    i++
                }
                c == '*' || c == '/' || c == '^' -> { out += Token.Op(c); i++ }
                c == 'V' -> { out += Token.Op('V'); i++ }        // 开方：a V b = a^(1/b)
                c == 'P' -> { out += Token.Op('P'); i++ }        // 排列 nPr
                c == 'C' -> { out += Token.Op('C'); i++ }        // 组合 nCr
                c == '%' -> { out += Token.Postfix('%'); i++ }
                c == '!' -> { out += Token.Postfix('!'); i++ }
                c == '⁻' -> {                                  // 倒数 ⁻¹（¹ 可带可不带）
                    out += Token.Postfix('r')
                    i++
                    if (i < s.length && s[i] == '¹') i++
                }
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) {
                        // e/E 只在后面跟数字或符号时算科学计数法
                        if ((s[i] == 'e' || s[i] == 'E') && i + 1 < s.length &&
                            (s[i + 1].isDigit() || s[i + 1] == '+' || s[i + 1] == '-')
                        ) i++ else if (s[i] == 'e' || s[i] == 'E') break else i++
                    }
                    val text = s.substring(start, i)
                    val v = text.toDoubleOrNull() ?: throw CalcException("数字格式不对：$text")
                    out += Token.Num(v)
                }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && (s[i].isLetter() || s[i] == '⁻' || s[i] == '¹')) i++   // sin⁻¹ 之类不拆
                    val raw = s.substring(start, i)
                    // 「⁻¹」= 反函数记法（sin⁻¹ = asin），先摘掉再认名
                    val inv = raw.endsWith("⁻¹") || raw.endsWith("⁻")
                    val name = raw.removeSuffix("⁻¹").removeSuffix("⁻").lowercase()
                    when {
                        name == "pi" || name == "p" -> out += Token.Num(Math.PI)
                        name == "e" -> out += Token.Num(Math.E)
                        name == "ans" -> out += Token.Num(ans)
                        inv && name == "sin" -> out += Token.Fn("asin")
                        inv && name == "cos" -> out += Token.Fn("acos")
                        inv && name == "tan" -> out += Token.Fn("atan")
                        !inv && name in FUNCS -> out += Token.Fn(name)
                        else -> throw CalcException("不认识的函数：$raw")
                    }
                }
                else -> throw CalcException("不能识别「$c」")
            }
        }
        return out
    }

    private fun prec(t: Token): Int = when (t) {
        is Token.Unary -> 4
        is Token.Op -> if (t.ch == '^' || t.ch == 'V' || t.ch == 'P' || t.ch == 'C') 3 else 2
        is Token.Postfix -> 5
        else -> 0
    }

    // ---------- 调度场 ----------
    private fun toRpn(tokens: List<Token>): List<Token> {
        val out = ArrayList<Token>()
        val stack = ArrayDeque<Token>()
        for (t in tokens) {
            when (t) {
                is Token.Num -> out += t
                is Token.Fn, is Token.Unary -> stack.addLast(t)
                is Token.Postfix -> out += t          // 后缀直接出
                is Token.Op -> {
                    while (stack.isNotEmpty()) {
                        val top = stack.last()
                        val pTop = prec(top)
                        if (pTop > prec(t) || (pTop == prec(t) && t.ch != '^')) {
                            out += stack.removeLast()
                        } else break
                    }
                    stack.addLast(t)
                }
                Token.Lp -> stack.addLast(t)
                Token.Rp -> {
                    while (stack.isNotEmpty() && stack.last() !is Token.Lp) out += stack.removeLast()
                    if (stack.isEmpty()) throw CalcException("括号不配对")
                    stack.removeLast()
                    if (stack.isNotEmpty() && stack.last() is Token.Fn) out += stack.removeLast()
                }
            }
        }
        while (stack.isNotEmpty()) {
            val t = stack.removeLast()
            if (t is Token.Lp) throw CalcException("括号不配对")
            out += t
        }
        return out
    }

    // ---------- RPN 求值 ----------
    private fun evalRpn(rpn: List<Token>, deg: Boolean): Double {
        val st = ArrayDeque<Double>()
        fun pop(): Double = st.removeLastOrNull() ?: throw CalcException("表达式不完整")

        // 角度换算：DEG 模式下三角入参转弧度、反三角出参转角度
        fun toRad(v: Double) = if (deg) v * Math.PI / 180.0 else v
        fun fromRad(v: Double) = if (deg) v * 180.0 / Math.PI else v

        for (t in rpn) {
            when (t) {
                is Token.Num -> st.addLast(t.v)
                is Token.Unary -> st.addLast(if (t.ch == '-') -pop() else pop())
                is Token.Postfix -> {
                    val v = pop()
                    st.addLast(
                        when (t.ch) {
                            '%' -> v / 100.0
                            'r' -> if (v == 0.0) throw CalcException("0 不能取倒数") else 1.0 / v
                            '!' -> {
                                if (v < 0 || v != floor(v)) throw CalcException("阶乘只要非负整数")
                                if (v > 170) throw CalcException("阶乘太大了")
                                var r = 1.0
                                for (k in 2..v.toInt()) r *= k
                                r
                            }
                            else -> throw CalcException("内部错误")
                        }
                    )
                }
                is Token.Op -> {
                    val b = pop(); val a = pop()
                    st.addLast(
                        when (t.ch) {
                            '+' -> a + b
                            '-' -> a - b
                            '*' -> a * b
                            '/' -> if (b == 0.0) throw CalcException("不能除以 0") else a / b
                            '^' -> a.pow(b)
                            'V' -> nthRoot(a, b)
                            'P' -> perm(a, b)
                            'C' -> comb(a, b)
                            else -> throw CalcException("内部错误")
                        }
                    )
                }
                is Token.Fn -> {
                    val v = pop()
                    st.addLast(
                        when (t.name) {
                            "sin" -> sin(toRad(v)); "cos" -> cos(toRad(v)); "tan" -> tan(toRad(v))
                            "asin" -> {
                                if (v < -1 || v > 1) throw CalcException("asin 定义域 [-1,1]")
                                fromRad(asin(v))
                            }
                            "acos" -> {
                                if (v < -1 || v > 1) throw CalcException("acos 定义域 [-1,1]")
                                fromRad(acos(v))
                            }
                            "atan" -> fromRad(atan(v))
                            "sinh" -> sinh(v); "cosh" -> cosh(v); "tanh" -> tanh(v)
                            "asinh" -> ln(v + sqrt(v * v + 1))
                            "acosh" -> {
                                if (v < 1) throw CalcException("acosh 要求 ≥ 1")
                                ln(v + sqrt(v * v - 1))
                            }
                            "atanh" -> {
                                if (v <= -1 || v >= 1) throw CalcException("atanh 定义域 (-1,1)")
                                0.5 * ln((1 + v) / (1 - v))
                            }
                            "ln" -> if (v <= 0) throw CalcException("ln 要求大于 0") else ln(v)
                            "log" -> if (v <= 0) throw CalcException("log 要求大于 0") else log10(v)
                            "sqrt" -> if (v < 0) throw CalcException("负数没有实平方根") else sqrt(v)
                            "cbrt" -> Math.cbrt(v)
                            "abs" -> abs(v); "floor" -> floor(v); "ceil" -> ceil(v); "exp" -> exp(v)
                            else -> throw CalcException("内部错误")
                        }
                    )
                }
                else -> throw CalcException("内部错误")
            }
        }
        val r = pop()
        if (st.isNotEmpty()) throw CalcException("表达式不完整")
        return r
    }

    /** a 的 b 次方根（负数只在 b 为奇数整数时有实数解） */
    private fun nthRoot(a: Double, b: Double): Double {
        if (b == 0.0) throw CalcException("根次不能为 0")
        return when {
            a >= 0 -> a.pow(1.0 / b)
            b == floor(b) && b.toInt() % 2 != 0 -> -((-a).pow(1.0 / b))
            else -> throw CalcException("负数的偶次方根没有实数解")
        }
    }

    /** 排列 P(n, r) = n!/(n-r)!，连乘算避免阶乘溢出 */
    private fun perm(n: Double, r: Double): Double {
        if (n < 0 || r < 0 || n != floor(n) || r != floor(r)) throw CalcException("排列只要非负整数")
        if (r > n) throw CalcException("排列 r 不能大于 n")
        var res = 1.0
        for (k in 0 until r.toInt()) res *= n - k
        return res
    }

    /** 组合 C(n, r) = n!/(r!(n-r)!)，逐项乘除防溢出 */
    private fun comb(n: Double, r: Double): Double {
        if (n < 0 || r < 0 || n != floor(n) || r != floor(r)) throw CalcException("组合只要非负整数")
        if (r > n) throw CalcException("组合 r 不能大于 n")
        val k = minOf(r.toInt(), (n - r).toInt())
        var res = 1.0
        for (i in 1..k) res = res * (n - k + i) / i
        return res
    }

    /** 结果展示：整数不带小数点，长小数截 10 位去尾零 */
    fun fmt(v: Double): String {
        if (v.isNaN() || v.isInfinite()) throw CalcException("结果无意义")
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        return String.format("%.10f", v).trimEnd('0').trimEnd('.')
    }
}
