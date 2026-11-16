package com.carloslafuente.finance.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.carloslafuente.finance.data.local.AccountEntity
import com.carloslafuente.finance.data.local.AccountKind
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.AssetEntity
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.PlanRole
import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.LocalDate

/** Everything the user typed by hand. Prices are left out: they can be fetched again. */
data class Backup(
    val accounts: List<AccountEntity>,
    val assets: List<AssetEntity>,
    val transactions: List<TransactionEntity>,
)

private const val FORMAT_VERSION = 1
private val prettyJson = Json { prettyPrint = true }

fun Backup.toJson(): String = prettyJson.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put("version", FORMAT_VERSION)
        put("accounts", buildJsonArray {
            accounts.forEach {
                add(buildJsonObject {
                    put("id", it.id)
                    put("name", it.name)
                    put("kind", it.kind.name)
                })
            }
        })
        put("assets", buildJsonArray {
            assets.forEach {
                add(buildJsonObject {
                    put("id", it.id)
                    put("name", it.name)
                    put("type", it.type.name)
                    put("isin", it.isin)
                    put("planRole", it.planRole?.name)
                    put("planWeight", it.planWeight?.toPlainString())
                })
            }
        })
        put("transactions", buildJsonArray {
            transactions.forEach {
                add(buildJsonObject {
                    put("id", it.id)
                    put("accountId", it.accountId)
                    put("assetId", it.assetId)
                    put("type", it.type.name)
                    put("date", it.date.toString())
                    // Decimals travel as strings so no precision is lost.
                    put("quantity", it.quantity?.toPlainString())
                    put("price", it.price?.toPlainString())
                    put("amount", it.amount?.toPlainString())
                    put("fee", it.fee.toPlainString())
                    put("tax", it.tax.toPlainString())
                    put("note", it.note)
                })
            }
        })
    },
)

/** Throws if [json] is not a backup written by [toJson]. */
fun parseBackup(json: String): Backup {
    val root = Json.parseToJsonElement(json).jsonObject
    require(root.getValue("version").jsonPrimitive.content.toInt() == FORMAT_VERSION) { "Unsupported backup version" }

    fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
    fun JsonObject.textOrNull(key: String): String? = getValue(key).jsonPrimitive.contentOrNull
    fun rows(key: String): List<JsonObject> = root.getValue(key).jsonArray.map { it.jsonObject }

    return Backup(
        accounts = rows("accounts").map {
            AccountEntity(id = it.text("id").toLong(), name = it.text("name"), kind = AccountKind.valueOf(it.text("kind")))
        },
        assets = rows("assets").map {
            AssetEntity(
                id = it.text("id").toLong(),
                name = it.text("name"),
                type = AssetType.valueOf(it.text("type")),
                isin = it.textOrNull("isin"),
                // Absent in backups written before the plan existed.
                planRole = it["planRole"]?.jsonPrimitive?.contentOrNull?.let(PlanRole::valueOf),
                planWeight = it["planWeight"]?.jsonPrimitive?.contentOrNull?.let(::BigDecimal),
            )
        },
        transactions = rows("transactions").map {
            TransactionEntity(
                id = it.text("id").toLong(),
                accountId = it.text("accountId").toLong(),
                assetId = it.textOrNull("assetId")?.toLong(),
                type = TransactionType.valueOf(it.text("type")),
                date = LocalDate.parse(it.text("date")),
                quantity = it.textOrNull("quantity")?.let(::BigDecimal),
                price = it.textOrNull("price")?.let(::BigDecimal),
                amount = it.textOrNull("amount")?.let(::BigDecimal),
                fee = BigDecimal(it.text("fee")),
                tax = BigDecimal(it.text("tax")),
                note = it.text("note"),
            )
        },
    )
}

class BackupRepository(private val db: AppDatabase, private val contentResolver: ContentResolver) {

    /** Returns false when the file could not be written. */
    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val backup = Backup(db.accountDao().getAll(), db.assetDao().getAll(), db.transactionDao().getAll())
        runCatching {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(backup.toJson().toByteArray()) }
        }.isSuccess
    }

    /** Replaces all current data with the file's. Returns false, changing nothing, if the file is not a valid backup. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val backup = runCatching {
            parseBackup(contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
        }.getOrNull() ?: return@withContext false
        db.withTransaction {
            db.transactionDao().deleteAll()
            db.assetDao().deleteAll()
            db.accountDao().deleteAll()
            db.accountDao().insertAll(backup.accounts)
            db.assetDao().insertAll(backup.assets)
            db.transactionDao().insertAll(backup.transactions)
        }
        true
    }
}
