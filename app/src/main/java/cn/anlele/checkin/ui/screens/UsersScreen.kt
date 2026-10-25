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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import cn.anlele.checkin.data.AdminUserDto
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.RoleBriefDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.ConfirmDialog
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/** 用户管理 —— 建号 / 改角色 / 启停 / 重置密码 / 删除。仅 admin.users 权限可见。 */
@Composable
fun UsersScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<AdminUserDto>>(emptyList()) }
    var roles by remember { mutableStateOf<List<RoleBriefDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdminUserDto?>(null) }
    var resetFor by remember { mutableStateOf<AdminUserDto?>(null) }
    var newPassword by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<AdminUserDto?>(null) }

    suspend fun load() {
        loading = true
        try {
            val d = Repo.adminUsers()
            rows = d.items
            roles = d.roles
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),

    ) {

        item { PageHeadCard(title = "用户管理", hint = "建号、改角色、启停、重置密码 —— 重置后会生成一次性新密码。") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${rows.size} 个账号", fontSize = 12.5.sp, color = p.textWeak, modifier = Modifier.weight(1f))
                TinyAction("新建账号", "plus") { editing = null; editorOpen = true }
            }
        }
        items(rows.size) { i ->
            val u = rows[i]
            AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), onClick = { editing = u; editorOpen = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).background(if (u.isActive) p.accentSoft else p.cardSoft, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(u.displayName.take(1).ifBlank { "?" }, fontSize = 14.sp, color = if (u.isActive) p.accent else p.textWeak)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.displayName, fontSize = 14.5.sp, color = p.text, fontWeight = FontWeight.Medium)
                            if (u.isSelf) {
                                Spacer(Modifier.width(6.dp))
                                Text("我", fontSize = 10.sp, color = p.accent, modifier = Modifier.background(p.accentSoft, chipShape()).padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        Text(
                            "@${u.username} · ${u.roleName}" + if (!u.isActive) " · 已停用" else "",
                            fontSize = 11.5.sp,
                            color = if (u.isActive) p.textWeak else androidx.compose.ui.graphics.Color(0xFFB3261E),
                        )
                    }
                    TinyAction("重置密码", null) { resetFor = u }
                }
            }
        }
        item {
            PrimaryButton("新建账号", Modifier.fillMaxWidth(), icon = "plus") { editing = null; editorOpen = true }
        }
    }

    if (editorOpen) {
        UserEditor(vm, editing, roles, onClose = { editorOpen = false }, onSaved = {
            editorOpen = false
            scope.launch { load() }
        }, onDelete = { u -> deleteFor = u })
    }
    if (resetFor != null) {
        ConfirmDialog(
            title = "重置 ${resetFor!!.displayName} 的密码",
            message = "会生成一个随机密码，对方下次登录必须改掉。旧设备上的登录会全部失效。",
            confirmText = "重置",
            onConfirm = {
                val u = resetFor!!
                resetFor = null
                scope.launch {
                    try {
                        newPassword = Repo.resetPassword(u.id).password
                    } catch (e: Exception) {
                        vm.toast("重置失败")
                    }
                }
            },
            onDismiss = { resetFor = null },
        )
    }
    if (newPassword != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { newPassword = null },
            containerColor = p.card,
            title = { Text("新密码（只显示这一次）", fontSize = 15.sp, color = p.text) },
            text = {
                Column {
                    Text(newPassword!!, fontSize = 20.sp, color = p.accent, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("复制发给对方，让他登录后立刻改掉。", fontSize = 12.5.sp, color = p.textSub)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { newPassword = null }) { Text("知道了", color = p.accent) }
            },
        )
    }
    if (deleteFor != null) {
        ConfirmDialog(
            title = "删除 ${deleteFor!!.displayName}",
            message = "这个账号的日程、备忘、打卡、账目等数据会一起删除，无法恢复。",
            confirmText = "确认删除",
            danger = true,
            onConfirm = {
                val u = deleteFor!!
                deleteFor = null
                scope.launch {
                    try {
                        Repo.deleteUser(u.id)
                        vm.toast("已删除")
                        load()
                    } catch (e: ApiException) {
                        vm.toast(e.message)
                    }
                }
            },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun UserEditor(
    vm: AppVm,
    initial: AdminUserDto?,
    roles: List<RoleBriefDto>,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onDelete: (AdminUserDto) -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var display by remember { mutableStateOf(initial?.displayName ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var roleId by remember { mutableStateOf(initial?.roleId ?: roles.firstOrNull()?.id ?: 0) }
    var active by remember { mutableStateOf(initial?.isActive ?: true) }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = if (initial == null) "新建账号" else "编辑账号",
        onClose = onClose,
        saving = saving,
        destructive = if (initial == null || initial.isSelf) null else ({
            Column { SheetDelete { onDelete(initial) } }
        }),
        onSave = {
            if (initial == null) {
                if (username.length < 3) { vm.toast("账号至少 3 个字符"); return@EditSheet }
                if (password.length < 8) { vm.toast("初始密码至少 8 位"); return@EditSheet }
            }
            saving = true
            scope.launch {
                try {
                    val req = Repo.AdminUserReq(
                        username = username.trim().lowercase(),
                        displayName = display.trim(),
                        email = email.trim(),
                        roleId = roleId,
                        isActive = active,
                        password = password,
                    )
                    if (initial == null) Repo.createUser(req) else Repo.updateUser(initial.id, req)
                    onSaved()
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("保存失败")
                } finally {
                    saving = false
                }
            }
        },
    ) {
        if (initial == null) {
            AppField("账号", username, { username = it }, placeholder = "英文/数字，至少 3 位")
            Spacer(Modifier.height(12.dp))
            AppField("初始密码", password, { password = it }, password = true, supporting = "对方首次登录会被要求改掉")
        } else {
            Text("@${initial.username}", fontSize = 12.5.sp, color = p.textWeak)
        }
        Spacer(Modifier.height(12.dp))
        AppField("昵称", display, { display = it }, placeholder = "显示的名字")
        Spacer(Modifier.height(12.dp))
        AppField("邮箱", email, { email = it }, placeholder = "可不填")
        Spacer(Modifier.height(14.dp))
        Text("角色", fontSize = 12.5.sp, color = p.textSub)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            roles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { r -> AppChip(r.name, selected = roleId == r.id) { roleId = r.id } }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AppChip("启用", selected = active) { active = true }
            AppChip("停用", selected = !active) { active = false }
        }
    }
}
