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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import cn.anlele.checkin.ui.components.Spinner
import cn.anlele.checkin.ui.components.TinyAction
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.ui.theme.buttonShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * AI 助手 —— 对话 + 建议单。
 *
 * 与网页同一套规则：AI 只「提建议」，用户点确认才真正写库。
 * 所以助手消息下面会跟一张建议卡，确认按钮点完变成「已写入」。
 *
 * 【发送过程为什么要单独做一块 UI】
 * 这一轮请求后端要 17~40 秒（整张课表截图识别甚至更久）。早期版本点完发送
 * 只是下面出现一行 12sp 的「正在想…」：用户刚打的字已经从输入框消失、列表里
 * 又没出现自己那条，一时间不知道到底发出去没有，容易连点好几次 ——
 * 后端那边就是几条重复消息 + 几倍 token。
 *
 * 现在改成：点发送立刻把「我刚发的那条」以本地气泡画出来（不等接口），
 * 底下挂一条状态行，分阶段说明正在干什么，并显示已等待秒数；
 * 超过 8 秒补一句「图片识别会慢一些」，超过 40 秒提示可以继续等或放弃。
 */
@Composable
fun AiScreen(vm: AppVm, onSwitchAccount: (() -> Unit)? = null) {
    val p = palette
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<AiPageDto?>(null) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var showClear by remember { mutableStateOf(false) }
    // 待发送图片：bmp 用来预览，data 是压缩后的 data URL（发给后端的就是它）
    var pickedBmp by remember { mutableStateOf<Bitmap?>(null) }
    var pickedData by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    // ---------- 发送中的状态机（全部由本地变量驱动，不依赖接口回包） ----------
    /** 非空表示正在等接口：内容是用户刚发的那条（立即回显） */
    var pendingText by remember { mutableStateOf<String?>(null) }
    var pendingImg by remember { mutableStateOf<Bitmap?>(null) }
    /** 已等待秒数。用 tick 累加而不是算时间差：手机休眠/切后台也不会跳秒太快 */
    var waited by remember { mutableStateOf(0) }
    /** 失败信息：非空时状态行变成错误 + 重试 */
    var failed by remember { mutableStateOf<String?>(null) }
    /** 上一次发送的内容，供「重试」原样再发一次 */
    var lastText by remember { mutableStateOf("") }
    var lastImg by remember { mutableStateOf<String?>(null) }

    val sending = pendingText != null

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

    // 等待秒数计数：只在 sending 时跑。放在这里而不是 send() 里，
    // 是因为旋转屏幕、切后台回来都要继续显示正确的时长。
    LaunchedEffect(sending, failed) {
        if (!sending || failed != null) return@LaunchedEffect
        while (true) {
            delay(1000)
            waited += 1
        }
    }

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

    /** 真正发请求。t/msg/img 由调用方给，便于「重试」原样重放 */
    fun fire(t: String, img: String?, imgBmp: Bitmap?) {
        pendingText = t
        pendingImg = imgBmp
        waited = 0
        failed = null
        lastText = t
        lastImg = img
        scope.launch {
            try {
                page = Repo.aiSend(t, img)
                pendingText = null
                pendingImg = null
                // 成功后把「我发的那条」交给接口返回的列表，避免出现两条一模一样的
                waited = 0
            } catch (e: ApiException) {
                failed = e.message
            } catch (e: Exception) {
                failed = "发送失败，检查网络后再试"
            }
        }
    }

    fun send() {
        val t = text.trim()
        val img = pickedData
        // 纯图也能发
        if ((t.isEmpty() && img == null) || sending) return
        val imgBmp = pickedBmp
        text = ""
        pickedBmp = null
        pickedData = null
        fire(t, img, imgBmp)
    }

    fun retry() {
        val t = lastText
        val img = lastImg
        if (t.isEmpty() && img == null) return
        fire(t, img, pendingImg)
    }

    fun discard() {
        pendingText = null
        pendingImg = null
        failed = null
        waited = 0
    }

    // 新消息进视野：本地气泡、状态行、接口回包三处都要跟着滚
    val msgsNow = page?.items.orEmpty().size
    LaunchedEffect(msgsNow, sending, failed) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部：与网页同款页头卡（标题 + 说明 + tokens 用量 + 清空）
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            PageHeadCard(
                title = "AI 助手",
                actions = {
                    // 账号相关操作收敛在页头：这里是「以谁的身份在问 AI」最直观的位置
                    if (onSwitchAccount != null) {
                        TinyAction("切换账号", "arrow-left-right", onClick = onSwitchAccount)
                    }
                    TinyAction("清空历史", "trash-2") { showClear = true }
                },
            )
            val q = page?.quota
            if (q != null) {
                Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日已用 ${q.usedTokens} / ${q.dailyTokens} tokens · ${q.usedRequests} / ${q.dailyRequests} 次",
                        fontSize = 11.sp,
                        color = p.textWeak,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "@${vm.ui.accounts.firstOrNull()?.username ?: vm.ui.me?.username.orEmpty()}",
                        fontSize = 11.sp,
                        color = p.textWeak,
                    )
                }
            }
        }

        val msgs = page?.items.orEmpty()
        if (msgs.isEmpty() && !loading && !sending) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    icon = "spark",
                    title = "还没有对话",
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(msgs.size) { i ->
                    val m = msgs[i]
                    if (m.role == "user") UserBubble(m.content, m.hasImage) else AssistantBubble(m) { idx ->
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

                // ---------- 本地回显：我发的那条（不等接口先画出来） ----------
                val pt = pendingText
                if (pt != null) {
                    item("pending-user") {
                        UserBubble(pt, pendingImg != null)
                    }
                    // 图片也回显 —— 否则发截图时「图去哪了」会让人以为没发出去
                    val pb = pendingImg
                    if (pb != null) {
                        item("pending-img") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Image(
                                    bitmap = pb.asImageBitmap(),
                                    contentDescription = "刚发送的图片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(chipShape()),
                                )
                            }
                        }
                    }
                    item("pending-state") {
                        SendStateRow(
                            failed = failed,
                            waited = waited,
                            hasImage = pendingImg != null,
                            onRetry = { retry() },
                            onDiscard = { discard() },
                        )
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
                                .clip(chipShape()),
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
                // 发送中把按钮画成「转圈」，比单纯置灰更能说明「正在处理」
                if (sending) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Spinner(18.dp, tint = p.accent) }
                } else {
                    IconBtn(
                        "send", "发送", size = 44.dp,
                        tint = if (text.isBlank() && pickedData == null) p.textWeak else p.accent,
                    ) { send() }
                }
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
                }) { Text("清空", color = Color(0xFFD93025)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClear = false }) { Text("取消", color = p.textSub) }
            },
        )
    }
}

