package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReportsTest {
    private fun tx(
        id: Long, type: TransactionType, date: String,
        quantity: String? = null, price: String? = null, amount: String? = null, tax: String = "0",
    ) = TransactionEntity(
        id = id, accountId = 1, assetId = 1.takeIf { type.needsAsset }?.toLong(), type = type, date = LocalDate.parse(date),
        quantity = quantity?.let(::BigDecimal), price = price?.let(::BigDecimal), amount = amount?.let(::BigDecimal),
        tax = BigDecimal(tax),
    )

    @Test
    fun `tax report covers only the year's sales and income`() {
        val transactions = listOf(
            tx(1, TransactionType.BUY, "2025-03-01", quantity = "10", price = "100"),
            tx(2, TransactionType.SELL, "2025-12-01", quantity = "2", price = "150"), // previous year
            tx(3, TransactionType.SELL, "2026-06-01", quantity = "5", price = "300"),
            tx(4, TransactionType.DIVIDEND, "2026-07-01", amount = "100", tax = "19"),
            tx(5, TransactionType.INTEREST, "2026-08-01", amount = "50", tax = "9.5"),
            tx(6, TransactionType.DIVIDEND, "2025-07-01", amount = "70", tax = "13.3"),
        )

        val report = taxReport(transactions, 2026)

        assertEquals(1, report.sales.size)
        assertEquals(0, BigDecimal("1500").compareTo(report.proceeds))
        assertEquals(0, BigDecimal("500").compareTo(report.cost))
        assertEquals(0, BigDecimal("1000").compareTo(report.gain))
        assertEquals(0, BigDecimal("100").compareTo(report.dividends))
        assertEquals(0, BigDecimal("50").compareTo(report.interest))
        assertEquals(0, BigDecimal("28.5").compareTo(report.withheld))
        // 1,150 of savings income, all in the 19 % bracket.
        assertEquals(BigDecimal("218.50"), report.estimatedTax)
    }

    @Test
    fun `projection compounds contributions month by month`() {
        val flat = project(start = 1000.0, monthly = 100.0, annualRate = 0.0, years = 2)
        assertEquals(listOf(1000.0, 2200.0, 3400.0), flat.map { it.value })
        assertEquals(listOf(1000.0, 2200.0, 3400.0), flat.map { it.contributed })

        // With no contributions a year at 6 % is exactly 6 % more.
        assertEquals(1060.0, project(1000.0, 0.0, 0.06, 1).last().value, 0.001)
        // Contributions made during the year earn less than a full year's return.
        val grown = project(0.0, 100.0, 0.06, 1).last().value
        assertEquals(true, grown > 1200.0 && grown < 1272.0)
    }
}
