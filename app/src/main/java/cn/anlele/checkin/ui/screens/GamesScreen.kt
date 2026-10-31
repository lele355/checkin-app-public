package cn.anlele.checkin.ui.screens

import android.os.SystemClock
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.GameCatalogDto
import cn.anlele.checkin.data.GameDisplayDto
import cn.anlele.checkin.data.GameLobbyDto
import cn.anlele.checkin.data.GameLobbyItemDto
import cn.anlele.checkin.data.GameRoomDto
import cn.anlele.checkin.data.GameRuleDefaultsDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.ConfirmDialog
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PickerSheetShell
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.components.SectionTitle
import cn.anlele.checkin.ui.components.SiteSwitch
import cn.anlele.checkin.ui.games.ArmyChessBoard
import cn.anlele.checkin.ui.games.ArmyChessDeployPiece
import cn.anlele.checkin.ui.games.ArmyChessLastMove
import cn.anlele.checkin.ui.games.rememberPieceIcons
import cn.anlele.checkin.ui.games.reversiLastFlips
import cn.anlele.checkin.ui.games.ArmyChessPiece
import cn.anlele.checkin.ui.games.armyChessGenDeploy
import cn.anlele.checkin.ui.games.armyChessTrySwap
import cn.anlele.checkin.ui.games.AnimBox
import cn.anlele.checkin.ui.games.GameSfx
import cn.anlele.checkin.ui.games.FishGame
import cn.anlele.checkin.ui.games.JungleBoard
import cn.anlele.checkin.ui.games.JungleLastMove
import cn.anlele.checkin.ui.games.JunglePiece
import cn.anlele.checkin.ui.games.LudoBoard
import cn.anlele.checkin.ui.games.LudoPiece
import cn.anlele.checkin.ui.games.MelonGame
import cn.anlele.checkin.ui.games.ReversiBoard
import cn.anlele.checkin.ui.games.TutorialSection
import cn.anlele.checkin.ui.games.TutorialSheet
import cn.anlele.checkin.ui.games.XiangqiBoard
import cn.anlele.checkin.ui.games.XiangqiLastMove
import cn.anlele.checkin.ui.games.XiangqiPiece
import cn.anlele.checkin.ui.games.buildReversiBoard
import cn.anlele.checkin.ui.games.tutorialFor
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** 盘面可选值 —— 与网页 / 服务端 GomokuRules.AllowedSizes 保持一致 */
private val BOARD_SIZES = listOf(13, 15, 19)

/** 单步限时可选值（秒），0 = 不限 */
private val MOVE_TIMEOUTS = listOf(0, 15, 30, 60, 120)

/** 每方总局时可选值（秒），0 = 不限 */
private val TOTAL_TIMEOUTS = listOf(0, 300, 600, 900, 1800)

/**
 * 小游戏（五子棋）。
 *
 * 联机靠**短轮询**：进对局后每 1.2 秒拉一次 /api/v1/games/{id}，
 * revision 变了才重绘 —— 回合制棋类不值得为它引一条 WebSocket。
 * 认输 / 再来一局 / 解散都带二次确认，因为都不可撤销。
 *
 * 【设置分两类，别混】
 *  · 盘面 / 禁手 / 限时 —— 建房那一刻**快照进房间**，双方共享，服务端据此判定。
 *    已开打的局不受之后改设置影响，否则两个人看到的规则会不一致。
 *  · 落子确认 / 坐标 / 最后一手 / 震动 —— 纯本人画面偏好，不上服务端、不影响判定。
 */
