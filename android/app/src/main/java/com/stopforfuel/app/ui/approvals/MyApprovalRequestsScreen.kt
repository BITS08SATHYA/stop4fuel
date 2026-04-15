package com.stopforfuel.app.ui.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stopforfuel.app.data.remote.dto.ApprovalRequestDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApprovalRequestsScreen(
    onBack: () -> Unit,
    viewModel: MyApprovalRequestsViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Requests") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.load() }) { Text("Retry") }
            }
            ui.requests.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No requests yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ui.requests, key = { it.id }) { r -> RequestCard(r) }
            }
        }
    }
}

@Composable
private fun RequestCard(r: ApprovalRequestDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    requestTypeLabel(r.requestType),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.weight(1f))
                StatusChip(r.status)
            }
            Text(
                "#${r.id}  •  ${r.createdAt ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!r.requestNote.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your note: ${r.requestNote}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!r.payload.isNullOrBlank() && r.payload != "{}") {
                Spacer(Modifier.height(4.dp))
                Text(
                    r.payload,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!r.reviewNote.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            "Admin remark",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(r.reviewNote, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusChip(status: String) {
    val (bg, fg, label) = when (status) {
        "APPROVED" -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Approved")
        "REJECTED" -> Triple(Color(0xFFFECACA), Color(0xFF991B1B), "Rejected")
        else -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "Pending")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

internal fun requestTypeLabel(type: String): String = when (type) {
    "ADD_VEHICLE" -> "Add Vehicle"
    "UNBLOCK_CUSTOMER" -> "Unblock Customer"
    "RAISE_CREDIT_LIMIT" -> "Raise Credit Limit"
    "RECORD_STATEMENT_PAYMENT" -> "Statement Payment"
    "RECORD_INVOICE_PAYMENT" -> "Invoice Payment"
    else -> type
}
