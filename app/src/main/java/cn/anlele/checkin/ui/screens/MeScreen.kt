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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Beian
import cn.anlele.checkin.data.MeDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.ConfirmDialog
import cn.anlele.checkin.ui.components.GroupHead
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/**
 * 我的 —— 与网页手机端一致：
 * 头像行 → 我的数据（2×4 八宫格，点数字直达模块）→ 账户（只读行 + 弹层编辑）→ 更多。
 */
@Composable
fun MeScreen(
    vm: AppVm,
    me: MeDto,
    onPrefs: () -> Unit,
    go: (String) -> Unit,
    onAccounts: () -> Unit = {},
) {
    val p = palette
    val scope = rememberCoroutineScope()

    var name by remember(me.id) { mutableStateOf(me.displayName) }
    var email by remember(me.id) { mutableStateOf(me.email) }
    var sig by remember(me.id) { mutableStateOf(me.signature) }
    var savingProfile by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }

    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var newPwd2 by remember { mutableStateOf("") }
    var savingPwd by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordEdit by remember { mutableStateOf(false) }
    var logoutAsk by remember { mutableStateOf(false) }
    var profileEdit by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 头像行 ----------
        StaggerIn(0) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).background(p.accentSoft, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            me.displayName.take(1).ifBlank { "我" },
                            fontSize = 23.sp, color = p.accent, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(me.displayName, fontSize = 17.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                me.roleName,
                                fontSize = 10.5.sp,
                                color = p.accent,
                                modifier = Modifier.background(p.accentSoft, chipShape()).padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Text("@${me.username}", fontSize = 12.sp, color = p.textWeak)
                        if (me.createdAt.isNotBlank()) {
                            Text(
                                "加入于 ${Dates.fmt(me.createdAt, "yyyy-MM-dd")}" +
                                    if (me.lastLoginAt.isNotBlank()) " · 上次登录 ${Dates.fmt(me.lastLoginAt, "MM-dd HH:mm")}" else "",
                                fontSize = 10.5.sp,
                                color = p.textWeak,
                            )
                        }
                    }
                    Icon(
                        AppIcons["palette"], "外观", Modifier.size(18.dp).clickable(onClick = onPrefs),
                        tint = p.textSub,
                    )
                    // 头像行右下角再放一个切换账号：多账号用户最常从「我的」进来找人
                    if (vm.ui.accounts.size > 1) {
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            AppIcons["arrow-left-right"], "切换账号",
                            Modifier.size(18.dp).clickable(onClick = onAccounts),
                            tint = p.textSub,
                        )
                    }
                }
            }
        }

        // ---------- 我的数据 ----------
        StaggerIn(1) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Column {
                    Text("我的数据", fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(11.dp))
                    DataGrid(me, go)
                }
            }
        }

        // ---------- 账户（只读行 + 二级编辑） ----------
        // 三个输入框常驻在这一页，页面永远「是个表单」—— 但多数时候只是想看一眼。
        // 改成「值在这里、点开才编辑」：平时清爽，要改点「编辑资料 / 修改密码」进弹层。
        StaggerIn(2) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("账户")
                    MeRow("user-cog", "登录名", trailing = me.username) { }
                    MeRow("pencil", "昵称", trailing = name.ifBlank { "未填写" }) { profileEdit = true }
                    MeRow("mail", "联系邮箱", trailing = email.ifBlank { "未填写" }) { profileEdit = true }
                    MeRow("pencil", "个性签名", trailing = sig.ifBlank { "未填写" }) { profileEdit = true }
                    MeRow("pencil", "编辑资料", trailing = null) { profileEdit = true }
                    MeRow("lock", "修改密码", trailing = null) { passwordEdit = true }
                }
            }
        }

        // ---------- 设置入口 ----------
        // 外观、通知、权限、更新、导出、账号、备案信息全搬到独立的「设置」页了。
        // 「我的」只留个人资料与改密码 —— 一个是常看常改的，一个是一学期点两次的。
        StaggerIn(3) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("更多")
                    MeRow("settings-2", "设置", trailing = "外观 / 通知 / 更新") { go("settings") }
                    MeRow("arrow-left-right", "切换账号", trailing = if (vm.ui.accounts.size > 1) "${vm.ui.accounts.size - 1} 个可选" else null) { onAccounts() }
                    MeRow("log-out", "退出登录", danger = true) { logoutAsk = true }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    if (profileEdit) {
        EditSheet(
            title = "编辑资料",
            onClose = { profileEdit = false; profileError = null },
            saving = savingProfile,
            onSave = {
                profileError = if (name.isBlank()) "昵称不能为空" else null
                if (profileError != null) return@EditSheet
                savingProfile = true
                scope.launch {
                    try {
                        val fresh = Repo.savePrefs(
                            Repo.PrefsReq(displayName = name.trim(), email = email.trim(), signature = sig.trim())
                        )
                        vm.updateMe(fresh)
                        vm.toast("资料已保存")
                        profileEdit = false
                        profileError = null
                    } catch (e: ApiException) {
                        profileError = e.message
                    } catch (e: Exception) {
                        profileError = "保存失败"
                    } finally {
                        savingProfile = false
                    }
                }
            },
        ) {
            profileError?.let { err ->
                Text(err, fontSize = 12.sp, color = Color(0xFFD93025), modifier = Modifier.padding(bottom = 8.dp))
            }
            AppField("登录名（不可改）", me.username, {}, enabled = false)
            Spacer(Modifier.height(11.dp))
            AppField("昵称", name, { name = it; profileError = null }, placeholder = "管理员")
            Spacer(Modifier.height(11.dp))
            AppField("联系邮箱（选填）", email, { email = it }, placeholder = "you@example.com")
            Spacer(Modifier.height(11.dp))
            AppField(
                "个性签名（选填）", sig, { sig = it },
                placeholder = "一句话介绍自己，最多 60 字",
                singleLine = false, minLines = 2, maxLines = 3,
            )
        }
    }

    if (passwordEdit) {
        EditSheet(
            title = "修改密码",
            onClose = { passwordEdit = false; passwordError = null },
            saving = savingPwd,
            saveText = "修改密码",
            onSave = {
                passwordError = when {
                    newPwd.length < 8 || !newPwd.any { it.isLetter() } || !newPwd.any { it.isDigit() } ->
                        "新密码至少 8 位，且须同时包含字母和数字"
                    newPwd != newPwd2 -> "两次输入的新密码不一致"
                    else -> null
                }
                if (passwordError != null) return@EditSheet
                savingPwd = true
                scope.launch {
                    try {
                        vm.updateMe(Repo.changePassword(oldPwd, newPwd))
                        oldPwd = ""; newPwd = ""; newPwd2 = ""
                        vm.toast("密码已更新，其它设备需要重新登录")
                        passwordEdit = false
                        passwordError = null
                    } catch (e: ApiException) {
                        passwordError = e.message
                    } catch (e: Exception) {
                        passwordError = "改密失败"
                    } finally {
                        savingPwd = false
                    }
                }
            },
        ) {
            passwordError?.let { err ->
                Text(err, fontSize = 12.sp, color = Color(0xFFD93025), modifier = Modifier.padding(bottom = 8.dp))
            }
            AppField("当前密码", oldPwd, { oldPwd = it }, password = true)
            Spacer(Modifier.height(11.dp))
            AppField("新密码（至少 8 位，含字母和数字）", newPwd, { newPwd = it; passwordError = null }, password = true)
            Spacer(Modifier.height(11.dp))
            AppField("确认新密码", newPwd2, { newPwd2 = it }, password = true)
        }
    }

    if (logoutAsk) {
        ConfirmDialog(
            title = "退出登录",
            message = "退出后要重新输入账号密码才能进来。",
            confirmText = "退出",
            danger = true,
            onConfirm = { logoutAsk = false; vm.logout() },
            onDismiss = { logoutAsk = false },
        )
    }

}

