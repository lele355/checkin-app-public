package cn.anlele.checkin.util

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 表达式求值引擎（简单/科学计算器共用）。
 *
 * 语法：数字（支持小数与科学计数法 1.2e3）、+ - × ÷（* / 同义）、^ 幂、% 百分号（后缀）、
 * ! 阶乘（后缀）、括号、函数（sin/cos/tan/asin/acos/atan/ln/log/sqrt/abs/floor/ceil/exp）、
 * 常量 pi（π）与 e、一元负号。
 *
 * 实现：分词 → 调度场算法（函数与一元负号按优先级入栈）→ RPN 求值。
 * 抛 [CalcException]，消息是给用户看的短句。
 */
object CalcEngine {

    class CalcException(message: String) : Exception(message)

    private sealed interface Token {
        data class Num(val v: Double) : Token
        data class Op(val ch: Char) : Token          // + - * / ^
        data class Fn(val name: String) : Token
        data object Lp : Token
        data object Rp : Token
        data class Postfix(val ch: Char) : Token             // % 百分号 / ! 阶乘（后缀）
        data class Unary(val ch: Char) : Token       // 一元负号/正号
    }

    fun eval(expr: String): Double {
        val src = expr.replace('×', '*').replace('÷', '/').replace('π', 'p').replace("pi", "p", ignoreCase = true)
        if (src.isBlank()) throw CalcException("空表达式")
        val rpn = toRpn(tokenize(src))
        return evalRpn(rpn)
    }

    // ---------- 分词 ----------
    private fun tokenize(s: String): List<Token> {
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
                    val unary = out.isEmpty() || out.last() is Token.Lp ||
                            (out.last() is Token.Op)
                    out += if (unary) Token.Unary(c) else Token.Op(c)
                    i++
                }
                c == '*' || c == '/' || c == '^' -> { out += Token.Op(c); i++ }
                c == '%' -> { out += Token.Postfix('%'); i++ }
                c == '!' -> { out += Token.Postfix('!'); i++ }
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
                c == 'p' -> { out += Token.Num(Math.PI); i++ }   // pi/π 归一成 p
                c == 'e' -> { out += Token.Num(Math.E); i++ }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    val name = s.substring(start, i).lowercase()
                    if (name in FUNCS) out += Token.Fn(name)
                    else throw CalcException("不认识的函数：$name")
                }
                else -> throw CalcException("不能识别「$c」")
            }
        }
        return out
    }

    private val FUNCS = setOf("sin", "cos", "tan", "asin", "acos", "atan", "ln", "log", "sqrt", "abs", "floor", "ceil", "exp")

    private fun prec(t: Token): Int = when (t) {
        is Token.Unary -> 4
        is Token.Op -> if (t.ch == '^') 3 else 2
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
    private fun evalRpn(rpn: List<Token>): Double {
        val st = ArrayDeque<Double>()
        fun pop(): Double = st.removeLastOrNull() ?: throw CalcException("表达式不完整")
        for (t in rpn) {
            when (t) {
                is Token.Num -> st.addLast(t.v)
                is Token.Unary -> st.addLast(if (t.ch == '-') -pop() else pop())
                is Token.Postfix -> {
                    val v = pop()
                    st.addLast(
                        when (t.ch) {
                            '%' -> v / 100.0
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
                            else -> throw CalcException("内部错误")
                        }
                    )
                }
                is Token.Fn -> {
                    val v = pop()
                    st.addLast(
                        when (t.name) {
                            "sin" -> sin(v); "cos" -> cos(v); "tan" -> tan(v)
                            "asin" -> asin(v); "acos" -> acos(v); "atan" -> atan(v)
                            "ln" -> if (v <= 0) throw CalcException("ln 要求大于 0") else ln(v)
                            "log" -> if (v <= 0) throw CalcException("log 要求大于 0") else log10(v)
                            "sqrt" -> if (v < 0) throw CalcException("负数没有实平方根") else sqrt(v)
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

    /** 结果展示：整数不带小数点，长小数截 10 位去尾零 */
    fun fmt(v: Double): String {
        if (v.isNaN() || v.isInfinite()) throw CalcException("结果无意义")
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        return String.format("%.10f", v).trimEnd('0').trimEnd('.')
    }
}
