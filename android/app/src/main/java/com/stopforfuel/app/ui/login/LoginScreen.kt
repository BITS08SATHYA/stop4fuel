package com.stopforfuel.app.ui.login

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App title
            Text(
                text = "StopForFuel",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    !uiState.mfaRequired -> "Enter your phone number & PIN"
                    uiState.mfaEnrolled -> "Enter the code from your authenticator app"
                    else -> "Set up your authenticator app to continue"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!uiState.mfaRequired) {
                // ---- Step 1: phone + passcode ----
                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = { viewModel.updatePhone(it) },
                    label = { Text("Phone Number") },
                    prefix = { Text("+91 ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    repeat(4) { index ->
                        Dot(filled = index < uiState.passcode.length)
                    }
                }
            } else {
                // ---- Step 2: TOTP second factor ----
                if (!uiState.mfaEnrolled) {
                    EnrollmentGuide()
                    Spacer(modifier = Modifier.height(16.dp))
                    uiState.qrDataUri?.let { QrImage(it) }
                    uiState.manualKey?.let { key ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Can't scan? Key: $key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    repeat(6) { index ->
                        Dot(filled = index < uiState.totpCode.length)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error message
            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Loading indicator
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Number pad — routes to passcode or TOTP depending on the step
            NumberPad(
                onDigit = {
                    if (uiState.mfaRequired) viewModel.appendTotp(it) else viewModel.appendPin(it)
                },
                onDelete = {
                    if (uiState.mfaRequired) viewModel.deleteLastTotpDigit() else viewModel.deleteLastDigit()
                },
                onClear = {
                    if (uiState.mfaRequired) viewModel.clearTotp() else viewModel.clearPin()
                },
                enabled = !uiState.isLoading
            )

            if (uiState.mfaRequired) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.backToCredentials() },
                    enabled = !uiState.isLoading
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun EnrollmentGuide() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Set up your authenticator app",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            listOf(
                "1. Install Google Authenticator, Microsoft Authenticator, or Authy (free).",
                "2. Open it and tap + → Scan a QR code.",
                "3. Scan the code below, then enter the 6-digit code it shows."
            ).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You only scan once. After this, just type the current code each time you sign in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QrImage(dataUri: String) {
    val bitmap = remember(dataUri) {
        runCatching {
            val base64 = dataUri.substringAfter("base64,")
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Authenticator QR code",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp)
        )
    }
}

@Composable
private fun Dot(filled: Boolean) {
    Surface(
        modifier = Modifier.size(16.dp),
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    ) {}
}

@Composable
private fun NumberPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { label ->
                    when (label) {
                        "C" -> FilledTonalButton(
                            onClick = onClear,
                            enabled = enabled,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Text("C", fontSize = 20.sp)
                        }
                        "DEL" -> FilledTonalButton(
                            onClick = onDelete,
                            enabled = enabled,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete"
                            )
                        }
                        else -> Button(
                            onClick = { onDigit(label) },
                            enabled = enabled,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Text(label, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
