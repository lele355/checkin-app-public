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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 飞行棋盘 v5【推倒重来版 2026-10-31】。
 * 按站主参考（metool.online 飞行棋）重建视觉：浅灰底 + 低饱和哑光四色圆角格（格心白点）
 * + 2×2 白圆盘机场（本色飞机）+ 中心白色圆角块（四色三角汇聚）+ 4 条彩色虚线捷径航线。
 * 去掉旧版补丁元素（字母格/加油站/环道箭头/跳格标记）。与网页 boards/ludo.js 同源同参。
 * 坐标拓扑 = 52 环 + 4×6 航道（与 LudoRules.cs 规则事实同源，规则层不动）。
 */

// ---- 拓扑：主环 52 格（顺时针，与 LudoRules.cs / 网页同源） ----
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
private val ludoRunway = mapOf(
    1 to listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7, 6 to 7),
    2 to listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5, 7 to 6),
    3 to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7, 8 to 7),
    4 to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9, 7 to 8),
)
private val ludoCenter = 7 to 7

// 四角 6×6 机场（区域左上角格）：1 红左上 / 2 黄右上 / 3 蓝右下 / 4 绿左下
private val ludoAirport = mapOf(1 to (0 to 0), 2 to (9 to 0), 3 to (9 to 9), 4 to (0 to 9))

// 起点 0/13/26/39；捷径入口→落点（start+16 → start+28，+12）
private val ludoStart = mapOf(1 to 0, 2 to 13, 3 to 26, 4 to 39)
private val ludoShortcut = mapOf(1 to (16 to 28), 2 to (29 to 41), 3 to (42 to 2), 4 to (3 to 15))
private val ludoHomeEntry = mapOf(1 to 50, 2 to 11, 3 to 24, 4 to 37)

// ---- metool 低饱和哑光配色（参考图采样）：1 红 / 2 黄 / 3 蓝 / 4 绿 ----
private val ludoBg = Color(0xFFFAFAFA)
private val ludoSeam = Color(0xFFE4E4E4)
private val ludoFull = listOf(Color(0xFFA94841), Color(0xFFD9CF52), Color(0xFF3780A0), Color(0xFF388B5D))

private fun ludoSideIdx(side: Int) = ((side - 1) % 4 + 4) % 4

// 环道第 i 格归属色：四色循环（同色格间隔恰 4 = 跳 +4 的视觉语义，与 LudoRules.ColorOfCell 同口径）
private fun ludoColorIdx(i: Int) = ((i % 4) + 4) % 4

/** 飞机落点格（col,row）：-1 机场 / 0-51 主环 / 52-57 航道 / 58 中心 */
private fun ludoPlaneCell(pos: Int, side: Int): Pair<Int, Int>? = when {
    pos < 0 -> null
    pos < 52 -> ludoRing[pos]
    pos <= 57 -> ludoRunway[side]!![pos - 52]
    else -> ludoCenter
}

/** 机场停机位（2×2 白圆盘中心，与网页同参：块内 1.85 / 4.15 格处） */
private fun ludoAirportSlot(side: Int, p: Int): Pair<Float, Float> {
    val a = ludoAirport[side]!!
    return (a.first + 1.85f + if (p % 2 == 0) 0f else 2.3f) to (a.second + 1.85f + if (p < 2) 0f else 2.3f)
}

/** 环道行进角（弧度）：idx → idx+1 的方向 */
private fun ludoRingAngle(i: Int): Float {
    val a = ludoRing[i]
    val b = ludoRing[(i + 1) % 52]
    return atan2((b.second - a.second).toFloat(), (b.first - a.first).toFloat())
}

