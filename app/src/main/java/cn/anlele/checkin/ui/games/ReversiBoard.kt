package cn.anlele.checkin.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset

/**
 * 黑白棋棋盘 —— 8×8 格、黑白圆子、翻子提示。
 * 固定色不跟主题（红线 18）。对应网页 boards/reversi.js。
 */
@Composable
fun ReversiBoard(
    board: Array<IntArray>,
    lastMove: Pair<Int, Int>? = null,
    showCoord: Boolean = false,
    lastMark: Boolean = true,
    mySide: Int = 0,
    myTurn: Boolean = false,
    modifier: Modifier = Modifier,
    onTap: ((Int, Int) -> Unit)? = null,
) {
    val size = 8
    GridBoard(
        size = size,
        modifier = modifier,
        bgColor = GameBoardColors.ReversiBg,
        showCoord = showCoord,
        lastMark = lastMark,
        onTap = onTap,
        drawCell = { geom, x, y ->
            val side = board[x][y]
            if (side != 0) {
                drawCircle(
                    color = if (side == 1) GameBoardColors.Black else GameBoardColors.White,
                    radius = geom.cellPx * 0.42f,
                    center = geom.center(x, y),
                )
                // 描边
                drawCircle(
                    color = if (side == 1) Color.Black else Color(0xFFCCCCCC),
                    radius = geom.cellPx * 0.42f,
                    center = geom.center(x, y),
                    style = Stroke(width = 1.5f),
                )
            } else if (myTurn && mySide != 0 && canFlip(board, x, y, mySide)) {
                // 可落子提示
                drawCircle(
                    color = if (mySide == 1) GameBoardColors.ReversiHint else Color(0x30FFFFFF),
                    radius = geom.cellPx * 0.15f,
                    center = geom.center(x, y),
                )
            }
        },
        drawOverlay = { geom ->
            if (lastMark && lastMove != null) {
                drawCircle(
                    color = GameBoardColors.LastMark,
                    radius = geom.cellPx * 0.12f,
                    center = geom.center(lastMove.first, lastMove.second),
                )
            }
        },
    )
}

/** 从事件列表重建盘面 */
fun buildReversiBoard(events: List<Map<String, Any?>>): Array<IntArray> {
    val board = Array(8) { IntArray(8) }
    board[3][3] = 2; board[4][4] = 2
    board[3][4] = 1; board[4][3] = 1

    for (e in events) {
        val t = e["t"] as? String ?: continue
        if (t != "move") continue
        val x = (e["x"] as? Number)?.toInt() ?: continue
        val y = (e["y"] as? Number)?.toInt() ?: continue
        val s = (e["s"] as? Number)?.toInt() ?: continue
        if (x !in 0..7 || y !in 0..7 || board[x][y] != 0) continue
        board[x][y] = s
        for ((fx, fy) in reversiFlips(board, x, y, s)) board[fx][fy] = s
    }
    return board
}

/** 计算 (x,y) 落子能翻的对方子 */
fun reversiFlips(board: Array<IntArray>, x: Int, y: Int, side: Int): List<Pair<Int, Int>> {
    val flips = mutableListOf<Pair<Int, Int>>()
    val dirs = listOf(
        1 to 0, -1 to 0, 0 to 1, 0 to -1,
        1 to 1, 1 to -1, -1 to 1, -1 to -1,
    )
    for ((dx, dy) in dirs) {
        val line = mutableListOf<Pair<Int, Int>>()
        var cx = x + dx; var cy = y + dy
        while (cx in 0..7 && cy in 0..7 && board[cx][cy] != 0 && board[cx][cy] != side) {
            line.add(cx to cy)
            cx += dx; cy += dy
        }
        if (line.isNotEmpty() && cx in 0..7 && cy in 0..7 && board[cx][cy] == side) {
            flips.addAll(line)
        }
    }
    return flips
}

/** 检查 (x,y) 是否能翻子 */
fun canFlip(board: Array<IntArray>, x: Int, y: Int, side: Int): Boolean =
    board[x][y] == 0 && reversiFlips(board, x, y, side).isNotEmpty()
