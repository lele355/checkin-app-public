package cn.anlele.checkin.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.EmailAccountDto
import cn.anlele.checkin.data.EmailGroupDto
import cn.anlele.checkin.data.MailDetailDto
import cn.anlele.checkin.data.MailListItemDto
import cn.anlele.checkin.data.ProviderDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.ViewSwitch
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/**
 * 邮件 —— 两个分区：收件箱 / 账号。
 *
 * 服务器只缓存信头，正文要等点开才去 IMAP 拉（第一次会慢一两秒），
 * 所以详情弹层里先显示「正在取正文…」，拉到就立刻渲染。
 */
@Composable
fun EmailScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var accounts by remember { mutableStateOf<List<EmailAccountDto>>(emptyList()) }
    var mails by remember { mutableStateOf<List<MailListItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf(0) }
    // 「按时间」/「按发件人」两种看信方式；聚合把同一发件人的信并成一行
    var groupMode by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<EmailGroupDto>>(emptyList()) }

    var openMail by remember { mutableStateOf<Int?>(null) }
    var detail by remember { mutableStateOf<MailDetailDto?>(null) }
    var detailLoading by remember { mutableStateOf(false) }

    var addOpen by remember { mutableStateOf(false) }

    suspend fun loadAccounts() {
        try {
            accounts = Repo.emailAccounts().items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        }
    }

    suspend fun loadMails() {
        try {
            if (groupMode) groups = Repo.emailGroups(accountId).items
            else mails = Repo.emails(accountId).items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        }
    }

    LaunchedEffect(tab, accountId) {
        loading = true
        if (tab == 0) loadMails() else loadAccounts()
        loading = false
    }

    LaunchedEffect(openMail) {
        val id = openMail ?: return@LaunchedEffect
        detail = null
        detailLoading = true
        try {
            detail = Repo.emailDetail(id)
            mails = mails.map { if (it.id == id) it.copy(isRead = true) else it }
        } catch (e: Exception) {
            vm.toast(e.message ?: "取正文失败")
        } finally {
            detailLoading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageHeadCard(
                title = if (tab == 0) "收件箱" else "邮箱账号",
                hint = if (tab == 0) "多个邮箱的信汇成一个列表；点开自动标已读。"
                       else "授权码加密存放，界面永不回显，删账号会连缓存信件一起清掉。",
            )
            ViewSwitch(
                options = listOf("mail" to "收件箱", "settings-2" to "账号"),
                selected = if (tab == 0) "收件箱" else "账号",
                onSelect = { tab = if (it == "收件箱") 0 else 1 },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            if (tab == 0) {
                TinyAction(if (syncing) "同步中…" else "同步", "rotate-ccw") {
                    if (syncing) return@TinyAction
                    syncing = true
                    scope.launch {
                        try {
                            val r = Repo.syncEmail(accountId)
                            val added = r.results.sumOf { it.added }
                            vm.toast(if (r.results.isEmpty()) "还没绑定邮箱" else "同步完成，新增 $added 封")
                            loadMails()
                        } catch (e: Exception) {
                            vm.toast("同步失败，稍后再试")
                        } finally {
                            syncing = false
                        }
                    }
                }
                TinyAction("已读", "check") {
                    scope.launch {
                        runCatching { Repo.markAllEmailRead(accountId) }
                        vm.toast("已全部标为已读")
                        loadMails()
                    }
                }
                TinyAction(if (groupMode) "按时间" else "按发件人", "users") {
                    groupMode = !groupMode
                    scope.launch { loadMails() }
                }
            } else {
                TinyAction("添加", "plus") { addOpen = true }
            }
        }

        if (tab == 1) {
            if (accounts.isEmpty() && !loading) {
                EmptyState(icon = "mail", title = "还没有邮箱", hint = "绑定一个 QQ / 163 / Gmail 都行") {
                    PrimaryButton("添加邮箱", icon = "plus") { addOpen = true }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(accounts.size) { i ->
                        val a = accounts[i]
                        AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(36.dp).background(p.accentSoft, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(AppIcons["mail"], null, Modifier.size(17.dp), tint = p.accent) }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.displayName, fontSize = 14.5.sp, color = p.text, fontWeight = FontWeight.Medium)
                                    Text(a.address, fontSize = 11.5.sp, color = p.textWeak)
                                    if (a.lastSyncError.isNotBlank())
                                        Text(a.lastSyncError, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFFB3261E), maxLines = 2)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (a.unread > 0)
                                        Text("${a.unread} 未读", fontSize = 11.5.sp, color = p.accent)
                                    TinyAction("删除", "trash-2", danger = true) {
                                        scope.launch {
                                            runCatching { Repo.deleteEmailAccount(a.id) }
                                            loadAccounts()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { PrimaryButton("添加邮箱", Modifier.fillMaxWidth(), icon = "plus") { addOpen = true } }
                }
            }
        } else {
            if (mails.isEmpty() && !loading) {
                EmptyState(icon = "mail", title = "收件箱是空的")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (accounts.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                AppChip("全部", selected = accountId == 0) { accountId = 0 }
                                accounts.forEach { a ->
                                    AppChip(a.displayName, selected = accountId == a.id) { accountId = a.id }
                                }
                            }
                        }
                    }
                    if (groupMode) {
                        items(groups.size) { i ->
                            val g = groups[i]
                            MailGroupRow(g,
                                onOpen = { openMail = g.lastId },
                                onMarkRead = {
                                    scope.launch {
                                        runCatching { Repo.markAllEmailRead(accountId, g.groupKey) }
                                        loadMails()
                                    }
                                },
                            )
                        }
                    } else {
                        items(mails.size) { i ->
                            val m = mails[i]
                            MailRow(m) { openMail = m.id }
                        }
                    }
                }
            }
        }
    }

    // 读信
    if (openMail != null) {
        MailDetailSheet(detail, detailLoading, onClose = { openMail = null; detail = null })
    }

    if (addOpen) {
        AddAccountSheet(vm, onClose = { addOpen = false }, onSaved = {
            addOpen = false
            scope.launch { loadAccounts() }
        })
    }
}

