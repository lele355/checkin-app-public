package cn.anlele.checkin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 心情贴纸脸（2026-11-02 站主定：贴合示意图的可爱手绘贴纸风）。
 * 圆脸（暖橙）/ 方脸（米色）区分两人；6 种心情：1 开心 2 平淡 3 难过 4 生气 5 困 6 害羞。
 * 与 Web 端 Ui/MoodStickers.cs 同款设计（圆脸/方脸 + 眼嘴 + 腮红）。
 */
@Composable
fun MoodSticker(mood: Int, round: Boolean, size: Dp = 40.dp) {
    val outline = Color(0xFF7A5B3C)
    val face = if (round) Color(0xFFFFD9A8) else Color(0xFFF2E3C9)
    val ink = Color(0xFF5B4632)
    val blush = Color(0xFFFF9E9E)
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = w / 64f   // 统一按 64 设计稿缩放

        // 脸形
        if (round) {
            drawCircle(face, radius = 25 * s, center = Offset(32 * s, 33 * s))
            drawCircle(outline, radius = 25 * s, center = Offset(32 * s, 33 * s), style = Stroke(width = 2.5f * s))
        } else {
            drawRoundRectPath(face, outline, s)
        }

        // 高光
        drawOval(
            Color.White.copy(alpha = 0.45f),
            topLeft = Offset(if (round) 17 * s else 16 * s, 16 * s),
            size = Size(if (round) 12 * s else 10 * s, 8 * s),
        )

        // 腮红（害羞更深）
        val blushA = if (mood == 6) 0.85f else 0.5f
        val eyY = if (round) 30f else 31f
        val cxs = if (round) 20f to 44f else 19f to 45f
        drawOval(blush.copy(alpha = blushA), topLeft = Offset((cxs.first - 5.2f) * s, (eyY + 8f - 3.4f) * s), size = Size(10.4f * s, 6.8f * s))
        drawOval(blush.copy(alpha = blushA), topLeft = Offset((cxs.second - 5.2f) * s, (eyY + 8f - 3.4f) * s), size = Size(10.4f * s, 6.8f * s))

        // 眼 + 嘴（按心情）
        when (mood) {
            1 -> {   // 生气（最坏）：皱眉 + 撅嘴
                strokeLine(ink, s, 19.5f, eyY - 3f, 26f, eyY - 0.8f)
                strokeLine(ink, s, 44.5f, eyY - 3f, 38f, eyY - 0.8f)
                drawCircle(ink, radius = 2.1f * s, center = Offset(23.6f * s, (eyY + 1.8f) * s))
                drawCircle(ink, radius = 2.1f * s, center = Offset(40.4f * s, (eyY + 1.8f) * s))
                poutStroke(ink, s, 27f, eyY + 10f)
            }
            2 -> {   // 难过：八字眉 + 下垂眼 + 撇嘴（∩ 型）
                strokeLine(ink, s, 19.5f, 25.5f, 26.5f, 24.7f)
                strokeLine(ink, s, 44.5f, 25.5f, 37.5f, 24.7f)
                arcStroke(ink, s, 20f, eyY + 1.5f, 6.4f, up = false)
                arcStroke(ink, s, 37.6f, eyY + 1.5f, 6.4f, up = false)
                sadMouth(ink, s, 26f, eyY + 10f)
            }
            3 -> {   // 困/累：闭眼线 + 小 o 嘴
                strokeLine(ink, s, 20f, eyY, 26.4f, eyY)
                strokeLine(ink, s, 37.6f, eyY, 44f, eyY)
                drawCircle(ink, radius = 3.4f * s, center = Offset(32f * s, (eyY + 9f) * s), style = Stroke(width = 2.4f * s))
            }
            4 -> {   // 平淡：豆豆眼 + 平嘴
                drawCircle(ink, radius = 2.4f * s, center = Offset(23.2f * s, eyY * s))
                drawCircle(ink, radius = 2.4f * s, center = Offset(40.8f * s, eyY * s))
                strokeLine(ink, s, 28f, eyY + 8f, 36f, eyY + 8f)
            }
            5 -> {   // 害羞：眯眼 + ω 猫嘴 + 深腮红（与开心拉开：ω 不是大笑弧）
                arcStroke(ink, s, 20f, eyY + 1f, 6.4f, up = true)
                arcStroke(ink, s, 37.6f, eyY + 1f, 6.4f, up = true)
                omegaMouth(ink, s, 27f, eyY + 8f)
            }
            else -> {   // 开心（最好）：弯月眯眼 + 大笑
                arcStroke(ink, s, 20f, eyY, 6.4f, up = true)
                arcStroke(ink, s, 37.6f, eyY, 6.4f, up = true)
                smileStroke(ink, s, 26f, eyY + 8f, 12f, big = true)
            }
        }
    }
}

