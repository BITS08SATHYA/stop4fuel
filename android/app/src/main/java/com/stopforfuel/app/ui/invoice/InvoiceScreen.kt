package com.stopforfuel.app.ui.invoice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    onBack: () -> Unit,
    onInvoiceCreated: () -> Unit,
    viewModel: InvoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPaytmDialog by remember { mutableStateOf(false) }

    if (showPaytmDialog) {
        AlertDialog(
            onDismissRequest = { showPaytmDialog = false },
            title = { Text("Paytm Integration") },
            text = { Text("Paytm payment gateway integration is coming soon. You'll be able to collect payments directly via Paytm QR/UPI.") },
            confirmButton = {
                TextButton(onClick = { showPaytmDialog = false }) { Text("OK") }
            }
        )
    }

    // Handle success
    LaunchedEffect(uiState.successBillNo) {
        if (uiState.successBillNo != null) {
            // Brief delay to show success, then navigate back
            kotlinx.coroutines.delay(1500)
            viewModel.resetForm()
            onInvoiceCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.shiftLabel) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.successBillNo != null) {
            // Success state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Invoice Created!",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Bill No: ${uiState.successBillNo}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (uiState.step == 1) {
            Step1Content(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        } else {
            Step2Content(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
                onPaytmSelected = { showPaytmDialog = true }
            )
        }
    }
}

