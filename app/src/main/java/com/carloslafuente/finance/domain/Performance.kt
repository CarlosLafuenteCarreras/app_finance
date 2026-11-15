package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.data.prices.Quote
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** Money moving between the investor and the investments: negative when invested, positive when received. */
data class CashFlow(val date: LocalDate, val amount: BigDecimal)

/** Buys, sells and dividends; deposits and other cash-only movements are not investment flows. */
fun investmentFlows(transactions: List<TransactionEntity>): List<CashFlow> = transactions
    .filter { it.type == TransactionType.BUY || it.type == TransactionType.SELL || it.type == TransactionType.DIVIDEND }
    .sortedWith(compareBy({ it.date }, { it.id }))
    .map { CashFlow(it.date, it.cashEffect()) }

/**
 * Annualized money-weighted return of [flows] ending with [finalValue] on [today].
 * Null when there is no rate that makes sense (no flows, or no sign change).
 */
fun annualizedReturn(flows: List<CashFlow>, finalValue: BigDecimal, today: LocalDate): Double? {
    val all = flows + CashFlow(today, finalValue)
    if (all.none { it.amount.signum() < 0 } || all.none { it.amount.signum() > 0 }) return null
    val start = all.minOf { it.date }

    fun netPresentValue(rate: Double): Double = all.sumOf {
        it.amount.toDouble() / (1 + rate).pow(ChronoUnit.DAYS.between(start, it.date) / 365.0)
    }

    // The value falls as the rate rises when money goes in first and comes out later; bisect.
    var low = -0.99
    var high = 100.0
    if (netPresentValue(low) < 0 || netPresentValue(high) > 0) return null
    repeat(200) {
        val mid = (low + high) / 2
        if (netPresentValue(mid) > 0) low = mid else high = mid
    }
    return (low + high) / 2
}

/**
 * What the same money would be worth today if every flow had gone into the benchmark instead:
 * each investment buys benchmark units at that day's price and each withdrawal sells them.
 * [history] must be sorted by date. Null if it does not reach back to the first flow.
 */
fun benchmarkValue(flows: List<CashFlow>, history: List<Quote>): BigDecimal? {
    if (flows.isEmpty() || history.isEmpty() || history.first().date > flows.first().date) return null
    var units = BigDecimal.ZERO
    for (flow in flows) {
        val price = history.last { it.date <= flow.date }.price
        units -= flow.amount.divide(price, MathContext.DECIMAL64)
    }
    return units * history.last().price
}
