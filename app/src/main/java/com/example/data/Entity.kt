package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "price_records")
data class PriceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val timestamp: Long, // milliseconds
    val price: Double,
    val volume: Long,
    val assetClass: String // "EQUITY", "CRYPTO", "FX", "INDEX"
) : Serializable

@Entity(tableName = "portfolios")
data class PortfolioEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdTimestamp: Long
) : Serializable

@Entity(tableName = "portfolio_assets")
data class PortfolioAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portfolioId: String,
    val symbol: String,
    val quantity: Double
) : Serializable
