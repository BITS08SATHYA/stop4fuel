package com.stopforfuel.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stopforfuel.app.data.remote.dto.AwsBillingDto
import com.stopforfuel.app.data.remote.dto.BackendHealthDto
import com.stopforfuel.app.data.remote.dto.ProductBreakdownDto
import com.stopforfuel.app.data.remote.dto.ProductDto
import com.stopforfuel.app.data.remote.dto.ProductSaleDto
import com.stopforfuel.app.data.remote.dto.TankStatusDto
import java.text.NumberFormat
import java.util.Locale

private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

// Vibrant gradient color pairs
private val GradientTeal = Brush.linearGradient(listOf(Color(0xFF00BFA5), Color(0xFF00897B)))
private val GradientBlue = Brush.linearGradient(listOf(Color(0xFF42A5F5), Color(0xFF1E88E5)))
private val GradientOrange = Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFF57C00)))
private val GradientRed = Brush.linearGradient(listOf(Color(0xFFEF5350), Color(0xFFD32F2F)))
private val GradientPurple = Brush.linearGradient(listOf(Color(0xFFAB47BC), Color(0xFF7B1FA2)))
private val GradientGreen = Brush.linearGradient(listOf(Color(0xFF66BB6A), Color(0xFF388E3C)))
private val GradientIndigo = Brush.linearGradient(listOf(Color(0xFF5C6BC0), Color(0xFF3949AB)))
private val GradientAmber = Brush.linearGradient(listOf(Color(0xFFFFCA28), Color(0xFFFFA000)))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerTabletDashboard(
    uiState: HomeUiState,
    onNavigateToInvoice: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        Column(Modifier.fillMaxSize()) {
            if (isTablet) {
                // ─── TABLET: Two-panel layout ───
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.weight(0.55f).verticalScroll(rememberScrollState()).padding(16.dp)
                    ) {
                        StatsSection(uiState)
                        Spacer(Modifier.height(16.dp))
                        FuelPricesSection(uiState)
                    }
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Column(
                        Modifier.weight(0.45f).verticalScroll(rememberScrollState()).padding(16.dp)
                    ) {
                        ProductSalesSection(uiState)
                        Spacer(Modifier.height(16.dp))
                        SystemHealthSection(uiState)
                    }
                }
            } else {
                // ─── PHONE: Single-column scrollable ───
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    StatsSection(uiState)
                    Spacer(Modifier.height(16.dp))
                    FuelPricesSection(uiState)
                    Spacer(Modifier.height(16.dp))
                    ProductSalesSection(uiState)
                    Spacer(Modifier.height(16.dp))
                    SystemHealthSection(uiState)
                }
            }

            // ─── BOTTOM NEW INVOICE BUTTON ───
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Button(
                    onClick = onNavigateToInvoice,
                    enabled = uiState.activeShift != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(56.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("New Invoice", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }
    }
}

