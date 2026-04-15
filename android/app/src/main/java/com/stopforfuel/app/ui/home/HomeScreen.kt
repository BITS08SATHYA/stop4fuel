package com.stopforfuel.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

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
    onNavigateToFastCashInvoice: () -> Unit = {},
    onNavigateToInvoiceBillExplorer: () -> Unit = {},
    onNavigateToStatementExplorer: () -> Unit = {},
    onNavigateToStockTransfer: () -> Unit = {},
    onNavigateToAttendance: () -> Unit = {},
    onNavigateToInvoiceUpload: () -> Unit = {},
    onNavigateToMyApprovals: () -> Unit = {},
    onNavigateToApprovalInbox: () -> Unit = {},
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
                Column(Modifier.fillMaxHeight()) {
                    // Header
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "StopForFuel",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(uiState.userName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            uiState.userRole,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()

                    // Scrollable menu items
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ── Operations ──
                        DrawerSectionHeader("Operations")
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
                        DrawerItem(Icons.Default.CameraAlt, "Invoice Upload") {
                            scope.launch { drawerState.close() }; onNavigateToInvoiceUpload()
                        }
                        DrawerItem(Icons.Default.FlashOn, "Fast Cash Invoice") {
                            scope.launch { drawerState.close() }; onNavigateToFastCashInvoice()
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // ── Payments ──
                        DrawerSectionHeader("Payments")
                        DrawerItem(Icons.Default.Search, "Invoice Explorer") {
                            scope.launch { drawerState.close() }; onNavigateToInvoiceBillExplorer()
                        }
                        DrawerItem(Icons.Default.Description, "Statement Explorer") {
                            scope.launch { drawerState.close() }; onNavigateToStatementExplorer()
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // ── Approvals ──
                        DrawerSectionHeader("Approvals")
                        if (isManager) {
                            DrawerItem(Icons.Default.Inbox, "Approvals Inbox") {
                                scope.launch { drawerState.close() }; onNavigateToApprovalInbox()
                            }
                        }
                        DrawerItem(Icons.Default.AssignmentTurnedIn, "My Requests") {
                            scope.launch { drawerState.close() }; onNavigateToMyApprovals()
                        }

                        if (isManager) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // ── Inventory ──
                            DrawerSectionHeader("Inventory")
                            DrawerItem(Icons.Default.SwapHoriz, "Stock Transfer") {
                                scope.launch { drawerState.close() }; onNavigateToStockTransfer()
                            }
                            DrawerItem(Icons.Default.Inventory, "Products") {
                                scope.launch { drawerState.close() }; onNavigateToProducts()
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // ── People ──
                            DrawerSectionHeader("People")
                            DrawerItem(Icons.Default.People, "Customers") {
                                scope.launch { drawerState.close() }; onNavigateToCustomers()
                            }
                            DrawerItem(Icons.Default.Badge, "Employees") {
                                scope.launch { drawerState.close() }; onNavigateToEmployees()
                            }
                            DrawerItem(Icons.Default.EventAvailable, "Attendance") {
                                scope.launch { drawerState.close() }; onNavigateToAttendance()
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // ── Analytics ──
                            DrawerSectionHeader("Analytics")
                            DrawerItem(Icons.Default.Dashboard, "Dashboard") {
                                scope.launch { drawerState.close() }; onNavigateToDashboard()
                            }
                        }
                    }

                    // Logout pinned at bottom
                    HorizontalDivider()
                    DrawerItem(Icons.AutoMirrored.Filled.Logout, "Logout") {
                        scope.launch { drawerState.close() }; viewModel.logout(); onLogout()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadData() }) { Text("Retry") }
                }
            } else if (uiState.isManager) {
                // --- OWNER/MANAGER: responsive dashboard (handles phone/tablet internally) ---
                Box(Modifier.fillMaxSize().padding(padding)) {
                    OwnerTabletDashboard(uiState, onNavigateToInvoice)
                }
            } else {
                // --- CASHIER DASHBOARD ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Invoice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    CashierDashboard(uiState)
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        title.uppercase(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp
    )
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
    val activeGradient = Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00BFA5)))
    val inactiveGradient = Brush.linearGradient(listOf(Color(0xFFEF5350), Color(0xFFC62828)))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .background(if (state.activeShift != null) activeGradient else inactiveGradient)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (state.activeShift != null) {
                val shift = state.activeShift
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FlashOn, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Shift #${shift.id} - ACTIVE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            "Attendant: ${shift.attendant?.name ?: "N/A"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            "Started: ${shift.startTime?.take(16)?.replace("T", " ") ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Block, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "No Active Shift",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            "Invoices cannot be created without an open shift",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PumpSessionCard(
    session: com.stopforfuel.app.data.remote.dto.PumpSessionDto,
    onEndSession: () -> Unit
) {
    val sessionGradient = Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF6D00)))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .background(sessionGradient)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalGasStation, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "${session.pump?.name ?: "Pump"} - Session Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            "Since: ${session.startTime?.take(16)?.replace("T", " ") ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                Button(
                    onClick = onEndSession,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("End", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OwnerDashboard(uiState: HomeUiState) {
    val stats = uiState.dashboardStats ?: return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Today's Revenue", inrFormat.format(stats.todayRevenue ?: 0), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        StatCard("Fuel Volume", "${stats.todayFuelVolume ?: 0} L", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Invoices", "${stats.todayInvoiceCount ?: 0}", subtitle = "${stats.todayCashInvoices ?: 0} cash / ${stats.todayCreditInvoices ?: 0} credit", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
        StatCard("Outstanding", inrFormat.format(stats.totalOutstanding ?: 0), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    uiState.systemHealth?.let { health ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Customers", "${health.activeCustomers ?: 0}", subtitle = "${health.blockedCustomers ?: 0} blocked, ${health.inactiveCustomers ?: 0} inactive", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            StatCard("Vehicles", "${health.totalVehicles ?: 0}", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
    if (uiState.fuelProducts.isNotEmpty()) {
        Text("Current Fuel Prices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        uiState.fuelProducts.forEach { product ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(product.name ?: "", fontWeight = FontWeight.Bold)
                    Text("${inrFormat.format(product.price ?: 0)} / ${product.unit ?: "L"}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
}

// Vibrant gradient color pairs for cashier dashboard
private val CashierGradientGreen = Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00C853)))
private val CashierGradientBlue = Brush.linearGradient(listOf(Color(0xFF448AFF), Color(0xFF2962FF)))
private val CashierGradientOrange = Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF6D00)))
private val CashierGradientPurple = Brush.linearGradient(listOf(Color(0xFFE040FB), Color(0xFFAA00FF)))
private val CashierGradientTeal = Brush.linearGradient(listOf(Color(0xFF1DE9B6), Color(0xFF00BFA5)))
private val CashierGradientPink = Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFC51162)))
private val CashierGradientCyan = Brush.linearGradient(listOf(Color(0xFF18FFFF), Color(0xFF00B8D4)))
private val CashierGradientAmber = Brush.linearGradient(listOf(Color(0xFFFFD740), Color(0xFFFFC400)))

