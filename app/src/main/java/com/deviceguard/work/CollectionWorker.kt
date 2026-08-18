package com.deviceguard.work

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deviceguard.DeviceGuardApp
import com.deviceguard.R
import kotlinx.coroutines.flow.first

/**
 * Tác vụ nền định kỳ của khâu 1.
 *
 * Mỗi lần chạy thành công đều đẩy một thông báo tóm tắt — việc thu thập cố ý được
 * làm cho "nhìn thấy được", đúng nguyên tắc minh bạch của đề tài.
 */
class CollectionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DeviceGuardApp).container

        if (!container.consentStore.hasAcceptedTerms.first()) return Result.success()
        if (!container.consentStore.backgroundCollectionEnabled.first()) return Result.success()

        return runCatching { container.collectionRepository.collectNow() }
            .fold(
                onSuccess = { result ->
                    notify(
                        "Đã ghi ảnh trạng thái thiết bị",
                        "${result.appCount} ứng dụng • ${result.usageRowCount} dòng thống kê sử dụng"
                    )
                    Result.success()
                },
                onFailure = { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() }
            )
    }

    private fun notify(title: String, text: String) {
        if (applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val notification = NotificationCompat.Builder(applicationContext, DeviceGuardApp.CHANNEL_COLLECTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val NAME = "deviceguard-periodic-collection"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_ATTEMPTS = 3

        @Suppress("unused")
        fun importance() = NotificationManager.IMPORTANCE_LOW
    }
}
