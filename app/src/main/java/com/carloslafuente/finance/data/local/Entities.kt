package com.carloslafuente.finance.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

enum class AccountKind { BROKER, BANK }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AccountKind,
)

enum class AssetType { STOCK, ETF, FUND, BOND }

/** The asset's place in the investment plan. Outside-plan assets are personal bets that do not count for its weights. */
enum class PlanRole { CORE, SATELLITE, OUTSIDE }

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AssetType,
    val isin: String? = null,
    val planRole: PlanRole? = null,
    /** Target weight in percent; for an asset outside the plan, the most it may weigh. */
    val planWeight: BigDecimal? = null,
)

/** Which form fields each kind of transaction uses; all money is in EUR, as charged by the broker. */
enum class TransactionType(
    val needsAsset: Boolean = false,
    val usesQuantity: Boolean = false,
    val usesPrice: Boolean = false,
    val usesAmount: Boolean = false,
    val usesFee: Boolean = false,
    val usesTax: Boolean = false,
) {
    BUY(needsAsset = true, usesQuantity = true, usesPrice = true, usesFee = true),
    SELL(needsAsset = true, usesQuantity = true, usesPrice = true, usesFee = true),
    DIVIDEND(needsAsset = true, usesAmount = true, usesTax = true),
    INTEREST(usesAmount = true, usesTax = true),
    DEPOSIT(usesAmount = true),
    WITHDRAWAL(usesAmount = true),
    FEE(usesAmount = true),

    /** [TransactionEntity.quantity] holds the ratio: new shares per old share. */
    SPLIT(needsAsset = true, usesQuantity = true),
}

/**
 * The ledger is the single source of truth: positions, cash and gains are always derived from
 * these rows, never stored.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("assetId"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val assetId: Long? = null,
    val type: TransactionType,
    val date: LocalDate,
    val quantity: BigDecimal? = null,
    /** Unit price for BUY/SELL. */
    val price: BigDecimal? = null,
    /** Gross cash amount for the types with [TransactionType.usesAmount]. */
    val amount: BigDecimal? = null,
    val fee: BigDecimal = BigDecimal.ZERO,
    /** Tax withheld at source on dividends and interest. */
    val tax: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
)

/** Latest known price per asset and day, in EUR; fetched from the network or typed by hand. */
@Entity(
    tableName = "prices",
    primaryKeys = ["assetId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PriceEntity(
    val assetId: Long,
    val date: LocalDate,
    val price: BigDecimal,
)
