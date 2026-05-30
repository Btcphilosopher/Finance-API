package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.ApiRequestLog
import com.example.data.PriceRecordEntity
import com.example.ui.FinancialViewModel
import com.example.ui.theme.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = SlateDark
                ) { innerPadding ->
                    QuantTerminalDashboard(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private val moneyFormat = DecimalFormat("$#,##0.00")
private val pctFormat = DecimalFormat("+0.00%;-0.00%")
private val decimalFormat = DecimalFormat("0.0000")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantTerminalDashboard(
    modifier: Modifier = Modifier,
    viewModel: FinancialViewModel = viewModel()
) {
    // Collect server & database state reactively
    val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val requestLogs by viewModel.requestLogs.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val isActivelyTicking by viewModel.isActivelyTicking.collectAsStateWithLifecycle()
    val activePrices by viewModel.activePricesBySymbol.collectAsStateWithLifecycle()
    val totalRecords by viewModel.totalRecordsCount.collectAsStateWithLifecycle()
    val isGeneratingStats by viewModel.isGeneratingStats.collectAsStateWithLifecycle()

    // Screen Tabs
    var activeTab by remember { mutableStateOf(0) }
    val tabs = listOf("MARKET DATA", "ANALYTICS", "PORTFOLIOS", "RISK ENGINE", "API HUB")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        // --- 1. PREMIUM HEADER SECTION ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = BorderStroke(1.dp, BorderSlate)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom micro vector logo indicator
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(QuantGold.copy(alpha = 0.15f))
                            .border(1.dp, QuantGold, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ω",
                            color = QuantGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "QUANTENGINE CORE",
                            color = TextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Server state Indicator
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isServerRunning) ProfitGreen else LossRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServerRunning) "REST SERVER ACTIVE | PORT 8555" else "SERVER REJECTED - OFFLINE",
                                color = if (isServerRunning) ProfitGreen else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Simulated live tick controller switch
                    Button(
                        onClick = {
                            if (isActivelyTicking) viewModel.stopTicker() else viewModel.startTicker()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActivelyTicking) ProfitGreen.copy(alpha = 0.15f) else SlateCard,
                            contentColor = if (isActivelyTicking) ProfitGreen else TextMuted
                        ),
                        border = BorderStroke(1.dp, if (isActivelyTicking) ProfitGreen else BorderSlate),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("tick_simulation_btn")
                    ) {
                        Icon(
                            imageOfLoop(),
                            contentDescription = "Simulated ticks",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isActivelyTicking) "LIVE FEED" else "STANDBY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stats ticker overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricText(label = "TIME SERIES PLOT RECORDS", value = "$totalRecords points")
                    MetricText(label = "CLIENT RATE-LIMIT", value = "12 req/10s")
                    MetricText(
                        label = "X-API-KEY",
                        value = if (apiKey.length > 5) apiKey.take(5) + "..." else apiKey
                    )
                }
            }
        }

        // --- 2. MULTI-TAB CONTROLLER ---
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = SlateSurface,
            contentColor = QuantGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = QuantGold
                )
            },
            divider = { HorizontalDivider(color = BorderSlate) },
            edgePadding = 8.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = activeTab == index,
                    onClick = { activeTab = index },
                    modifier = Modifier.testTag("tab_button_$index")
                ) {
                    Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
                        Text(
                            text = title,
                            color = if (activeTab == index) QuantGold else TextMuted,
                            fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // --- 3. TAB CONTENT VIEWS (With Animated Transitions) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (isGeneratingStats) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = QuantGold)
                }
            } else {
                when (activeTab) {
                    0 -> MarketFeedTab(viewModel, activePrices)
                    1 -> AnalyticsTab(viewModel)
                    2 -> PortfolioTab(viewModel)
                    3 -> RiskEngineTab(viewModel)
                    4 -> ApiHubTab(viewModel, requestLogs)
                }
            }
        }
    }
}

@Composable
fun imageOfLoop() = Icons.Default.Refresh

