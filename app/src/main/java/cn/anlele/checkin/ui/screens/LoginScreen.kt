package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.R
import cn.anlele.checkin.data.Api
import cn.anlele.checkin.data.Beian
import cn.anlele.checkin.data.SavedAccount
import cn.anlele.checkin.ui.components.BannerBar
import cn.anlele.checkin.ui.components.BannerTone
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.Spinner
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.AppTheme
import cn.anlele.checkin.ui.theme.Palette
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.fieldShape
import cn.anlele.checkin.ui.theme.palette

/** 站点根地址（用于「自助注册 / 隐私政策」等只能跳网页的入口） */
private val SITE: String = Api.BASE.substringBefore("/api").ifBlank { "https://example.com" }

/**
 * 密码保存方式 —— 与网页登录页的三个单选一一对应（见 Pages/Login.cshtml）：
 *   Auto    自动进入（下次跳过登录页）= 留 token + 预填账号密码
 *   Prefill 仅填充密码（下次仍到登录页）= 不留 token + 预填账号密码
 *   None    不保存 = 都不做，并把旧存档清掉
 * 落到存储上是两个独立开关（keepLogin / rememberPass），这里只是把三选一映射回去。
 */
private enum class RememberMode { Auto, Prefill, None }

private fun modeOf(keepLogin: Boolean, rememberPass: Boolean): RememberMode = when {
    keepLogin -> RememberMode.Auto
    rememberPass -> RememberMode.Prefill
    else -> RememberMode.None
}

/**
 * 登录页 —— 与网页 `/Login` 同构：
 * 一张卡片里依次是「图标 + 标题 / 说明 / 报错 / 账号 / 密码 / 记住密码三选一 / 登录 / 自助注册」，
 * 卡片下方是页脚链接与备案号。
 *
 * 【为什么用 LocalPalette 而不是固定品牌绿】
 * 网页登录页是跟着 `data-theme`/`data-tone`/`data-accent` 走的（body 底色、卡片圆角、输入框形态、
 * 按钮圆角全都变）。App 早期这一屏写死了墨绿 + 米白，于是"网页能换、App 只有一套皮"。
 * 现在整屏读当前 Palette，主题/底色/强调色一切换立刻跟着变。
 */
