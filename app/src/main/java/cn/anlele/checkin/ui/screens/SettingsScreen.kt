package cn.anlele.checkin.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.Beian
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.push.NotifyHelper
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.GroupHead
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.SiteSwitch
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.update.UpdateKit

/**
 * 设置页（独立于「我的」）。
 *
 * 【为什么从「我的」里拆出来】以前外观偏好、通知、导出、检查更新、退出登录全堆在「我的」
 * 最底下，一屏滑到底才看得见，而且跟「我的资料 / 改密码」混在一起 ——
 * 资料是常看常改的，设置是一学期点两次的，两类东西不该挤在同一页。
 *
 * 「我的」现在只留个人资料与改密码，页面底部给一个入口过来。
 */
@Composable
fun SettingsScreen(
    vm: AppVm,
    themeLabel: String,
    onPrefs: () -> Unit,
    onNotify: () -> Unit,
    onExport: () -> Unit,
    onAccounts: () -> Unit,
    onLogout: () -> Unit,
    onUpdate: () -> Unit,
    onChangelog: () -> Unit,
) {
    val p = palette
    val ctx = LocalContext.current

    var permGranted by remember { mutableStateOf(NotifyHelper.hasPermission(ctx)) }
    var autoCheck by remember { mutableStateOf(vm.autoUpdateCheck) }

    // 从系统设置页返回后要重新读一遍权限（用户可能刚在里面打开）
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permGranted = NotifyHelper.hasPermission(ctx) }
    LaunchedEffect(Unit) { permGranted = NotifyHelper.hasPermission(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 外观 ----------
        StaggerIn(0) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("外观")
                    SetRow("palette", "外观与偏好", trailing = themeLabel) { onPrefs() }
                }
            }
        }

        // ---------- 提醒与权限 ----------
        StaggerIn(1) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("提醒与权限")
                    SetRow("bell", "通知设置") { onNotify() }
                    // 权限状态放在这里：既是「当前状态」，也是「去开启」的入口
                    SetRow(
                        "lock",
                        "通知权限",
                        trailing = if (permGranted) "已开启" else "未开启",
                        trailingWarn = !permGranted,
                    ) {
                        if (permGranted) {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                    // 自动检查更新：默认开。关掉之后只保留「检查更新」手动入口。
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("自动检查更新", fontSize = 13.5.sp, color = p.text)
                            Text(
                                if (autoCheck) "每天首次打开时查一次，有新版本在顶部提示" else "已关闭，只能手动检查",
                                fontSize = 11.sp, color = p.textWeak, lineHeight = 16.sp,
                            )
                        }
                        SiteSwitch(autoCheck) {
                            autoCheck = it
                            vm.autoUpdateCheck = it
                        }
                    }
                }
            }
        }

        // ---------- 数据 ----------
        StaggerIn(2) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("数据")
                    SetRow("download", "导出数据") { onExport() }
                }
            }
        }

        // ---------- 账号 ----------
        StaggerIn(3) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
                Column {
                    GroupHead("账号")
                    SetRow("arrow-left-right", "切换账号") { onAccounts() }
                    SetRow("log-out", "退出登录", danger = true) { onLogout() }
                }
            }
        }

        // ---------- 关于 ----------
        StaggerIn(4) {
            AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("关于", fontSize = 13.sp, color = p.textSub, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            "v${UpdateKit.localName}",
                            fontSize = 11.5.sp, color = p.textWeak,
                            modifier = Modifier.clickable { onUpdate() },
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "日常管理 ${UpdateKit.localName}（原生版 ${UpdateKit.localCode}）",
                        fontSize = 11.sp, color = p.textWeak, lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    SetRow("rotate-ccw", "检查更新", trailing = UpdateKit.localName, noPadding = true) { onUpdate() }
                    SetRow("list", "更新日志", trailing = "全部版本", noPadding = true) { onChangelog() }
                    Spacer(Modifier.height(6.dp))
                    // 法规要求：备案信息要在显著位置可查
                    Text("ICP 备案 ${Beian.appIcp(vm.ui.beiAn)}", fontSize = 11.sp, color = p.textWeak)
                    Text(
                        "公安备案 ${Beian.policeText(vm.ui.policeBeiAn)}",
                        fontSize = 11.sp, color = p.textWeak,
                    )
                }
            }
        }
    }
}

/** 危险色：主题里没有 danger token，全站危险操作用同一个红（与「我的」页一致） */
private val SetDanger = androidx.compose.ui.graphics.Color(0xFFC0392B)

/**
 * 设置页的一行：图标 + 标题 + 右侧值 + 箭头。
 * 与「我的」里的 MeRow 同一个视觉规格（图标 15dp、行高靠 padding 撑），只是这里要支持
 * 「右值标红」（权限未开启）和「不带左右内边距」（嵌在关于卡片里）。
 */
@Composable
private fun SetRow(
    icon: String,
    title: String,
    trailing: String? = null,
    trailingWarn: Boolean = false,
    danger: Boolean = false,
    noPadding: Boolean = false,
    onClick: () -> Unit,
) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (noPadding) 0.dp else 16.dp,
                end = if (noPadding) 0.dp else 16.dp,
                top = if (noPadding) 6.dp else 11.dp,
                bottom = if (noPadding) 6.dp else 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).background(p.cardSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons[icon], null, Modifier.size(14.dp),
                tint = if (danger) SetDanger else p.textSub,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title, fontSize = 13.5.sp,
            color = if (danger) SetDanger else p.text,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing, fontSize = 11.5.sp,
                color = if (trailingWarn) SetDanger else p.textWeak,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(AppIcons["chevron-right"], null, Modifier.size(13.dp), tint = p.textWeak)
    }
}

/** 设置页里跳系统「应用通知设置」的兼容写法（部分 ROM 不认 ACTION_APP_NOTIFICATION_SETTINGS） */
@Suppress("unused")
private fun openAppNotificationSettings(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
