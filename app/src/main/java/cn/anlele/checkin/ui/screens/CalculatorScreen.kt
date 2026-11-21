package cn.anlele.checkin.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.anlele.checkin.AppVm
import cn.anlele.checkin.ui.components.AppCard
import cn.anlele.checkin.ui.components.PageHeadCard
import cn.anlele.checkin.ui.components.pressable
import cn.anlele.checkin.ui.components.StaggerIn
import cn.anlele.checkin.ui.components.ViewSwitch
import cn.anlele.checkin.ui.theme.chipShape
import cn.anlele.checkin.ui.theme.palette
import cn.anlele.checkin.util.CalcEngine
import cn.anlele.checkin.util.MatrixCalc

/**
 * 数学计算器 —— 三种模式：简单（手机式立即计算）/ 科学（表达式求值）/ 矩阵（2~4 阶）。
 * 全部本地运算，不走服务端。
 */
@Composable
fun CalculatorScreen(vm: AppVm) {
    val p = palette
    var mode by remember { mutableStateOf("简单") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeadCard(
                title = "计算器",
                subRow = {
                    ViewSwitch(
                        options = listOf("simple" to "简单", "science" to "科学", "matrix" to "矩阵"),
                        selected = mode,
                        onSelect = { mode = it },
                        fullWidth = true,
                    )
                },
            )
        }
        item {
            StaggerIn(0) {
                when (mode) {
                    "科学" -> ScienceCalc()
                    "矩阵" -> MatrixCalcPanel(vm)
                    else -> SimpleCalc()
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

// ==================== 按键 ====================

/** 计算器按键：数字/运算统一外观，fn 键走卡内浅底、op 键走强调色 */
@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier = Modifier,
    kind: KeyKind = KeyKind.Num,
    onClick: () -> Unit,
) {
    val p = palette
    val bg = when (kind) {
        KeyKind.Num -> p.cardSoft
        KeyKind.Fn -> p.cardSoft
        KeyKind.Op -> p.accent
    }
    val fg = when (kind) {
        KeyKind.Num -> p.text
        KeyKind.Fn -> p.textSub
        KeyKind.Op -> p.onAccent
    }
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(maxOf(p.radiusBtn, 12.dp)))
            .background(bg)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = when {
                label.length > 4 -> 10.5.sp
                label.length > 2 -> 13.sp
                else -> 18.sp
            },
            color = fg, fontWeight = FontWeight.Medium,
        )
    }
}

private enum class KeyKind { Num, Fn, Op }

