package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.data.LeaderboardDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.LeaderboardSheet
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * 扫雷 —— 规则内核与网页 `solo/minesweeper-core.js` 同参同规（0.1 铁律）：
 * 三档难度、首点及其 8 邻格必安全、翻开空白递归展开、插旗、双键展开、计分 = 底分 − 用时 × 系数（保底 100）。
 * 纯前端算分，清场时上报最高分（服务端幂等 max）。
 */
object MineCore {
    data class Level(
        val key: Int, val name: String,
        val rows: Int, val cols: Int, val mines: Int,
        val base: Int, val coef: Int,
    )

    /** 与 JS LEVELS 完全相同 */
    val LEVELS = listOf(
        Level(1, "初级", 9, 9, 10, 3000, 4),
        Level(2, "中级", 16, 16, 40, 6000, 6),
        Level(3, "高级", 16, 30, 99, 12000, 10),
    )

    fun levelOf(key: Int): Level = LEVELS.firstOrNull { it.key == key } ?: LEVELS[0]

    /** 单局得分：难度底分 − 用时秒数 × 难度系数，保底 100（越快分越高） */
    fun scoreOf(levelKey: Int, seconds: Int): Int {
        val lv = levelOf(levelKey)
        return (lv.base - maxOf(0, seconds) * lv.coef).coerceIn(100, 9_999_999)
    }

    class Cell {
        var mine = false
        var adj = 0
        var open = false
        var flag = false
    }

    class Game(val level: Int, val rows: Int, val cols: Int, val mines: Int) {
        val cells: List<Cell> = List(rows * cols) { Cell() }
        var started = false; private set
        var over = false; private set
        var won = false; private set
        var boom = -1; private set
        var opened = 0; private set
        var flags = 0; private set
        private var startAt = 0L
        private var endAt = 0L

        fun idx(x: Int, y: Int) = y * cols + x

        fun neighbors(i: Int): List<Int> {
            val x = i % cols
            val y = i / cols
            val out = ArrayList<Int>(8)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue
                out.add(idx(nx, ny))
            }
            return out
        }

        private fun plant(safe: Int) {
            // 首点及其 8 邻格不下雷
            val forbidden = HashSet<Int>()
            forbidden.add(safe)
            forbidden.addAll(neighbors(safe))
            val pool = ArrayList<Int>()
            for (i in cells.indices) if (i !in forbidden) pool.add(i)

            // 极端小盘兜底：池子不够就只排除首点
            if (pool.size < mines) {
                pool.clear()
                for (i in cells.indices) if (i != safe) pool.add(i)
            }
            repeat(mines) {
                if (pool.isEmpty()) return@repeat
                val pick = (Math.random() * pool.size).toInt().coerceIn(0, pool.size - 1)
                cells[pool[pick]].mine = true
                pool[pick] = pool[pool.size - 1]
                pool.removeAt(pool.size - 1)
            }
            for (i in cells.indices) {
                if (cells[i].mine) continue
                var n = 0
                for (j in neighbors(i)) if (cells[j].mine) n++
                cells[i].adj = n
            }
        }

