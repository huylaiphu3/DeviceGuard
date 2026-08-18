package com.deviceguard.core

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/** Cách một quyền được cấp — quyết định UI dẫn người dùng đi đâu. */
enum class GrantMechanism {
    /** Hộp thoại runtime tiêu chuẩn. */
    RUNTIME,

    /** Quyền đặc biệt: phải mở màn hình Cài đặt hệ thống tương ứng. */
    SPECIAL_SETTINGS
}

/**
 * Mô tả một nhóm quyền theo ngôn ngữ người dùng hiểu được.
 *
 * [purpose] được hiển thị nguyên văn trên màn hình Onboarding trước khi hỏi quyền,
 * để thỏa mãn nguyên tắc "người dùng biết chính xác dữ liệu nào được đọc và vì sao".
 */
data class PermissionSpec(
    val id: String,
    val title: String,
    val purpose: String,
    val mechanism: GrantMechanism,
    val required: Boolean,
    val runtimePermissions: List<String> = emptyList(),
    val minSdk: Int = Build.VERSION_CODES.O
)

object PermissionCatalog {

    const val ID_USAGE_STATS = "usage_stats"
    const val ID_MEDIA = "media"
    const val ID_NOTIFICATION_LOG = "notification_log"
    const val ID_ALL_FILES = "all_files"
    const val ID_PERSONAL = "personal"
    const val ID_POST_NOTIFICATIONS = "post_notifications"

    val specs: List<PermissionSpec> = listOf(
        PermissionSpec(
            id = ID_USAGE_STATS,
            title = "Truy cập thống kê sử dụng",
            purpose = "Đọc thời lượng và số lần mở từng ứng dụng trên chính máy này " +
                "để dựng biểu đồ thói quen sử dụng. Không đọc nội dung bên trong ứng dụng.",
            mechanism = GrantMechanism.SPECIAL_SETTINGS,
            required = false
        ),
        PermissionSpec(
            id = ID_MEDIA,
            title = "Ảnh, video và âm thanh",
            purpose = "Lập chỉ mục tệp media để thống kê dung lượng và để liệt kê " +
                "các tệp đang nằm trong thùng rác hệ thống có thể khôi phục.",
            mechanism = GrantMechanism.RUNTIME,
            required = false,
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        ),
        PermissionSpec(
            id = ID_NOTIFICATION_LOG,
            title = "Nhật ký thông báo",
            purpose = "Ghi lại tiêu đề thông báo xuất hiện trên máy này để phân tích " +
                "mức độ gián đoạn theo giờ. Nội dung chỉ nằm trong bộ nhớ máy, " +
                "có thể xóa sạch bất cứ lúc nào.",
            mechanism = GrantMechanism.SPECIAL_SETTINGS,
            required = false
        ),
        PermissionSpec(
            id = ID_ALL_FILES,
            title = "Quản lý toàn bộ tệp",
            purpose = "Chỉ dùng cho chức năng quét sâu tìm tệp còn sót trong thư mục " +
                "cache/.trashed. Không cấp quyền này thì các chức năng còn lại vẫn chạy.",
            mechanism = GrantMechanism.SPECIAL_SETTINGS,
            required = false,
            minSdk = Build.VERSION_CODES.R
        ),
        PermissionSpec(
            id = ID_PERSONAL,
            title = "Danh bạ, nhật ký cuộc gọi, tin nhắn",
            purpose = "Tùy chọn. Chỉ đếm và hiển thị thống kê tổng hợp trên chính máy " +
                "này (ví dụ: số liên hệ, số tin nhắn theo tháng). Mặc định TẮT.",
            mechanism = GrantMechanism.RUNTIME,
            required = false,
            runtimePermissions = listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_SMS
            )
        ),
        PermissionSpec(
            id = ID_POST_NOTIFICATIONS,
            title = "Gửi thông báo",
            purpose = "Báo cho bạn biết mỗi khi ứng dụng chụp một ảnh trạng thái thiết bị " +
                "ở chế độ nền — để việc thu thập luôn nhìn thấy được.",
            mechanism = GrantMechanism.RUNTIME,
            required = false,
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            },
            minSdk = Build.VERSION_CODES.TIRAMISU
        )
    )

    fun availableSpecs(): List<PermissionSpec> =
        specs.filter { Build.VERSION.SDK_INT >= it.minSdk }

    fun isGranted(context: Context, spec: PermissionSpec): Boolean = when (spec.id) {
        ID_USAGE_STATS -> hasUsageStatsAccess(context)
        ID_NOTIFICATION_LOG -> hasNotificationListenerAccess(context)
        ID_ALL_FILES -> hasAllFilesAccess()
        else -> spec.runtimePermissions.isNotEmpty() &&
            spec.runtimePermissions.all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
    }

    /** Số quyền runtime đã cấp trong nhóm — dùng cho nhóm cấp một phần (vd. chỉ ảnh). */
    fun grantedCount(context: Context, spec: PermissionSpec): Int =
        spec.runtimePermissions.count {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    fun settingsIntent(context: Context, spec: PermissionSpec): Intent? = when (spec.id) {
        ID_USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        ID_NOTIFICATION_LOG -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        ID_ALL_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            null
        }
        else -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun hasUsageStatsAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationListenerAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val component = ComponentName(
            context,
            "com.deviceguard.data.collector.NotificationLogService"
        )
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
}
