package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import cn.anlele.checkin.data.PermDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.RoleDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SheetDelete
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/** 角色管理 —— 自定义角色 + 勾权限。权限码由服务端播种，这里只负责勾选。 */
@Composable
fun RolesScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var roles by remember { mutableStateOf<List<RoleDto>>(emptyList()) }
    var perms by remember { mutableStateOf<List<PermDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RoleDto?>(null) }

    suspend fun load() {
        loading = true
        try {
            val d = Repo.roles()
            roles = d.roles
            perms = d.permissions
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

        item { PageHeadCard(title = "角色管理", hint = "权限码由系统提供，这里只负责把权限组合成角色。") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${roles.size} 个角色", fontSize = 12.5.sp, color = p.textWeak, modifier = Modifier.weight(1f))
                TinyAction("新建角色", "plus") { editing = null; editorOpen = true }
            }
        }
        items(roles.size) { i ->
            val r = roles[i]
            AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp), onClick = { editing = r; editorOpen = true }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, fontSize = 15.sp, color = p.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (r.isBuiltin)
                            Text("内置", fontSize = 10.sp, color = p.textWeak, modifier = Modifier.background(p.cardSoft, chipShape()).padding(horizontal = 6.dp, vertical = 1.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${r.userCount} 人", fontSize = 11.5.sp, color = p.textWeak)
                    }
                    if (r.description.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(r.description, fontSize = 12.sp, color = p.textWeak)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        r.codes.mapNotNull { code -> perms.firstOrNull { it.code == code }?.moduleLabel }.distinct().joinToString(" · ").ifBlank { "没有任何权限" },
                        fontSize = 11.5.sp,
                        color = p.accent,
                    )
                }
            }
        }
        item { PrimaryButton("新建角色", Modifier.fillMaxWidth(), icon = "plus") { editing = null; editorOpen = true } }
    }

    if (editorOpen) {
        RoleEditor(vm, editing, perms, onClose = { editorOpen = false }, onSaved = {
            editorOpen = false
            scope.launch { load() }
        })
    }
}

@Composable
private fun RoleEditor(
    vm: AppVm,
    initial: RoleDto?,
    perms: List<PermDto>,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var selected by remember { mutableStateOf(initial?.codes?.toSet() ?: emptySet()) }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = if (initial == null) "新建角色" else "编辑角色",
        onClose = onClose,
        saving = saving,
        destructive = if (initial == null || initial.isBuiltin) null else ({
            SheetDelete {
                scope.launch {
                    try {
                        Repo.deleteRole(initial.id)
                        onSaved()
                    } catch (e: ApiException) {
                        vm.toast(e.message)
                    }
                }
            }
        }),
        onSave = {
            if (name.isBlank() && initial?.isBuiltin != true) { vm.toast("角色名不能为空"); return@EditSheet }
            saving = true
            scope.launch {
                try {
                    val req = Repo.RoleReq(name.trim(), desc.trim(), selected.toList())
                    if (initial == null) Repo.createRole(req) else Repo.updateRole(initial.id, req)
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
        AppField("角色名", name, { name = it }, placeholder = "如：家人、同事", enabled = initial?.isBuiltin != true)
        Spacer(Modifier.height(12.dp))
        AppField("说明", desc, { desc = it }, placeholder = "这个角色能干什么")
        Spacer(Modifier.height(16.dp))
        Text("权限", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        perms.groupBy { it.moduleLabel }.forEach { (label, group) ->
            Column(Modifier.padding(bottom = 12.dp)) {
                Text(label, fontSize = 12.sp, color = p.textWeak)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { perm ->
                                AppChip(
                                    perm.description.ifBlank { perm.code },
                                    selected = selected.contains(perm.code),
                                ) {
                                    selected = if (selected.contains(perm.code)) selected - perm.code else selected + perm.code
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
