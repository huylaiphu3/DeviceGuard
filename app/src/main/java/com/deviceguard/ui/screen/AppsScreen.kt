package com.deviceguard.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deviceguard.data.local.InstalledAppEntity
import com.deviceguard.ui.component.SectionCard
import com.deviceguard.ui.component.formatBytes
import com.deviceguard.ui.component.formatShortDate
import com.deviceguard.ui.theme.DeviceGuardColors
import com.deviceguard.ui.viewmodel.AppsViewModel

/** Khâu 1 – Thu thập: kiểm kê ứng dụng và quyền nhạy cảm chúng đang giữ. */
@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val inventory by viewModel.inventory.collectAsState()
    val newlyInstalled by viewModel.newlyInstalled.collectAsState()
    val showSystem by viewModel.showSystemApps.collectAsState()

    val visible = inventory.filter { showSystem || !it.isSystemApp }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
    ) {
        item {
            SectionCard(
                title = "Kiểm kê ứng dụng",
                subtitle = "${inventory.count { !it.isSystemApp }} ứng dụng người dùng • " +
                    "${inventory.count { it.isSystemApp }} ứng dụng hệ thống"
            ) {
                FilterChip(
                    selected = showSystem,
                    onClick = viewModel::toggleSystemApps,
                    label = { Text("Hiện cả ứng dụng hệ thống") }
                )
            }
        }

        if (newlyInstalled.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Mới xuất hiện so với lần thu thập trước",
                    subtitle = "So sánh hai ảnh kiểm kê gần nhất"
                ) {
                    newlyInstalled.forEach { app ->
                        Text("• ${app.label} (${app.packageName})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                SectionCard(title = "Chưa có dữ liệu") {
                    Text("Hãy bấm \"Thu thập ngay\" ở tab Tổng quan.")
                }
            }
        }

        items(visible, key = { it.packageName }) { app -> AppRow(app) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppRow(app: InstalledAppEntity) {
    val dangerous = app.grantedDangerousPermissions
        .split(",")
        .filter { it.isNotBlank() }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (dangerous.isNotEmpty()) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${dangerous.size} quyền nhạy cảm") }
                    )
                }
            }
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "v${app.versionName ?: app.versionCode} • ${formatBytes(app.apkSizeBytes)} • " +
                    "target SDK ${app.targetSdk} • cài ${formatShortDate(app.firstInstallTime)}",
                style = MaterialTheme.typography.labelSmall
            )
            if (app.installerPackage != null) {
                Text(
                    "Nguồn cài: ${app.installerPackage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (dangerous.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    dangerous.joinToString(", ") { it.substringAfterLast('.') },
                    style = MaterialTheme.typography.labelSmall,
                    color = DeviceGuardColors.notice
                )
            }
        }
    }
}