/** 实心三角箭头（尖端朝 angle） */
private fun ludoArrowPath(px: Float, py: Float, size: Float, angle: Float): Path {
    val ca = cos(angle)
    val sa = sin(angle)
    val pts = floatArrayOf(0.6f, 0f, -0.42f, 0.44f, -0.42f, -0.44f)
    return Path().apply {
        var i = 0
        while (i < pts.size) {
            val x = pts[i]
            val y = pts[i + 1]
            val rx = px + (x * ca - y * sa) * size
            val ry = py + (x * sa + y * ca) * size
            if (i == 0) moveTo(rx, ry) else lineTo(rx, ry)
            i += 2
        }
        close()
    }
}

/** 机形件名（自绘俯视剪影，按归属色取变体） */
private fun ludoPlaneSrc(side: Int): String = when (side) {
    1 -> "airplane_red"
    2 -> "airplane_yellow"
    3 -> "airplane_blue"
    4 -> "airplane_green"
    else -> "airplane"
}

/** 飞机图标（自绘俯视剪影；机头朝 -Y，调用 angle=0 表示机头朝 +X → 旋转补 +90°，与网页同规） */
private fun DrawScope.ludoPlane(
    icons: Map<String, ImageBitmap>,
    px: Float, py: Float, size: Float, angle: Float,
    side: Int, white: Boolean = false,
) {
    val bmp = icons[if (white) "airplane_white" else ludoPlaneSrc(side)] ?: return
    val s = (size * 2.45f).toInt().coerceAtLeast(2)
    val deg = Math.toDegrees(angle.toDouble()).toFloat() + 90f
    withTransform({ rotate(deg, pivot = Offset(px, py)) }) {
        drawImage(
            bmp,
            dstSize = IntSize(s, s),
            dstOffset = IntOffset((px - s / 2f).toInt(), (py - s / 2f).toInt()),
        )
    }
}

private data class LudoPlaneXY(val p: Int, val side: Int, val x: Float, val y: Float)

