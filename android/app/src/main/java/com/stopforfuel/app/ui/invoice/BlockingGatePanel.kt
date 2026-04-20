package com.stopforfuel.app.ui.invoice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stopforfuel.app.data.remote.dto.BlockingGateDto
import com.stopforfuel.app.data.remote.dto.BlockingStatusResponse

@Composable
fun BlockingGatePanel(
    status: BlockingStatusResponse?,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    if (status == null && !loading) return

    var expanded by remember(status?.customerId) { mutableStateOf(status?.overall != "PASS") }

    val overallColor = when (status?.overall) {
        "PASS" -> Color(0xFF10B981)
        "WARN" -> Color(0xFFF59E0B)
        "BLOCKED" -> Color(0xFFEF4444)
        "OVERRIDE" -> Color(0xFF6366F1)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val overallLabel = when (status?.overall) {
        "PASS" -> "All checks passing"
        "WARN" -> "Approaching a threshold"
        "BLOCKED" -> "Invoice will be blocked"
        "OVERRIDE" -> "Force-unblock override"
        else -> "Checking…"
    }
    val overallIcon: ImageVector = when (status?.overall) {
        "PASS" -> Icons.Default.CheckCircle
        "WARN" -> Icons.Default.Warning
        "BLOCKED" -> Icons.Default.Block
        "OVERRIDE" -> Icons.Default.Shield
        else -> Icons.Default.HourglassEmpty
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = overallColor.copy(alpha = 0.10f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row — tap to expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    overallIcon,
                    contentDescription = null,
                    tint = overallColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        overallLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = overallColor
                    )
                    if (status?.primaryReason != null && status.overall != "PASS") {
                        Text(
                            status.primaryReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded && status != null) {
                Spacer(modifier = Modifier.height(8.dp))
                status.gates.forEach { gate ->
                    GateRow(gate)
                }
                if (!status.suggestedAction.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Action: ${status.suggestedAction}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GateRow(gate: BlockingGateDto) {
    val (color, label) = when (gate.state) {
        "PASS" -> Color(0xFF10B981) to "OK"
        "WARN" -> Color(0xFFF59E0B) to "WARN"
        "FAIL" -> Color(0xFFEF4444) to "BLOCKED"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "—"
    }
    val gateIcon: ImageVector = when (gate.key) {
        "CUSTOMER_STATUS" -> Icons.Default.Person
        "CREDIT_AMOUNT" -> Icons.Default.CurrencyRupee
        "CREDIT_LITERS" -> Icons.Default.LocalDrink
        "AGING" -> Icons.Default.Schedule
        "VEHICLE_STATUS" -> Icons.Default.DirectionsCar
        "VEHICLE_MONTHLY_LITERS" -> Icons.Default.Speed
        else -> Icons.Default.Info
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(gateIcon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                gate.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (!gate.detail.isNullOrBlank()) {
                Text(
                    gate.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
