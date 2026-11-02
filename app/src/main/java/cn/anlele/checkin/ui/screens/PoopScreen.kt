package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.PoopItemDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * 排便记录 —— 情侣大类（2026-11-02 站主规划）：点一下记一次，双方互见。
 */
@Composable
fun PoopScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<PoopItemDto>>(emptyList()) }
    var myUid by remember { mutableStateOf(0) }
    var tapOwner by remember { mutableStateOf(0) }   // 0=自己
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            val month = java.time.LocalDate.now().toString().take(7)
            val d = Repo.poops(month)
            items = d.items
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PageHeadCard(title = "排便记录", hint = "情侣互见 · 点一下记一次") }

            // 打卡大按钮
            item {
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(22.dp)) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val ownerIds = items.map { it.userId }.distinct()
                        if (ownerIds.size > 1) {
                            Row {
                                for (u in ownerIds) {
                                    val name = items.firstOrNull { it.userId == u }?.owner ?: "TA"
                                    Text(
                                        name,
                                        Modifier
                                            .padding(horizontal = 10.dp)
                                            .then(Modifier),
                                        fontSize = 12.sp,
                                        color = if ((tapOwner == 0 && u == items.firstOrNull()?.userId) || tapOwner == u) p.accent else p.textWeak,
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        PrimaryButton("💩 记一次", icon = "plus") {
                            scope.launch {
                                runCatching {
                                    Repo.tapPoop(Repo.PoopReq(userId = tapOwner.takeIf { it != 0 }))
                                }.onSuccess {
                                    vm.toast("记好啦")
                                    load()
                                }.onFailure { vm.toast(it.message ?: "记录失败") }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "本月共 ${items.size} 次",
                            fontSize = 11.5.sp, color = p.textWeak,
                        )
                    }
                }
            }

            item { Text("本月记录", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium) }
            if (items.isEmpty() && !loading) {
                item { Text("这个月还没有记录", fontSize = 12.5.sp, color = p.textWeak) }
            } else {
                items(items) { r ->
                    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.at, fontSize = 14.sp, color = p.text, fontWeight = FontWeight.Medium)
                                Text(r.owner, fontSize = 11.sp, color = p.textWeak)
                            }
                            TinyAction("删", "trash") {
                                scope.launch {
                                    runCatching { Repo.deletePoop(r.id) }.onSuccess { load() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
