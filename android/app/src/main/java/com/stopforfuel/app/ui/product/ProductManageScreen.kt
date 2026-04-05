package com.stopforfuel.app.ui.product

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.util.Locale

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManageScreen(
    onBack: () -> Unit,
    viewModel: ProductManageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    com.stopforfuel.app.ui.AutoRefreshOnResume { viewModel.refresh() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Products & Prices") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }
        } else {
            val grouped = state.products.groupBy { it.category?.uppercase() ?: "OTHER" }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                grouped.forEach { (category, products) ->
                    item {
                        Text(category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(products) { product ->
                        ProductCard(
                            product = product,
                            isExpanded = state.expandedProductId == product.id,
                            onToggleExpand = { viewModel.toggleExpand(product.id) },
                            onUpdatePrice = { price -> viewModel.updatePrice(product.id, price) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: com.stopforfuel.app.data.remote.dto.ProductDto,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdatePrice: (String) -> Unit
) {
    var priceText by remember(product.price) { mutableStateOf(product.price?.toPlainString() ?: "") }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(product.name ?: "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${product.unit ?: ""} | ${product.fuelFamily ?: product.category ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(inrFormat.format(product.price ?: 0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Expand", Modifier.padding(start = 4.dp))
            }
            AnimatedVisibility(visible = isExpanded) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = priceText, onValueChange = { priceText = it },
                        label = { Text("New Price (₹)") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { onUpdatePrice(priceText) }) { Text("Update") }
                }
            }
        }
    }
}
