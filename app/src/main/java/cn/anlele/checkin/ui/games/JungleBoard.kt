package cn.anlele.checkin.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * 斗兽棋棋盘 —— 7 列 × 9 行（竖长方，非方阵）+ 河/陷阱/兽穴 + 8 兽带字。
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
    selected: Pair<Int, Int>? = null,
) {
    val measurer = rememberTextMeasurer()
    val rankNames = remember { arrayOf("鼠", "猫", "狗", "狼", "豹", "虎", "狮", "象") }
    GridBoardRect(
        cols = 7,
        rows = 9,
        modifier = modifier,
        bgColor = Color(0xFFF5E6C8),
        onTap = onTap,
        drawCell = { geom, x, y ->
            // 特殊格
            when (jungleCellType(x, y)) {
                1 -> drawRect(Color(0x707EC8E3), topLeft = geom.start(x, y), size = geom.cellSize())
                2 -> drawRect(Color(0x60FFCC80), topLeft = geom.start(x, y), size = geom.cellSize())
                3 -> drawRect(Color(0x60EF9A9A), topLeft = geom.start(x, y), size = geom.cellSize())
            }
            // 陷阱/兽穴标记
            val mark = when (jungleCellType(x, y)) {
                2 -> "陷"
                3 -> "穴"
                else -> null
            }
            if (mark != null) {
                val t = measurer.measure(mark, TextStyle(fontSize = 11.sp, color = Color(0xFF8D6E63)))
                drawText(t, topLeft = Offset(geom.center(x, y).x - t.size.width / 2f, geom.center(x, y).y - t.size.height / 2f))
            }

            // 棋子（圆 + 兽名）
            val piece = pieces.find { it.x == x && it.y == y }
            if (piece != null) {
                val r = minOf(geom.cellW, geom.cellH) * 0.4f
                drawCircle(if (piece.side == 1) Color(0xFFD32F2F) else Color(0xFF1565C0), radius = r, center = geom.center(x, y))
                drawCircle(Color.White, radius = r, center = geom.center(x, y), style = Stroke(width = 2f))
                val name = rankNames.getOrElse(piece.rank - 1) { "?" }
                val t = measurer.measure(name, TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                drawText(t, topLeft = Offset(geom.center(x, y).x - t.size.width / 2f, geom.center(x, y).y - t.size.height / 2f))
            }
        },
        drawOverlay = { geom ->
            if (lastMark && lastMove != null) {
                drawRect(
                    color = Color(0xFFFF5722),
                    topLeft = Offset(geom.start(lastMove.tx, lastMove.ty).x + 2, geom.start(lastMove.tx, lastMove.ty).y + 2),
                    size = Size(geom.cellW - 4, geom.cellH - 4),
                    style = Stroke(width = 3f),
                )
            }
            // 两段式：选中起点高亮
            if (selected != null) {
                drawRect(
                    color = Color(0xFF1A73E8),
                    topLeft = Offset(geom.start(selected.first, selected.second).x + 2, geom.start(selected.first, selected.second).y + 2),
                    size = Size(geom.cellW - 4, geom.cellH - 4),
                    style = Stroke(width = 4f),
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