private fun DrawScope.drawRoundRectPath(fill: Color, outline: Color, s: Float) {
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                9f * s, 10f * s, 55f * s, 56f * s,
                androidx.compose.ui.geometry.CornerRadius(13f * s, 13f * s),
            )
        )
    }
    drawPath(path, fill)
    drawPath(path, outline, style = Stroke(width = 2.5f * s))
}

/** 弯月眼（up=true 眯眼笑 / false 下垂难过） */
private fun DrawScope.arcStroke(color: Color, s: Float, x: Float, y: Float, w: Float, up: Boolean) {
    val path = Path().apply {
        moveTo(x * s, y * s)
        quadraticBezierTo(
            (x + w / 2) * s, (y + if (up) -4.2f else 3.6f) * s,
            (x + w) * s, y * s,
        )
    }
    drawPath(path, color, style = Stroke(width = 2.6f * s, cap = StrokeCap.Round))
}

/** 笑嘴（big=大笑弧 / 小笑） */
private fun DrawScope.smileStroke(color: Color, s: Float, x: Float, y: Float, w: Float, big: Boolean) {
    val path = Path().apply {
        moveTo(x * s, y * s)
        quadraticBezierTo((x + w / 2) * s, (y + if (big) 6.2f else 3.6f) * s, (x + w) * s, y * s)
    }
    drawPath(path, color, style = Stroke(width = 2.5f * s, cap = StrokeCap.Round))
}

/** 波浪嘴（难过） */
private fun DrawScope.waveStroke(color: Color, s: Float, x: Float, y: Float) {
    val path = Path().apply {
        moveTo(x * s, y * s)
        quadraticBezierTo((x + 3f) * s, (y - 3f) * s, (x + 6f) * s, y * s)
        quadraticBezierTo((x + 9f) * s, (y + 3f) * s, (x + 12f) * s, y * s)
    }
    drawPath(path, color, style = Stroke(width = 2.4f * s, cap = StrokeCap.Round))
}

/** 撅嘴（生气/难过 ∩ 型） */
private fun DrawScope.poutStroke(color: Color, s: Float, x: Float, y: Float) {
    val path = Path().apply {
        moveTo(x * s, y * s)
        quadraticBezierTo((x + 6f) * s, (y - 4.4f) * s, (x + 12f) * s, y * s)
    }
    drawPath(path, color, style = Stroke(width = 2.6f * s, cap = StrokeCap.Round))
}

/** 撇嘴（难过，更大下垂弧） */
private fun DrawScope.sadMouth(color: Color, s: Float, x: Float, y: Float) {
    val path = Path().apply {
        moveTo(x * s, y * s)
        quadraticBezierTo((x + 6f) * s, (y - 5.5f) * s, (x + 12f) * s, y * s)
    }
    drawPath(path, color, style = Stroke(width = 2.6f * s, cap = StrokeCap.Round))
}

/** ω 猫嘴（害羞，与开心大笑区分） */
private fun DrawScope.omegaMouth(color: Color, s: Float, x: Float, y: Float) {
    val path = Path().apply {
        moveTo(x * s, y * s)
        quadraticBezierTo((x + 2.5f) * s, (y + 2.8f) * s, (x + 5f) * s, y * s)
        quadraticBezierTo((x + 7.5f) * s, (y - 2.8f) * s, (x + 10f) * s, y * s)
    }
    drawPath(path, color, style = Stroke(width = 2.3f * s, cap = StrokeCap.Round))
}

private fun DrawScope.strokeLine(color: Color, s: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
}