@Composable
fun GamesScreen(vm: AppVm, setModuleBack: ((() -> Boolean)?) -> Unit = {}) {
    val scope = rememberCoroutineScope()

    // 两级入口：gameKey == null 停在「游戏列表 + 游戏总设置」，
    // 选了某个游戏才进它的大厅 → 对局。加第二种游戏时这层骨架不用动。
    var catalog by remember { mutableStateOf(GameCatalogDto()) }
    var gameKey by remember { mutableStateOf<String?>(null) }

    var lobby by remember { mutableStateOf(GameLobbyDto()) }
    var display by remember { mutableStateOf(GameDisplayDto()) }
    var rules by remember { mutableStateOf(GameRuleDefaultsDto()) }
    var loading by remember { mutableStateOf(true) }
    var code by remember { mutableStateOf("") }
    var room by remember { mutableStateOf<GameRoomDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var displayOpen by remember { mutableStateOf(false) }
    var rulesOpen by remember { mutableStateOf(false) }
    var tutorialOpen by remember { mutableStateOf(false) }

    // 大厅里「这一局」的规则：默认跟账号设置走，建房前可以临时改（只影响这一盘）
    var newSize by remember { mutableStateOf(15) }
    var newForbidden by remember { mutableStateOf(false) }
    // 军旗玩法：明棋 / 暗棋 / 翻棋（建房显式带 mode；0=明棋 1=暗棋 2=翻棋）
    var newArmyMode by remember { mutableIntStateOf(0) }

    // 飞行棋建房：人数 + 每座朋友/AI（ludoAi 对应 seat1..3）
    var ludoSeatCount by remember { mutableIntStateOf(2) }
    var ludoAi by remember { mutableStateOf(listOf(false, false, false)) }

    // 设置对象变了（首次加载完 / 刚保存完）就把大厅选择重置回账号默认。
    // 用 data class 当 key：值没变就不会重启，用户在楼下挑好的盘面不会被无故冲掉。
    LaunchedEffect(rules) {
        newSize = rules.size
        newForbidden = rules.forbidden
    }

    suspend fun refreshLobby() {
        val key = gameKey ?: return
        try {
            lobby = Repo.gameLobby(key)
            display = lobby.display      // 服务端顺手带回来的我的设置，省一次往返
            rules = lobby.rules
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        catalog = try {
            Repo.gameCatalog()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
            GameCatalogDto()
        }
        // 修复 1：进游戏列表层就拉一次显示偏好，不让设置浮层用内存默认值兜底
        try { display = Repo.gameDisplay() } catch (_: Exception) { }
    }

    // 进到某个游戏才拉大厅；切游戏时把上一个游戏的房间状态清掉
    LaunchedEffect(gameKey) {
        if (gameKey == null) {
            room = null
            return@LaunchedEffect
        }
        loading = true
        room = null
        refreshLobby()
    }

    // 对局轮询：只有进房间后才跑；离开房间（room = null）就退出循环
    LaunchedEffect(room?.id) {
        while (true) {
            val cur = room ?: break
            delay(if (cur.status == "finished") 3000 else 1200)
            val latest = try {
                Repo.game(cur.id, gameKey ?: "gomoku")
            } catch (_: Exception) {
                continue          // 断网就下一轮再试，别把界面打回大厅
            }
            if (room?.id != cur.id) break
            if (latest.revision != cur.revision) room = latest
        }
    }

    fun create(side: Int) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                // 只覆盖盘面/禁手（这是用户在当前界面里刚选的）；
                // 限时留空 = 由服务端按账号默认填，避免本地缓存过期
                val key = gameKey ?: "gomoku"
                val d = Repo.createGame(
                    side, size = newSize, forbidden = newForbidden, gameKey = key,
                    mode = if (key == "armychess") listOf("open", "dark", "fanqi")[newArmyMode.coerceIn(0, 2)] else null,
                )
                room = Repo.game(d.id, key)
            } catch (e: Exception) {
                vm.toast(e.message ?: "建房失败")
            } finally {
                busy = false
            }
        }
    }

    fun onCreateAi() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val key = gameKey ?: "gomoku"
                val d = Repo.createGame(
                    1, opponent = "ai", aiLevel = 2, gameKey = key,
                    mode = if (key == "armychess") listOf("open", "dark", "fanqi")[newArmyMode.coerceIn(0, 2)] else null,
                )
                room = Repo.game(d.id, key)
            } catch (e: Exception) {
                vm.toast(e.message ?: "建房失败")
            } finally {
                busy = false
            }
        }
    }

    fun createLudo() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val key = "ludo"
                val d = if (ludoSeatCount <= 2) {
                    // 2 人：seat1 是 AI 走人机，否则等朋友
                    Repo.createGame(1, opponent = if (ludoAi.firstOrNull() == true) "ai" else "friend", gameKey = key)
                } else {
                    // 3~4 人：seats 含 seat0（房主），其余按 AI 开关
                    val specs = (0 until ludoSeatCount).map { i ->
                        cn.anlele.checkin.data.Repo.GameSeatSpec(isAi = if (i == 0) false else ludoAi.getOrElse(i - 1) { false })
                    }
                    Repo.createGame(1, gameKey = key, seats = specs)
                }
                room = Repo.game(d.id, key)
            } catch (e: Exception) {
                vm.toast(e.message ?: "建房失败")
            } finally {
                busy = false
            }
        }
    }

    fun joinByCode() {
        val c = code.trim().uppercase()
        if (c.isEmpty() || busy) return
        busy = true
        scope.launch {
            try {
                val d = Repo.joinGameByCode(c)
                room = Repo.game(d.id, gameKey ?: "gomoku")
                code = ""
            } catch (e: Exception) {
                vm.toast(e.message ?: "加入失败")
            } finally {
                busy = false
            }
        }
    }

    fun join(id: Int) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val key = gameKey ?: "gomoku"
                runCatching { Repo.joinGame(id, key) }      // 已经在局中会幂等返回
                room = Repo.game(id, key)
            } catch (e: Exception) {
                vm.toast(e.message ?: "加入失败")
            } finally {
                busy = false
            }
        }
    }

    fun move(x: Int, y: Int) {
        val cur = room ?: return
        scope.launch {
            try {
                room = Repo.gameMove(cur.id, x, y, gameKey ?: "gomoku")
            } catch (e: ApiException) {
                vm.toast(e.message)
                runCatching { room = Repo.game(cur.id, gameKey ?: "gomoku") }   // 拉一次真相，别停在错误局面
            } catch (e: Exception) {
                vm.toast("落子失败，检查网络")
            }
        }
    }

    fun roll() {
        val cur = room ?: return
        scope.launch {
            try {
                room = Repo.gameRoll(cur.id, gameKey ?: "ludo")
            } catch (e: Exception) {
                vm.toast(e.message ?: "掷骰失败")
            }
        }
    }

    fun ludoMovePiece(p: Int) {
        val cur = room ?: return
        scope.launch {
            try {
                room = Repo.ludoMove(cur.id, p, gameKey ?: "ludo")
            } catch (e: Exception) {
                vm.toast(e.message ?: "走子失败")
            }
        }
    }

    fun fromToMove(fx: Int, fy: Int, tx: Int, ty: Int) {
        val cur = room ?: return
        val key = gameKey ?: return
        scope.launch {
            try {
                room = Repo.gameMoveFromTo(cur.id, fx, fy, tx, ty, key)
            } catch (e: Exception) {
                vm.toast(e.message ?: "走子失败")
            }
        }
    }

    /** 军旗明棋布阵提交（pieces=自摆阵 / random=随机） */
    fun deploy(req: Repo.GameDeployReq) {
        val cur = room ?: return
        scope.launch {
            try {
                room = Repo.deployGame(cur.id, req, gameKey ?: "armychess")
            } catch (e: Exception) {
                vm.toast(e.message ?: "布阵提交失败")
                runCatching { room = Repo.game(cur.id, gameKey ?: "armychess") }
            }
        }
    }

    fun resign() {        val cur = room ?: return
        scope.launch {
            try {
                room = Repo.gameResign(cur.id, gameKey ?: "gomoku")
            } catch (e: Exception) {
                vm.toast(e.message ?: "操作失败")
            }
        }
    }

    fun rematch() {
        val cur = room ?: return
        scope.launch {
            try {
                room = Repo.gameRematch(cur.id, gameKey ?: "gomoku")
            } catch (e: Exception) {
                vm.toast(e.message ?: "操作失败")
            }
        }
    }

    fun backToLobby() {
        room = null
        scope.launch { refreshLobby() }
    }

    // 修复 2：注册模块内返回回调 —— 对局→大厅→列表→退出模块（逐级降层）
    // 列表层返回 false = 交给 shell 弹路由栈（退出游戏模块）
    LaunchedEffect(room?.id, gameKey) {
        setModuleBack {
            when {
                room != null -> { backToLobby(); true }
                gameKey != null -> { gameKey = null; true }
                else -> false
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { setModuleBack(null) }
    }

    fun leave() {
        val cur = room ?: return
        scope.launch {
            runCatching { Repo.gameLeave(cur.id, gameKey ?: "gomoku") }
            backToLobby()
        }
    }

    fun saveDisplay(next: GameDisplayDto) {
        scope.launch {
            try {
                display = Repo.saveGameDisplay(next)
                displayOpen = false
                vm.toast("设置已保存")
            } catch (e: Exception) {
                vm.toast(e.message ?: "保存失败")
            }
        }
    }

    fun saveRules(next: GameRuleDefaultsDto) {
        val key = gameKey ?: "gomoku"
        scope.launch {
            try {
                rules = Repo.saveGameRules(key, next)
                rulesOpen = false
                vm.toast("设置已保存")
            } catch (e: Exception) {
                vm.toast(e.message ?: "保存失败")
            }
        }
    }

    /** 保存两层（显示层 + 本游戏规则层）——单游戏设置面板一次提交 */
    fun saveBoth(dd: GameDisplayDto, rr: GameRuleDefaultsDto) {
        val key = gameKey ?: "gomoku"
        scope.launch {
            try {
                display = Repo.saveGameDisplay(dd)
                rules = Repo.saveGameRules(key, rr)
                rulesOpen = false
                vm.toast("设置已保存")
            } catch (e: Exception) {
                vm.toast(e.message ?: "保存失败")
            }
        }
    }

    /**
     * 一键重建（走查表 §3）：先落库新设置，再建新关旧跳新房。
     * AI 局免确认直接重建；真人局先征求对方同意（拒绝/超时 → 回滚提示，本局保留）。
     */
    fun saveAndRebuild(dd: GameDisplayDto, rr: GameRuleDefaultsDto) {
        val cur = room ?: return
        val key = gameKey ?: "gomoku"
        scope.launch {
            try {
                // 1) 先落库（新房按新默认建）
                display = Repo.saveGameDisplay(dd)
                rules = Repo.saveGameRules(key, rr)
                rulesOpen = false
                // 2) 征询（AI 局直接 ready）
                val ask = Repo.gameRebuild(key, cur.id, Repo.RebuildReq(action = "ask"))
                var vote: Int? = if (ask.ready == true) 1 else null
                if (vote == null) vm.toast("等待对方确认换房")
                var waited = 0
                while (vote == null && waited < 90) {
                    kotlinx.coroutines.delay(2000)
                    val s = Repo.game(cur.id, key)
                    val rb = s.rebuild
                    if ((rb?.doneId ?: 0) > 0) { room = Repo.game(rb!!.doneId, key); return@launch }
                    if (rb?.vote != null) vote = rb.vote
                    waited++
                }
                if (vote != 1) {
                    runCatching { Repo.gameRebuild(key, cur.id, Repo.RebuildReq(action = "cancel")) }
                    vm.toast("已保留当前对局")
                    return@launch
                }
                // 3) 建新（规则=刚落库的默认）关旧
                val d = Repo.createGame(
                    1,
                    opponent = if (cur.isAi) "ai" else "friend",
                    aiLevel = if (cur.isAi) cur.aiLevel.coerceAtLeast(1) else null,
                    gameKey = key,
                    mode = if (key == "armychess") rr.mode else null,
                )
                Repo.gameRebuild(key, cur.id, Repo.RebuildReq(action = "execute", newRoomId = d.id))
                room = Repo.game(d.id, key)
            } catch (e: Exception) {
                vm.toast(e.message ?: "重建失败")
            }
        }
    }

    /** 对手端：对重建请求表态 */
    fun rebuildVote(accept: Boolean) {
        val cur = room ?: return
        val key = gameKey ?: "gomoku"
        scope.launch {
            runCatching { Repo.gameRebuild(key, cur.id, Repo.RebuildReq(action = "vote", accept = accept)) }
                .onFailure { vm.toast(it.message ?: "操作失败") }
        }
    }

    /** 换房完成 → 跳新房 */
    fun rebuildGoto(id: Int) {
        val key = gameKey ?: "gomoku"
        scope.launch { runCatching { room = Repo.game(id, key) } }
    }

    // 修复 1：「恢复默认」= 立即恢复出厂值并落库（只动自己那半），不是清空草稿
    fun resetDisplay() {
        scope.launch {
            try {
                display = Repo.saveGameDisplay(GameDisplayDto())
                displayOpen = false
                vm.toast("已恢复默认")
            } catch (e: Exception) {
                vm.toast(e.message ?: "操作失败")
            }
        }
    }

    fun resetRules() {
        val key = gameKey ?: "gomoku"
        scope.launch {
            try {
                rules = Repo.saveGameRules(key, GameRuleDefaultsDto())
                rulesOpen = false
                vm.toast("已恢复默认")
            } catch (e: Exception) {
                vm.toast(e.message ?: "操作失败")
            }
        }
    }

    val cur = room
    val soloGame = gameKey?.let { k -> catalog.games.find { it.key == k && it.solo } }
    when {
        gameKey == null -> GamesListContent(
            catalog = catalog,
            onPick = { gameKey = it },
            onOpenDisplay = { displayOpen = true },
        )
        soloGame != null -> SoloContent(
            gameKey = soloGame.key,
            maxScore = soloGame.maxScore,
            onBack = { gameKey = null },
        )
        cur == null -> LobbyContent(
            lobby = lobby,
            rules = rules,
            newSize = newSize,
            newForbidden = newForbidden,
            newArmyMode = newArmyMode,
            loading = loading,
            code = code,
            busy = busy,
            onCodeChange = { code = it.take(6) },
            onSizeChange = { newSize = it },
            onForbiddenChange = { newForbidden = it },
            onArmyModeChange = { newArmyMode = it },
            onCreate = ::create,
            onCreateAi = ::onCreateAi,
            onJoinByCode = ::joinByCode,
            onJoin = ::join,
            onRefresh = { scope.launch { refreshLobby() } },
            onBack = { gameKey = null },
            onOpenRules = { rulesOpen = true },
            onOpenTutorial = { tutorialOpen = true },
            onLeave = { id -> scope.launch { runCatching { Repo.gameLeave(id, gameKey ?: "gomoku") }; refreshLobby() } },
            gameKey = gameKey ?: "gomoku",
            ludoSeatCount = ludoSeatCount,
            ludoAi = ludoAi,
            onLudoSeatCount = { ludoSeatCount = it },
            onLudoAiToggle = { i, v -> ludoAi = ludoAi.toMutableList().also { it[i] = v } },
            onCreateLudo = ::createLudo,
        )
        else -> RoomContent(
            dto = cur,
            prefs = display,
            onMove = ::move,
            onResign = { confirmResign = true },
            onRematch = ::rematch,
            onBack = ::backToLobby,
            onLeaveAsk = { confirmLeave = true },
            onOpenRules = { rulesOpen = true },
            onOpenTutorial = { tutorialOpen = true },
            onRoll = ::roll,
            onLudoMove = ::ludoMovePiece,
            onFromTo = ::fromToMove,
            onDeploy = ::deploy,
            onRebuildVote = ::rebuildVote,
            onRebuildGoto = ::rebuildGoto,
        )
    }

    // 音效开关同步（DisplaySettings.Sound → GameSfx，只影响本机）
    LaunchedEffect(display.sound) { GameSfx.enabled = display.sound }

    if (displayOpen) {
        GameDisplaySheet(
            initial = display,
            onDismiss = { displayOpen = false },
            onSave = ::saveDisplay,
            onResetDefault = ::resetDisplay,
        )
    }
    if (rulesOpen) {
        GameRulesSheet(
            gameKey = gameKey ?: "gomoku",
            initialDisplay = display,
            initialRules = rules,
            inGame = cur?.status == "playing",
            onDismiss = { rulesOpen = false },
            onSave = ::saveBoth,
            onResetDefault = ::resetRules,
            onRebuild = ::saveAndRebuild,
        )
    }

    if (confirmResign) {
        ConfirmDialog(
            title = "认输？",
            message = "这一局会判你负，棋盘保留可以复盘。",
            confirmText = "认输",
            danger = true,
            onConfirm = { confirmResign = false; resign() },
            onDismiss = { confirmResign = false },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = if (room?.status == "playing") "离开这一局？" else "收拾掉这一盘？",
            message = if (room?.status == "playing") "对局中离开等于认输。" else "结束的对局会被清掉，删除后看不到复盘了。",
            confirmText = "离开",
            danger = room?.status == "playing",
            onConfirm = { confirmLeave = false; leave() },
            onDismiss = { confirmLeave = false },
        )
    }

    if (tutorialOpen) {
        val gk = gameKey ?: "gomoku"
        val gName = catalog.games.find { it.key == gk }?.name ?: "游戏"
        // 方案 A：优先调服务端教程（单一事实源），失败时用本地兜底（仅 gomoku/reversi）
        val tutorialState = produceState<Pair<List<TutorialSection>?, String?>>(null to null, gk, tutorialOpen) {
            if (!tutorialOpen) return@produceState
            try {
                val sections = Repo.gameTutorial(gk).map {
                    TutorialSection(title = it.title, paras = it.paras)
                }
                value = sections to null
            } catch (_: Exception) {
                // 拉不到教程：用本地兜底（仅 gomoku/reversi），其它游戏显示暂不可用
                val fallback = tutorialFor(gk)
                if (fallback != null) {
                    value = fallback to null
                } else {
                    value = null to "教程暂不可用"
                }
            }
        }
        val (sections, errMsg) = tutorialState.value
        TutorialSheet(
            gameName = gName,
            sections = sections ?: emptyList(),
            error = errMsg,
            onClose = { tutorialOpen = false },
        )
    }
}

// ---------------- 游戏列表（入口第一屏） ----------------

/**
 * 入口第一屏：有哪些小游戏 + 游戏总设置入口。
 *
 * 列表来自服务端 `GameCatalog` —— 以后加第二种游戏只要在那儿登记一条，
 * 这里自动多出一张卡，页面骨架不用动。点进某张卡才进它的大厅。
 */
@Composable
private fun GamesListContent(
    catalog: GameCatalogDto,
    onPick: (String) -> Unit,
    onOpenDisplay: () -> Unit,
) {
    val p = palette
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeadCard(
                title = "小游戏",
                actions = { SecondaryButton("设置", onClick = onOpenDisplay) },
            )
        }

        items(catalog.games.size) { i ->
            val g = catalog.games[i]
            AppCard(
                Modifier.fillMaxWidth().then(
                    if (g.available) Modifier.clickable { onPick(g.key) } else Modifier,
                ),
                padding = PaddingValues(13.dp),
            ) {
                // AppCard 的 content 是 Box 不是 Column —— 多个子项要自己包一层
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).background(p.accentSoft, chipShape()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons["gamepad"], null, Modifier.size(19.dp), tint = p.accentStrong)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontSize = 14.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                        if (g.sub.isNotBlank()) {
                            Text(g.sub, fontSize = 11.5.sp, color = p.textSub, lineHeight = 16.sp)
                        }
                    }
                    if (g.available) {
                        Icon(AppIcons["chevron-right"], null, Modifier.size(17.dp), tint = p.textWeak)
                    } else {
                        AppChip("敬请期待")
                    }
                }
            }
        }
    }
}

