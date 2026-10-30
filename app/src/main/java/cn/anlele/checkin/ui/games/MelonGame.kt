package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.data.LeaderboardDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * 合成大西瓜 —— 单机 · 合并同款水果 · 冲击大西瓜。
 * 纯前端算分，结束时上报最高分（服务端幂等 max）。
 */
@Composable
fun MelonGame(
    maxScore: Int,
    onBack: () -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()

    var score by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var leaderboard by remember { mutableStateOf<LeaderboardDto?>(null) }
    var board by remember { mutableStateOf(MelonState.new()) }

    fun restart() {
        board = MelonState.new()
        score = 0
        over = false
        leaderboard = null
    }

    fun endGame(finalScore: Int) {
        over = true
        scope.launch {
            runCatching { Repo.submitScore("melon", min(finalScore, maxScore)) }
            runCatching { leaderboard = Repo.leaderboard("melon") }
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("合成大西瓜", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            SecondaryButton("返回") { onBack() }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$score 分", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.accentStrong)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("重开") { restart() }
                SecondaryButton("排行榜") { scope.launch { runCatching { leaderboard = Repo.leaderboard("melon") } } }
            }
        }
        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxWidth().aspectRatio(10f / 16f)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(board, over) {
                        detectTapGestures { pos ->
                            if (over) return@detectTapGestures
                            val col = (pos.x / size.width * 10).toInt().coerceIn(0, 9)
                            val result = board.dropAndMerge(col)
                            if (result == null) {
                                board = board.spawn()
                            } else {
                                board = result.newState
                                score += result.gained
                                if (result.newState.isOver()) endGame(score)
                                else board = result.newState.spawn()
                            }
                        }
                    }
            ) {
                drawRect(Color(0xFFF5E6C8))
                val cw = size.width / 10f
                val ch = size.height / 16f
                drawLine(Color(0xFFD32F2F), Offset(0f, ch), Offset(size.width, ch), 3f)
                for (r in 0 until 16) for (c in 0 until 10) {
                    val t = board.grid[r][c]
                    if (t >= 0) {
                        val center = Offset(c * cw + cw / 2, r * ch + ch / 2)
                        drawCircle(MELON_COLORS[t], (10 + t * 2.5f) * (cw / 36f), center)
                    }
                }
                if (!over && board.cur >= 0) {
                    val center = Offset(board.curCol * cw + cw / 2, ch)
                    drawCircle(MELON_COLORS[board.cur], 14f * (cw / 36f), center, alpha = 0.7f)
                }
            }
            if (over) {
                Text(
                    "本局 $score 分",
                    Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        leaderboard?.let { lb ->
            Spacer(Modifier.height(10.dp))
            Text("排行榜", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            lb.items.forEach { it ->
                Text("${it.rank}. ${it.name} · ${it.bestScore}", fontSize = 13.sp, color = p.textSub)
            }
            Text("我的最高分 ${lb.mine.bestScore} · 第 ${lb.mine.rank} 名", fontSize = 13.sp, color = p.textSub)
        }
    }
}

/** 合成大西瓜棋盘状态（不可变） */
private data class MelonState(
    val grid: List<IntArray>,
    val cur: Int,
    val curCol: Int,
) {
    companion object {
        fun new() = MelonState(
            grid = List(16) { IntArray(10) { -1 } },
            cur = (0..4).random(),
            curCol = 4,
        )
    }

    fun spawn() = copy(cur = (0..4).random(), curCol = 4)

    fun isOver(): Boolean = grid[1].any { it != -1 }

    /** 落子 + 连锁合并；返回新状态与得分；列满返回 null */
    fun dropAndMerge(col: Int): MergeResult? {
        var r = 15
        while (r >= 0 && grid[r][col] != -1) r--
        if (r < 0) return null
        val g = grid.map { it.copyOf() }
        g[r][col] = cur
        val gained = mergeChain(g, r, col)
        return MergeResult(MelonState(g, cur, col), gained)
    }

    private fun mergeChain(g: List<IntArray>, r: Int, c: Int): Int {
        val t = g[r][c]
        if (t < 0 || t >= 10) return 0
        val dirs = listOf(0 to -1, 0 to 1, 1 to 0, -1 to 0)
        for ((dr, dc) in dirs) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..15 && nc in 0..9 && g[nr][nc] == t) {
                g[r][c] = -1
                g[nr][nc] = -1
                val mr = maxOf(r, nr)
                g[mr][c] = t + 1
                return MELON_SCORES[t + 1] + mergeChain(g, mr, c)
            }
        }
        return 0
    }
}

private data class MergeResult(val newState: MelonState, val gained: Int)

private val MELON_SCORES = listOf(1, 2, 4, 8, 15, 30, 60, 120, 240, 480, 960)
private val MELON_COLORS = listOf(
    Color(0xFF9C27B0), Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835),
    Color(0xFF7CB342), Color(0xFFEF5350), Color(0xFFF48FB1), Color(0xFFF9A825),
    Color(0xFF8D6E63), Color(0xFF4CAF50), Color(0xFF2E7D32),
)
