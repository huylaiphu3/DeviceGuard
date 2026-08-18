package com.deviceguard.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.deviceguard.data.analysis.Insight
import com.deviceguard.ui.theme.DeviceGuardColors

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun InsightRow(insight: Insight, modifier: Modifier = Modifier) {
    val color = when (insight.severity) {
        Insight.Severity.INFO -> MaterialTheme.colorScheme.primary
        Insight.Severity.NOTICE -> DeviceGuardColors.notice
        Insight.Severity.WARNING -> DeviceGuardColors.warning
    }
    Column(modifier.padding(vertical = 6.dp)) {
        Text(insight.title, style = MaterialTheme.typography.titleSmall, color = color)
        Text(
            insight.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun IconStat(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
