package com.stopforfuel.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToInvoice: () -> Unit,
    onNavigateToShiftInvoices: () -> Unit,
    onNavigateToStartSession: () -> Unit,
    onNavigateToEndSession: (Long) -> Unit,
    onNavigateToCustomers: () -> Unit = {},
    onNavigateToEmployees: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToProducts: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    com.stopforfuel.app.ui.AutoRefreshOnResume { viewModel.loadData() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val role = uiState.userRole.uppercase()
    val isManager = role == "OWNER" || role == "MANAGER"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Header
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("StopForFuel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(uiState.userName, style = MaterialTheme.typography.bodyMedium)
                    Text(uiState.userRole, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()

                DrawerItem(Icons.Default.Dashboard, "Dashboard") {
                    scope.launch { drawerState.close() }; onNavigateToDashboard()
                }
                DrawerItem(Icons.Default.Receipt, "New Invoice") {
                    scope.launch { drawerState.close() }; onNavigateToInvoice()
                }
                DrawerItem(Icons.AutoMirrored.Filled.List, "Shift Bills") {
                    scope.launch { drawerState.close() }; onNavigateToShiftInvoices()
                }
                DrawerItem(Icons.Default.LocalGasStation, "Pump Session") {
                    scope.launch { drawerState.close() }
                    if (uiState.activePumpSession != null) onNavigateToEndSession(uiState.activePumpSession!!.id)
                    else onNavigateToStartSession()
                }

                if (isManager) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Management", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    DrawerItem(Icons.Default.People, "Customers") {
                        scope.launch { drawerState.close() }; onNavigateToCustomers()
                    }
                    DrawerItem(Icons.Default.Badge, "Employees") {
                        scope.launch { drawerState.close() }; onNavigateToEmployees()
                    }
                    DrawerItem(Icons.Default.Inventory, "Products") {
                        scope.launch { drawerState.close() }; onNavigateToProducts()
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DrawerItem(Icons.AutoMirrored.Filled.Logout, "Logout") {
                    scope.launch { drawerState.close() }; viewModel.logout(); onLogout()
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("StopForFuel") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadData() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadData() }) { Text("Retry") }
                } else {
                    ShiftStatusCard(uiState)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.activePumpSession != null) {
                        PumpSessionCard(
                            session = uiState.activePumpSession!!,
                            onEndSession = { onNavigateToEndSession(uiState.activePumpSession!!.id) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Quick action - New Invoice
                    Button(
                        onClick = onNavigateToInvoice,
                        enabled = uiState.activeShift != null,
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Invoice", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
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
                Text("Shift #${shift.id} - ACTIVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Attendant: ${shift.attendant?.name ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
                Text("Started: ${shift.startTime?.take(16)?.replace("T", " ") ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("No Active Shift", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Invoices cannot be created without an open shift", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${session.pump?.name ?: "Pump"} - Session Active", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Since: ${session.startTime?.take(16)?.replace("T", " ") ?: ""}", style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onEndSession) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("End")
            }
        }
    }
}
