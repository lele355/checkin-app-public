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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import cn.anlele.checkin.data.InviteDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.RoleBriefDto
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/** 邀请码 —— 本站不开放注册，新账号只能凭一次性的邀请码进来。 */
@Composable
fun InvitesScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<InviteDto>>(emptyList()) }
    var roles by remember { mutableStateOf<List<RoleBriefDto>>(emptyList()) }
    var siteUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var createOpen by remember { mutableStateOf(false) }
    var freshCode by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        try {
            val d = Repo.invites()
            rows = d.items
            roles = d.roles
            siteUrl = d.siteUrl
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

        item { PageHeadCard(title = "邀请码", hint = "本站不开放注册，新账号凭一次性邀请码进入，用完即失效。") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "有效 ${rows.count { it.usable }} 个",
                    fontSize = 12.5.sp,
                    color = p.textWeak,
                    modifier = Modifier.weight(1f),
                )
                TinyAction("生成邀请码", "plus") { createOpen = true }
            }
        }
        items(rows.size) { i ->
            val v = rows[i]
            AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            v.code,
                            fontSize = 15.sp,
                            color = if (v.usable) p.text else p.textWeak,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        val (label, tone) = when {
                            v.used -> "已使用" to p.textWeak
                            v.revoked -> "已撤销" to p.textWeak
                            v.expired -> "已过期" to androidx.compose.ui.graphics.Color(0xFFB3261E)
                            else -> "可用" to p.accent
                        }
                        Text(
                            label,
                            fontSize = 10.5.sp,
                            color = tone,
                            modifier = Modifier.background(p.cardSoft, chipShape()).padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${v.roleName} · 到期 ${Dates.fmt(v.expiresAt, "M月d日 HH:mm")}" +
                            (v.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                            (v.usedBy.takeIf { it.isNotBlank() }?.let { " · 被 $it 用了" } ?: ""),
                        fontSize = 11.5.sp,
                        color = p.textWeak,
                    )
                    if (v.usable) {
                        Spacer(Modifier.height(8.dp))
                        TinyAction("撤销", "x", danger = true) {
                            scope.launch {
                                runCatching { Repo.revokeInvite(v.id) }
                                load()
                            }
                        }
                    }
                }
            }
        }
        item { PrimaryButton("生成邀请码", Modifier.fillMaxWidth(), icon = "plus") { createOpen = true } }
        item {
            Text("注册地址：$siteUrl", fontSize = 11.sp, color = p.textWeak, modifier = Modifier.padding(top = 6.dp))
        }
    }

    if (createOpen) {
        InviteCreateSheet(vm, roles, onClose = { createOpen = false }, onCreated = { code ->
            createOpen = false
            freshCode = code
            scope.launch { load() }
        })
    }
    if (freshCode != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { freshCode = null },
            containerColor = p.card,
            title = { Text("邀请码已生成", fontSize = 15.sp, color = p.text) },
            text = {
                Column {
                    Text(freshCode!!, fontSize = 21.sp, color = p.accent, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("一次有效，发给对方让他到 $siteUrl 注册。", fontSize = 12.5.sp, color = p.textSub)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { freshCode = null }) { Text("知道了", color = p.accent) }
            },
        )
    }
}

@Composable
private fun InviteCreateSheet(
    vm: AppVm,
    roles: List<RoleBriefDto>,
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    var roleId by remember { mutableStateOf(roles.firstOrNull { it.name.contains("普通") }?.id ?: roles.lastOrNull()?.id ?: 0) }
    var note by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("7") }
    var saving by remember { mutableStateOf(false) }

    EditSheet(
        title = "生成邀请码",
        onClose = onClose,
        saving = saving,
        saveText = "生成",
        onSave = {
            saving = true
            scope.launch {
                try {
                    val r = Repo.createInvite(Repo.InviteReq(note.trim(), roleId, days.toIntOrNull() ?: 7))
                    // 后端返回 id，码需要再拉一次列表才知道 —— 直接读列表最新一条更省事
                    val list = Repo.invites().items
                    val code = list.firstOrNull { it.id == r.id }?.code ?: ""
                    onCreated(code)
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("生成失败")
                } finally {
                    saving = false
                }
            }
        },
    ) {
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
        AppField("备注", note, { note = it }, placeholder = "发给谁用的（自己看）")
        Spacer(Modifier.height(12.dp))
        AppField("有效天数", days, { days = it.filter { c -> c.isDigit() }.take(2) }, supporting = "1～90 天，默认 7 天")
        Spacer(Modifier.width(0.dp))
    }
}
