package com.deviceguard.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deviceguard.core.GrantMechanism
import com.deviceguard.core.PermissionCatalog
import com.deviceguard.ui.component.SectionCard
import com.deviceguard.ui.component.StatRow
import com.deviceguard.ui.theme.DeviceGuardColors
import com.deviceguard.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val background by viewModel.backgroundEnabled.collectAsState()
    val notificationLog by viewModel.notificationLogEnabled.collectAsState()
    val personalData by viewModel.personalDataEnabled.collectAsState()
    val personalSummary by viewModel.personalSummary.collectAsState()
    var refreshToken by remember { mutableIntStateOf(0) }
    var confirmRevoke by remember { mutableStateOf(false) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshToken++
        viewModel.refreshPersonalSummary()
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshToken++ }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        SectionCard(
            title = "Thu thập nền",
            subtitle = "WorkManager chạy 6 giờ một lần, mỗi lần đều đẩy thông báo cho bạn biết"
        ) {
            ToggleRow(
                label = "Bật thu thập định kỳ",
                checked = background,
                onCheckedChange = viewModel::setBackground
            )
        }

        SectionCard(
            title = "Nhóm dữ liệu tùy chọn",
            subtitle = "Tắt công tắc là dừng ghi ngay, kể cả khi quyền hệ thống vẫn còn"
        ) {
            ToggleRow(
                label = "Ghi nhật ký thông báo",
                checked = notificationLog,
                onCheckedChange = viewModel::setNotificationLog
            )
            ToggleRow(
                label = "Thống kê danh bạ / cuộc gọi / tin nhắn",
                checked = personalData,
                onCheckedChange = viewModel::setPersonalData
            )
            personalSummary?.let { summary ->
                Spacer(Modifier.height(8.dp))
                StatRow("Số liên hệ", summary.contactCount?.toString() ?: "chưa cấp quyền")
                StatRow("Số cuộc gọi", summary.callLogCount?.toString() ?: "chưa cấp quyền")
                StatRow("Số tin nhắn", summary.smsCount?.toString() ?: "chưa cấp quyền")
                Text(
                    "Ứng dụng chỉ đếm, không đọc và không lưu nội dung.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(
            title = "Trạng thái quyền",
            subtitle = "Bấm vào dòng chưa cấp để mở đúng màn hình cấp quyền"
        ) {
            val statuses = remember(refreshToken) { viewModel.permissionStatus() }
            statuses.forEach { (spec, granted) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(spec.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (granted) "Đã cấp" else "Chưa cấp",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (granted) DeviceGuardColors.positive
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!granted) {
                        TextButton(onClick = {
                            when (spec.mechanism) {
                                GrantMechanism.RUNTIME ->
                                    runtimeLauncher.launch(spec.runtimePermissions.toTypedArray())
                                GrantMechanism.SPECIAL_SETTINGS ->
                                    PermissionCatalog.settingsIntent(context, spec)
                                        ?.let { settingsLauncher.launch(it) }
                            }
                        }) {
                            Text("Cấp")
                        }
                    }
                }
            }
        }

        SectionCard(
            title = "Dữ liệu của bạn",
            subtitle = "Mọi thứ nằm trong bộ nhớ máy này và xóa được hoàn toàn"
        ) {
            OutlinedButton(
                onClick = viewModel::wipeData,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xóa toàn bộ dữ liệu đã thu thập")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmRevoke = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rút lại đồng ý và xóa sạch")
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (confirmRevoke) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text("Rút lại đồng ý?") },
            text = {
                Text(
                    "Ứng dụng sẽ hủy lịch thu thập nền, xóa toàn bộ dữ liệu đã lưu và " +
                        "quay lại màn hình giới thiệu."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevoke = false
                    viewModel.revokeEverything()
                }) {
                    Text("Xác nhận")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
