package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import java.math.BigDecimal
import kotlin.math.pow

/** What a tax year's income statement needs from the portfolio. */
data class TaxReport(
    val year: Int,
    /** Each sale of the year split by the lot it was matched against (FIFO), oldest sale first. */
    val sales: List<RealizedGain>,
    /** Gross dividends and interest, and what was withheld from them. */
    val dividends: BigDecimal,
    val interest: BigDecimal,
    val withheld: BigDecimal,
) {
    val proceeds: BigDecimal get() = sales.sumOf { it.proceeds }
    val cost: BigDecimal get() = sales.sumOf { it.cost }
    val gain: BigDecimal get() = proceeds - cost

    /**
     * Savings tax on the year's income before withholdings. Simplified: a net loss on sales
     * does not reduce the tax on dividends and interest (the law allows offsetting up to 25 %),
     * and losses carried from earlier years are not considered.
     */
    val estimatedTax: BigDecimal get() = savingsTax(gain.max(BigDecimal.ZERO) + dividends + interest)
}

fun taxReport(transactions: List<TransactionEntity>, year: Int): TaxReport {
    fun gross(type: TransactionType) =
        transactions.filter { it.type == type && it.date.year == year }.sumOf { it.amount ?: BigDecimal.ZERO }
    return TaxReport(
        year = year,
        sales = buildLedger(transactions).realized.filter { it.soldOn.year == year },
        dividends = gross(TransactionType.DIVIDEND),
        interest = gross(TransactionType.INTEREST),
        withheld = transactions
            .filter { (it.type == TransactionType.DIVIDEND || it.type == TransactionType.INTEREST) && it.date.year == year }
            .sumOf { it.tax },
    )
}

/** Where a plan of regular contributions stands after some years. */
data class ProjectionPoint(val year: Int, val value: Double, val contributed: Double)

/**
 * Year-by-year value of [start] plus [monthly] contributions, compounding monthly at the monthly
 * equivalent of [annualRate]. The first point is year 0, today.
 */
fun project(start: Double, monthly: Double, annualRate: Double, years: Int): List<ProjectionPoint> {
    val monthlyRate = (1 + annualRate).pow(1.0 / 12) - 1
    var value = start
    return listOf(ProjectionPoint(0, start, start)) + (1..years).map { year ->
        repeat(12) { value = value * (1 + monthlyRate) + monthly }
        ProjectionPoint(year, value, start + monthly * 12 * year)
    }
}
