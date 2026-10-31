package cn.anlele.checkin.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    tapConfirm: Boolean = false,
    revision: Int = 0,
    lastFlips: List<Pair<Int, Int>> = emptyList(),
    onTap: ((Int, Int) -> Unit)? = null,
) {
    val size = 8
    // 渐变圆子图标（icons-ready 同源，D-5）
    val stoneIcons = rememberPieceIcons("stone", listOf("black", "white"))
    // 落子确认预览（任务二尾巴，与 App 五子棋 Board 同规）：第一下预览、同点第二下落
    var ghost by remember(revision) { mutableStateOf<Int?>(null) }
    // 落子/翻子动画进度（任务二尾巴）：新着回弹 + 翻子挤压
    var placeProg by remember(revision) { mutableFloatStateOf(1f) }
    val flips = remember(revision, lastFlips) { lastFlips }
    LaunchedEffect(revision) {
        placeProg = 0f
        val t0 = withFrameNanos { it }
        while (placeProg < 1f) {
            withFrameNanos { ts -> placeProg = ((ts - t0) / 1_000_000f / 260f).coerceAtMost(1f) }
        }
    }
    val confirmTap: ((Int, Int) -> Unit)? = if (onTap == null) null else fun(x: Int, y: Int) {
        if (board[x][y] != 0) return
        if (tapConfirm) {
            val key = y * 8 + x
            if (ghost != key) { ghost = key; return }
            ghost = null
        }
        onTap(x, y)
    }
    GridBoard(
        size = size,
        modifier = modifier,
        bgColor = GameBoardColors.ReversiBg,
        showCoord = showCoord,
        lastMark = lastMark,
        onTap = confirmTap,
        drawCell = { geom, x, y ->
            val side = board[x][y]
            if (side != 0) {
                // 渐变圆子整枚图标（icons-ready 同源，D-5 双端同规）；新着回弹 + 翻子挤压（任务二尾巴）
                val bmp = stoneIcons[if (side == 1) "black" else "white"]
                if (bmp != null) {
                    val c = geom.center(x, y)
                    val isLast = lastMove == (x to y) && placeProg < 1f
                    val isFlip = flips.contains(x to y) && placeProg < 1f
                    val base = geom.cellPx * 0.9f
                    val hh = if (isLast) {
                        val pr = 1f - (1f - placeProg) * (1f - placeProg)
                        base * (0.35f + 0.65f * pr)
                    } else base
                    val ww = if (isFlip) {
                        val squeeze = kotlin.math.abs(kotlin.math.cos(Math.PI.toFloat() * placeProg))
                        (base * maxOf(0.08f, squeeze))
                    } else hh
                    val spH = hh.toInt().coerceAtLeast(2)
                    val spW = ww.toInt().coerceAtLeast(2)
                    drawImage(
                        bmp,
                        dstSize = IntSize(spW, spH),
                        dstOffset = IntOffset((c.x - spW / 2f).toInt(), (c.y - spH / 2f).toInt()),
                    )
                }
            } else if (ghost == y * 8 + x && myTurn) {
                // 落子确认预览（任务二尾巴）：半透明石子
                val bmpP = stoneIcons[if (mySide == 1) "black" else "white"]
                if (bmpP != null) {
                    val sp = (geom.cellPx * 0.9f).toInt().coerceAtLeast(2)
                    val c = geom.center(x, y)
                    drawImage(
                        bmpP,
                        dstSize = IntSize(sp, sp),
                        dstOffset = IntOffset((c.x - sp / 2f).toInt(), (c.y - sp / 2f).toInt()),
                        alpha = 0.45f,
                    )
                }
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

/** 最后一手翻转的格（任务二尾巴：翻子动画）；无着返回空 */
fun reversiLastFlips(events: List<Map<String, Any?>>): List<Pair<Int, Int>> {
    val board = Array(8) { IntArray(8) }
    board[3][3] = 2; board[4][4] = 2
    board[3][4] = 1; board[4][3] = 1
    var last: List<Pair<Int, Int>> = emptyList()
    for (e in events) {
        val t = e["t"] as? String ?: continue
        if (t != "move") continue
        val x = (e["x"] as? Number)?.toInt() ?: continue
        val y = (e["y"] as? Number)?.toInt() ?: continue
        val sv = (e["s"] as? Number)?.toInt() ?: continue
        if (x !in 0..7 || y !in 0..7 || board[x][y] != 0) continue
        board[x][y] = sv
        last = reversiFlips(board, x, y, sv).also { f -> f.forEach { (fx, fy) -> board[fx][fy] = sv } }
    }
    return last
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