        private fun flood(start: Int) {
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                val c = cells[cur]
                if (c.open || c.flag || c.mine) continue
                c.open = true
                opened++
                if (c.adj == 0) for (j in neighbors(cur)) if (!cells[j].open) stack.addLast(j)
            }
        }

        private fun now() = System.currentTimeMillis()

        /** 返回 "boom" / "open" / "noop" */
        fun reveal(i: Int): String {
            if (over || i < 0 || i >= cells.size) return "noop"
            val c = cells[i]
            if (c.open || c.flag) return "noop"
            if (!started) {
                plant(i)
                started = true
                startAt = now()
            }
            if (c.mine) {
                c.open = true
                boom = i
                over = true
                endAt = now()
                return "boom"
            }
            flood(i)
            if (opened == cells.size - mines) {
                won = true
                over = true
                endAt = now()
            }
            return "open"
        }

        fun toggleFlag(i: Int): Boolean {
            if (over || i < 0 || i >= cells.size) return false
            val c = cells[i]
            if (c.open) return false
            c.flag = !c.flag
            flags += if (c.flag) 1 else -1
            return true
        }

        /** 双键展开：数字格周围旗数够了 → 翻开其余邻格 */
        fun chord(i: Int): String {
            if (over || i < 0 || i >= cells.size) return "noop"
            val c = cells[i]
            if (!c.open || c.adj == 0) return "noop"
            var f = 0
            val targets = ArrayList<Int>()
            for (n in neighbors(i)) {
                if (cells[n].flag) f++
                else if (!cells[n].open) targets.add(n)
            }
            if (f != c.adj || targets.isEmpty()) return "noop"
            var boomHit = false
            for (t in targets) if (reveal(t) == "boom") boomHit = true
            return if (boomHit) "boom" else "open"
        }

        fun elapsedSec(): Int {
            if (!started) return 0
            val end = if (endAt > 0) endAt else now()
            return ((end - startAt) / 1000).toInt().coerceAtLeast(0)
        }

        fun score(): Int = scoreOf(level, elapsedSec())
    }

    /// 建一局。transpose = 竖屏时把「宽棋盘」转置成竖的（高级 30 列 → 16 列 × 30 行），
    /// 与网页端 fitShape 同规：扫雷只关心相邻关系，行列互换不影响规则与计分。
    fun newGame(levelKey: Int, transpose: Boolean = false): Game {
        val lv = levelOf(levelKey)
        val swap = transpose && lv.cols > lv.rows
        return if (swap) Game(lv.key, lv.cols, lv.rows, lv.mines)
               else Game(lv.key, lv.rows, lv.cols, lv.mines)
    }
}

private val MINE_NUM_COLOR = listOf(
    Color(0xFF37474F), Color(0xFF1A73E8), Color(0xFF137333), Color(0xFFD93025),
    Color(0xFF5B21B6), Color(0xFF8B5A00), Color(0xFF00838F), Color(0xFF37474F), Color(0xFF616161),
)

