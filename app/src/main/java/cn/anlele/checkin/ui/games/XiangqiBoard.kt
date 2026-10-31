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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    hints: Map<String, List<List<Int>>>? = null,
    anim: BoardAnim? = null,
    ghost: XiangqiPiece? = null,
    ghostAlpha: Float = 0f,
) {
    val measurer = rememberTextMeasurer()
    // 木纹圆饼字子图标（icons-ready 同源，D-5 图标二阶段）：type 1..7 × 红黑两套
    val pieceIcons = rememberPieceIcons("xiangqi", listOf(
        "red_shuai", "red_shi", "red_xiang", "red_ma", "red_che", "red_pao", "red_bing",
        "black_jiang", "black_shi", "black_xiang", "black_ma", "black_che", "black_pao", "black_zu",
    ))
    // 图标件名（下标 = 服务端 PieceType）：红=帅仕相马车炮兵 / 黑=将士象马车炮卒
    val xqIconRed = remember { listOf("", "red_shuai", "red_shi", "red_xiang", "red_ma", "red_che", "red_pao", "red_bing") }
    val xqIconBlack = remember { listOf("", "black_jiang", "black_shi", "black_xiang", "black_ma", "black_che", "black_pao", "black_zu") }
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
        fun pxf(x: Float) = pad + x * cw
        fun pyf(y: Float) = pad + y * ch

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

        // 可走提示（Batch D）：绿点=可走，红环=可吃
        if (selected != null && hints != null) {
            val key = "${selected.first},${selected.second}"
            for (tp in hints[key] ?: emptyList()) {
                val occ = pieces.any { it.x == tp[0] && it.y == tp[1] }
                if (occ) {
                    drawCircle(Color(0xFFE53935), radius = r * 1.15f, center = Offset(px(tp[0]), py(tp[1])), style = Stroke(width = 3f))
                } else {
                    drawCircle(Color(0xBF2E7D32), radius = r * 0.28f, center = Offset(px(tp[0]), py(tp[1])))
                }
            }
        }

        // 棋子（整枚图标，icons-ready 同源；走子动画插值 + 落点回弹缩放）
        for (p in pieces) {
            val isAnim = anim != null && p.x == anim.toX && p.y == anim.toY
            val center = if (isAnim) Offset(pxf(anim.x), pyf(anim.y)) else Offset(px(p.x), py(p.y))
            val rr = if (isAnim) r * anim.scale else r
            val key = (if (p.side == 1) xqIconRed else xqIconBlack).getOrElse(p.type) { "" }
            val bmp = pieceIcons[key]
            if (bmp != null) {
                val side = (rr * 2.2f).toInt().coerceAtLeast(2)   // 图标圆饼约占盒 94%，对齐旧 r
                drawImage(
                    bmp,
                    dstSize = IntSize(side, side),
                    dstOffset = IntOffset((center.x - side / 2f).toInt(), (center.y - side / 2f).toInt()),
                )
            }
        }

        // 吃子鬼影（Batch D）：被吃子淡出（图标同源）
        if (ghost != null && ghostAlpha > 0f) {
            val gc = Offset(px(ghost.x), py(ghost.y))
            val keyG = (if (ghost.side == 1) xqIconRed else xqIconBlack).getOrElse(ghost.type) { "" }
            val bmpG = pieceIcons[keyG]
            if (bmpG != null) {
                val sideG = (r * 2.2f).toInt().coerceAtLeast(2)
                drawImage(
                    bmpG,
                    dstSize = IntSize(sideG, sideG),
                    dstOffset = IntOffset((gc.x - sideG / 2f).toInt(), (gc.y - sideG / 2f).toInt()),
                    alpha = ghostAlpha,
                )
            }
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