// ---------------- 单机游戏 ----------------

/** 单机游戏画面：跳过建房/大厅/对局，直达玩法。 */
@Composable
private fun SoloContent(
    gameKey: String,
    maxScore: Int,
    onBack: () -> Unit,
) {
    when (gameKey) {
        "melon" -> MelonGame(maxScore = maxScore, onBack = onBack)
        "fish" -> FishGame(maxScore = maxScore, onBack = onBack)
        else -> MelonGame(maxScore = maxScore, onBack = onBack)
    }
}

// ---------------- 大厅 ----------------

@Composable
private fun LobbyContent(
    lobby: GameLobbyDto,
    rules: GameRuleDefaultsDto,
    newSize: Int,
    newForbidden: Boolean,
    newArmyMode: Int = 0,
    loading: Boolean,
    code: String,
    busy: Boolean,
    onCodeChange: (String) -> Unit,
    onSizeChange: (Int) -> Unit,
    onForbiddenChange: (Boolean) -> Unit,
    onArmyModeChange: (Int) -> Unit = {},
    onCreate: (Int) -> Unit,
    onCreateAi: () -> Unit = {},
    onJoinByCode: () -> Unit,
    onJoin: (Int) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenTutorial: () -> Unit = {},
    onLeave: (Int) -> Unit,
    gameKey: String = "gomoku",
    ludoSeatCount: Int = 2,
    ludoAi: List<Boolean> = listOf(false, false, false),
    onLudoSeatCount: (Int) -> Unit = {},
    onLudoAiToggle: (Int, Boolean) -> Unit = { _, _ -> },
    onCreateLudo: () -> Unit = {},
) {
    val p = palette
    val mine = lobby.items.filter { it.mySide != 0 }
    val open = lobby.items.filter { it.mySide == 0 }

    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeadCard(
                title = "小游戏",
                actions = {
                    SecondaryButton("刷新", onClick = onRefresh)
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton("设置", onClick = onOpenRules)
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton("返回", onClick = onBack)
                },
            )
        }

        // 新建对局：参数分组（人数/对手 · 盘面 · 规则 · 限时），主按钮突出
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    SectionTitle("新建对局", trailing = { SecondaryButton("玩法", onClick = onOpenTutorial) })
                    Spacer(Modifier.height(4.dp))

                    if (gameKey == "ludo") {
                        OptionChips(
                            title = "人数",
                            options = listOf("2 人", "3 人", "4 人"),
                            selectedIndex = (ludoSeatCount - 2).coerceIn(0, 2),
                            enabled = !busy,
                            onPick = { onLudoSeatCount(it + 2) },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("座位", fontSize = 12.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(7.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            AppChip("我 · 红")
                            (1 until ludoSeatCount).forEach { i ->
                                val colorName = when (i) { 1 -> "黄"; 2 -> "蓝"; else -> "绿" }
                                val isAi = ludoAi.getOrElse(i - 1) { false }
                                AppChip("${i + 1} 号 · $colorName · " + if (isAi) "AI" else "朋友", selected = isAi) {
                                    if (!busy) onLudoAiToggle(i - 1, !isAi)
                                }
                            }
                        }
                    } else {
                        if (gameKey == "armychess") {
                            OptionChips(
                                title = "玩法",
                                options = listOf("明棋", "暗棋", "翻棋"),
                                selectedIndex = newArmyMode.coerceIn(0, 2),
                                enabled = !busy,
                                onPick = onArmyModeChange,
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        OptionChips(
                            title = "盘面",
                            options = BOARD_SIZES.map { "$it 路" },
                            selectedIndex = BOARD_SIZES.indexOf(newSize).coerceAtLeast(0),
                            enabled = !busy,
                            onPick = { onSizeChange(BOARD_SIZES[it]) },
                        )
                        OptionChips(
                            title = "规则",
                            options = listOf("自由规则", "标准规则"),
                            selectedIndex = if (newForbidden) 1 else 0,
                            enabled = !busy,
                            onPick = { onForbiddenChange(it == 1) },
                        )
                    }

                    // 限时：只读事实标签（不写解释句，守 0.2）
                    Spacer(Modifier.height(10.dp))
                    Text("限时", fontSize = 12.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    AppChip(timeoutSummary(rules.moveTimeoutSec, rules.totalTimeoutSec))

                    Spacer(Modifier.height(14.dp))
                    if (gameKey == "ludo") {
                        PrimaryButton("新建对局", Modifier.fillMaxWidth(), icon = "plus", enabled = !busy) { onCreateLudo() }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton("新建对局（我先手）", Modifier.weight(1f), icon = "plus", enabled = !busy) { onCreate(1) }
                            SecondaryButton("我后手", Modifier.weight(0.6f), enabled = !busy) { onCreate(2) }
                        }
                        if (lobby.items.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            SecondaryButton("人机对战", Modifier.fillMaxWidth(), enabled = !busy) { onCreateAi() }
                        }
                    }
                }
            }
        }

        // 加入房间：次操作独立成卡，不跟建房混
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    SectionTitle("加入房间")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            AppField(
                                label = "",
                                value = code,
                                onValueChange = onCodeChange,
                                placeholder = "房间码（6 位）",
                                singleLine = true,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton("加入", enabled = code.isNotBlank() && !busy) { onJoinByCode() }
                    }
                }
            }
        }

        if (mine.isNotEmpty()) {
            item { SectionTitle("我的对局") }
            items(mine, key = { it.id }) { r ->
                RoomRowCard(r, mySide = r.mySide, onClick = { onJoin(r.id) }) {
                    if (r.status != "playing") {
                        SecondaryButton("解散", danger = true) { onLeave(r.id) }
                    }
                }
            }
        }

        item { SectionTitle("大厅里的房间") }
        if (open.isEmpty()) {
            item {
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("正在加载…", fontSize = 12.5.sp, color = p.textWeak)
                    }
                } else {
                    EmptyState(icon = "gamepad", title = "现在没人开着房间", hint = "你先建一盘吧")
                }
            }
        } else {
            items(open, key = { it.id }) { r ->
                RoomRowCard(r, mySide = 0, onClick = { onJoin(r.id) })
            }
        }
    }
}

