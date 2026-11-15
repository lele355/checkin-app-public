package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.CouplePageDto
import cn.anlele.checkin.data.LoveNoteDto
import cn.anlele.checkin.data.PeriodPageDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.DateField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.cardBackground
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/** 情侣空间 —— 在一起天数 + 互发小纸条。只有 couples.view 权限能进。 */
@Composable
fun CoupleScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf<CouplePageDto?>(null) }
    var pd by remember { mutableStateOf<PeriodPageDto?>(null) }
    // 今日心情（uid, name, mood?, round）——原小纸条位置（2026-11-02 站主定）
    var todayMoods by remember { mutableStateOf<List<Triple<Int, String, Int?>>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var settingOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            d = Repo.couple()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
        // 经期速览（情侣互见，加载失败不打扰主流程）
        runCatching { pd = Repo.period() }
        // 今日心情速览（失败不打扰）
        runCatching {
            val month = java.time.LocalDate.now().toString().take(7)
            val today = java.time.LocalDate.now().toString()
            val items = Repo.moods(month).items.filter { it.date == today }
            val owners = items.map { it.userId }.distinct()
            todayMoods = owners.mapIndexed { i, u ->
                Triple(u, items.firstOrNull { it.userId == u }?.owner ?: "TA", items.firstOrNull { it.userId == u }?.mood)
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    fun send() {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        sending = true
        scope.launch {
            try {
                Repo.postNote(t)
                text = ""
                load()
            } catch (e: ApiException) {
                vm.toast(e.message)
            } catch (e: Exception) {
                vm.toast("发送失败")
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),

        ) {

            item { PageHeadCard(title = "情侣空间") }
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(AppIcons["couple"], null, Modifier.size(26.dp), tint = p.accent)
                        Spacer(Modifier.height(8.dp))
                        Text(d?.title ?: "我们在一起", fontSize = 15.sp, color = p.textSub)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${d?.daysTogether ?: 0}",
                                fontSize = 32.sp,
                                color = p.accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(" 天", fontSize = 14.sp, color = p.textSub, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Text("从 ${Dates.fmt(d?.startDate, "yyyy年M月d日")} 起", fontSize = 11.5.sp, color = p.textWeak)
                        d?.nextAnniv?.let { a ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${a.title} · 还有 ${a.days} 天",
                                fontSize = 12.sp,
                                color = p.accent,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        TinyAction("修改设置", "settings-2") { settingOpen = true }
                    }
                }
            }

            // 经期速览（情侣互见：双方预测）
            pd?.let { pg ->
                if (pg.predict != null || pg.partnerPredict != null) {
                    item {
                        Text("经期速览", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                    }
                    item {
                        AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                            Column {
                                pg.predict?.let { pr ->
                                    Text(
                                        "我的预计下次：${Dates.dayMonth(pr.nextDate)}（" +
                                            (if (pr.daysLeft >= 0) "还有 ${pr.daysLeft} 天" else "已推迟 ${-pr.daysLeft} 天") + "）",
                                        fontSize = 12.sp, color = p.text,
                                    )
                                }
                                pg.partnerPredict?.let { pp ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${pg.partnerName ?: "TA"} 的预计下次：${Dates.dayMonth(pp.nextDate)}（" +
                                            (if (pp.daysLeft >= 0) "还有 ${pp.daysLeft} 天" else "已推迟 ${-pp.daysLeft} 天") + "）",
                                        fontSize = 12.sp, color = p.textWeak,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 今日心情（2026-11-02 站主定：原小纸条位置）——双方今日贴纸 + 快捷入口
            item {
                Text("今日心情", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
            }
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        todayMoods.forEachIndexed { i, (uid, name, mood) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (mood != null) {
                                    cn.anlele.checkin.ui.components.MoodSticker(mood = mood, round = i == 0, size = 44.dp)
                                } else {
                                    Text("＋", fontSize = 26.sp, color = p.textWeak.copy(alpha = .35f))
                                }
                                Text(name, fontSize = 10.sp, color = p.textWeak)
                            }
                            Spacer(Modifier.width(26.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        SecondaryButton("去记") { vm.openRoute("mood") }
                    }
                }
            }

            // 小纸条入口（2026-11-02 站主定：独立成页）
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), onClick = { vm.openRoute("lovenotes") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💌", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("小纸条", fontSize = 13.sp, color = p.text, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("写一张 →", fontSize = 11.sp, color = p.textWeak)
                    }
                }
            }
        }
    }

    if (settingOpen) {
        CoupleSettingSheet(vm, d, onClose = { settingOpen = false }, onSaved = {
            settingOpen = false
            scope.launch { load() }
        })
    }
}

/** 小纸条气泡圆角。不能用 p.cardShape()：editorial 主题下它是直角，配上实色底就成一个大方块 */
private val NoteBubbleShape = RoundedCornerShape(18.dp)

@Composable
private fun NoteBubble(n: LoveNoteDto, onDelete: () -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (n.mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (n.mine) Alignment.End else Alignment.Start) {
            Text(
                "${n.from} · ${Dates.fmt(n.createdAt, "M月d日 HH:mm")}",
                fontSize = 10.5.sp,
                color = p.textWeak,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            // 自己那条用「浅强调底 + 深强调字」：以前平涂 p.accent，editorial 下接近纯黑，
            // 再配直角卡片形状，看起来就是一个大黑方块。
            val bubbleMod = Modifier
                .fillMaxWidth(0.82f)
                .then(
                    if (n.mine) Modifier.background(p.accentSoft, NoteBubbleShape)
                    else Modifier.cardBackground(p, NoteBubbleShape)
                )
                .padding(horizontal = 13.dp, vertical = 10.dp)
            Box(bubbleMod) {
                Column {
                    Text(
                        n.content,
                        fontSize = 13.5.sp,
                        color = if (n.mine) p.accentStrong else p.text,
                        lineHeight = 21.sp,
                    )
                    if (n.mine) {
                        Spacer(Modifier.height(4.dp))
                        TinyAction("删除", null, danger = false, onClick = onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoupleSettingSheet(vm: AppVm, d: CouplePageDto?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(d?.title ?: "我们在一起") }
    var start by remember { mutableStateOf(d?.startDate ?: Dates.today()) }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = "在一起的日子",
        onClose = onClose,
        saving = saving,
        onSave = {
            if (title.isBlank()) { vm.toast("标题不能为空"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    Repo.saveCouple(Repo.CoupleReq(title.trim(), start))
                    onSaved()
                } catch (e: Exception) {
                    vm.toast("保存失败")
                } finally {
                    saving = false
                }
            }
        },
    ) {
        AppField("标题", title, { title = it }, placeholder = "A & B")
        Spacer(Modifier.height(12.dp))
        DateField("在一起的第一天", start, { start = it })
    }
}
