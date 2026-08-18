package com.deviceguard.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deviceguard.data.recovery.RecoveryItem
import com.deviceguard.data.recovery.RecoverySource
import com.deviceguard.data.recovery.ScanProgress
import com.deviceguard.ui.component.SectionCard
import com.deviceguard.ui.component.formatBytes
import com.deviceguard.ui.component.formatDateTime
import com.deviceguard.ui.theme.DeviceGuardColors
import com.deviceguard.ui.viewmodel.RecoveryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Khâu 2 – Phục hồi. */
@Composable
fun RecoveryScreen(viewModel: RecoveryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val candidates by viewModel.candidates.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    val trashRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        message = if (result.resultCode == Activity.RESULT_OK) {
            "Đã khôi phục khỏi thùng rác hệ thống."
        } else {
            "Bạn đã hủy yêu cầu khôi phục."
        }
    }

    // Ảnh đĩa để cắt theo chữ ký được chọn qua SAF rồi sao vào cache — ứng dụng
    // không tự đi tìm phân vùng nào của máy.
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val local = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(context.cacheDir, "image_to_carve.bin")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                    target
                }.getOrNull()
            }
            if (local != null && local.length() > 0) {
                viewModel.carve(local)
            } else {
                message = "Không đọc được tệp ảnh đĩa đã chọn."
            }
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
    ) {
        item {
            SectionCard(
                title = "Quét không xâm lấn",
                subtitle = viewModel.scopeDescription()
            ) {
                Text(
                    "Tìm trong thùng rác MediaStore (Android 11+) và các tệp còn sót " +
                        "trong cache/.trashed/LOST.DIR. Không cần root.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::startScan, enabled = !scanning) {
                        Text(if (scanning) "Đang quét…" else "Bắt đầu quét")
                    }
                    if (scanning) {
                        OutlinedButton(onClick = viewModel::cancelScan) { Text("Dừng") }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Cắt tệp theo chữ ký (nâng cao)",
                subtitle = "Chạy trên một tệp ảnh đĩa, không chạy trên phân vùng máy thật"
            ) {
                Text(
                    "Trên máy chưa root, ứng dụng không thể đọc thô /dev/block: SELinux " +
                        "chặn, và dữ liệu người dùng đã được mã hóa theo tệp nên block thô " +
                        "chỉ là bản mã. Hãy chọn một ảnh đĩa (ví dụ userdata trích từ " +
                        "emulator) để chạy thử thuật toán carving.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { imagePicker.launch(arrayOf("*/*")) },
                    enabled = !scanning
                ) {
                    Text("Chọn ảnh đĩa để cắt")
                }
            }
        }

        progress?.let { current ->
            item {
                SectionCard(title = "Tiến độ") {
                    when (current) {
                        is ScanProgress.Stage -> Text("${current.name}: ${current.detail}")
                        is ScanProgress.Bytes -> {
                            val fraction = if (current.total > 0) {
                                current.processed.toFloat() / current.total
                            } else {
                                0f
                            }
                            Text("${formatBytes(current.processed)} / ${formatBytes(current.total)}")
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is ScanProgress.Found -> Text("Tìm thấy: ${current.item.displayName}")
                        is ScanProgress.Finished ->
                            Text("Xong: ${current.total} mục trong ${current.elapsedMs} ms")
                        is ScanProgress.Failed ->
                            Text("Lỗi: ${current.reason}", color = DeviceGuardColors.warning)
                    }
                }
            }
        }

        message?.let { text ->
            item {
                SectionCard(title = "Thông báo") { Text(text) }
            }
        }

        item {
            val trashItems = candidates.filter { it.source == RecoverySource.MEDIASTORE_TRASH }
            SectionCard(
                title = "Ứng viên khôi phục",
                subtitle = "${candidates.size} mục • ${trashItems.size} mục trong thùng rác hệ thống"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trashItems.isNotEmpty()) {
                        Button(onClick = {
                            val request = viewModel.trashRestoreRequest(trashItems)
                            if (request != null) {
                                trashRestoreLauncher.launch(
                                    IntentSenderRequest.Builder(request.intentSender).build()
                                )
                            } else {
                                message = "Thiết bị này không hỗ trợ thùng rác MediaStore."
                            }
                        }) {
                            Text("Khôi phục tất cả từ thùng rác")
                        }
                    }
                    TextButton(onClick = viewModel::clearCandidates) { Text("Xóa danh sách") }
                }
            }
        }

        items(candidates, key = { it.id }) { item ->
            CandidateRow(item) { target ->
                viewModel.restore(target) { restored ->
                    message = if (restored != null) {
                        "Đã sao chép về: ${restored.absolutePath}"
                    } else {
                        "Không sao chép được ${target.displayName}."
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CandidateRow(item: RecoveryItem, onRestore: (RecoveryItem) -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${item.source.label} • ${item.confidence.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            item.deletedAt?.let {
                Text(
                    "Thời điểm xóa (ước lượng): ${formatDateTime(it)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                item.locator,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            if (item.restoredAt != null) {
                Text(
                    "Đã khôi phục lúc ${formatDateTime(item.restoredAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DeviceGuardColors.positive
                )
            } else if (item.source != RecoverySource.MEDIASTORE_TRASH && item.restorable) {
                TextButton(onClick = { onRestore(item) }) { Text("Sao chép về thư mục Khôi phục") }
            }
        }
    }
}
