package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

data class PortfolioMetrics(
    val id: String,
    val name: String,
    val totalValue: Double,
    val assetExposures: Map<String, Double>, // assetClass -> Exposure percentage (0.0 - 1.0)
    val itemExposures: Map<String, Double>,  // symbol -> exposure amount
    val totalReturns: Double,
    val maxDrawdown: Double
)

data class RiskMetrics(
    val portfolioValue: Double,
    val varHistoricalVal: Double,   // 95% Historical Value at Risk in currency units
    val varHistoricalPct: Double,   // 95% Historical Value at Risk as a percentage
    val varParametricVal: Double,   // 95% Parametric Value at Risk in currency units
    val varParametricPct: Double,   // 95% Parametric Value at Risk as a percentage
    val cvarHistoricalVal: Double,  // Conditional VaR (Expected Shortfall) in currency
    val cvarHistoricalPct: Double,
    val weightedVolatility: Double, // Annualized Volatility of portfolio
    val peakToTroughDrawdown: Double // Peak-to-trough max drawdown of portfolio
)

data class IngestionStats(
    val totalRecords: Int,
    val uniqueSymbols: List<String>,
    val lastIngestionTime: Long,
    val isActivelyTicking: Boolean
)

class FinancialRepository(private val dao: FinancialDao) {

    val portfolios: Flow<List<PortfolioEntity>> = dao.getPortfoliosFlow()
    val allPricesFlow: Flow<List<PriceRecordEntity>> = dao.getAllPricesFlow()

    // Configuration of standard symbols in the system
    private val assetsConfig = mapOf(
        "AAPL" to Pair(175.20, "EQUITY"),
        "GOOG" to Pair(152.50, "EQUITY"),
        "BTC" to Pair(64500.00, "CRYPTO"),
        "ETH" to Pair(3450.00, "CRYPTO"),
        "EURUSD" to Pair(1.0820, "FX"),
        "GBPUSD" to Pair(1.2650, "FX"),
        "SPX" to Pair(5120.00, "INDEX"),
        "NDX" to Pair(18100.00, "INDEX")
    )

    suspend fun getLatestPrice(symbol: String): PriceRecordEntity? {
        return dao.getLatestPrice(symbol)
    }

    suspend fun getHistoricalPrices(symbol: String): List<PriceRecordEntity> {
        return dao.getHistoricalPrices(symbol)
    }

    suspend fun getPortfolios(): Flow<List<PortfolioEntity>> {
        return dao.getPortfoliosFlow()
    }

    suspend fun getPortfolioAssets(portfolioId: String): List<PortfolioAssetEntity> {
        return dao.getPortfolioAssets(portfolioId)
    }

    suspend fun createPortfolio(id: String, name: String, holdings: List<Pair<String, Double>>) {
        dao.insertPortfolio(PortfolioEntity(id, name, System.currentTimeMillis()))
        val assetEntities = holdings.map { (symbol, quantity) ->
            PortfolioAssetEntity(portfolioId = id, symbol = symbol, quantity = quantity)
        }
        dao.deletePortfolioAssets(id)
        dao.insertPortfolioAssets(assetEntities)
    }

    // --- MARKET DATA INGESTION ENGINE ---
    // Generates historical time-series data using Brownian motion random walks
    suspend fun seedDatabase() {
        val r = Random()
        val list = mutableListOf<PriceRecordEntity>()
        val currentMillis = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        // Generate 60 days of historical data for each asset
        for ((symbol, data) in assetsConfig) {
            val initialPrice = data.first
            val assetClass = data.second
            var currentPrice = initialPrice
            
            // Standard deviation daily factor (roughly)
            val volDaily = when (assetClass) {
                "CRYPTO" -> 0.035  // High vol
                "EQUITY" -> 0.015  // Medium vol
                "INDEX" -> 0.008   // Low vol
                else -> 0.005      // FX low vol
            }

            for (day in 60 downTo 0) {
                val timestamp = currentMillis - (day * oneDayMillis)
                // Brownian walk step: P_t = P_{t-1} * (1 + Z * sigma)
                val ret = r.nextGaussian() * volDaily
                currentPrice *= (1.0 + ret)
                // Simulated volume
                val baseVol = when (assetClass) {
                    "CRYPTO" -> 50000L
                    "EQUITY" -> 500000L
                    "INDEX" -> 2000000L
                    else -> 10000000L
                }
                val vol = baseVol + (r.nextDouble() * baseVol * 0.4).toLong()

                list.add(PriceRecordEntity(
                    symbol = symbol,
                    timestamp = timestamp,
                    price = currentPrice,
                    volume = vol,
                    assetClass = assetClass
                ))
            }
        }
        dao.clearPrices()
        dao.insertPrices(list)
        
        // Also check if portfolio is seeded
        seedDefaultPortfolio()
    }

