package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class SaleTaxTest {
    private var nextId = 1L
    private val asset = 10L
    private val today = LocalDate.parse("2026-11-15")

    private fun trade(type: TransactionType, date: String, quantity: String, price: String, assetId: Long = asset) =
        TransactionEntity(
            id = nextId++,
            accountId = 1,
            assetId = assetId,
            type = type,
            date = LocalDate.parse(date),
            quantity = BigDecimal(quantity),
            price = BigDecimal(price),
        )

    private fun assertMoney(expected: String, actual: BigDecimal) =
        assertEquals(BigDecimal(expected), actual.setScale(2, RoundingMode.HALF_UP))

    @Test
    fun `savings brackets are applied progressively`() {
        assertMoney("190.00", savingsTax(BigDecimal("1000")))
        // 6000 at 19% + 4000 at 21%
        assertMoney("1980.00", savingsTax(BigDecimal("10000")))
        // 1140 + 9240 + 34500 + 27000 + 100000 at 30%
        assertMoney("101880.00", savingsTax(BigDecimal("400000")))
    }

    @Test
    fun `sale is matched fifo and taxed on the gain`() {
        val transactions = listOf(
            trade(TransactionType.BUY, "2024-01-10", "10", "100"),
            trade(TransactionType.BUY, "2025-01-10", "10", "150"),
        )

        val result = simulateSale(transactions, asset, BigDecimal("15"), BigDecimal("200"), BigDecimal("5"), today)!!

        assertMoney("2995.00", result.proceeds)
        assertMoney("1750.00", result.cost)
        assertMoney("236.55", result.tax)
        assertMoney("2758.45", result.afterTax)
        assertFalse(result.lossDeferred)
    }

    @Test
    fun `gains already realized this year push the sale into the next bracket`() {
        val other = 20L
        val transactions = listOf(
            trade(TransactionType.BUY, "2024-01-10", "100", "10", assetId = other),
            trade(TransactionType.SELL, "2026-03-01", "100", "65", assetId = other),
            trade(TransactionType.BUY, "2024-01-10", "10", "100"),
        )

        // 5500 already realized; this sale adds 1000: 500 at 19% and 500 at 21%.
        val result = simulateSale(transactions, asset, BigDecimal("10"), BigDecimal("200"), BigDecimal.ZERO, today)!!

        assertMoney("200.00", result.tax)
    }

    @Test
    fun `a loss offsets earlier gains and owes no tax`() {
        val other = 20L
        val transactions = listOf(
            trade(TransactionType.BUY, "2024-01-10", "10", "100", assetId = other),
            trade(TransactionType.SELL, "2026-03-01", "10", "200", assetId = other),
            trade(TransactionType.BUY, "2024-01-10", "10", "100"),
        )

        val result = simulateSale(transactions, asset, BigDecimal("10"), BigDecimal("60"), BigDecimal.ZERO, today)!!

        assertMoney("-400.00", result.gain)
        assertMoney("0.00", result.tax)
        assertFalse(result.lossDeferred)
    }

    @Test
    fun `a loss is deferred while shares bought in the last two months remain`() {
        val transactions = listOf(
            trade(TransactionType.BUY, "2024-01-10", "10", "100"),
            trade(TransactionType.BUY, "2026-10-20", "5", "90"),
        )

        val partial = simulateSale(transactions, asset, BigDecimal("10"), BigDecimal("60"), BigDecimal.ZERO, today)!!
        val everything = simulateSale(transactions, asset, BigDecimal("15"), BigDecimal("60"), BigDecimal.ZERO, today)!!

        assertTrue(partial.lossDeferred)
        assertFalse(everything.lossDeferred)
    }

    @Test
    fun `selling more than is held cannot be simulated`() {
        val transactions = listOf(trade(TransactionType.BUY, "2024-01-10", "10", "100"))

        assertNull(simulateSale(transactions, asset, BigDecimal("11"), BigDecimal("200"), BigDecimal.ZERO, today))
    }
}
