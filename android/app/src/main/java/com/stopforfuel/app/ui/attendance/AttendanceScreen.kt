package com.stopforfuel.app.ui.attendance

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")

private val statusOptions = listOf("PRESENT", "ABSENT", "HALF_DAY", "ON_LEAVE")
private fun statusColor(status: String): Color = when (status) {
    "PRESENT" -> Color(0xFF4CAF50)
    "ABSENT" -> Color(0xFFEF5350)
    "HALF_DAY" -> Color(0xFFFF9800)
    "ON_LEAVE" -> Color(0xFF42A5F5)
    else -> Color.Gray
}

private fun statusLabel(status: String): String = when (status) {
    "PRESENT" -> "P"
    "ABSENT" -> "A"
    "HALF_DAY" -> "HD"
    "ON_LEAVE" -> "L"
    else -> "?"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate.toEpochDay() * 86400000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / 86400000L)
                        viewModel.selectDate(date)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.employees.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.saveAll() },
                    icon = {
                        if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Save, contentDescription = null)
                    },
                    text = { Text("Save All") }
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date picker card
            item {
                Card(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                uiState.selectedDate.format(dateFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick date", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Messages
            if (uiState.successMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(uiState.successMessage!!, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (uiState.error != null) {
                item {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }

            // Summary
            item {
                val present = uiState.employees.count { it.status == "PRESENT" }
                val absent = uiState.employees.count { it.status == "ABSENT" }
                val halfDay = uiState.employees.count { it.status == "HALF_DAY" }
                val leave = uiState.employees.count { it.status == "ON_LEAVE" }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryChip("P: $present", Color(0xFF4CAF50), Modifier.weight(1f))
                    SummaryChip("A: $absent", Color(0xFFEF5350), Modifier.weight(1f))
                    SummaryChip("HD: $halfDay", Color(0xFFFF9800), Modifier.weight(1f))
                    SummaryChip("L: $leave", Color(0xFF42A5F5), Modifier.weight(1f))
                }
            }

            // Employee list
            items(uiState.employees, key = { it.employee.id }) { ea ->
                EmployeeAttendanceCard(
                    ea = ea,
                    onStatusChange = { status -> viewModel.updateStatus(ea.employee.id, status) }
                )
            }

            // Bottom spacing for FAB
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun SummaryChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun EmployeeAttendanceCard(
    ea: EmployeeAttendance,
    onStatusChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ea.employee.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    ea.employee.designation ?: ea.employee.role ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                statusOptions.forEach { status ->
                    val color = statusColor(status)
                    val isSelected = ea.status == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { onStatusChange(status) },
                        label = { Text(statusLabel(status), style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) color else MaterialTheme.colorScheme.outline,
                            selectedBorderColor = color,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
        }
    }
}
