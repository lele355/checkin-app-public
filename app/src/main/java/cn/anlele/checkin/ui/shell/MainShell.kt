package cn.anlele.checkin.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.MeDto
import cn.anlele.checkin.data.NavItemDto
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.BannerBar
import cn.anlele.checkin.ui.components.BannerTone
import cn.anlele.checkin.ui.components.DotBadge
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.screens.AccountSwitchSheet
import cn.anlele.checkin.ui.screens.AnniversaryScreen
import cn.anlele.checkin.ui.screens.AiScreen
import cn.anlele.checkin.ui.screens.BoardScreen
import cn.anlele.checkin.ui.screens.CoupleScreen
import cn.anlele.checkin.ui.screens.CoursesScreen
import cn.anlele.checkin.ui.screens.DashboardScreen
import cn.anlele.checkin.ui.screens.EmailScreen
import cn.anlele.checkin.ui.screens.ExpensesScreen
import cn.anlele.checkin.ui.screens.ExportScreen
import cn.anlele.checkin.ui.screens.GamesScreen
import cn.anlele.checkin.ui.screens.HabitsScreen
import cn.anlele.checkin.ui.screens.InvitesScreen
import cn.anlele.checkin.ui.components.ChangelogSheet
import cn.anlele.checkin.ui.components.UpdateSheet
import cn.anlele.checkin.ui.screens.MeScreen
import cn.anlele.checkin.ui.screens.SettingsScreen
import cn.anlele.checkin.ui.screens.MemoScreen
import cn.anlele.checkin.ui.screens.NotifyScreen
import cn.anlele.checkin.ui.screens.PeriodScreen
import cn.anlele.checkin.ui.screens.RemindersScreen
import cn.anlele.checkin.ui.screens.RolesScreen
import cn.anlele.checkin.ui.screens.ScheduleScreen
import cn.anlele.checkin.ui.screens.StatsScreen
import cn.anlele.checkin.ui.screens.UsersScreen
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.VersionDto
import cn.anlele.checkin.push.NotifyHelper
import cn.anlele.checkin.ui.components.NoticeBar
import cn.anlele.checkin.update.UpdateKit

/**
 * 主框架：侧栏抽屉 + 顶栏 + 页面容器。
 *
 * 结构与网站一致（2026-10-20 版）：没有底部导航栏 ——「外观」在顶栏调色板、
 * 「我的」在侧栏底部，手机屏幕上不额外占那 52px。
 *
 * 路由是自研的轻量栈（route + backStack）：
 * 侧栏切换会压栈，物理返回键先出栈、栈空再退出 App —— 这是「像 App」最关键的一条。
 */
