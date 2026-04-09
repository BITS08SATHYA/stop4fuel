package com.stopforfuel.app.ui.fastcash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.util.Locale

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val GradientTeal = Brush.linearGradient(listOf(Color(0xFF00BFA5), Color(0xFF00897B)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastCashInvoiceScreen(
    onBack: () -> Unit,
    onInvoiceCreated: () -> Unit = {},
    viewModel: FastCashInvoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fast Cash Invoice") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Product selector chips
            if (uiState.products.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.products.forEach { product ->
                        FilterChip(
                            selected = product.id == uiState.selectedProduct?.id,
                            onClick = { viewModel.selectProduct(product) },
                            label = { Text(product.name ?: "") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Selected product info
            uiState.selectedProduct?.let { product ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        Modifier
                            .background(GradientTeal)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(product.name ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                uiState.selectedNozzle?.let {
                                    Text("Nozzle: ${it.nozzleName ?: ""}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                            }
                            Text(
                                "${inrFormat.format(product.price ?: 0)}/L",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Amount display
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "AMOUNT (INR)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (uiState.amountInput.isBlank()) "0" else uiState.amountInput,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.amountInput.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                    if (uiState.amountInput.isNotBlank() && uiState.selectedProduct?.price != null) {
                        val amount = uiState.amountInput.toBigDecimalOrNull()
                        val price = uiState.selectedProduct!!.price!!
                        if (amount != null && price > java.math.BigDecimal.ZERO) {
                            val liters = amount.divide(price, 3, java.math.RoundingMode.HALF_UP)
                            Text(
                                "$liters L",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Quick amount buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(500, 1000, 2000, 5000).forEach { amount ->
                    OutlinedButton(
                        onClick = { viewModel.setQuickAmount(amount) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("$amount", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Number pad
            val buttons = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(".", "0", "DEL")
            )
            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (key == "DEL") viewModel.deleteLastDigit()
                                    else viewModel.appendDigit(key)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "DEL") {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                            } else {
                                Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.weight(1f))

            // Success message
            if (uiState.successBillNo != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Bill ${uiState.successBillNo} created!", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { viewModel.clearAmount() }) {
                            Text("New")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Error
            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            // Create button
            Button(
                onClick = { viewModel.createInvoice() },
                enabled = uiState.amountInput.isNotBlank() && !uiState.isCreating && uiState.selectedProduct != null && uiState.shiftId != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.FlashOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Cash Invoice", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
