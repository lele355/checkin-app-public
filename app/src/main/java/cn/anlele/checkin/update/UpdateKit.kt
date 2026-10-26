package cn.anlele.checkin.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.anlele.checkin.BuildConfig
import cn.anlele.checkin.data.Api
import cn.anlele.checkin.data.VersionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * App 内检查更新 —— 从服务端拿最新版本号，比对本地 versionCode。
 *
 * 【为什么要做在 App 里】
 * 以前只有网站 /download 页能下 APK，用户得先知道「有新版本了」这件事。
 * 现在应用商店没上架（自用邀请制），系统不会提醒，更新入口只能自己做。
 *
 * 【不做什么】
 * 不做静默安装 / 后台自动更新。那需要系统签名或 root，
 * 正规做法是下载完 APK 后交给系统安装器，让用户点一次「安装」。
 */
object UpdateKit {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** 本地版本号，用来跟服务端比 */
    val localCode: Int get() = BuildConfig.VERSION_CODE
    val localName: String get() = BuildConfig.VERSION_NAME

    /** 服务端版本是否比本地新 */
    fun hasUpdate(remote: VersionDto): Boolean = remote.versionCode > BuildConfig.VERSION_CODE

    /**
     * 下载的 URL（服务端给的是 /download/... 相对路径）。
     *
     * 注意别用 Api.absolute —— 那个固定指向线上域名（图片/CDN 用），
     * 调试包要能连本地后端，所以这里从 Api.BASE 反推站点根：
     *   https://example.com/api/v1  →  https://example.com
     *   http://10.0.2.2:5199/api/v1  →  http://10.0.2.2:5199
     */
    fun downloadUrl(remote: VersionDto): String {
        val raw = remote.url.ifBlank { "/download/app-release.apk" }
        if (raw.startsWith("http")) return raw
        val origin = Api.BASE.substringBefore("/api").ifBlank { "https://example.com" }
        return origin + if (raw.startsWith("/")) raw else "/$raw"
    }

    /**
     * 下载 APK。返回下载好的文件。
     *
     * @param onProgress 0f~1f；服务端没给 Content-Length 时会一直是 0f，界面就不画进度条
     */
    suspend fun download(
        ctx: Context,
        url: String,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        // 每次下载都清掉旧的：一个 APK 10MB，留着上一版没意义
        dir.listFiles()?.forEach { if (!it.isDirectory) it.delete() }
        val out = File(dir, "checkin-${BuildConfig.VERSION_CODE}.apk")
        val tmp = File(dir, "checkin.apk.part")

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("下载失败（HTTP $code）")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            // 每 256KB 报一次，避免进度回调把主线程刷爆
                            if (done - lastReported > 256 * 1024 || done == total) {
                                lastReported = done
                                val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) { onProgress(p) }
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
            out
        } finally {
            conn.disconnect()
            if (tmp.exists() && tmp.length() == 0L) tmp.delete()
        }
    }

    /**
     * 拉起系统安装器。
     *
     * Android 8.0+ 需要「允许安装未知应用」权限；没授权时系统安装器会自己弹提示，
     * 但这个提示对普通用户很不友好（还容易被误认为病毒），所以调用方先判断一次。
     */
    fun canInstall(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.packageManager.canRequestPackageInstalls()
        else true

    /** 跳到「安装未知应用」授权页（各家 ROM 位置不同，这是标准入口） */
    fun openInstallSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                // 部分 ROM 没有这个页面，退到「应用详情」
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    /** 把下载好的 APK 交给系统安装器 */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /** 兜底：实在装不了就打开浏览器下，至少别把人卡死在 App 里 */
    fun openInBrowser(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}