package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * 飞行棋十字盘（中国飞行棋）—— §9.2 坐标表与网页 boards/ludo.js 同源。
 * 15×15 网格；主环 52 格 color(i)=i%4；四角 6×6 机场；每色 6 格终点航道汇入中央 3×3；
 * 虚线捷径（入口→落点 +12）；✈ 飞机棋子（F8）；movable 高亮由服务端快照直供。
 * 固定色不跟主题（红线 18）。
 */

// ---- §9.2 主环 52 格坐标表（顺时针，(col,row)） ----
private val ludoRing = listOf(
    1 to 6, 2 to 6, 3 to 6, 4 to 6, 5 to 6,
    6 to 5, 6 to 4, 6 to 3, 6 to 2, 6 to 1, 6 to 0,
    7 to 0, 8 to 0,
    8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5,
    9 to 6, 10 to 6, 11 to 6, 12 to 6, 13 to 6, 14 to 6,
    14 to 7, 14 to 8,
    13 to 8, 12 to 8, 11 to 8, 10 to 8, 9 to 8,
    8 to 9, 8 to 10, 8 to 11, 8 to 12, 8 to 13, 8 to 14,
    7 to 14, 6 to 14,
    6 to 13, 6 to 12, 6 to 11, 6 to 10, 6 to 9,
    5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8,
    0 to 7, 0 to 6,
)

// 每色 6 格终点航道（h=0..5 → pos 52..57），中心 (7,7) = pos 58
private val ludoRunway = mapOf(
    1 to listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7, 6 to 7),
    2 to listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5, 7 to 6),
    3 to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7, 8 to 7),
    4 to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9, 7 to 8),
)
private val ludoCenter = 7 to 7

// 四角 6×6 机场（区域左上角格）：红左上 / 黄右上 / 蓝右下 / 绿左下
private val ludoAirport = mapOf(1 to (0 to 0), 2 to (9 to 0), 3 to (9 to 9), 4 to (0 to 9))

// 起点 0/13/26/39 与虚线捷径入口→落点（start+16 → start+28，+12）
private val ludoStart = mapOf(1 to 0, 2 to 13, 3 to 26, 4 to 39)
private val ludoShortcut = mapOf(1 to (16 to 28), 2 to (29 to 41), 3 to (42 to 2), 4 to (3 to 15))

// color(i)=i%4：0 红 1 黄 2 蓝 3 绿；side k 色号 = k−1
private val ludoCellFill = listOf(Color(0xFFFFCDD2), Color(0xFFFFF9C4), Color(0xFFBBDEFB), Color(0xFFC8E6C9))
private val ludoRunFill = listOf(Color(0xFFEF9A9A), Color(0xFFFFF59D), Color(0xFF90CAF9), Color(0xFFA5D6A7))
private val ludoFull = listOf(Color(0xFFEF5350), Color(0xFFFFEE58), Color(0xFF42A5F5), Color(0xFF66BB6A))
private val ludoDark = listOf(Color(0xFFC62828), Color(0xFFF9A825), Color(0xFF1565C0), Color(0xFF2E7D32))

private fun ludoSideIdx(side: Int) = ((side - 1) % 4 + 4) % 4
private fun ludoColorIdx(i: Int) = ((i % 4) + 4) % 4

/** 飞机落点格（col,row）：-1 机场 / 0-51 主环 / 52-57 航道 / 58 中心 */
private fun ludoPlaneCell(pos: Int, side: Int): Pair<Int, Int>? = when {
    pos < 0 -> null
    pos < 52 -> ludoRing[pos]
    pos <= 57 -> ludoRunway[side]!![pos - 52]
    else -> ludoCenter
}

/** 机场停机位（按机号 p 稳定 2×2 分布，返回网格浮点坐标） */
private fun ludoAirportSlot(side: Int, p: Int): Pair<Float, Float> {
    val a = ludoAirport[side]!!
    return (a.first + if (p % 2 == 0) 1.5f else 4.5f) to (a.second + if (p < 2) 1.5f else 4.5f)
}

private data class LudoPlaneXY(val p: Int, val side: Int, val x: Float, val y: Float)

