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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
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
 * 飞行棋十字盘（中国飞行棋）—— §9 坐标表与网页 boards/ludo.js 同源。
 * 15×15 网格；主环 52 格 color(i)=i%4；四角 6×6 机场；每色 6 格终点航道汇入中央 3×3；
 * 虚线捷径（入口→落点 +12，绿 3→15 以公式为准）；飞机棋子为路径剪影（禁 ✈ 字符）；
 * movable 高亮由服务端快照直供。固定色不跟主题（红线 18）。
 * 视觉规格：2026-10-31-ludo-layout-spec.md §3/§5/§6/§7/§8/§12。
 */

// ---- §2.2 主环 52 格坐标表（顺时针，(col,row)） ----
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

// 起点 0/13/26/39；虚线捷径入口→落点（start+16 → start+28，+12）：
// 红 16→28 / 黄 29→41 / 蓝 42→2 / 绿 3→15（§7.2 表绿「51」系笔误，以公式与 §6.2 为准）
private val ludoStart = mapOf(1 to 0, 2 to 13, 3 to 26, 4 to 39)
private val ludoShortcut = mapOf(1 to (16 to 28), 2 to (29 to 41), 3 to (42 to 2), 4 to (3 to 15))

// §6.2 跳 +4 标记 8 处（捷径前跳 = 入口−4；捷径后跳 = 捷径落点）
private val ludoJumpMark = listOf(12, 28, 25, 41, 38, 2, 51, 15)

// §5.3 HomeEntry（转入跑道的转角格）
private val ludoHomeEntry = mapOf(1 to 50, 2 to 11, 3 to 24, 4 to 37)

// color(i)=i%4：0 红 1 黄 2 蓝 3 绿；side k 的色号 = k−1
private val ludoRingFill = listOf(Color(0xFFEF5350), Color(0xFFFFEE58), Color(0xFF42A5F5), Color(0xFF66BB6A))
private val ludoFull = listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF1E88E5), Color(0xFF43A047))
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

/** 环道行进角（弧度）：idx → idx+1 的方向 */
private fun ludoRingAngle(i: Int): Float {
    val a = ludoRing[i]
    val b = ludoRing[(i + 1) % 52]
    return atan2((b.second - a.second).toFloat(), (b.first - a.first).toFloat())
}

/** 实心三角箭头（尖端朝 angle；与网页 games 同几何） */
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

/** 飞机图标件名（icons-ready 同源，自绘俯视剪影）：按填充色取色套变体；未知色=中性深色 */
private fun ludoPlaneSrc(fill: Color): String = when (fill.toArgb() or 0xFF000000.toInt()) {
    0xFFE53935.toInt(), 0xFFC62828.toInt() -> "airplane_red"
    0xFFFDD835.toInt(), 0xFFF9A825.toInt() -> "airplane_yellow"
    0xFF1E88E5.toInt(), 0xFF1565C0.toInt() -> "airplane_blue"
    0xFF43A047.toInt(), 0xFF2E7D32.toInt() -> "airplane_green"
    0xFFFFFFFF.toInt() -> "airplane_white"
    else -> "airplane"
}

/** 飞机图标（自绘俯视剪影，站主决策；stroke 参数保签名兼容，色套变体自带描边光晕）。
 *  素材机头朝 -Y，调用方 angle=0 表示机头朝 +X —— 旋转补 +90°（与网页同规）。 */
private fun DrawScope.ludoPlane(icons: Map<String, ImageBitmap>, px: Float, py: Float, size: Float, angle: Float, fill: Color, stroke: Color? = null, sw: Float = 1.2f) {
    val bmp = icons[ludoPlaneSrc(fill)] ?: return
    val side = (size * 2.45f).toInt().coerceAtLeast(2)
    val deg = Math.toDegrees(angle.toDouble()).toFloat() + 90f
    withTransform({
        rotate(deg, pivot = Offset(px, py))
    }) {
        drawImage(
            bmp,
            dstSize = IntSize(side, side),
            dstOffset = IntOffset((px - side / 2f).toInt(), (py - side / 2f).toInt()),
            alpha = fill.alpha,
        )
    }
}

