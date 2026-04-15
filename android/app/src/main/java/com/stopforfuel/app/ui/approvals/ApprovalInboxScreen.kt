package com.stopforfuel.app.ui.approvals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stopforfuel.app.data.remote.dto.ApprovalRequestDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalInboxScreen(
    onBack: () -> Unit,
    viewModel: ApprovalInboxViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var rejectTargetId by remember { mutableStateOf<Long?>(null) }
    var rejectNote by remember { mutableStateOf("") }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }
    LaunchedEffect(ui.error) {
        ui.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Approvals • ${ui.pending.size} pending") },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.pending.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No pending requests", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ui.pending, key = { it.id }) { r ->
                    PendingCard(
                        r = r,
                        actioning = ui.actioningId == r.id,
                        onApprove = { viewModel.approve(r.id, null) },
                        onReject = { rejectTargetId = r.id; rejectNote = "" }
                    )
                }
            }
        }
    }

    if (rejectTargetId != null) {
        AlertDialog(
            onDismissRequest = { rejectTargetId = null },
            title = { Text("Reject request") },
            text = {
                Column {
                    Text("Enter a reason — the cashier will see it.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectNote,
                        onValueChange = { rejectNote = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rejectNote.isNotBlank(),
                    onClick = {
                        val id = rejectTargetId!!
                        viewModel.reject(id, rejectNote)
                        rejectTargetId = null
                    }
                ) { Text("Reject") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTargetId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PendingCard(
    r: ApprovalRequestDto,
    actioning: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
            if (r.customerId != null) {
                Text(
                    "Customer #${r.customerId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!r.requestNote.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Note: ${r.requestNote}", style = MaterialTheme.typography.bodySmall)
            }
            if (!r.payload.isNullOrBlank() && r.payload != "{}") {
                Spacer(Modifier.height(4.dp))
                Text(
                    r.payload,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    enabled = !actioning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (actioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Approve")
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !actioning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reject")
                }
            }
        }
    }
}