@Composable
fun MainShell(vm: AppVm, me: MeDto) {
    val p = palette
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }

    var route by remember { mutableStateOf(vm.pendingRoute ?: "workbench") }
    val stack = remember { mutableStateListOf<String>() }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    var showPrefs by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    // 模块内返回回调（修复 2）：游戏模块注册后，系统返回/顶栏返回逐级降层。
    // 默认 null = 旧行为（直接弹路由栈），其它模块零影响。
    val moduleBackState = remember { mutableStateOf<(() -> Boolean)?>(null) }

    // ---------- 权限 / 更新：顶部提醒 ----------
    val ctx = LocalContext.current
    var permMissing by remember { mutableStateOf(!NotifyHelper.hasPermission(ctx)) }
    var updateInfo by remember { mutableStateOf<VersionDto?>(null) }
    var permDismissed by remember { mutableStateOf(false) }
    var updateDismissed by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permMissing = !NotifyHelper.hasPermission(ctx) }

    // 首次进 App 主动问一次通知权限（只问一次，之后靠顶部提醒引导）
    LaunchedEffect(Unit) {
        if (!vm.permAsked && !NotifyHelper.hasPermission(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vm.permAsked = true
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 从系统设置回来 / 切后台再回来，都要重新读权限 —— 用户可能刚在里面打开
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permMissing = !NotifyHelper.hasPermission(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // 自动检查更新：默认每天一次（设置里可关）。失败静默，不打扰。
    LaunchedEffect(Unit) {
        if (!vm.autoUpdateCheck) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - vm.lastUpdateCheck < 20L * 3600 * 1000) return@LaunchedEffect
        vm.lastUpdateCheck = now
        runCatching { Repo.version() }.getOrNull()?.let { if (UpdateKit.hasUpdate(it)) updateInfo = it }
    }

    fun titleOf(code: String): String =
        me.nav.firstOrNull { it.code == code }?.label
            ?: when (code) {
                "me" -> "我的"
                "settings" -> "设置"
                "prefs" -> "偏好设置"
                "notify" -> "通知设置"
                else -> "日常管理"
            }

    fun go(code: String) {
        if (code == route) { scope.launch { drawer.close() }; return }
        stack.add(route)
        route = code
        scope.launch { drawer.close() }
    }

    fun back(): Boolean {
        // 模块内返回回调：先问当前模块要不要自己处理「返回」（如游戏：对局→大厅→列表）。
        // 返回 true = 已降层，不再弹路由栈。默认 null = 旧行为，其它模块零影响。
        if (moduleBackState.value?.invoke() == true) return true
        if (stack.isNotEmpty()) { route = stack.removeAt(stack.lastIndex); return true }
        if (route != "workbench") { route = "workbench"; return true }
        return false
    }

    BackHandler(enabled = stack.isNotEmpty() || route != "workbench") { back() }

    // 点通知进来：切到对应页面（冷启动与热启动都走这里）
    LaunchedEffect(vm.pendingRoute) {
        val target = vm.pendingRoute ?: return@LaunchedEffect
        if (target != route) {
            stack.add(route)
            route = target
        }
        vm.consumeRoute()
    }

    // 提示消息统一走 Snackbar
    LaunchedEffect(vm.ui.toast) {
        val msg = vm.ui.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.consumeToast()
    }

    val canBack = stack.isNotEmpty() || route != "workbench"

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = p.sideBar,
                drawerShape = RoundedCornerShape(topEnd = p.radiusCard, bottomEnd = p.radiusCard),
                modifier = Modifier.width(292.dp),
            ) {
                SidebarContent(
                    me = me,
                    active = route,
                    collapsed = collapsed,
                    accountCount = vm.ui.accounts.size,
                    onToggleGroup = { g -> collapsed[g] = !(collapsed[g] ?: false) },
                    onGo = { go(it) },
                    onPrefs = { scope.launch { drawer.close() }; showPrefs = true },
                    onAccounts = { scope.launch { drawer.close() }; showAccounts = true },
                    onLogout = { scope.launch { drawer.close() }; vm.logout() },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = p.bg,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                AppTopBar(
                    title = if (route == "workbench") "日常管理" else titleOf(route),
                    canBack = canBack,
                    onBack = { back() },
                    onMenu = { scope.launch { drawer.open() } },
                    onPrefs = { go("settings") },
                    userInitial = me.displayName.take(1).ifBlank { "我" },
                    onUser = { go("me") },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (vm.ui.offline) {
                    BannerBar(
                        "当前离线，显示的是上次同步的数据",
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        BannerTone.Warn,
                    )
                }
                // 通知权限没开：顶一条，可一键忽略（忽略后 7 天内不再顶）
                if (permMissing && !permDismissed && System.currentTimeMillis() > vm.permSnoozeUntil) {
                    NoticeBar(
                        text = "通知权限没开，打卡 / 日程提醒可能收不到",
                        action = "去开启",
                        onAction = { go("settings") },
                        onClose = {
                            permDismissed = true
                            vm.permSnoozeUntil = System.currentTimeMillis() + 7L * 24 * 3600 * 1000
                        },
                        tone = BannerTone.Warn,
                    )
                }
                // 有新版本
                updateInfo?.let { info ->
                    if (!updateDismissed) {
                        NoticeBar(
                            text = "发现新版本 ${info.version}，点这里看更新内容",
                            action = "查看",
                            onAction = { showUpdate = true },
                            onClose = { updateDismissed = true },
                            tone = BannerTone.Info,
                        )
                    }
                }
                // 页面切换用 key(route) 直接换：侧栏切换本来就该是「立刻换掉」，
                // 之前用 AnimatedContent 做滑动过渡，在低端机/软件渲染下会出现
                // 「标题已经变了、内容还停在上一页」的鬼影，加了 key 才彻底解决。
                key(route) {
                    ScreenHost(
                        route, vm, me,
                        onPrefs = { showPrefs = true },
                        onAccounts = { showAccounts = true },
                        setModuleBack = { moduleBackState.value = it },
                        go = { code -> go(code) },
                    )
                }
            }
        }
    }

    if (showUpdate) {
        UpdateSheet(vm, onClose = { showUpdate = false })
    }


    if (showPrefs) {
        PrefsSheet(vm, me, onClose = { showPrefs = false })
    }

    if (showAccounts) {
        AccountSwitchSheet(
            accounts = vm.ui.accounts,
            currentUsername = me.username,
            switchingTo = vm.ui.switchingTo,
            onSwitch = { acc ->
                showAccounts = false
                vm.switchAccount(acc)
                // 切号后必须回工作台：上一个账号停在哪一页（比如某个管理页）对下一个账号毫无意义，
                // 直接停在那儿还可能因为权限不同而白屏。
                stack.clear()
                route = "workbench"
            },
            onForget = { acc -> vm.forgetAccount(acc.username) },
            onManualLogin = {
                showAccounts = false
                vm.logout()
            },
            onDismiss = { showAccounts = false },
        )
    }
}

@Composable
private fun ScreenHost(
    route: String,
    vm: AppVm,
    me: MeDto,
    onPrefs: () -> Unit,
    onAccounts: () -> Unit,
    go: (String) -> Unit,
    setModuleBack: ((() -> Boolean)?) -> Unit = {},
) {
    // 「检查更新」「更新日志」两个弹层都在这里开（入口在设置页）
    var updateSheet by remember { mutableStateOf(false) }
    if (updateSheet) UpdateSheet(vm, onClose = { updateSheet = false })

    var changelogSheet by remember { mutableStateOf(false) }
    if (changelogSheet) ChangelogSheet(onClose = { changelogSheet = false })

    when (route) {
        "workbench" -> DashboardScreen(vm, me, go)
        "schedule" -> ScheduleScreen(vm)
        "memo" -> MemoScreen(vm)
        "habits" -> HabitsScreen(vm)
        "courses" -> CoursesScreen(vm)
        "expense" -> ExpensesScreen(vm)
        "stats" -> StatsScreen(vm)
        "anniversary" -> AnniversaryScreen(vm)
        "board" -> BoardScreen(vm)
        "games" -> GamesScreen(vm, setModuleBack = setModuleBack)
        "couples" -> CoupleScreen(vm)
        "period" -> PeriodScreen(vm)
        "reminders" -> RemindersScreen(vm, go)
        "ai" -> AiScreen(vm, onSwitchAccount = onAccounts)
        "email" -> EmailScreen(vm)
        "export" -> ExportScreen(vm)
        "admin-users" -> UsersScreen(vm)
        "admin-roles" -> RolesScreen(vm)
        "admin-invites" -> InvitesScreen(vm)
        "me" -> MeScreen(vm, me, onPrefs, go, onAccounts)
        "settings" -> SettingsScreen(
            vm = vm,
            themeLabel = vm.ui.theme.ifBlank { "默认" },
            onPrefs = onPrefs,
            onNotify = { go("notify") },
            onExport = { go("export") },
            onAccounts = onAccounts,
            onLogout = { vm.logout() },
            onUpdate = { updateSheet = true },
            onChangelog = { changelogSheet = true },
        )
        "notify" -> NotifyScreen(vm)
        else -> DashboardScreen(vm, me, go)
    }
}

// ---------------- 侧栏 ----------------

@Composable
private fun SidebarContent(
    me: MeDto,
    active: String,
    collapsed: MutableMap<String, Boolean>,
    accountCount: Int,
    onToggleGroup: (String) -> Unit,
    onGo: (String) -> Unit,
    onPrefs: () -> Unit,
    onAccounts: () -> Unit,
    onLogout: () -> Unit,
) {
    val p = palette
    val groups = me.nav.groupBy { it.group }

    Column(Modifier.fillMaxHeight().statusBarsPadding()) {
        // 品牌 + 用户
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(p.accent, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons["check"], null, Modifier.size(21.dp), tint = p.onAccent)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("日常管理", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Text(
                    "${me.displayName} · ${me.roleName}",
                    fontSize = 11.5.sp,
                    color = p.textWeak,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            groups.forEach { (group, items) ->
                val isCollapsed = collapsed[group] ?: false
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggleGroup(group) }
                        .padding(start = 10.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group,
                        fontSize = 11.sp,
                        color = p.textWeak,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        AppIcons[if (isCollapsed) "chevron-right" else "chevron-down"],
                        null,
                        Modifier.size(13.dp),
                        tint = p.textWeak,
                    )
                }
                if (!isCollapsed) {
                    items.forEach { item -> NavRow(item, item.code == active) { onGo(item.code) } }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 底部固定项：我的 / 切换账号 / 外观 / 退出
        Column(Modifier.padding(horizontal = 10.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            Spacer(Modifier.height(6.dp))
            NavRow(NavItemDto(code = "me", label = "我的", icon = "user-cog"), active == "me") { onGo("me") }
            // 「切换账号」放在「我的」下面而不是藏进设置：多账号是高频动作，
            // 而且这是唯一能一眼看出「本机还存了几个号」的地方，右侧带个计数。
            NavRow(
                NavItemDto(code = "switch-account", label = "切换账号", icon = "arrow-left-right"),
                false,
                trailing = if (accountCount > 1) "${accountCount - 1} 个可选" else null,
            ) { onAccounts() }
            // 「设置」独立成页：外观 / 通知 / 权限 / 更新 / 数据 / 账号都收进去了，
            // 「我的」只留个人资料，两个页面各管一件事
            NavRow(NavItemDto(code = "settings", label = "设置", icon = "settings-2"), active == "settings") { onGo("settings") }
            NavRow(NavItemDto(code = "logout", label = "退出登录", icon = "log-out"), false) { onLogout() }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun NavRow(
    item: NavItemDto,
    active: Boolean,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val p = palette
    val bg = if (active) p.accentSoft else Color.Transparent
    val fg = if (active) p.accent else p.textSub
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(bg, chipShape())
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons[item.icon], null, Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(11.dp))
        Text(
            item.label,
            fontSize = 14.sp,
            color = if (active) p.text else p.textSub,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, fontSize = 10.5.sp, color = p.textWeak)
        }
    }
}

// ---------------- 顶栏 ----------------

@Composable
private fun AppTopBar(
    title: String,
    canBack: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onPrefs: () -> Unit,
    userInitial: String,
    onUser: () -> Unit,
) {
    val p = palette
    Column(Modifier.fillMaxWidth().background(p.topBar).statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 与网页一致：返回和汉堡「同时存在」（不是二选一）。
            // 二选一会导致进到二级页后打不开侧栏，只能一路返回到首页 —— 很难用。
            if (canBack) IconBtn("chevron-left", "返回", onClick = onBack)
            IconBtn("menu", "菜单", onClick = onMenu)
            Text(
                title,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 2.dp),
            )
            IconBtn("palette", "外观", onClick = onPrefs)
            Box(
                Modifier
                    .size(32.dp)
                    .background(p.accentSoft, CircleShape)
                    .clickable(onClick = onUser),
                contentAlignment = Alignment.Center,
            ) {
                Text(userInitial, fontSize = 13.sp, color = p.accent, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.border == Color.Transparent) p.borderStrong.copy(alpha = .12f) else p.border))
    }
}
