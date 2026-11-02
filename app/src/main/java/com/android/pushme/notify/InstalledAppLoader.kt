package com.android.pushme.notify

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.android.pushme.utils.Settings
import java.text.Collator
import java.util.Locale

/**
 * 「应用过滤」列表里的一行。
 *
 * 刻意**不持有图标 Drawable**：列表可能上百项，一次性把所有图标解码出来又慢又占内存，
 * 图标由 UI 层按可见项懒加载（LazyColumn 只会组合屏幕上可见的那几行）。
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

/**
 * 读取设备上的应用列表，供「应用过滤」勾选用。
 *
 * ⚠️ Android 11（API 30）起有**软件包可见性过滤**（package visibility）：
 * `getInstalledApplications()` 只会返回「对当前应用可见」的应用 ——
 * 自己的应用、部分核心系统应用，以及清单里 `<queries>` 声明命中的应用。
 * 官方文档明确说明该方法「返回过滤后的列表」，所以这里必须做多来源合并：
 *
 * 1. `getInstalledApplications()`：主来源，覆盖系统应用；
 * 2. `queryIntentActivities(MAIN + LAUNCHER)`：**带启动图标的应用**。
 *    通知来源应用几乎都有启动图标，而这一类通过清单里的 `<queries>` 声明已经对我们可见，
 *    所以即使 ① 因 ROM 差异漏了，这里也能补回来；
 * 3. 已在过滤名单里的包名：即使当前不可见也保留，避免用户一保存就把旧选择静默丢掉。
 *
 * 仍然列不出来的只有「既没有启动图标、也不在可见集合里」的少数应用（纯后台服务等），
 * 这是系统限制。要完全绕过只能声明 `QUERY_ALL_PACKAGES` 权限，本项目**故意不加**
 * （Google Play 对该权限有政策限制，收益不值这个代价）。
 */
object InstalledAppLoader {

    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val self = context.packageName
        val apps = LinkedHashMap<String, InstalledApp>()

        // ① 已安装应用（受可见性过滤，能拿到多少算多少）
        runCatching {
            pm.getInstalledApplications(0).forEach { info ->
                val pkg = info.packageName ?: return@forEach
                // 本应用自己的通知是防回环硬约束，永远不转发，列出来没有意义
                if (pkg == self) return@forEach
                apps[pkg] = InstalledApp(pkg, label(pm, info), isSystem(info))
            }
        }

        // ② 兜底：所有带启动图标的应用
        runCatching {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0).forEach { resolve ->
                val info = resolve.activityInfo?.applicationInfo ?: return@forEach
                val pkg = info.packageName ?: return@forEach
                if (pkg == self) return@forEach
                if (!apps.containsKey(pkg)) {
                    apps[pkg] = InstalledApp(pkg, label(pm, info), isSystem(info))
                }
            }
        }

        // ③ 已在过滤名单里、但当前不可见的包名也要保留
        NotifyFilter.parsePackages(Settings.notifyAppFilterList).forEach { pkg ->
            if (pkg == self || apps.containsKey(pkg)) return@forEach
            apps[pkg] = InstalledApp(pkg, label(pm, pkg), false)
        }

        // 普通应用在前、系统应用在后；同类按本地化规则排序（中文按拼音）
        val collator = Collator.getInstance(Locale.getDefault())
        return apps.values.sortedWith(
            compareBy<InstalledApp> { it.isSystem }
                .thenComparator { a, b -> collator.compare(a.label, b.label) }
        )
    }

    /** 系统应用：以 FLAG_SYSTEM 为准，与系统设置里的「系统应用」口径一致 */
    private fun isSystem(info: ApplicationInfo): Boolean {
        return (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private fun label(pm: PackageManager, info: ApplicationInfo): String {
        val name = runCatching { pm.getApplicationLabel(info).toString() }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: (info.packageName ?: "")
    }

    /** 包名不可见（已卸载 / 被系统过滤）时退回包名本身，不影响勾选 */
    private fun label(pm: PackageManager, packageName: String): String {
        val name = runCatching { label(pm, pm.getApplicationInfo(packageName, 0)) }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: packageName
    }
}