@Composable
fun LoginScreen(vm: AppVm) {
    val state = vm.ui
    val p = palette
    val focus = LocalFocusManager.current
    val uri = LocalUriHandler.current

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // 打开就填好上次记住的账号密码。只在「当前为空」时填，避免覆盖用户正在输入的内容。
    LaunchedEffect(state.savedUser, state.savedPass) {
        if (username.isBlank() && state.savedUser.isNotBlank()) username = state.savedUser
        if (password.isBlank() && state.savedPass.isNotBlank()) password = state.savedPass
    }

    val mode = modeOf(state.keepLogin, state.rememberPass)
    val canSubmit = username.isNotBlank() && password.isNotBlank() && !state.busy

    fun submit() {
        if (state.busy) return
        if (username.isBlank() || password.isBlank()) {
            vm.toast("请填写账号和密码")
            return
        }
        focus.clearFocus()
        vm.login(username, password)
    }

    fun switchMode(m: RememberMode) {
        vm.setLoginOptions(
            keepLogin = m == RememberMode.Auto,
            rememberPass = m != RememberMode.None,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            // clay 的纸面是渐变，其他主题是纯色 —— 统一走 bgBrush 兜底
            .then(
                if (p.bgBrush != null) Modifier.background(p.bgBrush!!)
                else Modifier.background(p.bg)
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 网页登录页是 `margin: 8vh auto 0`，不是一个真正的垂直居中 —— 这里照抄那个节奏，
        // 键盘弹出时卡片也还留得住上半屏的呼吸感。
        Spacer(Modifier.height(52.dp))

        LoginCard(p) {
            // ---------- 品牌行 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    // 用 drawable-nodpi 里的位图，不能指向 R.mipmap.ic_launcher：
                    // 那个名字解析到 mipmap-anydpi-v26/ic_launcher.xml（adaptive-icon），
                    // painterResource 只认 VectorDrawable/位图，遇到 XML 直接抛
                    // IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "日常管理",
                    // 衬线/黑体由主题决定（editorial 是衬线，其余是黑体）
                    style = MaterialTheme.typography.titleLarge,
                    color = p.text,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "自用小站，账号由管理员开通，或凭邀请码自助注册。",
                fontSize = 12.sp,
                color = p.textSub,
                lineHeight = 18.sp,
            )

            if (state.loginError != null) {
                Spacer(Modifier.height(14.dp))
                BannerBar(state.loginError, tone = BannerTone.Error)
            }

            // ---------- 本机账号：点一下直接登，不用输密码 ----------
            // 只在真存过密码时才出现。这是切换账号在「已经退出/被踢下线」状态的入口 ——
            // 否则用户每次换号都得重新把密码打一遍。
            val quick = state.accounts.filter { it.password.isNotBlank() }
            if (quick.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "本机账号",
                    fontSize = 10.5.sp,
                    color = p.textWeak,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(7.dp))
                quick.take(3).forEach { acc ->
                    QuickAccountRow(
                        acc = acc,
                        busy = state.switchingTo.equals(acc.username, ignoreCase = true),
                        disabled = state.busy || state.switchingTo != null,
                        onPick = { vm.switchAccount(acc) },
                        onForget = { vm.forgetAccount(acc.username) },
                    )
                    Spacer(Modifier.height(7.dp))
                }
                Spacer(Modifier.height(3.dp))
            }

            Spacer(Modifier.height(18.dp))

            // ---------- 账号 / 密码 ----------
            LoginField(
                label = "账号",
                value = username,
                onValue = { username = it },
                placeholder = "你的用户名",
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(14.dp))
            LoginField(
                label = "密码",
                value = password,
                onValue = { password = it },
                placeholder = "密码",
                password = true,
                imeAction = ImeAction.Done,
                onDone = { submit() },
            )

            // ---------- 记住密码（三选一，文案与网页一致） ----------
            Spacer(Modifier.height(18.dp))
            Text(
                "记住密码",
                fontSize = 10.5.sp,
                color = p.textWeak,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(5.dp))
            RememberRow("自动进入（下次跳过登录页）", mode == RememberMode.Auto) {
                switchMode(RememberMode.Auto)
            }
            RememberRow("仅填充密码（下次仍到登录页）", mode == RememberMode.Prefill) {
                switchMode(RememberMode.Prefill)
            }
            RememberRow("不保存", mode == RememberMode.None) {
                switchMode(RememberMode.None)
            }

            // ---------- 登录 ----------
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = if (state.busy) "正在登录…" else "登 录",
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit,
                loading = state.busy,
                onClick = { submit() },
            )

            // ---------- 自助注册（网页专属流程，跳浏览器） ----------
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("有邀请码？", fontSize = 12.sp, color = p.textSub)
                Text(
                    "自助注册",
                    fontSize = 12.sp,
                    color = p.accent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(chipShape())
                        .clickable { runCatching { uri.openUri("$SITE/Register") } }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---------- 页脚（与网页登录页一致：链接行 + 备案行 + 版本行） ----------
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FootLink("关于") { runCatching { uri.openUri("$SITE/About") } }
            FootDot()
            FootLink("隐私政策") { runCatching { uri.openUri("$SITE/Privacy") } }
            FootDot()
            FootLink("用户协议") { runCatching { uri.openUri("$SITE/Terms") } }
        }
        Spacer(Modifier.height(4.dp))
        // 法规要求：备案号要在显著位置可见且能点进官方查询页。
        // App 用「-2A」这一号（网站是「-1」），公安备案号是独立编号。
        // 和网页一样排成一行：字号压到 9.5sp 才塞得下，超窄屏会各自缩到省略号，
        // 所以给每个号 maxLines=1 而不是靠外层截断。
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BeianFoot(Beian.appIcp(vm.ui.beiAn)) { runCatching { uri.openUri(Beian.ICP_URL) } }
            Text("·", fontSize = 9.5.sp, color = p.textWeak)
            BeianFoot(Beian.POLICE) {
                runCatching { uri.openUri(Beian.POLICE_URL) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "日常管理 v${cn.anlele.checkin.BuildConfig.VERSION_NAME} · 仅限受邀成员",
            fontSize = 10.sp,
            color = p.textWeak.copy(alpha = .8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * 卡片外壳 —— 不直接用 AppCard，因为登录卡片在网页端是「一屏主容器」：
 * 圆角取 radiusCard、背景走 cardBrush（clay 渐变）、有边框就描边框、新拟物给外阴影。
 * 与 AppCard 的差别只有内边距更大（网页登录卡片 padding 20px，窄屏 11px，这里折中 18dp）。
 */
@Composable
private fun LoginCard(p: Palette, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(p.radiusCard)
    var m = Modifier.fillMaxWidth().clip(shape)
    m = if (p.cardBrush != null) m.background(p.cardBrush!!, shape) else m.background(p.card, shape)
    if (p.border != Color.Transparent) m = m.border(1.dp, p.border, shape)
    Column(m.padding(horizontal = 18.dp, vertical = 18.dp)) { content() }
}

/**
 * 输入框 —— 按主题换形态，对齐 themes.css 里各主题对 `.field input` 的覆盖：
 *   editorial   去边框只留底线（focus 时底线变墨色，label 变斜体）
 *   natural     白色胶囊（999px）
 *   neumorphism 浅底无边框、圆角 12
 *   macos       底色比卡片更深一档、细边框
 *   clay        浅底无边框、圆角 20
 */
@Composable
private fun LoginField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    val p = palette
    var reveal by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val editorial = p.theme == AppTheme.Editorial

    Column {
        Text(
            label,
            fontSize = if (editorial) 11.5.sp else 12.5.sp,
            color = if (focused && editorial) p.text else p.textSub,
            fontWeight = if (editorial) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = if (editorial) 0.5.sp else 0.sp,
            // 网页 `[data-theme="editorial"] .field:focus-within label { font-style: italic }`
            fontStyle = if (focused && editorial) FontStyle.Italic else FontStyle.Normal,
            modifier = Modifier.padding(bottom = 5.dp),
        )

        val boxShape = RoundedCornerShape(
            when (p.theme) {
                AppTheme.Natural -> 999.dp
                AppTheme.Editorial -> 0.dp
                else -> p.radiusField
            }
        )
        val boxBg = when (p.theme) {
            AppTheme.Editorial -> Color.Transparent
            AppTheme.Natural -> Color.White
            AppTheme.Neumorphism -> p.cardSoft
            AppTheme.Macos -> p.bg
            AppTheme.Clay -> p.cardSoft
        }
        val padH = when (p.theme) {
            AppTheme.Editorial -> 0.dp
            AppTheme.Natural -> 17.dp
            else -> 13.dp
        }

        var box = Modifier
            .fillMaxWidth()
            .heightIn(min = if (editorial) 40.dp else 46.dp)
            .clip(boxShape)
            .background(boxBg, boxShape)

        // 边框：editorial 不描边（改用底线），其余主题描一圈
        if (!editorial) {
            val borderColor = when {
                focused -> p.accent
                p.border != Color.Transparent -> p.border
                else -> p.borderStrong.copy(alpha = .35f)
            }
            // natural 的边框本来就是强边框色（米色主题看不清浅边）
            val bc = if (p.theme == AppTheme.Natural && !focused) p.borderStrong else borderColor
            box = box.border(1.dp, bc, boxShape)
        } else {
            val line = if (focused) p.text else p.borderStrong.copy(alpha = .35f)
            box = box.drawBehind {
                val w = 1.dp.toPx()
                drawLine(
                    color = line,
                    start = Offset(0f, size.height - w / 2),
                    end = Offset(size.width, size.height - w / 2),
                    strokeWidth = w,
                )
            }
        }

        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 15.sp),
            cursorBrush = SolidColor(p.accent),
            interactionSource = interaction,
            visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            decorationBox = { inner ->
                Row(
                    box.padding(horizontal = padH),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(placeholder, fontSize = 15.sp, color = p.textWeak)
                        }
                        inner()
                    }
                    if (password) {
                        Icon(
                            AppIcons[if (reveal) "eye-off" else "eye"],
                            if (reveal) "隐藏密码" else "显示密码",
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { reveal = !reveal }
                                .padding(6.dp),
                            tint = if (reveal) p.accent else p.textWeak,
                        )
                    }
                }
            },
        )
    }
}

