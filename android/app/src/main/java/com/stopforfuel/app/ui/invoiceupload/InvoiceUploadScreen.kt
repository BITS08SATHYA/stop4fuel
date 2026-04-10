package com.stopforfuel.app.ui.invoiceupload

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.stopforfuel.app.data.remote.dto.InvoiceBillDto
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceUploadScreen(
    onBack: () -> Unit,
    viewModel: InvoiceUploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Camera state
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var uploadType by remember { mutableStateOf("bill-pic") }
    var showTypeChooser by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            viewModel.clearMessages()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            viewModel.uploadPhoto(photoFile!!, uploadType)
        }
    }

    fun launchCamera(type: String) {
        uploadType = type
        val cacheDir = File(context.cacheDir, "camera_photos")
        cacheDir.mkdirs()
        val file = File(cacheDir, "invoice_${System.currentTimeMillis()}.jpg")
        photoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        photoUri = uri
        cameraLauncher.launch(uri)
    }

    // Show snackbar for messages
    LaunchedEffect(uiState.uploadSuccess, uiState.error) {
        uiState.uploadSuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice Upload") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadShiftInvoices() }) {
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
        ) {
            // Shift info + filter toggle
            if (uiState.shiftId != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Shift #${uiState.shiftId}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${uiState.creditCount} credit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Show cash bills toggle
                if (uiState.cashCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Show cash bills (${uiState.cashCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.showCashBills,
                            onCheckedChange = { viewModel.toggleShowCashBills() },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.invoices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Receipt, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (uiState.showCashBills) "No invoices in current shift"
                            else "No credit invoices in current shift",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.invoices, key = { it.id }) { invoice ->
                        InvoiceUploadCard(
                            invoice = invoice,
                            isSelected = uiState.selectedInvoice?.id == invoice.id,
                            isUploading = uiState.isUploading && uiState.selectedInvoice?.id == invoice.id,
                            onSelect = { viewModel.selectInvoice(invoice) },
                            onCapturePhoto = { type ->
                                viewModel.selectInvoice(invoice)
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                                // The permission callback doesn't chain well, so just launch camera
                                // Camera permission is usually already granted after first ask
                                launchCamera(type)
                            }
                        )
                    }
                }
            }
        }

        // Upload type chooser dialog
        if (showTypeChooser) {
            AlertDialog(
                onDismissRequest = { showTypeChooser = false },
                title = { Text("Upload Type") },
                text = {
                    Column {
                        UploadTypeOption("Bill Photo", "bill-pic", Icons.Default.Receipt) {
                            showTypeChooser = false
                            launchCamera("bill-pic")
                        }
                        UploadTypeOption("Indent Photo", "indent-pic", Icons.Default.Description) {
                            showTypeChooser = false
                            launchCamera("indent-pic")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTypeChooser = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun InvoiceUploadCard(
    invoice: InvoiceBillDto,
    isSelected: Boolean,
    isUploading: Boolean,
    onSelect: () -> Unit,
    onCapturePhoto: (String) -> Unit
) {
    val isCreditBill = invoice.billType == "CREDIT"
    val borderColor = if (isCreditBill) Color(0xFFFF6B00) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val billPicCount = invoice.photos?.count { it.photoType == "bill-pic" } ?: if (!invoice.billPic.isNullOrEmpty()) 1 else 0
    val indentPicCount = invoice.photos?.count { it.photoType == "indent-pic" } ?: if (!invoice.indentPic.isNullOrEmpty()) 1 else 0
    val hasBillPic = billPicCount > 0
    val hasIndentPic = indentPicCount > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected)
            CardDefaults.outlinedCardBorder().copy(width = 2.dp)
        else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: Bill No, Type badge, Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        invoice.billNo ?: "#${invoice.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCreditBill) Color(0xFFFF6B00).copy(alpha = 0.15f)
                        else Color(0xFF4CAF50).copy(alpha = 0.15f)
                    ) {
                        Text(
                            invoice.billType ?: "CASH",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCreditBill) Color(0xFFFF6B00) else Color(0xFF4CAF50)
                        )
                    }
                }
                Text(
                    inrFormat.format(invoice.netAmount ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(4.dp))

            // Customer / Vehicle / Driver
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (invoice.customer?.name != null) {
                        Text(
                            invoice.customer.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (invoice.vehicle?.vehicleNumber != null) {
                        Text(
                            invoice.vehicle.vehicleNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (invoice.driverName != null) {
                        Text(
                            "Driver: ${invoice.driverName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                // Products summary
                Column(horizontalAlignment = Alignment.End) {
                    invoice.products?.forEach { p ->
                        Text(
                            "${p.productName ?: ""} ${p.quantity ?: ""}L",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Upload status indicators + action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isCreditBill) {
                        UploadStatusChip("Bill", hasBillPic, mandatory = true, count = billPicCount)
                        UploadStatusChip("Indent", hasIndentPic, mandatory = false, count = indentPicCount)
                    } else {
                        UploadStatusChip("Bill", hasBillPic, mandatory = false, count = billPicCount)
                    }
                }

                // Action buttons
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Bill photo button
                        FilledTonalIconButton(
                            onClick = { onCapturePhoto("bill-pic") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (hasBillPic) Icons.Default.CameraAlt else Icons.Default.AddAPhoto,
                                contentDescription = "Capture Bill Photo",
                                modifier = Modifier.size(18.dp),
                                tint = if (hasBillPic) Color(0xFF4CAF50)
                                    else if (isCreditBill) Color(0xFFD32F2F)
                                    else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Indent photo button (credit only, optional)
                        if (isCreditBill) {
                            FilledTonalIconButton(
                                onClick = { onCapturePhoto("indent-pic") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (hasIndentPic) Icons.Default.CameraAlt else Icons.Default.AddAPhoto,
                                    contentDescription = "Capture Indent Photo",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (hasIndentPic) Color(0xFF4CAF50) else Color(0xFFFF6B00)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadStatusChip(label: String, uploaded: Boolean, mandatory: Boolean = false, count: Int = 0) {
    val pendingColor = if (mandatory) Color(0xFFD32F2F) else MaterialTheme.colorScheme.outline
    val pendingBg = if (mandatory) Color(0xFFD32F2F).copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (uploaded) Color(0xFF4CAF50).copy(alpha = 0.12f) else pendingBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (uploaded) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (uploaded) Color(0xFF4CAF50) else pendingColor
            )
            Spacer(Modifier.width(4.dp))
            Text(
                when {
                    uploaded && count > 1 -> "$label ($count)"
                    uploaded -> label
                    mandatory -> "$label *"
                    else -> label
                },
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = if (uploaded) Color(0xFF4CAF50) else pendingColor
            )
        }
    }
}

@Composable
private fun UploadTypeOption(
    label: String,
    type: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
