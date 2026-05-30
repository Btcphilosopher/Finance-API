package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialDao {

    // --- Price Tickers & Time-series Time Ranges ---
    @Query("SELECT * FROM price_records WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPrice(symbol: String): PriceRecordEntity?

    @Query("SELECT * FROM price_records ORDER BY timestamp DESC")
    fun getAllPricesFlow(): Flow<List<PriceRecordEntity>>

    @Query("SELECT * FROM price_records WHERE symbol = :symbol ORDER BY timestamp ASC")
    suspend fun getHistoricalPrices(symbol: String): List<PriceRecordEntity>

    @Query("SELECT * FROM price_records WHERE symbol = :symbol AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getPricesInRange(symbol: String, startTime: Long, endTime: Long): List<PriceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<PriceRecordEntity>)

    @Query("DELETE FROM price_records")
    suspend fun clearPrices()

    // --- Portfolios ---
    @Query("SELECT * FROM portfolios ORDER BY createdTimestamp DESC")
    fun getPortfoliosFlow(): Flow<List<PortfolioEntity>>

    @Query("SELECT * FROM portfolios WHERE id = :id")
    suspend fun getPortfolioById(id: String): PortfolioEntity?

    @Query("SELECT * FROM portfolio_assets WHERE portfolioId = :portfolioId")
    suspend fun getPortfolioAssets(portfolioId: String): List<PortfolioAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolio(portfolio: PortfolioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolioAssets(assets: List<PortfolioAssetEntity>)

    @Query("DELETE FROM portfolios WHERE id = :portfolioId")
    suspend fun deletePortfolio(portfolioId: String)

    @Query("DELETE FROM portfolio_assets WHERE portfolioId = :portfolioId")
    suspend fun deletePortfolioAssets(portfolioId: String)
}
