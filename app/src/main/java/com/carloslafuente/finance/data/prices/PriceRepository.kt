package com.carloslafuente.finance.data.prices

import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.PriceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

class PriceRepository(private val db: AppDatabase, private val sources: PriceSources) {

    /** Fetches a fresh price for every asset with an ISIN. Returns the names of the assets that got none. */
    suspend fun refresh(): List<String> = withContext(Dispatchers.IO) {
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
