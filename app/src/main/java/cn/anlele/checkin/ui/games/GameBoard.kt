package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * 棋盘渲染抽象 —— 3 种几何（grid / point / track）+ 共享原语。
 * 对应网页 boards.js，固定色不跟主题（红线 18）。
 */
object GameBoardColors {
    val BoardBg = Color(0xFFD4A843)
    val GridLine = Color(0xFF444444)
    val Black = Color(0xFF222222)
    val White = Color(0xFFEEEEEE)
    val Select = Color(0xFF1A73E8)
    val LastMark = Color(0xFFFF5722)
    val Hint = Color(0x40FFFFFF)
    val CoordText = Color(0xFF888888)
    val ReversiBg = Color(0xFF2E7D32)
    val ReversiGrid = Color(0xFF1B5E20)
    val ReversiHint = Color(0x40000000)
}

/**
 * 通用格子棋盘 —— grid 几何：棋子放格内。
 * cfg: 每款游戏的配置；state: 快照数据；onTap(x, y): 落子回调。
 */
@Composable
fun GridBoard(
    size: Int,
    modifier: Modifier = Modifier,
    bgColor: Color = GameBoardColors.BoardBg,
    showCoord: Boolean = false,
    lastMark: Boolean = true,
    onTap: ((Int, Int) -> Unit)? = null,
    drawCell: DrawScope.(geom: BoardGeom, x: Int, y: Int) -> Unit = { _, _, _ -> },
    drawOverlay: DrawScope.(geom: BoardGeom) -> Unit = {},
) {
    val geom = BoardGeom(size, showCoord)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (onTap != null) Modifier.pointerInput(size) {
                detectTapGestures { pos ->
                    val gx = floor((pos.x - geom.padPx) / geom.cellPx).toInt()
                    val gy = floor((pos.y - geom.padPx) / geom.cellPx).toInt()
                    if (gx in 0 until size && gy in 0 until size) onTap(gx, gy)
                }
            } else Modifier)
    ) {
        drawRect(bgColor)
        geom.density = this.size.width / 640f
        geom.update(this.size.width)

        // 格线
        for (i in 0..size) {
            drawLine(
                GameBoardColors.GridLine,
                Offset(geom.cellStartPx(i), geom.cellStartPx(0)),
                Offset(geom.cellStartPx(i), geom.cellStartPx(size)),
                strokeWidth = 1.5f,
            )
            drawLine(
                GameBoardColors.GridLine,
                Offset(geom.cellStartPx(0), geom.cellStartPx(i)),
                Offset(geom.cellStartPx(size), geom.cellStartPx(i)),
                strokeWidth = 1.5f,
            )
        }

        // 每格自定义绘制
        for (x in 0 until size) {
            for (y in 0 until size) {
                drawCell(geom, x, y)
            }
        }

        // 叠加层
        drawOverlay(geom)

        // 坐标
        if (showCoord) {
            // Compose Canvas 里画文字需要 TextMeasurer，这里用简化版：只画刻度线
        }
    }
}

/** 棋盘几何换算：归一化 640 坐标 → 实际像素 */
class BoardGeom(val size: Int, val showCoord: Boolean) {
    var canvasPx: Float = 640f; private set
    var density: Float = 1f
    val padNorm: Float = if (showCoord) 40f else 20f
    val padPx: Float get() = padNorm * density
    val cellPx: Float get() = (canvasPx - padPx * 2) / size

    fun update(totalPx: Float) { canvasPx = totalPx }
    fun center(x: Int, y: Int): Offset = Offset(cellStartPx(x) + cellPx / 2, cellStartPx(y) + cellPx / 2)
    fun cellStartPx(i: Int): Float = padPx + i * cellPx
}

/** 矩形格子棋盘几何（非正方，如斗兽棋 7×9）：列宽/行高分开算 */
class BoardGeomRect(val cols: Int, val rows: Int) {
    var canvasW: Float = 640f; private set
    var canvasH: Float = 640f * rows / cols; private set
    var density: Float = 1f
    val padPx: Float get() = 20f * density
    val cellW: Float get() = (canvasW - padPx * 2) / cols
    val cellH: Float get() = (canvasH - padPx * 2) / rows

    fun update(w: Float, h: Float) { canvasW = w; canvasH = h }
    fun center(x: Int, y: Int): Offset = Offset(padPx + x * cellW + cellW / 2, padPx + y * cellH + cellH / 2)
    fun start(x: Int, y: Int): Offset = Offset(padPx + x * cellW, padPx + y * cellH)
    fun cellSize(): Size = Size(cellW, cellH)
}

/**
 * 矩形格子棋盘（cols × rows，纵横比 cols/rows）—— 斗兽棋等非方阵棋盘用。
 * onTap(x, y) 回调格子坐标。
 */
@Composable
fun GridBoardRect(
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    bgColor: Color = GameBoardColors.BoardBg,
    onTap: ((Int, Int) -> Unit)? = null,
    drawCell: DrawScope.(geom: BoardGeomRect, x: Int, y: Int) -> Unit = { _, _, _ -> },
    drawOverlay: DrawScope.(geom: BoardGeomRect) -> Unit = {},
) {
    val geom = BoardGeomRect(cols, rows)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(cols.toFloat() / rows)
            .then(if (onTap != null) Modifier.pointerInput(cols, rows) {
                detectTapGestures { pos ->
                    val gx = floor((pos.x - geom.padPx) / geom.cellW).toInt()
                    val gy = floor((pos.y - geom.padPx) / geom.cellH).toInt()
                    if (gx in 0 until cols && gy in 0 until rows) onTap(gx, gy)
                }
            } else Modifier)
    ) {
        drawRect(bgColor)
        geom.density = size.width / 640f
        geom.update(size.width, size.height)

        // 格线
        for (i in 0..cols) {
            drawLine(
                GameBoardColors.GridLine,
                Offset(geom.start(i, 0).x, geom.start(0, 0).y),
                Offset(geom.start(i, 0).x, geom.start(0, rows).y),
                1.5f,
            )
        }
        for (j in 0..rows) {
            drawLine(
                GameBoardColors.GridLine,
                Offset(geom.start(0, 0).x, geom.start(0, j).y),
                Offset(geom.start(cols, 0).x, geom.start(0, j).y),
                1.5f,
            )
        }

        for (x in 0 until cols) for (y in 0 until rows) drawCell(geom, x, y)
        drawOverlay(geom)
    }
}