@Composable
fun MineGame(
    maxScore: Int,
    onBack: () -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    // 竖屏：宽棋盘转置（与网页 fitShape 同规），转屏会重开一局
    val portrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_PORTRAIT

    var level by remember { mutableIntStateOf(1) }
    var game by remember(portrait) { mutableStateOf(MineCore.newGame(1, portrait)) }
    var tick by remember { mutableIntStateOf(0) }        // 重绘节拍：计时/动效每 250ms 一拍
    // ⚠️ 必须**读** tick 才能让 tick++ 触发重组：Compose 只订阅「被读过」的 state。
    // 2026-11-13「点了只出声、格子不翻」就是这里 —— tick 只写不读，画面冻在第一帧。
    @Suppress("UNUSED_VARIABLE")
    val tickPulse = tick
    var best by remember { mutableIntStateOf(0) }
    var leaderboard by remember { mutableStateOf<LeaderboardDto?>(null) }
    var reported by remember { mutableStateOf(false) }
    // 标记（插旗）模式：点了就插旗，不用记「长按」这个手势（2026-11-13 站主反馈「没有标记功能」）
    var flagMode by remember { mutableStateOf(false) }
    val flagModeState = rememberUpdatedState(flagMode)

    fun restart(lv: Int = level) {
        level = lv
        game = MineCore.newGame(lv, portrait)
        reported = false
        tick++
    }

    // 计时驱动：每 250ms 一拍（开局后才刷，局终即停）
    LaunchedEffect(game) {
        while (isActive && !game.over) {
            delay(250)
            if (game.started) tick++
        }
    }

    // 首次进来拉一次最高分（与网页「最高 x」同源）
    LaunchedEffect(Unit) {
        runCatching { Repo.leaderboard("minesweeper") }.onSuccess {
            best = it.mine.bestScore
            leaderboard = null
        }
    }

    // ---- 动效（2026-11-13 站主要求）：踩雷震屏 + 清场撒花，音效按翻开格数分档 ----
    var shakeX by remember { mutableIntStateOf(0) }
    val confetti = remember { mutableStateListOf<Confetto>() }

    fun boomShake() {
        scope.launch {
            val t0 = System.currentTimeMillis()
            while (true) {
                val el = System.currentTimeMillis() - t0
                if (el > 230) break
                val amp = ((230 - el) / 230f) * 9f
                shakeX = ((Math.random() - 0.5) * 2 * amp).toInt()
                delay(16)
            }
            shakeX = 0
        }
    }

    fun burstConfetti() {
        confetti.clear()
        val colors = listOf(
            Color(0xFF1A73E8), Color(0xFF137333), Color(0xFFD93025),
            Color(0xFFF9AB00), Color(0xFF8E24AA), Color(0xFF00ACC1),
        )
        repeat(70) {
            confetti.add(
                Confetto(
                    x = Math.random().toFloat(),
                    y = -0.05f - Math.random().toFloat() * 0.5f,
                    vx = (Math.random() - 0.5).toFloat() * 0.004f,
                    vy = 0.012f + Math.random().toFloat() * 0.016f,
                    life = 150,
                    color = colors[(Math.random() * colors.size).toInt()],
                )
            )
        }
    }

    // 帧循环：只在场上有彩纸时跑，撒完即停（不常驻耗电）
    LaunchedEffect(confetti.size) {
        if (confetti.isEmpty()) return@LaunchedEffect
        while (confetti.isNotEmpty()) {
            withFrameNanos {
                confetti.forEach { c -> c.x += c.vx; c.y += c.vy; c.vy += 0.0009f; c.life-- }
                confetti.removeAll { it.life <= 0 || it.y > 1.15f }
                tick++
            }
        }
    }

    fun afterAction(res: String, openedNow: Int) {
        if (res == "boom") {
            GameSfx.play("hurt")
            boomShake()
            if (!reported) {
                reported = true
            }
        } else if (res == "open") {
            // 连开一片用更高更长的一声（eat），单开一格给短 pop（merge）
            GameSfx.play(if (openedNow > 3) "eat" else "merge")
            if (game.won && !reported) {
                reported = true
                GameSfx.play("end")
                burstConfetti()
                val sc = min(game.score(), maxScore)
                if (sc > best) best = sc
                scope.launch {
                    runCatching { Repo.submitScore("minesweeper", sc) }
                    runCatching {
                        val lb = Repo.leaderboard("minesweeper")
                        best = lb.mine.bestScore
                    }
                }
            }
        }
        tick++
    }

    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("扫雷", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            SecondaryButton("返回") { onBack() }
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            MineCore.LEVELS.forEach { lv ->
                AppChip(lv.name, selected = level == lv.key) { restart(lv.key) }
            }
            Spacer(Modifier.weight(1f))
            // 插旗/翻开模式切换：点哪格就做哪件事，长按仍然可以临时插旗
            AppChip("插旗", selected = flagMode) { flagMode = true; GameSfx.play("dash"); tick++ }
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("雷 ${game.mines - game.flags}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.accentStrong)
                Text(fmtSec(game.elapsedSec()), fontSize = 15.sp, color = p.textSub)
                Text("最高 $best", fontSize = 12.sp, color = p.textWeak)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("重开") { restart() }
                SecondaryButton("排行榜") { scope.launch { runCatching { leaderboard = Repo.leaderboard("minesweeper") } } }
            }
        }
        Spacer(Modifier.height(10.dp))

        val cols = game.cols
        val rows = game.rows
        val ratio = cols.toFloat() / rows
        // 竖屏高级盘转置后是 16×30（比例 0.53），按满宽画会高到溢出屏幕；
        // 这里把高度压到视口的 62% 以内，宽度反过来算（与网页 0.70 视口高的取法同思路）
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val maxBoardH = (cfg.screenHeightDp * 0.62f).dp
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,   // 窄棋盘要居中，不然高级盘会贴在左边
        ) {
            val boardW = minOf(maxWidth, maxBoardH * ratio)
            Box(
                Modifier.width(boardW).aspectRatio(ratio)
                    .offset { IntOffset(shakeX, 0) }   // 踩雷震屏（230ms 衰减抖动）
            ) {
                Canvas(
                // ⚠️ key 只能放 game：tick 每 250ms 会变一次，把它放进 key 会让手势检测器
                // 反复重启，点击根本识别不到（2026-11-13「点了没反应」的根因）
                Modifier.fillMaxSize().pointerInput(game) {
                    detectTapGestures(
                        onLongPress = { pos ->
                            // 长按临时插旗/取消，不用切模式
                            val i = cellAt(pos.x, pos.y, size.width.toFloat(), size.height.toFloat(), game)
                            if (i >= 0 && game.toggleFlag(i)) {
                                GameSfx.play(if (game.cells[i].flag) "dash" else "merge")
                                tick++
                            }
                        },
                        onTap = { pos ->
                            val i = cellAt(pos.x, pos.y, size.width.toFloat(), size.height.toFloat(), game)
                            if (i < 0 || game.over) return@detectTapGestures
                            if (flagModeState.value) {
                                // 插旗模式：插旗后自动切回翻开（扫雷常规习惯，避免连点误标）
                                if (game.toggleFlag(i)) {
                                    flagMode = false
                                    GameSfx.play(if (game.cells[i].flag) "dash" else "merge")
                                    tick++
                                }
                                return@detectTapGestures
                            }
                            val before = game.opened
                            val res = if (game.cells[i].open) game.chord(i) else game.reveal(i)
                            if (res != "noop") afterAction(res, game.opened - before)
                        },
                    )
                },
            ) {
                val cs = size.width / cols
                // 双保险：draw 里也订阅一次 tick —— 格子翻没翻靠它重绘
                @Suppress("UNUSED_EXPRESSION") val pulse = tick
                // 棋盘缝底色：比两种格子都深（与网页 2026-11-13 改法同规）
                drawRect(Color(0xFFAEB9C7), Offset.Zero, Size(size.width, size.height))

                for (i in game.cells.indices) {
                    val c = game.cells[i]
                    val x = (i % cols) * cs
                    val y = (i / cols) * cs

                    if (c.open) {
                        // 已翻开：纯白平底（与未翻的蓝灰凸起块一眼区分）
                        drawRect(
                            if (c.mine) Color(0xFFFFD6D6) else Color(0xFFFFFFFF),
                            Offset(x + 0.5f, y + 0.5f), Size(cs - 1f, cs - 1f),
                        )
                        drawRect(
                            Color(0x1F263238), Offset(x + 0.5f, y + 0.5f), Size(cs - 1f, cs - 1f),
                            style = Stroke(1f),
                        )
                        if (c.mine) {
                            drawMine(x + cs / 2, y + cs / 2, cs, i == game.boom)
                        } else if (c.adj > 0) {
                            val style = TextStyle(
                                color = MINE_NUM_COLOR[c.adj.coerceIn(0, 8)],
                                fontSize = (cs * 0.58f).toSp(),
                                fontWeight = FontWeight.Bold,
                            )
                            val layout = measurer.measure(c.adj.toString(), style)
                            drawText(
                                layout,
                                topLeft = Offset(
                                    x + (cs - layout.size.width) / 2f,
                                    y + (cs - layout.size.height) / 2f,
                                ),
                            )
                        }
                        continue
                    }

                    // 未翻开：蓝灰渐变的凸起块 + 左上高光（立体感 = 与已翻格的最大区分）
                    val corner = androidx.compose.ui.geometry.CornerRadius(cs * 0.16f)
                    drawRoundRect(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFFEAF0F8), Color(0xFFC3D0E0)),
                            start = Offset(x, y),
                            end = Offset(x + cs, y + cs),
                        ),
                        topLeft = Offset(x + 1f, y + 1f), size = Size(cs - 2f, cs - 2f),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        Color(0x52263238),
                        Offset(x + 1f, y + 1f), Size(cs - 2f, cs - 2f),
                        cornerRadius = corner,
                        style = Stroke(1f),
                    )
                    drawLine(
                        Color(0xBFFFFFFF),
                        Offset(x + 2.5f, y + cs - 3f), Offset(x + 2.5f, y + 2.5f), 1f,
                    )
                    drawLine(
                        Color(0xBFFFFFFF),
                        Offset(x + 2.5f, y + 2.5f), Offset(x + cs - 3f, y + 2.5f), 1f,
                    )
                    if (c.flag) drawFlag(x + cs / 2, y + cs / 2, cs)
                    if (game.over && c.mine && !c.flag) drawMine(x + cs / 2, y + cs / 2, cs, false)
                }

                // 清场彩纸（坐标归一化，乘画布尺寸；随帧循环下落）
                confetti.forEach { cf ->
                    val w = (cs * 0.26f).coerceAtLeast(3f)
                    val h = (cs * 0.36f).coerceAtLeast(4f)
                    drawRect(
                        cf.color.copy(alpha = (cf.life / 40f).coerceIn(0f, 1f)),
                        topLeft = Offset(cf.x * size.width - w / 2, cf.y * size.height - h / 2),
                        size = Size(w, h),
                    )
                }
            }

            if (game.over) {
                // 结算浮层（2026-11-13 修）：原来白字直接压在浅色棋盘上看不清 ——
                // 网页那边有弹窗挡着，App 这边是浮层，自己垫一层暗底
                Box(
                    Modifier.matchParentSize().background(Color(0xA6000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (game.won) "清场了 · ${game.score()} 分" else "踩雷了",
                            color = Color(0xFFFFFFFF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (game.won) "用时 ${fmtSec(game.elapsedSec())}" else "再来一局试试手气",
                            color = Color(0xCCFFFFFF),
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("再来一局") { restart() }
                    }
                }
            }
            }
        }

        leaderboard?.let { lb ->
            LeaderboardSheet(lb = lb, onDismiss = { leaderboard = null })
        }
    }
}

 /** 清场彩纸：坐标用 0..1 归一化，绘制时乘画布尺寸（与网页 confetti 同风格） */
