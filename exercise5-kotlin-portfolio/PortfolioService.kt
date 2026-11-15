package com.practice.portfolio

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Date

/**
 * PR #523 — "Portfolio valuation + rebalancing suggestions"
 *
 * Kotlin service used by the mobile BFF. Review it. ~10 minutes.
 * You may answer in Java terms, but notice the Kotlin-specific smells.
 */
data class Position(
    var isin: String,
    var units: Double,
    var avgPrice: Double,
    var currency: String? = null
)

data class Valuation(val total: Double, val positions: List<Position>, val asOf: String)

@Service
class PortfolioService(
    private val positionRepository: PositionRepository,
    private val priceClient: PriceClient,
    private val auditLog: AuditLog
) {

    lateinit var baseCurrency: String

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    private val cache = mutableMapOf<String, Valuation>()

    fun valuation(customerId: String): Valuation {
        cache[customerId]?.let { return it }

        val positions = positionRepository.findByCustomerId(customerId)
        var total = 0.0
        for (p in positions) {
            val price = runBlocking { priceClient.latestPrice(p.isin) }
            total += p.units * price!!
        }
        val result = Valuation(total, positions, dateFormat.format(Date()))
        cache[customerId] = result

        GlobalScope.launch {
            auditLog.record(customerId, "VALUATION", total)
        }
        return result
    }

    @Transactional
    fun rebalance(customerId: String, targetWeights: Map<String, Double>) {
        val valuation = valuation(customerId)
        if (targetWeights.values.sum() != 1.0) {
            throw IllegalArgumentException("weights must sum to 1")
        }
        for (p in valuation.positions) {
            val target = targetWeights[p.isin] ?: 0.0
            val targetValue = valuation.total * target
            val currentValue = p.units * priceOf(p.isin)
            if (currentValue > targetValue) {
                p.units = targetValue / priceOf(p.isin)
                positionRepository.save(p)
            }
        }
        cache.remove(customerId)
    }

    private fun priceOf(isin: String): Double = runBlocking { priceClient.latestPrice(isin) } ?: 0.0

    fun positionsAbove(customerId: String, threshold: Double): List<Position> {
        return positionRepository.findByCustomerId(customerId)
            .filter { it.units * priceOf(it.isin) > threshold }
            .sortedBy { it.units }
            .also { println("found ${it.size} positions") }
    }

    fun format(p: Position) = "${p.isin}: ${p.units} @ ${p.avgPrice} ${p.currency!!}"
}

interface PriceClient {
    suspend fun latestPrice(isin: String): Double?
}

interface PositionRepository {
    fun findByCustomerId(customerId: String): List<Position>
    fun save(p: Position): Position
}

interface AuditLog {
    suspend fun record(customerId: String, event: String, value: Double)
}
