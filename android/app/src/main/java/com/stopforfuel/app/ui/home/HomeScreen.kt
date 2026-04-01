package com.stopforfuel.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToInvoice: () -> Unit,
    onNavigateToShiftInvoices: () -> Unit,
    onNavigateToStartSession: () -> Unit,
    onNavigateToEndSession: (Long) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StopForFuel") },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // User info
            Text(
                text = "Welcome, ${uiState.userName}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = uiState.userRole,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.loadData() }) {
                    Text("Retry")
                }
            } else {
                // Shift status card
                ShiftStatusCard(uiState)

                Spacer(modifier = Modifier.height(16.dp))

                // Pump session card (for attendants or anyone with active session)
                if (uiState.activePumpSession != null) {
                    PumpSessionCard(
                        session = uiState.activePumpSession!!,
                        onEndSession = { onNavigateToEndSession(uiState.activePumpSession!!.id) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action buttons
                val hasShift = uiState.activeShift != null

                Button(
                    onClick = onNavigateToInvoice,
                    enabled = hasShift,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Invoice", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onNavigateToShiftInvoices,
                    enabled = hasShift,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shift Bills")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pump session button
                if (uiState.activePumpSession == null) {
                    OutlinedButton(
                        onClick = onNavigateToStartSession,
                        enabled = hasShift,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Pump Session")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftStatusCard(state: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.activeShift != null)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (state.activeShift != null) {
                val shift = state.activeShift
                Text(
                    text = "Shift #${shift.id} - ACTIVE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Attendant: ${shift.attendant?.name ?: "N/A"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Started: ${shift.startTime?.take(16)?.replace("T", " ") ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No Active Shift",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Invoices cannot be created without an open shift",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun PumpSessionCard(
    session: com.stopforfuel.app.data.remote.dto.PumpSessionDto,
    onEndSession: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${session.pump?.name ?: "Pump"} - Session Active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Since: ${session.startTime?.take(16)?.replace("T", " ") ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FilledTonalButton(onClick = onEndSession) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("End")
            }
        }
    }
}
