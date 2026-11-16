package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.TransactionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

enum class OrderProblem { MISSING_ASSET, MISSING_QUANTITY, MISSING_PRICE, MISSING_AMOUNT }

/** A transaction read by the model from a screenshot or pasted text, before the user confirms it. */
data class ExtractedOrder(
    val type: TransactionType,
    val assetName: String?,
    val isin: String?,
    val assetType: AssetType?,
    /** Null when the source shows no date, as in a snapshot of current holdings. */
    val date: LocalDate?,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val amount: BigDecimal?,
    val fee: BigDecimal,
    val tax: BigDecimal,
    /** Broker or bank name when the source identifies it. */
    val account: String?,
) {
    /** Why this order cannot be saved as it is, or null if it can. */
    val problem: OrderProblem?
        get() = when {
            type.needsAsset && assetName.isNullOrBlank() -> OrderProblem.MISSING_ASSET
            type.usesQuantity && (quantity == null || quantity.signum() <= 0) -> OrderProblem.MISSING_QUANTITY
            type.usesPrice && (price == null || price.signum() <= 0) -> OrderProblem.MISSING_PRICE
            type.usesAmount && (amount == null || amount.signum() <= 0) -> OrderProblem.MISSING_AMOUNT
            else -> null
        }
}

data class Extraction(val orders: List<ExtractedOrder>, val notes: String)

/**
 * Reads the model's JSON answer (see the schema in OrderExtractor). Throws if it does not have
 * that shape. A buy or sale that only states the total gets its unit price from total / quantity.
 */
fun parseExtraction(json: String): Extraction {
    val root = Json.parseToJsonElement(json).jsonObject
    fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null }
    fun JsonObject.decimal(key: String): BigDecimal? = text(key)?.toBigDecimalOrNull()

    val orders = root.getValue("orders").jsonArray.map { element ->
        val row = element.jsonObject
        val type = TransactionType.valueOf(row.text("type")!!)
        val quantity = row.decimal("quantity")
        val amount = row.decimal("amount")
        ExtractedOrder(
            type = type,
            assetName = row.text("asset_name"),
            isin = row.text("isin")?.uppercase(),
            assetType = row.text("asset_type")?.let(AssetType::valueOf),
            date = row.text("date")?.let(LocalDate::parse),
            quantity = quantity,
            price = row.decimal("price")
                ?: if (type.usesPrice && amount != null && quantity != null && quantity.signum() > 0) {
                    amount.divide(quantity, MathContext.DECIMAL64)
                } else {
                    null
                },
            amount = amount,
            fee = row.decimal("fee") ?: BigDecimal.ZERO,
            tax = row.decimal("tax") ?: BigDecimal.ZERO,
            account = row.text("account"),
        )
    }
    return Extraction(orders, root.text("notes").orEmpty())
}
