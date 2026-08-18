package com.deviceguard.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deviceguard.core.GrantMechanism
import com.deviceguard.core.PermissionCatalog
import com.deviceguard.ui.theme.DeviceGuardColors

/**
 * Màn hình đầu tiên khi mở ứng dụng lần đầu.
 *
 * Đây là nơi hiện thực hóa nguyên tắc "người dùng biết rõ mục đích trước khi cấp
 * quyền": liệt kê từng nhóm dữ liệu, nói rõ đọc để làm gì, và không có nhóm nào
 * bắt buộc — bỏ qua hết vẫn dùng được phần lớn ứng dụng.
 */
@Composable
fun OnboardingScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    val specs = remember { PermissionCatalog.availableSpecs() }
    var refreshToken by remember { mutableIntStateOf(0) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshToken++ }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshToken++ }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("DeviceGuard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Công cụ thu thập, phục hồi và phân tích dữ liệu trên CHÍNH thiết bị này.",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Ứng dụng này hoạt động thế nào", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Principle("Chỉ đọc dữ liệu của máy đang cài ứng dụng. Không có chức năng theo dõi máy khác.")
                Principle("Toàn bộ dữ liệu nằm trong bộ nhớ máy. Ứng dụng không có kết nối mạng ra ngoài.")
                Principle("Biểu tượng và tên ứng dụng luôn hiển thị trong danh sách ứng dụng, không ẩn.")
                Principle("Mọi quyền đều tùy chọn và thu hồi được. Có nút xóa sạch dữ liệu trong Cài đặt.")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Các quyền ứng dụng có thể xin", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cấp quyền nào thì mở khóa đúng chức năng tương ứng. Bạn có thể bỏ qua bước này và cấp sau.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        specs.forEach { spec ->
            // refreshToken buộc đọc lại trạng thái sau khi người dùng quay về từ Settings.
            val granted = remember(refreshToken) { PermissionCatalog.isGranted(context, spec) }
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (granted) Icons.Default.CheckCircle
                            else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (granted) DeviceGuardColors.positive
                            else MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            spec.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(spec.purpose, style = MaterialTheme.typography.bodySmall)
                    if (!granted) {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                when (spec.mechanism) {
                                    GrantMechanism.RUNTIME ->
                                        runtimeLauncher.launch(spec.runtimePermissions.toTypedArray())
                                    GrantMechanism.SPECIAL_SETTINGS ->
                                        PermissionCatalog.settingsIntent(context, spec)
                                            ?.let { settingsLauncher.launch(it) }
                                }
                            }) {
                                Text(
                                    if (spec.mechanism == GrantMechanism.RUNTIME) "Cấp quyền"
                                    else "Mở Cài đặt hệ thống"
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = onAccepted, modifier = Modifier.fillMaxWidth()) {
            Text("Tôi đã đọc và đồng ý — bắt đầu dùng")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Principle(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