/** 显示屏：大数字右对齐，长内容省略号 */
@Composable
private fun CalcDisplay(text: String, sub: String = "") {
    val p = palette
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        if (sub.isNotBlank()) {
            Text(
                sub, fontSize = 13.sp, color = p.textWeak,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text, fontSize = 34.sp, color = p.text, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ==================== 简单计算器 ====================

/** 手机式立即计算：数字 → 运算符 → 数字 → = */
@Composable
private fun SimpleCalc() {
    val p = palette
    var display by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<Char?>(null) }
    var waiting by remember { mutableStateOf(true) }   // true = 下一数字键重开输入
    var lastExpr by remember { mutableStateOf("") }

    fun apply(op: Char, a: Double, b: Double): Double = when (op) {
        '+' -> a + b; '-' -> a - b; '×' -> a * b
        '÷' -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    fun digit(d: String) {
        if (waiting) {
            display = if (d == ".") "0." else d
            waiting = false
        } else {
            if (d == "." && display.contains('.')) return
            if (display == "0" && d != ".") display = d
            else if (display.length < 15) display += d
        }
    }

    fun op(o: Char) {
        val cur = display.toDoubleOrNull() ?: 0.0
        if (pendingOp != null && !waiting) {
            val r = apply(pendingOp!!, acc ?: 0.0, cur)
            acc = r
            display = if (r.isNaN()) "不能除以 0" else CalcEngine.fmt(r)
        } else {
            acc = cur
        }
        pendingOp = o
        waiting = true
        lastExpr = "${CalcEngine.fmt(acc ?: 0.0)} $o"
    }

    fun equals() {
        val cur = display.toDoubleOrNull() ?: return
        val o = pendingOp ?: return
        val a = acc ?: 0.0
        lastExpr = "${CalcEngine.fmt(a)} $o ${CalcEngine.fmt(cur)} ="
        val r = apply(o, a, cur)
        display = if (r.isNaN()) "不能除以 0" else CalcEngine.fmt(r)
        acc = r
        pendingOp = null
        waiting = true
    }

    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column {
            CalcDisplay(display, lastExpr)
            Spacer(Modifier.height(8.dp))
            val pad = Arrangement.spacedBy(8.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = pad) {
                CalcKey("AC", Modifier.weight(1f), KeyKind.Fn) {
                    display = "0"; acc = null; pendingOp = null; waiting = true; lastExpr = ""
                }
                CalcKey("±", Modifier.weight(1f), KeyKind.Fn) {
                    display = if (display.startsWith("-")) display.drop(1) else if (display != "0") "-$display" else display
                }
                CalcKey("%", Modifier.weight(1f), KeyKind.Fn) {
                    display = CalcEngine.fmt((display.toDoubleOrNull() ?: 0.0) / 100.0); waiting = true
                }
                CalcKey("÷", Modifier.weight(1f), KeyKind.Op) { op('÷') }
            }
            Spacer(Modifier.height(8.dp))
            listOf(listOf("7", "8", "9", "×"), listOf("4", "5", "6", "-"), listOf("1", "2", "3", "+")).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = pad) {
                    row.forEach { k ->
                        CalcKey(k, Modifier.weight(1f), if (k.length == 1 && k[0] in "+-×÷") KeyKind.Op else KeyKind.Num) {
                            if (k.length == 1 && k[0] in "+-×÷") op(k[0]) else digit(k)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = pad) {
                CalcKey("0", Modifier.weight(2.1f)) { digit("0") }
                CalcKey(".", Modifier.weight(1f)) { digit(".") }
                CalcKey("=", Modifier.weight(1f), KeyKind.Op) { equals() }
            }
        }
    }
}

// ==================== 科学计算器 ====================

/**
 * 科学计算器：表达式求值，键位对齐卡西欧 82MS 的功能面 ——
 * 三角/反三角/双曲、幂/开方（含 n 次方根）、阶乘/排列/组合、倒数/百分号、
 * EXP 科学计数、Ans 上次结果、DEG/RAD 角度制切换。
 */
@Composable
private fun ScienceCalc() {
    val p = palette
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var ans by remember { mutableStateOf(0.0) }
    var deg by remember { mutableStateOf(true) }

    fun put(s: String) { expr += s; error = ""; result = "" }
    fun back() { expr = expr.dropLast(1); error = ""; result = "" }
    fun calc() {
        try {
            val v = CalcEngine.eval(expr, ans, deg)
            ans = v
            result = CalcEngine.fmt(v)
            error = ""
        } catch (e: CalcEngine.CalcException) {
            error = e.message ?: "算不出来"
        } catch (e: Exception) {
            error = "算不出来"
        }
    }

    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
        Column {
            // 角度制指示 + 上次结果（Ans）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(chipShape())
                        .background(if (deg) p.accentSoft else p.cardSoft)
                        .clickable { deg = !deg }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(if (deg) "DEG" else "RAD", fontSize = 10.5.sp, color = p.textSub, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                Text("Ans = ${CalcEngine.fmt(ans)}", fontSize = 10.5.sp, color = p.textWeak, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            // 表达式（可编辑）
            BasicTextField(
                value = expr,
                onValueChange = {
                    expr = it.filter { c -> c.isLetterOrDigit() || c in "+-*/^%!.()×÷⁻¹VP" }.take(100)
                    error = ""; result = ""
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 17.sp, color = p.text, fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            if (result.isNotBlank()) {
                Text(
                    "= $result", fontSize = 22.sp, color = p.accentStrong, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (error.isNotBlank()) {
                Text(error, fontSize = 11.5.sp, color = p.textWeak, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))

            // 82MS 式键位：上半区科学函数（5 列），下半区数字键盘
            val pad = Arrangement.spacedBy(5.dp)
            listOf(
                listOf("DEG", "Ans", "π", "e", "AC"),
                listOf("sin", "cos", "tan", "(", ")"),
                listOf("sin⁻¹", "cos⁻¹", "tan⁻¹", "ln", "log"),
                listOf("sinh", "cosh", "tanh", "eˣ", "10ˣ"),
                listOf("x²", "x³", "xʸ", "√", "∛"),
                listOf("ⁿ√x", "1/x", "n!", "nPr", "nCr"),
            ).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = pad) {
                    row.forEach { k ->
                        CalcKey(k, Modifier.weight(1f), KeyKind.Fn) {
                            when (k) {
                                "DEG" -> deg = !deg
                                "Ans" -> put("Ans")
                                "π" -> put("pi")
                                "e" -> put("e")
                                "AC" -> { expr = ""; result = ""; error = "" }
                                "sin", "cos", "tan", "sinh", "cosh", "tanh", "ln", "log" -> put("$k(")
                                "sin⁻¹" -> put("asin(")
                                "cos⁻¹" -> put("acos(")
                                "tan⁻¹" -> put("atan(")
                                "eˣ" -> put("exp(")
                                "10ˣ" -> put("10^(")
                                "x²" -> put("^2")
                                "x³" -> put("^3")
                                "xʸ" -> put("^")
                                "√" -> put("sqrt(")
                                "∛" -> put("cbrt(")
                                "ⁿ√x" -> put(" V ")       // a V b = a 的 b 次方根
                                "1/x" -> put("⁻¹")
                                "n!" -> put("!")
                                "nPr" -> put(" P ")
                                "nCr" -> put(" C ")
                                else -> put(k)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            listOf(
                listOf("7", "8", "9", "÷", "DEL"),
                listOf("4", "5", "6", "×", "AC"),
                listOf("1", "2", "3", "−", "%"),
                listOf("0", ".", "+", "EXP", "="),
            ).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = pad) {
                    row.forEach { k ->
                        val kind = when {
                            k == "=" -> KeyKind.Op
                            k in listOf("÷", "×", "−", "+") -> KeyKind.Op
                            else -> KeyKind.Num
                        }
                        CalcKey(k, Modifier.weight(1f), kind) {
                            when (k) {
                                "DEL" -> back()
                                "AC" -> { expr = ""; result = ""; error = "" }
                                "=" -> calc()
                                "EXP" -> put("×10^")
                                "−" -> put("-")
                                "÷" -> put("/")
                                "×" -> put("*")
                                "%" -> put("%")
                                else -> put(k)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

// ==================== 矩阵计算器 ====================

/**
 * 2~4 阶矩阵：A/B 可编辑，支持 加/减/乘/行列式/逆/转置。
 * 单元格是数字输入框；结果显示在下方卡片，可一键设为 A 继续算。
 */
@Composable
private fun MatrixCalcPanel(vm: AppVm) {
    val p = palette
    var n by remember { mutableStateOf(2) }
    var aTexts by remember { mutableStateOf(List(4) { List(4) { "0" } }) }
    var bTexts by remember { mutableStateOf(List(4) { List(4) { "0" } }) }
    var result by remember { mutableStateOf<List<List<Double>>?>(null) }
    var resultLabel by remember { mutableStateOf("") }
    var scalar by remember { mutableStateOf("") }   // 行列式结果等标量
    var error by remember { mutableStateOf("") }

    fun parse(m: List<List<String>>): List<List<Double>> = List(n) { i ->
        List(n) { j ->
            m[i][j].trim().toDoubleOrNull() ?: throw CalcEngine.CalcException("第 ${i + 1} 行第 ${j + 1} 列不是数字")
        }
    }

    fun run(op: String) {
        error = ""
        scalar = ""
        result = null
        try {
            val a = parse(aTexts)
            when (op) {
                "add" -> { result = MatrixCalc.add(a, parse(bTexts)); resultLabel = "A + B" }
                "sub" -> { result = MatrixCalc.sub(a, parse(bTexts)); resultLabel = "A − B" }
                "mul" -> { result = MatrixCalc.mul(a, parse(bTexts)); resultLabel = "A × B" }
                "det" -> { scalar = MatrixCalc.fmt(MatrixCalc.det(a)); resultLabel = "det(A)" }
                "inv" -> { result = MatrixCalc.inv(a); resultLabel = "A⁻¹" }
                "tr" -> { result = MatrixCalc.transpose(a); resultLabel = "Aᵀ" }
            }
        } catch (e: CalcEngine.CalcException) {
            error = e.message ?: "算不出来"
        } catch (e: Exception) {
            error = "算不出来"
        }
    }

    @Composable
    fun MatrixGrid(title: String, texts: List<List<String>>, onChange: (Int, Int, String) -> Unit) {
        Column {
            Text(title, fontSize = 12.5.sp, color = p.textSub, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            repeat(n) { i ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(n) { j ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(chipShape())
                                .background(p.cardSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicTextField(
                                value = texts[i][j],
                                onValueChange = { v ->
                                    onChange(i, j, v.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(10))
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.5.sp, color = p.text, textAlign = TextAlign.Center,
                                ),
                                cursorBrush = SolidColor(p.accent),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    AppCard(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column {
            // 阶数选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("阶数", fontSize = 12.5.sp, color = p.textSub)
                Spacer(Modifier.width(10.dp))
                listOf(2, 3, 4).forEach { k ->
                    val on = k == n
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(chipShape())
                            .background(if (on) p.accent else p.cardSoft)
                            .clickable {
                                n = k
                                result = null; scalar = ""; error = ""
                            }
                            .padding(horizontal = 13.dp, vertical = 6.dp),
                    ) {
                        Text("${k}×$k", fontSize = 12.5.sp, color = if (on) p.onAccent else p.textSub)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MatrixGrid("矩阵 A", aTexts) { i, j, v -> aTexts = aTexts.mapIndexed { r, row -> if (r == i) row.mapIndexed { c, s -> if (c == j) v else s } else row } }
            Spacer(Modifier.height(10.dp))
            MatrixGrid("矩阵 B", bTexts) { i, j, v -> bTexts = bTexts.mapIndexed { r, row -> if (r == i) row.mapIndexed { c, s -> if (c == j) v else s } else row } }
            Spacer(Modifier.height(10.dp))
            // 运算按钮两行
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CalcKey("A+B", Modifier.weight(1f), KeyKind.Fn) { run("add") }
                CalcKey("A−B", Modifier.weight(1f), KeyKind.Fn) { run("sub") }
                CalcKey("A×B", Modifier.weight(1f), KeyKind.Fn) { run("mul") }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CalcKey("det(A)", Modifier.weight(1f), KeyKind.Fn) { run("det") }
                CalcKey("A⁻¹", Modifier.weight(1f), KeyKind.Fn) { run("inv") }
                CalcKey("Aᵀ", Modifier.weight(1f), KeyKind.Fn) { run("tr") }
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(error, fontSize = 12.sp, color = p.textWeak)
            }
            if (scalar.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("$resultLabel =", fontSize = 12.sp, color = p.textWeak)
                Text(scalar, fontSize = 24.sp, color = p.text, fontWeight = FontWeight.SemiBold)
            }
            val r = result
            if (r != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$resultLabel =", fontSize = 12.sp, color = p.textWeak, modifier = Modifier.weight(1f))
                    Text("设为 A", fontSize = 12.sp, color = p.accentStrong,
                        modifier = Modifier
                            .clip(chipShape())
                            .clickable {
                                aTexts = List(4) { i -> List(4) { j -> if (i < r.size && j < r.size) MatrixCalc.fmt(r[i][j]) else "0" } }
                                result = null; resultLabel = ""; scalar = ""
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Spacer(Modifier.height(6.dp))
                r.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { v ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(chipShape())
                                    .background(p.accentSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    MatrixCalc.fmt(v), fontSize = 13.5.sp, color = p.text,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
