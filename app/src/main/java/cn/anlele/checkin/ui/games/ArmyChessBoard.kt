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
 * 军旗棋盘 —— 5 列 × 12 行 + 铁路粗线/公路细线/山界/行营双圈/大本营 + 长方军牌。
 * 拓扑常量与服务端 ArmyChessBoard.cs / 网页 armychess.js 三端同源（§9.1）。
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
    val railRows = remember { intArrayOf(1, 5, 6, 10) }
    val railCols = remember { intArrayOf(0, 4) }
    val camps = remember {
        listOf(1 to 2, 3 to 2, 2 to 3, 1 to 4, 3 to 4, 1 to 7, 3 to 7, 2 to 8, 1 to 9, 3 to 9)
    }
    val dens = remember { listOf(1 to 0, 3 to 0, 1 to 11, 3 to 11) }
    val gaps = remember { listOf((1 to 5) to (1 to 6), (3 to 5) to (3 to 6)) }
    val campDiag = remember {
        val upper = listOf(
            1 to 2 to (0 to 1), 1 to 2 to (2 to 1), 1 to 2 to (0 to 3), 1 to 2 to (2 to 3),
            3 to 2 to (2 to 1), 3 to 2 to (4 to 1), 3 to 2 to (2 to 3), 3 to 2 to (4 to 3),
            2 to 3 to (1 to 2), 2 to 3 to (3 to 2), 2 to 3 to (1 to 4), 2 to 3 to (3 to 4),
            1 to 4 to (0 to 3), 1 to 4 to (2 to 3), 1 to 4 to (0 to 5), 1 to 4 to (2 to 5),
            3 to 4 to (2 to 3), 3 to 4 to (4 to 3), 3 to 4 to (2 to 5), 3 to 4 to (4 to 5),
        )
        upper + upper.map { (c, d) -> (c.first to 11 - c.second) to (d.first to 11 - d.second) }
    }

    fun isRail(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        if (y1 == y2 && railRows.contains(y1) && Math.abs(x1 - x2) == 1) return true
        if (x1 == x2 && railCols.contains(x1) && Math.abs(y1 - y2) == 1) return true
        if ((x1 == 2 && y1 == 5 && x2 == 2 && y2 == 6) || (x1 == 2 && y1 == 6 && x2 == 2 && y2 == 5)) return true
        return false
    }
    fun isGap(x1: Int, y1: Int, x2: Int, y2: Int): Boolean =
        gaps.any { (a, b) -> (a.first == x1 && a.second == y1 && b.first == x2 && b.second == y2) ||
                (a.first == x2 && a.second == y2 && b.first == x1 && b.second == y1) }
    fun isCampDiag(x1: Int, y1: Int, x2: Int, y2: Int): Boolean =
        campDiag.any { (c, d) -> (c == (x1 to y1) && d == (x2 to y2)) || (c == (x2 to y2) && d == (x1 to y1)) }
    fun isRoad(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        if (isGap(x1, y1, x2, y2)) return false
        if (Math.abs(x1 - x2) + Math.abs(y1 - y2) == 1 && !isRail(x1, y1, x2, y2)) return true
        return isCampDiag(x1, y1, x2, y2)
    }

    GridBoardRect(
        cols = 5,
        rows = 12,
        modifier = modifier,
        bgColor = Color(0xFFC8B896),
        onTap = onTap,
        drawCell = { geom, x, y ->
            when (armyChessCellType(x, y)) {
                1 -> drawRect(Color(0x60A5D6A7), topLeft = geom.start(x, y), size = geom.cellSize())
                2 -> drawRect(Color(0x60EF9A9A), topLeft = geom.start(x, y), size = geom.cellSize())
            }
        },
        drawOverlay = { geom ->
            // 公路（细线）
            for (y in 0 until 12) for (x in 0 until 5) {
                if (x + 1 < 5 && isRoad(x, y, x + 1, y))
                    drawLine(Color(0xFFA08A63), geom.center(x, y), geom.center(x + 1, y), 2f)
                if (y + 1 < 12 && isRoad(x, y, x, y + 1))
                    drawLine(Color(0xFFA08A63), geom.center(x, y), geom.center(x, y + 1), 2f)
            }
            for ((c, d) in campDiag)
                drawLine(Color(0xFFA08A63), geom.center(c.first, c.second), geom.center(d.first, d.second), 2f)
            // 铁路（粗线双轨）
            for (y in 0 until 12) for (x in 0 until 5) {
                if (x + 1 < 5 && isRail(x, y, x + 1, y)) {
                    drawLine(Color(0xFF4E342E), geom.center(x, y), geom.center(x + 1, y), 7f)
                    drawLine(Color(0xFFE8DDC8), geom.center(x, y), geom.center(x + 1, y), 2f)
                }
                if (y + 1 < 12 && isRail(x, y, x, y + 1)) {
                    drawLine(Color(0xFF4E342E), geom.center(x, y), geom.center(x, y + 1), 7f)
                    drawLine(Color(0xFFE8DDC8), geom.center(x, y), geom.center(x, y + 1), 2f)
                }
            }
            // 山界缺口
            for ((a, b) in gaps) {
                val mx = (geom.center(a.first, a.second).x + geom.center(b.first, b.second).x) / 2
                val my = (geom.center(a.first, a.second).y + geom.center(b.first, b.second).y) / 2
                val t = measurer.measure("山", TextStyle(fontSize = 16.sp, color = Color(0xFF6D4C41), fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(mx - t.size.width / 2f, my - t.size.height / 2f))
            }
            // 行营双圈 / 大本营
            for ((x, y) in camps) {
                drawCircle(Color(0xFFC8E6C9), radius = geom.cellW * 0.42f, center = geom.center(x, y))
                drawCircle(Color(0xFF2E7D32), radius = geom.cellW * 0.42f, center = geom.center(x, y), style = Stroke(width = 2f))
                drawCircle(Color(0xFF2E7D32), radius = geom.cellW * 0.32f, center = geom.center(x, y), style = Stroke(width = 1f))
            }
            for ((x, y) in dens) {
                drawCircle(Color(0xFFFFCDD2), radius = geom.cellW * 0.42f, center = geom.center(x, y))
                drawCircle(Color(0xFFB71C1C), radius = geom.cellW * 0.42f, center = geom.center(x, y), style = Stroke(width = 2f))
            }

            // 棋子（长方军牌；暗子背面「军」；翻棋暗子无归属 → 中性灰）
            for (p in pieces) {
                val tagW = geom.cellW * 0.78f
                val tagH = geom.cellH * 0.46f
                val cx = geom.center(p.x, p.y)
                drawRoundRect(
                    color = when (p.side) {
                        1 -> Color(0xFFD32F2F)
                        2 -> Color(0xFF1565C0)
                        else -> Color(0xFF616161)
                    },
                    topLeft = Offset(cx.x - tagW / 2, cx.y - tagH / 2),
                    size = Size(tagW, tagH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                )
                val label = if (p.hidden) "军" else (rankNames[p.rank] ?: "?")
                val t = measurer.measure(label, TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(cx.x - t.size.width / 2f, cx.y - t.size.height / 2f))
            }

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

// ==================== 布阵生成器（明棋自摆阵，约束与服务端/网页一致） ====================

data class ArmyChessDeployPiece(val x: Int, val y: Int, val rank: String)

private val armySpec = listOf(
    "rank_commander" to 1, "rank_army" to 1, "rank_division" to 2, "rank_brigade" to 2,
    "rank_regiment" to 2, "rank_battalion" to 2, "rank_company" to 3, "rank_platoon" to 3,
    "rank_engineer" to 3, "rank_mine" to 3, "rank_bomb" to 2, "rank_flag" to 1,
)
private val armyCamps = setOf(
    1 to 2, 3 to 2, 2 to 3, 1 to 4, 3 to 4,
    1 to 7, 3 to 7, 2 to 8, 1 to 9, 3 to 9,
)

/** 随机生成一套合法布阵：旗∈己方大本营 / 雷末两排 / 炸弹非前线排 / 行营空 / 一格一子 */
fun armyChessGenDeploy(side: Int): List<ArmyChessDeployPiece> {
    val cells = mutableListOf<Pair<Int, Int>>()
    for (y in 0 until 12) for (x in 0 until 5) {
        if (if (side == 1) y < 6 else y > 5) continue
        if ((x to y) in armyCamps) continue
        cells.add(x to y)
    }
    // 从底线往外排，保证雷/旗落后排
    cells.sortWith(if (side == 1) compareByDescending { it.second } else compareBy { it.second })
    val den = if (side == 1) (1 to 11) else (1 to 0)
    val backRows = if (side == 1) setOf(10, 11) else setOf(0, 1)
    val frontRow = if (side == 1) 6 else 5

    fun pick(pred: (Pair<Int, Int>) -> Boolean): Pair<Int, Int>? {
        val idx = cells.indexOfFirst(pred)
        return if (idx >= 0) cells.removeAt(idx) else null
    }

    val out = mutableListOf<ArmyChessDeployPiece>()
    out.add(ArmyChessDeployPiece(den.first, den.second, "rank_flag"))
    cells.removeAll { it == den }   // 旗格从候选移除，否则后续子会重复布到同一格
    repeat(3) { pick { it.second in backRows }?.let { c -> out.add(ArmyChessDeployPiece(c.first, c.second, "rank_mine")) } }
    repeat(2) { pick { it.second != frontRow }?.let { c -> out.add(ArmyChessDeployPiece(c.first, c.second, "rank_bomb")) } }
    for ((rank, cnt) in armySpec) {
        if (rank == "rank_flag" || rank == "rank_mine" || rank == "rank_bomb") continue
        repeat(cnt) { pick { true }?.let { c -> out.add(ArmyChessDeployPiece(c.first, c.second, rank)) } }
    }
    return out
}

/** 交换两格棋子；交换后任一格违反约束则拒绝（约束即时反馈 = 不可换） */
fun armyChessTrySwap(
    side: Int,
    pieces: List<ArmyChessDeployPiece>,
    ax: Int, ay: Int, bx: Int, by: Int,
): List<ArmyChessDeployPiece>? {
    val ra = pieces.firstOrNull { it.x == ax && it.y == ay }?.rank ?: return null
    val rb = pieces.firstOrNull { it.x == bx && it.y == by }?.rank ?: return null
    fun legal(x: Int, y: Int, rank: String): Boolean = when (rank) {
        "rank_flag" -> armyChessCellType(x, y) == 2 && (if (side == 1) y == 11 else y == 0)
        "rank_mine" -> if (side == 1) y >= 10 else y <= 1
        "rank_bomb" -> if (side == 1) y != 6 else y != 5
        else -> true
    }
    if (!legal(bx, by, ra) || !legal(ax, ay, rb)) return null
    return pieces.map {
        when {
            it.x == ax && it.y == ay -> ArmyChessDeployPiece(bx, by, it.rank)
            it.x == bx && it.y == by -> ArmyChessDeployPiece(ax, ay, it.rank)
            else -> it
        }
    }
}
