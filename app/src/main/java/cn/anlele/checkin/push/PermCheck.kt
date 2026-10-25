package cn.anlele.checkin.push

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 本机「权限体检」：把系统层面决定通知能不能响的四件事查一遍。
 *
 * 顺序就是重要性：电池无限制 > 闹钟和提醒 > 通知权限 > 厂商自启动。
 * 前三项能程序检测；**自启动无法检测**（厂商私有开关，没有公开 API），只能给路径让用户自己开。
 */
data class PermState(
    val notifyOk: Boolean,
    val exactOk: Boolean,
    val batteryOk: Boolean,
    val brand: String,
    /** 该机型的自启动路径说明（给用户看的文字） */
    val autoStartHint: String,
    val isXiaomiLike: Boolean,
)

object PermCheck {

    private val pkg get() = "cn.anlele.checkin"

    fun read(ctx: Context): PermState {
        val brandRaw = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        val brand = when {
            brandRaw.contains("xiaomi") || brandRaw.contains("redmi") || brandRaw.contains("poco") -> "小米 / Redmi"
            brandRaw.contains("huawei") -> "华为"
            brandRaw.contains("honor") -> "荣耀"
            brandRaw.contains("oppo") || brandRaw.contains("realme") || brandRaw.contains("oneplus") -> "OPPO / 一加 / realme"
            brandRaw.contains("vivo") || brandRaw.contains("iqoo") -> "vivo / iQOO"
            brandRaw.contains("samsung") -> "三星"
            brandRaw.contains("google") -> "Pixel"
            else -> Build.MANUFACTURER.ifBlank { "其他" }
        }

        return PermState(
            notifyOk = NotifyHelper.hasPermission(ctx),
            exactOk = AlarmScheduler.canExact(ctx),
            batteryOk = isBatteryUnrestricted(ctx),
            brand = brand,
            autoStartHint = autoStartHint(brand),
            isXiaomiLike = brandRaw.contains("xiaomi") || brandRaw.contains("redmi"),
        )
    }

    private fun isBatteryUnrestricted(ctx: Context): Boolean = try {
        val pm = ctx.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true
    } catch (_: Exception) {
        true
    }

    private fun autoStartHint(brand: String): String = when {
        brand.startsWith("小米") -> "应用信息 → 自启动（打开）；省电策略 → 无限制；最近任务里长按卡片加锁"
        brand == "华为" || brand == "荣耀" -> "设置 → 电池 → 应用启动管理 → 改成「手动管理」→ 自启动 / 关联启动 / 后台活动 三个全开"
        brand.startsWith("OPPO") -> "应用 → 电池 → 允许后台活动；有的话再开「允许自启动」；最近任务加锁"
        brand.startsWith("vivo") -> "自启动 + 后台高耗电 → 允许"
        brand == "三星" -> "电池 → 无限制；电池 → 后台使用限制 → 加进「永不休眠的应用」"
        else -> "本机通常不需要额外设置；若重启后不响，把电池策略设为「无限制」"
    }

    // ---------------- 跳转 ----------------

    fun openNotifySettings(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        })
    }

    fun openExactAlarm(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            openAppDetails(ctx); return
        }
        safeStart(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${ctx.packageName}")
        })
    }

    fun openBattery(ctx: Context) {
        // 优先直接弹「忽略电池优化」确认框；部分 ROM 不认这种 intent，回退到列表页
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }
        if (!safeStart(ctx, direct)) {
            safeStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openAppDetails(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${ctx.packageName}")
        })
    }

    /** 自启动：厂商私有页面，逐个试，全失败就落到应用信息页 */
    fun openAutoStart(ctx: Context) {
        val candidates = listOf(
            // 小米 / Redmi
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // 华为 / 荣耀
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            // OPPO / 一加 / realme
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            // vivo / iQOO
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )
        for (cn in candidates) {
            val ok = runCatching {
                ctx.startActivity(Intent().apply {
                    component = cn
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            }.getOrDefault(false)
            if (ok) return
        }
        openAppDetails(ctx)
    }

    private fun safeStart(ctx: Context, intent: Intent): Boolean = runCatching {
        ctx.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        true
    }.getOrDefault(false)
}
