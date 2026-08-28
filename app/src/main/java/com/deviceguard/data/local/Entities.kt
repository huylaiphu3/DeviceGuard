package com.deviceguard.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ảnh chụp trạng thái thiết bị tại một thời điểm — nền cho phần phân tích theo thời gian. */
@Entity(tableName = "device_snapshot", indices = [Index("capturedAt")])
data class DeviceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val batteryPercent: Int,
    val batteryStatus: String,
    val batteryTempC: Float,
    val isCharging: Boolean,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val networkType: String,
    val isMetered: Boolean,
    val installedAppCount: Int,
    val userAppCount: Int
)

/** Bản ghi một ứng dụng đã cài, chụp lại theo từng lần thu thập. */
@Entity(
    tableName = "installed_app",
    primaryKeys = ["packageName", "capturedAt"],
    indices = [Index("capturedAt")]
)
data class InstalledAppEntity(
    val packageName: String,
    val capturedAt: Long,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val targetSdk: Int,
    val apkSizeBytes: Long,
    val requestedPermissionCount: Int,
    val grantedDangerousPermissions: String,
    val installerPackage: String?,
    /** Có activity mở được từ launcher không. RAT thường ẩn icon → false. */
    val hasLauncherIcon: Boolean = true,
    /** Toàn bộ quyền ứng dụng KHAI BÁO (không chỉ quyền đã cấp), ngăn cách bằng dấu phẩy. */
    val requestedPermissions: String = "",
    /** Ứng dụng có kèm dịch vụ Trợ năng (Accessibility) — kênh bị RAT lạm dụng để đọc màn hình/keylog. */
    val usesAccessibility: Boolean = false
)

/** Thời lượng sử dụng theo ứng dụng theo ngày (nguồn: UsageStatsManager). */
@Entity(
    tableName = "app_usage",
    primaryKeys = ["packageName", "dayStart"],
    indices = [Index("dayStart")]
)
data class AppUsageEntity(
    val packageName: String,
    val dayStart: Long,
    val foregroundTimeMs: Long,
    val launchCount: Int,
    val lastTimeUsed: Long
)

/** Một sự kiện đưa ứng dụng ra tiền cảnh — dùng cho biểu đồ theo giờ. */
@Entity(tableName = "usage_event", indices = [Index("timestamp"), Index("packageName")])
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val eventType: String
)

/** Nhật ký thông báo — chỉ ghi khi người dùng bật công tắc riêng. */
@Entity(tableName = "notification_log", indices = [Index("postedAt"), Index("packageName")])
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val category: String?,
    val postedAt: Long,
    val isOngoing: Boolean
)

/** Ứng viên khôi phục do module Recovery tìm được. */
@Entity(tableName = "recovery_candidate", indices = [Index("discoveredAt"), Index("source")])
data class RecoveryCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val displayName: String,
    val locator: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val deletedAt: Long?,
    val discoveredAt: Long,
    val confidence: String,
    val restorable: Boolean,
    val restoredAt: Long? = null
)
