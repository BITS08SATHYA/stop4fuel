package com.stopforfuel.app.ui.employee

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeManageScreen(
    onBack: () -> Unit,
    viewModel: EmployeeManageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    com.stopforfuel.app.ui.AutoRefreshOnResume { viewModel.loadAll() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Passcode dialog
    if (state.newPasscode != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPasscodeDialog() },
            title = { Text("New Passcode") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("${state.newPasscodeForUser}'s new passcode:")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        state.newPasscode!!,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Share this with the employee",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPasscodeDialog() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Employees") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                label = { Text("Search employees") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pending reset requests
                    if (state.resetRequests.isNotEmpty()) {
                        item {
                            Text(
                                "Pending Reset Requests (${state.resetRequests.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        items(state.resetRequests) { request ->
                            ResetRequestCard(
                                request = request,
                                onApprove = { viewModel.approveResetRequest(request.id) },
                                onReject = { viewModel.rejectResetRequest(request.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // Employee list
                    item {
                        Text(
                            "All Employees (${state.employees.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(state.employees) { employee ->
                        EmployeeCard(
                            employee = employee,
                            onResetPasscode = { viewModel.resetPasscode(employee.id, employee.name) },
                            onToggleStatus = { viewModel.toggleStatus(employee.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeCard(
    employee: com.stopforfuel.app.data.remote.dto.AdminUserDto,
    onResetPasscode: () -> Unit,
    onToggleStatus: () -> Unit = {}
) {
    var showConfirm by remember { mutableStateOf(false) }
    val isActive = employee.status?.uppercase() == "ACTIVE"

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Reset Passcode?") },
            text = { Text("Reset passcode for ${employee.name}? The current passcode will be invalidated.") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onResetPasscode() }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            employee.name ?: "Unknown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        com.stopforfuel.app.ui.customer.StatusBadge(status = employee.status ?: "ACTIVE")
                    }
                Text(
                    "${employee.designation ?: employee.role ?: ""} | ${employee.phone ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
                FilledTonalButton(onClick = { showConfirm = true }) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset PIN")
                }
            }
            // Toggle status row
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggleStatus) {
                    Text(if (isActive) "Deactivate" else "Activate")
                }
            }
        }
    }
}

@Composable
private fun ResetRequestCard(
    request: com.stopforfuel.app.data.remote.dto.PasscodeResetRequestDto,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.userName ?: "Unknown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Phone: ${request.phone ?: ""} | ${request.requestedAt?.take(16)?.replace("T", " ") ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = onApprove) { Text("Approve") }
                OutlinedButton(onClick = onReject) { Text("Reject") }
            }
        }
    }
}
