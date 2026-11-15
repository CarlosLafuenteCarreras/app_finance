package com.carloslafuente.finance.data.prices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Fixtures are trimmed copies of real responses captured on 2026-11-12. */
class PriceParsersTest {

    @Test
    fun `justETF quote`() {
        val json = """{"latestQuote":{"raw":131.75,"localized":"131.75"},"latestQuoteDate":"2026-11-12",""" +
            """"previousQuote":{"raw":130.99,"localized":"130.99"},"quoteTradingVenue":"XETRA"}"""

        assertEquals(Quote(LocalDate.parse("2026-11-12"), BigDecimal("131.75")), parseJustEtfQuote(json))
    }

    @Test
    fun `LS Exchange instrument id`() {
        val json = """[{"id":41780,"displayname":"Apple Inc.","isin":"US0378331005","instrumentId":41780}]"""

        assertEquals(41780L, parseLsInstrumentId(json))
        assertNull(parseLsInstrumentId("[]"))
    }

    @Test
    fun `LS Exchange chart uses the most recent point of either series`() {
        val json = """{"info":{"isin":"US0378331005"},"series":{""" +
            """"intraday":{"id":"41780tradei","data":[[1794468720000,321.025],[1794468780000,321]]},""" +
            """"history":{"id":"41780tradeh","data":[[1794441600000,321.025],[1794520687000,320.875]]}}}"""

        assertEquals(Quote(LocalDate.parse("2026-11-12"), BigDecimal("320.875")), parseLsChart(json))
    }

    @Test
    fun `LS Exchange history keeps one close per day, oldest first`() {
        val json = """{"series":{"intraday":{"data":[[1794468720000,321.025]]},""" +
            """"history":{"data":[[1794355200000,320.775],[1794441600000,321.025],[1794520687000,320.875]]}}}"""

        assertEquals(
            listOf(
                Quote(LocalDate.parse("2026-11-11"), BigDecimal("320.775")),
                Quote(LocalDate.parse("2026-11-12"), BigDecimal("320.875")),
            ),
            parseLsHistory(json),
        )
        assertEquals(emptyList<Quote>(), parseLsHistory("<html>blocked</html>"))
    }

    @Test
    fun `FT tearsheet price and date`() {
        val html = """<span class="mod-ui-data-list__label" title="...">Price (EUR)</span>""" +
            """<span class="mod-ui-data-list__value">1,065.27</span></li>""" +
            """<div class="mod-disclaimer">Data delayed at least 15 minutes, as of Nov 11 2026.</div>"""

        assertEquals(Quote(LocalDate.parse("2026-11-11"), BigDecimal("1065.27")), parseFtTearsheet(html))
    }

    @Test
    fun `FT tearsheet in another currency is rejected`() {
        val html = """<span class="mod-ui-data-list__label">Price (USD)</span>""" +
            """<span class="mod-ui-data-list__value">65.27</span><div>as of Nov 11 2026.</div>"""

        assertNull(parseFtTearsheet(html))
    }

    @Test
    fun `unexpected responses yield no quote`() {
        assertNull(parseJustEtfQuote("<html>blocked</html>"))
        assertNull(parseLsChart("""{"series":{}}"""))
        assertNull(parseFtTearsheet("<html></html>"))
    }
}
