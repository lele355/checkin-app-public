package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 大鱼吃小鱼 —— 单机 · 吃小鱼变大 · 躲大鱼。
 * 纯前端算分，被大鱼吃掉时上报最高分。
 */
@Composable
fun FishGame(
    maxScore: Int,
    onBack: () -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(FishState.new()) }
    var score by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var leaderboard by remember { mutableStateOf<LeaderboardDto?>(null) }

    fun restart() {
        state = FishState.new()
        score = 0
        over = false
        leaderboard = null
    }

    fun endGame(finalScore: Int) {
        over = true
        scope.launch {
            runCatching { Repo.submitScore("fish", min(finalScore, maxScore)) }
            runCatching { leaderboard = Repo.leaderboard("fish") }
        }
    }

    // 游戏循环：食物/鱼游动 + 碰撞判定
    androidx.compose.runtime.LaunchedEffect(state, over) {
        while (!over) {
            delay(50)
            val step = state.step()
            if (step.eaten) {
                state = step.state
                score += step.gained
                if (step.state.level > 10) { /* 体型已满 */ }
            } else if (step.gameOver) {
                state = step.state
                endGame(score)
                break
            } else {
                state = step.state
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("大鱼吃小鱼", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            SecondaryButton("返回") { onBack() }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$score 分", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.accentStrong)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("重开") { restart() }
                SecondaryButton("排行榜") { scope.launch { runCatching { leaderboard = Repo.leaderboard("fish") } } }
            }
        }
        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(over) {
                        detectDragGestures { change, _ ->
                            if (!over) {
                                state = state.copy(px = change.position.x, py = change.position.y)
                            }
                            change.consume()
                        }
                    }
            ) {
                drawRect(Color(0xFFB3E5FC))
                // 食物
                for (f in state.foods) drawCircle(Color(0xFF8BC34A), 3f, Offset(f.x, f.y))
                // 其它鱼
                for (f in state.fishes) {
                    val color = if (f.level > state.level) Color(0xFFE53935) else Color(0xFF5C6BC0)
                    drawFish(f.x, f.y, f.level, f.dir, color)
                }
                // 玩家鱼
                drawFish(state.px, state.py, state.level, 1, Color(0xFFFFB300))
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFish(
    x: Float, y: Float, lvl: Int, dir: Int, color: Color,
) {
    val r = 10f + lvl * 5f
    val cx = x
    val cy = y
    drawCircle(color, r, Offset(cx, cy))
    drawCircle(Color.White, r * 0.22f, Offset(cx + r * 0.35f * dir, cy - r * 0.25f))
}

private data class FishState(
    val px: Float,
    val py: Float,
    val level: Int,
    val foods: List<Food>,
    val fishes: List<Fish>,
    val eatenCount: Int = 0,
) {
    companion object {
        fun new(): FishState {
            val foods = List(8) { Food(Random.nextFloat() * 360f, Random.nextFloat() * 480f) }
            val fishes = List(6) {
                Fish(Random.nextFloat() * 360f, Random.nextFloat() * 480f, Random.nextInt(7), if (Random.nextBoolean()) 1 else -1)
            }
            return FishState(180f, 240f, 0, foods, fishes, 0)
        }
    }

    fun copy(px: Float = this.px, py: Float = this.py): FishState = FishState(px, py, level, foods, fishes, eatenCount)

    fun step(): StepResult {
        // 其它鱼游动
        val movedFishes = fishes.map { f ->
            var nx = f.x + f.dir * (0.4f + f.level * 0.15f)
            var nd = f.dir
            if (nx < 0f || nx > 360f) { nd *= -1; nx = f.x.coerceIn(0f, 360f) }
            f.copy(x = nx, dir = nd)
        }
        // 食物碰撞
        val r = 10f + level * 5f
        var gained = 0
        var eaten = false
        val remainFoods = foods.filter { f ->
            val d = sqrt((f.x - px) * (f.x - px) + (f.y - py) * (f.y - py))
            if (d < r + 4f) { gained += 1; eaten = true; false } else true
        }.toMutableList()
        // 补食物
        if (remainFoods.size < 12 && Random.nextFloat() < 0.1f) {
            remainFoods.add(Food(Random.nextFloat() * 360f, Random.nextFloat() * 480f))
        }
        // 鱼碰撞：吃「不大于自己」的鱼；每吃 3 条升一级（吃小鱼变大、能吃更大的鱼）
        var newLevel = level
        var newEaten = eatenCount
        var gameOver = false
        val remainFishes = movedFishes.filter { f ->
            val d = sqrt((f.x - px) * (f.x - px) + (f.y - py) * (f.y - py))
            if (d < r + (10f + f.level * 5f) - 4f) {
                when {
                    f.level <= level -> {
                        gained += (f.level + 1) * 5
                        newEaten++
                        if (newEaten % 3 == 0) newLevel = (newLevel + 1).coerceAtMost(10)
                        eaten = true; false
                    }
                    f.level > level -> { gameOver = true; true }
                    else -> true
                }
            } else true
        }.toMutableList()
        if (remainFishes.size < 6 && !gameOver) {
            remainFishes.add(Fish(Random.nextFloat() * 360f, Random.nextFloat() * 480f, Random.nextInt(7), if (Random.nextBoolean()) 1 else -1))
        }
        val newState = FishState(px, py, newLevel, remainFoods, remainFishes, newEaten)
        return StepResult(newState, gained, eaten, gameOver)
    }
}

private data class Food(val x: Float, val y: Float)
private data class Fish(val x: Float, val y: Float, val level: Int, val dir: Int)
private data class StepResult(val state: FishState, val gained: Int, val eaten: Boolean, val gameOver: Boolean)
