package com.carloslafuente.finance.data.prices

import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.PriceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

/** iShares Core MSCI World (accumulating): the yardstick the portfolio is compared against. */
private const val BENCHMARK_ISIN = "IE00B4L5Y983"

class PriceRepository(private val db: AppDatabase, private val sources: PriceSources) {
    private val _benchmark = MutableStateFlow<List<Quote>>(emptyList())

    /** Daily history of the benchmark, kept in memory only; empty until the first successful refresh. */
    val benchmark: StateFlow<List<Quote>> = _benchmark

    /** Fetches a fresh price for every asset with an ISIN. Returns the names of the assets that got none. */
    suspend fun refresh(): List<String> = withContext(Dispatchers.IO) {
        sources.history(BENCHMARK_ISIN).takeIf { it.isNotEmpty() }?.let { _benchmark.value = it }
        db.assetDao().getAll().mapNotNull { asset ->
            val isin = asset.isin ?: return@mapNotNull null
            val quote = sources.fetch(asset.type, isin)
            if (quote == null) {
                asset.name
            } else {
                db.priceDao().upsert(PriceEntity(asset.id, quote.date, quote.price))
                null
            }
        }
    }

    suspend fun setManualPrice(assetId: Long, price: BigDecimal) =
        db.priceDao().upsert(PriceEntity(assetId, LocalDate.now(), price))
}
