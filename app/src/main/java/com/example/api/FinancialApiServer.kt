package com.example.api

import android.util.Log
import com.example.data.FinancialRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ApiRequestLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val latencyMs: Long,
    val errorMessage: String? = null
)

class FinancialApiServer(
    private val repository: FinancialRepository,
    private val port: Int = 8080
) {
    private var server: HttpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // State flows to expose server status and request logs to Compose UI
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _requestLogs = MutableStateFlow<List<ApiRequestLog>>(emptyList())
    val requestLogs: StateFlow<List<ApiRequestLog>> = _requestLogs.asStateFlow()

    private val _apiKey = MutableStateFlow("QNT-ENG-2026-AX")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    // Sliding window rate limiter: Ip address -> List of millisecond timestamps of requests
    private val clientRequestTimes = mutableMapOf<String, MutableList<Long>>()
    private val rateLimitWindowMs = 10000L // 10 seconds
    private val maxRequestsPerWindow = 12  // Allow 12 requests per 10 secs

    fun updateApiKey(newKey: String) {
        _apiKey.value = newKey
    }

    fun startServer() {
        if (_isServerRunning.value) return
        try {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                // Main dispatcher route
                createContext("/api/v1", ApiHandler())
                executor = null // Use default single-threaded executor, handled on dispatcher IO
            }
            server?.start()
            _isServerRunning.value = true
            addLog("SYSTEM", "STARTUP", "Server binding successfully to Port $port", 200, 0)
        } catch (e: Exception) {
            Log.e("FinancialApiServer", "Error starting server: ", e)
            addLog("SYSTEM", "ERROR", "Server startup failed: ${e.message}", 500, 0)
        }
    }

    fun stopServer() {
        if (!_isServerRunning.value) return
        try {
            server?.stop(0)
            server = null
            _isServerRunning.value = false
            addLog("SYSTEM", "SHUTDOWN", "Server stopped on Port $port", 200, 0)
        } catch (e: Exception) {
            Log.e("FinancialApiServer", "Error stopping server: ", e)
        }
    }

    private fun addLog(ip: String, method: String, path: String, status: Int, latency: Long, error: String? = null) {
        val newLog = ApiRequestLog(
            clientIp = ip,
            method = method,
            path = path,
            statusCode = status,
            latencyMs = latency,
            errorMessage = error
        )
        _requestLogs.value = (listOf(newLog) + _requestLogs.value).take(150) // Keep last 150 entries
    }

    private inner class ApiHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val startTime = System.currentTimeMillis()
            val clientIp = exchange.remoteAddress?.address?.hostAddress ?: "unknown"
            val method = exchange.requestMethod
            val uri = exchange.requestURI.toString()
            val path = exchange.requestURI.path

            // 1. Sliding Window Rate-Limiting Check
            if (isRateLimited(clientIp)) {
                val responseMsg = "{\"error\": \"Too many requests. Rate limit is $maxRequestsPerWindow requests per ${rateLimitWindowMs/1000} seconds.\"}"
                sendResponse(exchange, 429, responseMsg)
                val latency = System.currentTimeMillis() - startTime
                addLog(clientIp, method, uri, 429, latency, "Rate limit exceeded")
                return
            }

            // 2. API Key Authentication Check
            val requestApiKey = exchange.requestHeaders.getFirst("X-API-KEY")
            val currentRequiredKey = _apiKey.value
            if (currentRequiredKey.isNotEmpty() && requestApiKey != currentRequiredKey) {
                val responseMsg = "{\"error\": \"Unauthorized. Invalid or missing X-API-KEY header.\"}"
                sendResponse(exchange, 401, responseMsg)
                val latency = System.currentTimeMillis() - startTime
                addLog(clientIp, method, uri, 401, latency, "Authentication failed")
                return
            }

            // Handle APIs asynchronously to avoid blocking the server loop
            scope.launch {
                var statusCode = 200
                var jsonResponse = "{}"
                var exceptionMsg: String? = null

                try {
                    val queryMap = parseQueryParams(exchange.requestURI.query ?: "")
                    when {
                        // --- 1. GET PRICE / TICKER / TIME-SERIES ---
                        path.startsWith("/api/v1/prices/") -> {
                            val symbol = path.substringAfter("/api/v1/prices/").uppercase()
                            val latest = repository.getLatestPrice(symbol)
                            if (latest == null) {
                                statusCode = 404
                                jsonResponse = "{\"error\": \"Price feed for symbol '$symbol' not found.\"}"
                            } else {
                                val history = repository.getHistoricalPrices(symbol)
                                val historyJson = history.joinToString(prefix = "[", postfix = "]") { h ->
                                    "{\"timestamp\": ${h.timestamp}, \"price\": ${h.price}, \"volume\": ${h.volume}}"
                                }
                                jsonResponse = """
                                    {
                                        "symbol": "${latest.symbol}",
                                        "assetClass": "${latest.assetClass}",
                                        "latestPrice": ${latest.price},
                                        "volume": ${latest.volume},
                                        "timestamp": ${latest.timestamp},
                                        "historyCount": ${history.size},
                                        "history": $historyJson
                                    }
                                """.trimIndent()
                            }
                        }

                        // --- 2. GET RETURNS TIMELINE ---
                        path.startsWith("/api/v1/returns/") -> {
                            val symbol = path.substringAfter("/api/v1/returns/").uppercase()
                            val retList = repository.getReturns(symbol)
                            if (retList.isEmpty()) {
                                statusCode = 404
                                jsonResponse = "{\"error\": \"Return series for '$symbol' could not be calculated.\"}"
                            } else {
                                val returnsJson = retList.joinToString(prefix = "[", postfix = "]")
                                val vol = repository.calculateVolatility(symbol)
                                jsonResponse = """
                                    {
                                        "symbol": "$symbol",
                                        "dailyReturnsCount": ${retList.size},
                                        "annualizedVolatility": $vol,
                                        "returns": $returnsJson
                                    }
                                """.trimIndent()
                            }
                        }

                        // --- 3. CREATE PORTFOLIO ---
                        path == "/api/v1/portfolio/create" -> {
                            val id = queryMap["id"] ?: "portfolio_${UUID.randomUUID().toString().take(6)}"
                            val name = queryMap["name"] ?: "Managed Portfolio"
                            val assetsRaw = queryMap["assets"] ?: "AAPL:10" // format ticker:amt,ticker:amt
                            
                            val holdings = mutableListOf<Pair<String, Double>>()
                            try {
                                assetsRaw.split(",").forEach { pairStr ->
                                    val parts = pairStr.split(":")
                                    if (parts.size == 2) {
                                        holdings.add(parts[0].uppercase() to parts[1].toDouble())
                                    }
                                }
                                repository.createPortfolio(id, name, holdings)
                                jsonResponse = """
                                    {
                                        "status": "success",
                                        "portfolioId": "$id",
                                        "name": "$name",
                                        "holdingsCount": ${holdings.size}
                                    }
                                """.trimIndent()
                            } catch (e: Exception) {
                                statusCode = 400
                                jsonResponse = "{\"error\": \"Invalid asset allocation format. Use: assets=AAPL:10,BTC:0.5\"}"
                            }
                        }

                        // --- 4. PORTFOLIO VALUE exposures, P&L ---
                        path == "/api/v1/portfolio/value" -> {
                            val id = queryMap["id"] ?: "default_portfolio"
                            val metrics = repository.getPortfolioMetrics(id)
                            if (metrics == null) {
                                statusCode = 404
                                jsonResponse = "{\"error\": \"Portfolio with ID '$id' not found.\"}"
                            } else {
                                val classExposureJson = metrics.assetExposures.map { "\"${it.key}\": ${it.value}" }.joinToString(prefix = "{", postfix = "}")
                                val itemExposureJson = metrics.itemExposures.map { "\"${it.key}\": ${it.value}" }.joinToString(prefix = "{", postfix = "}")
                                jsonResponse = """
                                    {
                                        "portfolioId": "${metrics.id}",
                                        "portfolioName": "${metrics.name}",
                                        "totalAssetValue": ${metrics.totalValue},
                                        "portfolioTotalReturnPct": ${metrics.totalReturns},
                                        "historicalMaxDrawdownPct": ${metrics.maxDrawdown},
                                        "assetClassAllocations": $classExposureJson,
                                        "individualExposuresCurrency": $itemExposureJson
                                    }
                                """.trimIndent()
                            }
                        }

                        // --- 5. RISK ENGINE (Value at Risk / Expected Shortfall) ---
                        path == "/api/v1/risk/var" -> {
                            val id = queryMap["id"] ?: "default_portfolio"
                            val conf = (queryMap["confidence"] ?: "0.95").toDoubleOrNull() ?: 0.95
                            val risk = repository.getRiskMetrics(portfolioId = id, confidence = conf)
                            if (risk == null) {
                                statusCode = 404
                                jsonResponse = "{\"error\": \"Unable to compute risk metrics for portfolio ID '$id'. Check if it has active assets in DB.\"}"
                            } else {
                                jsonResponse = """
                                    {
                                        "portfolioId": "$id",
                                        "portfolioValue": ${risk.portfolioValue},
                                        "confidenceLevel": $conf,
                                        "valueAtRiskHistorical": {
                                            "amount": ${risk.varHistoricalVal},
                                            "percentage": ${risk.varHistoricalPct}
                                        },
                                        "valueAtRiskParametric": {
                                            "amount": ${risk.varParametricVal},
                                            "percentage": ${risk.varParametricPct}
                                        },
                                        "conditionalVaRHistorical": {
                                            "amount": ${risk.cvarHistoricalVal},
                                            "percentage": ${risk.cvarHistoricalPct}
                                        },
                                        "portfolioAnnualizedVolatility": ${risk.weightedVolatility},
                                        "portfolioMaxDrawdown": ${risk.peakToTroughDrawdown}
                                    }
                                """.trimIndent()
                            }
                        }

                        // --- 6. RISK VOLATILITY METRICS ---
                        path == "/api/v1/risk/volatility" -> {
                            val symbol = queryMap["symbol"] ?: "AAPL"
                            val vol = repository.calculateVolatility(symbol)
                            jsonResponse = """
                                {
                                    "target": "$symbol",
                                    "annualizedVolatility": $vol
                                }
                            """.trimIndent()
                        }

                        // --- 7. ANALYTICS CORRELATION MATRIX ---
                        path == "/api/v1/analytics/correlation" -> {
                            val matrix = repository.getCorrelationMatrix()
                            val matrixRows = matrix.map { (rowSym, colMap) ->
                                val cols = colMap.map { "\"${it.key}\": ${it.value}" }.joinToString(prefix = "{", postfix = "}")
                                "\"$rowSym\": $cols"
                            }.joinToString(prefix = "{", postfix = "}")
                            jsonResponse = """
                                {
                                    "description": "Pearson Correlation Matrix of returns (historical simulation series)",
                                    "matrix": $matrixRows
                                }
                            """.trimIndent()
                        }

                        // Catch fallback
                        else -> {
                            statusCode = 404
                            jsonResponse = "{\"error\": \"Endpoint not found. Exposed paths: /api/v1/prices/{symbol}, /api/v1/returns/{symbol}, /api/v1/portfolio/create, /api/v1/portfolio/value, /api/v1/risk/var, /api/v1/risk/volatility, /api/v1/analytics/correlation\"}"
                        }
                    }
                } catch (e: Exception) {
                    statusCode = 500
                    jsonResponse = "{\"error\": \"Internal server execution failure: ${e.message}\"}"
                    exceptionMsg = e.message
                }

                // Final write to exchange
                sendResponse(exchange, statusCode, jsonResponse)
                val latency = System.currentTimeMillis() - startTime
                addLog(clientIp, method, uri, statusCode, latency, exceptionMsg)
            }
        }

        private fun sendResponse(exchange: HttpExchange, statusCode: Int, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*") // CORS enable
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            val os: OutputStream = exchange.responseBody
            os.write(bytes)
            os.close()
        }

        private fun parseQueryParams(query: String?): Map<String, String> {
            if (query.isNullOrBlank()) return emptyMap()
            val result = mutableMapOf<String, String>()
            query.split("&").forEach { pair ->
                val parts = pair.split("=")
                if (parts.size == 2) {
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    result[key] = value
                }
            }
            return result
        }
    }

    private fun isRateLimited(clientIp: String): Boolean {
        if (clientIp == "SYSTEM") return false
        val now = System.currentTimeMillis()
        synchronized(clientRequestTimes) {
            val list = clientRequestTimes.getOrPut(clientIp) { mutableListOf() }
            list.removeAll { now - it > rateLimitWindowMs }
            if (list.size >= maxRequestsPerWindow) return true
            list.add(now)
            return false
        }
    }
}
