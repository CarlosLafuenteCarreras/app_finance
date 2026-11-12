package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

private val MC = MathContext.DECIMAL64

/** A purchase not yet fully sold. [cost] includes the buy fee and shrinks as the lot is consumed. */
data class Lot(val acquiredOn: LocalDate, val quantity: BigDecimal, val cost: BigDecimal)

/** The part of a sale matched against one lot. [proceeds] is net of the sell fee. */
data class RealizedGain(
    val assetId: Long,
    val soldOn: LocalDate,
    val acquiredOn: LocalDate,
    val quantity: BigDecimal,
    val proceeds: BigDecimal,
    val cost: BigDecimal,
) {
    val gain: BigDecimal get() = proceeds - cost
}

data class HoldingKey(val accountId: Long, val assetId: Long)

data class Ledger(
    /**
     * Open lots per asset in FIFO order. They are pooled across accounts because Spanish tax
     * applies FIFO per security to everything the holder owns, wherever it is deposited.
     */
    val lots: Map<Long, List<Lot>>,
    val holdings: Map<HoldingKey, BigDecimal>,
    val cash: Map<Long, BigDecimal>,
    val realized: List<RealizedGain>,
    /** Ids of sales that sell more than the account held at that point. */
    val oversold: List<Long>,
)

/** Signed change in the account's cash balance caused by this transaction. */
fun TransactionEntity.cashEffect(): BigDecimal {
    val amount = amount ?: BigDecimal.ZERO
    return when (type) {
        TransactionType.DEPOSIT -> amount
        TransactionType.WITHDRAWAL, TransactionType.FEE -> -amount
        TransactionType.DIVIDEND, TransactionType.INTEREST -> amount - tax
        TransactionType.BUY -> -(quantity!! * price!! + fee)
        TransactionType.SELL -> quantity!! * price!! - fee
        TransactionType.SPLIT -> BigDecimal.ZERO
    }
}

fun buildLedger(transactions: List<TransactionEntity>): Ledger {
    val lots = mutableMapOf<Long, ArrayDeque<Lot>>()
    val holdings = mutableMapOf<HoldingKey, BigDecimal>()
    val cash = mutableMapOf<Long, BigDecimal>()
    val realized = mutableListOf<RealizedGain>()
    val oversold = mutableListOf<Long>()

    for (tx in transactions.sortedWith(compareBy({ it.date }, { it.id }))) {
        cash[tx.accountId] = (cash[tx.accountId] ?: BigDecimal.ZERO) + tx.cashEffect()
        when (tx.type) {
            TransactionType.DEPOSIT, TransactionType.WITHDRAWAL, TransactionType.FEE,
            TransactionType.DIVIDEND, TransactionType.INTEREST -> Unit

            TransactionType.BUY -> {
                val assetId = tx.assetId!!
                val quantity = tx.quantity!!
                val cost = quantity * tx.price!! + tx.fee
                lots.getOrPut(assetId) { ArrayDeque() }.addLast(Lot(tx.date, quantity, cost))
                val key = HoldingKey(tx.accountId, assetId)
                holdings[key] = (holdings[key] ?: BigDecimal.ZERO) + quantity
            }

            TransactionType.SELL -> {
                val assetId = tx.assetId!!
                val quantity = tx.quantity!!
                val proceeds = quantity * tx.price!! - tx.fee
                val key = HoldingKey(tx.accountId, assetId)
                val held = holdings[key] ?: BigDecimal.ZERO
                if (held < quantity) oversold += tx.id
                holdings[key] = held - quantity

                val queue = lots.getOrPut(assetId) { ArrayDeque() }
                var remaining = quantity
                while (remaining.signum() > 0 && queue.isNotEmpty()) {
                    val lot = queue.removeFirst()
                    val taken = remaining.min(lot.quantity)
                    val costTaken = lot.cost.multiply(taken, MC).divide(lot.quantity, MC)
                    realized += RealizedGain(
                        assetId = assetId,
                        soldOn = tx.date,
                        acquiredOn = lot.acquiredOn,
                        quantity = taken,
                        proceeds = proceeds.multiply(taken, MC).divide(quantity, MC),
                        cost = costTaken,
                    )
                    if (taken < lot.quantity) {
                        queue.addFirst(lot.copy(quantity = lot.quantity - taken, cost = lot.cost - costTaken))
                    }
                    remaining -= taken
                }
            }

            TransactionType.SPLIT -> {
                val assetId = tx.assetId!!
                val ratio = tx.quantity!!
                lots[assetId]?.let { queue ->
                    val scaled = queue.map { it.copy(quantity = it.quantity.multiply(ratio, MC)) }
                    queue.clear()
                    queue.addAll(scaled)
                }
                for (key in holdings.keys.filter { it.assetId == assetId }) {
                    holdings[key] = holdings.getValue(key).multiply(ratio, MC)
                }
            }
        }
    }

    return Ledger(
        lots = lots.filterValues { it.isNotEmpty() },
        holdings = holdings.filterValues { it.signum() != 0 },
        cash = cash,
        realized = realized,
        oversold = oversold,
    )
}