/** 计算全部飞机的画布坐标（绘制与点击命中共用，免两套换算） */
private fun ludoPlanePositions(pieces: List<LudoPiece>, cell: Float, sizeW: Float): List<LudoPlaneXY> {
    fun cx(col: Float) = (col + 0.5f) * cell
    fun cy(row: Float) = (row + 0.5f) * cell
    val out = mutableListOf<LudoPlaneXY>()
    val stacked = mutableMapOf<String, Int>()
    for (p in pieces) {
        if (p.side !in 1..4) continue
        val sx: Float
        val sy: Float
        if (p.pos < 0) {
            val slot = ludoAirportSlot(p.side, p.p)
            sx = cx(slot.first); sy = cy(slot.second)
        } else {
            val c = ludoPlaneCell(p.pos, p.side) ?: continue
            val key = "${c.first},${c.second}"
            val k = stacked[key] ?: 0
            stacked[key] = k + 1
            sx = cx(c.first.toFloat()) + ((k % 2) - 0.5f) * cell * 0.34f
            sy = cy(c.second.toFloat()) + ((k / 2 % 2) - 0.5f) * cell * 0.34f
        }
        out.add(LudoPlaneXY(p.p, p.side, sx, sy))
    }
    return out
}

/**
 * 十字盘。movable = 服务端算好的可走机号（可走机加橙色高亮圈）；
 * onTap 回传被点中的飞机机号（阈值外点击不回传）。
 */
