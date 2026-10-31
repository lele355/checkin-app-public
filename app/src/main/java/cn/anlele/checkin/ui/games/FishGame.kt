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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 大鱼吃小鱼 —— 单机 · 吃小鱼变大 · 躲大鱼（与网页 fish.js 同规则，0.1 铁律）。
 * 规则依据：checkin-work/docs/research/2026-10-30-audit-solo-games.md §2.1
 *   3 条命 + 被吃后 2 秒无敌（F2）；鱼群两侧穿屏（F3）；平滑跟随 + 转身 + 冲刺（F4）；
 *   多鱼种轮廓（F5）；海底背景（F6）；档位计分 + 连击倍率（F7）；同级可吃（F8）；
 *   生命/成长条/图例（F9）；珍珠加分（F10 先行）。
 *   Batch D：F11 关卡制（随分数/时间提速加密度，图形提示不加文字）；F12 天敌危害
 *   （水母、鲨鱼碰触扣命 + 无敌帧，HUD 图形标示危险鱼）；F13 音效（吃鱼/受伤/冲刺/结束，
 *   GameSfx 合成音）；P3-3 教程 7 档命名与引擎同规。
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
    // 音效事件计数快照（F13）：帧循环观测增量 → GameSfx 播放
    val prevEv = remember { IntArray(4) }

    LaunchedEffect(over, engine) {
        if (over) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { ts ->
                val dt = if (last == 0L) 1f / 60f else ((ts - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = ts
                if (!engine.over) engine.step(dt)
                if (engine.evEat != prevEv[0]) { prevEv[0] = engine.evEat; GameSfx.play("eat") }
                if (engine.evHurt != prevEv[1]) { prevEv[1] = engine.evHurt; GameSfx.play("hurt") }
                if (engine.evDash != prevEv[2]) { prevEv[2] = engine.evDash; GameSfx.play("dash") }
                if (engine.evEnd != prevEv[3]) { prevEv[3] = engine.evEnd; GameSfx.play("end") }
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
        prevEv.fill(0)
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
                            quadraticTo(w.x * sx + sway * sx, size.height - w.h * 0.6f * sy, w.x * sx + sway * 1.6f * sx, size.height - w.h * sy)
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

                    // 其它鱼：普通鱼（可吃蓝绿 / 更大红）+ 天敌（水母/鲨恒红带刺，F12）
                    for (f in engine.fishes) {
                        val enemy = f.kind >= 3
                        val danger = enemy || f.level > engine.level
                        val color = if (danger) Color(0xFFEF5350) else Color(0xFF4FC3F7)
                        drawFishShape(f.x * sx, f.y * sy, f.level, f.dir, color, f.kind, sx, spiky = enemy)
                    }

                    // 玩家鱼（无敌闪烁，F2）
                    val flashing = engine.invuln > 0f && (engine.time * 8).toInt() % 2 == 0
                    if (!flashing) {
                        drawFishShape(engine.px * sx, engine.py * sy, engine.level, engine.dir, Color(0xFFFFB300), 4, sx)
                    }

                    // 升关冲击波（F11 图形提示）
                    if (engine.stageFlash > 0f) {
                        val a = engine.stageFlash / 1.2f
                        val ring = (10f + engine.level * 5f) * sx + 20f * sx + (1.2f - engine.stageFlash) * 60f * sx
                        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.6f * a), ring, Offset(engine.px * sx, engine.py * sy), style = Stroke(3f * sx))
                        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.6f * a), ring * 0.7f, Offset(engine.px * sx, engine.py * sy), style = Stroke(3f * sx))
                    }

                    // HUD（F9/F11/F12）：关卡格 / 生命 / 成长条 / 图例 —— 全图形，不加文字
                    // 关卡格（F11）：顶部一排菱形，升关点亮 + 光圈
                    for (i in 0 until FishEngine.STAGE_MAX) {
                        val cx = size.width / 2f - (FishEngine.STAGE_MAX - 1) * 7f * sx + i * 14f * sx
                        val cy = 5f * sy
                        val hs = if (engine.stageFlash > 0f && i < engine.stage) 5.2f * sx else 4f * sx
                        val pip = Path().apply {
                            moveTo(cx, cy - hs); lineTo(cx + hs, cy); lineTo(cx, cy + hs); lineTo(cx - hs, cy); close()
                        }
                        drawPath(pip, if (i < engine.stage) Color(0xFFFFD54F) else Color(0x47FFFFFF))
                        if (engine.stageFlash > 0f && i == engine.stage - 1) {
                            drawCircle(
                                Color(0xFFFFD54F).copy(alpha = 0.5f * engine.stageFlash / 1.2f),
                                hs + 3f * sx + 4f * sx * (1.2f - engine.stageFlash),
                                Offset(cx, cy),
                                style = Stroke(1.5f * sx),
                            )
                        }
                    }
                    // 生命（F9 图形心形）+ 成长条
                    drawRect(Color(0x59000000), topLeft = Offset(8f * sx, 8f * sy), size = androidx.compose.ui.geometry.Size(176f * sx, 26f * sy))
                    for (i in 0 until 3) {
                        drawHeartShape((22f + i * 20f) * sx, 21f * sy, 7f * sx, if (i < engine.lives) Color(0xFFE53935) else Color(0x40E53935))
                    }
                    drawRect(Color(0x59FFFFFF), topLeft = Offset(84f * sx, 16f * sy), size = androidx.compose.ui.geometry.Size(92f * sx, 10f * sy))
                    val growFrac = min(1f, engine.growth.toFloat() / engine.growthNeed())
                    drawRect(Color(0xFFFFD54F), topLeft = Offset(84f * sx, 16f * sy), size = androidx.compose.ui.geometry.Size(92f * sx * growFrac, 10f * sy))
                    // 图例（F12 图形标示）：绿小鱼=可吃 / 红刺鱼=危险
                    drawRect(Color(0x59000000), topLeft = Offset((size.width - 168f * sx), 8f * sy), size = androidx.compose.ui.geometry.Size(160f * sx, 26f * sy))
                    drawFishShape(size.width - 138f * sx, 21f * sy, 0, 1, Color(0xFFC8E6C9), 0, sx * 0.6f)
                    drawFishShape(size.width - 38f * sx, 21f * sy, 0, 1, Color(0xFFEF5350), 4, sx * 0.6f, spiky = true)
                    // 连击（F7）：与网页同款角标（0.1 双端一致）
                    if (engine.chain >= 3) {
                        drawRect(Color(0x59000000), topLeft = Offset(size.width / 2f - 36f * sx, 42f * sy), size = androidx.compose.ui.geometry.Size(72f * sx, 22f * sy))
                        drawIntoCanvas { c ->
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#FFEE58")
                                textSize = 15f * sy
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            c.nativeCanvas.drawText(
                                "连击 ×" + if (engine.chain >= 6) "2" else "1.5",
                                size.width / 2f, 58f * sy, paint,
                            )
                        }
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

/** 多鱼种轮廓（F5）：0 椭圆身 / 1 尖嘴梭鱼 / 2 鳐形 / 3 水母 / 4 鲨形；spiky=天敌尖刺标记（F12） */
private fun DrawScope.drawFishShape(x: Float, y: Float, level: Int, facing: Int, color: Color, kind: Int, scale: Float, spiky: Boolean = false) {
    val r = (10f + level * 5f) * scale
    val dir = if (facing >= 0) 1f else -1f
    when (kind) {
        3 -> { // 水母：伞盖 + 触手
            val dome = Path().apply {
                addArc(androidx.compose.ui.geometry.Rect(x - r, y - r, x + r, y + r), 180f, 180f)
                quadraticTo(x + r * 0.7f * dir, y + r * 0.55f, x, y + r * 0.5f)
                quadraticTo(x - r * 0.7f * dir, y + r * 0.55f, x - r, y)
            }
            drawPath(dome, color)
            for (i in -2..2) {
                val path = Path().apply {
                    moveTo(x + i * r * 0.3f, y + r * 0.45f)
                    quadraticTo(x + i * r * 0.4f, y + r * 0.9f, x + i * r * 0.25f, y + r * 1.3f)
                }
                drawPath(path, color, style = Stroke(2f * scale))
            }
        }
        2 -> { // 鳐：菱形身 + 长尾
            val body = Path().apply {
                moveTo(x + r * 1.1f * dir, y)
                quadraticTo(x, y - r * 0.95f, x - r * 1.05f * dir, y)
                quadraticTo(x, y + r * 0.95f, x + r * 1.1f * dir, y)
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
    // 天敌尖刺标记（F12）：红鱼 + 刺，HUD/场上同一套图形语言
    if (spiky) {
        for (sp in -2..2) {
            val ang = (-Math.PI / 2 + sp * 0.5).toFloat()
            val bx = x + cos(ang) * r * 1.05f
            val by = y + sin(ang) * r * 1.05f
            val tX = x + cos(ang) * r * 1.35f
            val tY = y + sin(ang) * r * 1.35f
            val spike = Path().apply {
                moveTo(bx - r * 0.12f, by)
                lineTo(tX, tY)
                lineTo(bx + r * 0.12f, by)
                close()
            }
            drawPath(spike, color)
        }
    }
}

/** 心形生命图钉（F9 图形，不加文字）。 */
private fun DrawScope.drawHeartShape(cx: Float, cy: Float, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(cx, cy + s * 0.75f)
        cubicTo(cx - s * 1.4f, cy - s * 0.25f, cx - s * 0.55f, cy - s * 1.05f, cx, cy - s * 0.35f)
        cubicTo(cx + s * 0.55f, cy - s * 1.05f, cx + s * 1.4f, cy - s * 0.25f, cx, cy + s * 0.75f)
        close()
    }
    drawPath(path, color)
}

/** 大鱼吃小鱼引擎 —— 与网页 fish.js 同规则（改一处必须同步另一处）。internal 供 JVM 压力测试。 */
internal class FishEngine(val width: Float = W, val height: Float = H) {
    companion object {
        const val W = 360f
        const val H = 480f
        const val MAX_LEVEL = 6
        val VALUES = intArrayOf(50, 150, 400, 900, 1800, 3600, 7200)  // 普通鱼按体型档计分（P3-3：7 档命名）
        val LEVEL_WEIGHTS = intArrayOf(3, 3, 2, 2, 1, 1, 1)          // 小鱼多大鱼少
        const val STAGE_MAX = 6                                       // 关卡上限（F11）
        const val STAGE_SCORE_STEP = 1000                             // 每 1000 分 +1 关（F11）
        const val STAGE_TIME_STEP = 120f                              // 每 120 秒 +1 关（F11）
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
    var stage = 1; private set                       // 关卡（F11）
    var stageFlash = 0f; private set                 // 升关图形提示计时（F11）
    var evEat = 0; private set                       // 音效事件计数（F13）：UI 观测增量播放
    var evHurt = 0; private set
    var evDash = 0; private set
    var evEnd = 0; private set
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

    /** 关卡（F11）：随分数与时间推进，封顶 STAGE_MAX。与网页 fish.js stageOf 同式。 */
    fun stageOf(sc: Int, t: Float): Int =
        1 + min(STAGE_MAX - 1, sc / STAGE_SCORE_STEP + (t / STAGE_TIME_STEP).toInt())

    private fun rollLevel(): Int {
        val total = LEVEL_WEIGHTS.sum()
        var r = (0 until total).random()
        for (i in LEVEL_WEIGHTS.indices) { r -= LEVEL_WEIGHTS[i]; if (r < 0) return i }
        return 0
    }

    private fun spawnFish(side: Int) {
        val lv = rollLevel()
        val kind = if (lv >= 3 && Math.random() < 0.5) {
            // 天敌（F12）：水母 / 鲨 —— 恒危险、不可吃；0~2 档永远是普通鱼（新手期只按体型判定）
            if (Math.random() < 0.5) 3 else 4
        } else {
            (0..2).random()                          // 普通鱼三种轮廓（F5）
        }
        val fromLeft = if (side == -1) Math.random() < 0.5 else side == 0
        val f = Fish(
            x = if (fromLeft) -radius(lv) - 10f else width + radius(lv) + 10f,
            y = 60f + Math.random().toFloat() * (height - 160f),
            baseY = 0f, phase = Math.random().toFloat() * 6.28f,
            level = lv, dir = if (fromLeft) 1 else -1,
            speed = 0.7f + lv * 0.12f + Math.random().toFloat() * 0.3f,
            kind = kind,                             // 0 椭圆 / 1 梭鱼 / 2 鳐 / 3 水母[天敌] / 4 鲨[天敌]
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
        evDash++
    }

    fun step(dt: Float) {
        if (over) return
        time += dt
        // F11 关卡推进：随分数与时间提速加密度；升关用图形冲击波提示（不加文字）
        val ns = stageOf(score, time)
        if (ns > stage) { stage = ns; stageFlash = 1.2f }
        if (stageFlash > 0f) stageFlash -= dt
        val speedMult = 1f + 0.12f * (stage - 1)
        // 平滑跟随 + 转身（F4）
        val k = min(1f, dt * 8f)
        val nx = px + (tx - px) * k
        val ny = py + (ty - py) * k
        if (abs(nx - px) > 0.3f) dir = if (nx > px) 1 else -1
        px = nx; py = ny

        if (invuln > 0f) invuln -= dt
        if (dashCd > 0f) dashCd -= dt
        if (chainTimer > 0f) { chainTimer -= dt; if (chainTimer <= 0f) chain = 0 }

        // 鱼群穿屏（F3）。注意：迭代中不能直接 spawnFish（List.add 触发
        // ConcurrentModificationException —— 真机 5 秒闪退根因，首条鱼出屏即炸）。
        val respawns = mutableListOf<Int>()
        val eit = fishes.iterator()
        while (eit.hasNext()) {
            val f = eit.next()
            f.x += f.dir * f.speed * speedMult * 60f * dt
            f.phase += dt * 1.5f
            f.y = f.baseY + sin(f.phase) * 12f
            if ((f.dir > 0 && f.x > width + 80f) || (f.dir < 0 && f.x < -80f)) {
                eit.remove()
                respawns.add(if (f.dir > 0) 1 else 0)   // 出屏侧 → 从对侧补入
            }
        }
        for (side in respawns) spawnFish(side)
        // 鱼群密度与刷新随关卡提升（F11）
        if (fishes.size < 10 + 2 * (stage - 1) && Math.random() < dt * (0.6 + 0.15 * (stage - 1))) spawnSchool()
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
                evEat++
            }
        }

        // 吃鱼 / 被吃（F2/F7/F8/F12）。同样：迭代中只 remove，补鱼放到循环外。
        var eaten = 0
        val fit = fishes.iterator()
        while (fit.hasNext()) {
            val fi = fit.next()
            val rr = radius(fi.level) + pr - 4f
            if (hypot(fi.x - px, fi.y - py) >= rr) continue
            if (fi.kind < 3 && fi.level <= level) {          // 普通鱼：不大于自己可吃（同级可吃）
                chain++
                chainTimer = 3f
                score += (fishValue(fi.level) * comboMult()).roundToInt()   // 防御性统一 round 口径（与网页 Math.round 同规，BUG-2 同款）
                growth += 1 + fi.level
                if (level < MAX_LEVEL && growth >= growthNeed()) { growth = 0; level++ }
                fit.remove()
                eaten++
                evEat++
            } else if (invuln <= 0f) {                        // 更大的鱼或天敌（水母/鲨）碰触
                lives--
                evHurt++
                if (lives <= 0) { over = true; evEnd++; return }
                invuln = 2f
                chain = 0
                px = width / 2; py = height / 2; tx = px; ty = py
            }
        }
        repeat(eaten) { spawnFish(-1) }
    }
}