/**
 * 聚合行：同一发件人的信并成一行。
 * 未读数用徽章显示（0 则不显示），右侧给了「这个发件人一键已读」。
 */
@Composable
private fun MailGroupRow(g: EmailGroupDto, onOpen: () -> Unit, onMarkRead: () -> Unit) {
    val p = palette
    val who = g.fromName.ifBlank { g.fromAddress }.ifBlank { "（无发件人）" }
    AppCard(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        padding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(p.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(who.take(1), fontSize = 14.sp, color = p.accent, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        who, fontSize = 13.5.sp,
                        color = if (g.unread > 0) p.text else p.textSub,
                        fontWeight = if (g.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (g.unread > 0) {
                        Box(
                            Modifier.background(p.accent, chipShape()).padding(horizontal = 6.dp, vertical = 1.dp)
                        ) { Text("${g.unread} 未读", fontSize = 10.sp, color = p.onAccent) }
                    }
                }
                Text(
                    g.lastSubject.ifBlank { g.lastSnippet },
                    fontSize = 11.5.sp, color = p.textWeak,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "共 ${g.total} 封 · ${Dates.fmt(g.lastAt, "M月d日 HH:mm")}",
                    fontSize = 10.5.sp, color = p.textWeak,
                )
            }
            if (g.unread > 0) {
                Spacer(Modifier.width(4.dp))
                TinyAction("已读", "check") { onMarkRead() }
            }
        }
    }
}

