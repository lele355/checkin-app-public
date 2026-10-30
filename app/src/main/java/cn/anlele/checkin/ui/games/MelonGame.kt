package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.data.LeaderboardDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 合成大西瓜 —— 物理玩法（与网页 melon-core.js 同参数同规则，0.1 铁律）。
 * 规则依据：checkin-work/docs/research/2026-10-30-audit-solo-games.md §1.1
 *   圆形刚体：重力/滚动/堆叠/碰撞合成；等差计分；警戒线缓冲；加权掉落；下一个预览。
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
    var engine by remember { mutableStateOf(MelonEngine()) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(over, engine) {
        if (over) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { ts ->
                val dt = if (last == 0L) 1f / 60f else ((ts - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = ts
                engine.step(dt)
                score = engine.score
                if (engine.over && !over) {
                    over = true
                    val finalScore = min(engine.score, maxScore)
                    scope.launch {
                        runCatching { Repo.submitScore("melon", finalScore) }
                        runCatching { leaderboard = Repo.leaderboard("melon") }
                    }
                }
                frameTick++
            }
        }
    }

    fun restart() {
        engine = MelonEngine()
        score = 0
        over = false
        leaderboard = null
        frameTick = 0
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("合成大西瓜", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            SecondaryButton("返回") { onBack() }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$score 分", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.accentStrong)
                Text("下一个 ${MelonEngine.NAMES[engine.nextLevel - 1]}", fontSize = 12.sp, color = p.textSub)
            }
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
                    .pointerInput(engine, over) {
                        detectDragGestures(
                            onDragStart = { pos -> if (!over) engine.aim(pos.x / size.width * MelonEngine.W) },
                            onDrag = { change, _ -> if (!over) engine.aim(change.position.x / size.width * MelonEngine.W) },
                        )
                    }
                    .pointerInput(engine, over) {
                        detectTapGestures { pos ->
                            if (over) return@detectTapGestures
                            engine.aim(pos.x / size.width * MelonEngine.W)
                            engine.drop()
                        }
                    }
            ) {
                frameTick   // 帧订阅：每帧重绘
                val s = size.width / MelonEngine.W
                clipRect {
                    drawRect(Color(0xFFFFF8E1))
                    drawRect(Color(0xFFFFECB3), topLeft = Offset(0f, size.height / 2f), size = Size(size.width, size.height / 2f))
                    drawRect(Color(0xFFBCAAA4), style = Stroke(6f * s))

                    // 警戒线（越线计时闪红，M10）
                    val lineColor = if (engine.overTimer > 0f) Color(0xFFD32F2F) else Color(0x8CD32F2F)
                    var x = 6f * s
                    while (x < size.width - 6f * s) {
                        drawLine(lineColor, Offset(x, MelonEngine.WARN_Y * s), Offset(min(x + 8f * s, size.width), MelonEngine.WARN_Y * s), 3f * s)
                        x += 13f * s
                    }

                    // 已落水果
                    for (b in engine.bodies) drawFruit(b.x * s, b.y * s, b.level, b.r * s, b.angle)

                    // 落点预览 + 虚线导引（M3/M5）
                    if (!over) {
                        val cr = MelonEngine.RADII[engine.curLevel - 1] * s
                        drawLine(Color(0x2E000000), Offset(engine.aimX * s, MelonEngine.DROP_Y * s + cr), Offset(engine.aimX * s, size.height - 8f * s), 2f * s)
                        drawFruit(engine.aimX * s, MelonEngine.DROP_Y * s, engine.curLevel, cr, 0f, alpha = 0.85f)
                        // 下一个预览
                        val nr = MelonEngine.RADII[engine.nextLevel - 1] * s
                        drawFruit(size.width - 26f * s - nr, 22f * s, engine.nextLevel, nr, 0f, alpha = 0.9f)
                    }
                }
            }
            if (over) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("本局 $score 分", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("再来一局") { restart() }
                }
            }
        }

        leaderboard?.let { lb ->
            Spacer(Modifier.height(10.dp))
            Text("排行榜", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            lb.items.forEach {
                Text("${it.rank}. ${it.name} · ${it.bestScore}", fontSize = 13.sp, color = p.textSub)
            }
            Text("我的最高分 ${lb.mine.bestScore} · 第 ${lb.mine.rank} 名", fontSize = 13.sp, color = p.textSub)
        }
    }
}