// ── Stats grid (shared between phone and tablet) ──
@Composable
private fun StatsSection(uiState: HomeUiState) {
    Text(
        "TODAY'S NUMBERS",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp
    )
    Spacer(Modifier.height(12.dp))

    val stats = uiState.dashboardStats
    val health = uiState.systemHealth

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientStatCard(Icons.Default.CurrencyRupee, "Revenue", inrFormat.format(stats?.todayRevenue ?: 0), gradient = GradientTeal, modifier = Modifier.weight(1f))
        GradientStatCard(Icons.Default.AccountBalanceWallet, "Outstanding", inrFormat.format(stats?.totalOutstanding ?: 0), gradient = GradientRed, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientStatCard(Icons.Default.Receipt, "Invoices", "${stats?.todayInvoiceCount ?: 0}", subtitle = "${stats?.todayCashInvoices ?: 0} cash / ${stats?.todayCreditInvoices ?: 0} credit", gradient = GradientPurple, modifier = Modifier.weight(1f))
        GradientStatCard(Icons.Default.People, "Customers", "${health?.activeCustomers ?: 0} active", subtitle = "${health?.blockedCustomers ?: 0} blocked", gradient = GradientGreen, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientStatCard(Icons.Default.Badge, "Employees", "${health?.activeEmployees ?: 0} active", subtitle = "${health?.todayAttendanceCount ?: 0} present", gradient = GradientIndigo, modifier = Modifier.weight(1f))
        GradientStatCard(Icons.Default.Description, "Statements", "${stats?.totalStatements ?: 0}", subtitle = "${stats?.unpaidStatements ?: 0} unpaid / ${stats?.paidStatements ?: 0} paid", gradient = GradientOrange, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientStatCard(Icons.Default.CreditCard, "MTD Credit", "${stats?.mtdCreditCount ?: 0} bills", subtitle = inrFormat.format(stats?.mtdCreditAmount ?: 0), gradient = GradientBlue, modifier = Modifier.weight(1f))
        GradientStatCard(Icons.Default.Payments, "MTD Payments", "${stats?.mtdPaymentCount ?: 0} txns", subtitle = inrFormat.format(stats?.mtdPaymentAmount ?: 0), gradient = GradientAmber, modifier = Modifier.weight(1f))
    }
}

// ── Fuel prices section ──
@Composable
private fun FuelPricesSection(uiState: HomeUiState) {
    if (uiState.fuelProducts.isNotEmpty()) {
        SectionHeader(Icons.Default.LocalOffer, "FUEL PRICES")
        Spacer(Modifier.height(8.dp))
        uiState.fuelProducts.forEach { product -> FuelPriceRow(product) }
    }
}

// ── System health section ──
@Composable
private fun SystemHealthSection(uiState: HomeUiState) {
    SectionHeader(Icons.Default.Monitor, "SYSTEM HEALTH")
    Spacer(Modifier.height(8.dp))
    HealthAndBillingSection(uiState.backendHealth, uiState.awsBilling)
}

// ── Section header with icon ──
@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp
        )
    }
}