/** 计算全部飞机的画布坐标（绘制与点击命中共用） */
private fun ludoPlanePositions(pieces: List<LudoPiece>, cell: Float): List<LudoPlaneXY> {
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
 * 十字盘（metool 重建版）。movable = 服务端算好的可走机号（可走机加橙色高亮圈）；
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
    val planeIcons = rememberPieceIcons(
        "ludo",
        listOf("airplane", "airplane_red", "airplane_yellow", "airplane_blue", "airplane_green", "airplane_white"),
    )
    val planes = remember(pieces) { pieces }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(planes, movable, myTurn, mySide) {
                val cell = size.width / 15f
                val xys = ludoPlanePositions(planes, cell)
                detectTapGestures { off ->
                    if (!myTurn || movable.isEmpty()) return@detectTapGestures
                    var best = -1
                    var bestD = cell * 0.62f
                    for (q in xys) {
                        if (q.side != mySide) continue
                        val d = Math.abs(q.x - off.x) + Math.abs(q.y - off.y)
                        if (d < bestD) { bestD = d; best = q.p }
                    }
                    if (best >= 0 && movable.contains(best)) onTap?.invoke(best)
                }
            }
    ) {
        val cell = size.width / 15f
        val u = cell / 42.6667f   // 网页 640 视觉单位 → 画布像素（双端同比例）
        fun cx(col: Float) = (col + 0.5f) * cell
        fun cy(row: Float) = (row + 0.5f) * cell
        val mx = cx(ludoCenter.first.toFloat())
        val my = cy(ludoCenter.second.toFloat())

        // ---- 底：浅灰白 + 细灰外框 ----
        drawRect(ludoBg)
        drawRect(ludoSeam, topLeft = Offset(3f * u, 3f * u), size = Size(size.width - 6f * u, size.height - 6f * u), style = Stroke(width = 2f * u))

        // ---- 四角机场：2×2 白圆盘（本色环）+ 本色飞机，盘间小点装饰 ----
        for (side in 1..4) {
            val a = ludoAirport[side]!!
            val ci = ludoSideIdx(side)
            // 机场底衬：本色淡色圆角块
            drawRoundRect(
                color = ludoFull[ci].copy(alpha = 0.22f),
                topLeft = Offset(a.first * cell + 0.55f * cell, a.second * cell + 0.55f * cell),
                size = Size(4.9f * cell, 4.9f * cell),
                cornerRadius = CornerRadius(10f * u, 10f * u),
            )
            // 盘间小点装饰
            for (q in listOf(1.05f to 1.05f, 4.95f to 1.05f, 1.05f to 4.95f, 4.95f to 4.95f, 3f to 3f)) {
                drawCircle(Color.White, radius = cell * 0.12f, center = Offset((a.first + q.first) * cell, (a.second + q.second) * cell))
            }
            // 4 个白圆盘停机位 + 本色飞机
            for (p in 0 until 4) {
                val s = ludoAirportSlot(side, p)
                drawCircle(Color.White, radius = cell * 0.92f, center = Offset(cx(s.first), cy(s.second)))
                drawCircle(ludoFull[ci], radius = cell * 0.92f, center = Offset(cx(s.first), cy(s.second)), style = Stroke(width = 3f * u))
                ludoPlane(planeIcons, cx(s.first), cy(s.second), cell * 0.62f, 0f, side)
            }
        }

        // ---- 主环 52 格：圆角彩色格 + 格心白点（起飞格画白色小飞机） ----
        for (i in 0 until 52) {
            val g = ludoRing[i]
            val ci = ludoColorIdx(i)
            val isStart = ludoStart.values.contains(i)
            drawRoundRect(
                color = ludoFull[ci],
                topLeft = Offset(g.first * cell + 1.5f * u, g.second * cell + 1.5f * u),
                size = Size(cell - 3f * u, cell - 3f * u),
                cornerRadius = CornerRadius(7f * u, 7f * u),
            )
            if (isStart) {
                // 起飞格：深灰小飞机（白色在黄格上对比不足，side=0 取中性机形）
                ludoPlane(planeIcons, cx(g.first.toFloat()), cy(g.second.toFloat()), cell * 0.3f, ludoRingAngle(i), 0)
            } else {
                drawCircle(Color.White, radius = cell * 0.26f, center = Offset(cx(g.first.toFloat()), cy(g.second.toFloat())))
            }
        }

        // ---- 终点航道：本色圆角格 + 白点（h=0..5） ----
        for (side in 1..4) {
            val ci = ludoSideIdx(side)
            for (h in 0 until 6) {
                val r = ludoRunway[side]!![h]
                drawRoundRect(
                    color = ludoFull[ci],
                    topLeft = Offset(r.first * cell + 1.5f * u, r.second * cell + 1.5f * u),
                    size = Size(cell - 3f * u, cell - 3f * u),
                    cornerRadius = CornerRadius(7f * u, 7f * u),
                )
                drawCircle(Color.White, radius = cell * 0.26f, center = Offset(cx(r.first.toFloat()), cy(r.second.toFloat())))
            }
        }

        // ---- 中心：四色三角汇聚 + 白色圆角块 + 红点 ----
        val z = 3 * cell
        val zx = 6 * cell
        val zy = 6 * cell
        val zc = listOf(
            ludoFull[1] to (Offset(zx, zy) to Offset(zx + z, zy)),                 // 上（黄方口）
            ludoFull[3] to (Offset(zx, zy + z) to Offset(zx + z, zy + z)),         // 下（绿方口）
            ludoFull[0] to (Offset(zx, zy) to Offset(zx, zy + z)),                 // 左（红方口）
            ludoFull[2] to (Offset(zx + z, zy) to Offset(zx + z, zy + z)),         // 右（蓝方口）
        )
        for ((color, ends) in zc) {
            val path = Path().apply {
                moveTo(ends.first.x, ends.first.y)
                lineTo(ends.second.x, ends.second.y)
                lineTo(mx, my)
                close()
            }
            drawPath(path, color.copy(alpha = 0.9f))
        }
        val box = cell * 0.95f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(mx - box, my - box),
            size = Size(box * 2f, box * 2f),
            cornerRadius = CornerRadius(cell * 0.34f, cell * 0.34f),
        )
        drawRoundRect(
            color = ludoSeam,
            topLeft = Offset(mx - box, my - box),
            size = Size(box * 2f, box * 2f),
            cornerRadius = CornerRadius(cell * 0.34f, cell * 0.34f),
            style = Stroke(width = 2f * u),
        )
        drawCircle(ludoFull[0], radius = cell * 0.26f, center = Offset(mx, my))

        // ---- 4 条彩色虚线捷径航线（入口 → 落点，箭头指示飞越方向） ----
        for (side in 1..4) {
            val (en, ln) = ludoShortcut[side]!!
            val from = ludoRing[en]
            val to = ludoRing[ln]
            val x1 = cx(from.first.toFloat()); val y1 = cy(from.second.toFloat())
            val x2 = cx(to.first.toFloat()); val y2 = cy(to.second.toFloat())
            val cxs = 7.5f * cell + ((x1 + x2) / 2f - 7.5f * cell) * 0.3f
            val cys = 7.5f * cell + ((y1 + y2) / 2f - 7.5f * cell) * 0.3f
            val path = Path().apply {
                moveTo(x1, y1)
                quadraticTo(cxs, cys, x2, y2)
            }
            drawPath(
                path,
                ludoFull[ludoSideIdx(side)].copy(alpha = 0.85f),
                style = Stroke(width = 3f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * u, 7f * u)), cap = StrokeCap.Round),
            )
            val a2 = atan2(y2 - cys, x2 - cxs)
            val arrowPath = ludoArrowPath(x2 - cos(a2) * cell * 0.42f, y2 - sin(a2) * cell * 0.42f, cell * 0.2f, a2)
            drawPath(arrowPath, ludoFull[ludoSideIdx(side)])
            drawPath(arrowPath, Color.White, style = Stroke(width = 1f * u))
        }

        // ---- 最近骰点：中心白块右上小徽标 ----
        if (lastRoll != null) {
            val dx0 = mx + cell * 1.5f
            val dy0 = my - cell * 1.45f
            val hb = cell * 0.36f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(dx0 - hb, dy0 - hb),
                size = Size(hb * 2f, hb * 2f),
                cornerRadius = CornerRadius(cell * 0.16f, cell * 0.16f),
            )
            drawRoundRect(
                color = ludoSeam,
                topLeft = Offset(dx0 - hb, dy0 - hb),
                size = Size(hb * 2f, hb * 2f),
                cornerRadius = CornerRadius(cell * 0.16f, cell * 0.16f),
                style = Stroke(width = 2f * u),
            )
            val t = measurer.measure(
                lastRoll.toString(),
                TextStyle(fontSize = (cell * 0.42f / density).sp, color = Color(0xFF555555), fontWeight = FontWeight.Bold),
            )
            drawText(t, topLeft = Offset(dx0 - t.size.width / 2f, dy0 - t.size.height / 2f))
        }

        // ---- 飞机棋子（同格错开；movable 高亮） ----
        val xys = ludoPlanePositions(planes, cell)
        for (pl in xys) {
            if (myTurn && pl.side == mySide && movable.contains(pl.p)) {
                drawCircle(
                    color = Color(0xFFF57C00),
                    radius = cell * 0.52f,
                    center = Offset(pl.x, pl.y),
                    style = Stroke(width = 3.5f * u),
                )
            }
            val piecePos = planes.first { it.p == pl.p && it.side == pl.side }.pos
            val pieceAngle = when {
                piecePos < 0 -> floatArrayOf(PI.toFloat() / 2f, PI.toFloat(), -PI.toFloat() / 2f, 0f)[ludoSideIdx(pl.side)]
                piecePos < 52 -> ludoRingAngle(piecePos)
                else -> atan2(my - pl.y, mx - pl.x)
            }
            ludoPlane(planeIcons, pl.x, pl.y, cell * 0.52f, pieceAngle, pl.side)
        }
    }
}

data class LudoPiece(val side: Int, val p: Int, val pos: Int)
