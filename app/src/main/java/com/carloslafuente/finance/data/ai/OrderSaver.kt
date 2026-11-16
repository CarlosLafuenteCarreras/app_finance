package com.carloslafuente.finance.data.ai

import androidx.room.withTransaction
import com.carloslafuente.finance.data.local.AccountEntity
import com.carloslafuente.finance.data.local.AccountKind
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.AssetEntity
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.domain.ExtractedOrder
import java.time.LocalDate

/**
 * Saves confirmed orders as transactions, creating the accounts and assets they mention that do
 * not exist yet. Accounts match by name; assets by ISIN, or by name when there is none.
 * Orders without an account go to [defaultAccount]; orders without a date are dated [today].
 */
suspend fun saveOrders(db: AppDatabase, orders: List<ExtractedOrder>, defaultAccount: String, today: LocalDate) {
    db.withTransaction {
        val accounts = db.accountDao().getAll().associate { it.name.lowercase() to it.id }.toMutableMap()
        val assets = db.assetDao().getAll().toMutableList()

        for (order in orders) {
            val accountName = (order.account ?: defaultAccount).trim()
            val accountId = accounts.getOrPut(accountName.lowercase()) {
                db.accountDao().insert(AccountEntity(name = accountName, kind = AccountKind.BROKER))
            }
            val assetId = order.assetName?.takeIf { order.type.needsAsset }?.let { name ->
                val existing = assets.firstOrNull {
                    if (order.isin != null && it.isin != null) it.isin == order.isin else it.name.equals(name, ignoreCase = true)
                }
                existing?.id ?: run {
                    val asset = AssetEntity(name = name.trim(), type = order.assetType ?: AssetType.STOCK, isin = order.isin)
                    db.assetDao().insert(asset).also { assets += asset.copy(id = it) }
                }
            }
            db.transactionDao().insert(
                TransactionEntity(
                    accountId = accountId,
                    assetId = assetId,
                    type = order.type,
                    date = order.date ?: today,
                    quantity = order.quantity.takeIf { order.type.usesQuantity },
                    price = order.price.takeIf { order.type.usesPrice },
                    amount = order.amount.takeIf { order.type.usesAmount },
                    fee = order.fee,
                    tax = order.tax,
                ),
            )
        }
    }
}