// ── Gradient stat card with icon ──
@Composable
private fun GradientStatCard(
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
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ── Product sales sections ──
@Composable
private fun ProductSalesSection(uiState: HomeUiState) {
    val lastShiftSales = uiState.dashboardStats?.lastShiftProductSales
    if (!lastShiftSales.isNullOrEmpty()) {
        val shiftLabel = uiState.dashboardStats?.lastShiftId?.let { "LAST SHIFT (#$it) SALES" } ?: "YESTERDAY SHIFT SALES"
        SectionHeader(Icons.Default.BarChart, shiftLabel)
        Spacer(Modifier.height(8.dp))
        ProductSalesTable(
            items = lastShiftSales.map { Triple(it.productName ?: "", it.quantity ?: 0.0, it.amount ?: 0.0) }
        )
        Spacer(Modifier.height(16.dp))
    }

    // Tank stock + MTD combined table
    if (!uiState.dashboardStats?.tankStatuses.isNullOrEmpty()) {
        SectionHeader(Icons.Default.PropaneTank, "STOCK & MTD SUMMARY")
        Spacer(Modifier.height(8.dp))
        StockMtdTable(uiState)
    }
}

@Composable
private fun ProductSalesTable(items: List<Triple<String, Double, Double>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Product", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Qty (L)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text("Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            items.forEach { (name, qty, amount) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(String.format("%.1f", qty), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(inrFormat.format(amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth()) {
                Text("Total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(String.format("%.1f", items.sumOf { it.second }), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text(inrFormat.format(items.sumOf { it.third }), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StockMtdTable(uiState: HomeUiState) {
    val tanks = uiState.dashboardStats?.tankStatuses?.filter { it.active == true } ?: return
    val mtdSales = uiState.dashboardStats?.mtdSales ?: emptyList()
    val mtdPurchases = uiState.dashboardStats?.mtdPurchases ?: emptyList()

    val stockByProduct = tanks.groupBy { it.productName ?: "Unknown" }
        .mapValues { (_, t) -> t.sumOf { it.currentStock ?: 0.0 } }
    val mtdSalesByProduct = mtdSales.associateBy { it.productName ?: "" }
    val mtdPurchaseByProduct = mtdPurchases.associateBy { it.productName ?: "" }
    val fuelProducts = stockByProduct.keys.toList()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Product", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
                Text("Stock (L)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text("MTD Sales (L)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text("MTD Purchase (L)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            fuelProducts.forEach { product ->
                val stock = stockByProduct[product] ?: 0.0
                val salesQty = mtdSalesByProduct[product]?.quantity ?: 0.0
                val purchaseQty = mtdPurchaseByProduct[product]?.quantity ?: 0.0
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(product, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
                    Text(String.format("%.0f", stock), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(String.format("%.1f", salesQty), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(String.format("%.1f", purchaseQty), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Tank stock bar ──
@Composable
private fun TankStockBar(tank: TankStatusDto) {
    val capacity = tank.capacity ?: 0.0
    val stock = tank.currentStock ?: 0.0
    val threshold = tank.thresholdStock ?: 0.0
    val fillRatio = if (capacity > 0) (stock / capacity).toFloat().coerceIn(0f, 1f) else 0f
    val isBelowThreshold = threshold > 0 && stock <= threshold
    val pct = (fillRatio * 100).toInt()

    val barColor = when {
        isBelowThreshold -> Color(0xFFEF5350)
        fillRatio > 0.6f -> Color(0xFF00BFA5)
        fillRatio > 0.3f -> Color(0xFFFF9800)
        else -> Color(0xFFEF5350)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(Modifier.width(90.dp)) {
                Text(
                    tank.tankName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    tank.productName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { fillRatio },
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${stock.toLong()}L",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
                Text(
                    "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isBelowThreshold) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Low stock",
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Fuel price row (clean card style) ──
@Composable
private fun FuelPriceRow(product: ProductDto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = Color(0xFF00BFA5),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    product.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "${inrFormat.format(product.price ?: 0)}/L",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00BFA5)
            )
        }
    }
}

// ── Health + AWS Billing section ──
@Composable
private fun HealthAndBillingSection(health: BackendHealthDto?, billing: AwsBillingDto?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Health indicators card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Services",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                HealthIndicatorRow(
                    label = "Backend",
                    status = health?.status,
                    detail = if (health?.latencyMs != null) "${health.latencyMs}ms" else null
                )
                Spacer(Modifier.height(6.dp))
                HealthIndicatorRow(
                    label = "Database",
                    status = health?.database,
                    detail = null
                )
                Spacer(Modifier.height(6.dp))
                HealthIndicatorRow(
                    label = "Frontend",
                    status = "UP",  // if we're running, frontend is up
                    detail = null
                )
            }
        }

        // AWS Billing donut card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                Modifier.padding(14.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "AWS MTD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                if (billing?.available == true && billing.monthToDateCost != null) {
                    AwsDonutChart(
                        cost = billing.monthToDateCost.toFloat(),
                        currency = billing.currency ?: "USD"
                    )
                } else {
                    // Show placeholder when billing not available
                    AwsDonutChart(cost = 0f, currency = "USD", unavailable = true)
                }
            }
        }
    }
}

// ── Health indicator row ──
@Composable
private fun HealthIndicatorRow(label: String, status: String?, detail: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Colored dot
        val dotColor = when (status) {
            "UP" -> Color(0xFF4CAF50)
            "DOWN" -> Color(0xFFF44336)
            "DEGRADED" -> Color(0xFFFF9800)
            else -> Color.Gray
        }
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = dotColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            status ?: "?",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = dotColor
        )
        if (detail != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── AWS Donut Chart ──
@Composable
private fun AwsDonutChart(
    cost: Float,
    currency: String,
    unavailable: Boolean = false,
    budgetLimit: Float = 100f  // default budget cap for visual
) {
    val fraction = if (budgetLimit > 0) (cost / budgetLimit).coerceIn(0f, 1f) else 0f

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
        Canvas(modifier = Modifier.size(90.dp)) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background track
            drawArc(
                color = Color(0xFFE2E8F0),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = topLeft,
                size = arcSize
            )

            if (!unavailable) {
                // Colored arc
                val sweepColor = when {
                    fraction > 0.8f -> Color(0xFFEF5350)
                    fraction > 0.5f -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                }
                drawArc(
                    color = sweepColor,
                    startAngle = -90f,
                    sweepAngle = fraction * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = topLeft,
                    size = arcSize
                )
            }
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (unavailable) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "N/A",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            } else {
                Icon(
                    Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "$${String.format("%.0f", cost)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}
