package cn.anlele.checkin.util

import kotlin.math.abs

/**
 * 矩阵计算器的运算内核（2~4 阶）。
 * 行主序 List<List<Double>>；不可逆/奇异时抛 [CalcEngine.CalcException]（消息给用户看）。
 * 行列式与逆都走高斯消元，和手算同思路，数值上也稳（带部分主元）。
 */
object MatrixCalc {

    fun add(a: List<List<Double>>, b: List<List<Double>>): List<List<Double>> =
        a.zip(b) { r1, r2 -> r1.zip(r2) { x, y -> x + y } }

    fun sub(a: List<List<Double>>, b: List<List<Double>>): List<List<Double>> =
        a.zip(b) { r1, r2 -> r1.zip(r2) { x, y -> x - y } }

    fun mul(a: List<List<Double>>, b: List<List<Double>>): List<List<Double>> {
        val n = a.size
        return List(n) { i ->
            List(n) { j -> (0 until n).sumOf { k -> a[i][k] * b[k][j] } }
        }
    }

    fun transpose(a: List<List<Double>>): List<List<Double>> {
        val n = a.size
        return List(n) { i -> List(n) { j -> a[j][i] } }
    }

    /** 行列式：高斯消元成上三角后对角线连乘（部分主元，交换行变号） */
    fun det(a: List<List<Double>>): Double {
        val n = a.size
        val m = a.map { it.toMutableList() }.toMutableList()
        var sign = 1.0
        for (col in 0 until n) {
            var pivot = col
            for (r in col until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (abs(m[pivot][col]) < 1e-12) return 0.0
            if (pivot != col) {
                val t = m[col]; m[col] = m[pivot]; m[pivot] = t
                sign = -sign
            }
            for (r in col + 1 until n) {
                val f = m[r][col] / m[col][col]
                for (c in col until n) m[r][c] -= f * m[col][c]
            }
        }
        var d = sign
        for (i in 0 until n) d *= m[i][i]
        return d
    }

    /** 逆矩阵：增广单位阵做高斯-约当消元 */
    fun inv(a: List<List<Double>>): List<List<Double>> {
        val n = a.size
        val m = a.map { it.toMutableList() }.toMutableList()
        val id = List(n) { i -> MutableList(n) { j -> if (i == j) 1.0 else 0.0 } }.toMutableList()
        for (col in 0 until n) {
            var pivot = col
            for (r in col until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (abs(m[pivot][col]) < 1e-12) throw CalcEngine.CalcException("矩阵不可逆（行列式为 0）")
            if (pivot != col) {
                val t = m[col]; m[col] = m[pivot]; m[pivot] = t
                val ti = id[col]; id[col] = id[pivot]; id[pivot] = ti
            }
            val div = m[col][col]
            for (c in 0 until n) { m[col][c] /= div; id[col][c] /= div }
            for (r in 0 until n) {
                if (r == col) continue
                val f = m[r][col]
                if (abs(f) < 1e-15) continue
                for (c in 0 until n) {
                    m[r][c] -= f * m[col][c]
                    id[r][c] -= f * id[col][c]
                }
            }
        }
        return id
    }

    /** 数值格式：整数去小数点，长小数截 6 位 */
    fun fmt(v: Double): String {
        if (abs(v) < 1e-10) return "0"
        if (v == kotlin.math.floor(v) && abs(v) < 1e15) return v.toLong().toString()
        return String.format("%.6f", v).trimEnd('0').trimEnd('.')
    }
}
