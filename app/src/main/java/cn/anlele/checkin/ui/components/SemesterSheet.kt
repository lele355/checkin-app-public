package cn.anlele.checkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.data.ApiException
import cn.anlele.checkin.data.Repo
import cn.anlele.checkin.data.SemesterDto
import cn.anlele.checkin.ui.icons.AppIcons
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.Dates
import kotlinx.coroutines.launch

/**
 * 学期管理弹层：看所有学期、切换当前、新建、删除。
 *
 * 【为什么是弹层而不是又一个页面】这一屏最多三五条，看完就关。
 * 开整页要占一次返回，且课表页本身还得留入口 —— 弹层更省。
 *
 * 【删除为什么要点两次】删学期会**连带删掉它下面的所有课**（外键级联），
 * 这是不可撤销的。第一下把按钮变成「确认删除」，3 秒不点自动退回，
 * 与站内其它危险操作同一套交互。
 */
@Composable
fun SemesterSheet(
    vm: AppVm,
    currentId: Int,
    onClose: () -> Unit,
    onChanged: () -> Unit,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<SemesterDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // 新建表单
    var newName by remember { mutableStateOf("") }
    var newStart by remember { mutableStateOf(Dates.today()) }

    // 二次确认：记住「哪一行正在等确认」
    var confirmDelete by remember { mutableStateOf(0) }

    suspend fun load() {
        loading = true
        try {
            list = Repo.semesters().items
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = "连不上服务器，稍后再试"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // 3 秒没再点就撤销「确认删除」，避免误触后一直挂着
    LaunchedEffect(confirmDelete) {
        if (confirmDelete == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(3000)
        confirmDelete = 0
    }

    PickerSheetShell(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("学期", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "每个学期一份课表。切换只改「现在看哪一学期」；删除会把它下面的课一起删掉。",
                fontSize = 12.sp, color = p.textWeak, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))

            if (loading && list.isEmpty()) {
                Text("正在加载…", fontSize = 12.5.sp, color = p.textWeak)
            }
            if (error != null) {
                Text(error!!, fontSize = 12.5.sp, color = p.textSub)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("重试", Modifier.fillMaxWidth()) { scope.launch { load() } }
            }

            list.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(chipShape())
                        .background(if (s.isCurrent) p.accentSoft else p.cardSoft)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name.ifBlank { "未命名" }, fontSize = 13.5.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                            if (s.isCurrent) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.background(p.accent, chipShape()).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                    Text("当前", fontSize = 9.5.sp, color = p.onAccent)
                                }
                            }
                        }
                        Text(
                            "${Dates.dayMonth(s.startDate)} 起 · ${s.courseCount} 门课",
                            fontSize = 11.sp, color = p.textWeak,
                        )
                    }
                    if (!s.isCurrent) {
                        TinyAction("切换", "arrow-left-right") {
                            if (busy) return@TinyAction
                            busy = true
                            scope.launch {
                                try {
                                    Repo.switchSemester(s.id)
                                    onChanged()
                                    onClose()
                                } catch (e: Exception) {
                                    vm.toast((e as? ApiException)?.message ?: "切换失败")
                                } finally { busy = false }
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                    }
                    TinyAction(
                        if (confirmDelete == s.id) "确认删除" else "删除",
                        if (confirmDelete == s.id) null else "trash-2",
                        danger = confirmDelete == s.id,
                    ) {
                        if (busy) return@TinyAction
                        if (confirmDelete != s.id) { confirmDelete = s.id; return@TinyAction }
                        confirmDelete = 0
                        busy = true
                        scope.launch {
                            try {
                                Repo.deleteSemester(s.id)
                                onChanged()
                                load()
                            } catch (e: Exception) {
                                vm.toast((e as? ApiException)?.message ?: "删除失败")
                            } finally { busy = false }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text("新建学期", fontSize = 12.5.sp, color = p.textSub, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AppField(
                        label = "",
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "2027 春（留空自动命名）",
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
                DateField(
                    label = "",
                    value = newStart,
                    placeholder = "开学第一周的哪天",
                    onChange = { newStart = it },
                )
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("新建并切过去", Modifier.fillMaxWidth(), loading = busy) {
                busy = true
                scope.launch {
                    try {
                        Repo.createSemester(newName.trim(), Dates.mondayOf(newStart))
                        onChanged()
                        onClose()
                    } catch (e: Exception) {
                        vm.toast((e as? ApiException)?.message ?: "新建失败")
                    } finally { busy = false }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
