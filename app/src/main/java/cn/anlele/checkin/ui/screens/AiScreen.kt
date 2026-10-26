package cn.anlele.checkin.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.AiActionDto
import cn.anlele.checkin.data.AiMsgDto
import cn.anlele.checkin.data.AiPageDto
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EmptyState
import cn.anlele.checkin.ui.components.IconBtn
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * AI 助手 —— 对话 + 建议单。
 *
 * 与网页同一套规则：AI 只「提建议」，用户点确认才真正写库。
 * 所以助手消息下面会跟一张建议卡，确认按钮点完变成「已写入」。
 */
@Composable
fun AiScreen(vm: AppVm) {
    val p = palette
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<AiPageDto?>(null) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var showClear by remember { mutableStateOf(false) }
    // 待发送图片：bmp 用来预览，data 是压缩后的 data URL（发给后端的就是它）
    var pickedBmp by remember { mutableStateOf<Bitmap?>(null) }
    var pickedData by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    suspend fun load() {
        loading = true
        try {
            page = Repo.aiMessages()
        } catch (e: Exception) {
            vm.toast(e.message ?: "加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // 系统相册（PickVisualMedia：Android 13+ 走照片选择器，低版本自动回落文档选择器，
    // 两条路都不需要申请读相册权限）
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 解码 + 压缩放 IO 线程：手机原图动辄 3~5MB，主线程干这个必卡
            val loaded = withContext(Dispatchers.IO) { loadCompressed(ctx, uri) }
            if (loaded == null) vm.toast("这张图片读不出来，换一张试试")
            else { pickedBmp = loaded.bmp; pickedData = loaded.dataUrl }
        }
    }

    fun send() {
        val t = text.trim()
        val img = pickedData
        if ((t.isEmpty() && img == null) || sending) return     // 纯图也能发
        text = ""
        pickedBmp = null
        pickedData = null
        sending = true
        scope.launch {
            try {
                page = Repo.aiSend(t, img)
            } catch (e: ApiException) {
                vm.toast(e.message)
            } catch (e: Exception) {
                vm.toast("发送失败，检查网络后再试")
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部：与网页同款页头卡（标题 + 说明 + tokens 用量 + 清空）
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            PageHeadCard(
                title = "AI 助手",
                hint = "说「明天下午三点开会」或「午饭 15 元」就能直接建；点左下角回形针可发课表截图。AI 只出建议，确认后才写入。",
                actions = { TinyAction("清空历史", "trash-2") { showClear = true } },
            )
            val q = page?.quota
            if (q != null) {
                Text(
                    "今日已用 ${q.usedTokens} / ${q.dailyTokens} tokens",
                    fontSize = 11.sp,
                    color = p.textWeak,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
        }

        val msgs = page?.items.orEmpty()
        if (msgs.isEmpty() && !loading) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    icon = "spark",
                    title = "问我点什么吧",
                    hint = "「明天下午三点开会」我就能帮你排进日程",
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(msgs.size) { i ->
                    val m = msgs[i]
                    if (m.role == "user") UserBubble(m) else AssistantBubble(m) { idx ->
                        scope.launch {
                            try {
                                val r = Repo.aiExecute(m.id, idx)
                                vm.toast(r.message.ifBlank { "已写入" })
                                load()
                            } catch (e: ApiException) {
                                vm.toast(e.message)
                            } catch (e: Exception) {
                                vm.toast("执行失败")
                            }
                        }
                    }
                }
                if (sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = p.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在想…", fontSize = 12.sp, color = p.textWeak)
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(p.card)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // 待发送图片预览（右上角 ✕ 可撤掉）
            val bmp = pickedBmp
            if (bmp != null) {
                Row(
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "待发送图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 7.dp, y = (-7).dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(p.card)
                                .clickable { pickedBmp = null; pickedData = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons["x"], "移除图片", Modifier.size(11.dp), tint = p.textSub)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("会随这条消息一起发出去", fontSize = 11.sp, color = p.textWeak)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconBtn("paperclip", "添加图片", size = 44.dp, tint = if (sending) p.textWeak else p.textSub) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    AppField("", text, { text = it }, placeholder = "说点什么，或发张图让我看看…", singleLine = false, minLines = 1, maxLines = 4)
                }
                Spacer(Modifier.width(6.dp))
                IconBtn(
                    "send", "发送", size = 44.dp,
                    tint = if ((text.isBlank() && pickedData == null) || sending) p.textWeak else p.accent,
                ) { send() }
            }
        }
    }

    if (showClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClear = false },
            containerColor = p.card,
            title = { Text("清空对话", fontSize = 16.sp, color = p.text) },
            text = { Text("会删掉和 AI 的全部聊天记录，删了找不回来。", fontSize = 13.5.sp, color = p.textSub) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showClear = false
                    scope.launch {
                        runCatching { Repo.aiClear() }
                        load()
                    }
                }) { Text("清空", color = androidx.compose.ui.graphics.Color(0xFFD93025)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClear = false }) { Text("取消", color = p.textSub) }
            },
        )
    }
}

@Composable
private fun UserBubble(m: AiMsgDto) {
    val p = palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .fillMaxWidth(0.84f)
                .background(p.cardSoft, p.cardShape())
                .padding(12.dp),
        ) {
            Text(m.content.ifBlank { if (m.hasImage) "[图片]" else "" }, fontSize = 13.5.sp, color = p.text, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun AssistantBubble(m: AiMsgDto, onExecute: (Int) -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(26.dp).background(p.accentSoft, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons["spark"], null, Modifier.size(14.dp), tint = p.accent) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.fillMaxWidth(0.9f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(p.card, p.cardShape())
                    .padding(12.dp),
            ) {
                Text(m.content.ifBlank { "（没有文字回复）" }, fontSize = 13.5.sp, color = p.text, lineHeight = 21.sp)
            }
            m.actions.filterNot { it.done }.forEach { a ->
                Spacer(Modifier.height(7.dp))
                ActionCard(a) { onExecute(a.index) }
            }
        }
    }
}

@Composable
private fun ActionCard(a: AiActionDto, onConfirm: () -> Unit) {
    val p = palette
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(11.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.background(p.accentSoft, chipShape()).padding(horizontal = 7.dp, vertical = 2.dp),
                ) { Text(a.label, fontSize = 10.5.sp, color = p.accent) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(p.accent, chipShape())
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    Text("确认写入", fontSize = 11.5.sp, color = p.onAccent)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(a.summary, fontSize = 12.5.sp, color = p.textSub)
        }
    }
}

/** 选中的图片：压缩后的位图（预览用）+ data URL（发给后端的就是这个） */
private data class PickedImage(val bmp: Bitmap, val dataUrl: String)

/**
 * 读相册图片并压缩：最长边降到 1600px 以内、JPEG 0.82 —— 与网页端同一标准。
 * 手机原图 3~5MB 一般压到 150~400KB，离服务端 5MB 上限很远。
 * 先只读尺寸再按 2 的幂降采样（BitmapFactory 原生支持，比解码后缩放省内存），
 * 全程在 IO 线程调用（见调用点 withContext(Dispatchers.IO)）。
 */
private fun loadCompressed(ctx: Context, uri: Uri): PickedImage? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2

    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: return null

    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    PickedImage(bmp, "data:image/jpeg;base64,$b64")
} catch (e: Exception) {
    null
}
