package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.prices.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class PerformanceTest {
    private fun flow(date: String, amount: String) = CashFlow(LocalDate.parse(date), BigDecimal(amount))
    private fun quote(date: String, price: String) = Quote(LocalDate.parse(date), BigDecimal(price))

    @Test
    fun `one investment that grows ten percent in a year returns ten percent`() {
        val rate = annualizedReturn(listOf(flow("2025-01-01", "-1000")), BigDecimal("1100"), LocalDate.parse("2026-01-01"))

        assertEquals(0.10, rate!!, 1e-6)
    }

    @Test
    fun `later contributions are weighted by the time they were invested`() {
        // 1000 for two years and 1000 for one year, ending at 2310: exactly 10% a year on each.
        val flows = listOf(flow("2024-01-01", "-1000"), flow("2025-01-01", "-1000"))

        val rate = annualizedReturn(flows, BigDecimal("2310"), LocalDate.parse("2025-12-31"))

        assertEquals(0.10, rate!!, 1e-3)
    }

    @Test
    fun `a loss gives a negative rate`() {
        val rate = annualizedReturn(listOf(flow("2025-01-01", "-1000")), BigDecimal("800"), LocalDate.parse("2026-01-01"))

        assertEquals(-0.20, rate!!, 1e-6)
    }

    @Test
    fun `no rate without money invested`() {
        assertNull(annualizedReturn(emptyList(), BigDecimal.ZERO, LocalDate.parse("2026-01-01")))
    }

    @Test
    fun `benchmark buys and sells units at each flow date`() {
        val history = listOf(
            quote("2025-01-02", "100"),
            quote("2025-06-02", "125"),
            quote("2025-12-01", "200"),
        )
        // 10 units bought at 100, 4 more at 125 (weekend: last price before the flow), 2 sold at 125.
        val flows = listOf(flow("2025-01-02", "-1000"), flow("2025-06-07", "-500"), flow("2025-06-08", "250"))

        val value = benchmarkValue(flows, history)

        assertEquals(BigDecimal("2400.00"), value!!.setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `no benchmark when its history starts after the first flow`() {
        assertNull(benchmarkValue(listOf(flow("2024-01-01", "-1000")), listOf(quote("2025-01-02", "100"))))
    }
}
