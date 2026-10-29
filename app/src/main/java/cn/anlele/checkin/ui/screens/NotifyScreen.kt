package cn.anlele.checkin.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.push.AlarmScheduler
import cn.anlele.checkin.push.NotifyHelper
import cn.anlele.checkin.push.NotifyResync
import cn.anlele.checkin.push.NotifySettingsDto
import cn.anlele.checkin.push.NotifySettingsReq
import cn.anlele.checkin.push.PermCheck
import cn.anlele.checkin.push.PermState
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.GroupHead
import cn.anlele.checkin.ui.components.HeadButton
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SiteSwitch
import cn.anlele.checkin.ui.components.staggerItem
import cn.anlele.checkin.ui.components.TimeRangeRow
import cn.anlele.checkin.ui.components.TimeRow
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

private val LEAD_OPTIONS = listOf(5, 10, 30)

/**
 * 通知设置。
 *
 * 设计要点：
 * - 顶部先做「权限体检」—— 本地通知收不到，九成是系统层面这四项没开，不是 App 的问题；
 * - 所有改动只改本地 draft，底部一次保存（弱网不会出现「改 5 项只存上 3 项」）；
 * - 「测试通知」立刻发一条，让人当场确认权限链路是通的。
 */
@Composable
fun NotifyScreen(vm: AppVm) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var perms by remember { mutableStateOf(PermCheck.read(ctx)) }
    var draft by remember { mutableStateOf<NotifySettingsDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var scheduled by remember { mutableStateOf(0) }
    var showGuide by remember { mutableStateOf(false) }

    // 从系统设置页返回后立刻重新体检（否则用户开完权限回来状态还是"未开启"）
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        perms = PermCheck.read(ctx)
        scheduled = AlarmScheduler.scheduledCount(ctx)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        perms = PermCheck.read(ctx)
        if (!granted) vm.toast("没有通知权限，到点的提醒不会显示")
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !perms.notifyOk) {
            // 系统还愿意弹就弹，弹不出（被拒两次）再引导去设置
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PermCheck.openNotifySettings(ctx)
        }
    }

    LaunchedEffect(Unit) {
        try {
            val s = Repo.notifySettings()
            draft = s
        } catch (e: Exception) {
            draft = NotifySettingsDto()
            vm.toast("设置加载失败，先显示默认值")
        } finally {
            loading = false
            scheduled = AlarmScheduler.scheduledCount(ctx)
        }
    }

    val d = draft ?: NotifySettingsDto()
    val on = d.enabled

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 页头 ----------
        staggerItem(0) {
            PageHeadCard(
                title = "通知设置",
                hint = "下面「手机权限」没开的那几项，会让所有开关失效。",
                actions = {
                    HeadButton("测试", icon = "bell") {
                        NotifyHelper.showTest(ctx)
                        vm.toast(
                            if (NotifyHelper.hasPermission(ctx)) "已发送测试通知"
                            else "没有通知权限，先在上面打开"
                        )
                    }
                },
            )
        }

        // ---------- 权限体检 ----------
        staggerItem(1) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("手机权限")
                    PermRow(
                        icon = "bell",
                        title = "通知权限",
                        state = perms.notifyOk,
                        stateText = if (perms.notifyOk) "已开启" else "未开启",
                        actionText = "去开启",
                    ) { askNotificationPermission() }
                    PermRow(
                        icon = "calendar",
                        title = "闹钟和提醒",
                        state = perms.exactOk,
                        stateText = if (perms.exactOk) "已开启" else "未开启",
                        actionText = "去开启",
                    ) { PermCheck.openExactAlarm(ctx) }
                    PermRow(
                        icon = "wallet",
                        title = "电池使用 · 无限制",
                        state = perms.batteryOk,
                        stateText = if (perms.batteryOk) "无限制" else "受限制",
                        actionText = "去设置",
                    ) { PermCheck.openBattery(ctx) }
                    PermRow(
                        icon = "settings-2",
                        title = "自启动 · ${perms.brand}",
                        state = null,
                        stateText = "需手动确认",
                        actionText = "去设置",
                    ) { PermCheck.openAutoStart(ctx) }
                    Text(
                        perms.autoStartHint,
                        fontSize = 11.sp,
                        color = p.textWeak,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // ---------- 闹钟自检 ----------
        staggerItem(0) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("闹钟自检", fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.Medium)
                        Text(
                            "点一下，5 秒后应当弹出「闹钟链路自检」通知。这条走的是真实提醒的完整链路。",
                            fontSize = 11.sp, color = p.textWeak, lineHeight = 16.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    TinyAction("5 秒后响", "bell") {
                        if (!AlarmScheduler.canExact(ctx)) {
                            vm.toast("先打开上面的「闹钟和提醒」权限")
                            PermCheck.openExactAlarm(ctx)
                        } else {
                            val ok = AlarmScheduler.scheduleSelfTest(ctx, 5)
                            vm.toast(if (ok) "已排好，5 秒后响" else "排程失败，检查「闹钟和提醒」权限")
                        }
                    }
                }
            }
        }

        // ---------- 总开关 ----------
        staggerItem(2) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("总开关")
                    SettingRow("接收提醒", "关掉后所有提醒都不再弹出（包括日程）") {
                        SiteSwitch(checked = d.enabled) { v ->
                            draft = d.copy(enabled = v)
                            if (!v) scope.launch { NotifyResync.clearAll(ctx) }
                        }
                    }
                    SettingRow("免打扰", "这期间到点的提醒顺延到结束后；日程不补") {
                        SiteSwitch(
                            checked = d.dnd.enabled,
                            onChange = { v -> draft = d.copy(dnd = d.dnd.copy(enabled = v)) },
                            enabled = on,
                        )
                    }
                    Box(Modifier.alpha(if (on && d.dnd.enabled) 1f else .45f)) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            TimeRangeRow(
                                label = "时段",
                                startMinute = d.dnd.startMinute,
                                endMinute = d.dnd.endMinute,
                                enabled = on && d.dnd.enabled,
                            ) { s, e -> draft = d.copy(dnd = d.dnd.copy(startMinute = s, endMinute = e)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // ---------- 日程 ----------
        staggerItem(3) {
            Column(Modifier.alpha(if (on) 1f else .45f)) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                    Column {
                        GroupHead("日程")
                        SettingRow("日程提醒", "开始前按下面的提前量提醒") {
                            SiteSwitch(checked = d.schedule.enabled, onChange = { v ->
                                draft = d.copy(schedule = d.schedule.copy(enabled = v))
                            }, enabled = on)
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("提前", fontSize = 13.sp, color = p.textSub, modifier = Modifier.width(46.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    LEAD_OPTIONS.forEach { lead ->
                                        AppChip(
                                            "$lead 分钟",
                                            selected = d.schedule.leads.contains(lead),
                                        ) {
                                            if (!on || !d.schedule.enabled) return@AppChip
                                            val next = if (d.schedule.leads.contains(lead))
                                                d.schedule.leads - lead else d.schedule.leads + lead
                                            draft = d.copy(schedule = d.schedule.copy(leads = next.sorted()))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TimeRow(
                                "全天日程提醒时刻",
                                d.schedule.allDayMinute,
                                enabled = on && d.schedule.enabled,
                            ) { m -> draft = d.copy(schedule = d.schedule.copy(allDayMinute = m)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ---------- 每天 ----------
        staggerItem(4) {
            Column(Modifier.alpha(if (on) 1f else .45f)) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                    Column {
                        GroupHead("每天")
                        SettingRow("每日早报", "今天几个日程、打卡进度、最近纪念日") {
                            SiteSwitch(checked = d.digest.enabled, onChange = { v -> draft = d.copy(digest = d.digest.copy(enabled = v)) }, enabled = on)
                        }
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            TimeRow("早报时间", d.digest.minute, enabled = on && d.digest.enabled) { m ->
                                draft = d.copy(digest = d.digest.copy(minute = m))
                            }
                        }
                        SettingRow("打卡提醒", "当天已全部打卡就不打扰") {
                            SiteSwitch(checked = d.habit.enabled, onChange = { v -> draft = d.copy(habit = d.habit.copy(enabled = v)) }, enabled = on)
                        }
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            TimeRow("打卡提醒时间", d.habit.minute, enabled = on && d.habit.enabled) { m ->
                                draft = d.copy(habit = d.habit.copy(minute = m))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        // ---------- 特别的日子 ----------
        staggerItem(5) {
            Column(Modifier.alpha(if (on) 1f else .45f)) {
                AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                    Column {
                        GroupHead("特别的日子")
                        SettingRow("纪念日当天", "每年重复的会按今年的日期提醒") {
                            SiteSwitch(checked = d.anniversary.enabled, onChange = { v ->
                                draft = d.copy(anniversary = d.anniversary.copy(enabled = v))
                            }, enabled = on)
                        }
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            TimeRow("纪念日提醒时间", d.anniversary.minute, enabled = on && d.anniversary.enabled) { m ->
                                draft = d.copy(anniversary = d.anniversary.copy(minute = m))
                            }
                        }
                        SettingRow("经期预测", "按最近 3 个周期的平均值推算") {
                            SiteSwitch(checked = d.period.enabled, onChange = { v -> draft = d.copy(period = d.period.copy(enabled = v)) }, enabled = on)
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("提前", fontSize = 13.sp, color = p.textSub, modifier = Modifier.width(46.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    (0..7).forEach { n ->
                                        if (n in listOf(0, 1, 2, 3, 5, 7)) {
                                            AppChip("$n 天", selected = d.period.daysBefore == n) {
                                                if (!on || !d.period.enabled) return@AppChip
                                                draft = d.copy(period = d.period.copy(daysBefore = n))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        // ---------- 新消息说明 ----------
        staggerItem(6) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("新消息")
                    SettingRow("首页未读提示", "新邮件、留言板、小纸条不弹系统通知，打开 App 时提示") {
                        SiteSwitch(checked = d.unreadHint, onChange = { v -> draft = d.copy(unreadHint = v) })
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // ---------- 保存 ----------
        staggerItem(7) {
            Column {
                if (scheduled > 0) {
                    Text(
                        "本机已排 $scheduled 条提醒",
                        fontSize = 11.sp,
                        color = p.textWeak,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                PrimaryButton("保存设置", Modifier.fillMaxWidth(), loading = saving) {
                    if (saving) return@PrimaryButton
                    saving = true
                    scope.launch {
                        try {
                            val saved = Repo.saveNotifySettings(
                                NotifySettingsReq(
                                    enabled = d.enabled,
                                    dnd = d.dnd,
                                    schedule = d.schedule,
                                    digest = d.digest,
                                    habit = d.habit,
                                    anniversary = d.anniversary,
                                    period = d.period,
                                    unreadHint = d.unreadHint,
                                )
                            )
                            draft = saved
                            vm.toast("已保存")
                            if (saved.enabled) NotifyResync.trigger(ctx) else NotifyResync.clearAll(ctx)
                        } catch (e: Exception) {
                            vm.toast("保存失败：${e.message ?: "检查网络"}")
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        }

        // ---------- 机型说明 ----------
        staggerItem(8) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { showGuide = !showGuide },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("国产手机收不到提醒？", fontSize = 13.sp, color = p.textSub, modifier = Modifier.weight(1f))
                        Icon(
                            AppIcons[if (showGuide) "chevron-up" else "chevron-down"], null,
                            Modifier.size(14.dp), tint = p.textWeak,
                        )
                    }
                    if (showGuide) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "小米 / Redmi：应用信息 → 自启动开；省电策略 → 无限制；最近任务里长按卡片加锁\n" +
                                "华为 / 荣耀：设置 → 电池 → 应用启动管理 → 手动管理 → 自启动/关联启动/后台活动 全开\n" +
                                "OPPO / 一加 / realme：应用 → 电池 → 允许后台活动；允许自启动；最近任务加锁\n" +
                                "vivo / iQOO：自启动 + 后台高耗电允许\n" +
                                "三星：电池 → 无限制；后台使用限制 → 加进「永不休眠的应用」\n\n" +
                                "系统大版本更新后这些开关可能被重置，发现不响了先回来复查一遍。",
                            fontSize = 11.5.sp,
                            color = p.textWeak,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
    }
}

/** 权限体检的一行 */
@Composable
private fun PermRow(
    icon: String,
    title: String,
    state: Boolean?,
    stateText: String,
    actionText: String,
    onAction: () -> Unit,
) {
    val p = palette
    val tone = when (state) {
        true -> Color(0xFF1E7B45)
        false -> Color(0xFFC0392B)
        null -> p.textWeak
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).background(p.cardSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons[icon], null, Modifier.size(15.dp), tint = p.textSub) }
        Spacer(Modifier.width(11.dp))
        Text(title, fontSize = 13.5.sp, color = p.text, modifier = Modifier.weight(1f))
        Text(
            stateText,
            fontSize = 11.5.sp,
            color = tone,
            modifier = Modifier.background(p.cardSoft, chipShape()).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(8.dp))
        TinyAction(actionText, null, onClick = onAction)
    }
}

/** 设置页通用行：标题 + 说明 + 右侧控件 */
@Composable
private fun SettingRow(
    title: String,
    desc: String? = null,
    trailing: @Composable () -> Unit,
) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = p.text, fontWeight = FontWeight.Medium)
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 11.sp, color = p.textWeak, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}