/**
 * 发送状态行 —— 做完这一屏最核心的一块：让用户始终知道「发到哪一步了」。
 *
 * 三态：
 *   进行中 → 转圈 + 阶段文案 + 已等待秒数（超过阈值补安慰/提示）
 *   失败   → 红色一行原因 + 「重试 / 放弃」
 * 什么都不显示的情况不存在：只要在等，就一定有话可说。
 */
@Composable
private fun SendStateRow(
    failed: String?,
    waited: Int,
    hasImage: Boolean,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val p = palette

    if (failed != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(p.cardShape())
                .background(p.cardSoft)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons["bell"], null, Modifier.size(14.dp), tint = Color(0xFFD93025))
                Spacer(Modifier.width(7.dp))
                Text(failed, fontSize = 12.5.sp, color = Color(0xFFD93025), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 主操作是「重试」：这一条消息用户是认真打的，不该因为一次网络抖动白丢
                Box(
                    Modifier
                        .clip(p.buttonShape())
                        .background(p.accent)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("重试", fontSize = 12.5.sp, color = p.onAccent, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier
                        .clip(p.buttonShape())
                        .background(p.card)
                        .clickable(onClick = onDiscard)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("放弃这条", fontSize = 12.5.sp, color = p.textSub)
                }
            }
        }
        return
    }

    // 阶段文案：宁可写得具体一点，也不要说「请稍候」这种等于没说的话
    val stage = when {
        waited < 3 -> "已发送，正在等 AI 回应…"
        waited < 10 -> "AI 正在读你的问题…"
        waited < 25 -> "正在生成回复，这一步稍慢…"
        else -> "还在算，网络或长截图会让它更久…"
    }
    // 长等待的补充说明：只在超过阈值时出现，避免一上来就吓人
    val tip = when {
        waited >= 45 -> "已经比较久了，可以继续等，也可以放弃后换个问法。"
        // 提示跟着「这条到底带没带图」走：纯文字说「带图片 20~40 秒」纯属误导
        waited >= 8 && hasImage -> "带图片的请求要逐行识别，通常 20~40 秒。"
        waited >= 20 && !hasImage -> "纯文字一般十几秒，这会儿可能是服务器在排队。"
        else -> null
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(p.cardShape())
            .background(p.card)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarDot(24.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spinner(13.dp)
                Spacer(Modifier.width(7.dp))
                Text(stage, fontSize = 12.5.sp, color = p.textSub, modifier = Modifier.weight(1f))
                Text("${waited}s", fontSize = 11.sp, color = p.textWeak)
            }
            if (tip != null) {
                Spacer(Modifier.height(4.dp))
                Text(tip, fontSize = 11.sp, color = p.textWeak, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun AvatarDot(size: Dp) {
    val p = palette
    Box(
        Modifier.size(size).background(p.accentSoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons["spark"], null, Modifier.size(size * .54f), tint = p.accent) }
}

/** 用户气泡：文字 + （可选）图片角标。本地回显与历史消息共用同一个形态 */
@Composable
private fun UserBubble(text: String, hasImage: Boolean) {
    val p = palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .fillMaxWidth(0.84f)
                .background(p.cardSoft, p.cardShape())
                .padding(12.dp),
        ) {
            Text(
                text.ifBlank { if (hasImage) "[图片]" else "" },
                fontSize = 13.5.sp,
                color = p.text,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun AssistantBubble(m: AiMsgDto, onExecute: (Int) -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth()) {
        AvatarDot(26.dp)
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