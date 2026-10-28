package cn.anlele.checkin.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.GameLobbyDto
import cn.anlele.checkin.data.GameLobbyItemDto
import cn.anlele.checkin.data.GameRoomDto
import cn.anlele.checkin.data.GameSettingsDto
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

/** 每方总用时可选值（秒），0 = 不限 */
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
fun GamesScreen(vm: AppVm) {
    val scope = rememberCoroutineScope()
    var lobby by remember { mutableStateOf(GameLobbyDto()) }
    var settings by remember { mutableStateOf(GameSettingsDto()) }
    var loading by remember { mutableStateOf(true) }
    var code by remember { mutableStateOf("") }
    var room by remember { mutableStateOf<GameRoomDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    // 大厅里「这一局」的规则：默认跟账号设置走，建房前可以临时改（只影响这一盘）
    var newSize by remember { mutableStateOf(15) }
    var newForbidden by remember { mutableStateOf(false) }

    // 设置对象变了（首次加载完 / 刚保存完）就把大厅选择重置回账号默认。
    // 用 data class 当 key：值没变就不会重启，用户在楼下挑好的盘面不会被无故冲掉。
    LaunchedEffect(settings) {
        newSize = settings.size
        newForbidden = settings.forbidden
    }

    suspend fun refreshLobby() {
        try {
            lobby = Repo.games()
            settings = lobby.settings      // 服务端顺手带回来的我的设置，省一次往返
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshLobby() }

    // 对局轮询：只有进房间后才跑；离开房间（room = null）就退出循环
    LaunchedEffect(room?.id) {
        while (true) {
            val cur = room ?: break
            delay(if (cur.status == "finished") 3000 else 1200)
            val latest = try {
                Repo.game(cur.id)
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
                val d = Repo.createGame(side, size = newSize, forbidden = newForbidden)
                room = Repo.game(d.id)
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
                room = Repo.game(d.id)
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
                runCatching { Repo.joinGame(id) }      // 已经在局中会幂等返回
                room = Repo.game(id)
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
                room = Repo.gameMove(cur.id, x, y)
            } catch (e: ApiException) {
                vm.toast(e.message)
                runCatching { room = Repo.game(cur.id) }   // 拉一次真相，别停在错误局面
            } catch (e: Exception) {
                vm.toast("落子失败，检查网络")
            }
        }
    }

    fun resign() {
        val cur = room ?: return
        scope.launch {
            try {
                room = Repo.gameResign(cur.id)
            } catch (e: Exception) {
                vm.toast(e.message ?: "操作失败")
            }
        }
    }

    fun rematch() {
        val cur = room ?: return
        scope.launch {
            try {
                room = Repo.gameRematch(cur.id)
            } catch (e: Exception) {
                vm.toast(e.message ?: "操作失败")
            }
        }
    }

    fun backToLobby() {
        room = null
        scope.launch { refreshLobby() }
    }

    fun leave() {
        val cur = room ?: return
        scope.launch {
            runCatching { Repo.gameLeave(cur.id) }
            backToLobby()
        }
    }

    fun saveSettings(next: GameSettingsDto) {
        scope.launch {
            try {
                settings = Repo.saveGameSettings(next)
                settingsOpen = false
                vm.toast("设置已保存")
            } catch (e: Exception) {
                vm.toast(e.message ?: "保存失败")
            }
        }
    }

    val cur = room
    if (cur == null) {
        LobbyContent(
            lobby = lobby,
            settings = settings,
            newSize = newSize,
            newForbidden = newForbidden,
            loading = loading,
            code = code,
            busy = busy,
            onCodeChange = { code = it.take(6) },
            onSizeChange = { newSize = it },
            onForbiddenChange = { newForbidden = it },
            onCreate = ::create,
            onJoinByCode = ::joinByCode,
            onJoin = ::join,
            onRefresh = { scope.launch { refreshLobby() } },
            onOpenSettings = { settingsOpen = true },
            onLeave = { id -> scope.launch { runCatching { Repo.gameLeave(id) }; refreshLobby() } },
        )
    } else {
        RoomContent(
            dto = cur,
            prefs = settings,
            onMove = ::move,
            onResign = { confirmResign = true },
            onRematch = ::rematch,
            onBack = ::backToLobby,
            onLeaveAsk = { confirmLeave = true },
            onOpenSettings = { settingsOpen = true },
        )
    }

    if (settingsOpen) {
        GameSettingsSheet(
            initial = settings,
            onDismiss = { settingsOpen = false },
            onSave = ::saveSettings,
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
}

// ---------------- 大厅 ----------------

@Composable
private fun LobbyContent(
    lobby: GameLobbyDto,
    settings: GameSettingsDto,
    newSize: Int,
    newForbidden: Boolean,
    loading: Boolean,
    code: String,
    busy: Boolean,
    onCodeChange: (String) -> Unit,
    onSizeChange: (Int) -> Unit,
    onForbiddenChange: (Boolean) -> Unit,
    onCreate: (Int) -> Unit,
    onJoinByCode: () -> Unit,
    onJoin: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onLeave: (Int) -> Unit,
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
                hint = "建一盘，把房间码发给朋友，他输码就能坐下。",
                actions = {
                    SecondaryButton("设置", onClick = onOpenSettings)
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton("刷新", onClick = onRefresh)
                },
            )
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                // AppCard 的 content 是 Box —— 多个子项必须自己包 Column，否则全叠在左上角
                Column(Modifier.fillMaxWidth()) {
                    SectionTitle("新开一盘")
                    Spacer(Modifier.height(10.dp))

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
                    Text(
                        if (newForbidden) "标准规则：黑方连成六个以上判负（白方长连仍算赢）。"
                        else "自由规则：超过五连也算赢，没有禁手。",
                        fontSize = 11.sp,
                        color = p.textWeak,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (settings.moveTimeoutSec > 0 || settings.totalTimeoutSec > 0) {
                        Text(
                            "限时按你的默认设置：" + timeoutSummary(settings.moveTimeoutSec, settings.totalTimeoutSec),
                            fontSize = 11.sp,
                            color = p.textWeak,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("我先手", Modifier.weight(1f), icon = "plus", enabled = !busy) { onCreate(1) }
                        SecondaryButton("我后手", Modifier.weight(1f), enabled = !busy) { onCreate(2) }
                    }
                    Spacer(Modifier.height(14.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameSettingsSheet(
    initial: GameSettingsDto,
    onDismiss: () -> Unit,
    onSave: (GameSettingsDto) -> Unit,
) {
    val p = palette
    // 草稿语义：改动只落在本地，点「保存」才提交
    var d by remember(initial) { mutableStateOf(initial) }

    PickerSheetShell(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 22.dp)) {
            Text("小游戏设置", fontSize = 16.sp, color = p.text, fontWeight = FontWeight.SemiBold)
            Text(
                "盘面、禁手、限时会在建房时写进房间，已经开打的局不受影响；其余几项只改你自己的画面。",
                fontSize = 11.5.sp,
                color = p.textWeak,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 5.dp),
            )

            OptionChips(
                title = "盘面",
                options = BOARD_SIZES.map { "$it 路" },
                selectedIndex = BOARD_SIZES.indexOf(d.size).coerceAtLeast(0),
                onPick = { d = d.copy(size = BOARD_SIZES[it]) },
            )
            OptionChips(
                title = "规则",
                options = listOf("自由规则", "标准规则"),
                selectedIndex = if (d.forbidden) 1 else 0,
                onPick = { d = d.copy(forbidden = it == 1) },
            )
            Text(
                "标准规则下黑方连成六个以上判负（白方长连仍算赢）。",
                fontSize = 11.sp,
                color = p.textWeak,
                modifier = Modifier.padding(top = 4.dp),
            )
            OptionChips(
                title = "单步限时",
                options = MOVE_TIMEOUTS.map { moveTimeoutLabel(it) },
                selectedIndex = MOVE_TIMEOUTS.indexOf(d.moveTimeoutSec).coerceAtLeast(0),
                onPick = { d = d.copy(moveTimeoutSec = MOVE_TIMEOUTS[it]) },
            )
            OptionChips(
                title = "每方总用时",
                options = TOTAL_TIMEOUTS.map { totalTimeoutLabel(it) },
                selectedIndex = TOTAL_TIMEOUTS.indexOf(d.totalTimeoutSec).coerceAtLeast(0),
                onPick = { d = d.copy(totalTimeoutSec = TOTAL_TIMEOUTS[it]) },
            )

            Spacer(Modifier.height(10.dp))
            SwitchRow(
                title = "落子二次确认",
                desc = "第一下只预览，再点同一处才落子。",
                checked = d.tapConfirm,
                onChange = { d = d.copy(tapConfirm = it) },
            )
            SwitchRow(
                title = "显示坐标",
                desc = "棋盘边上标 A、B… 与 1、2…",
                checked = d.showCoord,
                onChange = { d = d.copy(showCoord = it) },
            )
            SwitchRow(
                title = "标记最后一手",
                desc = "在最后落下的那颗棋上点一个小圆点。",
                checked = d.lastMark,
                onChange = { d = d.copy(lastMark = it) },
            )
            SwitchRow(
                title = "落子震动",
                desc = "手机上落子时轻震一下。",
                checked = d.vibrate,
                onChange = { d = d.copy(vibrate = it) },
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("保存", Modifier.weight(1f)) { onSave(d) }
                SecondaryButton(
                    "恢复默认",
                    Modifier.weight(1f),
                ) { d = GameSettingsDto() }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = p.text)
            Text(desc, fontSize = 11.sp, color = p.textWeak, lineHeight = 15.sp)
        }
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
    prefs: GameSettingsDto,
    onMove: (Int, Int) -> Unit,
    onResign: () -> Unit,
    onRematch: () -> Unit,
    onBack: () -> Unit,
    onLeaveAsk: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val p = palette
    val n = if (dto.size > 0) dto.size else 15
    val showClock = dto.moveTimeoutSec > 0 || dto.totalTimeoutSec > 0

    // 本地倒数：不靠服务端每 250ms 推一次，拿到快照那一刻记下基准，之后自己算。
    // tick 只是用来触发重组，真正的数值由 baseAt + 当前时刻算出来。
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(dto.id, dto.status) {
        if (dto.status != "playing") return@LaunchedEffect
        while (true) {
            delay(250)
            tick++
        }
    }
    val baseAt = remember(dto.revision) { SystemClock.elapsedRealtime() }
    // tick 只用来建立重组订阅；时间一律现取钟表，别拿 tick * 间隔 攒（会越走越偏）。
    // 乘 0L 是为了让编译器别把这次读取优化掉，同时不留「未使用变量」告警。
    val elapsed = SystemClock.elapsedRealtime() - baseAt + tick * 0L

    // 服务端只给「该走棋的一方」计时（没轮到你就不扣），所以这里也不能三方一起倒数，
    // 否则对手/自己的数字会先掉下去再被下一次轮询拉回来，看着像在乱跳。
    val playing = dto.status == "playing"
    fun remain(v: Long?, running: Boolean): Long? = v?.let { if (running) max(0L, it - elapsed) else it }
    val moveLeft = remain(dto.moveRemainMs, playing)
    val myLeft = remain(dto.myRemainMs, playing && dto.myTurn)
    val oppLeft = remain(dto.oppRemainMs, playing && !dto.myTurn)
    val low = (moveLeft != null && moveLeft < 10_000) || (myLeft != null && myLeft < 60_000)

    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeadCard(
                title = "五子棋",
                hint = "房间 ${dto.code}",
                actions = {
                    SecondaryButton("设置", onClick = onOpenSettings)
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton("回大厅", onClick = onBack)
                },
            )
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                // AppCard 的 content 是 Box —— 多个子项必须自己包 Column，否则全叠在左上角
                Column(Modifier.fillMaxWidth()) {
                    // 双方 + 状态
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerPill(
                            name = if (dto.hostSide == 1) dto.hostName else dto.guestName.ifBlank { "等朋友" },
                            black = true,
                            isMe = dto.mySide == 1,
                            active = dto.status == "playing" && dto.turn == 1,
                        )
                        Text("vs", fontSize = 11.5.sp, color = p.textWeak, modifier = Modifier.padding(horizontal = 8.dp))
                        PlayerPill(
                            name = if (dto.hostSide == 1) dto.guestName.ifBlank { "等朋友" } else dto.hostName,
                            black = false,
                            isMe = dto.mySide == 2,
                            active = dto.status == "playing" && dto.turn == 2,
                        )
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
                            // 剩余时间：本步 / 我 / 对手，单位的取舍跟网页端一致
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

                    Spacer(Modifier.height(8.dp))
                    Board(dto = dto, n = n, prefs = prefs, onTap = onMove)

                    Spacer(Modifier.height(10.dp))
                    // 这一局的规则（建房时定下的，改了账号设置也不会变）
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppChip("${n}×${n}")
                        AppChip(if (dto.forbidden) "标准规则" else "自由规则")
                        if (dto.moveTimeoutSec > 0) AppChip("单步 " + moveTimeoutLabel(dto.moveTimeoutSec))
                        if (dto.totalTimeoutSec > 0) AppChip("总用时 ${dto.totalTimeoutSec / 60} 分钟")
                    }

                    Spacer(Modifier.height(12.dp))
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

                    Spacer(Modifier.height(10.dp))
                    Text(
                        buildString {
                            append("点交叉点落子。先连成五个（含斜线）就赢。")
                            if (dto.forbidden) append("标准规则：黑方连成六个以上判负。")
                            else append("超过五连也算赢（自由规则）。")
                            if (prefs.tapConfirm) append("已开「落子确认」：第一下预览，再点同一处才落子。")
                        },
                        fontSize = 11.5.sp,
                        color = p.textWeak,
                        lineHeight = 17.sp,
                    )
                }
            }
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

/** 星位：四角三三点 + 天元。19 路也只点这 5 个，当视觉锚点够用（与网页端同一套）。 */
private fun starPoints(n: Int): List<Pair<Int, Int>> =
    if (n < 13) emptyList()
    else listOf(3 to 3, n - 4 to 3, 3 to n - 4, n - 4 to n - 4, n / 2 to n / 2)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Board(dto: GameRoomDto, n: Int, prefs: GameSettingsDto, onTap: (Int, Int) -> Unit) {
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
        // 棋子
        dto.moves.forEach { m ->
            if (m.x >= n || m.y >= n) return@forEach
            val c = Offset(cx(m.x), cx(m.y))
            if (m.s == 1) {
                drawCircle(Color(0xFF24242C), radius = step * 0.42f, center = c)
            } else {
                drawCircle(Color(0xFFFBF8F2), radius = step * 0.42f, center = c)
                drawCircle(Color(0x38000000), radius = step * 0.42f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = step * 0.05f))
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
            if (dto.mySide == 1) {
                drawCircle(Color(0xFF24242C).copy(alpha = 0.45f), radius = step * 0.42f, center = c)
            } else {
                drawCircle(Color(0xFFFBF8F2).copy(alpha = 0.55f), radius = step * 0.42f, center = c)
                drawCircle(Color(0x4D000000), radius = step * 0.42f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = step * 0.05f))
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
        if (totalSec > 0) add("总用时 ${totalSec / 60} 分钟")
    }
    return if (parts.isEmpty()) "不限时" else parts.joinToString(" · ")
}
