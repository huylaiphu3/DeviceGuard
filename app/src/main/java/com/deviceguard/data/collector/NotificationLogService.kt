package com.deviceguard.data.collector

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.deviceguard.data.local.DeviceGuardDatabase
import com.deviceguard.data.local.NotificationLogEntity
import com.deviceguard.core.ConsentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Khâu 1 – Thu thập: nhật ký thông báo hiển thị trên chính máy này.
 *
 * Hai lớp bảo vệ:
 *  1. Android buộc người dùng tự bật trong Settings và cảnh báo rõ ràng.
 *  2. Ứng dụng còn kiểm tra công tắc riêng trong [ConsentStore]; tắt công tắc là
 *     ngừng ghi ngay, kể cả khi quyền hệ thống vẫn còn.
 *
 * Chỉ lưu tiêu đề/nội dung rút gọn để phục vụ thống kê; không lưu ảnh, không gửi
 * đi đâu — dữ liệu nằm trong Room của ứng dụng và xóa được bằng một nút bấm.
 */
class NotificationLogService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        scope.launch {
            val consent = ConsentStore(applicationContext)
            if (!consent.hasAcceptedTerms.first()) return@launch
            if (!consent.notificationLogEnabled.first()) return@launch

            val extras = notification.notification.extras
            DeviceGuardDatabase.get(applicationContext).notificationLogDao().insert(
                NotificationLogEntity(
                    packageName = notification.packageName,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(MAX_LEN),
                    text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(MAX_LEN),
                    category = notification.notification.category,
                    postedAt = notification.postTime,
                    isOngoing = notification.isOngoing
                )
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val MAX_LEN = 200
    }
}