@Composable
private fun MailRow(m: MailListItemDto, onClick: () -> Unit) {
    val p = palette
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(13.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!m.isRead) {
                        Box(Modifier.size(6.dp).background(p.accent, CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        m.fromName.ifBlank { m.fromAddress },
                        fontSize = 12.5.sp,
                        color = if (m.isRead) p.textSub else p.text,
                        fontWeight = if (m.isRead) FontWeight.Normal else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(Dates.fmt(m.sentAt, "M月d日"), fontSize = 10.5.sp, color = p.textWeak)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    m.subject,
                    fontSize = 13.5.sp,
                    color = if (m.isRead) p.textSub else p.text,
                    fontWeight = if (m.isRead) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(m.snippet, fontSize = 11.5.sp, color = p.textWeak, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (m.hasAttachment) {
                Spacer(Modifier.width(6.dp))
                Icon(AppIcons["paperclip"], null, Modifier.size(14.dp), tint = p.textWeak)
            }
        }
    }
}

@Composable
private fun MailDetailSheet(d: MailDetailDto?, loading: Boolean, onClose: () -> Unit) {
    val p = palette
    EditSheet(title = d?.subject ?: "读取中…", onClose = onClose) {
        if (loading || d == null) {
            Text("正在取正文…（第一次会慢一点）", fontSize = 12.5.sp, color = p.textWeak)
            return@EditSheet
        }
        Text(
            "${d.fromName.ifBlank { d.fromAddress }} · ${Dates.fmt(d.sentAt, "yyyy年M月d日 HH:mm")}",
            fontSize = 11.5.sp,
            color = p.textWeak,
        )
        if (d.bodyError.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("正文没取到：${d.bodyError}", fontSize = 12.5.sp, color = androidx.compose.ui.graphics.Color(0xFFB3261E))
        }
        Spacer(Modifier.height(14.dp))
        Text(d.body.ifBlank { "（无正文）" }, fontSize = 13.5.sp, color = p.text, lineHeight = 22.sp)
    }
}

@Composable
private fun AddAccountSheet(vm: AppVm, onClose: () -> Unit, onSaved: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf<List<ProviderDto>>(emptyList()) }
    var provider by remember { mutableStateOf("qq") }
    var display by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // 服务器地址：预设选好会自动填，但用户随时能改（自定义邮箱必须能填，
    // 否则 provider=custom 时服务端直接报「IMAP 服务器地址不能为空」）
    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("") }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("") }
    // 同步范围（天 / 封），留空 = 跟全局默认
    var syncDays by remember { mutableStateOf("") }
    var syncCount by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { providers = Repo.emailProviders().items }
    }
    // 换服务商就把地址端口填好（QQ/163 之类），用户可再改
    LaunchedEffect(provider) {
        providers.firstOrNull { it.key == provider }?.let { pr ->
            if (pr.imapHost.isNotBlank()) imapHost = pr.imapHost
            if (pr.imapPort > 0) imapPort = pr.imapPort.toString()
            if (pr.smtpHost.isNotBlank()) smtpHost = pr.smtpHost
            if (pr.smtpPort > 0) smtpPort = pr.smtpPort.toString()
        }
    }

    EditSheet(
        title = "绑定邮箱",
        onClose = onClose,
        saving = saving,
        saveText = "绑定",
        onSave = {
            if (address.isBlank() || code.isBlank()) { vm.toast("地址和授权码都要填"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    Repo.saveEmailAccount(
                        Repo.AccountReq(
                            displayName = display.ifBlank { address },
                            address = address.trim(),
                            provider = provider,
                            imapHost = imapHost.trim(),
                            imapPort = imapPort.trim().toIntOrNull() ?: 0,
                            smtpHost = smtpHost.trim(),
                            smtpPort = smtpPort.trim().toIntOrNull() ?: 0,
                            authCode = code.trim(),
                            syncDays = syncDays.trim().toIntOrNull() ?: 0,
                            syncCount = syncCount.trim().toIntOrNull() ?: 0,
                        )
                    )
                    onSaved()
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("绑定失败")
                } finally {
                    saving = false
                }
            }
        },
    ) {
        Text(
            "授权码不是登录密码：QQ 邮箱在「设置 → 账户 → IMAP 服务」里生成；163 在「设置 → POP3/SMTP/IMAP」里开。",
            fontSize = 12.sp,
            color = p.textSub,
        )
        Spacer(Modifier.height(12.dp))
        Text("服务商", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            providers.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { pr ->
                        AppChip(pr.label, selected = provider == pr.key) { provider = pr.key }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppField("邮箱地址", address, { address = it }, placeholder = "you@qq.com", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AppField("授权码", code, { code = it }, placeholder = "16 位授权码", password = true)
        Spacer(Modifier.height(12.dp))
        AppField("显示名", display, { display = it }, placeholder = "可不填")

        Spacer(Modifier.height(16.dp))
        Text("收信服务器（IMAP）", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(2f)) {
                AppField("服务器", imapHost, { imapHost = it }, placeholder = "imap.example.com")
            }
            Box(Modifier.weight(1f)) {
                AppField("端口", imapPort, { imapPort = it }, placeholder = "993",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("发信服务器（SMTP，不发信可不填）", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(2f)) {
                AppField("服务器", smtpHost, { smtpHost = it }, placeholder = "smtp.example.com")
            }
            Box(Modifier.weight(1f)) {
                AppField("端口", smtpPort, { smtpPort = it }, placeholder = "465",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("同步范围（留空 = 跟全局默认）", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                AppField("最近 N 天", syncDays, { syncDays = it }, placeholder = "跟默认",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            }
            Box(Modifier.weight(1f)) {
                AppField("最近 N 封", syncCount, { syncCount = it }, placeholder = "跟默认",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "163/126 邮箱：先在网页版「设置 → POP3/SMTP/IMAP」里打开 IMAP/SMTP 服务，用生成的授权码；" +
                "发信时发件人必须是这个账号本身。",
            fontSize = 11.sp, color = p.textWeak, lineHeight = 16.sp,
        )
    }
}