/** 我的数据：2 行 × 4 列，点一格跳到对应模块 */
@Composable
private fun DataGrid(me: MeDto, go: (String) -> Unit) {
    val p = palette
    val items = listOf(
        Triple("calendar", "${me.counts.schedules}", "日程") to "schedule",
        Triple("note", "${me.counts.memos}", "备忘") to "memo",
        Triple("check", "${me.counts.checkins}", "打卡") to "habits",
        Triple("grid", "${me.counts.courses}", "课程") to "courses",
        Triple("heart", "${me.counts.anniversaries}", "纪念日") to "anniversary",
        Triple("wallet", "${me.counts.expenses}", "记账") to "expense",
        Triple("chat", "${me.counts.board}", "留言") to "board",
        Triple("mail", "${me.counts.emailAccounts}", "邮箱") to "email",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (item, route) ->
                    val (icon, value, label) = item
                    Column(
                        Modifier
                            .weight(1f)
                            .background(p.cardSoft, chipShape())
                            .clickable { go(route) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(AppIcons[icon], null, Modifier.size(15.dp), tint = p.textSub)
                        Spacer(Modifier.height(4.dp))
                        Text(value, fontSize = 15.sp, color = p.text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Text(label, fontSize = 10.sp, color = p.textWeak, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeRow(
    icon: String,
    label: String,
    danger: Boolean = false,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val p = palette
    val tint = if (danger) androidx.compose.ui.graphics.Color(0xFFC0392B) else p.textSub
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons[icon], null, Modifier.size(17.dp), tint = tint)
        Spacer(Modifier.width(11.dp))
        Text(
            label, fontSize = 14.sp,
            color = if (danger) androidx.compose.ui.graphics.Color(0xFFC0392B) else p.text,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, fontSize = 11.5.sp, color = p.textWeak)
            Spacer(Modifier.width(6.dp))
        }
        Icon(AppIcons["chevron-right"], null, Modifier.size(15.dp), tint = p.textWeak)
    }
}

/**
 * 备案信息一行：内容 + 右侧箭头，点了打开官方查询页（合规要求）。
 *
 * [strong] = true 时内容当「编号」看（强调色）；false 时当「状态」看（灰字）——
 * 公安备案 App 侧没有编号，写的是「已备案」，用蓝色会让人以为是编号。
 */
@Composable
private fun BeianLine(label: String, code: String, url: String, strong: Boolean = true) {
    val p = palette
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { runCatching { uri.openUri(url) } }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label ", fontSize = 11.sp, color = p.textWeak)
        Text(code, fontSize = 11.sp, color = if (strong) p.accent else p.textSub, modifier = Modifier.weight(1f))
        Icon(AppIcons["chevron-right"], null, Modifier.size(11.dp), tint = p.textWeak)
    }
}
