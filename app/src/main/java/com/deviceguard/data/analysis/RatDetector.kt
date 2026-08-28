package com.deviceguard.data.analysis

import com.deviceguard.data.local.InstalledAppEntity

/**
 * Khâu 3 – Phân tích (nhánh an ninh): rà các ứng dụng ĐÃ cài trên chính máy này để
 * tìm dấu hiệu giống phần mềm điều khiển từ xa / theo dõi (RAT/spyware) kiểu AndroRAT.
 *
 * Nguyên tắc, giữ đúng tinh thần [UsageAnalyzer]:
 *  - Chỉ đọc siêu dữ liệu do khâu Thu thập lưu lại (quyền khai báo, icon launcher,
 *    dịch vụ Trợ năng, nguồn cài, lịch sử dùng). KHÔNG gọi mạng, KHÔNG chấm điểm mù.
 *  - Mỗi tín hiệu (indicator) đi kèm bằng chứng người dùng tự kiểm chứng lại được.
 *  - Đây là công cụ GỢI Ý rà soát, không phải bản án: một app hợp lệ vẫn có thể trúng
 *    vài tín hiệu. Điểm số chỉ để sắp thứ tự ưu tiên xem xét.
 *
 * Vì sao các tín hiệu này đặc trưng cho RAT: một RAT như AndroRAT thường (1) ẩn icon để
 * người dùng quên nó tồn tại, (2) gom cùng lúc quyền micro + camera + đọc SMS/danh bạ để
 * hút dữ liệu, (3) tự khởi động lại sau reboot để duy trì kết nối C2, (4) được cài kèm
 * (sideload) từ ngoài chợ ứng dụng. Không tín hiệu đơn lẻ nào là bằng chứng chắc chắn —
 * nhưng khi CHỒNG nhiều tín hiệu lên một app ít dùng, đó là thứ đáng mở ra xem.
 */
class RatDetector {

    /** Một tín hiệu nghi vấn tìm thấy trên một ứng dụng, kèm trọng số rủi ro. */
    data class Indicator(
        val id: String,
        val title: String,
        val evidence: String,
        val weight: Int
    )

    /** Tổng hợp các tín hiệu trên một ứng dụng. */
    data class Finding(
        val packageName: String,
        val label: String,
        val installerPackage: String?,
        val score: Int,
        val level: Level,
        val indicators: List<Indicator>
    ) {
        enum class Level { LOW, MEDIUM, HIGH }
    }

    data class Report(
        val scannedApps: Int,
        val findings: List<Finding>
    ) {
        val highCount: Int get() = findings.count { it.level == Finding.Level.HIGH }
        val mediumCount: Int get() = findings.count { it.level == Finding.Level.MEDIUM }
    }

    /**
     * @param apps          kiểm kê ứng dụng ở lần chụp mới nhất.
     * @param usedPackages  các gói có thời lượng tiền cảnh > 0 trong kỳ (để nhận diện
     *                      app "nằm im" nhưng ôm nhiều quyền — cần biết mới có).
     */
    fun analyze(
        apps: List<InstalledAppEntity>,
        usedPackages: Set<String> = emptySet()
    ): Report {
        // Chỉ soi ứng dụng do người dùng cài. App hệ thống có nhiều quyền là chuyện bình
        // thường và không phải mặt trận của mối đe dọa sideload kiểu AndroRAT.
        val candidates = apps.filter { !it.isSystemApp }

        val findings = candidates.mapNotNull { app ->
            val perms = app.requestedPermissions
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            val indicators = buildIndicators(app, perms, usedPackages)
            if (indicators.isEmpty()) return@mapNotNull null

            val score = indicators.sumOf { it.weight }
            Finding(
                packageName = app.packageName,
                label = app.label,
                installerPackage = app.installerPackage,
                score = score,
                level = levelOf(score),
                indicators = indicators.sortedByDescending { it.weight }
            )
        }.sortedByDescending { it.score }

        return Report(scannedApps = candidates.size, findings = findings)
    }

