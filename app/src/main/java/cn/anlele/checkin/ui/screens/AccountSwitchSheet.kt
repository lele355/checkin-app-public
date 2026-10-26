package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.anlele.checkin.data.SavedAccount
import cn.anlele.checkin.ui.components.Spinner
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.fieldShape
import cn.anlele.checkin.ui.theme.palette

/**
 * 切换账号面板。
 *
 * 【位置】登录页、侧栏、AI 页页头三处都能打开同一张面板 ——
 * 它们都是「我现在是谁」这件事最容易被想到的地方。
 *
 * 【为什么用 Dialog 不用 ModalBottomSheet】
 * 见 DatePicker.kt 里 PickerSheetShell 的注释：全 App 只允许 EditSheet / PrefsSheet
 * 两个 ModalBottomSheet 存在，其余弹层一律 Dialog。这里可能从侧栏（Drawer）或
 * AI 页里打开，任何一层如果本身是 sheet 都会叠成 bug。
 *
 * 【形态】与 PickerSheetShell 一致的底部卡片：22dp 顶圆角、横条、点空白关闭、横屏限宽 520dp。
 */
@Composable
fun AccountSwitchSheet(
    accounts: List<SavedAccount>,
    currentUsername: String,
    switchingTo: String?,
    onSwitch: (SavedAccount) -> Unit,
    onForget: (SavedAccount) -> Unit,
    onManualLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = palette
    val current = accounts.firstOrNull { it.username.equals(currentUsername, ignoreCase = true) }
    val others = accounts.filterNot { it.username.equals(currentUsername, ignoreCase = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 外层铺满整屏当遮罩：usePlatformDefaultWidth=false 时 Dialog 仍是 wrapContent，
        // 不 fillMaxSize 就点不到内容以外的空白区域（点了关不掉，只能按返回键）。
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(p.card)
                    // 卡片自己吃掉点击，否则点卡片空白处会穿到遮罩上把面板关掉
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
                SheetGripBar()
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("切换账号", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点一下就直接换成那个号，不用重新输密码。",
                        fontSize = 11.5.sp,
                        color = p.textWeak,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(14.dp))

                    // 先亮明「现在是谁」：否则面板里只看到别人的号，
                    // 用户会怀疑自己是不是已经被换掉了，也不确定列表里该点哪个。
                    if (current != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(fieldShape())
                                .background(p.accentSoft)
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(32.dp).background(p.card, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    current.displayName.take(1).ifBlank { current.username.take(1).ifBlank { "?" } },
                                    fontSize = 14.sp,
                                    color = p.accent,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text("当前账号", fontSize = 10.5.sp, color = p.textWeak)
                                Text(
                                    current.displayName.ifBlank { current.username },
                                    fontSize = 13.5.sp,
                                    color = p.text,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(AppIcons["check-plain"], null, Modifier.size(15.dp), tint = p.accent)
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    if (others.isEmpty()) {
                        // 只有一个号（或一个都没存）时的空态：说清楚怎么才会有第二个
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(fieldShape())
                                .background(p.cardSoft)
                                .padding(horizontal = 14.dp, vertical = 16.dp),
                        ) {
                            Column {
                                Text("还只有这一个账号", fontSize = 12.5.sp, color = p.textSub)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "用另一个号登录一次，它就会出现在这里，之后两边可以一键来回切。",
                                    fontSize = 11.5.sp,
                                    color = p.textWeak,
                                    lineHeight = 17.sp,
                                )
                            }
                        }
                    } else {
                        others.forEach { acc ->
                            AccountRow(
                                acc = acc,
                                busy = switchingTo.equals(acc.username, ignoreCase = true),
                                disabled = switchingTo != null,
                                onSwitch = { onSwitch(acc) },
                                onForget = { onForget(acc) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // 手动登录：账号簿里没有的号，或者密码已经被改过的号
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(fieldShape())
                            .background(p.cardSoft)
                            .clickable(enabled = switchingTo == null) { onManualLogin() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons["log-out"], null, Modifier.size(16.dp), tint = p.textSub)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("用其它账号登录", fontSize = 13.sp, color = p.text)
                            Text("会先退出当前账号，再回到登录页", fontSize = 11.sp, color = p.textWeak)
                        }
                        Icon(AppIcons["chevron-right"], null, Modifier.size(14.dp), tint = p.textWeak)
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "账号密码只存在这台手机上（Base64 混淆，非加密）。换手机或清除数据后需要重新登录。",
                        fontSize = 10.5.sp,
                        color = p.textWeak,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(22.dp))
                }
            }
        }
    }
}

/** 面板顶部的小横条（与 EditSheet / PickerSheetShell 同一形态） */
@Composable
private fun SheetGripBar() {
    val p = palette
    Box(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(p.borderStrong.copy(alpha = .35f))
        )
    }
}

/**
 * 一个可切换的账号。
 *
 * 「移除」不做成右侧常驻的危险按钮：误触概率太高（这一行本身就是个点击区），
 * 改成先点叉号露出「移除 / 算了」，再点一次真删 —— 与网页端 data-confirm 同一套思路：
 * 确认放在用户本来就要点的地方，不弹对话框打断流程。
 */
@Composable
private fun AccountRow(
    acc: SavedAccount,
    busy: Boolean,
    disabled: Boolean,
    onSwitch: () -> Unit,
    onForget: () -> Unit,
) {
    val p = palette
    var armed by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(fieldShape())
            .background(p.cardSoft)
            // armed 时整行不可点：否则「移除」弹出来的瞬间，手还在原处，容易点成切换
            .clickable(enabled = !disabled && !armed, onClick = onSwitch)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(p.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                acc.displayName.take(1).ifBlank { acc.username.take(1).ifBlank { "?" } },
                fontSize = 15.sp,
                color = p.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(11.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    acc.displayName.ifBlank { acc.username },
                    fontSize = 14.sp,
                    color = p.text,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (acc.roleName.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        acc.roleName,
                        fontSize = 9.5.sp,
                        color = p.accent,
                        modifier = Modifier
                            .background(p.accentSoft, chipShape())
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text("@${acc.username}", fontSize = 11.sp, color = p.textWeak)
        }

        when {
            busy -> Spinner(16.dp)
            armed -> Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(chipShape())
                        .background(Color(0xFFD93025))
                        .clickable { armed = false; onForget() }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) { Text("移除", fontSize = 11.sp, color = Color.White) }
                Box(
                    Modifier
                        .clip(chipShape())
                        .clickable { armed = false }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) { Text("算了", fontSize = 11.sp, color = p.textSub) }
            }
            else -> Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { armed = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons["x"], "移除这个账号", Modifier.size(12.dp), tint = p.textWeak)
            }
        }
    }
}