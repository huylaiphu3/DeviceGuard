package com.deviceguard.data.collector

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

/** Thông tin phần cứng/hệ điều hành ổn định, đọc một lần khi mở màn hình. */
data class StaticDeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildFingerprint: String,
    val supportedAbis: List<String>,
    val isEmulator: Boolean
)

/** Trạng thái động, đọc lại ở mỗi lần thu thập. */
data class DeviceState(
    val batteryPercent: Int,
    val batteryStatus: String,
    val batteryTempC: Float,
    val isCharging: Boolean,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val networkType: String,
    val isMetered: Boolean
)

/**
 * Khâu 1 – Thu thập: thông tin hệ thống của CHÍNH thiết bị đang chạy ứng dụng.
 * Toàn bộ API dùng ở đây đều là API công khai, không cần root.
 */
class DeviceInfoCollector(private val context: Context) {

    fun staticInfo(): StaticDeviceInfo = StaticDeviceInfo(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        device = Build.DEVICE,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH,
        buildFingerprint = Build.FINGERPRINT,
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        isEmulator = detectEmulator()
    )

    fun currentState(): DeviceState {
        val battery = readBattery()
        val storage = readStorage()
        val ram = readRam()
        val network = readNetwork()
        return DeviceState(
            batteryPercent = battery.first,
            batteryStatus = battery.second,
            batteryTempC = battery.third,
            isCharging = battery.second == "charging" || battery.second == "full",
            storageTotalBytes = storage.first,
            storageFreeBytes = storage.second,
            ramTotalBytes = ram.first,
            ramAvailableBytes = ram.second,
            networkType = network.first,
            isMetered = network.second
        )
    }

    private fun readBattery(): Triple<Int, String, Float> {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        return Triple(percent, status, tempC)
    }

    private fun readStorage(): Pair<Long, Long> {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return total to free
    }

    private fun readRam(): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem to info.availMem
    }

    private fun readNetwork(): Pair<String, Boolean> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?: return "offline" to false
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return type to metered
    }

    /**
     * Nhận diện môi trường máy ảo. Có ích khi viết luận văn: kết quả đo trên
     * emulator cần được ghi chú rõ là không phản ánh phần cứng thật.
     */
    private fun detectEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("vbox") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.PRODUCT.contains("sdk") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
}
