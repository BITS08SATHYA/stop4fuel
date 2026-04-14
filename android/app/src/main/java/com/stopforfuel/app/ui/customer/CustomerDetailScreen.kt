package com.stopforfuel.app.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stopforfuel.app.data.remote.dto.VehicleDto
import java.text.NumberFormat
import java.util.Locale

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    com.stopforfuel.app.ui.AutoRefreshOnResume { viewModel.loadAll() }

    // Show snackbar for action messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.customer?.name ?: "Customer") },
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
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadAll() }) { Text("Retry") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Customer Info
                item { CustomerInfoCard(state) }

                // Credit Limits
                item { CreditLimitsCard(state, viewModel) }

                // Status Actions
                item { StatusActionsCard(state, viewModel) }

                // Vehicles Header
                item {
                    Text(
                        "Vehicles (${state.vehicles.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Add Vehicle button
                item {
                    OutlinedButton(
                        onClick = { viewModel.showAddVehicle() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Vehicle")
                    }
                }

                // Vehicle rows
                items(state.vehicles) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        isExpanded = state.expandedVehicleId == vehicle.id,
                        onToggleExpand = { viewModel.toggleVehicleExpand(vehicle.id) },
                        onToggleStatus = { viewModel.toggleVehicleStatus(vehicle.id) },
                        onBlock = { viewModel.blockVehicle(vehicle.id) },
                        onUnblock = { viewModel.unblockVehicle(vehicle.id) },
                        onUpdateLimit = { limit -> viewModel.updateVehicleLiterLimit(vehicle.id, limit) }
                    )
                }
            }
        }
    }

    // Add Vehicle Dialog
    if (state.showAddVehicle) {
        AddVehicleDialog(
            vehicleTypes = state.vehicleTypes,
            products = state.products,
            onDismiss = { viewModel.hideAddVehicle() },
            onSave = { number, typeId, fuelId, cap, limit ->
                viewModel.createVehicle(number, typeId, fuelId, cap, limit)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVehicleDialog(
    vehicleTypes: List<com.stopforfuel.app.data.remote.dto.VehicleTypeDto>,
    products: List<com.stopforfuel.app.data.remote.dto.ProductDto>,
    onDismiss: () -> Unit,
    onSave: (String, Long?, Long?, String, String) -> Unit
) {
    var vehicleNumber by remember { mutableStateOf("") }
    var selectedTypeId by remember { mutableStateOf<Long?>(null) }
    var selectedFuelId by remember { mutableStateOf<Long?>(null) }
    var maxCapacity by remember { mutableStateOf("") }
    var literLimit by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }
    var fuelExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vehicleNumber,
                    onValueChange = { vehicleNumber = it.uppercase() },
                    label = { Text("Vehicle Number *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Vehicle type dropdown
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = vehicleTypes.find { it.id == selectedTypeId }?.typeName ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vehicle Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        vehicleTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.typeName ?: "") },
                                onClick = { selectedTypeId = type.id; typeExpanded = false }
                            )
                        }
                    }
                }

                // Fuel type dropdown
                ExposedDropdownMenuBox(
                    expanded = fuelExpanded,
                    onExpandedChange = { fuelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = products.find { it.id == selectedFuelId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fuel Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = fuelExpanded, onDismissRequest = { fuelExpanded = false }) {
                        products.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name ?: "") },
                                onClick = { selectedFuelId = p.id; fuelExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = maxCapacity,
                    onValueChange = { maxCapacity = it },
                    label = { Text("Max Capacity (L)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                OutlinedTextField(
                    value = literLimit,
                    onValueChange = { literLimit = it },
                    label = { Text("Liter Limit / Month") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(vehicleNumber, selectedTypeId, selectedFuelId, maxCapacity, literLimit) },
                enabled = vehicleNumber.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CustomerInfoCard(state: CustomerDetailState) {
    val customer = state.customer ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(customer.name ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusBadge(status = customer.status ?: "ACTIVE")
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (customer.group?.groupName != null) {
                Text("Group: ${customer.group.groupName}", style = MaterialTheme.typography.bodySmall)
            }
            if (!customer.phoneNumbers.isNullOrEmpty()) {
                Text("Phone: ${customer.phoneNumbers.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CreditLimitsCard(state: CustomerDetailState, viewModel: CustomerDetailViewModel) {
    val customer = state.customer ?: return
    var amountText by remember(customer.creditLimitAmount) {
        mutableStateOf(customer.creditLimitAmount?.toPlainString() ?: "")
    }
    var litersText by remember(customer.creditLimitLiters) {
        mutableStateOf(customer.creditLimitLiters?.toPlainString() ?: "")
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Credit Limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Credit info summary
            state.creditInfo?.let { info ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Outstanding", style = MaterialTheme.typography.labelSmall)
                        Text(
                            inrFormat.format(info.balance ?: 0),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if ((info.balance ?: java.math.BigDecimal.ZERO) > java.math.BigDecimal.ZERO)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("Consumed Liters", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${info.consumedLiters ?: 0} L",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount Limit (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = litersText,
                onValueChange = { litersText = it },
                label = { Text("Liters Limit") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.updateCreditLimits(amountText, litersText) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Credit Limits")
            }
        }
    }
}

@Composable
private fun StatusActionsCard(state: CustomerDetailState, viewModel: CustomerDetailViewModel) {
    val customer = state.customer ?: return
    val status = customer.status?.uppercase() ?: "ACTIVE"
    val orange = Color(0xFFFF9800)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (status == "ACTIVE") {
                    OutlinedButton(
                        onClick = { viewModel.toggleStatus() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Deactivate") }
                    Button(
                        onClick = { viewModel.blockCustomer() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Block") }
                } else if (status == "BLOCKED") {
                    Button(
                        onClick = { viewModel.unblockCustomer() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Unblock") }
                } else {
                    // INACTIVE
                    Button(
                        onClick = { viewModel.toggleStatus() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Activate") }
                }
            }

            // Force Unblock toggle - OWNER only
            if (state.isOwner) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Force Unblock",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Bypass credit limits for invoicing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = customer.forceUnblocked == true,
                        onCheckedChange = { viewModel.toggleForceUnblock(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = orange
                        )
                    )
                }

                // Force Unblocked banner
                if (customer.forceUnblocked == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = orange.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = orange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Force Unblocked",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = orange
                                )
                                val details = buildString {
                                    if (!customer.forceUnblockedBy.isNullOrBlank()) append("by ${customer.forceUnblockedBy}")
                                    if (!customer.forceUnblockedAt.isNullOrBlank()) {
                                        if (isNotEmpty()) append(" on ")
                                        append(customer.forceUnblockedAt.take(10))
                                    }
                                }
                                if (details.isNotBlank()) {
                                    Text(
                                        details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = orange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(
    vehicle: VehicleDto,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleStatus: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onUpdateLimit: (String) -> Unit
) {
    val status = vehicle.status?.uppercase() ?: "ACTIVE"
    var limitText by remember(vehicle.maxLitersPerMonth) {
        mutableStateOf(vehicle.maxLitersPerMonth?.toPlainString() ?: "")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        vehicle.vehicleNumber ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Limit: ${vehicle.maxLitersPerMonth ?: "None"} L | Used: ${vehicle.consumedLiters ?: 0} L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = status)
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Expanded actions
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Liter limit
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = limitText,
                            onValueChange = { limitText = it },
                            label = { Text("Liter Limit/Month") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(onClick = { onUpdateLimit(limitText) }) {
                            Text("Save")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (status) {
                            "ACTIVE" -> {
                                OutlinedButton(onClick = onToggleStatus, modifier = Modifier.weight(1f)) {
                                    Text("Deactivate")
                                }
                                Button(
                                    onClick = onBlock,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Block") }
                            }
                            "BLOCKED" -> {
                                Button(onClick = onUnblock, modifier = Modifier.weight(1f)) {
                                    Text("Unblock")
                                }
                            }
                            else -> {
                                Button(onClick = onToggleStatus, modifier = Modifier.weight(1f)) {
                                    Text("Activate")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
