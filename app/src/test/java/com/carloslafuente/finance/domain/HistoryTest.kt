package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.data.prices.Quote
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HistoryTest {
    private fun day(d: Int) = LocalDate.of(2026, 11, d)

    private fun trade(id: Long, type: TransactionType, d: Int, quantity: String, price: String) = TransactionEntity(
        id = id, accountId = 1, assetId = 1, type = type, date = day(d),
        quantity = BigDecimal(quantity), price = BigDecimal(price),
    )

    private fun assertPoint(date: LocalDate, value: String, invested: String, actual: ValuePoint) {
        assertEquals(date, actual.date)
        assertEquals(0, BigDecimal(value).compareTo(actual.value))
        assertEquals(0, BigDecimal(invested).compareTo(actual.invested))
    }

    @Test
    fun `values each day at the last known price and tracks the money put in`() {
        val transactions = listOf(
            trade(1, TransactionType.BUY, 2, "10", "100"),
            trade(2, TransactionType.BUY, 5, "5", "120"),
            trade(3, TransactionType.SELL, 6, "3", "130"),
        )
        val quotes = listOf(
            Quote(day(1), BigDecimal("90")), // before the first trade: not a point
            Quote(day(3), BigDecimal("110")),
            Quote(day(6), BigDecimal("130")),
        )

        val history = portfolioHistory(transactions, mapOf(1L to quotes))

        assertEquals(4, history.size)
        assertPoint(day(2), "900", "1000", history[0]) // quote of day 1 carried forward
        assertPoint(day(3), "1100", "1000", history[1])
        assertPoint(day(5), "1650", "1600", history[2]) // no quote that day: still 110
        assertPoint(day(6), "1560", "1210", history[3])
        // 900 -> 1560 with 210 of net new money: the rest is gain.
        assertEquals(0, BigDecimal("450").compareTo(gainOver(history)))
    }

    @Test
    fun `an asset without quotes is valued at its trade price`() {
        val history = portfolioHistory(listOf(trade(1, TransactionType.BUY, 2, "10", "100")), emptyMap())

        assertEquals(1, history.size)
        assertPoint(day(2), "1000", "1000", history[0])
    }
}
