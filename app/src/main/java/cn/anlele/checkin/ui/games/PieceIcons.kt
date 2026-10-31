package cn.anlele.checkin.ui.games

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * 棋子图标缓存（D-5 图标二阶段集成）。
 *
 * 资源为 icons-ready 流水线同源产物（网页 SVG → 本处 PNG @2x/@3x），安卓 res 扁平命名：
 * `<game>_<key>`（如 xiangqi_red_shuai / armychess_red_siling / ludo_airplane_red）。
 * 0.1 双端同规：同一 key 同一形，网页 `wwwroot/pieces/` 与本处 PNG 同一素材源。
 */
@Composable
fun rememberPieceIcons(prefix: String, keys: List<String>): Map<String, ImageBitmap> {
    val ctx = LocalContext.current
    return remember(prefix, keys) {
        keys.associateWith { key ->
            val id = ctx.resources.getIdentifier("${prefix}_$key", "drawable", ctx.packageName)
            val bmp = if (id != 0) BitmapFactory.decodeResource(ctx.resources, id) else null
            bmp?.asImageBitmap() ?: ImageBitmap(1, 1)   // 缺件防御：1×1 透明占位，绝不 NPE
        }
    }
}