private class Confetto(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Int, val color: Color,
)

private fun fmtSec(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return (if (m < 10) "0" else "") + m + ":" + (if (s < 10) "0" else "") + s
}

private fun cellAt(px: Float, py: Float, w: Float, h: Float, game: MineCore.Game): Int {
    val cs = w / game.cols
    val cx = (px / cs).toInt()
    val cy = (py / cs).toInt()
    if (cx < 0 || cy < 0 || cx >= game.cols || cy >= game.rows) return -1
    return cy * game.cols + cx
}

private fun DrawScope.drawFlag(cx: Float, cy: Float, cs: Float) {
    val s = cs * 0.5f
    drawLine(Color(0xFF37474F), Offset(cx + s * 0.12f, cy - s * 0.42f), Offset(cx + s * 0.12f, cy + s * 0.42f), 1.8f)
    val flag = Path().apply {
        moveTo(cx + s * 0.12f, cy - s * 0.42f)
        lineTo(cx - s * 0.5f, cy - s * 0.16f)
        lineTo(cx + s * 0.12f, cy + s * 0.08f)
        close()
    }
    drawPath(flag, Color(0xFFD93025))
    drawLine(Color(0xFF37474F), Offset(cx - s * 0.36f, cy + s * 0.42f), Offset(cx + s * 0.32f, cy + s * 0.42f), 1.6f)
}

private fun DrawScope.drawMine(cx: Float, cy: Float, cs: Float, boom: Boolean) {
    // 与网页 drawMine 同参（2026-11-13）：0.27 半径 / 0.06 线宽 / 1.48 刺长
    val r = cs * 0.27f
    drawCircle(if (boom) Color(0xFFB3261E) else Color(0xFF263238), r, Offset(cx, cy))
    for (a in 0 until 8) {
        val ang = a * Math.PI / 4
        drawLine(
            if (boom) Color(0xFF7F1D1A) else Color(0xFF263238),
            Offset(cx + (Math.cos(ang) * r * 0.9).toFloat(), cy + (Math.sin(ang) * r * 0.9).toFloat()),
            Offset(cx + (Math.cos(ang) * r * 1.48).toFloat(), cy + (Math.sin(ang) * r * 1.48).toFloat()),
            (cs * 0.06f).coerceAtLeast(1.6f),
        )
    }
    drawCircle(Color(0xD9FFFFFF), r * 0.28f, Offset(cx - r * 0.3f, cy - r * 0.34f))
}
