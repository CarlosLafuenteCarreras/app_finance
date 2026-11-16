package com.carloslafuente.finance.data.prices

import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.PriceEntity
import com.carloslafuente.finance.data.local.AssetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

/** iShares Core MSCI World (accumulating): the yardstick the portfolio is compared against. */
private const val BENCHMARK_ISIN = "IE00B4L5Y983"
private const val HISTORY_YEARS = 5L

class PriceRepository(private val db: AppDatabase, private val sources: PriceSources) {
    private val _benchmark = MutableStateFlow<List<Quote>>(emptyList())

    /** Daily history of the benchmark, kept in memory only; empty until the first successful refresh. */
    val benchmark: StateFlow<List<Quote>> = _benchmark

    /** Fetches a fresh price for every asset with an ISIN. Returns the names of the assets that got none. */
    suspend fun refresh(): List<String> = withContext(Dispatchers.IO) {
        coroutineScope {
            val benchmark = async { sources.history(AssetType.ETF, BENCHMARK_ISIN) }
            val failed = db.assetDao().getAll().map { asset ->
                async {
                    val isin = asset.isin ?: return@async null
                    val oldest = LocalDate.now().minusYears(HISTORY_YEARS)
                    // The latest quote goes last so it wins over the history's close for the same day.
                    val quotes = sources.history(asset.type, isin).filter { it.date >= oldest } +
                        listOfNotNull(sources.fetch(asset.type, isin))
                    db.priceDao().upsertAll(quotes.map { PriceEntity(asset.id, it.date, it.price) })
                    asset.name.takeIf { quotes.isEmpty() }
                }
            }.awaitAll().filterNotNull()
            benchmark.await().takeIf { it.isNotEmpty() }?.let { _benchmark.value = it }
            failed
        }
    }

    suspend fun setManualPrice(assetId: Long, price: BigDecimal) =
        db.priceDao().upsert(PriceEntity(assetId, LocalDate.now(), price))
}
