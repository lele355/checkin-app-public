package cn.anlele.checkin.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.AppField
import cn.anlele.checkin.ui.components.EditSheet
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * App 内自助注册。
 *
 * 【为什么不再跳网页】以前点「有邀请码？自助注册」直接 `openUri(SITE/Register)`，
 * 用户被甩到浏览器 —— 注册完还得回 App 重新输账号密码，中间还可能掉登录态。
 * 现在原生表单直接调 `/api/v1/auth/register`，注册成功当场登录。
 *
 * 【和网页版规则完全一致】邀请码 12 位（横杠可省略）、用户名 2–20 位、
 * 密码至少 8 位且含字母和数字。校验以后端为准，前端只做「两次密码一致」这种即时提示。
 */
@Composable
fun RegisterSheet(
    vm: AppVm,
    onClose: () -> Unit,
    onRegistered: (username: String, password: String) -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    EditSheet(
        title = "注册新账号",
        onClose = onClose,
        saving = busy,
        saveText = "注册并登录",
        onSave = {
            if (code.isBlank()) { vm.toast("请填写邀请码"); return@EditSheet }
            if (username.isBlank()) { vm.toast("请填写用户名"); return@EditSheet }
            if (password.length < 8) { vm.toast("密码至少 8 位"); return@EditSheet }
            if (password != password2) { vm.toast("两次输入的密码不一致"); return@EditSheet }

            busy = true
            scope.launch {
                try {
                    Repo.register(
                        Repo.RegisterReq(
                            code = code.trim(),
                            username = username.trim(),
                            displayName = display.trim(),
                            password = password,
                        )
                    )
                    vm.toast("注册成功，正在登录…")
                    onRegistered(username.trim(), password)
                } catch (e: ApiException) {
                    vm.toast(e.message)
                } catch (e: Exception) {
                    vm.toast("注册失败，请稍后再试")
                } finally {
                    busy = false
                }
            }
        },
    ) {
        Text(
            "本站是邀请制：需要管理员发的 12 位邀请码（形如 ANLE-8F3K-2Q7M，横杠写不写都行）。",
            fontSize = 12.sp, color = p.textSub, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))

        AppField("邀请码", code, { code = it }, placeholder = "ANLE-8F3K-2Q7M")
        Spacer(Modifier.height(11.dp))
        AppField("用户名", username, { username = it }, placeholder = "登录用，2–20 位")
        Spacer(Modifier.height(11.dp))
        AppField("昵称（选填）", display, { display = it }, placeholder = "不填就用用户名")
        Spacer(Modifier.height(11.dp))
        AppField(
            "密码", password, { password = it },
            placeholder = "至少 8 位，含字母和数字",
            password = true,
        )
        Spacer(Modifier.height(11.dp))
        AppField(
            "确认密码", password2, { password2 = it },
            placeholder = "再输一遍",
            password = true,
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("注册完会直接登录，不用再输一遍。", fontSize = 11.sp, color = p.textWeak)
        }
    }
}
