package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * 象棋棋盘 —— 9×10 交叉点 + 楚河汉界 + 棋子带字。别画成格子棋（红线：交叉点是特征）。
 * 固定色不跟主题。对应网页 boards/xiangqi.js。
 */
@Composable
fun XiangqiBoard(
    pieces: List<XiangqiPiece>,
    lastMove: XiangqiLastMove? = null,
    inCheck: Boolean = false,
    showCoord: Boolean = false,
    lastMark: Boolean = true,
    modifier: Modifier = Modifier,
    onTap: ((Int, Int) -> Unit)? = null,
    selected: Pair<Int, Int>? = null,
) {
    val measurer = rememberTextMeasurer()
    // 红=帅仕相马车炮兵 / 黑=将士象马车炮卒（下标 = 服务端 PieceType）
    val redNames = remember { arrayOf("", "帅", "仕", "相", "马", "车", "炮", "兵") }
    val blackNames = remember { arrayOf("", "将", "士", "象", "马", "车", "炮", "卒") }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(640f / 720f)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val pad = w * 50f / 640f
                    val cw = (w - pad * 2) / 8f
                    val ch = (h - pad * 2) / 9f
                    val gx = ((pos.x - pad + cw / 2) / cw).toInt()
                    val gy = ((pos.y - pad + ch / 2) / ch).toInt()
                    if (gx in 0..8 && gy in 0..9) onTap?.invoke(gx, gy)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val pad = w * 50f / 640f
        val cw = (w - pad * 2) / 8f
        val ch = (h - pad * 2) / 9f

        fun px(x: Int) = pad + x * cw
        fun py(y: Int) = pad + y * ch

        // 背景
        drawRect(Color(0xFFF0D9A8))

        // 格线（交叉点连线）
        for (i in 0..8) {
            drawLine(Color(0xFF5D4037), Offset(px(i), py(0)), Offset(px(i), py(9)), 1.2f)
        }
        for (j in 0..4) {
            drawLine(Color(0xFF5D4037), Offset(px(0), py(j)), Offset(px(8), py(j)), 1.2f)
            drawLine(Color(0xFF5D4037), Offset(px(0), py(j + 5)), Offset(px(8), py(j + 5)), 1.2f)
        }

        // 九宫斜线
        drawLine(Color(0xFF5D4037), Offset(px(3), py(0)), Offset(px(5), py(2)), 1.2f)
        drawLine(Color(0xFF5D4037), Offset(px(5), py(0)), Offset(px(3), py(2)), 1.2f)
        drawLine(Color(0xFF5D4037), Offset(px(3), py(7)), Offset(px(5), py(9)), 1.2f)
        drawLine(Color(0xFF5D4037), Offset(px(5), py(7)), Offset(px(3), py(9)), 1.2f)

        // 楚河汉界
        drawRect(Color(0x808D6E63), topLeft = Offset(px(0), py(4) + ch * 0.1f), size = Size(px(8) - px(0), ch * 0.8f))

        val r = cw * 0.38f
        // 两段式：选中起点高亮
        if (selected != null) {
            drawCircle(
                Color(0xFF1A73E8), radius = r * 1.25f,
                center = Offset(px(selected.first), py(selected.second)),
                style = Stroke(width = 4f),
            )
        }

        // 棋子（圆 + 汉字）
        for (p in pieces) {
            val center = Offset(px(p.x), py(p.y))
            drawCircle(Color(0xFFF5E6C8), radius = r, center = center)
            drawCircle(
                if (p.side == 1) Color(0xFFC62828) else Color(0xFF212121),
                radius = r, center = center, style = Stroke(width = 2.5f),
            )
            val name = (if (p.side == 1) redNames else blackNames).getOrElse(p.type) { "?" }
            val t = measurer.measure(name, TextStyle(fontSize = (r * 0.9f).sp, color = if (p.side == 1) Color(0xFFC62828) else Color(0xFF212121), fontWeight = FontWeight.Bold))
            drawText(t, topLeft = Offset(center.x - t.size.width / 2f, center.y - t.size.height / 2f))
        }

        // 最后一手
        if (lastMark && lastMove != null) {
            drawCircle(
                Color(0xFFFF5722), radius = r * 1.2f,
                center = Offset(px(lastMove.tx), py(lastMove.ty)),
                style = Stroke(width = 3f),
            )
        }

        // 被将军提示
        if (inCheck) {
            val t = measurer.measure("将军", TextStyle(fontSize = 20.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold))
            drawText(t, topLeft = Offset(w / 2f - t.size.width / 2f, h * 0.46f))
        }
    }
}

data class XiangqiPiece(val x: Int, val y: Int, val side: Int, val type: Int)
data class XiangqiLastMove(val fx: Int, val fy: Int, val tx: Int, val ty: Int)
