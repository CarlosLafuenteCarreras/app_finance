package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class LedgerTest {
    private var nextId = 1L
    private val broker = 1L
    private val otherBroker = 2L
    private val asset = 10L

    private fun tx(
        type: TransactionType,
        date: String,
        account: Long = broker,
        assetId: Long? = asset,
        quantity: String? = null,
        price: String? = null,
        amount: String? = null,
        fee: String = "0",
        tax: String = "0",
    ) = TransactionEntity(
        id = nextId++,
        accountId = account,
        assetId = assetId,
        type = type,
        date = LocalDate.parse(date),
        quantity = quantity?.let(::BigDecimal),
        price = price?.let(::BigDecimal),
        amount = amount?.let(::BigDecimal),
        fee = BigDecimal(fee),
        tax = BigDecimal(tax),
    )

    private fun assertMoney(expected: String, actual: BigDecimal?) =
        assertEquals(BigDecimal(expected), actual!!.setScale(2, RoundingMode.HALF_UP))

    @Test
    fun `sale consumes the oldest lots first and fees reduce the gain`() {
        val ledger = buildLedger(
            listOf(
                tx(TransactionType.BUY, "2024-01-10", quantity = "10", price = "100", fee = "1"),
                tx(TransactionType.BUY, "2024-06-10", quantity = "10", price = "200", fee = "1"),
                tx(TransactionType.SELL, "2025-02-01", quantity = "15", price = "300", fee = "3"),
            ),
        )

        // Net proceeds 4497, split 10/15 and 5/15 between the two lots.
        assertEquals(2, ledger.realized.size)
        val (first, second) = ledger.realized
        assertEquals(LocalDate.parse("2024-01-10"), first.acquiredOn)
        assertMoney("1001.00", first.cost)
        assertMoney("2998.00", first.proceeds)
        assertEquals(LocalDate.parse("2024-06-10"), second.acquiredOn)
        assertMoney("1000.50", second.cost)
        assertMoney("1499.00", second.proceeds)

        val open = ledger.lots.getValue(asset).single()
        assertEquals(0, BigDecimal("5").compareTo(open.quantity))
        assertMoney("1000.50", open.cost)
        assertEquals(0, BigDecimal("5").compareTo(ledger.holdings.getValue(HoldingKey(broker, asset))))
    }

    @Test
    fun `fifo is applied per asset across accounts`() {
        val ledger = buildLedger(
            listOf(
                tx(TransactionType.BUY, "2024-01-10", account = broker, quantity = "5", price = "100"),
                tx(TransactionType.BUY, "2024-03-10", account = otherBroker, quantity = "5", price = "150"),
                tx(TransactionType.SELL, "2025-01-10", account = otherBroker, quantity = "5", price = "200"),
            ),
        )

        // The shares sold sit in the second account, but the tax lot consumed is the oldest one.
        assertMoney("500.00", ledger.realized.single().cost)
        assertMoney("750.00", ledger.lots.getValue(asset).single().cost)
        assertEquals(setOf(HoldingKey(broker, asset)), ledger.holdings.keys)
        assertEquals(emptyList<Long>(), ledger.oversold)
    }

    @Test
    fun `split scales quantities and keeps the cost basis`() {
        val ledger = buildLedger(
            listOf(
                tx(TransactionType.BUY, "2024-01-10", quantity = "2", price = "1000"),
                tx(TransactionType.SPLIT, "2024-05-01", quantity = "10"),
                tx(TransactionType.SELL, "2024-09-01", quantity = "5", price = "150"),
            ),
        )

        assertMoney("500.00", ledger.realized.single().cost)
        assertMoney("250.00", ledger.realized.single().gain)
        assertEquals(0, BigDecimal("15").compareTo(ledger.holdings.getValue(HoldingKey(broker, asset))))
        assertMoney("1500.00", ledger.lots.getValue(asset).single().cost)
    }

    @Test
    fun `cash balance follows every kind of movement`() {
        val ledger = buildLedger(
            listOf(
                tx(TransactionType.DEPOSIT, "2024-01-01", assetId = null, amount = "1000"),
                tx(TransactionType.BUY, "2024-01-02", quantity = "3", price = "100", fee = "1"),
                tx(TransactionType.DIVIDEND, "2024-03-01", amount = "10", tax = "1.90"),
                tx(TransactionType.INTEREST, "2024-04-01", assetId = null, amount = "5", tax = "0.95"),
                tx(TransactionType.FEE, "2024-05-01", assetId = null, amount = "2"),
                tx(TransactionType.SELL, "2024-06-01", quantity = "1", price = "120", fee = "1"),
                tx(TransactionType.WITHDRAWAL, "2024-07-01", assetId = null, amount = "200"),
            ),
        )

        // 1000 - 301 + 8.10 + 4.05 - 2 + 119 - 200
        assertMoney("628.15", ledger.cash[broker])
    }

    @Test
    fun `selling more than the account holds is flagged`() {
        val buy = tx(TransactionType.BUY, "2024-01-10", quantity = "5", price = "100")
        val sell = tx(TransactionType.SELL, "2024-02-10", quantity = "8", price = "100")

        assertEquals(listOf(sell.id), buildLedger(listOf(buy, sell)).oversold)
    }

    @Test
    fun `transactions are processed by date regardless of input order`() {
        val sell = tx(TransactionType.SELL, "2024-02-10", quantity = "5", price = "120")
        val buy = tx(TransactionType.BUY, "2024-01-10", quantity = "5", price = "100")

        val ledger = buildLedger(listOf(sell, buy))

        assertEquals(emptyList<Long>(), ledger.oversold)
        assertMoney("100.00", ledger.realized.single().gain)
    }
}
