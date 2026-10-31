package cn.anlele.checkin.ui.games

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.ui.theme.popShape

/**
 * 规则教程弹层 —— 对应网页教程页，五节纯文字。
 * 入口=大厅/对局页「规则」按钮，卡片不加入口（铁律 0.2）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialSheet(
    gameName: String,
    sections: List<TutorialSection>,
    error: String? = null,
    onClose: () -> Unit,
) {
    val p = palette
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = p.card,
        shape = p.popShape(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text("$gameName · 规则", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Spacer(Modifier.height(16.dp))

            if (error != null) {
                Text(error, fontSize = 14.sp, color = p.textSub)
            }

            for (section in sections) {
                Text(section.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Spacer(Modifier.height(6.dp))
                for (para in section.paras) {
                    Text(para, fontSize = 13.sp, color = p.textSub, lineHeight = 20.sp)
                    Spacer(Modifier.height(3.dp))
                }
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(26.dp))
        }
    }
}

data class TutorialSection(
    val title: String = "",
    val paras: List<String> = emptyList(),
)

/** 五子棋教程（与服务端 GameTutorials.Gomoku 同源） */
val GomokuTutorial = listOf(
    TutorialSection("目标", listOf(
        "在棋盘上横、竖、斜任意方向先连成五子（或以上）即获胜。",
        "黑棋先手，白棋后手，双方轮流落子。",
    )),
    TutorialSection("胜负判定", listOf(
        "先连成五子的一方获胜。",
        "棋盘下满且无人成五，判为和棋。",
        "标准规则下，黑方连成六子以上（长连）判负；自由规则下长连也算赢。",
    )),
    TutorialSection("走法", listOf(
        "轮流在棋盘交叉点上落子，棋子落下后不能移动。",
        "棋盘可选 13 路、15 路或 19 路。",
    )),
    TutorialSection("特殊规则", listOf(
        "标准规则：黑方长连（六连以上）判负，白方长连仍算赢。",
        "自由规则：先连成五子或以上即胜，不限长连。",
        "限时规则：可设单步限时与每方总局时，超时判负。",
    )),
    TutorialSection("常见新手误区", listOf(
        "只顾自己连子，忘了堵对方的四连。",
        "在角落落子容易被围，中间区域更灵活。",
        "注意观察对方的活三（两端开放的三连），及时堵截。",
    )),
)

/** 黑白棋教程（与服务端 GameTutorials.Reversi 同源） */
val ReversiTutorial = listOf(
    TutorialSection("目标", listOf(
        "在 8×8 棋盘上，用自己的棋子夹住对方棋子并将其翻转为己方颜色。",
        "终局时棋盘上己方棋子多的一方获胜。",
    )),
    TutorialSection("胜负判定", listOf(
        "双方都无处可落时终局，子多者胜。",
        "32:32 平局判为和棋。",
    )),
    TutorialSection("走法", listOf(
        "轮流在空格上落子，必须夹住至少一枚对方棋子（横、竖、斜 8 个方向）。",
        "落子后，被夹住的所有对方棋子翻转为己方颜色。",
        "如果一方无处可落，自动跳过（pass），由对方继续。",
    )),
    TutorialSection("特殊规则", listOf(
        "初始棋盘中央有四枚棋子（黑白各二）。",
        "角落位置一旦占据，对方无法翻转，是战略要地。",
        "边线位置也较稳定，但不如角落安全。",
    )),
    TutorialSection("常见新手误区", listOf(
        "只看翻子数量，忽略了位置价值。角落比中间更有价值。",
        "过早占据 X 位（角旁边的对角格），会让对方拿到角落。",
        "无处可落时不要慌，自动跳过是正常机制。",
    )),
)

/**
 * 本地兜底教程 —— 仅 gomoku/reversi 有副本（离线时用）。
 * 其它游戏拉不到教程时显示「暂不可用」，绝不回落到错误教程（给错规则比没规则更糟）。
 */
fun tutorialFor(gameKey: String): List<TutorialSection>? = when (gameKey) {
    "gomoku" -> GomokuTutorial
    "reversi" -> ReversiTutorial
    else -> null  // 其它游戏不给本地兜底，由调用方处理 null = 暂不可用
}
