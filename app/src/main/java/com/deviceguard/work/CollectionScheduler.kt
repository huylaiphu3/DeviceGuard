package com.deviceguard.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Lên lịch thu thập nền. Chu kỳ tối thiểu WorkManager cho phép là 15 phút;
 * mặc định dùng 6 giờ để không làm hao pin — chính ứng dụng đo pin thì không nên
 * là nguyên nhân làm pin tụt.
 */
object CollectionScheduler {

    private const val DEFAULT_INTERVAL_HOURS = 6L

    fun enable(context: Context, intervalHours: Long = DEFAULT_INTERVAL_HOURS) {
        val request = PeriodicWorkRequestBuilder<CollectionWorker>(
            intervalHours, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CollectionWorker.NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CollectionWorker.NAME)
    }
}
