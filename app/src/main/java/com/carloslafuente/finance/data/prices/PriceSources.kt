package com.carloslafuente.finance.data.prices

import com.carloslafuente.finance.data.local.AssetType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A price in EUR and the day it refers to. */
data class Quote(val date: LocalDate, val price: BigDecimal)

// The sources below are undocumented endpoints of public sites. Any of them can change without
// notice, so every parser returns null instead of guessing when the response looks different.

/** justETF quote JSON: `{"latestQuote":{"raw":131.75},"latestQuoteDate":"2026-11-12",...}`. */
fun parseJustEtfQuote(json: String): Quote? = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    Quote(
        date = LocalDate.parse(root.getValue("latestQuoteDate").jsonPrimitive.content),
        price = BigDecimal(root.getValue("latestQuote").jsonObject.getValue("raw").jsonPrimitive.content),
    )
}.getOrNull()

/** LS Exchange search JSON: `[{"instrumentId":41780,...}]`. */
fun parseLsInstrumentId(json: String): Long? = runCatching {
    Json.parseToJsonElement(json).jsonArray.first().jsonObject.getValue("instrumentId").jsonPrimitive.content.toLong()
}.getOrNull()

/** LS Exchange chart JSON: `series.intraday.data` and `series.history.data` hold `[epochMillis, price]` points. */
fun parseLsChart(json: String): Quote? = runCatching {
    val series = Json.parseToJsonElement(json).jsonObject.getValue("series").jsonObject
    val point = listOf("intraday", "history")
        .mapNotNull { series[it]?.jsonObject?.get("data")?.jsonArray?.lastOrNull()?.jsonArray }
        .maxBy { it[0].jsonPrimitive.content.toLong() }
    Quote(
        date = Instant.ofEpochMilli(point[0].jsonPrimitive.content.toLong()).atZone(ZoneId.of("Europe/Berlin")).toLocalDate(),
        price = BigDecimal(point[1].jsonPrimitive.content),
    )
}.getOrNull()

private val ftPrice = Regex("""Price \(EUR\)</span><span class="mod-ui-data-list__value">([\d.,]+)<""")
private val ftDate = Regex("""as of ([A-Z][a-z]{2} \d{1,2} \d{4})""")
private val ftDateFormat = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)

/** Financial Times fund tearsheet HTML; only accepted when the price is quoted in EUR. */
fun parseFtTearsheet(html: String): Quote? = runCatching {
    Quote(
        date = LocalDate.parse(ftDate.find(html)!!.groupValues[1], ftDateFormat),
        price = BigDecimal(ftPrice.find(html)!!.groupValues[1].replace(",", "")),
    )
}.getOrNull()

class PriceSources(private val client: OkHttpClient) {

    /** Tries the sources that fit the asset type in order; null when none of them has a usable price. */
    fun fetch(type: AssetType, isin: String): Quote? = when (type) {
        AssetType.FUND -> financialTimes(isin)
        AssetType.ETF -> justEtf(isin) ?: lsExchange(isin)
        AssetType.STOCK -> lsExchange(isin)
        AssetType.BOND -> null
    }

    private fun justEtf(isin: String): Quote? =
        get("https://www.justetf.com/api/etfs/$isin/quote?locale=en&currency=EUR&isin=$isin")?.let(::parseJustEtfQuote)

    // LS Exchange is the venue Trade Republic executes on, so this is the price the broker shows.
    private fun lsExchange(isin: String): Quote? {
        val id = get("https://www.ls-tc.de/_rpc/json/.lstc/instrument/search/main?q=$isin&localeId=2")
            ?.let(::parseLsInstrumentId) ?: return null
        return get("https://www.ls-tc.de/_rpc/json/instrument/chart/dataForInstrument?instrumentId=$id")?.let(::parseLsChart)
    }

    private fun financialTimes(isin: String): Quote? =
        get("https://markets.ft.com/data/funds/tearsheet/summary?s=$isin:EUR")?.let(::parseFtTearsheet)

    private fun get(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response -> if (response.isSuccessful) response.body.string() else null }
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
    }
}