@Composable
fun LudoBoard(
    pieces: List<LudoPiece>,
    lastRoll: Int? = null,
    movable: List<Int> = emptyList(),
    myTurn: Boolean = false,
    mySide: Int = 0,
    modifier: Modifier = Modifier,
    onTap: ((Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val planes = remember(pieces) { pieces }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(planes, movable, myTurn, mySide) {
                val cell = size.width / 15f
                val xys = ludoPlanePositions(planes, cell, size.width.toFloat())
                detectTapGestures { off ->
                    if (!myTurn || movable.isEmpty()) return@detectTapGestures
                    var best = -1
                    var bestD = cell * 0.62f
                    for (q in xys) {
                        if (q.side != mySide) continue   // 只认己方机（movable 是本方机号集）
                        val d = Math.abs(q.x - off.x) + Math.abs(q.y - off.y)
                        if (d < bestD) { bestD = d; best = q.p }
                    }
                    if (best >= 0 && movable.contains(best)) onTap?.invoke(best)
                }
            }
    ) {
        val cell = size.width / 15f
        fun cx(col: Float) = (col + 0.5f) * cell
        fun cy(row: Float) = (row + 0.5f) * cell

        drawRect(Color(0xFFE8F5E9))

        // ---- 四角机场 ----
        for (side in 1..4) {
            val a = ludoAirport[side]!!
            val ci = ludoSideIdx(side)
            drawRoundRect(
                color = ludoFull[ci].copy(alpha = 0.16f),
                topLeft = Offset(a.first * cell, a.second * cell),
                size = Size(6 * cell, 6 * cell),
                cornerRadius = CornerRadius(14f, 14f),
            )
            drawRoundRect(
                color = ludoDark[ci].copy(alpha = 0.55f),
                topLeft = Offset(a.first * cell, a.second * cell),
                size = Size(6 * cell, 6 * cell),
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(width = 2f),
            )
            for (p in 0 until 4) {
                val s = ludoAirportSlot(side, p)
                drawCircle(Color.White.copy(alpha = 0.5f), radius = cell * 0.36f, center = Offset(cx(s.first), cy(s.second)))
                drawCircle(ludoDark[ci], radius = cell * 0.36f, center = Offset(cx(s.first), cy(s.second)), style = Stroke(width = 1.2f))
            }
        }

        // ---- 主环 52 格 ----
        for (i in 0 until 52) {
            val g = ludoRing[i]
            val ci = ludoColorIdx(i)
            val isStart = ludoStart.values.contains(i)
            drawRoundRect(
                color = if (isStart) ludoFull[ci] else ludoCellFill[ci],
                topLeft = Offset(g.first * cell + 2f, g.second * cell + 2f),
                size = Size(cell - 4f, cell - 4f),
                cornerRadius = CornerRadius(7f, 7f),
            )
            drawRoundRect(
                color = if (isStart) ludoDark[ci] else Color(0xFF90A4AE),
                topLeft = Offset(g.first * cell + 2f, g.second * cell + 2f),
                size = Size(cell - 4f, cell - 4f),
                cornerRadius = CornerRadius(7f, 7f),
                style = Stroke(width = if (isStart) 2.5f else 1.2f),
            )
        }

        // ---- 终点航道（每色 6 格） ----
        for (side in 1..4) {
            val ci = ludoSideIdx(side)
            for (h in 0 until 6) {
                val r = ludoRunway[side]!![h]
                drawRoundRect(
                    color = ludoRunFill[ci],
                    topLeft = Offset(r.first * cell + 2f, r.second * cell + 2f),
                    size = Size(cell - 4f, cell - 4f),
                    cornerRadius = CornerRadius(7f, 7f),
                )
                drawRoundRect(
                    color = ludoDark[ci],
                    topLeft = Offset(r.first * cell + 2f, r.second * cell + 2f),
                    size = Size(cell - 4f, cell - 4f),
                    cornerRadius = CornerRadius(7f, 7f),
                    style = Stroke(width = 1.2f),
                )
            }
        }

        // ---- 中央 3×3 终点区：四色箭头汇入中心 ----
        drawRoundRect(
            color = Color(0xFFDCEDC8),
            topLeft = Offset(6 * cell, 6 * cell),
            size = Size(3 * cell, 3 * cell),
            cornerRadius = CornerRadius(10f, 10f),
        )
        drawRoundRect(
            color = Color(0xFF90A4AE),
            topLeft = Offset(6 * cell, 6 * cell),
            size = Size(3 * cell, 3 * cell),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = 1.5f),
        )
        val mx = cx(ludoCenter.first.toFloat())
        val my = cy(ludoCenter.second.toFloat())
        val q = (3 * cell) / 2f - 4f
        fun wedge(color: Color, tip: Offset, a: Offset, b: Offset) {
            val path = Path().apply { moveTo(tip.x, tip.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }
            drawPath(path, color = color.copy(alpha = 0.85f))
        }
        // 左红 / 上黄 / 右蓝 / 下绿（与各自航道入口方向一致）
        wedge(ludoFull[0], Offset(mx - q, my), Offset(mx, my - q * 0.62f), Offset(mx, my + q * 0.62f))
        wedge(ludoFull[1], Offset(mx, my - q), Offset(mx - q * 0.62f, my), Offset(mx + q * 0.62f, my))
        wedge(ludoFull[2], Offset(mx + q, my), Offset(mx, my - q * 0.62f), Offset(mx, my + q * 0.62f))
        wedge(ludoFull[3], Offset(mx, my + q), Offset(mx - q * 0.62f, my), Offset(mx + q * 0.62f, my))

        // ---- 虚线捷径（每色入口 → 落点，+12） ----
        for (side in 1..4) {
            val sc = ludoShortcut[side]!!
            val a2 = ludoRing[sc.first]
            val b2 = ludoRing[sc.second]
            val ci = ludoSideIdx(side)
            drawLine(
                color = ludoDark[ci].copy(alpha = 0.6f),
                start = Offset(cx(a2.first.toFloat()), cy(a2.second.toFloat())),
                end = Offset(cx(b2.first.toFloat()), cy(b2.second.toFloat())),
                strokeWidth = 3.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            drawCircle(ludoDark[ci].copy(alpha = 0.8f), radius = 5f, center = Offset(cx(a2.first.toFloat()), cy(a2.second.toFloat())))
        }

        // ---- 最近骰点（中央浮牌） ----
        if (lastRoll != null) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(mx - cell * 0.62f, my - cell * 0.62f),
                size = Size(cell * 1.24f, cell * 1.24f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = Color(0xFF37474F),
                topLeft = Offset(mx - cell * 0.62f, my - cell * 0.62f),
                size = Size(cell * 1.24f, cell * 1.24f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2f),
            )
            val t = measurer.measure(
                lastRoll.toString(),
                TextStyle(fontSize = (cell * 0.5f).sp, color = Color(0xFF37474F), fontWeight = FontWeight.Bold),
            )
            drawText(t, topLeft = Offset(mx - t.size.width / 2f, my - t.size.height / 2f))
        }

        // ---- ✈ 飞机（同格错开半格；movable 高亮） ----
        val xys = ludoPlanePositions(planes, cell, size.width)
        for (pl in xys) {
            val ci = ludoSideIdx(pl.side)
            if (myTurn && pl.side == mySide && movable.contains(pl.p)) {
                drawCircle(
                    color = Color(0xFFF57C00),
                    radius = cell * 0.46f,
                    center = Offset(pl.x, pl.y),
                    style = Stroke(width = 3.5f),
                )
            }
            drawCircle(ludoFull[ci], radius = cell * 0.32f, center = Offset(pl.x, pl.y))
            drawCircle(ludoDark[ci], radius = cell * 0.32f, center = Offset(pl.x, pl.y), style = Stroke(width = 2f))
            val t = measurer.measure(
                "✈",
                TextStyle(fontSize = (cell * 0.32f).sp, color = Color.White, fontWeight = FontWeight.Bold),
            )
            drawText(t, topLeft = Offset(pl.x - t.size.width / 2f, pl.y - t.size.height / 2f))
        }
    }
}

data class LudoPiece(val side: Int, val p: Int, val pos: Int)