private fun DrawScope.ludoArrow(px: Float, py: Float, size: Float, angle: Float, fill: Color, stroke: Color? = null) {
    val p = ludoArrowPath(px, py, size, angle)
    drawPath(p, fill)
    if (stroke != null) drawPath(p, stroke, style = Stroke(width = 1f))
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
    // 机形图标（自绘俯视剪影，icons-ready 同源，D-5 站主决策换俯视）
    val planeIcons = rememberPieceIcons("ludo", listOf("airplane", "airplane_red", "airplane_yellow", "airplane_blue", "airplane_green", "airplane_white"))
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
        val u = cell / 42.6667f   // 网页 640 视觉单位 → 画布像素（双端同比例）
        fun cx(col: Float) = (col + 0.5f) * cell
        fun cy(row: Float) = (row + 0.5f) * cell
        val mx = cx(ludoCenter.first.toFloat())
        val my = cy(ludoCenter.second.toFloat())

        drawRect(Color(0xFFE8F5E9))

        // ---- 四角机场（§3.2 粗彩边框 + 本色底 + 停机位飞机剪影 + 中央大飞机） ----
        for (side in 1..4) {
            val a = ludoAirport[side]!!
            val ci = ludoSideIdx(side)
            val padAngle = floatArrayOf(PI.toFloat() / 2f, PI.toFloat(), -PI.toFloat() / 2f, 0f)[ci]
            // 十字轮廓（参考图实测）：块与臂之间留白缺口（向心两侧边内缩）+ 内角 45° 斜切
            val g = 0.15f * cell
            val ins = listOf(
                listOf(0f, 0f, 1f, 1f), listOf(1f, 0f, 0f, 1f),
                listOf(1f, 1f, 0f, 0f), listOf(0f, 1f, 1f, 0f),
            )[side - 1]
            val x0 = a.first * cell + ins[0] * g
            val y0 = a.second * cell + ins[1] * g
            val x1 = (a.first + 6) * cell - ins[2] * g
            val y1 = (a.second + 6) * cell - ins[3] * g
            drawRoundRect(
                color = ludoFull[ci],
                topLeft = Offset(x0, y0),
                size = Size(x1 - x0, y1 - y0),
                cornerRadius = CornerRadius(14f * u, 14f * u),
            )
            drawRoundRect(
                color = ludoDark[ci],
                topLeft = Offset(x0, y0),
                size = Size(x1 - x0, y1 - y0),
                cornerRadius = CornerRadius(14f * u, 14f * u),
                style = Stroke(width = 2f * u),
            )
            // 内角斜切：底板色三角切掉块内角（避开停机位）
            val ncx = if (ins[0] > 0f) x0 else x1
            val ncy = if (ins[1] > 0f) y0 else y1
            val dx = if (ins[0] > 0f) 1f else -1f
            val dy = if (ins[1] > 0f) 1f else -1f
            val ln = 1.25f * cell
            val notch = Path().apply {
                moveTo(ncx, ncy)
                lineTo(ncx + dx * ln, ncy)
                lineTo(ncx, ncy + dy * ln)
                close()
            }
            drawPath(notch, Color(0xFFE8F5E9))
            // 中央大号半透明飞机
            ludoPlane(planeIcons, cx((a.first + 3).toFloat()), cy((a.second + 3).toFloat()), cell * 1.15f, padAngle, ludoDark[ci].copy(alpha = 0.28f))
            // 4 个停机位：白圆 + 本色飞机剪影
            for (p in 0 until 4) {
                val s = ludoAirportSlot(side, p)
                drawCircle(Color.White, radius = cell * 0.36f, center = Offset(cx(s.first), cy(s.second)))
                drawCircle(ludoDark[ci], radius = cell * 0.36f, center = Offset(cx(s.first), cy(s.second)), style = Stroke(width = 1.4f * u))
                ludoPlane(planeIcons, cx(s.first), cy(s.second), cell * 0.26f, padAngle, ludoFull[ci], ludoDark[ci], 1f * u)
            }
            // 盘外角落装饰飞机（参考图：机场块外缘角落 2-3 架小飞机，朝外，不挤占停机位）
            val deco = mapOf(
                1 to listOf(Triple(0.62f, 0.62f, -3f * PI.toFloat() / 4f), Triple(3f, 0.52f, -PI.toFloat() / 2f), Triple(0.52f, 3f, PI.toFloat())),
                2 to listOf(Triple(14.38f, 0.62f, -PI.toFloat() / 4f), Triple(12f, 0.52f, -PI.toFloat() / 2f), Triple(14.48f, 3f, 0f)),
                3 to listOf(Triple(14.38f, 14.38f, PI.toFloat() / 4f), Triple(12f, 14.48f, PI.toFloat() / 2f), Triple(14.48f, 12f, 0f)),
                4 to listOf(Triple(0.62f, 14.38f, 3f * PI.toFloat() / 4f), Triple(3f, 14.48f, PI.toFloat() / 2f), Triple(0.52f, 12f, PI.toFloat())),
            )[side]!!
            for (d in deco) {
                ludoPlane(planeIcons, d.first * cell, d.second * cell, cell * 0.3f, d.third, ludoDark[ci].copy(alpha = 0.85f))
            }
        }

        // ---- 主环 52 格（中等色 + 白圈，§12-1/§12-2） ----
        for (i in 0 until 52) {
            val g = ludoRing[i]
            val ci = ludoColorIdx(i)
            val isStart = ludoStart.values.contains(i)
            val left = g.first * cell + 2f * u
            val top = g.second * cell + 2f * u
            val w = cell - 4f * u
            drawRoundRect(
                color = if (isStart) ludoFull[ci] else ludoRingFill[ci],
                topLeft = Offset(left, top),
                size = Size(w, w),
                cornerRadius = CornerRadius(7f * u, 7f * u),
            )
            drawRoundRect(
                color = if (isStart) ludoDark[ci] else Color(0xFF78909C),
                topLeft = Offset(left, top),
                size = Size(w, w),
                cornerRadius = CornerRadius(7f * u, 7f * u),
                style = Stroke(width = if (isStart) 2.5f * u else 1f * u),
            )
            drawCircle(Color.White, radius = cell * 0.3f, center = Offset(cx(g.first.toFloat()), cy(g.second.toFloat())))
        }

        // ---- 终点航道（每色 6 格，全饱和 + 白圈，§5.2） ----
        // h=5 末端格压在中心三角下层：先不画，三角之后只补白圈（参考图：白圈浮于色块）
        for (side in 1..4) {
            val ci = ludoSideIdx(side)
            for (h in 0 until 5) {
                val r = ludoRunway[side]!![h]
                val left = r.first * cell + 2f * u
                val top = r.second * cell + 2f * u
                val w = cell - 4f * u
                drawRoundRect(
                    color = ludoFull[ci],
                    topLeft = Offset(left, top),
                    size = Size(w, w),
                    cornerRadius = CornerRadius(7f * u, 7f * u),
                )
                drawRoundRect(
                    color = ludoDark[ci],
                    topLeft = Offset(left, top),
                    size = Size(w, w),
                    cornerRadius = CornerRadius(7f * u, 7f * u),
                    style = Stroke(width = 1f * u),
                )
                drawCircle(Color.White, radius = cell * 0.3f, center = Offset(cx(r.first.toFloat()), cy(r.second.toFloat())))
            }
        }

        // ---- 跳 +4 标记（§6.2 共 8 处：本色圆圈 + 白飞机剪影，机头朝行进方向） ----
        for (idx in ludoJumpMark) {
            val g = ludoRing[idx]
            val ci = ludoColorIdx(idx)
            drawCircle(ludoFull[ci], radius = cell * 0.32f, center = Offset(cx(g.first.toFloat()), cy(g.second.toFloat())))
            drawCircle(ludoDark[ci], radius = cell * 0.32f, center = Offset(cx(g.first.toFloat()), cy(g.second.toFloat())), style = Stroke(width = 2f * u))
            ludoPlane(planeIcons, cx(g.first.toFloat()), cy(g.second.toFloat()), cell * 0.22f, ludoRingAngle(idx), Color.White)
        }

        // ---- 起飞口标记（§4.3：深色飞机剪影，机头朝行进方向） ----
        for (side in 1..4) {
            val idx = ludoStart[side]!!
            val g = ludoRing[idx]
            ludoPlane(planeIcons, cx(g.first.toFloat()), cy(g.second.toFloat()), cell * 0.24f, ludoRingAngle(idx), Color(0xFF4E342E))
        }

        // ---- 虚线捷径航线（§7.3 两段折线掠过中心区四角，画在中心区下层） ----
        for (side in 1..4) {
            val sc = ludoShortcut[side]!!
            val a2 = ludoRing[sc.first]
            val b2 = ludoRing[sc.second]
            val ci = ludoSideIdx(side)
            val bendX = mx + (cx(a2.first.toFloat()) - mx) * 0.42f + (cx(b2.first.toFloat()) - mx) * 0.42f
            val bendY = my + (cy(a2.second.toFloat()) - my) * 0.42f + (cy(b2.second.toFloat()) - my) * 0.42f
            val line = Path().apply {
                moveTo(cx(a2.first.toFloat()), cy(a2.second.toFloat()))
                lineTo(bendX, bendY)
                lineTo(cx(b2.first.toFloat()), cy(b2.second.toFloat()))
            }
            // 白色衬线 + 本色虚线（在彩色格上也可读），线整体画在中心区之下
            drawPath(line, Color.White.copy(alpha = 0.55f), style = Stroke(width = 6.5f * u))
            drawPath(
                line, ludoDark[ci].copy(alpha = 0.75f),
                style = Stroke(width = 3.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * u, 8f * u))),
            )
        }

        // ---- 中央终点区：带 V 缺口的四箭头三角拼风车（参考图实测，画在虚线上层）----
        // 尖端汇聚中心 ★；平边=中心区四边（缩进 0.38 格，四角留白成 V 缺口）；
        // 跑道臂从平边中央穿出（臂两侧肩阶）；跑道末端白圈浮于色块之上。
        val zx = 6 * cell
        val zy = 6 * cell
        val zs = 3 * cell
        val ins = 0.38f * cell
        val zc = listOf(
            ludoFull[0] to (Offset(zx, zy + ins) to Offset(zx, zy + zs - ins)),                 // 左红
            ludoFull[1] to (Offset(zx + ins, zy) to Offset(zx + zs - ins, zy)),                 // 上黄
            ludoFull[2] to (Offset(zx + zs, zy + ins) to Offset(zx + zs, zy + zs - ins)),       // 右蓝
            ludoFull[3] to (Offset(zx + ins, zy + zs) to Offset(zx + zs - ins, zy + zs)),       // 下绿
        )
        for ((color, ends) in zc) {
            val path = Path().apply {
                moveTo(ends.first.x, ends.first.y)
                lineTo(ends.second.x, ends.second.y)
                lineTo(mx, my)
                close()
            }
            drawPath(path, color)
        }
        // 跑道末端白圈叠在三角上
        for (c in listOf(6 to 7, 7 to 6, 8 to 7, 7 to 8)) {
            drawCircle(Color.White, radius = cell * 0.3f, center = Offset(cx(c.first.toFloat()), cy(c.second.toFloat())))
        }
        // 中心 ★：参考图四尖汇点无星（像素取证：四色直接相接），不绘制标记

        // ---- 虚线入口/落点标记（§7.3）：入口飞机+方向箭头，落点虚线目标圈 ----
        for (side in 1..4) {
            val sc = ludoShortcut[side]!!
            val a3 = ludoRing[sc.first]
            val b3 = ludoRing[sc.second]
            val ci = ludoSideIdx(side)
            val fa = atan2(cy(b3.second.toFloat()) - cy(a3.second.toFloat()), cx(b3.first.toFloat()) - cx(a3.first.toFloat()))
            ludoPlane(planeIcons, cx(a3.first.toFloat()), cy(a3.second.toFloat()), cell * 0.26f, fa, ludoFull[ci], ludoDark[ci], 1.2f * u)
            ludoArrow(
                cx(a3.first.toFloat()) + cos(fa) * cell * 0.62f,
                cy(a3.second.toFloat()) + sin(fa) * cell * 0.62f,
                cell * 0.18f, fa, ludoDark[ci],
            )
            drawCircle(
                ludoDark[ci].copy(alpha = 0.8f), radius = cell * 0.42f,
                center = Offset(cx(b3.first.toFloat()), cy(b3.second.toFloat())),
                style = Stroke(width = 1.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 4f * u))),
            )
        }

        // ---- 环道方向箭头（顺时针 4 处）：彩色实心画在块内角斜切缺口（白缺口+彩箭头，参考图观感） ----
        val dirArrows = listOf(
            Triple(5.55f, 5.55f, 0f) to 0,
            Triple(9.45f, 5.55f, PI.toFloat() / 2f) to 1,
            Triple(9.45f, 9.45f, PI.toFloat()) to 2,
            Triple(5.55f, 9.45f, -PI.toFloat() / 2f) to 3,
        )
        for ((pos, colorIdx) in dirArrows) {
            ludoArrow(pos.first * cell, pos.second * cell, cell * 0.42f, pos.third, ludoFull[colorIdx], Color.White)
        }

        // ---- HomeEntry 转弯箭头（§8.2 指向跑道方向，本色实心） ----
        for (side in 1..4) {
            val he = ludoRing[ludoHomeEntry[side]!!]
            val ci = ludoSideIdx(side)
            val ha = atan2(my - cy(he.second.toFloat()), mx - cx(he.first.toFloat()))
            ludoArrow(cx(he.first.toFloat()), cy(he.second.toFloat()), cell * 0.42f, ha, ludoFull[ci], Color.White)
        }

        // ---- 最近骰点（中央浮牌，位于终点区上层、棋子下层） ----
        if (lastRoll != null) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(mx - cell * 0.62f, my - cell * 0.62f),
                size = Size(cell * 1.24f, cell * 1.24f),
                cornerRadius = CornerRadius(10f * u, 10f * u),
            )
            drawRoundRect(
                color = Color(0xFF37474F),
                topLeft = Offset(mx - cell * 0.62f, my - cell * 0.62f),
                size = Size(cell * 1.24f, cell * 1.24f),
                cornerRadius = CornerRadius(10f * u, 10f * u),
                style = Stroke(width = 2f * u),
            )
            val t = measurer.measure(
                lastRoll.toString(),
                TextStyle(fontSize = (cell * 0.5f).sp, color = Color(0xFF37474F), fontWeight = FontWeight.Bold),
            )
            drawText(t, topLeft = Offset(mx - t.size.width / 2f, my - t.size.height / 2f))
        }

        // ---- 飞机棋子（路径剪影，同格错开半格；movable 高亮，禁 ✈ 字符） ----
        val xys = ludoPlanePositions(planes, cell, size.width)
        for (pl in xys) {
            val ci = ludoSideIdx(pl.side)
            if (myTurn && pl.side == mySide && movable.contains(pl.p)) {
                drawCircle(
                    color = Color(0xFFF57C00),
                    radius = cell * 0.46f,
                    center = Offset(pl.x, pl.y),
                    style = Stroke(width = 3.5f * u),
                )
            }
            // 棋子机头朝行进方向：机场=朝起飞口，主环=顺时针，航道/中心=朝中心
            val piecePos = planes.first { it.p == pl.p && it.side == pl.side }.pos
            val pieceAngle = when {
                piecePos < 0 -> floatArrayOf(PI.toFloat() / 2f, PI.toFloat(), -PI.toFloat() / 2f, 0f)[ci]
                piecePos < 52 -> ludoRingAngle(piecePos)
                else -> atan2(my - pl.y, mx - pl.x)
            }
            ludoPlane(planeIcons, pl.x, pl.y, cell * 0.34f, pieceAngle, ludoFull[ci], ludoDark[ci], 1.6f * u)
        }
    }
}

data class LudoPiece(val side: Int, val p: Int, val pos: Int)