    // Seeds a default portfolio with various assets to demonstrate multi-asset risk tracking
    private suspend fun seedDefaultPortfolio() {
        createPortfolio(
            id = "default_portfolio",
            name = "Global Multi-Asset Fund",
            holdings = listOf(
                Pair("AAPL", 50.0),    // Equity: ~ $8,700
                Pair("BTC", 0.25),     // Crypto: ~ $16,100
                Pair("EURUSD", 10000.0), // FX exposure: ~ $10,800
                Pair("SPX", 3.0)        // Index: ~ $15,300
            )
        )
    }

    // Live realt-time price update ticking
    suspend fun tickLivePrices() {
        val r = Random()
        val currentMillis = System.currentTimeMillis()
        val updatedTickers = mutableListOf<PriceRecordEntity>()

        for ((symbol, config) in assetsConfig) {
            val latest = dao.getLatestPrice(symbol) ?: continue
            val volTick = when (config.second) {
                "CRYPTO" -> 0.004
                "EQUITY" -> 0.001
                "INDEX" -> 0.0005
                else -> 0.0002
            }
            // Brownian walk tick
            val ret = r.nextGaussian() * volTick
            val newPrice = latest.price * (1.0 + ret)
            val newVol = latest.volume + r.nextInt(1000) - 500

            updatedTickers.add(PriceRecordEntity(
                symbol = symbol,
                timestamp = currentMillis,
                price = newPrice,
                volume = if (newVol < 100) 100 else newVol,
                assetClass = config.second
            ))
        }
        dao.insertPrices(updatedTickers)
    }

    // --- PRICING ENGINE ---
    suspend fun getReturns(symbol: String): List<Double> {
        val history = dao.getHistoricalPrices(symbol)
        if (history.size < 2) return emptyList()
        val returns = mutableListOf<Double>()
        for (i in 1 until history.size) {
            val prev = history[i - 1].price
            val curr = history[i].price
            if (prev != 0.0) {
                returns.add((curr - prev) / prev)
            }
        }
        return returns
    }

    suspend fun getRollingSMA(symbol: String, window: Int = 10): List<Double> {
        val history = dao.getHistoricalPrices(symbol)
        if (history.size < window) return emptyList()
        val smas = mutableListOf<Double>()
        for (i in (window - 1) until history.size) {
            var sum = 0.0
            for (j in 0 until window) {
                sum += history[i - j].price
            }
            smas.add(sum / window)
        }
        return smas
    }

    suspend fun calculateVolatility(symbol: String): Double {
        val returns = getReturns(symbol)
        if (returns.size < 2) return 0.0
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.sum() / (returns.size - 1)
        // Annualize the daily standard deviation (assuming 252 trading days/year)
        return sqrt(variance) * sqrt(252.0)
    }