/** 果面（M6：按级画特征，与网页 melon.js 同风格） */
private fun DrawScope.drawFruit(x: Float, y: Float, level: Int, r: Float, angle: Float, alpha: Float = 1f) {
    rotate(angle * 57.29578f, Offset(x, y)) {
        val base = MELON_COLORS[level - 1]
        drawCircle(base.copy(alpha = alpha), r, Offset(x, y))
        drawCircle(Color(0x40000000).copy(alpha = alpha), r, Offset(x, y), style = Stroke(1.5f * (r / 30f)))
        val dot = when (level) {
            1 -> Color(0x59FFFFFF)          // 葡萄颗粒
            2 -> Color(0x80780000)          // 樱桃核
            3 -> Color(0x80FFF0C8)          // 橘子皮孔
            5 -> Color(0xB31E3C14)          // 猕猴桃籽
            8 -> Color(0x8CB41414)          // 菠萝网纹（点代）
            9 -> Color(0xCC3C2819)          // 椰子眼
            10 -> Color(0xD9141414)         // 半瓜黑籽
            else -> null
        }
        if (dot != null) {
            val n = if (level == 2 || level == 9) 3 else 8
            for (i in 0 until n) {
                val a = i * 6.2832f / n + level
                drawCircle(dot.copy(alpha = alpha), r * 0.12f, Offset(x + cos(a) * r * 0.5f, y + sin(a) * r * 0.5f))
            }
        }
        if (level == 10) {   // 半个西瓜：红瓤
            val path = Path().apply {
                addArc(Rect(x - r * 0.95f, y - r * 0.7f, x + r * 0.95f, y + r * 1.2f), 180f, 180f)
            }
            drawPath(path, Color(0xFFEF5350).copy(alpha = alpha))
        }
        if (level == 11) {   // 西瓜条纹
            for (k in -2..2) {
                drawLine(
                    Color(0xFF81C784).copy(alpha = alpha),
                    Offset(x + k * r * 0.38f, y - r * 0.95f),
                    Offset(x + k * r * 0.38f, y + r * 0.95f),
                    r * 0.14f,
                )
            }
        }
        if (level == 8) {    // 菠萝顶冠
            val path = Path().apply {
                moveTo(x - r * 0.45f, y - r * 0.85f)
                lineTo(x, y - r * 1.35f)
                lineTo(x + r * 0.45f, y - r * 0.85f)
                close()
            }
            drawPath(path, Color(0xFF2E7D32).copy(alpha = alpha))
        }
    }
}

/**
 * 合成大西瓜物理引擎 —— 与网页 melon-core.js 同参数同规则（改一处必须同步另一处）。
 * 圆刚体 + 冲量法碰撞（8 次松弛迭代）；同级接触即合成；等差计分；警戒线缓冲判负。
 */
private class MelonEngine(val width: Float = W, val height: Float = H, seed: Long = System.nanoTime()) {
    companion object {
        const val W = 360f
        const val H = 480f
        val RADII = floatArrayOf(12f, 15f, 18f, 22f, 26f, 31f, 36f, 42f, 49f, 58f, 68f)
        val NAMES = listOf("葡萄", "樱桃", "橘子", "柠檬", "猕猴桃", "番茄", "桃子", "菠萝", "椰子", "半个西瓜", "西瓜")
        const val GRAVITY = 1600f
        const val RESTITUTION = 0.22f
        const val FRICTION = 0.10f
        const val ITER = 8
        const val DROP_Y = 46f
        const val WARN_Y = 96f
        const val OVER_GRACE = 0.8f
        const val MERGE_LOCK = 8
        const val DROP_COOLDOWN = 0.35f
        val WEIGHTS = intArrayOf(2, 3, 4, 1, 2)   // 橘子最常见、柠檬最少（M8）
    }

    class Body(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val r: Float, val level: Int,
        var angle: Float = 0f, var av: Float = 0f,
        var lock: Int = 0, var age: Float = 0f, var dead: Boolean = false,
    )

    private val rng = java.util.Random(seed)
    val bodies = mutableListOf<Body>()
    var score = 0; private set
    var over = false; private set
    var overTimer = 0f; private set
    var curLevel = rollLevel(); private set
    var nextLevel = rollLevel(); private set
    var aimX = width / 2; private set
    private var cool = 0f

    private fun rollLevel(): Int {
        val total = WEIGHTS.sum()
        var r = rng.nextInt(total)
        for (i in WEIGHTS.indices) { r -= WEIGHTS[i]; if (r < 0) return i + 1 }
        return 5
    }

    private fun addBody(level: Int, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, lock: Int = 0): Body {
        val b = Body(x, y, vx, vy, RADII[level - 1], level, lock = lock)
        bodies.add(b)
        return b
    }

    /** 两个同级相撞 → 合成高一级（源级 k 得 k 分；合出西瓜 +100）；两个西瓜相撞 → 爆炸 +100（M9） */
    private fun tryMerge(a: Body, b: Body) {
        if (a.dead || b.dead || a.level != b.level || a.lock > 0 || b.lock > 0) return
        if (a.level >= 11) {
            a.dead = true; b.dead = true; score += 100
            return
        }
        val lv = a.level
        val mx = (a.x + b.x) / 2f
        val my = (a.y + b.y) / 2f
        val mvx = (a.vx + b.vx) / 2f * 0.9f
        val mvy = (a.vy + b.vy) / 2f * 0.9f
        a.dead = true; b.dead = true
        score += lv
        val nl = lv + 1
        if (nl == 11) score += 100
        addBody(nl, mx, my, mvx, mvy, MERGE_LOCK)
    }