@Composable
private fun CashierGradientStatCard(
    icon: ImageVector,
    title: String,
    value: String,
    gradient: Brush,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .background(gradient)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        value,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashierInfoBar(uiState: HomeUiState) {
    val shift = uiState.activeShift
    val health = uiState.backendHealth

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Cashier name
            if (shift != null) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF00BFA5).copy(alpha = 0.15f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, Modifier.size(12.dp), tint = Color(0xFF00BFA5))
                        Spacer(Modifier.width(4.dp))
                        Text(shift.attendant?.name ?: "—", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5))
                    }
                }
            }

            // Health dots
            val apiUp = health?.status == "UP"
            val dbUp = health?.database == "UP"
            Row(verticalAlignment = Alignment.CenterVertically) {
                val apiColor = if (apiUp) Color(0xFF4CAF50) else Color(0xFFF44336)
                val dbColor = if (dbUp) Color(0xFF4CAF50) else Color(0xFFF44336)
                androidx.compose.foundation.Canvas(Modifier.size(6.dp)) { drawCircle(apiColor) }
                Spacer(Modifier.width(2.dp))
                Text("API", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = apiColor)
                Spacer(Modifier.width(6.dp))
                androidx.compose.foundation.Canvas(Modifier.size(6.dp)) { drawCircle(dbColor) }
                Spacer(Modifier.width(2.dp))
                Text("DB", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = dbColor)
            }

            // Fuel prices inline
            uiState.fuelProducts.forEach { product ->
                val upper = (product.name ?: "").uppercase()
                val abbr = when {
                    upper.contains("PETROL") || upper == "MS" -> "MS"
                    upper.contains("XTRA") || upper.contains("XP") || upper.contains("PREMIUM") -> "XP"
                    upper.contains("DIESEL") || upper == "HSD" -> "HSD"
                    else -> (product.name ?: "").take(3).uppercase()
                }
                Text(
                    "$abbr ₹${String.format("%.0f", product.price ?: 0)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CashierDashboard(uiState: HomeUiState) {
    val cd = uiState.cashierDashboard ?: return

    // Compact info bar with fuel prices + health
    CashierInfoBar(uiState)
    Spacer(Modifier.height(16.dp))

    // Shift Summary header
    Text(
        "SHIFT SUMMARY",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp
    )
    Spacer(Modifier.height(12.dp))

    // Sales breakdown — vibrant gradients
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CashierGradientStatCard(
            Icons.Default.AccountBalance, "Cash Sales",
            inrFormat.format(cd.cashBillTotal ?: 0),
            subtitle = "${cd.cashInvoiceCount ?: 0} bills",
            gradient = CashierGradientGreen,
            modifier = Modifier.weight(1f)
        )
        CashierGradientStatCard(
            Icons.Default.CreditCard, "Credit Sales",
            inrFormat.format(cd.creditBillTotal ?: 0),
            subtitle = "${cd.creditInvoiceCount ?: 0} bills",
            gradient = CashierGradientBlue,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(12.dp))

    // Total invoices + cash in hand
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CashierGradientStatCard(
            Icons.Default.Receipt, "Total Invoices",
            "${cd.totalInvoiceCount ?: 0}",
            gradient = CashierGradientPurple,
            modifier = Modifier.weight(1f)
        )
        CashierGradientStatCard(
            Icons.Default.Wallet, "Cash in Hand",
            inrFormat.format(cd.cashInHand ?: 0),
            gradient = CashierGradientTeal,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(12.dp))

    // E-advances + Expenses
    val eAdvanceTotal = cd.eAdvanceTotals?.get("total") ?: java.math.BigDecimal.ZERO
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CashierGradientStatCard(
            Icons.Default.CreditScore, "E-Advances",
            inrFormat.format(eAdvanceTotal),
            gradient = CashierGradientCyan,
            modifier = Modifier.weight(1f)
        )
        CashierGradientStatCard(
            Icons.Default.MoneyOff, "Expenses",
            inrFormat.format(cd.expenseTotal ?: java.math.BigDecimal.ZERO),
            gradient = CashierGradientPink,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(12.dp))

    // Payments
    val paymentsTotal = (cd.billPaymentTotal ?: java.math.BigDecimal.ZERO).add(cd.statementPaymentTotal ?: java.math.BigDecimal.ZERO)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CashierGradientStatCard(
            Icons.Default.Payments, "Payments",
            inrFormat.format(paymentsTotal),
            gradient = CashierGradientOrange,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(16.dp))

    // Recent invoices with colored accent
    if (!cd.recentInvoices.isNullOrEmpty()) {
        Text(
            "RECENT INVOICES",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(8.dp))
        cd.recentInvoices.forEach { inv ->
            val accentColor = when (inv.billType?.uppercase()) {
                "CREDIT" -> Color(0xFF448AFF)
                else -> Color(0xFF00C853)
            }
            Card(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Accent bar
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(inv.billNo ?: "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${inv.billType ?: ""} · ${inv.paymentMode ?: ""} · ${inv.customerName ?: "Walk-in"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        inrFormat.format(inv.netAmount ?: 0),
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}