    // --- PORTFOLIO ENGINE ---
    suspend fun getPortfolioMetrics(portfolioId: String): PortfolioMetrics? {
        val portfolio = dao.getPortfolioById(portfolioId) ?: return null
        val holdings = dao.getPortfolioAssets(portfolioId)
        if (holdings.isEmpty()) {
            return PortfolioMetrics(
                id = portfolioId,
                name = portfolio.name,
                totalValue = 0.0,
                assetExposures = emptyMap(),
                itemExposures = emptyMap(),
                totalReturns = 0.0,
                maxDrawdown = 0.0
            )
        }

        var totalValue = 0.0
        val itemExposures = mutableMapOf<String, Double>()
        val classExposures = mutableMapOf<String, Double>()
        var startValue = 0.0

        for (holding in holdings) {
            val latest = dao.getLatestPrice(holding.symbol)
            val history = dao.getHistoricalPrices(holding.symbol)
            
            val currentPrice = latest?.price ?: 0.0
            val startPrice = history.firstOrNull()?.price ?: currentPrice
            
            val assetValue = currentPrice * holding.quantity
            val initialAssetValue = startPrice * holding.quantity
            
            totalValue += assetValue
            startValue += initialAssetValue

            itemExposures[holding.symbol] = assetValue
            val assetClass = latest?.assetClass ?: "Unknown"
            classExposures[assetClass] = (classExposures[assetClass] ?: 0.0) + assetValue
        }

        // Percentage calculations
        val assetExposuresPct = classExposures.mapValues { if (totalValue == 0.0) 0.0 else it.value / totalValue }

        // Overall returns
        val totalReturns = if (startValue == 0.0) 0.0 else (totalValue - startValue) / startValue

        // Max drawdown of portfolio over time
        val maxDrawdown = calculatePortfolioMaxDrawdown(holdings)

        return PortfolioMetrics(
            id = portfolioId,
            name = portfolio.name,
            totalValue = totalValue,
            assetExposures = assetExposuresPct,
            itemExposures = itemExposures,
            totalReturns = totalReturns,
            maxDrawdown = maxDrawdown
        )
    }

    private suspend fun calculatePortfolioMaxDrawdown(holdings: List<PortfolioAssetEntity>): Double {
        // Collect timelines of and align prices
        if (holdings.isEmpty()) return 0.0
        val timelineSize = 60
        val portfolioValues = DoubleArray(timelineSize) { 0.0 }
        
        for (holding in holdings) {
            val history = dao.getHistoricalPrices(holding.symbol)
            // Pick up to past 60 entries
            val offset = (history.size - timelineSize).coerceAtLeast(0)
            for (i in 0 until timelineSize) {
                val indexInHistory = offset + i
                if (indexInHistory < history.size) {
                    portfolioValues[i] += history[indexInHistory].price * holding.quantity
                }
            }
        }

        var maxDrawdown = 0.0
        var peak = Double.MIN_VALUE
        for (valAtT in portfolioValues) {
            if (valAtT > peak) {
                peak = valAtT
            }
            if (peak > 0.0) {
                val dd = (peak - valAtT) / peak
                if (dd > maxDrawdown) {
                    maxDrawdown = dd
                }
            }
        }
        return maxDrawdown
    }

