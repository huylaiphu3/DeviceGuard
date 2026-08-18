package com.deviceguard.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deviceguard.AppContainer
import com.deviceguard.DeviceGuardApp
import com.deviceguard.core.PermissionCatalog
import com.deviceguard.core.PermissionSpec
import com.deviceguard.data.analysis.UsageAnalysis
import com.deviceguard.data.collector.DeviceInfoCollector
import com.deviceguard.data.collector.PersonalDataCollector
import com.deviceguard.data.collector.PersonalDataSummary
import com.deviceguard.data.collector.StaticDeviceInfo
import com.deviceguard.data.local.DeviceSnapshotEntity
import com.deviceguard.data.local.InstalledAppEntity
import com.deviceguard.data.recovery.RecoveryItem
import com.deviceguard.data.recovery.ScanProgress
import com.deviceguard.data.repository.CollectionResult
import com.deviceguard.work.CollectionScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

private fun windowStart(days: Long = 7) =
    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)

/** Factory chung: mọi ViewModel lấy phụ thuộc từ [AppContainer] của Application. */
object DeviceGuardViewModels {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { OverviewViewModel(app(), container()) }
        initializer { AppsViewModel(container()) }
        initializer { RecoveryViewModel(container()) }
        initializer { SettingsViewModel(app(), container()) }
    }

    private fun androidx.lifecycle.viewmodel.CreationExtras.app(): Application =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application

    private fun androidx.lifecycle.viewmodel.CreationExtras.container(): AppContainer =
        (app() as DeviceGuardApp).container
}

/** Dùng chung cho màn hình Tổng quan và màn hình Sử dụng. */
class OverviewViewModel(
    private val application: Application,
    private val container: AppContainer
) : ViewModel() {

    val staticDeviceInfo: StaticDeviceInfo = DeviceInfoCollector(application).staticInfo()

    private val _collecting = MutableStateFlow(false)
    val collecting: StateFlow<Boolean> = _collecting.asStateFlow()

    private val _lastResult = MutableStateFlow<CollectionResult?>(null)
    val lastResult: StateFlow<CollectionResult?> = _lastResult.asStateFlow()

    val latestSnapshot: StateFlow<DeviceSnapshotEntity?> =
        container.database.snapshotDao().observeLatest()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val snapshotHistory: StateFlow<List<DeviceSnapshotEntity>> =
        container.database.snapshotDao().observeSince(windowStart())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analysis: StateFlow<UsageAnalysis?> = combine(
        container.database.usageDao().observeUsageSince(windowStart()),
        container.database.usageDao().observeEventsSince(windowStart()),
        container.database.installedAppDao().observeLatestInventory(),
        container.database.notificationLogDao().observeSince(windowStart())
    ) { usage, events, apps, notifications ->
        container.usageAnalyzer.analyze(usage, events, apps, notifications)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasUsageAccess: Boolean
        get() = PermissionCatalog.hasUsageStatsAccess(application)

    fun collectNow() {
        if (_collecting.value) return
        viewModelScope.launch {
            _collecting.value = true
            _lastResult.value = runCatching { container.collectionRepository.collectNow() }
                .getOrNull()
            _collecting.value = false
        }
    }
}

class AppsViewModel(container: AppContainer) : ViewModel() {

    val inventory: StateFlow<List<InstalledAppEntity>> =
        container.database.installedAppDao().observeLatestInventory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val newlyInstalled: StateFlow<List<InstalledAppEntity>> =
        container.database.installedAppDao().observeNewlyInstalled()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    fun toggleSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
    }
}

class RecoveryViewModel(private val container: AppContainer) : ViewModel() {

    private val _progress = MutableStateFlow<ScanProgress?>(null)
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var job: Job? = null

    val candidates: StateFlow<List<RecoveryItem>> = container.recoveryRepository.storedCandidates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun scopeDescription(): String {
        val scope = container.recoveryRepository.scanScope()
        val prefix = if (scope.hasAllFilesAccess) {
            "Phạm vi: toàn bộ bộ nhớ dùng chung"
        } else {
            "Phạm vi: vùng riêng của ứng dụng (chưa cấp quyền quản lý toàn bộ tệp)"
        }
        return "$prefix — ${scope.roots.size} thư mục gốc"
    }

    fun startScan() {
        if (_scanning.value) return
        job = viewModelScope.launch {
            _scanning.value = true
            container.recoveryRepository.scanNonInvasive().collect { _progress.value = it }
            _scanning.value = false
        }
    }

    fun carve(image: File) {
        if (_scanning.value) return
        job = viewModelScope.launch {
            _scanning.value = true
            container.recoveryRepository.carveImage(image).collect { _progress.value = it }
            _scanning.value = false
        }
    }

    fun cancelScan() {
        job?.cancel()
        _scanning.value = false
    }

    fun restore(item: RecoveryItem, onResult: (File?) -> Unit) {
        viewModelScope.launch { onResult(container.recoveryRepository.restoreByCopy(item)) }
    }

    fun trashRestoreRequest(items: List<RecoveryItem>) =
        container.recoveryRepository.createTrashRestoreRequest(items)

    fun clearCandidates() {
        viewModelScope.launch { container.recoveryRepository.clearCandidates() }
    }
}

class SettingsViewModel(
    private val application: Application,
    private val container: AppContainer
) : ViewModel() {

    val termsAccepted: StateFlow<Boolean> = container.consentStore.hasAcceptedTerms
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val backgroundEnabled: StateFlow<Boolean> = container.consentStore.backgroundCollectionEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val notificationLogEnabled: StateFlow<Boolean> = container.consentStore.notificationLogEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val personalDataEnabled: StateFlow<Boolean> = container.consentStore.personalDataEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _personalSummary = MutableStateFlow<PersonalDataSummary?>(null)
    val personalSummary: StateFlow<PersonalDataSummary?> = _personalSummary.asStateFlow()

    fun acceptTerms() = viewModelScope.launch { container.consentStore.acceptTerms() }

    fun setBackground(enabled: Boolean) = viewModelScope.launch {
        container.consentStore.setBackgroundCollection(enabled)
        if (enabled) CollectionScheduler.enable(application) else CollectionScheduler.disable(application)
    }

    fun setNotificationLog(enabled: Boolean) = viewModelScope.launch {
        container.consentStore.setNotificationLog(enabled)
    }

    fun setPersonalData(enabled: Boolean) = viewModelScope.launch {
        container.consentStore.setPersonalData(enabled)
        _personalSummary.value = if (enabled) {
            PersonalDataCollector(application).summarize()
        } else {
            null
        }
    }

    fun refreshPersonalSummary() {
        if (personalDataEnabled.value) {
            _personalSummary.value = PersonalDataCollector(application).summarize()
        }
    }

    /** Rút lại đồng ý = xóa sạch dữ liệu + hủy lịch chạy nền. */
    fun revokeEverything() = viewModelScope.launch {
        CollectionScheduler.disable(application)
        container.collectionRepository.wipeAll()
        container.consentStore.revokeAll()
    }

    fun wipeData() = viewModelScope.launch { container.collectionRepository.wipeAll() }

    fun permissionStatus(): List<Pair<PermissionSpec, Boolean>> =
        PermissionCatalog.availableSpecs().map { it to PermissionCatalog.isGranted(application, it) }
}