@Composable
private fun Step1Content(
    uiState: InvoiceUiState,
    viewModel: InvoiceViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Customer toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isWalkIn) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Walk-in") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    } else {
                        AssistChip(
                            onClick = { viewModel.setWalkIn() },
                            label = { Text(uiState.selectedCustomer?.name ?: "Customer") },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    if (uiState.isWalkIn) {
                        TextButton(onClick = { /* TODO: show customer search sheet */ }) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Customer?")
                        }
                    }
                }
            }

            // Blocking-status panel (all 6 gates) — shown when a real customer is selected
            if (!uiState.isWalkIn && uiState.selectedCustomer != null) {
                item {
                    BlockingGatePanel(
                        status = uiState.blockingStatus,
                        loading = uiState.blockingStatusLoading
                    )
                }
            }

            // Quick select product buttons
            item {
                Text(
                    "QUICK SELECT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                QuickSelectRow(
                    products = uiState.products,
                    onSelect = { viewModel.selectProduct(it) }
                )
            }

            // Product buttons
            item {
                Text(
                    "PRODUCT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                ProductGrid(
                    products = uiState.products,
                    selectedId = uiState.selectedProduct?.id,
                    onSelect = { viewModel.selectProduct(it) }
                )
            }

            // Nozzle buttons (only for fuel)
            if (uiState.filteredNozzles.isNotEmpty()) {
                item {
                    Text(
                        "NOZZLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.filteredNozzles) { nozzle ->
                            val isSelected = uiState.selectedNozzle?.id == nozzle.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectNozzle(nozzle) },
                                label = {
                                    Text("${nozzle.nozzleName ?: ""} (${nozzle.pump?.name ?: ""})")
                                }
                            )
                        }
                    }
                }
            }

            // Quantity section
            if (uiState.selectedProduct != null) {
                item {
                    QuantitySection(uiState = uiState, viewModel = viewModel)
                }
            }

            // Added items list (at bottom so product grid stays visible)
            if (uiState.lineItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ADDED ITEMS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { viewModel.clearAllItems() }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                itemsIndexed(uiState.lineItems) { index, item ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.product.name ?: "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(buildString {
                                    if (item.nozzle != null) append("${item.nozzle.nozzleName} · ")
                                    append("${item.quantity}${if (item.product.category.equals("Fuel", ignoreCase = true)) "L" else ""}")
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(inrFormat.format(item.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.removeItem(index) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Bottom bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
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
                        "TOTAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = inrFormat.format(uiState.totalAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${uiState.lineItems.size} item(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { viewModel.goToStep2() },
                    enabled = uiState.lineItems.isNotEmpty(),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("NEXT")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun QuickSelectRow(
    products: List<com.stopforfuel.app.data.remote.dto.ProductDto>,
    onSelect: (com.stopforfuel.app.data.remote.dto.ProductDto) -> Unit
) {
    data class QuickBtn(val label: String, val matcher: (com.stopforfuel.app.data.remote.dto.ProductDto) -> Boolean)

    val quickButtons = listOf(
        QuickBtn("MS") { it.category?.uppercase() == "FUEL" && it.name?.contains("petrol", ignoreCase = true) == true && it.name?.contains("additive", ignoreCase = true) != true },
        QuickBtn("XP") { it.category?.uppercase() == "FUEL" && (it.name?.contains("xtra premium", ignoreCase = true) == true || it.name?.contains("extra premium", ignoreCase = true) == true) },
        QuickBtn("HSD") { it.category?.uppercase() == "FUEL" && it.name?.contains("diesel", ignoreCase = true) == true && it.name?.contains("additive", ignoreCase = true) != true },
        QuickBtn("2T Loose") { it.name?.contains("2t", ignoreCase = true) == true && it.name?.contains("loose", ignoreCase = true) == true },
        QuickBtn("2T 20ml") { it.name?.contains("2t", ignoreCase = true) == true && it.name?.contains("20", ignoreCase = true) == true },
        QuickBtn("2T 40ml") { it.name?.contains("2t", ignoreCase = true) == true && it.name?.contains("40", ignoreCase = true) == true }
    )

    val orange = Color(0xFFFF9800)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(quickButtons) { btn ->
            val matched = products.firstOrNull { btn.matcher(it) }
            if (matched != null) {
                ElevatedAssistChip(
                    onClick = { onSelect(matched) },
                    label = {
                        Text(
                            btn.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    },
                    colors = AssistChipDefaults.elevatedAssistChipColors(
                        containerColor = orange,
                        labelColor = Color.Black
                    ),
                    modifier = Modifier.height(36.dp)
                )
            }
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<com.stopforfuel.app.data.remote.dto.ProductDto>,
    selectedId: Long?,
    onSelect: (com.stopforfuel.app.data.remote.dto.ProductDto) -> Unit
) {
    // Group: Fuel first, then others
    val fuelProducts = products.filter { it.category.equals("Fuel", ignoreCase = true) }
    val otherProducts = products.filter { it.category != "Fuel" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Fuel products as large buttons
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fuelProducts) { product ->
                val isSelected = product.id == selectedId
                ElevatedFilterChip(
                    selected = isSelected,
                    onClick = { onSelect(product) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                product.name ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                inrFormat.format(product.price ?: BigDecimal.ZERO),
                                fontSize = 11.sp
                            )
                        }
                    },
                    modifier = Modifier.height(48.dp)
                )
            }
        }

        // Non-fuel as smaller chips
        if (otherProducts.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(otherProducts) { product ->
                    val isSelected = product.id == selectedId
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(product) },
                        label = { Text(product.name ?: "") }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantitySection(
    uiState: InvoiceUiState,
    viewModel: InvoiceViewModel
) {
    Column {
        // Mode toggle + input display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    uiState.isRupeesMode -> "AMOUNT (₹)"
                    uiState.selectedProduct?.category.equals("Fuel", ignoreCase = true) -> "QUANTITY (Liters)"
                    else -> "QUANTITY"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.selectedProduct?.category.equals("Fuel", ignoreCase = true)) {
                Row {
                    FilterChip(
                        selected = !uiState.isRupeesMode,
                        onClick = { if (uiState.isRupeesMode) viewModel.toggleRupeesMode() },
                        label = { Text("Liters") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = uiState.isRupeesMode,
                        onClick = { if (!uiState.isRupeesMode) viewModel.toggleRupeesMode() },
                        label = { Text("Rupees") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input display
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = uiState.quantityInput.ifEmpty { "0" },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            // Conversion display
            val inputVal = uiState.quantityInput.toBigDecimalOrNull()
            val price = uiState.selectedProduct?.price
            if (inputVal != null && price != null && price > java.math.BigDecimal.ZERO && uiState.selectedProduct?.category.equals("Fuel", ignoreCase = true)) {
                val convText = if (uiState.isRupeesMode) {
                    "= ${inputVal.divide(price, 3, java.math.RoundingMode.HALF_UP)} L"
                } else {
                    "= ₹${inputVal.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP)}"
                }
                Text(
                    text = convText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick amount buttons (for fuel in rupees mode)
        if (uiState.selectedProduct?.category.equals("Fuel", ignoreCase = true)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(500, 1000, 2000).forEach { amount ->
                    OutlinedButton(
                        onClick = { viewModel.setQuickAmount(amount) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("${inrFormat.format(amount)}")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Number pad
        val buttons = listOf(
            listOf("7", "8", "9"),
            listOf("4", "5", "6"),
            listOf("1", "2", "3"),
            listOf("C", "0", ".")
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { label ->
                        when (label) {
                            "C" -> FilledTonalButton(
                                onClick = { viewModel.clearQuantity() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("C", fontSize = 18.sp)
                            }
                            else -> OutlinedButton(
                                onClick = { viewModel.appendQuantity(label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            // Delete + Add to Bill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.deleteLastQuantityDigit() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                }
                Button(
                    onClick = { viewModel.addToList() },
                    enabled = uiState.canAddToList,
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD TO BILL")
                }
            }
        }
    }
}

@Composable
private fun Step2Content(
    uiState: InvoiceUiState,
    viewModel: InvoiceViewModel,
    modifier: Modifier = Modifier,
    onPaytmSelected: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Payment mode
            item {
                Text(
                    "PAYMENT MODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf("CASH", "UPI", "CARD", "CHEQUE", "BANK TRANSFER", "PAYTM")
                    items(modes) { mode ->
                        FilterChip(
                            selected = uiState.paymentMode == mode,
                            onClick = {
                                if (mode == "PAYTM") onPaytmSelected()
                                else viewModel.setPaymentMode(mode)
                            },
                            label = { Text(mode) }
                        )
                    }
                }
            }

            // Driver info (collapsible)
            item {
                Text(
                    "DRIVER (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.driverName,
                        onValueChange = { viewModel.setDriverName(it) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.driverPhone,
                        onValueChange = { viewModel.setDriverPhone(it) },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Invoice summary
            item {
                Text(
                    "INVOICE SUMMARY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(uiState.shiftLabel, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        uiState.lineItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "${item.product.name} · ${item.quantity}${if (item.product.category.equals("Fuel", ignoreCase = true)) "L" else ""} x ${inrFormat.format(item.unitPrice)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (item.nozzle != null) {
                                        Text(
                                            "Nozzle: ${item.nozzle.nozzleName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    inrFormat.format(item.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Customer:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (uiState.isWalkIn) "Walk-in" else uiState.selectedCustomer?.name ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "NET TOTAL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                inrFormat.format(uiState.totalAmount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Error
            if (uiState.error != null) {
                item {
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Bottom bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { viewModel.goToStep1() },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BACK")
                }
                Button(
                    onClick = { viewModel.confirmInvoice() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.height(48.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CONFIRM BILL")
                    }
                }
            }
        }
    }
}
