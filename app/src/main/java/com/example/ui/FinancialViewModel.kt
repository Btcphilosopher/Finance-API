package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiRequestLog
import com.example.api.FinancialApiServer
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinancialViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = FinancialRepository(database.financialDao())
    val server = FinancialApiServer(repository, port = 8555) // Binding to 8555 inside the app emulator

    // Background jobs
    private var tickerJob: Job? = null

    // Server States
    val isServerRunning: StateFlow<Boolean> = server.isServerRunning
    val requestLogs: StateFlow<List<ApiRequestLog>> = server.requestLogs
    val apiKey: StateFlow<String> = server.apiKey

    // Market Data States
    private val _isGeneratingStats = MutableStateFlow(false)
    val isGeneratingStats: StateFlow<Boolean> = _isGeneratingStats.asStateFlow()

    private val _isActivelyTicking = MutableStateFlow(false)
    val isActivelyTicking: StateFlow<Boolean> = _isActivelyTicking.asStateFlow()

    val activePricesBySymbol = repository.allPricesFlow
        .map { list ->
            // Group by symbol and take the latest (highest timestamp)
            list.groupBy { it.symbol }
                .mapValues { entry -> entry.value.maxByOrNull { it.timestamp } }
                .values.filterNotNull()
                .sortedBy { it.symbol }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRecordsCount = repository.allPricesFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Interactive selections
    val selectedSymbol = MutableStateFlow("AAPL")
    val selectedPortfolioId = MutableStateFlow("default_portfolio")
    val riskConfidenceLevel = MutableStateFlow(0.95)

    // Derived states driven by selection on Dispatchers.Default
    val selectedAssetMetrics = combine(selectedSymbol, activePricesBySymbol, repository.allPricesFlow) { sym, _, _ ->
        _isGeneratingStats.value = true
        val vol = repository.calculateVolatility(sym)
        val list = repository.getHistoricalPrices(sym)
        val returns = repository.getReturns(sym)
        val sma = repository.getRollingSMA(sym, window = 10)
        _isGeneratingStats.value = false
        Triple(vol, returns, sma)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0.0, emptyList(), emptyList()))

    val correlationMatrix = repository.allPricesFlow.map {
        repository.getCorrelationMatrix()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(15000), emptyMap())

    val portfolios = repository.portfolios.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPortfolioMetrics = combine(selectedPortfolioId, activePricesBySymbol) { id, _ ->
        repository.getPortfolioMetrics(id)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedPortfolioRisk = combine(selectedPortfolioId, riskConfidenceLevel, activePricesBySymbol) { id, conf, _ ->
        repository.getRiskMetrics(id, conf)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Core initialization: seed baseline values and startup ticking automatically
        viewModelScope.launch {
            _isGeneratingStats.value = true
            // Seed baseline values
            repository.seedDatabase()
            _isGeneratingStats.value = false
            
            // Start local HTTP server
            server.startServer()
            
            // Start background ticks
            startTicker()
        }
    }

    fun toggleServer() {
        if (isServerRunning.value) {
            server.stopServer()
        } else {
            server.startServer()
        }
    }

    fun startTicker() {
        if (_isActivelyTicking.value) return
        _isActivelyTicking.value = true
        tickerJob = viewModelScope.launch(Dispatchers.IO) {
            while (_isActivelyTicking.value) {
                repository.tickLivePrices()
                delay(3000) // Ticks every 3 seconds to keep UI/endpoints alive
            }
        }
    }

    fun stopTicker() {
        _isActivelyTicking.value = false
        tickerJob?.cancel()
        tickerJob = null
    }

    fun triggerManualIngestion() {
        viewModelScope.launch {
            _isGeneratingStats.value = true
            repository.seedDatabase()
            _isGeneratingStats.value = false
        }
    }

    fun addPortfolioDirectly(id: String, name: String, holdings: List<Pair<String, Double>>) {
        viewModelScope.launch {
            _isGeneratingStats.value = true
            repository.createPortfolio(id, name, holdings)
            _isGeneratingStats.value = false
        }
    }

    fun changeApiKey(newKey: String) {
        server.updateApiKey(newKey)
    }

    override fun onCleared() {
        super.onCleared()
        server.stopServer()
        stopTicker()
    }
}