@Composable
private fun RoomRowCard(
    r: GameLobbyItemDto,
    mySide: Int,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = palette
    val statusText = when {
        r.status == "waiting" && r.mine -> "等人进来"
        r.status == "waiting" -> "可加入"
        r.status == "finished" -> when {
            r.winner == 3 -> "和棋"
            r.winner == 0 -> "已结束"
            r.winner == mySide -> "你赢了"
            else -> "你输了"
        }
        r.myTurn -> "轮到你"
        else -> "等对手"
    }
    val hot = r.status == "waiting" || (r.status == "playing" && r.myTurn)
    val rules = "${r.size}×${r.size} · ${if (r.forbidden) "标准" else "自由"}"

    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(if (hot) p.accent else p.borderStrong, CircleShape),
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                val title = when {
                    r.status == "waiting" -> "${r.hostName} 在等人"
                    r.guestName.isEmpty() -> r.hostName
                    else -> "${r.hostName} vs ${r.guestName}"
                }
                Text(title, fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (r.status == "waiting") "房间码 ${r.code} · $rules · $statusText"
                    else "$rules · ${r.moveCount} 手 · $statusText · ${Dates.dayTime(r.updatedAt)}",
                    fontSize = 11.5.sp,
                    color = p.textWeak,
                    maxLines = 1,
                )
            }
            if (trailing != null) trailing()
        }
    }
}

// ---------------- 设置弹层 ----------------

/// 游戏总设置：只改自己的画面，不进房间、不影响对手
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameDisplaySheet(
    initial: GameDisplayDto,
    onDismiss: () -> Unit,
    onSave: (GameDisplayDto) -> Unit,
    onResetDefault: () -> Unit,
) {
    val p = palette
    // 草稿语义：改动只落在本地，点「保存」才提交
    var d by remember(initial) { mutableStateOf(initial) }

    PickerSheetShell(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 22.dp)) {
            Text("游戏设置", fontSize = 16.sp, color = p.text, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(10.dp))
            SwitchRow(
                title = "落子二次确认",
                checked = d.tapConfirm,
                onChange = { d = d.copy(tapConfirm = it) },
            )
            SwitchRow(
                title = "显示坐标",
                checked = d.showCoord,
                onChange = { d = d.copy(showCoord = it) },
            )
            SwitchRow(
                title = "标记最后一手",
                checked = d.lastMark,
                onChange = { d = d.copy(lastMark = it) },
            )
            SwitchRow(
                title = "落子震动",
                checked = d.vibrate,
                onChange = { d = d.copy(vibrate = it) },
            )
            SwitchRow(
                title = "音效",
                checked = d.sound,
                onChange = { d = d.copy(sound = it) },
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("保存", Modifier.weight(1f)) { onSave(d) }
                // 恢复默认 = 立即恢复出厂值并落库（只动显示偏好这半），不是清空草稿
                SecondaryButton("恢复默认", Modifier.weight(1f)) { onResetDefault() }
            }
        }
    }
}

