package cn.anlele.checkin.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.VersionDto
import cn.anlele.checkin.ui.theme.cardShape
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.update.UpdateKit
import kotlinx.coroutines.launch

/**
 * 「检查更新」弹层 —— 从「我的 → 更多」进来。
 *
 * 【为什么是弹层不是新页面】
 * 这件事只有三个状态：查一下、有没有新版本、下不下载。开一个整页要占掉一次返回操作，
 * 弹层看完就关，和 App 里其它临时操作一致。
 *
 * 【流程】
 * 打开即自动查一次（用户点进来就是想看结果，不用再点一次「检查」）→
 * 有新版就显示版本号 + 更新说明 + 「下载并安装」→ 下载带进度 →
 * 完成后交给系统安装器（需要「允许安装未知应用」权限，没授权就先引导去开）。
 */
@Composable
fun UpdateSheet(vm: cn.anlele.checkin.AppVm, onClose: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(true) }
    var remote by remember { mutableStateOf<VersionDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloaded by remember { mutableStateOf<java.io.File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // 打开就查。失败只影响这一块，不弹 toast 打断。
    LaunchedEffect(Unit) {
        checking = true
        error = null
        try {
            remote = Repo.version()
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = "连不上服务器，稍后再试"
        } finally {
            checking = false
        }
    }

    val info = remote
    val update = info != null && UpdateKit.hasUpdate(info)

    PickerSheetShell(onDismiss = onClose) {
        // 注意：外层 PickerSheetShell 已经带 verticalScroll 了。
        // 这里再套一层 Column(verticalScroll) 会得到无限最大高度约束 → 直接崩：
        // "Vertically scrollable component was measured with an infinity maximum height constraints"
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Text("检查更新", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "当前 ${UpdateKit.localName}（${UpdateKit.localCode}）",
                fontSize = 12.sp, color = p.textWeak,
            )
            Spacer(Modifier.height(16.dp))

            when {
                // ---------- 查询中 ----------
                checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = p.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("正在查询…", fontSize = 13.sp, color = p.textSub)
                }

                // ---------- 查询失败 ----------
                error != null -> Column {
                    Text(error!!, fontSize = 13.sp, color = p.textSub, lineHeight = 20.sp)
                    Spacer(Modifier.height(14.dp))
                    SecondaryButton("重试", Modifier.fillMaxWidth()) {
                        scope.launch {
                            checking = true; error = null
                            try { remote = Repo.version() } catch (e: Exception) {
                                error = (e as? ApiException)?.message ?: "连不上服务器，稍后再试"
                            } finally { checking = false }
                        }
                    }
                }

                // ---------- 已是最新 ----------
                info != null && !update -> Column {
                    Box(
                        Modifier.fillMaxWidth().clip(cardShape()).background(p.cardSoft)
                            .padding(vertical = 22.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(40.dp).background(p.accentSoft, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✓", fontSize = 20.sp, color = p.accent, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("已经是最新版", fontSize = 14.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text("线上版本 ${info.version}", fontSize = 11.5.sp, color = p.textWeak)
                        }
                    }
                }

                // ---------- 有新版本 ----------
                info != null -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "新版本 ${info.version}",
                            fontSize = 13.5.sp, color = p.accent, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${UpdateKit.localName} → ${info.version}", fontSize = 11.sp, color = p.textWeak)
                    }
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            info.notes,
                            fontSize = 12.5.sp, color = p.textSub, lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(chipShape())
                                .background(p.cardSoft)
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    if (downloading) {
                        // 进度条：不用 LinearProgressIndicator 是因为要跟主题色统一，
                        // 而且服务端没给 Content-Length 时（3% 的情况）干脆画个滚动条
                        Column {
                            Box(
                                Modifier.fillMaxWidth().height(6.dp)
                                    .clip(CircleShape).background(p.cardSoft),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(if (progress > 0f) progress else 0.02f)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(p.accent),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (progress > 0f) "正在下载 ${(progress * 100).toInt()}%"
                                else "正在下载…",
                                fontSize = 11.5.sp, color = p.textWeak,
                            )
                        }
                    } else if (downloaded != null) {
                        Column {
                            Text(
                                "下载好了，点下面的按钮安装。安装时系统会问一次是否允许，选「允许」就行。",
                                fontSize = 12.sp, color = p.textSub, lineHeight = 19.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton("安装", Modifier.fillMaxWidth(), icon = "download") {
                                if (!UpdateKit.canInstall(ctx)) {
                                    vm.toast("先允许「安装未知应用」，回来再点安装")
                                    UpdateKit.openInstallSettings(ctx)
                                } else {
                                    runCatching { UpdateKit.install(ctx, downloaded!!) }
                                        .onFailure { vm.toast("打不开安装器，去浏览器下载吧") }
                                }
                            }
                        }
                    } else {
                        if (downloadError != null) {
                            Text(downloadError!!, fontSize = 12.sp, color = p.textSub)
                            Spacer(Modifier.height(10.dp))
                        }
                        PrimaryButton("下载并安装", Modifier.fillMaxWidth(), icon = "download") {
                            downloading = true
                            progress = 0f
                            downloadError = null
                            scope.launch {
                                try {
                                    val f = UpdateKit.download(ctx, UpdateKit.downloadUrl(info)) { progress = it }
                                    downloaded = f
                                    // 下载完直接尝试装。没授权时引导一次，用户回来后还能再点「安装」
                                    if (UpdateKit.canInstall(ctx)) {
                                        runCatching { UpdateKit.install(ctx, f) }
                                            .onFailure { vm.toast("打开安装器失败") }
                                    } else {
                                        vm.toast("请先允许「安装未知应用」")
                                        UpdateKit.openInstallSettings(ctx)
                                    }
                                } catch (e: Exception) {
                                    downloadError = e.message ?: "下载失败"
                                } finally {
                                    downloading = false
                                }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        SecondaryButton("用浏览器下载", Modifier.fillMaxWidth()) {
                            UpdateKit.openInBrowser(ctx, UpdateKit.downloadUrl(info))
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}

/**
 * 「下载并安装」失败时的兜底提示。留在独立函数里，方便以后加「忽略此版本」这类开关。
 */
@Composable
fun UpdateFallbackHint(text: String, onBrowser: () -> Unit) {
    val p = palette
    Column(
        Modifier.fillMaxWidth().clip(chipShape()).background(p.cardSoft)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, fontSize = 12.sp, color = p.textSub, lineHeight = 19.sp)
        TinyAction("去浏览器下载", "download") { onBrowser() }
    }
}