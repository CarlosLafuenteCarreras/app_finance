package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Spanish savings-income brackets (base del ahorro) in force since 2025: upper limit to rate. */
private val SAVINGS_BRACKETS = listOf(
    BigDecimal("6000") to BigDecimal("0.19"),
    BigDecimal("50000") to BigDecimal("0.21"),
    BigDecimal("200000") to BigDecimal("0.23"),
    BigDecimal("300000") to BigDecimal("0.27"),
    null to BigDecimal("0.30"),
)

/** Tax due on a positive savings base. */
fun savingsTax(base: BigDecimal): BigDecimal {
    var tax = BigDecimal.ZERO
    var lower = BigDecimal.ZERO
    for ((upper, rate) in SAVINGS_BRACKETS) {
        if (base <= lower) break
        val slice = (if (upper == null) base else base.min(upper)) - lower
        tax += slice * rate
        lower = upper ?: break
    }
    return tax.setScale(2, RoundingMode.HALF_UP)
}

data class SaleSimulation(
    /** Net of the sell fee. */
    val proceeds: BigDecimal,
    val cost: BigDecimal,
    /** Extra tax this sale adds on top of the gains already realized in the year. */
    val tax: BigDecimal,
    /**
     * The sale is at a loss and shares bought in the previous two months would remain, so the loss
     * could not be deducted yet (art. 33.5.f LIRPF).
     */
    val lossDeferred: Boolean,
) {
    val gain: BigDecimal get() = proceeds - cost
    val afterTax: BigDecimal get() = proceeds - tax
}

/**
 * What selling [quantity] of [assetId] today would leave after tax, applying FIFO over the whole
 * ledger. Returns null if that many shares are not held.
 *
 * Simplified: only this year's realized gains and losses are netted; dividends, interest and
 * losses carried from earlier years are not considered.
 */
fun simulateSale(
    transactions: List<TransactionEntity>,
    assetId: Long,
    quantity: BigDecimal,
    price: BigDecimal,
    fee: BigDecimal,
    today: LocalDate,
): SaleSimulation? {
    val before = buildLedger(transactions)
    val held = before.lots[assetId].orEmpty().sumOf { it.quantity }
    if (quantity.signum() <= 0 || quantity > held) return null

    val accountId = before.holdings.keys.first { it.assetId == assetId }.accountId
    val sale = TransactionEntity(
        id = Long.MAX_VALUE,
        accountId = accountId,
        assetId = assetId,
        type = TransactionType.SELL,
        date = today,
        quantity = quantity,
        price = price,
        fee = fee,
    )
    val after = buildLedger(transactions + sale)
    val matched = after.realized.drop(before.realized.size)
    val proceeds = matched.sumOf { it.proceeds }
    val cost = matched.sumOf { it.cost }
    val gain = proceeds - cost

    val earlierThisYear = before.realized.filter { it.soldOn.year == today.year }.sumOf { it.gain }
    val tax = savingsTax((earlierThisYear + gain).max(BigDecimal.ZERO)) - savingsTax(earlierThisYear.max(BigDecimal.ZERO))

    val twoMonthsAgo = today.minusMonths(2)
    return SaleSimulation(
        proceeds = proceeds,
        cost = cost,
        tax = tax.max(BigDecimal.ZERO),
        lossDeferred = gain.signum() < 0 && after.lots[assetId].orEmpty().any { it.acquiredOn >= twoMonthsAgo },
    )
}