/// 单游戏设置（走查表 §2.1）：按 gameKey 只显示适用项——固定盘面绝不出现「盘面大小」。
/// 显示层（落子确认/坐标/最后落子/音效）即时生效；重建层（盘面/禁手/军旗模式/限时）
/// 建房快照——对局中改 → 提醒 + 一键重建（走查表 §3）。红线 19：各设置 DTO 独立。
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameRulesSheet(
    gameKey: String,
    initialDisplay: GameDisplayDto,
    initialRules: GameRuleDefaultsDto,
    inGame: Boolean,
    onDismiss: () -> Unit,
    onSave: (GameDisplayDto, GameRuleDefaultsDto) -> Unit,
    onResetDefault: () -> Unit,
    onRebuild: (GameDisplayDto, GameRuleDefaultsDto) -> Unit,
) {
    val p = palette
    var d by remember(initialDisplay) { mutableStateOf(initialDisplay) }
    var r by remember(initialRules) { mutableStateOf(initialRules) }

    // §2.1 适用项（不适用项一个字都不出现，0.2）
    val online = gameKey in setOf("gomoku", "jungle", "xiangqi", "armychess", "reversi", "ludo")
    val showTapConfirm = online
    val showCoord = gameKey in setOf("gomoku", "jungle", "xiangqi", "reversi")
    val showLastMark = online
    val showSound = gameKey == "fish" || gameKey == "melon"   // 有音效的游戏才显示（melon M13 落地）
    val showSize = gameKey == "gomoku"
    val showForbidden = gameKey == "gomoku"
    val showMode = gameKey == "armychess"
    val showTimers = online

    val rebuildChanged = (showSize && r.size != initialRules.size) ||
        (showForbidden && r.forbidden != initialRules.forbidden) ||
        (showMode && r.mode != initialRules.mode) ||
        (showTimers && (r.moveTimeoutSec != initialRules.moveTimeoutSec ||
            r.totalTimeoutSec != initialRules.totalTimeoutSec))

    PickerSheetShell(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 22.dp)) {
            Text("游戏设置", fontSize = 16.sp, color = p.text, fontWeight = FontWeight.SemiBold)

            if (showTapConfirm) SwitchRow(title = "落子确认", checked = d.tapConfirm, onChange = { d = d.copy(tapConfirm = it) })
            if (showCoord) SwitchRow(title = "坐标显示", checked = d.showCoord, onChange = { d = d.copy(showCoord = it) })
            if (showLastMark) SwitchRow(title = "最后落子标记", checked = d.lastMark, onChange = { d = d.copy(lastMark = it) })
            if (showSound) SwitchRow(title = "音效", checked = d.sound, onChange = { d = d.copy(sound = it) })

            if (showSize) {
                OptionChips(
                    title = "盘面",
                    options = BOARD_SIZES.map { "$it 路" },
                    selectedIndex = BOARD_SIZES.indexOf(r.size).coerceAtLeast(0),
                    onPick = { r = r.copy(size = BOARD_SIZES[it]) },
                )
            }
            if (showForbidden) {
                OptionChips(
                    title = "规则",
                    options = listOf("自由规则", "标准规则"),
                    selectedIndex = if (r.forbidden) 1 else 0,
                    onPick = { r = r.copy(forbidden = it == 1) },
                )
            }
            if (showMode) {
                OptionChips(
                    title = "军旗模式",
                    options = listOf("明棋", "暗棋", "翻棋"),
                    selectedIndex = listOf("open", "dark", "fanqi").indexOf(r.mode).coerceAtLeast(0),
                    onPick = { r = r.copy(mode = listOf("open", "dark", "fanqi")[it]) },
                )
            }
            if (showTimers) {
                OptionChips(
                    title = "单步限时",
                    options = MOVE_TIMEOUTS.map { moveTimeoutLabel(it) },
                    selectedIndex = MOVE_TIMEOUTS.indexOf(r.moveTimeoutSec).coerceAtLeast(0),
                    onPick = { r = r.copy(moveTimeoutSec = MOVE_TIMEOUTS[it]) },
                )
                OptionChips(
                    title = "每方总局时",
                    options = TOTAL_TIMEOUTS.map { totalTimeoutLabel(it) },
                    selectedIndex = TOTAL_TIMEOUTS.indexOf(r.totalTimeoutSec).coerceAtLeast(0),
                    onPick = { r = r.copy(totalTimeoutSec = TOTAL_TIMEOUTS[it]) },
                )
            }

            if (inGame && rebuildChanged) {
                // 重建提醒（走查表 §3）：0.2 文案极简
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().background(p.card.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚠ 该设置需重建房间生效", Modifier.weight(1f), fontSize = 12.sp, color = p.text)
                    PrimaryButton("一键重建") { onRebuild(d, r) }
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton("取消") { onDismiss() }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("保存", Modifier.weight(1f)) { onSave(d, r) }
                // 恢复默认 = 立即恢复出厂值并落库（只动本游戏规则这半）
                SecondaryButton("恢复默认", Modifier.weight(1f)) { onResetDefault() }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 13.sp, color = p.text)
        Spacer(Modifier.width(10.dp))
        SiteSwitch(checked) { onChange(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChips(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onPick: (Int) -> Unit,
) {
    val p = palette
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(title, fontSize = 12.sp, color = p.textSub, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            options.forEachIndexed { i, label ->
                if (enabled) {
                    AppChip(label, selected = i == selectedIndex) { onPick(i) }
                } else {
                    AppChip(label, selected = i == selectedIndex)
                }
            }
        }
    }
}

// ---------------- 棋盘 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomContent(
    dto: GameRoomDto,
    prefs: GameDisplayDto,
    onMove: (Int, Int) -> Unit,
    onResign: () -> Unit,
    onRematch: () -> Unit,
    onBack: () -> Unit,
    onLeaveAsk: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenTutorial: () -> Unit = {},
    onRoll: () -> Unit = {},
    onLudoMove: (Int) -> Unit = {},
    onFromTo: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onDeploy: (Repo.GameDeployReq) -> Unit = {},
    onRebuildVote: (Boolean) -> Unit = {},
    onRebuildGoto: (Int) -> Unit = {},
) {
    val n = if (dto.size > 0) dto.size else 15
    val showClock = dto.moveTimeoutSec > 0 || dto.totalTimeoutSec > 0

    // 重建换房握手（走查表 §3）：对方请求 → 同意/拒绝条；换房完成 → 跳新房
    val rebuild = dto.rebuild
    LaunchedEffect(rebuild?.doneId) {
        val done = rebuild?.doneId ?: 0
        if (done > 0) onRebuildGoto(done)
    }

    // 两段式走子（象棋/斗兽棋/军旗）：选起点 → 高亮 → 选终点
    var selFrom by remember(dto.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(dto.revision, dto.myTurn) { if (!dto.myTurn) selFrom = null }
    fun isOwnPiece(x: Int, y: Int): Boolean =
        dto.state?.pieces?.any { it.x == x && it.y == y && it.side == dto.mySide } == true

    // 可走提示按需表（QA §3-P1）：选中棋子才拉单子提示，轮询不再全量下发
    var hintMap by remember(dto.id) { mutableStateOf<Map<String, List<List<Int>>>>(emptyMap()) }
    LaunchedEffect(selFrom) {
        val s = selFrom
        hintMap = if (s == null) emptyMap()
        else runCatching { Repo.gameHints(dto.gameKey, dto.id, s.first, s.second) }.getOrDefault(emptyMap())
    }

    // 可走提示查询（按需化）：以单子表为准；未就绪退回直接走（服务端仍兜底校验）
    fun canTarget(fx: Int, fy: Int, tx: Int, ty: Int): Boolean {
        val lst = hintMap["$fx,$fy"] ?: return true
        return lst.any { it.size >= 2 && it[0] == tx && it[1] == ty }
    }

    // 走子滑动动画 + 吃子鬼影（Batch D）：revision 变化按 lastMove 启动，帧循环推进
    val animBox = remember(dto.id) { AnimBox() }
    var animTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(dto.revision) {
        animBox.onNewState(dto.state?.lastMove, dto.state?.pieces ?: emptyList())
        if (!animBox.done) {
            val t0 = withFrameNanos { it }
            while (!animBox.done) {
                withFrameNanos { ts -> animBox.advance((ts - t0) / 1_000_000f); animTick++ }
            }
        } else {
            animTick++
        }
    }

    // 对手端重建请求提示（走查表 §3，0.2 文案极简）
    if (rebuild != null && rebuild.doneId == 0 && rebuild.askSide in 1..2 &&
        rebuild.askSide != dto.mySide && rebuild.vote == null
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("对手请求重建房间", Modifier.weight(1f), fontSize = 12.sp, color = palette.text)
            PrimaryButton("同意换房") { onRebuildVote(true) }
            Spacer(Modifier.width(8.dp))
            SecondaryButton("拒绝") { onRebuildVote(false) }
        }
    }

    // 军旗明棋布阵：本地排布（客户端生成，确认后提交 deploy）——与网页 games.js 同流
    val deployActive = dto.gameKey == "armychess" && dto.status == "setup" && dto.mySide > 0 &&
        !(dto.state?.deployDone?.getOrNull(dto.mySide - 1) ?: false)
    var deployPieces by remember(dto.id) { mutableStateOf<List<ArmyChessDeployPiece>?>(null) }
    var deploySel by remember(dto.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(dto.id, deployActive) {
        if (deployActive && deployPieces == null) deployPieces = armyChessGenDeploy(dto.mySide)
        if (!deployActive) { deployPieces = null; deploySel = null }
    }

    val twoStep = dto.gameKey == "xiangqi" || dto.gameKey == "jungle" || dto.gameKey == "armychess"
    val boardTap: (Int, Int) -> Unit = { x, y ->
        if (deployActive && deployPieces != null) {
            // 布阵期：点两格互换（约束不合法的交换不动）
            val mine = deployPieces!!.any { it.x == x && it.y == y }
            val cur = deploySel
            when {
                !mine -> {}
                cur == null -> { deploySel = x to y }
                cur.first == x && cur.second == y -> deploySel = null
                else -> {
                    val swapped = armyChessTrySwap(dto.mySide, deployPieces!!, cur.first, cur.second, x, y)
                    if (swapped != null) deployPieces = swapped
                    deploySel = null
                }
            }
        } else if (dto.gameKey == "armychess" && dto.state?.mode == "fanqi") {
            // 翻棋：点暗子 = 翻 {x,y}；点己方已翻子 = 两段式走子；阵营未定只翻子
            val st = dto.state   // 由分支条件保证非空
            val piece = st.pieces.firstOrNull { it.x == x && it.y == y }
            val myColor = st.myColor
            when {
                piece != null && piece.hidden -> { selFrom = null; onMove(x, y) }
                !st.settled -> {}
                piece != null && piece.side == myColor -> { selFrom = x to y }
                selFrom != null && canTarget(selFrom!!.first, selFrom!!.second, x, y) -> {
                    val cur = selFrom!!
                    selFrom = null
                    onFromTo(cur.first, cur.second, x, y)
                }
                else -> { selFrom = null }   // 点别处 = 取消选中（Batch D）
            }
        } else if (twoStep) {
            val cur = selFrom
            when {
                isOwnPiece(x, y) -> { selFrom = x to y }
                cur != null && !(cur.first == x && cur.second == y) && canTarget(cur.first, cur.second, x, y) -> {
                    onFromTo(cur.first, cur.second, x, y)
                    selFrom = null
                }
                else -> { selFrom = null }   // 点别处 = 取消选中（Batch D）
            }
        } else {
            onMove(x, y)
        }
    }

    // 本地倒数：拿到快照记基准，之后自己算；tick 只用来触发重组。
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(dto.id, dto.status) {
        if (dto.status != "playing") return@LaunchedEffect
        while (true) { delay(250); tick++ }
    }
    val baseAt = remember(dto.revision) { SystemClock.elapsedRealtime() }
    val elapsed = SystemClock.elapsedRealtime() - baseAt + tick * 0L
    val playing = dto.status == "playing"
    fun remain(v: Long?, running: Boolean): Long? = v?.let { if (running) max(0L, it - elapsed) else it }
    val moveLeft = remain(dto.moveRemainMs, playing)
    val myLeft = remain(dto.myRemainMs, playing && dto.myTurn)
    val oppLeft = remain(dto.oppRemainMs, playing && !dto.myTurn)
    val low = (moveLeft != null && moveLeft < 10_000) || (myLeft != null && myLeft < 60_000)

    // 横屏左右分栏：只对这四款棋类生效（五子棋/黑白棋竖屏即可）
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val split = dto.gameKey == "xiangqi" || dto.gameKey == "jungle" ||
        dto.gameKey == "armychess" || dto.gameKey == "ludo"

    val head: @Composable () -> Unit = {
        PageHeadCard(
            title = gameTitle(dto.gameKey),
            hint = "房间 ${dto.code}",
            actions = {
                SecondaryButton("规则", onClick = onOpenTutorial)
                Spacer(Modifier.width(8.dp))
                SecondaryButton("设置", onClick = onOpenRules)
                Spacer(Modifier.width(8.dp))
                SecondaryButton("回大厅", onClick = onBack)
            },
        )
    }

    if (landscape && split) {
        // 横屏：棋盘居左（按高度限成正方形，绝不裁切），信息/计时/操作居右
        Column(
            Modifier.fillMaxSize().navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        ) {
            head()
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.fillMaxHeight().aspectRatio(1f)) {
                        RoomBoard(
                            dto, n, prefs, boardTap,
                            selected = deploySel ?: selFrom,
                            onLudoPiece = onLudoMove,
                            deployPieces = if (deployActive) deployPieces else null,
                            hints = hintMap,
                            anim = animBox.anim,
                            ghostPiece = animBox.ghostPiece,
                            ghostAlpha = animBox.ghostAlpha,
                        )
                    }
                }
                Column(
                    Modifier.widthIn(min = 220.dp, max = 320.dp).fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    RoomInfo(dto, moveLeft, myLeft, oppLeft, low, showClock)
                    RoomLudoControls(dto, playing, onRoll, onLudoMove)
                    RoomDeployControls(
                        visible = deployActive,
                        onRandom = { deployPieces = armyChessGenDeploy(dto.mySide); deploySel = null },
                        onConfirm = {
                            deployPieces?.let { ps ->
                                onDeploy(Repo.GameDeployReq(pieces = ps.map { p -> Repo.GameDeployPieceReq(p.x, p.y, p.rank) }))
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    RoomRules(dto, n)
                    Spacer(Modifier.height(12.dp))
                    RoomActions(dto, onResign, onRematch, onLeaveAsk)
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { head() }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        RoomInfo(dto, moveLeft, myLeft, oppLeft, low, showClock)
                        Spacer(Modifier.height(8.dp))
                        RoomBoard(
                            dto, n, prefs, boardTap,
                            selected = deploySel ?: selFrom,
                            onLudoPiece = onLudoMove,
                            deployPieces = if (deployActive) deployPieces else null,
                            hints = hintMap,
                            anim = animBox.anim,
                            ghostPiece = animBox.ghostPiece,
                            ghostAlpha = animBox.ghostAlpha,
                        )
                        RoomLudoControls(dto, playing, onRoll, onLudoMove)
                        RoomDeployControls(
                            visible = deployActive,
                            onRandom = { deployPieces = armyChessGenDeploy(dto.mySide); deploySel = null },
                            onConfirm = {
                                deployPieces?.let { ps ->
                                    onDeploy(Repo.GameDeployReq(pieces = ps.map { p -> Repo.GameDeployPieceReq(p.x, p.y, p.rank) }))
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        RoomRules(dto, n)
                        Spacer(Modifier.height(12.dp))
                        RoomActions(dto, onResign, onRematch, onLeaveAsk)
                    }
                }
            }
        }
    }
}

private fun gameTitle(key: String): String = when (key) {
    "gomoku" -> "五子棋"
    "reversi" -> "黑白棋"
    "jungle" -> "斗兽棋"
    "xiangqi" -> "象棋"
    "ludo" -> "飞行棋"
    "armychess" -> "军旗"
    else -> "小游戏"
}

/** 双方/多座 + 状态行（横竖屏共用） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomInfo(
    dto: GameRoomDto,
    moveLeft: Long?,
    myLeft: Long?,
    oppLeft: Long?,
    low: Boolean,
    showClock: Boolean,
) {
    val p = palette
    val plist = dto.players
    if (dto.gameKey == "ludo" && plist != null) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            plist.forEach { pl -> LudoSeatChip(pl, isTurn = dto.status == "playing" && dto.turn == pl.side) }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerPill(
                name = if (dto.hostSide == 1) dto.hostName else dto.guestName.ifBlank { "等朋友" },
                black = true, isMe = dto.mySide == 1, active = dto.status == "playing" && dto.turn == 1,
            )
            Text("vs", fontSize = 11.5.sp, color = p.textWeak, modifier = Modifier.padding(horizontal = 8.dp))
            PlayerPill(
                name = if (dto.hostSide == 1) dto.guestName.ifBlank { "等朋友" } else dto.hostName,
                black = false, isMe = dto.mySide == 2, active = dto.status == "playing" && dto.turn == 2,
            )
        }
    }

    Spacer(Modifier.height(9.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            statusText(dto),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = if (dto.myTurn) p.accent else p.textSub,
            fontWeight = if (dto.myTurn) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (showClock) {
            val parts = buildList {
                if (moveLeft != null && dto.status == "playing") add("本步 " + fmtMs(moveLeft))
                myLeft?.let { add("我 " + fmtMs(it)) }
                oppLeft?.let { add("对手 " + fmtMs(it)) }
            }.joinToString(" · ")
            if (parts.isNotBlank()) {
                Text(
                    parts,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (low) Color(0xFFD93025) else p.accentStrong,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (low) Color(0x1FD93025) else p.accentSoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** 按 gameKey 分发棋盘（不回落到五子棋盘 —— 画错棋盘比不画更糟） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomBoard(
    dto: GameRoomDto,
    n: Int,
    prefs: GameDisplayDto,
    onTap: (Int, Int) -> Unit,
    selected: Pair<Int, Int>? = null,
    onLudoPiece: (Int) -> Unit = {},
    deployPieces: List<ArmyChessDeployPiece>? = null,
    hints: Map<String, List<List<Int>>>? = null,
    anim: cn.anlele.checkin.ui.games.BoardAnim? = null,
    ghostPiece: cn.anlele.checkin.data.GamePieceDto? = null,
    ghostAlpha: Float = 0f,
) {
    val p = palette
    val st = dto.state
    when (dto.gameKey) {
        "gomoku" -> Board(dto = dto, n = n, prefs = prefs, onTap = onTap)
        "reversi" -> {
            val evs = (st?.events ?: emptyList()).map { e ->
                buildMap<String, Any?> { put("t", e.t); put("s", e.s); put("x", e.x); put("y", e.y) }
            }
            ReversiBoard(
                board = buildReversiBoard(evs),
                lastMove = st?.lastMove?.let { it.tx to it.ty },
                showCoord = prefs.showCoord, lastMark = prefs.lastMark,
                mySide = dto.mySide, myTurn = dto.myTurn, onTap = onTap,
                tapConfirm = prefs.tapConfirm, revision = dto.revision,
                lastFlips = reversiLastFlips(evs),
            )
        }
        "jungle" -> JungleBoard(
            pieces = st?.pieces?.map { JunglePiece(it.x, it.y, it.side, it.rank?.jsonPrimitive?.intOrNull ?: 0) } ?: emptyList(),
            lastMove = st?.lastMove?.let { JungleLastMove(it.fx ?: it.tx, it.fy ?: it.ty, it.tx, it.ty) },   // P4-1：与 FanqiRules 哨兵语义对齐
            showCoord = prefs.showCoord, lastMark = prefs.lastMark, onTap = onTap,
            selected = selected, hints = hints, anim = anim,
            ghost = ghostPiece?.let { JunglePiece(it.x, it.y, it.side, it.rank?.jsonPrimitive?.intOrNull ?: 0) },
            ghostAlpha = ghostAlpha,
        )
        "xiangqi" -> XiangqiBoard(
            pieces = st?.pieces?.map { XiangqiPiece(it.x, it.y, it.side, it.type) } ?: emptyList(),
            lastMove = st?.lastMove?.let { XiangqiLastMove(it.fx ?: it.tx, it.fy ?: it.ty, it.tx, it.ty) },   // P4-1：与 FanqiRules 哨兵语义对齐
            inCheck = st?.inCheck ?: false,
            showCoord = prefs.showCoord, lastMark = prefs.lastMark, onTap = onTap,
            selected = selected, hints = hints, anim = anim,
            ghost = ghostPiece?.let { XiangqiPiece(it.x, it.y, it.side, it.type) },
            ghostAlpha = ghostAlpha,
        )
        "ludo" -> LudoBoard(
            pieces = st?.pieces?.map { LudoPiece(it.side, it.p, it.pos) } ?: emptyList(),
            lastRoll = st?.lastRoll,
            movable = st?.movable ?: emptyList(),
            myTurn = dto.myTurn,
            mySide = dto.mySide,
            onTap = onLudoPiece,
        )
        "armychess" -> ArmyChessBoard(
            pieces = if (deployPieces != null) {
                // 布阵期：本地排布的己方军牌 + 对方（服务端视角化）
                deployPieces.map { ArmyChessPiece(it.x, it.y, dto.mySide, it.rank, false) } +
                    (st?.pieces?.filter { it.side != dto.mySide }
                        ?.map { ArmyChessPiece(it.x, it.y, it.side, it.rank?.jsonPrimitive?.contentOrNull, it.hidden) } ?: emptyList())
            } else {
                st?.pieces?.map { ArmyChessPiece(it.x, it.y, it.side, it.rank?.jsonPrimitive?.contentOrNull, it.hidden) } ?: emptyList()
            },
            lastMove = st?.lastMove?.let { ArmyChessLastMove(it.fx ?: it.tx, it.fy ?: it.ty, it.tx, it.ty) },   // P4-1：与 FanqiRules 哨兵语义对齐
            showCoord = prefs.showCoord, lastMark = prefs.lastMark,
            onTap = onTap, selected = selected, hints = hints, anim = anim,
            ghost = ghostPiece?.let { ArmyChessPiece(it.x, it.y, it.side, it.rank?.jsonPrimitive?.contentOrNull, it.hidden) },
            ghostAlpha = ghostAlpha,
        )
        else -> Text("此游戏的棋盘暂不支持", color = p.textSub, fontSize = 14.sp)
    }
}

/** 飞行棋掷骰 / 选机走子（轮到我才显示；可走机号来自服务端 movable） */
@Composable
private fun RoomLudoControls(dto: GameRoomDto, playing: Boolean, onRoll: () -> Unit, onLudoMove: (Int) -> Unit) {
    if (dto.gameKey != "ludo" || !playing || !dto.myTurn) return
    Spacer(Modifier.height(10.dp))
    if (dto.state?.needRoll == true) {
        PrimaryButton("掷骰子", Modifier.fillMaxWidth()) { onRoll() }
    } else {
        val movable = dto.state?.movable ?: emptyList()
        if (movable.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                movable.forEach { pidx ->
                    SecondaryButton("飞机 ${pidx + 1}", Modifier.weight(1f)) { onLudoMove(pidx) }
                }
            }
        }
    }
}

/** 军旗明棋布阵按钮条：随机 / 确认（0.2：无解释文字，错误走 toast） */
@Composable
private fun RoomDeployControls(visible: Boolean, onRandom: () -> Unit, onConfirm: () -> Unit) {
    if (!visible) return
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("随机布阵", Modifier.weight(1f), onClick = onRandom)
        PrimaryButton("确认布阵", Modifier.weight(1f)) { onConfirm() }
    }
}

/** 这一局的规则 chips（建房快照，改了账号设置也不变） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomRules(dto: GameRoomDto, n: Int) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dto.gameKey == "gomoku") {
            AppChip("${n}×${n}")
            AppChip(if (dto.forbidden) "标准规则" else "自由规则")
        }
        if (dto.gameKey == "ludo") {
            AppChip("${dto.players?.size ?: 2} 人")
        }
        if (dto.gameKey == "armychess" && dto.state?.mode == "fanqi") {
            // 翻棋状态事实（与网页状态条同规）：阵营 / 执色 / 剩余暗子
            AppChip(if (dto.state.settled) "翻子或走子" else "阵营未定")
            if (dto.state.myColor > 0) AppChip(if (dto.state.myColor == 1) "你执红" else "你执蓝")
            AppChip("暗子 ${dto.state.darkLeft}")
        }
        if (dto.gameKey == "reversi") {
            AppChip("黑 ${dto.state?.black ?: 0} · 白 ${dto.state?.white ?: 0}")
        }
        if (dto.moveTimeoutSec > 0) AppChip("单步 " + moveTimeoutLabel(dto.moveTimeoutSec))
        if (dto.totalTimeoutSec > 0) AppChip("总局时 ${dto.totalTimeoutSec / 60} 分钟")
    }
}

/** 认输 / 再来一局 / 解散 */
@Composable
private fun RoomActions(dto: GameRoomDto, onResign: () -> Unit, onRematch: () -> Unit, onLeaveAsk: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dto.mySide != 0 && dto.status == "playing") {
            SecondaryButton("认输", danger = true, onClick = onResign)
        }
        if (dto.mySide != 0 && dto.status == "finished") {
            PrimaryButton("再来一局", onClick = onRematch)
            SecondaryButton("收摊", onClick = onLeaveAsk)
        }
        if (dto.status == "waiting" && dto.mySide != 0) {
            SecondaryButton("解散", danger = true, onClick = onLeaveAsk)
        }
    }
}

@Composable
private fun PlayerPill(name: String, black: Boolean, isMe: Boolean, active: Boolean) {
    val p = palette
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isMe) p.accentSoft else p.cardSoft)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(
                    if (black) Color(0xFF23232B) else Color(0xFFF9F6F0),
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (black) Color(0x80FFFFFF) else Color(0x33000000),
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            name.ifBlank { "—" } + if (active) " ●" else "",
            fontSize = 12.5.sp,
            color = p.text,
            fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 飞行棋座位小标：颜色点 + 名字 + 出局/轮到标记 */
@Composable
private fun LudoSeatChip(pl: cn.anlele.checkin.data.LudoPlayerDto, isTurn: Boolean) {
    val p = palette
    val dotColor = when (pl.color) {
        "yellow" -> Color(0xFFF9A825)
        "blue" -> Color(0xFF1565C0)
        "green" -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isTurn) p.accentSoft else p.cardSoft)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(6.dp))
        val label = (pl.name.ifBlank { "空位" }) +
            (if (!pl.active) " ✗" else "") +
            (if (isTurn) " ●" else "") +
            (if (pl.isAi) " AI" else "")
        Text(
            label,
            fontSize = 12.5.sp,
            color = if (pl.active) p.text else p.textWeak,
            fontWeight = if (pl.isMe) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 星位：四角三三点 + 天元。19 路也只点这 5 个，当视觉锚点够用（与网页端同一套）。 */
private fun starPoints(n: Int): List<Pair<Int, Int>> =
    if (n < 13) emptyList()
    else listOf(3 to 3, n - 4 to 3, 3 to n - 4, n - 4 to n - 4, n / 2 to n / 2)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Board(dto: GameRoomDto, n: Int, prefs: GameDisplayDto, onTap: (Int, Int) -> Unit) {
    // 渐变圆子图标（icons-ready 同源，D-5）
    val stoneIcons = rememberPieceIcons("stone", listOf("black", "white"))
    // 新着回弹动画（任务二尾巴）：0.35→1 缓出
    var placeProg by remember(dto.revision) { mutableFloatStateOf(1f) }
    LaunchedEffect(dto.revision) {
        placeProg = 0f
        val t0 = androidx.compose.runtime.withFrameNanos { it }
        while (placeProg < 1f) {
            androidx.compose.runtime.withFrameNanos { ts ->
                placeProg = ((ts - t0) / 1_000_000f / 220f).coerceAtMost(1f)
            }
        }
    }
    // 棋盘与棋子都用固定色，不跟主题：它代表的是一块真实棋盘（浅木底），
    // 五套主题下长得一样。跟着主题变深色的话，黑子会直接糊进背景（macos 主题实测如此）。
    val boardBg = Color(0xFFE9E0CC)
    val gridColor = Color(0xFF8B7F66).copy(alpha = 0.62f)
    val coordColor = Color(0xFF7A6E58)
    val starColor = Color(0xFF6F6551).copy(alpha = 0.85f)
    val canPlay = dto.status == "playing" && dto.myTurn

    // 二次确认的预览格：y * n + x 编码成一个 Int，省一个对象
    var ghost by remember(dto.id) { mutableStateOf<Int?>(null) }
    // 真有棋子落地就把预览圈收掉（自己的落子 / 对手的落子都算）
    LaunchedEffect(dto.moves.size) { ghost = null }
    // 轮次一交出去（或者这局结束了）也要收，否则预览圈会一直挂着不动
    LaunchedEffect(canPlay) { if (!canPlay) ghost = null }

    val haptic = LocalHapticFeedback.current
    // 开了坐标要留一圈字位，留白比平时大
    val padRatio = if (prefs.showCoord) 0.085f else 0.05f

    // 坐标标签在组合期量好，draw 里只摆位置 —— 倒数时每秒重组 4 次，
    // 放到 draw 里每帧 measure 38 次会明显掉帧
    val measurer = rememberTextMeasurer()
    val coordLabels = remember(n, prefs.showCoord) {
        if (!prefs.showCoord) emptyList()
        else {
            val st = TextStyle(fontSize = 9.sp, color = coordColor)
            (0 until n).map { i ->
                measurer.measure(('A' + i).toString(), style = st) to
                measurer.measure((i + 1).toString(), style = st)
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(boardBg)
            .pointerInput(dto.revision, canPlay, prefs.tapConfirm, n) {
                detectTapGestures { off ->
                    if (!canPlay) return@detectTapGestures
                    val w = size.width.toFloat()
                    val pad = w * padRatio
                    if (n < 2) return@detectTapGestures
                    val step = (w - 2 * pad) / (n - 1)
                    val col = ((off.x - pad) / step).roundToInt()
                    val row = ((off.y - pad) / step).roundToInt()
                    if (col < 0 || row < 0 || col >= n || row >= n) return@detectTapGestures
                    // 离交叉点太远当没点中（手机上防误触）
                    if (abs(off.x - (pad + col * step)) > step * 0.6f) return@detectTapGestures
                    if (abs(off.y - (pad + row * step)) > step * 0.6f) return@detectTapGestures
                    if (dto.moves.any { it.x == col && it.y == row }) return@detectTapGestures

                    // 开了「落子确认」：第一下只预览，点同一处第二下才落
                    if (prefs.tapConfirm) {
                        val key = row * n + col
                        if (ghost != key) {
                            ghost = key
                            if (prefs.vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@detectTapGestures
                        }
                        ghost = null
                    }
                    if (prefs.vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTap(col, row)
                }
            },
    ) {
        val w = size.width
        val pad = w * padRatio
        val step = (w - 2 * pad) / (n - 1)
        fun cx(i: Int) = pad + i * step

        // 网格
        for (i in 0 until n) {
            drawLine(gridColor, Offset(cx(0), cx(i)), Offset(cx(n - 1), cx(i)), strokeWidth = 1f)
            drawLine(gridColor, Offset(cx(i), cx(0)), Offset(cx(i), cx(n - 1)), strokeWidth = 1f)
        }
        // 星位
        starPoints(n).forEach { (sx, sy) ->
            if (sx < n && sy < n) {
                drawCircle(starColor, radius = step * 0.09f, center = Offset(cx(sx), cx(sy)))
            }
        }
        // 坐标：上面 A、B…，左边 1、2…
        coordLabels.forEachIndexed { i, pair ->
            val (colLabel, rowLabel) = pair
            drawText(colLabel, topLeft = Offset(cx(i) - colLabel.size.width / 2f, 2f))
            drawText(rowLabel, topLeft = Offset(2f, cx(i) - rowLabel.size.height / 2f))
        }
        // 棋子：渐变圆子整枚图标（icons-ready 同源，D-5 双端同规）
        dto.moves.forEach { m ->
            if (m.x >= n || m.y >= n) return@forEach
            val c = Offset(cx(m.x), cx(m.y))
            val bmp = stoneIcons[if (m.s == 1) "black" else "white"]
            if (bmp != null) {
                val isLast = dto.lastMove?.let { it.x == m.x && it.y == m.y } == true && placeProg < 1f
                val pr = 1f - (1f - placeProg) * (1f - placeProg)
                val side = (step * 0.9f * (if (isLast) 0.35f + 0.65f * pr else 1f)).toInt().coerceAtLeast(2)
                drawImage(
                    bmp,
                    dstSize = IntSize(side, side),
                    dstOffset = IntOffset((c.x - side / 2f).toInt(), (c.y - side / 2f).toInt()),
                )
            }
        }
        // 最后一手标记：棋子中心的高对比小圆点。
        // 不用主题强调色：editorial 等主题的强调色接近黑，套环会被看成棋子的描边。
        if (prefs.lastMark) {
            dto.lastMove?.let { m ->
                if (m.x < n && m.y < n) {
                    drawCircle(
                        if (m.s == 1) Color(0xFFF0EADC) else Color(0xFF2A2A33),
                        radius = step * 0.16f,
                        center = Offset(cx(m.x), cx(m.y)),
                    )
                }
            }
        }
        // 二次确认的预览圈
        ghost?.let { key ->
            val gx = key % n
            val gy = key / n
            val c = Offset(cx(gx), cx(gy))
            val bmpG = stoneIcons[if (dto.mySide == 1) "black" else "white"]
            if (bmpG != null) {
                val side = (step * 0.9f).toInt().coerceAtLeast(2)
                drawImage(
                    bmpG,
                    dstSize = IntSize(side, side),
                    dstOffset = IntOffset((c.x - side / 2f).toInt(), (c.y - side / 2f).toInt()),
                    alpha = if (dto.mySide == 1) 0.45f else 0.55f,
                )
            }
        }
    }
}

// ---------------- 文案 ----------------

private fun statusText(d: GameRoomDto): String {
    if (d.status == "waiting") return "等人进来 —— 把房间码 ${d.code} 发给朋友"
    if (d.status == "finished") {
        val who = when {
            d.winner == 3 -> "和棋"
            d.winner == d.mySide -> "你赢了"
            else -> "你输了"
        }
        val how = when (d.resultReason) {
            "resign" -> "（认输）"
            "draw" -> "（棋盘下满）"
            "overline" -> "（黑方长连判负）"
            "timeout" -> "（超时）"
            else -> ""
        }
        return "$who$how · 共 ${d.moves.size} 手"
    }
    if (d.mySide == 0) return "你在旁观这一局"
    if (d.status == "setup") return "布阵中"   // 军棋布阵期阶段名（与网页同词，0.2 极简）
    val base = if (d.myTurn) "轮到你落子" else "等 ${d.opponentName.ifBlank { "对手" }} 落子"
    return if (d.opponentOnline) base else "$base · 对手好像离开了"
}

/** 毫秒 → 29s / 4:58，与网页端 games.js 的 fmt() 同一套规则 */
private fun fmtMs(ms: Long): String {
    val s = ceil(max(0L, ms) / 1000.0).toInt()
    val m = s / 60
    val r = s % 60
    return if (m > 0) "$m:" + (if (r < 10) "0$r" else "$r") else "${r}s"
}

private fun moveTimeoutLabel(sec: Int): String = when (sec) {
    0 -> "不限"
    60 -> "1 分钟"
    120 -> "2 分钟"
    else -> "$sec 秒"
}

private fun totalTimeoutLabel(sec: Int): String = if (sec == 0) "不限" else "${sec / 60} 分钟"

private fun timeoutSummary(moveSec: Int, totalSec: Int): String {
    val parts = buildList {
        if (moveSec > 0) add("单步 $moveSec 秒")
        if (totalSec > 0) add("总局时 ${totalSec / 60} 分钟")
    }
    return if (parts.isEmpty()) "不限时" else parts.joinToString(" · ")
}
