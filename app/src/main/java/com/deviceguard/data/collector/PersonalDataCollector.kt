package com.deviceguard.data.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony

/**
 * Thống kê tổng hợp về dữ liệu cá nhân trên chính máy này.
 *
 * Cố ý chỉ ĐẾM, không đọc/không lưu nội dung: mục tiêu của luận văn ở đây là chứng
 * minh khả năng truy xuất qua Content Provider, không phải sao chép dữ liệu.
 * Kết quả không được ghi vào Room, chỉ hiển thị tại chỗ rồi biến mất.
 */
data class PersonalDataSummary(
    val contactCount: Int?,
    val callLogCount: Int?,
    val smsCount: Int?
)

class PersonalDataCollector(private val context: Context) {

    fun summarize(): PersonalDataSummary = PersonalDataSummary(
        contactCount = countIfPermitted(
            Manifest.permission.READ_CONTACTS,
            ContactsContract.Contacts.CONTENT_URI
        ),
        callLogCount = countIfPermitted(
            Manifest.permission.READ_CALL_LOG,
            CallLog.Calls.CONTENT_URI
        ),
        smsCount = countIfPermitted(
            Manifest.permission.READ_SMS,
            Telephony.Sms.CONTENT_URI
        )
    )

    private fun countIfPermitted(permission: String, uri: android.net.Uri): Int? {
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.count }
        }.getOrNull()
    }
}
