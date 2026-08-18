package com.deviceguard.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.consentDataStore by preferencesDataStore(name = "deviceguard_consent")

/**
 * Lưu trạng thái đồng ý của người dùng.
 *
 * Nguyên tắc: không một collector nào được chạy trước khi [hasAcceptedTerms] = true.
 * Từng nhóm dữ liệu nhạy cảm (danh bạ, tin nhắn, nhật ký thông báo) còn có công tắc
 * riêng, mặc định TẮT, người dùng bật/tắt bất cứ lúc nào trong màn hình Cài đặt.
 */
class ConsentStore(private val context: Context) {

    val hasAcceptedTerms: Flow<Boolean> =
        context.consentDataStore.data.map { it[KEY_TERMS] ?: false }

    val backgroundCollectionEnabled: Flow<Boolean> =
        context.consentDataStore.data.map { it[KEY_BACKGROUND] ?: false }

    val notificationLogEnabled: Flow<Boolean> =
        context.consentDataStore.data.map { it[KEY_NOTIFICATION_LOG] ?: false }

    val personalDataEnabled: Flow<Boolean> =
        context.consentDataStore.data.map { it[KEY_PERSONAL_DATA] ?: false }

    suspend fun acceptTerms() = context.consentDataStore.edit { it[KEY_TERMS] = true }

    suspend fun setBackgroundCollection(enabled: Boolean) =
        context.consentDataStore.edit { it[KEY_BACKGROUND] = enabled }

    suspend fun setNotificationLog(enabled: Boolean) =
        context.consentDataStore.edit { it[KEY_NOTIFICATION_LOG] = enabled }

    suspend fun setPersonalData(enabled: Boolean) =
        context.consentDataStore.edit { it[KEY_PERSONAL_DATA] = enabled }

    /** Rút lại toàn bộ đồng ý — gọi kèm với việc xóa sạch cơ sở dữ liệu. */
    suspend fun revokeAll() = context.consentDataStore.edit { it.clear() }

    private companion object {
        val KEY_TERMS = booleanPreferencesKey("terms_accepted")
        val KEY_BACKGROUND = booleanPreferencesKey("background_collection")
        val KEY_NOTIFICATION_LOG = booleanPreferencesKey("notification_log")
        val KEY_PERSONAL_DATA = booleanPreferencesKey("personal_data")
    }
}
