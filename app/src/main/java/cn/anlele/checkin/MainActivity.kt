package cn.anlele.checkin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import cn.anlele.checkin.ui.screens.LoginScreen
import cn.anlele.checkin.ui.shell.MainShell
import cn.anlele.checkin.ui.theme.AppTheme
import cn.anlele.checkin.ui.theme.CheckinTheme
import cn.anlele.checkin.ui.theme.palette

class MainActivity : ComponentActivity() {

    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // 启动屏一直留到「本地令牌读完 + 界面有内容」为止，避免白屏闪一下
        splash.setKeepOnScreenCondition { vm.ui.booting }

        enableEdgeToEdge()

        // 点通知冷启动：先把目标页记下，登录态就绪后 MainShell 会消费
        intent?.getStringExtra("route")?.let { vm.openRoute(it) }

        setContent {
            LaunchedEffect(Unit) { vm.bootstrap() }
            AppRoot(vm)
        }
    }

    /** 点通知热启动（App 已在后台）：singleTop + 新 intent 走这里 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("route")?.let { vm.openRoute(it) }
    }
}

@Composable
fun AppRoot(vm: AppVm) {
    val state = vm.ui

    CheckinTheme(AppTheme.of(state.theme), state.accent, state.tone) {
        SystemBars(dark = palette.isDark)

        Box(Modifier.fillMaxSize().background(palette.bg)) {
            when {
                state.booting -> SplashHolder()
                state.me == null -> LoginScreen(vm)
                else -> MainShell(vm, state.me)
            }
        }
    }
}

/** 状态栏/导航栏图标颜色跟随主题深浅（macos 暗色主题时自动变白） */
@Composable
private fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val p = palette
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
        window.statusBarColor = Color.Transparent.value.toInt()
        window.navigationBarColor = Color.Transparent.value.toInt()
    }
}

/** 启动屏之后、数据到来之前的一帧（正常几乎看不到，弱网时兜底） */
@Composable
private fun SplashHolder() {
    Box(Modifier.fillMaxSize().background(palette.bg))
}
