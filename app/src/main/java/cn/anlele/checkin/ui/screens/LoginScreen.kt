package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.R
import cn.anlele.checkin.ui.icons.AppIcons

private val BrandGreen = Color(0xFF0F6E56)
private val BrandGreenDark = Color(0xFF0A5342)
private val PaperBg = Color(0xFFF9F8F6)
private val Ink = Color(0xFF1C1C1C)

/**
 * 登录页 —— 用固定的品牌色（不跟个人主题走）：
 * 登录前还不知道是谁，这一屏就是 App 的「封面」，跟网站登录页同一套米白 + 墨绿。
 */
@Composable
fun LoginScreen(vm: AppVm) {
    val state = vm.ui
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    fun submit() {
        if (state.busy) return
        focus.clearFocus()
        vm.login(username, password)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PaperBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))

        // ---- 品牌区 ----
        Box(
            Modifier
                .size(76.dp)
                .background(Brush.linearGradient(listOf(BrandGreen, BrandGreenDark)), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "日常管理",
            fontSize = 26.sp,
            color = Ink,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text("记录每一天的小事", fontSize = 13.5.sp, color = Ink.copy(alpha = .45f))

        Spacer(Modifier.height(40.dp))

        // ---- 表单卡片 ----
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.dp, Ink.copy(alpha = .08f), RoundedCornerShape(18.dp))
                .padding(20.dp),
        ) {
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

            if (state.loginError != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFEBEA), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(state.loginError, fontSize = 12.5.sp, color = Color(0xFFB3261E))
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(
                        if (username.isBlank() || password.isBlank()) BrandGreen.copy(alpha = .4f) else BrandGreen,
                        RoundedCornerShape(12.dp),
                    )
                    .then(
                        if (username.isNotBlank() && password.isNotBlank() && !state.busy)
                            Modifier.androidClickable { submit() } else Modifier
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("正在登录…", color = Color.White, fontSize = 15.sp)
                } else {
                    Text("登 录", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "自用小站 · 仅限受邀成员",
            fontSize = 12.sp,
            color = Ink.copy(alpha = .3f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
    }
}

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
    Column {
        Text(label, fontSize = 12.5.sp, color = Ink.copy(alpha = .5f), modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Ink.copy(alpha = .3f), fontSize = 14.5.sp) },
            singleLine = true,
            shape = RoundedCornerShape(11.dp),
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                color = Ink, fontSize = 15.sp,
            ),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandGreen,
                unfocusedBorderColor = Ink.copy(alpha = .12f),
                focusedContainerColor = Color(0xFFFAFAF8),
                unfocusedContainerColor = Color(0xFFFAFAF8),
                cursorColor = BrandGreen,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
            ),
        )
    }
}

/** 无涟漪点击（登录页按钮不需要水波纹，按下反馈由颜色变化承担） */
private fun Modifier.androidClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )
