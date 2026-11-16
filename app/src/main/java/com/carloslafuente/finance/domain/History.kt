package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.data.prices.Quote
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Market value of the positions on a day, next to the money put into them so far (buys minus sales). */
data class ValuePoint(val date: LocalDate, val value: BigDecimal, val invested: BigDecimal)

/**
 * Day-by-day value of the positions, from the first trade to the last known price.
 * [prices] holds each asset's quotes sorted by date. A day without a quote reuses the previous
 * one; before an asset's first quote, the price of its own trades stands in.
 */
fun portfolioHistory(transactions: List<TransactionEntity>, prices: Map<Long, List<Quote>>): List<ValuePoint> {
    val trades = transactions
        .filter { it.type == TransactionType.BUY || it.type == TransactionType.SELL || it.type == TransactionType.SPLIT }
        .sortedWith(compareBy({ it.date }, { it.id }))
    val start = trades.firstOrNull()?.date ?: return emptyList()
    val assetIds = trades.map { it.assetId!! }.toSet()
    val quotes = assetIds.associateWith { prices[it].orEmpty() }
    val dates = (trades.map { it.date } + quotes.values.flatten().map { it.date }.filter { it >= start }).toSortedSet()

    val quantity = mutableMapOf<Long, BigDecimal>()
    val price = mutableMapOf<Long, BigDecimal>()
    val nextQuote = assetIds.associateWith { 0 }.toMutableMap()
    var invested = BigDecimal.ZERO
    var nextTrade = 0

    return dates.map { date ->
        for ((assetId, list) in quotes) {
            var i = nextQuote.getValue(assetId)
            while (i < list.size && list[i].date <= date) price[assetId] = list[i++].price
            nextQuote[assetId] = i
        }
        while (nextTrade < trades.size && trades[nextTrade].date <= date) {
            val tx = trades[nextTrade++]
            val assetId = tx.assetId!!
            val held = quantity[assetId] ?: BigDecimal.ZERO
            when (tx.type) {
                TransactionType.BUY -> quantity[assetId] = held + tx.quantity!!
                TransactionType.SELL -> quantity[assetId] = held - tx.quantity!!
                else -> quantity[assetId] = held.multiply(tx.quantity!!, MathContext.DECIMAL64)
            }
            if (tx.type != TransactionType.SPLIT) {
                invested -= tx.cashEffect()
                price.getOrPut(assetId) { tx.price!! }
            }
        }
        ValuePoint(date, quantity.entries.sumOf { (assetId, held) -> held * (price[assetId] ?: BigDecimal.ZERO) }, invested)
    }
}

/** Gain between the first and last point: the change in value that new money does not explain. */
fun gainOver(points: List<ValuePoint>): BigDecimal =
    if (points.isEmpty()) BigDecimal.ZERO
    else (points.last().value - points.first().value) - (points.last().invested - points.first().invested)
