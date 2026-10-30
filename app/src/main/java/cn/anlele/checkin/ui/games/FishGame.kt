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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 大鱼吃小鱼 —— 单机 · 吃小鱼变大 · 躲大鱼（与网页 fish.js 同规则，0.1 铁律）。
 * 规则依据：checkin-work/docs/research/2026-10-30-audit-solo-games.md §2.1
 *   3 条命 + 被吃后 2 秒无敌（F2）；鱼群两侧穿屏（F3）；平滑跟随 + 转身 + 冲刺（F4）；
 *   多鱼种轮廓（F5）；海底背景（F6）；档位计分 + 连击倍率（F7）；同级可吃（F8）；
 *   生命/成长条/图例（F9）；珍珠加分（F10 先行）。
 * 纯前端算分，结束时上报最高分（服务端幂等 max）。
 */
@Composable
fun FishGame(
    maxScore: Int,
    onBack: () -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()

    var engine by remember { mutableStateOf(FishEngine()) }
    var score by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var leaderboard by remember { mutableStateOf<LeaderboardDto?>(null) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(over, engine) {
        if (over) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { ts ->
                val dt = if (last == 0L) 1f / 60f else ((ts - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = ts
                if (!engine.over) engine.step(dt)
                score = engine.score
                if (engine.over && !over) {
                    over = true
                    val finalScore = min(engine.score, maxScore)
                    scope.launch {
                        runCatching { Repo.submitScore("fish", finalScore) }
                        runCatching { leaderboard = Repo.leaderboard("fish") }
                    }
                }
                frameTick++
            }
        }
    }

    fun restart() {
        engine = FishEngine()
        score = 0
        over = false
        leaderboard = null
        frameTick = 0
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("大鱼吃小鱼", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            SecondaryButton("返回") { onBack() }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$score 分", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.accentStrong)
                Text("体型 ${engine.level + 1}/7", fontSize = 12.sp, color = p.textSub)
            }
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
                    .pointerInput(engine, over) {
                        detectDragGestures { change, _ ->
                            if (!over) engine.setTarget(change.position.x / size.width * FishEngine.W, change.position.y / size.height * FishEngine.H)
                            change.consume()
                        }
                    }
                    .pointerInput(engine, over) {
                        detectTapGestures { pos ->
                            if (!over) {
                                engine.setTarget(pos.x / size.width * FishEngine.W, pos.y / size.height * FishEngine.H)
                                engine.dash()
                            }
                        }
                    }
            ) {
                frameTick   // 帧订阅：每帧重绘
                val sx = size.width / FishEngine.W
                val sy = size.height / FishEngine.H
                clipRect {
                    // 海底渐变（F6）
                    drawRect(Color(0xFF81D4FA))
                    drawRect(Color(0xFF29B6F6), topLeft = Offset(0f, size.height * 0.3f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.25f))
                    drawRect(Color(0xFF01579B), topLeft = Offset(0f, size.height * 0.55f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.45f))
                    // 光柱（F6）
                    for (i in 0..2) {
                        val x0 = size.width * (0.2f + i * 0.3f)
                        val path = Path().apply {
                            moveTo(x0 - 18f * sx, 0f); lineTo(x0 + 18f * sx, 0f)
                            lineTo(x0 + 60f * sx, size.height); lineTo(x0 - 60f * sx, size.height)
                            close()
                        }
                        drawPath(path, Color(0x14FFFFFF))
                    }
                    // 海草（F6）
                    for (w in engine.seaweed) {
                        val sway = sin(engine.time * 1.2f + w.phase) * 10f
                        val path = Path().apply {
                            moveTo(w.x * sx, size.height)
                            quadraticBezierTo(w.x * sx + sway * sx, size.height - w.h * 0.6f * sy, w.x * sx + sway * 1.6f * sx, size.height - w.h * sy)
                        }
                        drawPath(path, Color(0xBF1B5E20), style = Stroke(5f * sx))
                    }
                    // 沙底
                    drawRect(Color(0xFFC8B078), topLeft = Offset(0f, size.height - 14f * sy), size = androidx.compose.ui.geometry.Size(size.width, 14f * sy))
                    // 气泡（F6）
                    for (b in engine.bubbles) drawCircle(Color(0x80FFFFFF), b.r * sx, Offset(b.x * sx, b.y * sy), style = Stroke(1f * sx))

                    // 珍珠（F10）
                    for (po in engine.pearls) {
                        drawCircle(Color(0xFFF8BBD0), po.r * sx, Offset(po.x * sx, po.y * sy))
                        drawCircle(Color(0xCCFFFFFF), (3f + sin(po.t) * 1.2f) * 0.45f * sx, Offset((po.x - 1.2f) * sx, (po.y - 1.2f) * sy))
                    }

                    // 其它鱼（危险红 / 可吃蓝绿，F5 多轮廓）
                    for (f in engine.fishes) {
                        val danger = f.level > engine.level
                        val color = if (danger) Color(0xFFEF5350) else if (f.kind == 3) Color(0xFF80DEEA) else Color(0xFF4FC3F7)
                        drawFishShape(f.x * sx, f.y * sy, f.level, f.dir, color, f.kind, sx)
                    }

                    // 玩家鱼（无敌闪烁，F2）
                    val flashing = engine.invuln > 0f && (engine.time * 8).toInt() % 2 == 0
                    if (!flashing) {
                        drawFishShape(engine.px * sx, engine.py * sy, engine.level, engine.dir, Color(0xFFFFB300), 4, sx)
                    }

                    // HUD（F9）：生命 / 成长条 / 图例 / 连击
                    drawRect(Color(0x59000000), topLeft = Offset(8f * sx, 8f * sy), size = androidx.compose.ui.geometry.Size(200f * sx, 26f * sy))
                    for (i in 0 until 3) {
                        drawCircle(if (i < engine.lives) Color(0xFFE53935) else Color(0x40E53935), 6f * sx, Offset((20f + i * 20f) * sx, 21f * sy))
                    }
                    drawRect(Color(0x59FFFFFF), topLeft = Offset(82f * sx, 16f * sy), size = androidx.compose.ui.geometry.Size(118f * sx, 10f * sy))
                    val growFrac = min(1f, engine.growth.toFloat() / engine.growthNeed())
                    drawRect(Color(0xFFFFD54F), topLeft = Offset(82f * sx, 16f * sy), size = androidx.compose.ui.geometry.Size(118f * sx * growFrac, 10f * sy))
                    drawRect(Color(0x59000000), topLeft = Offset((size.width - 168f * sx), 8f * sy), size = androidx.compose.ui.geometry.Size(160f * sx, 26f * sy))
                    drawCircle(Color(0xFFC8E6C9), 5f * sx, Offset(size.width - 152f * sx, 21f * sy))
                    drawCircle(Color(0xFFFFCDD2), 5f * sx, Offset(size.width - 22f * sx, 21f * sy))
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

/** 多鱼种轮廓（F5）：0 椭圆身 / 1 尖嘴梭鱼 / 2 鳐形 / 3 水母 / 4 鲨形 */
private fun DrawScope.drawFishShape(x: Float, y: Float, level: Int, facing: Int, color: Color, kind: Int, scale: Float) {
    val r = (10f + level * 5f) * scale
    val dir = if (facing >= 0) 1f else -1f
    when (kind) {
        3 -> { // 水母：伞盖 + 触手
            val dome = Path().apply {
                addArc(androidx.compose.ui.geometry.Rect(x - r, y - r, x + r, y + r), 180f, 180f)
                quadraticBezierTo(x + r * 0.7f * dir, y + r * 0.55f, x, y + r * 0.5f)
                quadraticBezierTo(x - r * 0.7f * dir, y + r * 0.55f, x - r, y)
            }
            drawPath(dome, color)
            for (i in -2..2) {
                val path = Path().apply {
                    moveTo(x + i * r * 0.3f, y + r * 0.45f)
                    quadraticBezierTo(x + i * r * 0.4f, y + r * 0.9f, x + i * r * 0.25f, y + r * 1.3f)
                }
                drawPath(path, color, style = Stroke(2f * scale))
            }
        }
        2 -> { // 鳐：菱形身 + 长尾
            val body = Path().apply {
                moveTo(x + r * 1.1f * dir, y)
                quadraticBezierTo(x, y - r * 0.95f, x - r * 1.05f * dir, y)
                quadraticBezierTo(x, y + r * 0.95f, x + r * 1.1f * dir, y)
                close()
            }
            drawPath(body, color)
            drawLine(color, Offset(x - r * dir, y), Offset(x - r * 1.8f * dir, y + r * 0.12f), 3f * scale)
        }
        else -> { // 鱼身：椭圆 + 尾 +（鲨背鳍/梭鱼尖嘴）
            drawOval(
                color,
                topLeft = Offset(x - r * if (kind == 1) 1.25f else 1.05f, y - r * if (kind == 4) 0.72f else 0.82f),
                size = androidx.compose.ui.geometry.Size(
                    r * 2f * if (kind == 1) 1.25f else 1.05f,
                    r * 2f * if (kind == 4) 0.72f else 0.82f,
                ),
            )
            if (kind == 4) {
                val fin = Path().apply {
                    moveTo(x - r * 0.1f, y - r * 0.68f)
                    lineTo(x + r * 0.35f, y - r * 1.35f)
                    lineTo(x + r * 0.55f, y - r * 0.6f)
                    close()
                }
                drawPath(fin, color)
            }
            val tail = Path().apply {
                moveTo(x - r * 0.95f * dir, y)
                lineTo(x - r * 1.65f * dir, y - r * 0.5f)
                lineTo(x - r * 1.65f * dir, y + r * 0.5f)
                close()
            }
            drawPath(tail, color)
            if (kind == 1) {
                val mouth = Path().apply {
                    moveTo(x + r * dir, y)
                    lineTo(x + r * 1.45f * dir, y - r * 0.12f)
                    lineTo(x + r * 1.45f * dir, y + r * 0.12f)
                    close()
                }
                drawPath(mouth, color)
            }
        }
    }
    // 眼睛
    drawCircle(Color.White, r * 0.2f, Offset(x + r * 0.38f * dir, y - r * 0.22f))
    drawCircle(Color.Black, r * 0.09f, Offset(x + r * 0.43f * dir, y - r * 0.22f))
}

/** 大鱼吃小鱼引擎 —— 与网页 fish.js 同规则（改一处必须同步另一处） */
private class FishEngine(val width: Float = W, val height: Float = H) {
    companion object {
        const val W = 360f
        const val H = 480f
        const val MAX_LEVEL = 6
        val VALUES = intArrayOf(50, 50, 150, 400, 900, 1800, 1800)   // 档位分（F7）
        val LEVEL_WEIGHTS = intArrayOf(3, 3, 2, 2, 1, 1, 1)          // 小鱼多大鱼少
    }

    class Fish(
        var x: Float, var y: Float, var baseY: Float, var phase: Float,
        val level: Int, val dir: Int, val speed: Float, val kind: Int,
    )

    class Pearl(var x: Float, var y: Float, val r: Float = 5f, var t: Float = 0f)
    class Bubble(var x: Float, var y: Float, val r: Float, val s: Float)
    class Weed(val x: Float, val h: Float, val phase: Float)

    var level = 0; private set
    var growth = 0; private set
    var lives = 3; private set
    var px = W / 2; private set
    var py = H / 2; private set
    var dir = 1; private set
    var score = 0; private set
    var over = false; private set
    var invuln = 0f; private set
    var chain = 0; private set
    var time = 0f; private set
    private var tx = W / 2
    private var ty = H / 2
    private var chainTimer = 0f
    private var dashCd = 0f

    val fishes = mutableListOf<Fish>()
    val pearls = mutableListOf<Pearl>()
    val bubbles = mutableListOf<Bubble>()
    val seaweed = mutableListOf<Weed>()

    init {
        repeat(4) { spawnSchool() }
        repeat(3) { spawnPearl() }
        repeat(12) { bubbles.add(Bubble((0..360).random().toFloat(), (0..480).random().toFloat(), 1f + Math.random().toFloat() * 3f, 0.3f + Math.random().toFloat() * 0.7f)) }
        repeat(6) { seaweed.add(Weed(30f + it * (W - 60f) / 5f, 40f + Math.random().toFloat() * 45f, Math.random().toFloat() * 6.28f)) }
    }

    fun radius(lvl: Int) = 10f + lvl * 5f
    fun growthNeed() = 4 + level * 2
    private fun fishValue(l: Int) = VALUES[min(l, 6)]
    fun comboMult(): Double = if (chain >= 6) 2.0 else if (chain >= 3) 1.5 else 1.0

    private fun rollLevel(): Int {
        val total = LEVEL_WEIGHTS.sum()
        var r = (0 until total).random()
        for (i in LEVEL_WEIGHTS.indices) { r -= LEVEL_WEIGHTS[i]; if (r < 0) return i }
        return 0
    }

    private fun spawnFish(side: Int) {
        val lv = rollLevel()
        val fromLeft = if (side == -1) Math.random() < 0.5 else side == 0
        val f = Fish(
            x = if (fromLeft) -radius(lv) - 10f else width + radius(lv) + 10f,
            y = 60f + Math.random().toFloat() * (height - 160f),
            baseY = 0f, phase = Math.random().toFloat() * 6.28f,
            level = lv, dir = if (fromLeft) 1 else -1,
            speed = 0.7f + lv * 0.12f + Math.random().toFloat() * 0.3f,
            kind = lv % 5,
        )
        f.baseY = f.y
        if (abs(f.x - px) < 130f && abs(f.y - py) < 90f) f.y = if (f.y < py) 50f else height - 90f
        fishes.add(f)
    }

    private fun spawnSchool() {
        val n = 2 + (0..1).random()
        val side = if (Math.random() < 0.5) 0 else 1
        repeat(n) { spawnFish(side) }
    }

    private fun spawnPearl() {
        pearls.add(Pearl(30f + Math.random().toFloat() * (width - 60f), 60f + Math.random().toFloat() * (height - 140f), t = Math.random().toFloat() * 6.28f))
    }

    fun setTarget(x: Float, y: Float) {
        tx = x.coerceIn(0f, width)
        ty = y.coerceIn(0f, height)
    }

    /** 冲刺：朝目标方向瞬时加推（冷却 1.2s，F4） */
    fun dash() {
        if (dashCd > 0f) return
        val dx = tx - px
        val dy = ty - py
        val d = hypot(dx, dy).coerceAtLeast(1f)
        tx = px + dx / d * 130f
        ty = py + dy / d * 130f
        dashCd = 1.2f
    }

    fun step(dt: Float) {
        time += dt
        // 平滑跟随 + 转身（F4）
        val k = min(1f, dt * 8f)
        val nx = px + (tx - px) * k
        val ny = py + (ty - py) * k
        if (abs(nx - px) > 0.3f) dir = if (nx > px) 1 else -1
        px = nx; py = ny

        if (invuln > 0f) invuln -= dt
        if (dashCd > 0f) dashCd -= dt
        if (chainTimer > 0f) { chainTimer -= dt; if (chainTimer <= 0f) chain = 0 }

        // 鱼群穿屏（F3）
        val it = fishes.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.x += f.dir * f.speed * 60f * dt
            f.phase += dt * 1.5f
            f.y = f.baseY + sin(f.phase) * 12f
            if ((f.dir > 0 && f.x > width + 80f) || (f.dir < 0 && f.x < -80f)) {
                it.remove()
                spawnFish(if (f.dir > 0) 1 else 0)
            }
        }
        if (fishes.size < 10 && Math.random() < dt * 0.6) spawnSchool()
        if (pearls.size < 4 && Math.random() < dt * 0.15) spawnPearl()

        for (b in bubbles) {
            b.y -= b.s * 40f * dt
            if (b.y < -6f) { b.y = height + 6f; b.x = Math.random().toFloat() * width }
        }

        // 吃珍珠（F10）
        val pr = radius(level)
        val pit = pearls.iterator()
        while (pit.hasNext()) {
            val po = pit.next()
            po.t += dt * 3f
            if (hypot(po.x - px, po.y - py) < pr + po.r + 2f) {
                pit.remove()
                score += 10
            }
        }

        // 吃鱼 / 被吃（F2/F7/F8）
        val fit = fishes.iterator()
        while (fit.hasNext()) {
            val fi = fit.next()
            val rr = radius(fi.level) + pr - 4f
            if (hypot(fi.x - px, fi.y - py) >= rr) continue
            if (fi.level <= level) {
                chain++
                chainTimer = 3f
                score += (fishValue(fi.level) * comboMult()).toInt()
                growth += 1 + fi.level
                if (level < MAX_LEVEL && growth >= growthNeed()) { growth = 0; level++ }
                fit.remove()
                spawnFish(-1)
            } else if (invuln <= 0f) {
                lives--
                if (lives <= 0) { over = true; return }
                invuln = 2f
                chain = 0
                px = width / 2; py = height / 2; tx = px; ty = py
            }
        }
    }
}
