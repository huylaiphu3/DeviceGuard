package com.deviceguard.data.recovery

/**
 * Ba tầng kỹ thuật của khâu 2 – Phục hồi, xếp theo mức độ can thiệp tăng dần.
 * Xem phân tích đầy đủ trong docs/03-phuc-hoi-du-lieu.md.
 */
enum class RecoverySource(val label: String, val requiresRoot: Boolean) {
    /** Tầng 1: thùng rác MediaStore (API 30+). Khôi phục nguyên vẹn, có API chính thức. */
    MEDIASTORE_TRASH("Thùng rác hệ thống", false),

    /** Tầng 2: tệp còn sót trong cache/.trashed/LOST.DIR. Khôi phục bằng cách sao chép. */
    RESIDUAL_FILE("Tệp còn sót", false),

    /** Tầng 3: cắt tệp theo chữ ký từ ảnh đĩa (file carving). Cần ảnh đĩa/quyền root. */
    CARVED("Cắt theo chữ ký", true)
}

enum class RecoveryConfidence(val label: String) {
    HIGH("Cao – tệp còn nguyên vẹn"),
    MEDIUM("Trung bình – còn nội dung, có thể mất metadata"),
    LOW("Thấp – chỉ dựng lại được một phần")
}

data class RecoveryItem(
    val id: Long = 0,
    val source: RecoverySource,
    val displayName: String,
    /** URI (tầng 1) hoặc đường dẫn tuyệt đối (tầng 2, 3). */
    val locator: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val deletedAt: Long?,
    val confidence: RecoveryConfidence,
    val restorable: Boolean,
    val restoredAt: Long? = null
)

/** Tiến độ của một lượt quét, phát ra liên tục để UI hiển thị. */
sealed interface ScanProgress {
    data class Stage(val name: String, val detail: String) : ScanProgress
    data class Bytes(val processed: Long, val total: Long) : ScanProgress
    data class Found(val item: RecoveryItem) : ScanProgress
    data class Finished(val total: Int, val elapsedMs: Long) : ScanProgress
    data class Failed(val reason: String) : ScanProgress
}