    private fun buildIndicators(
        app: InstalledAppEntity,
        perms: Set<String>,
        usedPackages: Set<String>
    ): List<Indicator> = buildList {
        // 1. Ẩn icon khỏi launcher — dấu hiệu che giấu điển hình của RAT.
        if (!app.hasLauncherIcon) {
            add(
                Indicator(
                    id = "hidden_icon",
                    title = "Không có biểu tượng trên màn hình chính",
                    evidence = "Ứng dụng không đăng ký activity mở từ launcher — không thấy " +
                        "icon để mở/gỡ như app thường. RAT hay ẩn mình theo cách này.",
                    weight = 4
                )
            )
        }

        // 2. Kèm dịch vụ Trợ năng — kênh mạnh để đọc màn hình, ghi phím, tự bấm.
        if (app.usesAccessibility) {
            add(
                Indicator(
                    id = "accessibility_service",
                    title = "Có dịch vụ Trợ năng (Accessibility)",
                    evidence = "Trợ năng cho phép đọc toàn bộ nội dung màn hình và tự thao tác " +
                        "thay người dùng; spyware thường lạm dụng để keylog và vượt xác nhận.",
                    weight = 4
                )
            )
        }

        // 3. Combo giám sát: thu âm/quay + đọc dữ liệu cá nhân.
        val capture = perms.intersect(setOf(P_RECORD_AUDIO, P_CAMERA))
        val harvest = perms.intersect(setOf(P_READ_SMS, P_READ_CALL_LOG, P_READ_CONTACTS))
        if (capture.isNotEmpty() && harvest.isNotEmpty()) {
            add(
                Indicator(
                    id = "surveillance_combo",
                    title = "Gom quyền thu thập kiểu theo dõi",
                    evidence = "Xin cùng lúc ${shortNames(capture + harvest)} — bộ quyền cho phép " +
                        "ghi âm/quay và hút tin nhắn/nhật ký gọi/danh bạ.",
                    weight = 3
                )
            )
        }

        // 4. Chặn/đọc SMS rồi có đường ra mạng — kênh exfil OTP, tin nhắn.
        val sms = perms.intersect(setOf(P_READ_SMS, P_RECEIVE_SMS))
        if (sms.isNotEmpty() && P_INTERNET in perms) {
            add(
                Indicator(
                    id = "sms_exfiltration",
                    title = "Đọc SMS kèm quyền ra mạng",
                    evidence = "Có ${shortNames(sms)} + INTERNET — đủ để đọc tin nhắn (kể cả mã OTP) " +
                        "và gửi ra ngoài.",
                    weight = 2
                )
            )
        }

        // 5. Tự chạy lại sau khi khởi động máy — cơ chế duy trì (persistence) của RAT.
        if (P_RECEIVE_BOOT_COMPLETED in perms) {
            add(
                Indicator(
                    id = "boot_persistence",
                    title = "Tự khởi động cùng máy",
                    evidence = "Xin RECEIVE_BOOT_COMPLETED để chạy lại sau mỗi lần reboot — cách " +
                        "RAT giữ kết nối tới máy chủ điều khiển bền bỉ.",
                    weight = 2
                )
            )
        }

        // 6. Vẽ đè lên ứng dụng khác — che giao diện, dựng màn hình giả.
        if (P_SYSTEM_ALERT_WINDOW in perms) {
            add(
                Indicator(
                    id = "overlay",
                    title = "Vẽ đè lên ứng dụng khác",
                    evidence = "Có SYSTEM_ALERT_WINDOW — có thể phủ lớp giả lên app khác hoặc " +
                        "che hành vi ngầm.",
                    weight = 2
                )
            )
        }

        // 7. Cài từ ngoài chợ ứng dụng (sideload) — kiểu phát tán của AndroRAT.
        if (isSideloaded(app.installerPackage)) {
            add(
                Indicator(
                    id = "sideloaded",
                    title = "Cài ngoài chợ ứng dụng",
                    evidence = "Nguồn cài: ${app.installerPackage ?: "không xác định"} — không qua " +
                        "cửa hàng chính thống, giống cách AndroRAT được cài thủ công.",
                    weight = 2
                )
            )
        }

        // 8. Tự cài thêm gói khác — có thể thả payload bổ sung.
        if (P_REQUEST_INSTALL_PACKAGES in perms) {
            add(
                Indicator(
                    id = "install_packages",
                    title = "Có thể cài thêm ứng dụng khác",
                    evidence = "Xin REQUEST_INSTALL_PACKAGES — cho phép thả và cài thêm gói ngoài.",
                    weight = 1
                )
            )
        }

        // 9. Ôm nhiều quyền nhạy cảm nhưng người dùng gần như không mở.
        val dangerousCount = app.grantedDangerousPermissions
            .split(",").count { it.isNotBlank() }
        if (dangerousCount >= DORMANT_PERMISSION_MIN && app.packageName !in usedPackages) {
            add(
                Indicator(
                    id = "dormant_privileged",
                    title = "Nhiều quyền nhạy cảm nhưng nằm im",
                    evidence = "Đang giữ $dangerousCount quyền nguy hiểm đã cấp nhưng không ghi nhận " +
                        "lần mở nào trong kỳ — app càng ít lộ diện càng đáng soi.",
                    weight = 1
                )
            )
        }
    }

    private fun levelOf(score: Int): Finding.Level = when {
        score >= HIGH_THRESHOLD -> Finding.Level.HIGH
        score >= MEDIUM_THRESHOLD -> Finding.Level.MEDIUM
        else -> Finding.Level.LOW
    }

    private fun isSideloaded(installer: String?): Boolean =
        installer == null || installer in SIDELOAD_INSTALLERS

    private fun shortNames(perms: Set<String>): String =
        perms.joinToString(", ") { it.substringAfterLast('.') }

    private companion object {
        const val P_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        const val P_CAMERA = "android.permission.CAMERA"
        const val P_READ_SMS = "android.permission.READ_SMS"
        const val P_RECEIVE_SMS = "android.permission.RECEIVE_SMS"
        const val P_READ_CALL_LOG = "android.permission.READ_CALL_LOG"
        const val P_READ_CONTACTS = "android.permission.READ_CONTACTS"
        const val P_INTERNET = "android.permission.INTERNET"
        const val P_RECEIVE_BOOT_COMPLETED = "android.permission.RECEIVE_BOOT_COMPLETED"
        const val P_SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW"
        const val P_REQUEST_INSTALL_PACKAGES = "android.permission.REQUEST_INSTALL_PACKAGES"

        const val DORMANT_PERMISSION_MIN = 3
        const val MEDIUM_THRESHOLD = 3
        const val HIGH_THRESHOLD = 6

        /** Trình cài đặt hệ thống / thủ công — coi là sideload (không phải chợ ứng dụng). */
        val SIDELOAD_INSTALLERS = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.shell",
            "adb"
        )
    }
}
