package com.deviceguard.data.collector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.deviceguard.data.local.InstalledAppEntity
import java.io.File

/**
 * Khâu 1 – Thu thập: kiểm kê ứng dụng đã cài kèm quyền đã được cấp.
 *
 * Đây là dữ liệu nền quan trọng cho phần phân tích: một ứng dụng giữ nhiều quyền
 * nguy hiểm mà người dùng hiếm khi mở là tín hiệu đáng chú ý.
 */
class InstalledAppCollector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun collect(capturedAt: Long): List<InstalledAppEntity> {
        val dangerousCache = mutableMapOf<String, Boolean>()
        return queryPackages().map { pkg ->
            val appInfo = pkg.applicationInfo
            val requested = pkg.requestedPermissions.orEmpty()
            val flags = pkg.requestedPermissionsFlags
            val grantedDangerous = requested.filterIndexed { index, name ->
                val granted = flags != null && index < flags.size &&
                    (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                granted && dangerousCache.getOrPut(name) { isDangerous(name) }
            }
            InstalledAppEntity(
                packageName = pkg.packageName,
                capturedAt = capturedAt,
                label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg.packageName,
                versionName = pkg.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkg.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode.toLong()
                },
                isSystemApp = appInfo != null &&
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                firstInstallTime = pkg.firstInstallTime,
                lastUpdateTime = pkg.lastUpdateTime,
                targetSdk = appInfo?.targetSdkVersion ?: 0,
                apkSizeBytes = appInfo?.sourceDir?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L,
                requestedPermissionCount = requested.size,
                grantedDangerousPermissions = grantedDangerous.joinToString(","),
                installerPackage = installerOf(pkg.packageName)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun queryPackages(): List<PackageInfo> {
        val flags = PackageManager.GET_PERMISSIONS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun installerOf(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    private fun isDangerous(permission: String): Boolean = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPermissionInfo(permission, 0)
        } else {
            @Suppress("DEPRECATION")
            pm.getPermissionInfo(permission, 0)
        }
        val protection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.protection
        } else {
            @Suppress("DEPRECATION")
            info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        }
        protection == PermissionInfo.PROTECTION_DANGEROUS
    }.getOrDefault(false)
}