    // --- RISK ENGINE (Value at Risk & Covariances) ---
    suspend fun getRiskMetrics(portfolioId: String, confidence: Double = 0.95): RiskMetrics? {
        val portfolioMetrics = getPortfolioMetrics(portfolioId) ?: return null
        val holdings = dao.getPortfolioAssets(portfolioId)
        if (holdings.isEmpty() || portfolioMetrics.totalValue == 0.0) return null

        val totalValue = portfolioMetrics.totalValue

        // Retrieve Return timelines for historical VaR
        // Align returns for each asset
        val assetReturns = mutableMapOf<String, List<Double>>()
        for (holding in holdings) {
            assetReturns[holding.symbol] = getReturns(holding.symbol)
        }

        // Find common size
        val minReturnSize = assetReturns.values.map { it.size }.minOrNull() ?: 0
        if (minReturnSize < 5) return null

        // 1. HISTORICAL SIMULATION METHOD
        // Re-construct the portfolio Return distribution over time
        val portfolioHistoricalReturns = mutableListOf<Double>()
        for (i in 0 until minReturnSize) {
            var sumDayValue = 0.0
            var sumPrevDayValue = 0.0
            for (holding in holdings) {
                val history = dao.getHistoricalPrices(holding.symbol)
                val indexCurr = history.size - minReturnSize + i
                val indexPrev = indexCurr - 1
                if (indexCurr < history.size && indexPrev >= 0) {
                    sumDayValue += history[indexCurr].price * holding.quantity
                    sumPrevDayValue += history[indexPrev].price * holding.quantity
                }
            }
            if (sumPrevDayValue > 0.0) {
                portfolioHistoricalReturns.add((sumDayValue - sumPrevDayValue) / sumPrevDayValue)
            }
        }

        if (portfolioHistoricalReturns.isEmpty()) return null
        portfolioHistoricalReturns.sort() // ascending - worst outcomes first

        // Find VaR percentile index
        // e.g. for 95% confidence, worst 5% is the ceiling index of returns
        val alpha = 1.0 - confidence
        val indexVaR = ((alpha * portfolioHistoricalReturns.size).toInt()).coerceIn(0, portfolioHistoricalReturns.size - 1)
        val varHistPct = abs(portfolioHistoricalReturns[indexVaR])
        val varHistVal = varHistPct * totalValue

        // CVaR is average return of tail outcomes beyond VaR index
        val tailReturns = portfolioHistoricalReturns.subList(0, indexVaR + 1)
        val cvarHistPct = if (tailReturns.isNotEmpty()) abs(tailReturns.average()) else varHistPct
        val cvarHistVal = cvarHistPct * totalValue

        // 2. PARAMETRIC METHOD (Variance-Covariance)
        // Weight matrix
        val weights = holdings.map { holding ->
            val value = (dao.getLatestPrice(holding.symbol)?.price ?: 0.0) * holding.quantity
            holding.symbol to value / totalValue
        }.toMap()

        // Volatilities
        val vols = holdings.associate { holding ->
            holding.symbol to calculateVolatility(holding.symbol) / sqrt(252.0) // Daily volatility
        }

        // Standard Z score
        val z = when {
            confidence >= 0.99 -> 2.326
            confidence >= 0.95 -> 1.645
            confidence >= 0.90 -> 1.282
            else -> 1.645
        }

        // Compute Parametric Correlation & Covariance Matrix
        var portfolioVariance = 0.0
        for (i in holdings.indices) {
            val assetI = holdings[i].symbol
            val wI = weights[assetI] ?: 0.0
            val volI = vols[assetI] ?: 0.0
            portfolioVariance += (wI * volI) * (wI * volI)

            for (j in i + 1 until holdings.size) {
                val assetJ = holdings[j].symbol
                val wJ = weights[assetJ] ?: 0.0
                val volJ = vols[assetJ] ?: 0.0
                val correl = calculatePearsonCorrelation(assetReturns[assetI] ?: emptyList(), assetReturns[assetJ] ?: emptyList())
                // Covariance ij = correlation * volI * volJ
                val cov = correl * volI * volJ
                portfolioVariance += 2.0 * wI * wJ * cov
            }
        }

        val portfolioDailyVol = sqrt(portfolioVariance)
        val varParametricPct = portfolioDailyVol * z
        val varParametricVal = varParametricPct * totalValue
        val annualizedVol = portfolioDailyVol * sqrt(252.0)

        return RiskMetrics(
            portfolioValue = totalValue,
            varHistoricalVal = varHistVal,
            varHistoricalPct = varHistPct,
            varParametricVal = varParametricVal,
            varParametricPct = varParametricPct,
            cvarHistoricalVal = cvarHistVal,
            cvarHistoricalPct = cvarHistPct,
            weightedVolatility = annualizedVol,
            peakToTroughDrawdown = portfolioMetrics.maxDrawdown
        )
    }

    private fun calculatePearsonCorrelation(seriesA: List<Double>, seriesB: List<Double>): Double {
        val size = seriesA.size.coerceAtMost(seriesB.size)
        if (size < 2) return 0.0
        val a = seriesA.take(size)
        val b = seriesB.take(size)

        val meanA = a.average()
        val meanB = b.average()

        var num = 0.0
        var denA = 0.0
        var denB = 0.0

        for (i in 0 until size) {
            val diffA = a[i] - meanA
            val diffB = b[i] - meanB
            num += diffA * diffB
            denA += diffA * diffA
            denB += diffB * diffB
        }

        if (denA == 0.0 || denB == 0.0) return 0.0
        return num / (sqrt(denA) * sqrt(denB))
    }

    // Computes Correlation Matrix for active symbols
    suspend fun getCorrelationMatrix(): Map<String, Map<String, Double>> {
        val symbols = assetsConfig.keys.toList()
        val returns = symbols.associate { it to getReturns(it) }

        val matrix = mutableMapOf<String, Map<String, Double>>()
        for (sym1 in symbols) {
            val row = mutableMapOf<String, Double>()
            for (sym2 in symbols) {
                if (sym1 == sym2) {
                    row[sym2] = 1.0
                } else {
                    row[sym2] = calculatePearsonCorrelation(returns[sym1] ?: emptyList(), returns[sym2] ?: emptyList())
                }
            }
            matrix[sym1] = row
        }
        return matrix
    }
}
