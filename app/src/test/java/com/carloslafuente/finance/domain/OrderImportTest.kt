package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class OrderImportTest {

    @Test
    fun `reads orders, deriving the unit price from the total when it is missing`() {
        val json = """
            {"orders":[
              {"type":"BUY","asset_name":"NVIDIA","isin":"us67066g1040","asset_type":"STOCK","date":"2026-11-03",
               "quantity":2.5,"price":120.4,"amount":null,"fee":1,"tax":null,"account":"Trade Republic"},
              {"type":"BUY","asset_name":"Fidelity MSCI World","isin":null,"asset_type":"FUND","date":null,
               "quantity":40,"price":null,"amount":500,"fee":null,"tax":null,"account":null},
              {"type":"DIVIDEND","asset_name":"NVIDIA","isin":null,"asset_type":null,"date":"2026-10-01",
               "quantity":null,"price":null,"amount":3.2,"fee":null,"tax":0.48,"account":null}
            ],"notes":"La segunda no muestra fecha."}
        """.trimIndent()

        val extraction = parseExtraction(json)

        assertEquals("La segunda no muestra fecha.", extraction.notes)
        assertEquals(
            ExtractedOrder(
                TransactionType.BUY, "NVIDIA", "US67066G1040", AssetType.STOCK, LocalDate.parse("2026-11-03"),
                BigDecimal("2.5"), BigDecimal("120.4"), null, BigDecimal("1"), BigDecimal.ZERO, "Trade Republic",
            ),
            extraction.orders[0],
        )
        assertNull(extraction.orders[1].date)
        assertEquals(0, BigDecimal("12.5").compareTo(extraction.orders[1].price))
        assertEquals(BigDecimal("0.48"), extraction.orders[2].tax)
        assertEquals(listOf(null, null, null), extraction.orders.map { it.problem })
    }

    @Test
    fun `flags orders that lack what their type needs`() {
        fun order(type: TransactionType, asset: String?, quantity: String?, price: String?, amount: String?) = ExtractedOrder(
            type, asset, null, null, null, quantity?.let(::BigDecimal), price?.let(::BigDecimal), amount?.let(::BigDecimal),
            BigDecimal.ZERO, BigDecimal.ZERO, null,
        )

        assertEquals(OrderProblem.MISSING_ASSET, order(TransactionType.BUY, null, "1", "10", null).problem)
        assertEquals(OrderProblem.MISSING_QUANTITY, order(TransactionType.SELL, "Apple", null, "10", null).problem)
        assertEquals(OrderProblem.MISSING_PRICE, order(TransactionType.BUY, "Apple", "1", null, null).problem)
        assertEquals(OrderProblem.MISSING_AMOUNT, order(TransactionType.DEPOSIT, null, null, null, "0").problem)
        assertNull(order(TransactionType.DEPOSIT, null, null, null, "500").problem)
    }
}
