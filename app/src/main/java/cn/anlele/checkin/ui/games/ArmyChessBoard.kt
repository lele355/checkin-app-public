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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * 军棋棋盘 —— 5 列 × 12 行（竖长方）+ 行营/大本营 + 暗子渲染 + 棋子带字。
 * 硬要求 2：camp/den 是棋盘格属性（由 armyChessCellType 提供），不在 piece 对象里。
 * 固定色不跟主题（红线 18）。对应网页 boards/armychess.js。
 */
@Composable
fun ArmyChessBoard(
    pieces: List<ArmyChessPiece>,
    lastMove: ArmyChessLastMove? = null,
    showCoord: Boolean = false,
    lastMark: Boolean = true,
    modifier: Modifier = Modifier,
    onTap: ((Int, Int) -> Unit)? = null,
    selected: Pair<Int, Int>? = null,
) {
    val measurer = rememberTextMeasurer()
    val rankNames = remember {
        mapOf(
            "rank_commander" to "司令", "rank_army" to "军长", "rank_division" to "师长",
            "rank_brigade" to "旅长", "rank_regiment" to "团长", "rank_battalion" to "营长",
            "rank_company" to "连长", "rank_platoon" to "排长", "rank_engineer" to "工兵",
            "rank_mine" to "地雷", "rank_flag" to "军旗", "rank_bomb" to "炸弹",
        )
    }
    GridBoardRect(
        cols = 5,
        rows = 12,
        modifier = modifier,
        bgColor = Color(0xFFC8B896),
        onTap = onTap,
        drawCell = { geom, x, y ->
            // 特殊格（硬要求 2：格子属性）
            when (armyChessCellType(x, y)) {
                1 -> drawRect(Color(0x60A5D6A7), topLeft = geom.start(x, y), size = geom.cellSize())
                2 -> drawRect(Color(0x60EF9A9A), topLeft = geom.start(x, y), size = geom.cellSize())
            }
            // 棋子
            val piece = pieces.find { it.x == x && it.y == y }
            if (piece != null) {
                val r = minOf(geom.cellW, geom.cellH) * 0.4f
                drawCircle(
                    color = if (piece.side == 1) Color(0xFFD32F2F) else Color(0xFF1565C0),
                    radius = r, center = geom.center(x, y),
                )
                drawCircle(Color.White, radius = r, center = geom.center(x, y), style = Stroke(width = 2f))
                // 明子带字；暗子不显字（对面看不见的子由服务端不下发或 hidden 标记）
                if (!piece.hidden && piece.rank != null) {
                    val name = rankNames[piece.rank] ?: ""
                    if (name.isNotEmpty()) {
                        val t = measurer.measure(name, TextStyle(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold))
                        drawText(t, topLeft = Offset(geom.center(x, y).x - t.size.width / 2f, geom.center(x, y).y - t.size.height / 2f))
                    }
                }
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

data class ArmyChessPiece(val x: Int, val y: Int, val side: Int, val rank: String?, val hidden: Boolean)
data class ArmyChessLastMove(val fx: Int, val fy: Int, val tx: Int, val ty: Int)

/** 特殊格类型（与服务端 ArmyChessRules.CellType 一致）：0 普通 / 1 行营 / 2 大本营 */
fun armyChessCellType(x: Int, y: Int): Int = when {
    (x == 1 || x == 3) && (y == 0 || y == 11) -> 2 // 大本营
    (x == 1 || x == 3) && (y == 2 || y == 4 || y == 7 || y == 9) -> 1 // 行营
    x == 2 && (y == 3 || y == 8) -> 1 // 行营（中路）
    else -> 0
}