    private fun pairResolve(a: Body, b: Body) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist2 = dx * dx + dy * dy
        val rs = a.r + b.r
        if (dist2 >= rs * rs || dist2 == 0f) return
        val dist = sqrt(dist2)
        val nx = dx / dist
        val ny = dy / dist
        val overlap = rs - dist

        // 同级接触且未锁定即合成；不合成的接触仍走位置分离（防互穿沉底，与 melon-core.js 同步）
        if (a.level == b.level && a.lock <= 0 && b.lock <= 0) { tryMerge(a, b); return }

        val ma = a.r * a.r
        val mb = b.r * b.r
        val tm = ma + mb
        a.x -= nx * overlap * (mb / tm); a.y -= ny * overlap * (mb / tm)
        b.x += nx * overlap * (ma / tm); b.y += ny * overlap * (ma / tm)

        val rvx = b.vx - a.vx
        val rvy = b.vy - a.vy
        val vn = rvx * nx + rvy * ny
        if (vn < 0) {
            val j = -(1 + RESTITUTION) * vn / (1 / ma + 1 / mb)
            a.vx -= j * nx / ma; a.vy -= j * ny / ma
            b.vx += j * nx / mb; b.vy += j * ny / mb
        }
        val tx = -ny
        val ty = nx
        val vt = rvx * tx + rvy * ty
        val jt = -vt * FRICTION / (1 / ma + 1 / mb)
        a.vx -= jt * tx / ma; a.vy -= jt * ty / ma
        b.vx += jt * tx / mb; b.vy += jt * ty / mb
        a.av += vt / a.r * 0.02f; b.av += vt / b.r * 0.02f
    }

    private fun wallResolve(b: Body) {
        if (b.x - b.r < 0) { b.x = b.r; if (b.vx < 0) b.vx = -b.vx * RESTITUTION; b.av += b.vy * 0.002f }
        if (b.x + b.r > width) { b.x = width - b.r; if (b.vx > 0) b.vx = -b.vx * RESTITUTION; b.av -= b.vy * 0.002f }
        if (b.y + b.r > height) {
            b.y = height - b.r
            if (b.vy > 0) b.vy = -b.vy * RESTITUTION
            b.vx *= 0.985f
            b.av *= 0.985f
        }
    }

    fun step(dtIn: Float) {
        if (over) return
        val dt = min(dtIn, 0.05f)
        val sub = 1f / 60f
        val steps = maxOf(1, (dt / sub).toInt())
        repeat(steps) {
            for (b in bodies) {
                if (b.dead) continue
                b.vy += GRAVITY * sub
                b.x += b.vx * sub; b.y += b.vy * sub
                b.angle += b.av * sub
                b.age += sub
                if (b.lock > 0) b.lock--
            }
            repeat(ITER) {
                for (b in bodies) if (!b.dead) wallResolve(b)
                for (i in bodies.indices) {
                    if (bodies[i].dead) continue
                    for (j in i + 1 until bodies.size) {
                        if (bodies[j].dead) continue
                        pairResolve(bodies[i], bodies[j])
                    }
                }
            }
            val it = bodies.iterator()
            while (it.hasNext()) {
                val b = it.next()
                b.vx *= 0.999f; b.vy *= 0.999f
                if (b.dead) it.remove()
            }
            if (cool > 0) cool -= sub

            // 警戒线：质心越线且静止 > 缓冲 → 判负（M10）
            var resting = false
            for (b in bodies) {
                if (b.age > 0.4f && b.y < WARN_Y && abs(b.vx) + abs(b.vy) < 30f) { resting = true; break }
            }
            overTimer = if (resting) overTimer + sub else maxOf(0f, overTimer - sub * 2)
            if (overTimer >= OVER_GRACE) over = true
        }
    }

    fun aim(x: Float) {
        val r = RADII[curLevel - 1]
        aimX = x.coerceIn(r, width - r)
    }

    fun drop(): Boolean {
        if (over || cool > 0) return false
        val r = RADII[curLevel - 1]
        addBody(curLevel, aimX.coerceIn(r, width - r), DROP_Y)
        curLevel = nextLevel
        nextLevel = rollLevel()
        cool = DROP_COOLDOWN
        return true
    }
}

private val MELON_COLORS = listOf(
    Color(0xFF9C27B0), Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835),
    Color(0xFF7CB342), Color(0xFFEF5350), Color(0xFFF48FB1), Color(0xFFF9A825),
    Color(0xFF8D6E63), Color(0xFF43A047), Color(0xFF2E7D32),
)
