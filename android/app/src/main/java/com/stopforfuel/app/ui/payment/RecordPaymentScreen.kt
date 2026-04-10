package com.stopforfuel.app.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.util.Locale

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordPaymentScreen(
    onBack: () -> Unit,
    onPaymentRecorded: () -> Unit,
    viewModel: RecordPaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate back on success
    LaunchedEffect(uiState.success) {
        if (uiState.success != null) {
            snackbarHostState.showSnackbar("Payment recorded successfully")
            onPaymentRecorded()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val targetLabel = if (uiState.paymentTarget == "statement") "Statement" else "Invoice"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Payment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary card
                val summary = uiState.summary
                if (summary != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "$targetLabel #${uiState.targetId}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SummaryItem("Total", summary.totalAmount)
                                SummaryItem("Received", summary.receivedAmount)
                                SummaryItem("Balance", summary.balanceAmount, highlight = true)
                            }
                            if (summary.paymentCount != null && summary.paymentCount > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${summary.paymentCount} payment(s) recorded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Amount input
                OutlinedTextField(
                    value = uiState.amount,
                    onValueChange = { viewModel.updateAmount(it) },
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("Enter amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (summary?.balanceAmount != null && summary.balanceAmount > java.math.BigDecimal.ZERO) {
                            TextButton(onClick = { viewModel.fillBalance() }) {
                                Text("Full", fontSize = 12.sp)
                            }
                        }
                    }
                )

                // Payment mode selector
                Text(
                    "Payment Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PAYMENT_MODES.forEach { mode ->
                        FilterChip(
                            selected = uiState.paymentMode == mode,
                            onClick = { viewModel.updatePaymentMode(mode) },
                            label = { Text(mode.replace("_", " "), fontSize = 12.sp) },
                            leadingIcon = if (uiState.paymentMode == mode) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                // Reference No (show for non-cash)
                if (uiState.paymentMode != "CASH") {
                    OutlinedTextField(
                        value = uiState.referenceNo,
                        onValueChange = { viewModel.updateReferenceNo(it) },
                        label = { Text("Reference No") },
                        placeholder = {
                            Text(
                                when (uiState.paymentMode) {
                                    "UPI" -> "UPI ref / UTR number"
                                    "CHEQUE" -> "Cheque number"
                                    "NEFT", "BANK_TRANSFER" -> "UTR / Transaction ID"
                                    "CARD" -> "Approval code"
                                    else -> "Reference number"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Remarks
                OutlinedTextField(
                    value = uiState.remarks,
                    onValueChange = { viewModel.updateRemarks(it) },
                    label = { Text("Remarks (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2
                )

                Spacer(Modifier.height(8.dp))

                // Submit button
                Button(
                    onClick = { viewModel.submit() },
                    enabled = !uiState.isSubmitting && uiState.amount.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (uiState.isSubmitting) "Recording..." else "Record Payment",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: java.math.BigDecimal?,
    highlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            inrFormat.format(amount ?: 0),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (highlight) Color(0xFFEF5350)
            else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
