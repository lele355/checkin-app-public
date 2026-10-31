package cn.anlele.checkin.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
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
    // 手势闭包必须取「最新」onTap：pointerInput(size) 只创建一次，直接捕获 onTap 会永远用首帧闭包
    // （2026-10-31 线上事故根因：走一步后选中判断用旧局面 → 棋子点不动）
    val tapCb = rememberUpdatedState(onTap)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (onTap != null) Modifier.pointerInput(size) {
                detectTapGestures { pos ->
                    val gx = floor((pos.x - geom.padPx) / geom.cellPx).toInt()
                    val gy = floor((pos.y - geom.padPx) / geom.cellPx).toInt()
                    if (gx in 0 until size && gy in 0 until size) tapCb.value?.invoke(gx, gy)
                }
            } else Modifier)
    ) {
        drawRect(bgColor)
        geom.density = this.size.width / 640f
        geom.update(this.size.width)

        // 格线（盘面边界不走 vy —— 0..size 的边界线翻转前后是同一组线）
        for (i in 0..size) {
            val lp = geom.padPx + i * geom.cellPx
            drawLine(
                GameBoardColors.GridLine,
                Offset(lp, geom.padPx),
                Offset(lp, geom.padPx + size * geom.cellPx),
                strokeWidth = 1.5f,
            )
            drawLine(
                GameBoardColors.GridLine,
                Offset(geom.padPx, lp),
                Offset(geom.padPx + size * geom.cellPx, lp),
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

/** 棋盘几何换算：归一化 640 坐标 → 实际像素。
 *  flip = 视角对调（己方恒在下半区）：格位 y 上下翻转；回调/onTap 均逻辑坐标。 */
class BoardGeom(val size: Int, val showCoord: Boolean, val flip: Boolean = false) {
    var canvasPx: Float = 640f; private set
    var density: Float = 1f
    val padNorm: Float = if (showCoord) 40f else 20f
    val padPx: Float get() = padNorm * density
    val cellPx: Float get() = (canvasPx - padPx * 2) / size

    fun update(totalPx: Float) { canvasPx = totalPx }
    fun vy(y: Int): Int = if (flip) size - 1 - y else y
    fun center(x: Int, y: Int): Offset = Offset(cellStartPx(x) + cellPx / 2, cellStartPx(y) + cellPx / 2)
    fun cellStartPx(i: Int): Float = padPx + vy(i) * cellPx
}

/** 矩形格子棋盘几何（非正方，如斗兽棋 7×9）：列宽/行高分开算。
 *  flip = 视角对调（己方恒在下半区）：格位 y 上下翻转。 */
class BoardGeomRect(val cols: Int, val rows: Int, val flip: Boolean = false) {
    var canvasW: Float = 640f; private set
    var canvasH: Float = 640f * rows / cols; private set
    var density: Float = 1f
    val padPx: Float get() = 20f * density
    val cellW: Float get() = (canvasW - padPx * 2) / cols
    val cellH: Float get() = (canvasH - padPx * 2) / rows

    fun update(w: Float, h: Float) { canvasW = w; canvasH = h }
    fun vy(y: Int): Int = if (flip) rows - 1 - y else y
    fun center(x: Int, y: Int): Offset = Offset(padPx + x * cellW + cellW / 2, padPx + vy(y) * cellH + cellH / 2)
    fun start(x: Int, y: Int): Offset = Offset(padPx + x * cellW, padPx + vy(y) * cellH)
    fun cellSize(): Size = Size(cellW, cellH)
}

/**
 * 矩形格子棋盘（cols × rows，纵横比 cols/rows）—— 斗兽棋等非方阵棋盘用。
 * onTap(x, y) 回调格子坐标（恒逻辑坐标；flip 视角对调时手势层自动反变换）。
 */
@Composable
fun GridBoardRect(
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    bgColor: Color = GameBoardColors.BoardBg,
    flip: Boolean = false,
    onTap: ((Int, Int) -> Unit)? = null,
    drawCell: DrawScope.(geom: BoardGeomRect, x: Int, y: Int) -> Unit = { _, _, _ -> },
    drawOverlay: DrawScope.(geom: BoardGeomRect) -> Unit = {},
) {
    val geom = BoardGeomRect(cols, rows, flip)
    // 同上：手势闭包取最新回调（pointerInput(cols, rows, flip) 不随局面变化重建）
    val tapCb = rememberUpdatedState(onTap)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(cols.toFloat() / rows)
            .then(if (onTap != null) Modifier.pointerInput(cols, rows, flip) {
                detectTapGestures { pos ->
                    var gx = floor((pos.x - geom.padPx) / geom.cellW).toInt()
                    var gy = floor((pos.y - geom.padPx) / geom.cellH).toInt()
                    if (gx in 0 until cols && gy in 0 until rows) {
                        if (flip) gy = rows - 1 - gy   // 显示行 → 逻辑行
                        tapCb.value?.invoke(gx, gy)
                    }
                }
            } else Modifier)
    ) {
        drawRect(bgColor)
        geom.density = size.width / 640f
        geom.update(size.width, size.height)

        // 格线（盘面边界不走 vy —— 0..rows 的边界线翻转前后是同一组线）
        for (i in 0..cols) {
            val x0 = geom.padPx + i * geom.cellW
            drawLine(
                GameBoardColors.GridLine,
                Offset(x0, geom.padPx),
                Offset(x0, geom.padPx + rows * geom.cellH),
                1.5f,
            )
        }
        for (j in 0..rows) {
            val y0 = geom.padPx + j * geom.cellH
            drawLine(
                GameBoardColors.GridLine,
                Offset(geom.padPx, y0),
                Offset(geom.padPx + cols * geom.cellW, y0),
                1.5f,
            )
        }

        for (x in 0 until cols) for (y in 0 until rows) drawCell(geom, x, y)
        drawOverlay(geom)
    }
}

/** 走子滑动动画（Batch D）：from → to 插值 + 落点回弹缩放。progress 0..1.3（1 之后是回弹段） */
data class BoardAnim(
    val fromX: Int, val fromY: Int, val toX: Int, val toY: Int, val progress: Float,
) {
    val x: Float get() = fromX + (toX - fromX) * progress.coerceAtMost(1f)
    val y: Float get() = fromY + (toY - fromY) * progress.coerceAtMost(1f)

    /** 滑行微缩 → 落点回弹（1.15 → 1） */
    val scale: Float
        get() = if (progress <= 1f) 0.92f + 0.08f * progress
        else 1f + 0.15f * (1f - ((progress - 1f) / 0.3f)).coerceAtLeast(0f)
}

/**
 * 动画驱动盒（Batch D）：对局 revision 变化时按 lastMove 启动滑动动画；
 * 吃子在目标点留淡出鬼影。RoomContent 每帧 advance()。
 */
class AnimBox {
    var anim: BoardAnim? = null
        private set
    /** 吃子鬼影：被吃的那枚棋子（原始 DTO，各棋盘自己映射内部类型）+ 淡出透明度 */
    var ghostPiece: cn.anlele.checkin.data.GamePieceDto? = null
        private set
    var ghostAlpha: Float = 0f
        private set
    private var prev: List<cn.anlele.checkin.data.GamePieceDto> = emptyList()
    var done: Boolean = true
        private set

    /** 上一状态在 (x,y) 的棋子快照（吃子鬼影源） */
    fun pieceAt(x: Int, y: Int): cn.anlele.checkin.data.GamePieceDto? = prev.find { it.x == x && it.y == y }

    fun onNewState(
        lastMove: cn.anlele.checkin.data.GameLastMoveDto?,
        pieces: List<cn.anlele.checkin.data.GamePieceDto>,
    ) {
        val lm = lastMove
        val fx = lm?.fx
        val fy = lm?.fy
        val captured = if (lm != null && fx != null && fy != null) pieceAt(lm.tx, lm.ty) else null
        prev = pieces
        ghostAlpha = 0f
        ghostPiece = captured
        if (lm == null || fx == null || fy == null || (fx == lm.tx && fy == lm.ty)) {
            anim = null; done = true; return
        }
        if (captured != null) ghostAlpha = 1f
        anim = BoardAnim(fx, fy, lm.tx, lm.ty, 0f)
        done = false
    }

    fun advance(elapsedMs: Float) {
        val a = anim ?: run { done = true; return }
        val p = (elapsedMs / 200f).coerceAtMost(1.3f)
        anim = a.copy(progress = p)
        ghostAlpha = (1f - elapsedMs / 240f).coerceAtLeast(0f)
        if (elapsedMs > 280f) { done = true; anim = null; ghostAlpha = 0f }
    }
}