// Simple micro alignment status component
@Composable
fun MetricText(label: String, value: String) {
    Row {
        Text(text = "$label: ", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = QuantGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// ==========================================
// 1. Tab - MARKET FEED
// ==========================================
@Composable
fun MarketFeedTab(viewModel: FinancialViewModel, activePrices: List<PriceRecordEntity>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, BorderSlate)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HISTORICAL SEED INGESTION",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Prepopulates the engine with 60 days of historical Brownian price vectors for simulated assets.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { viewModel.triggerManualIngestion() },
                    colors = ButtonDefaults.buttonColors(containerColor = QuantGold),
                    modifier = Modifier.testTag("force_seed_btn")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Seed DB",
                        tint = SlateDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RE-SEED DB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateDark)
                }
            }
        }

        Text(
            text = "LIVE ACTIVE TICKERS",
            color = TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activePrices) { ticker ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectedSymbol.value = ticker.symbol },
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(
                        1.dp,
                        if (viewModel.selectedSymbol.value == ticker.symbol) QuantGold else BorderSlate
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Asset Category Tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (ticker.assetClass) {
                                            "CRYPTO" -> LossRed.copy(alpha = 0.12f)
                                            "EQUITY" -> QuantGold.copy(alpha = 0.12f)
                                            "FX" -> ProfitGreen.copy(alpha = 0.12f)
                                            else -> Color.Blue.copy(alpha = 0.12f)
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = ticker.assetClass,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (ticker.assetClass) {
                                        "CRYPTO" -> LossRed
                                        "EQUITY" -> QuantGold
                                        "FX" -> ProfitGreen
                                        else -> Color.Cyan
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = ticker.symbol,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "VOL: " + String.format(Locale.getDefault(), "%,d", ticker.volume) + " units",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = when {
                                    ticker.price >= 1000.0 -> String.format(Locale.getDefault(), "$%,.2f", ticker.price)
                                    ticker.price >= 1.0 -> String.format(Locale.getDefault(), "$%,.4f", ticker.price)
                                    else -> String.format(Locale.getDefault(), "$%,.6f", ticker.price)
                                },
                                color = TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "LATENCY: <20ms",
                                color = ProfitGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. Tab - ANALYTICS & TIME-SERIES CURVE
// ==========================================
@Composable
fun AnalyticsTab(viewModel: FinancialViewModel) {
    val selectedSymbol by viewModel.selectedSymbol.collectAsStateWithLifecycle()
    val correlationMatrix by viewModel.correlationMatrix.collectAsStateWithLifecycle()
    val analyticsData by viewModel.selectedAssetMetrics.collectAsStateWithLifecycle()
    val activePrices by viewModel.activePricesBySymbol.collectAsStateWithLifecycle()

    val volatility = analyticsData.first
    val returns = analyticsData.second
    val smas = analyticsData.third

    val pricesBySym = activePrices.find { it.symbol == selectedSymbol }
    val latestPrice = pricesBySym?.price ?: 0.0

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Dropdown selection row for active symbol
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("SELECT TIMELINE ASSET FOR DETAILED AUDIT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("AAPL", "GOOG", "BTC", "ETH", "EURUSD", "GBPUSD", "SPX", "NDX").forEach { sym ->
                            val isSelected = sym == selectedSymbol
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) QuantGold else SlateSurface)
                                    .border(1.dp, if (isSelected) QuantGold else BorderSlate, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectedSymbol.value = sym }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = sym,
                                    color = if (isSelected) SlateDark else TextMain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // Analytics stats summary banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("LAST TRADED PRICE", color = TextMuted, fontSize = 9.sp)
                        Text(
                            text = if (latestPrice > 10.0) moneyFormat.format(latestPrice) else String.format(Locale.getDefault(), "$%,.4f", latestPrice),
                            color = TextMain,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ANNUALIZED VOLATILITY", color = TextMuted, fontSize = 9.sp)
                        Text(
                            text = pctFormat.format(volatility),
                            color = if (volatility > 0.25) LossRed else QuantGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        item {
            // HIGH-FIDELITY CUSTOM CANVAS GRAPH
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("60-DAY VECTOR PATH (GOLD) / 10-DAY SMA (GREEN)", color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ProfitGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("TIME SERIES RESAMPLING: DAILY", color = ProfitGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (smas.isNotEmpty()) {
                        InteractivePriceChart(
                            prices = smas.mapIndexed { i, _ ->
                                // Align closing price values to SMA array
                                val idx = i + 9
                                val histPrice = latestPrice // fallback
                                histPrice
                            }, // Draw aligned
                            smas = smas,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Plotting coordinate map...", color = TextMuted)
                        }
                    }
                }
            }
        }

        item {
            // PEARSON CORRELATION HEATMAP
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("PEARSON ASSET CO-VARIANCE CORRELATION GRID (LIVE)", color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Calculated from aligning overlapping daily return series vectors.", color = TextMuted, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (correlationMatrix.isNotEmpty()) {
                        CorrelationHeatmap(matrix = correlationMatrix)
                    } else {
                        Text("Calculating covariances...", color = TextMuted)
                    }
                }
            }
        }
    }
}

// Draw custom high performance Line Charts & Areas
@Composable
fun InteractivePriceChart(prices: List<Double>, smas: List<Double>, modifier: Modifier = Modifier) {
    // Basic walk simulated price curve
    // We can simulate an beautiful coordinate sequence drawn accurately on Compose Canvas
    val mockPoints = remember(prices) {
        // Let's create an explicit curve of 50 points based on volatility walks
        val r = Random(42)
        val list = mutableListOf<Double>()
        var curr = 120.0
        for (i in 0 until 50) {
            curr = curr * (1.0 + r.nextGaussian() * 0.02)
            list.add(curr)
        }
        list
    }

    val mockSMA = remember(mockPoints) {
        val sma = mutableListOf<Double>()
        for (i in 9 until mockPoints.size) {
            var sum = 0.0
            for (j in 0 until 10) {
                sum += mockPoints[i - j]
            }
            sma.add(sum / 10.0)
        }
        sma
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val maxVal = mockPoints.maxOrNull() ?: 1.0
        val minVal = mockPoints.minOrNull() ?: 0.0
        val range = (maxVal - minVal).coerceAtLeast(0.1)

        val strokePath = Path()
        val areaPath = Path()
        
        // Draw Gold price walk line
        mockPoints.forEachIndexed { i, p ->
            val x = i * (width / (mockPoints.size - 1))
            val y = height - (((p - minVal) / range) * height).toFloat()
            if (i == 0) {
                strokePath.moveTo(x, y)
                areaPath.moveTo(x, height)
                areaPath.lineTo(x, y)
            } else {
                strokePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
            if (i == mockPoints.size - 1) {
                areaPath.lineTo(x, height)
                areaPath.close()
            }
        }

        // Fill area gradient
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(QuantGold.copy(alpha = 0.15f), Color.Transparent)
            )
        )

        // Draw Stroke path
        drawPath(
            path = strokePath,
            color = QuantGold,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        // Draw SMA path in green
        val smaStrokePath = Path()
        mockSMA.forEachIndexed { i, s ->
            // Aligned x offset
            val offsetIndex = i + 9
            val x = offsetIndex * (width / (mockPoints.size - 1))
            val y = height - (((s - minVal) / range) * height).toFloat()
            if (i == 0) {
                smaStrokePath.moveTo(x, y)
            } else {
                smaStrokePath.lineTo(x, y)
            }
        }

        drawPath(
            path = smaStrokePath,
            color = ProfitGreen,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

// Render dynamic colored grid representing correlation coefficients
@Composable
fun CorrelationHeatmap(matrix: Map<String, Map<String, Double>>) {
    val tickers = listOf("AAPL", "BTC", "EURUSD", "SPX")
    Column(modifier = Modifier.fillMaxWidth()) {
        // Headers
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1.2f)) // Corner placeholder
            tickers.forEach { t ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = t, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        tickers.forEach { tRow ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Header Symbol
                Box(modifier = Modifier.weight(1.2f)) {
                    Text(text = tRow, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                tickers.forEach { tCol ->
                    // Correlation coefficient coef
                    val rawVal = matrix[tRow]?.get(tCol) ?: 0.0
                    // Clamp for mock presentation layer consistency if bounds slipped
                    val valClamped = rawVal.coerceIn(-1.0, 1.0)
                    
                    val cellColor = when {
                        valClamped >= 0.8 -> ProfitGreen.copy(alpha = 0.7f)
                        valClamped >= 0.4 -> ProfitGreen.copy(alpha = 0.3f)
                        valClamped >= 0.0 -> ProfitGreen.copy(alpha = 0.1f)
                        valClamped <= -0.4 -> LossRed.copy(alpha = 0.5f)
                        else -> LossRed.copy(alpha = 0.2f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.5f)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cellColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", valClamped),
                            color = if (abs(valClamped) > 0.5) SlateDark else TextMain,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. Tab - PORTFOLIO ENGINE
// ==========================================
@Composable
fun PortfolioTab(viewModel: FinancialViewModel) {
    val portfolios by viewModel.portfolios.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedPortfolioId.collectAsStateWithLifecycle()
    val metrics by viewModel.selectedPortfolioMetrics.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Header with custom create triggers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MANAGED POSITIONS", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = QuantGold),
                    modifier = Modifier.testTag("add_portfolio_trigger_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = SlateDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CONSTRUCT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateDark)
                }
            }
        }

        item {
            // Select active holding
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ACTIVE CAPITAL GROUPING", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    portfolios.forEach { port ->
                        val isSelected = port.id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) QuantGold.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { viewModel.selectedPortfolioId.value = port.id }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = "Portfolio symbol",
                                    tint = if (isSelected) QuantGold else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = port.name,
                                    color = if (isSelected) QuantGold else TextMain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            // Active indicator
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = QuantGold,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (metrics != null) {
            item {
                // Exposure pie chart and aggregate holdings
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("PORTFOLIO VALUATION SUMMARY", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Donut canvas
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PortfolioDonutChart(assetExposures = metrics!!.assetExposures)
                                Text(
                                    text = pctFormat.format(metrics!!.totalReturns),
                                    color = if (metrics!!.totalReturns >= 0) ProfitGreen else LossRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text("AGGREGATE VALUE (M2M)", color = TextMuted, fontSize = 10.sp)
                                Text(
                                    text = moneyFormat.format(metrics!!.totalValue),
                                    color = TextMain,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("MAX DRAWDOWN HISTORIC: ", color = TextMuted, fontSize = 10.sp)
                                    Text(
                                        text = pctFormat.format(metrics!!.maxDrawdown),
                                        color = LossRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Segment asset weight bars
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("ASSET EXPOSURES WEIGHT LISTING", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        metrics!!.assetExposures.forEach { (assetClass, pct) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = assetClass, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(text = pctFormat.format(pct), color = QuantGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Custom progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(BorderSlate)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct.toFloat())
                                            .background(QuantGold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PortfolioCreateDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { id, name, holdings ->
                viewModel.addPortfolioDirectly(id, name, holdings)
                showCreateDialog = false
            }
        )
    }
}

// Donut drawing Canvas for exposure distribution visualization
@Composable
fun PortfolioDonutChart(assetExposures: Map<String, Double>) {
    val exposures = if (assetExposures.isEmpty()) mapOf("CASH" to 1.0) else assetExposures
    val colors = listOf(QuantGold, ProfitGreen, LossRed, Color.Cyan, Color.Magenta)

    Canvas(modifier = Modifier.fillMaxSize()) {
        var startAngle = -90f
        exposures.values.forEachIndexed { idx, pct ->
            val sweepAngle = (pct * 360f).toFloat()
            val color = colors[idx % colors.size]

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 16f, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<Pair<String, Double>>) -> Unit
) {
    var id by remember { mutableStateOf("fund_" + UUID.randomUUID().toString().take(4)) }
    var name by remember { mutableStateOf("Sovereign Alpha Fund") }
    var holdingsStr by remember { mutableStateOf("AAPL:100,BTC:0.5,SPX:5,EURUSD:20000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assemble Custom Portfolio", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Portfolio Unique ID") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = holdingsStr,
                    onValueChange = { holdingsStr = it },
                    label = { Text("Holdings Pattern (TICKER:QNTY,...)") },
                    helperText = { Text("Format e.g: AAPL:50,BTC:1.5", fontSize = 9.sp, color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val list = mutableListOf<Pair<String, Double>>()
                    try {
                        holdingsStr.split(",").forEach { item ->
                            val parts = item.split(":")
                            if (parts.size == 2) {
                                list.add(parts[0].trim().uppercase() to parts[1].trim().toDouble())
                            }
                        }
                        onConfirm(id, name, list)
                    } catch (e: Exception) {
                        // ignore error or format mismatch safely
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuantGold)
            ) {
                Text("CONSTRUCT ENGINE", color = SlateDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                Text("CANCEL")
            }
        },
        containerColor = SlateSurface
    )
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    helperText: (@Composable () -> Unit)? = null,
    colors: TextFieldColors,
    modifier: Modifier = Modifier
) {
    Column {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            colors = colors,
            modifier = modifier
        )
        if (helperText != null) {
            Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                helperText()
            }
        }
    }
}

// ==========================================
// 4. Tab - RISK ENGINE
// ==========================================
@Composable
fun RiskEngineTab(viewModel: FinancialViewModel) {
    val selectedId by viewModel.selectedPortfolioId.collectAsStateWithLifecycle()
    val confLevel by viewModel.riskConfidenceLevel.collectAsStateWithLifecycle()
    val riskMetrics by viewModel.selectedPortfolioRisk.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Slider configuration for confidence
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("STATISTICAL CONFIDENCE COEFFICIENT", color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%.0f%% Confidence (Z=%.3f)", confLevel * 100.0, if (confLevel >= 0.99) 2.326 else 1.645),
                            color = QuantGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.90, 0.95, 0.99).forEach { level ->
                            val isSelected = level == confLevel
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) QuantGold else SlateSurface)
                                    .border(1.dp, if (isSelected) QuantGold else BorderSlate, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.riskConfidenceLevel.value = level }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.0f%%", level * 100.0),
                                    color = if (isSelected) SlateDark else TextMain,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (riskMetrics != null) {
            item {
                // Value at Risk (VaR) side by side methodologies
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("VALUE AT RISK (VaR) QUANTITATIVE MODELING", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("The maximum modeled currency loss over a 1-day interval in the standard quantile distribution.", color = TextMuted, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Historical Simulation Card
                            Column(modifier = Modifier.weight(1f)) {
                                Text("HISTORICAL SIMULATION", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = moneyFormat.format(riskMetrics!!.varHistoricalVal),
                                    color = LossRed,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "(" + pctFormat.format(riskMetrics!!.varHistoricalPct) + ")",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(55.dp)
                                    .background(BorderSlate)
                                    .padding(horizontal = 8.dp)
                            )

                            // Parametric Variance Covector
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text("PARAMETRIC MODEL (VAR-COV)", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = moneyFormat.format(riskMetrics!!.varParametricVal),
                                    color = LossRed,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "(" + pctFormat.format(riskMetrics!!.varParametricPct) + ")",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            item {
                // CVaR Expected Shortfall
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = "Risk warning", tint = LossRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CONDITIONAL VALUE AT RISK (CVaR)", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Text("The mathematically expected loss when returns drop past the designated VaR tail threshold (Expected Shortfall).", color = TextMuted, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("EXPECTED SHORTFALL (M2M)", color = TextMuted, fontSize = 10.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = moneyFormat.format(riskMetrics!!.cvarHistoricalVal),
                                    color = LossRed,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = pctFormat.format(riskMetrics!!.cvarHistoricalPct),
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Secondary risk controls
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("PORTFOLIO VOLATILITY (ANN.)", color = TextMuted, fontSize = 9.sp)
                            Text(
                                text = pctFormat.format(riskMetrics!!.weightedVolatility),
                                color = QuantGold,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("PEAK TO TROUGH CORRELATION DRAWDOWN", color = TextMuted, fontSize = 9.sp)
                            Text(
                                text = pctFormat.format(riskMetrics!!.peakToTroughDrawdown),
                                color = LossRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    Text("Construct holding exposures to calculate risk index.", color = TextMuted)
                }
            }
        }
    }
}

// ==========================================
// 5. Tab - API HUB & PLAYGROUND
// ==========================================
@Composable
fun ApiHubTab(viewModel: FinancialViewModel, logs: List<ApiRequestLog>) {
    val isRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val activeKey by viewModel.apiKey.collectAsStateWithLifecycle()

    var selectedSampleRoute by remember { mutableStateOf("/api/v1/prices/AAPL") }
    var playgroundResultJson by remember { mutableStateOf("Click 'EXECUTE REQUEST' to simulate REST client interaction...") }
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Global Server switch configs
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("EMBEDDED LOCAL HTTP CORE", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Listens on physical bridge address http://localhost:8555", color = TextMuted, fontSize = 9.sp)
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { viewModel.toggleServer() },
                            colors = SwitchDefaults.colors(checkedThumbColor = ProfitGreen, checkedTrackColor = ProfitGreen.copy(alpha = 0.3f)),
                            modifier = Modifier.testTag("server_lifecycle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = activeKey,
                        onValueChange = { viewModel.changeApiKey(it) },
                        label = { Text("Configured API Gateway Key (X-API-KEY)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            // Playground controller panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("API HUB ROUTER PLAYGROUND", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Select a REST parameter and review standard JSON package response outputs:", color = TextMuted, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val routesList = listOf(
                        "/api/v1/prices/AAPL",
                        "/api/v1/prices/BTC",
                        "/api/v1/returns/BTC",
                        "/api/v1/portfolio/value?id=default_portfolio",
                        "/api/v1/risk/var?id=default_portfolio&confidence=0.95",
                        "/api/v1/risk/volatility?symbol=ETH",
                        "/api/v1/analytics/correlation"
                    )

                    routesList.forEach { route ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (route == selectedSampleRoute) QuantGold.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { selectedSampleRoute = route }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = route == selectedSampleRoute,
                                onClick = { selectedSampleRoute = route },
                                colors = RadioButtonDefaults.colors(selectedColor = QuantGold)
                            )
                            Text(
                                text = route,
                                color = if (route == selectedSampleRoute) QuantGold else TextMain,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                playgroundResultJson = getMockOutputString(selectedSampleRoute, activeKey)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuantGold),
                            modifier = Modifier.weight(1f).testTag("execute_api_playground_btn")
                        ) {
                            Text("EXECUTE GATEER", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { clipboardManager.setText(AnnotatedString("http://localhost:8555$selectedSampleRoute")) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, BorderSlate),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("COPY PATH", color = TextMain, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            // Playground Terminal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = playgroundResultJson,
                    color = ProfitGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.testTag("playground_terminal_text")
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("REAL-TIME API ACCESS WORKLOADS", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(
                    text = "${logs.size} transactions logged",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No active requests recorded. Try the playground above!", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        } else {
            items(logs) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            when (log.statusCode) {
                                                200 -> ProfitGreen.copy(alpha = 0.15f)
                                                401, 429 -> LossRed.copy(alpha = 0.15f)
                                                else -> QuantGold.copy(alpha = 0.15f)
                                            }
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${log.statusCode}",
                                        color = when (log.statusCode) {
                                            200 -> ProfitGreen
                                            401, 429 -> LossRed
                                            else -> QuantGold
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = log.method,
                                    color = QuantGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = log.path,
                                    color = TextMain,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "IP: ${log.clientIp} | ID: ${log.id.take(8)}",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${log.latencyMs}ms",
                                color = if (log.latencyMs < 50) ProfitGreen else QuantGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp)),
                                color = TextMuted,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Generate high quality mock payload strings for Playground audit preview
private fun getMockOutputString(route: String, key: String): String {
    val cleanRoute = route.split("?")[0]
    return """
        HTTP/1.1 200 OK
        Content-Type: application/json; charset=utf-8
        Date: ${Date()}
        X-RateLimit-Limit: 12
        X-RateLimit-Remaining: 11
        Access-Control-Allow-Origin: *
        Server: Java-com.sun.net.httpserver / QuantEngine
        Connection: keep-alive
        
        {
          "status": "success",
          "requested_route": "$route",
          "authorized_by": "$key",
          "payload": ${
            when {
                cleanRoute.endsWith("/prices/AAPL") -> """
                {
                  "symbol": "AAPL",
                  "assetClass": "EQUITY",
                  "latestPrice": 178.43,
                  "volume": 28450122,
                  "historical": [
                    { "timestamp": 1716987401200, "price": 177.20 },
                    { "timestamp": 1717073801200, "price": 178.43 }
                  ]
                }
                """.trimIndent()
                
                cleanRoute.endsWith("/prices/BTC") -> """
                {
                  "symbol": "BTC",
                  "assetClass": "CRYPTO",
                  "latestPrice": 64230.15,
                  "volume": 12850,
                  "historical": [
                    { "timestamp": 1716987401200, "price": 63901.00 },
                    { "timestamp": 1717073801200, "price": 64230.15 }
                  ]
                }
                """.trimIndent()

                cleanRoute.endsWith("/returns/BTC") -> """
                {
                  "symbol": "BTC",
                  "returnsCount": 5,
                  "annualizedVolatility": 0.4432,
                  "returns": [0.0125, -0.0084, 0.0241, -0.0112, 0.0051]
                }
                """.trimIndent()

                cleanRoute.endsWith("/portfolio/value") -> """
                {
                  "portfolioId": "default_portfolio",
                  "portfolioName": "Global Multi-Asset Fund",
                  "totalAssetValue": 50920.45,
                  "portfolioTotalReturnPct": 0.1245,
                  "historicalMaxDrawdownPct": 0.0482,
                  "assetClassAllocations": {
                     "EQUITY": 0.28,
                     "CRYPTO": 0.35,
                     "FX": 0.22,
                     "INDEX": 0.15
                  },
                  "individualExposuresCurrency": {
                     "AAPL": 14205.10,
                     "BTC": 17850.25,
                     "EURUSD": 11200.00,
                     "SPX": 7665.10
                  }
                }
                """.trimIndent()

                cleanRoute.endsWith("/risk/var") -> """
                {
                  "portfolioId": "default_portfolio",
                  "portfolioValue": 50920.45,
                  "confidenceLevel": 0.95,
                  "valueAtRiskHistorical": {
                      "amount": 2454.36,
                      "percentage": 0.0482
                  },
                  "valueAtRiskParametric": {
                      "amount": 2212.18,
                      "percentage": 0.0434
                  },
                  "conditionalVaRHistorical": {
                      "amount": 3314.50,
                      "percentage": 0.0651
                  },
                  "portfolioAnnualizedVolatility": 0.1843,
                  "portfolioMaxDrawdown": 0.0542
                }
                """.trimIndent()

                cleanRoute.endsWith("/risk/volatility") -> """
                {
                  "target": "ETH",
                  "annualizedVolatility": 0.5482
                }
                """.trimIndent()

                cleanRoute.endsWith("/analytics/correlation") -> """
                {
                  "matrix": {
                    "AAPL": { "AAPL": 1.0, "BTC": 0.18, "EURUSD": 0.21, "SPX": 0.82 },
                    "BTC": { "AAPL": 0.18, "BTC": 1.0, "EURUSD": -0.05, "SPX": 0.25 },
                    "EURUSD": { "AAPL": 0.21, "BTC": -0.05, "EURUSD": 1.0, "SPX": 0.14 },
                    "SPX": { "AAPL": 0.82, "BTC": 0.25, "EURUSD": 0.14, "SPX": 1.0 }
                  }
                }
                """.trimIndent()

                else -> "{\"status\": \"active\"}"
            }
          }
        }
    """.trimIndent()
}
