package cn.anlele.checkin.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.AppChip
import cn.anlele.checkin.ui.components.DateField
import cn.anlele.checkin.ui.components.LoadingBlock
import cn.anlele.checkin.ui.components.PrimaryButton
import cn.anlele.checkin.ui.components.SecondaryButton
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.palette
import kotlinx.coroutines.launch
import java.io.File

/**
 * 导出 —— 把自己名下所有数据打包成一个 JSON，存到 App 的私有目录再调系统分享。
 * 发到微信/QQ/邮箱都由系统分享面板处理，App 自己不碰网络。
 *
 * 两种导法：全量备份（什么都导），自定义导出（勾模块 + 定时间范围）。
 * 服务端保证：只要导了记账，条目（合并容器）的定义就跟着一起走，不会被时间范围切掉。
 */
private val EXPORT_MODULES = listOf(
    "schedules" to "日程",
    "memos" to "备忘",
    "habits" to "打卡",
    "courses" to "课程",
    "expenses" to "记账",
    "anniversaries" to "纪念日",
    "period" to "经期",
    "board" to "留言",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(vm: AppVm) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lastFile by remember { mutableStateOf<String?>(null) }

    // 自定义导出：模块默认全选（用户只取消不要的），时间默认不限
    var selModules by remember { mutableStateOf(EXPORT_MODULES.map { it.first }.toSet()) }
    var expFrom by remember { mutableStateOf("") }
    var expTo by remember { mutableStateOf("") }

    fun share(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "分享备份文件"))
    }

    fun doExport(modules: List<String>, from: String, to: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val d = Repo.exportAll(modules, from, to)
                val dir = File(ctx.getExternalFilesDir(null), "export").apply { mkdirs() }
                val f = File(dir, d.filename.ifBlank { "日常管理-导出.json" })
                f.writeText(d.content, Charsets.UTF_8)
                lastFile = f.absolutePath
                vm.toast("已生成：${f.name}")
                share(f, "application/json")
            } catch (e: Exception) {
                vm.toast("导出失败：${e.message ?: "未知错误"}")
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeadCard(title = "数据导出", hint = "导出文件不含密码")
        AppCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons["download"], null, Modifier.size(18.dp), tint = p.accent)
                    Spacer(Modifier.width(9.dp))
                    Text("全量备份", fontSize = 15.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "把日程、备忘、打卡、记账、课程、纪念日、经期、留言打包成一个 JSON 文件。",
                    fontSize = 12.5.sp,
                    color = p.textSub,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(14.dp))
                if (busy) LoadingBlock("正在打包…") else
                    PrimaryButton("生成并分享", Modifier.fillMaxWidth(), icon = "download",
                        onClick = { doExport(emptyList(), "", "") })
            }
        }

        // ---------- 自定义导出：勾模块 + 定时间范围 ----------
        AppCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons["settings-2"], null, Modifier.size(18.dp), tint = p.accent)
                    Spacer(Modifier.width(9.dp))
                    Text("自定义导出", fontSize = 15.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))

                Text("模块", fontSize = 12.sp, color = p.textWeak)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EXPORT_MODULES.forEach { (key, label) ->
                        AppChip(label, selected = key in selModules) {
                            selModules = if (key in selModules) selModules - key else selModules + key
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("时间范围（留空 = 不限）", fontSize = 12.sp, color = p.textWeak)
                Spacer(Modifier.height(8.dp))
                DateField("开始日期", expFrom, { expFrom = it }, clearable = true)
                Spacer(Modifier.height(12.dp))
                DateField("结束日期", expTo, { expTo = it }, clearable = true)

                Spacer(Modifier.height(16.dp))
                if (busy) LoadingBlock("正在打包…") else
                    PrimaryButton(
                        "生成并分享",
                        Modifier.fillMaxWidth(),
                        icon = "download",
                        enabled = selModules.isNotEmpty() && (expFrom.isBlank() || expTo.isBlank() || expFrom <= expTo),
                        onClick = { doExport(selModules.sorted(), expFrom, expTo) },
                    )
            }
        }

        lastFile?.let { path ->
            AppCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Column {
                    Text("上次导出", fontSize = 12.5.sp, color = p.textSub)
                    Spacer(Modifier.height(4.dp))
                    Text(path, fontSize = 11.sp, color = p.textWeak)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("再分享一次", icon = "send") {
                        val f = File(path)
                        if (f.exists()) share(f, "application/json") else vm.toast("文件已不在了，重新生成吧")
                    }
                }
            }
        }

        AppCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Column {
                Text("小提示", fontSize = 13.sp, color = p.text, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 导出的是你自己的数据，不含别人的。\n" +
                        "· 邮件内容不在备份里（只同步信头和正文缓存，不进导出）。\n" +
                        "· 想恢复？找开发者按 JSON 写回库即可。",
                    fontSize = 12.sp,
                    color = p.textSub,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}
