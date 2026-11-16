package com.carloslafuente.finance.data.prices

import com.carloslafuente.finance.data.local.AssetType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

/** Daily closes from the `series.history.data` points of the LS Exchange chart JSON, oldest first. */
fun parseLsHistory(json: String): List<Quote> = runCatching {
    val zone = ZoneId.of("Europe/Berlin")
    Json.parseToJsonElement(json).jsonObject.getValue("series").jsonObject
        .getValue("history").jsonObject.getValue("data").jsonArray
        .map { point ->
            Quote(
                date = Instant.ofEpochMilli(point.jsonArray[0].jsonPrimitive.content.toLong()).atZone(zone).toLocalDate(),
                price = BigDecimal(point.jsonArray[1].jsonPrimitive.content),
            )
        }
        .associateBy { it.date }.values.sortedBy { it.date }
}.getOrDefault(emptyList())

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

private val ftXid = Regex("""xid&quot;:&quot;(\d+)&quot;""")

/** Internal id the FT chart API needs, embedded in the tearsheet HTML. */
fun parseFtXid(html: String): String? = ftXid.find(html)?.groupValues?.get(1)

/** FT chart series JSON: parallel `Dates` and `Elements[0].ComponentSeries[Type=Close].Values` arrays. */
fun parseFtSeries(json: String): List<Quote> = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    val dates = root.getValue("Dates").jsonArray
    val closes = root.getValue("Elements").jsonArray.first().jsonObject.getValue("ComponentSeries").jsonArray
        .first { it.jsonObject.getValue("Type").jsonPrimitive.content == "Close" }
        .jsonObject.getValue("Values").jsonArray
    dates.zip(closes) { date, close ->
        Quote(LocalDate.parse(date.jsonPrimitive.content.take(10)), BigDecimal(close.jsonPrimitive.content))
    }
}.getOrDefault(emptyList())

class PriceSources(private val client: OkHttpClient) {

    /** Tries the sources that fit the asset type in order; null when none of them has a usable price. */
    fun fetch(type: AssetType, isin: String): Quote? = when (type) {
        AssetType.FUND -> financialTimes(isin)
        AssetType.ETF -> justEtf(isin) ?: lsExchange(isin)
        AssetType.STOCK -> lsExchange(isin)
        AssetType.BOND -> null
    }

    /** Daily price history, oldest first; empty when it cannot be fetched. */
    fun history(type: AssetType, isin: String): List<Quote> = when (type) {
        AssetType.FUND -> financialTimesHistory(isin)
        AssetType.ETF, AssetType.STOCK -> lsExchangeHistory(isin)
        AssetType.BOND -> emptyList()
    }

    private fun lsExchangeHistory(isin: String): List<Quote> {
        val id = get("https://www.ls-tc.de/_rpc/json/.lstc/instrument/search/main?q=$isin&localeId=2")
            ?.let(::parseLsInstrumentId) ?: return emptyList()
        return get("https://www.ls-tc.de/_rpc/json/instrument/chart/dataForInstrument?instrumentId=$id")
            ?.let(::parseLsHistory).orEmpty()
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

    private fun financialTimesHistory(isin: String): List<Quote> {
        val xid = get("https://markets.ft.com/data/funds/tearsheet/summary?s=$isin:EUR")
            ?.takeIf { parseFtTearsheet(it) != null } // rejects share classes not quoted in EUR
            ?.let(::parseFtXid) ?: return emptyList()
        val body = """{"days":$HISTORY_DAYS,"dataPeriod":"Day","dataInterval":1,"realtime":false,""" +
            """"returnDateType":"ISO8601","elements":[{"Type":"price","Symbol":"$xid"}]}"""
        return get("https://markets.ft.com/data/chartapi/series", body)?.let(::parseFtSeries).orEmpty()
    }

    // These sites answer with a server error every now and then (FT returns 520), so a failed
    // request is tried again; a client error means the instrument is not there and is final.
    private fun get(url: String, jsonBody: String? = null): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .apply { if (jsonBody != null) post(jsonBody.toRequestBody("application/json".toMediaType())) }
            .build()
        repeat(ATTEMPTS) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return response.body.string()
                    if (response.code < 500) return null
                }
            } catch (e: IOException) {
                // Try again.
            }
        }
        return null
    }

    private companion object {
        const val ATTEMPTS = 3
        const val HISTORY_DAYS = 1825
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
    }
}
