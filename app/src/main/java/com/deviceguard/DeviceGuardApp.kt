package com.deviceguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.deviceguard.core.ConsentStore
import com.deviceguard.data.analysis.RatDetector
import com.deviceguard.data.analysis.UsageAnalyzer
import com.deviceguard.data.local.DeviceGuardDatabase
import com.deviceguard.data.repository.CollectionRepository
import com.deviceguard.data.repository.RecoveryRepository

/**
 * Container phụ thuộc viết tay — đề tài không cần Hilt, giữ đồ thị phụ thuộc
 * hiển hiện ở một chỗ giúp phần trình bày kiến trúc trong luận văn ngắn gọn hơn.
 */
class AppContainer(application: Application) {
    val database: DeviceGuardDatabase = DeviceGuardDatabase.get(application)
    val consentStore = ConsentStore(application)
    val collectionRepository = CollectionRepository(application, database, consentStore)
    val recoveryRepository = RecoveryRepository(application, database)
    val usageAnalyzer = UsageAnalyzer()
    val ratDetector = RatDetector()
}

class DeviceGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_COLLECTION,
            getString(R.string.collection_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.collection_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_COLLECTION = "collection"
    }
}
