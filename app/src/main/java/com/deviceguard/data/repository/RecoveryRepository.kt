package com.deviceguard.data.repository

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.deviceguard.data.local.DeviceGuardDatabase
import com.deviceguard.data.local.RecoveryCandidateEntity
import com.deviceguard.data.recovery.FileCarver
import com.deviceguard.data.recovery.MediaStoreTrashScanner
import com.deviceguard.data.recovery.RecoveryConfidence
import com.deviceguard.data.recovery.RecoveryItem
import com.deviceguard.data.recovery.RecoverySource
import com.deviceguard.data.recovery.ResidualFileScanner
import com.deviceguard.data.recovery.ScanProgress
import com.deviceguard.data.recovery.toRecoveryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** Điều phối khâu 2 – Phục hồi trên cả ba tầng kỹ thuật. */
class RecoveryRepository(
    private val context: Context,
    private val database: DeviceGuardDatabase
) {

    private val trashScanner = MediaStoreTrashScanner(context)
    private val residualScanner = ResidualFileScanner(context)
    private val carver = FileCarver()

    val storedCandidates: Flow<List<RecoveryItem>> =
        database.recoveryDao().observeAll().map { rows -> rows.map { it.toModel() } }

    fun scanScope(): ResidualFileScanner.ScanScope = residualScanner.currentScope()

    fun isTrashSupported(): Boolean = trashScanner.isSupported()

    /** Quét tầng 1 + tầng 2. Phát tiến độ để UI hiển thị theo thời gian thực. */
    fun scanNonInvasive(): Flow<ScanProgress> = callbackFlow {
        val startedAt = System.currentTimeMillis()
        val found = mutableListOf<RecoveryItem>()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                trySend(ScanProgress.Stage("Thùng rác hệ thống", "Đang truy vấn MediaStore"))
                val trashed = trashScanner.scan()
                trashed.forEach { trySend(ScanProgress.Found(it)) }
                found += trashed
            }

            trySend(ScanProgress.Stage("Tệp còn sót", "Đang duyệt thư mục"))
            found += residualScanner.scan { trySend(it) }
        }.onFailure {
            trySend(ScanProgress.Failed(it.message ?: it::class.java.simpleName))
        }

        persist(found)
        trySend(ScanProgress.Finished(found.size, System.currentTimeMillis() - startedAt))
        close()
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    /**
     * Quét tầng 3 trên một ảnh đĩa do người dùng chỉ định.
     * Xem [FileCarver] để biết vì sao đầu vào phải là ảnh đĩa chứ không phải
     * `/dev/block` của máy thật.
     */
    fun carveImage(image: File): Flow<ScanProgress> = callbackFlow {
        val startedAt = System.currentTimeMillis()
        val outputDir = File(context.getExternalFilesDir(null), CARVED_DIR)
        val found = mutableListOf<RecoveryItem>()

        runCatching {
            trySend(ScanProgress.Stage("Cắt theo chữ ký", image.name))
            val carved = carver.carve(image, outputDir) { progress ->
                trySend(progress)
                if (progress is ScanProgress.Found) found += progress.item
            }
            if (found.isEmpty()) {
                found += carved.map { it.toRecoveryItem() }
            }
        }.onFailure {
            trySend(ScanProgress.Failed(it.message ?: it::class.java.simpleName))
        }

        persist(found)
        trySend(ScanProgress.Finished(found.size, System.currentTimeMillis() - startedAt))
        close()
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    /** Tầng 1: trả PendingIntent để Activity mở hộp thoại xác nhận của hệ thống. */
    fun createTrashRestoreRequest(items: List<RecoveryItem>): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            trashScanner.createRestoreRequest(items.filter { it.source == RecoverySource.MEDIASTORE_TRASH })
        } else {
            null
        }

    /** Tầng 2/3: sao chép ra thư mục "recovered" của ứng dụng. */
    suspend fun restoreByCopy(item: RecoveryItem): File? = withContext(Dispatchers.IO) {
        val restored = residualScanner.restore(item)
        if (restored != null && item.id != 0L) {
            database.recoveryDao().markRestored(item.id, System.currentTimeMillis())
        }
        restored
    }

    suspend fun clearCandidates() = withContext(Dispatchers.IO) {
        database.recoveryDao().clearUnrestored()
    }

    private suspend fun persist(items: List<RecoveryItem>) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        database.recoveryDao().insertAll(items.map { it.toEntity(now) })
    }

    private fun RecoveryItem.toEntity(discoveredAt: Long) = RecoveryCandidateEntity(
        source = source.name,
        displayName = displayName,
        locator = locator,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        deletedAt = deletedAt,
        discoveredAt = discoveredAt,
        confidence = confidence.name,
        restorable = restorable
    )

    private fun RecoveryCandidateEntity.toModel() = RecoveryItem(
        id = id,
        source = runCatching { RecoverySource.valueOf(source) }
            .getOrDefault(RecoverySource.RESIDUAL_FILE),
        displayName = displayName,
        locator = locator,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        deletedAt = deletedAt,
        confidence = runCatching { RecoveryConfidence.valueOf(confidence) }
            .getOrDefault(RecoveryConfidence.LOW),
        restorable = restorable,
        restoredAt = restoredAt
    )

    private companion object {
        const val CARVED_DIR = "carved"
    }
}
