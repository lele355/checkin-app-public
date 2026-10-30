package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.sin

/**
 * 飞行棋棋盘 —— 环形跑道 + 起飞点 + 终点跑道 + 骰子（2~4 人四色）。
 * 固定色不跟主题。对应网页 boards/ludo.js。
 */
@Composable
fun LudoBoard(
    pieces: List<LudoPiece>,
    lastRoll: Int? = null,
    modifier: Modifier = Modifier,
    onTap: ((Int) -> Unit)? = null,
) {
    // side → 颜色 / 起点 / 进终点前位置 / 机库角落（对应网页 SIDES）
    fun colorOf(side: Int) = when (side) {
        2 -> Color(0xFFFFEE58); 3 -> Color(0xFF42A5F5); 4 -> Color(0xFF66BB6A); else -> Color(0xFFEF5350)
    }
    fun darkOf(side: Int) = when (side) {
        2 -> Color(0xFFF9A825); 3 -> Color(0xFF1565C0); 4 -> Color(0xFF2E7D32); else -> Color(0xFFC62828)
    }
    fun startOf(side: Int) = when (side) { 2 -> 26; 3 -> 13; 4 -> 39; else -> 0 }
    fun homeEntryOf(side: Int) = when (side) { 2 -> 24; 3 -> 11; 4 -> 37; else -> 50 }

    // 本局在用的 side 集合（从 pieces 推断，缺省 1,2）
    val sides = pieces.map { it.side }.filter { it in 1..4 }.distinct().sorted().ifEmpty { listOf(1, 2) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) { detectTapGestures { onTap?.invoke(0) } }
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val trackR = size.width * 0.36f
        val slotR = size.width * 0.028f

        drawRect(Color(0xFFE8F5E9))

        // 环形跑道格子（起点格上色）
        for (i in 0 until 52) {
            val angle = (i / 52f) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2
            val sx = cx + trackR * cos(angle)
            val sy = cy + trackR * sin(angle)
            val startSide = sides.firstOrNull { startOf(it) == i }
            val fill = if (startSide != null) colorOf(startSide).copy(alpha = 0.75f) else Color(0xFFC8E6C9)
            drawCircle(fill, radius = slotR, center = Offset(sx, sy))
            drawCircle(Color(0xFF66BB6A), radius = slotR, center = Offset(sx, sy), style = Stroke(1.5f))
        }

        // 中央
        drawCircle(Color(0xFFA5D6A7), radius = size.width * 0.125f, center = Offset(cx, cy))
        drawCircle(Color(0xFF66BB6A), radius = size.width * 0.125f, center = Offset(cx, cy), style = Stroke(2f))

        // 终点跑道（每个在用 side 一条）
        for (side in sides) {
            val he = homeEntryOf(side)
            for (h in 0 until 6) {
                val startAngle = (he / 52f) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2
                val angle = startAngle + (h + 1) / 8f * Math.PI.toFloat() * 0.4f
                val r = trackR - size.width * 0.06f - h * size.width * 0.045f
                val sx = cx + r * cos(angle)
                val sy = cy + r * sin(angle)
                drawCircle(colorOf(side).copy(alpha = 0.55f), radius = slotR * 0.8f, center = Offset(sx, sy))
            }
        }

        // 跑道 / 终点跑道上的棋子
        for (p in pieces) {
            if (p.side !in 1..4 || p.pos < 0) continue
            val (sx, sy) = if (p.pos < 52) {
                val angle = (p.pos / 52f) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2
                (cx + trackR * cos(angle)) to (cy + trackR * sin(angle))
            } else {
                val h = p.pos - 52
                val startAngle = (homeEntryOf(p.side) / 52f) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2
                val angle = startAngle + (h + 1) / 8f * Math.PI.toFloat() * 0.4f
                val r = trackR - size.width * 0.06f - h * size.width * 0.045f
                (cx + r * cos(angle)) to (cy + r * sin(angle))
            }
            val pr = size.width * 0.02f
            drawCircle(colorOf(p.side), radius = pr, center = Offset(sx, sy))
            drawCircle(darkOf(p.side), radius = pr, center = Offset(sx, sy), style = Stroke(2f))
        }

        // 在家棋子（角落机库）
        for (side in sides) {
            val homePieces = pieces.filter { it.side == side && it.pos < 0 }
            // 红=左下 / 黄=右上 / 蓝=右下 / 绿=左上
            val (hx, hy) = when (side) {
                1 -> 60f to (size.height - 60f)
                2 -> (size.width - 60f) to 60f
                3 -> (size.width - 60f) to (size.height - 60f)
                else -> 60f to 60f
            }
            homePieces.forEachIndexed { k, _ ->
                val px = hx + (k % 2) * (size.width * 0.045f)
                val py = hy + (k / 2) * (size.width * 0.045f)
                drawCircle(colorOf(side), radius = size.width * 0.019f, center = Offset(px, py))
            }
        }

        // 骰子
        if (lastRoll != null) {
            drawRect(Color.White, topLeft = Offset(cx - 25f, cy - 25f), size = Size(50f, 50f))
            drawRect(Color(0xFF333333), topLeft = Offset(cx - 25f, cy - 25f), size = Size(50f, 50f), style = Stroke(2f))
        }
    }
}

data class LudoPiece(val side: Int, val p: Int, val pos: Int)
