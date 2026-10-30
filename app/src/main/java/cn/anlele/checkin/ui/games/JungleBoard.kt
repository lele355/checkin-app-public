package cn.anlele.checkin.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 斗兽棋棋盘 —— 7×9 格子 + 特殊格（河/陷阱/兽穴）+ 8 种兽。
 * 固定色不跟主题（红线 18）。对应网页 boards/jungle.js。
 */
@Composable
fun JungleBoard(
    pieces: List<JunglePiece>,
    lastMove: JungleLastMove? = null,
    showCoord: Boolean = false,
    lastMark: Boolean = true,
    modifier: Modifier = Modifier,
    onTap: ((Int, Int) -> Unit)? = null,
) {
    GridBoard(
        size = 7,
        modifier = modifier,
        bgColor = Color(0xFFF5E6C8),
        showCoord = showCoord,
        lastMark = lastMark,
        onTap = onTap,
        drawCell = { geom, x, y ->
            // 特殊格
            val cellType = jungleCellType(x, y)
            when (cellType) {
                1 -> drawRect(Color(0x707EC8E3), topLeft = androidx.compose.ui.geometry.Offset(geom.cellStartPx(x), geom.cellStartPx(y)), size = androidx.compose.ui.geometry.Size(geom.cellPx, geom.cellPx))
                2 -> drawRect(Color(0x60FFCC80), topLeft = androidx.compose.ui.geometry.Offset(geom.cellStartPx(x), geom.cellStartPx(y)), size = androidx.compose.ui.geometry.Size(geom.cellPx, geom.cellPx))
                3 -> drawRect(Color(0x60EF9A9A), topLeft = androidx.compose.ui.geometry.Offset(geom.cellStartPx(x), geom.cellStartPx(y)), size = androidx.compose.ui.geometry.Size(geom.cellPx, geom.cellPx))
            }
            // 棋子
            val piece = pieces.find { it.x == x && it.y == y }
            if (piece != null) {
                drawCircle(
                    color = if (piece.side == 1) Color(0xFFD32F2F) else Color(0xFF1565C0),
                    radius = geom.cellPx * 0.4f,
                    center = geom.center(x, y),
                )
            }
        },
        drawOverlay = { geom ->
            if (lastMark && lastMove != null) {
                drawRect(
                    color = Color(0xFFFF5722),
                    topLeft = androidx.compose.ui.geometry.Offset(geom.cellStartPx(lastMove.tx) + 2, geom.cellStartPx(lastMove.ty) + 2),
                    size = androidx.compose.ui.geometry.Size(geom.cellPx - 4, geom.cellPx - 4),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                )
            }
        },
    )
}

data class JunglePiece(val x: Int, val y: Int, val side: Int, val rank: Int)
data class JungleLastMove(val fx: Int, val fy: Int, val tx: Int, val ty: Int)

/** 特殊格类型（与服务端 JungleRules.CellType 一致） */
fun jungleCellType(x: Int, y: Int): Int = when {
    (x == 3 && y == 0) || (x == 3 && y == 8) -> 3 // den
    (x == 2 && y == 0) || (x == 4 && y == 0) || (x == 3 && y == 1) -> 2 // trap
    (x == 2 && y == 8) || (x == 4 && y == 8) || (x == 3 && y == 7) -> 2 // trap
    x in listOf(1, 2, 4, 5) && y in listOf(3, 4, 5) -> 1 // water
    else -> 0
}
