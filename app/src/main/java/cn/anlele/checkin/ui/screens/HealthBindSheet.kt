package cn.anlele.checkin.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cn.anlele.checkin.data.HealthBindingDto
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 绑定管理：列表（改名/解绑两下确认）+ 「绑定小米账号」QR 流程。
 * 解绑按钮自己关弹层确认 —— 弹层内不再嵌套弹层（Compose 不嵌套 ModalBottomSheet 的约定）。
 */
@Composable
fun HealthBindSheet(
    bindings: List<HealthBindingDto>,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()

    var qrPng by remember { mutableStateOf<String?>(null) }
    var qrBindingId by remember { mutableStateOf(0) }
    var polling by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableIntStateOf(-1) }   // 两下确认：-1=无，其它=待删 id
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableIntStateOf(-1) }      // 正在改名的绑定 id
    var editName by remember { mutableStateOf("") }
    var editOwner by remember { mutableStateOf("") }

    // QR 轮询：2s 一次，最多 3 分钟（QR 有效期 10 分钟，通常秒级出结果）
    LaunchedEffect(qrBindingId) {
        if (qrBindingId <= 0) return@LaunchedEffect
        polling = true
        repeat(90) {
            delay(2000)
            try {
                val st = Repo.healthBindStatus(qrBindingId)
                if (st.status == "active") {
                    onToast("绑定成功")
                    qrPng = null
                    qrBindingId = 0
                    polling = false
                    onClose()
                    return@LaunchedEffect
                }
                if (st.status == "expired" || st.status == "failed") {
                    onToast("二维码已过期，重新发起")
                    qrPng = null
                    qrBindingId = 0
                    polling = false
                    return@LaunchedEffect
                }
            } catch (_: Exception) { /* 网络抖一下继续轮询 */ }
        }
        polling = false
    }

    Dialog(onDismissRequest = { if (!polling) onClose() }) {
        AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("绑定管理", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "完成", fontSize = 13.sp, color = p.accentStrong,
                        modifier = Modifier.clickable { onClose() }.padding(4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))

                // ---- 已有绑定 ----
                bindings.forEach { b ->
                    if (editing == b.id) {
                        // 改名态：名称 + 归属标签
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = editName,
                                onValueChange = { editName = it.take(20) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = p.text),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
                                modifier = Modifier.fillMaxWidth()
                                    .clip(chipShape())
                                    .background(p.cardSoft)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = editOwner,
                                onValueChange = { editOwner = it.take(12) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = p.text),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
                                modifier = Modifier.fillMaxWidth()
                                    .clip(chipShape())
                                    .background(p.cardSoft)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Text(
                                    "取消", fontSize = 12.sp, color = p.textWeak,
                                    modifier = Modifier.clickable { editing = -1 }.padding(6.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    "保存", fontSize = 12.5.sp, color = p.accentStrong, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable {
                                        val id = b.id
                                        val n = editName.trim(); val o = editOwner.trim()
                                        editing = -1
                                        busy = true
                                        scope.launch {
                                            try {
                                                Repo.healthRename(id, n.ifBlank { null }, o)
                                                onToast("已改名")
                                                onClose()
                                            } catch (e: Exception) {
                                                onToast(e.message ?: "改名失败")
                                            } finally { busy = false }
                                        }
                                    }.padding(6.dp),
                                )
                            }
                        }
                    } else {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f).clickable {
                                editing = b.id
                                editName = b.displayName
                                editOwner = b.ownerName
                            },
                        ) {
                            Text(
                                b.displayName.ifBlank { "绑定 ${b.id}" },
                                fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.Medium,
                            )
                            Text(
                                listOfNotNull(
                                    b.ownerName.ifBlank { null },
                                    b.miUserId.take(6).ifBlank { null },
                                    when (b.status) {
                                        "active" -> "已连接"
                                        "pending" -> "扫码中"
                                        else -> b.status
                                    },
                                ).joinToString(" · "),
                                fontSize = 10.5.sp, color = p.textWeak,
                            )
                        }
                        Text(
                            if (confirmDel == b.id) "再点一次解绑" else "解绑",
                            fontSize = 12.sp,
                            color = if (confirmDel == b.id) p.textWeak else p.accentStrong,
                            modifier = Modifier
                                .clip(chipShape())
                                .clickable {
                                    if (confirmDel == b.id) {
                                        confirmDel = -1
                                        busy = true
                                        scope.launch {
                                            try {
                                                Repo.healthUnbind(b.id)
                                                onToast("已解绑")
                                                onClose()
                                            } catch (e: Exception) {
                                                onToast(e.message ?: "解绑失败")
                                            } finally { busy = false }
                                        }
                                    } else {
                                        confirmDel = b.id
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ---- QR 绑定 ----
                if (qrPng != null) {
                    val bmp = remember(qrPng) {
                        try {
                            BitmapFactory.decodeByteArray(
                                Base64.decode(qrPng, Base64.DEFAULT), 0,
                                Base64.decode(qrPng, Base64.DEFAULT).size,
                            )
                        } catch (_: Exception) { null }
                    }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        bmp?.let {
                            Image(
                                it.asImageBitmap(), contentDescription = "绑定二维码",
                                modifier = Modifier.size(220.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "用「米家」App 扫码确认登录",
                            fontSize = 11.5.sp, color = p.textSub, textAlign = TextAlign.Center,
                        )
                        if (polling) {
                            Spacer(Modifier.height(4.dp))
                            Text("等待确认…", fontSize = 10.5.sp, color = p.textWeak)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "取消", fontSize = 12.sp, color = p.accentStrong,
                            modifier = Modifier.clickable {
                                qrPng = null; qrBindingId = 0; polling = false
                            }.padding(4.dp),
                        )
                    }
                } else {
                    val bindLabel = if (busy) "处理中…" else "＋ 绑定小米账号"
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(chipShape())
                            .background(p.accentSoft)
                            .clickable(enabled = !busy) {
                                busy = true
                                scope.launch {
                                    try {
                                        val q = Repo.healthStartBind()
                                        qrPng = q.qrPng
                                        qrBindingId = q.id
                                    } catch (e: Exception) {
                                        onToast(e.message ?: "发起绑定失败")
                                    } finally { busy = false }
                                }
                            }
                            .padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(bindLabel, fontSize = 13.5.sp, color = p.accentStrong, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
