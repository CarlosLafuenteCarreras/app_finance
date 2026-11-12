package com.carloslafuente.finance.ui.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SummaryTest {
    private val state = GestionUiState(
        loading = false,
        total = BigDecimal("2000"),
        accounts = listOf(AccountRow(1, "Trade Republic", BigDecimal("500"))),
        positions = listOf(
            PositionRow(1, "MSCI World", "IE00B4L5Y983", BigDecimal("10"), BigDecimal("1000"), BigDecimal("1200"), LocalDate.parse("2026-11-12")),
            PositionRow(2, "Letra", null, BigDecimal("3"), BigDecimal("300"), value = null, priceDate = null),
        ),
    )
    private val today = LocalDate.parse("2026-11-12")

    @Test
    fun `full summary carries weights, amounts and return`() {
        val nbsp = " "
        assertEquals(
            """
            # Mi cartera a 12/11/2026
            Patrimonio total: 2.000,00$nbsp€

            ## Posiciones
            - MSCI World (IE00B4L5Y983): peso 60,0 % · 10 participaciones · valor 1.200,00$nbsp€ · coste 1.000,00$nbsp€ · rentabilidad sobre coste +20,0 %
            - Letra: peso 15,0 % · 3 participaciones · valor 300,00$nbsp€ · coste 300,00$nbsp€ · sin precio de mercado

            ## Efectivo
            - Trade Republic: peso 25,0 % · 500,00$nbsp€
            """.trimIndent(),
            buildSummary(state, today, withAmounts = true),
        )
    }

    @Test
    fun `summary without amounts reveals no money figure`() {
        val summary = buildSummary(state, today, withAmounts = false)

        assertFalse(summary.contains("€"))
        assertFalse(summary.contains("participaciones"))
        assertEquals(
            "- MSCI World (IE00B4L5Y983): peso 60,0 % · rentabilidad sobre coste +20,0 %",
            summary.lines().first { it.startsWith("- MSCI") },
        )
    }
}
