package com.carloslafuente.finance.data.plan

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.AssetEntity
import com.carloslafuente.finance.domain.parsePlanMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [applied] products now carry the plan's role and weight; [unmatched] had no ISIN and no asset with their name. */
data class PlanImportResult(val applied: Int, val unmatched: List<String>)

class PlanImporter(private val db: AppDatabase, private val contentResolver: ContentResolver) {

    /**
     * Applies the plan document's product table to the assets, creating the ones that do not exist.
     * Assets the document does not mention keep whatever was set by hand.
     */
    suspend fun import(uri: Uri): PlanImportResult = withContext(Dispatchers.IO) {
        val products = runCatching {
            parsePlanMarkdown(contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
        }.getOrDefault(emptyList())
        val unmatched = mutableListOf<String>()
        var applied = 0
        db.withTransaction {
            val assets = db.assetDao().getAll()
            for (product in products) {
                val existing = assets.firstOrNull {
                    if (product.isin != null) it.isin == product.isin else it.name.equals(product.name, ignoreCase = true)
                }
                when {
                    existing != null ->
                        db.assetDao().update(existing.copy(planRole = product.role, planWeight = product.weight))
                    product.isin != null ->
                        db.assetDao().insert(
                            AssetEntity(
                                name = product.name, type = product.type, isin = product.isin,
                                planRole = product.role, planWeight = product.weight,
                            ),
                        )
                    else -> {
                        unmatched += product.name
                        continue
                    }
                }
                applied++
            }
        }
        PlanImportResult(applied, unmatched)
    }
}
