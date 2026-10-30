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
 * 飞行棋棋盘 —— 环形跑道 + 起飞点 + 终点跑道 + 骰子。
 * 固定色不跟主题。对应网页 boards/ludo.js。
 */
@Composable
fun LudoBoard(
    pieces: List<LudoPiece>,
    lastRoll: Int? = null,
    modifier: Modifier = Modifier,
    onTap: ((Int) -> Unit)? = null,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    // 简化：点击区域不精确到格子，只传递给上层处理
                    onTap?.invoke(0)
                }
            }
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val trackR = size.width * 0.36f
        val slotR = 18f

        // 背景
        drawRect(Color(0xFFE8F5E9))

        // 环形跑道格子
        for (i in 0 until 52) {
            val angle = (i / 52f) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2
            val sx = cx + trackR * cos(angle)
            val sy = cy + trackR * sin(angle)
            val fill = when (i) {
                0 -> Color(0xFFFFCDD2)
                26 -> Color(0xFFFFF9C4)
                else -> Color(0xFFC8E6C9)
            }
            drawCircle(fill, radius = slotR, center = Offset(sx, sy))
            drawCircle(Color(0xFF66BB6A), radius = slotR, center = Offset(sx, sy), style = Stroke(1.5f))
        }

        // 中央
        drawCircle(Color(0xFFA5D6A7), radius = 80f, center = Offset(cx, cy))
        drawCircle(Color(0xFF66BB6A), radius = 80f, center = Offset(cx, cy), style = Stroke(2f))

        // 棋子
        for (p in pieces) {
            if (p.pos < 0) continue
            val angle = if (p.pos < 52) {
                (p.pos / 52f) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2
            } else {
                val h = p.pos - 52
                val startAngle = (if (p.side == 1) 0 else 26) / 52f * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2
                startAngle + (h + 1) / 8f * Math.PI.toFloat() * 0.5f
            }
            val r = if (p.pos < 52) trackR else trackR - 40f - (p.pos - 52) * 30f
            val sx = cx + r * cos(angle)
            val sy = cy + r * sin(angle)
            drawCircle(
                if (p.side == 1) Color(0xFFEF5350) else Color(0xFFFFEE58),
                radius = 13f, center = Offset(sx, sy),
            )
            drawCircle(
                if (p.side == 1) Color(0xFFC62828) else Color(0xFFF9A825),
                radius = 13f, center = Offset(sx, sy), style = Stroke(2f),
            )
        }

        // 在家棋子
        for (side in 1..2) {
            val homePieces = pieces.filter { it.side == side && it.pos < 0 }
            val hx = if (side == 1) 60f else size.width - 60f
            val hy = if (side == 1) size.height - 60f else 60f
            homePieces.forEachIndexed { k, _ ->
                val px = hx + (k % 2) * 28f
                val py = hy + (k / 2) * 28f
                drawCircle(
                    if (side == 1) Color(0xFFEF5350) else Color(0xFFFFEE58),
                    radius = 12f, center = Offset(px, py),
                )
            }
        }

        // 骰子
        if (lastRoll != null) {
            drawRect(Color.White, topLeft = Offset(cx - 25, cy - 25), size = Size(50f, 50f))
            drawRect(Color(0xFF333333), topLeft = Offset(cx - 25, cy - 25), size = Size(50f, 50f), style = Stroke(2f))
        }
    }
}

data class LudoPiece(val side: Int, val p: Int, val pos: Int)