/**
 * 登录页上的「本机账号」快捷行 —— 点一下就登进去。
 *
 * 和 RememberRow 的区别：那个是选项（单选手感），这个是「换个人进去」（按钮手感），
 * 所以做成整行可点的胶囊 + 头像，视觉上更接近账号列表而不是设置项。
 */
@Composable
private fun QuickAccountRow(
    acc: SavedAccount,
    busy: Boolean,
    disabled: Boolean,
    onPick: () -> Unit,
    onForget: () -> Unit,
) {
    val p = palette
    var armed by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(fieldShape())
            .background(p.cardSoft)
            .clickable(enabled = !disabled && !armed, onClick = onPick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).background(p.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                acc.displayName.take(1).ifBlank { acc.username.take(1) },
                fontSize = 12.5.sp, color = p.accent, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                acc.displayName.ifBlank { acc.username },
                fontSize = 13.sp, color = p.text, fontWeight = FontWeight.Medium, maxLines = 1,
            )
            Text("@${acc.username}", fontSize = 10.5.sp, color = p.textWeak, maxLines = 1)
        }
        when {
            busy -> Spinner(15.dp)
            armed -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(chipShape())
                        .clickable { armed = false; onForget() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) { Text("移除", fontSize = 10.5.sp, color = Color(0xFFD93025)) }
                Box(
                    Modifier
                        .clip(chipShape())
                        .clickable { armed = false }
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                ) { Text("算了", fontSize = 10.5.sp, color = p.textWeak) }
            }
            else -> Box(
                Modifier.size(26.dp).clip(CircleShape).clickable { armed = true },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons["x"], "移除此账号", Modifier.size(11.dp), tint = p.textWeak) }
        }
    }
}

/** 「记住密码」单选项：圆点 + 一行文案，样子对齐网页的 radio 列表 */
@Composable
private fun RememberRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(chipShape())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) p.accent else p.borderStrong.copy(alpha = .55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(p.accent))
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            fontSize = 12.5.sp,
            color = if (selected) p.text else p.textSub,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun FootLink(text: String, onClick: () -> Unit) {
    val p = palette
    Text(
        text,
        fontSize = 11.sp,
        color = p.textSub,
        modifier = Modifier
            .clip(chipShape())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun FootDot() {
    val p = palette
    Text("·", fontSize = 11.sp, color = p.textWeak)
}

@Composable
private fun BeianFoot(text: String, onClick: () -> Unit) {
    val p = palette
    Text(
        text,
        fontSize = 9.5.sp,
        color = p.textWeak,
        maxLines = 1,
        modifier = Modifier
            .clip(chipShape())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
    )
}