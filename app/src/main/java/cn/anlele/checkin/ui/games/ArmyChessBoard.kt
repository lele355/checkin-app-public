package cn.anlele.checkin.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 军棋棋盘 —— 5×12 格子 + 行营/大本营 + 暗子渲染。
 * 硬要求 2：camp/den 是棋盘格属性（由 jungleCellType 提供），不在 piece 对象里。
 * 固定色不跟主题（红线 18）。
 */
@Composable
fun ArmyChessBoard(
    pieces: List<ArmyChessPiece>,
    lastMove: ArmyChessLastMove? = null,
    showCoord: Boolean = false,
    lastMark: Boolean = true,
    modifier: Modifier = Modifier,
    onTap: ((Int, Int) -> Unit)? = null,
) {
    GridBoard(
        size = 5,
        modifier = modifier,
        bgColor = Color(0xFFC8B896),
        showCoord = showCoord,
        lastMark = lastMark,
        onTap = onTap,
        drawCell = { geom, x, y ->
            // 特殊格（硬要求 2：格子属性）
            val cellType = armyChessCellType(x, y)
            when (cellType) {
                1 -> drawRect(Color(0x60A5D6A7), topLeft = androidx.compose.ui.geometry.Offset(geom.cellStartPx(x), geom.cellStartPx(y)), size = androidx.compose.ui.geometry.Size(geom.cellPx, geom.cellPx))
                2 -> drawRect(Color(0x60EF9A9A), topLeft = androidx.compose.ui.geometry.Offset(geom.cellStartPx(x), geom.cellStartPx(y)), size = androidx.compose.ui.geometry.Size(geom.cellPx, geom.cellPx))
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

data class ArmyChessPiece(val x: Int, val y: Int, val side: Int, val rank: String?, val hidden: Boolean)
data class ArmyChessLastMove(val fx: Int, val fy: Int, val tx: Int, val ty: Int)

/** 棋盘格类型（硬要求 2：camp/den 是格子属性，不是棋子属性） */
fun armyChessCellType(x: Int, y: Int): Int = when {
    (x == 1 && y == 0) || (x == 3 && y == 0) || (x == 1 && y == 11) || (x == 3 && y == 11) -> 2 // den
    (x == 1 && y == 2) || (x == 3 && y == 2) || (x == 2 && y == 3) || (x == 1 && y == 4) || (x == 3 && y == 4) ||
    (x == 1 && y == 7) || (x == 3 && y == 7) || (x == 2 && y == 8) || (x == 1 && y == 9) || (x == 3 && y == 9) -> 1 // camp
    else -> 0
}
