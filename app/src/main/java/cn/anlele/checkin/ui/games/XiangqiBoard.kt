package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 象棋棋盘 —— 9×10 交叉点 + 楚河汉界。别画成格子棋（红线：交叉点是特征）。
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
) {
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
        drawRect(Color(0x808D6E63), topLeft = Offset(px(0), py(4) + ch * 0.1f), size = androidx.compose.ui.geometry.Size(px(8) - px(0), ch * 0.8f))

        // 棋子
        for (p in pieces) {
            drawCircle(Color(0xFFF5E6C8), radius = 22f, center = Offset(px(p.x), py(p.y)))
            drawCircle(
                if (p.side == 1) Color(0xFFC62828) else Color(0xFF212121),
                radius = 22f, center = Offset(px(p.x), py(p.y)),
                style = Stroke(width = 2.5f),
            )
        }

        // 最后一手
        if (lastMark && lastMove != null) {
            drawCircle(
                Color(0xFFFF5722), radius = 26f,
                center = Offset(px(lastMove.tx), py(lastMove.ty)),
                style = Stroke(width = 3f),
            )
        }
    }
}

data class XiangqiPiece(val x: Int, val y: Int, val side: Int, val type: Int)
data class XiangqiLastMove(val fx: Int, val fy: Int, val tx: Int, val ty: Int)
