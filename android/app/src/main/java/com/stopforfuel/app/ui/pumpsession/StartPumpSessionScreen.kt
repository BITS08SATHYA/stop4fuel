package com.stopforfuel.app.ui.pumpsession

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stopforfuel.app.data.remote.dto.NozzleDto
import com.stopforfuel.app.data.remote.dto.PumpDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartPumpSessionScreen(
    onBack: () -> Unit,
    onSessionStarted: () -> Unit,
    viewModel: PumpSessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.sessionStarted) {
        if (uiState.sessionStarted) onSessionStarted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start Pump Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pump selection
            item {
                Text(
                    "Select Your Pump",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.pumps) { pump ->
                        FilterChip(
                            selected = uiState.selectedPump?.id == pump.id,
                            onClick = { viewModel.selectPump(pump) },
                            label = { Text(pump.name ?: "Pump ${pump.id}") }
                        )
                    }
                }
            }

            // Nozzle readings
            if (uiState.pumpNozzles.isNotEmpty()) {
                item {
                    Text(
                        "Opening Meter Readings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.pumpNozzles) { nozzle ->
                    val reading = uiState.openReadings[nozzle.id] ?: ""
                    val productName = nozzle.tank?.productName ?: ""

                    OutlinedTextField(
                        value = reading,
                        onValueChange = { viewModel.updateOpenReading(nozzle.id, it) },
                        label = { Text("${nozzle.nozzleName} ($productName)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Error
            if (uiState.error != null) {
                item {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Start button
            item {
                Button(
                    onClick = { viewModel.startSession() },
                    enabled = !uiState.isLoading && uiState.selectedPump != null && uiState.pumpNozzles.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("START SESSION")
                    }
                }
            }
        }
    }
}
