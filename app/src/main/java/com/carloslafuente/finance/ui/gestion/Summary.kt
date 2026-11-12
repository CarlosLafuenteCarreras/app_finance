package com.carloslafuente.finance.ui.gestion

import com.carloslafuente.finance.ui.formatDate
import com.carloslafuente.finance.ui.formatMoney
import com.carloslafuente.finance.ui.formatQuantity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val HUNDRED = BigDecimal(100)

private fun percent(part: BigDecimal, whole: BigDecimal, signed: Boolean = false): String {
    if (whole.signum() == 0) return "n/d"
    val value = part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP)
    val text = value.toPlainString().replace('.', ',')
    return (if (signed && value.signum() > 0) "+" else "") + text + " %"
}

/**
 * Plain-text portfolio summary meant to be pasted into an AI chat. With [withAmounts] false it
 * only carries weights and returns, so nothing reveals how much money there is.
 */
fun buildSummary(state: GestionUiState, today: LocalDate, withAmounts: Boolean): String = buildString {
    val total = state.total
    appendLine("# Mi cartera a ${formatDate(today)}")
    if (withAmounts) appendLine("Patrimonio total: ${formatMoney(total)}")
    appendLine()
    appendLine("## Posiciones")
    for (position in state.positions) {
        val value = position.value ?: position.cost
        val parts = buildList {
            add("peso ${percent(value, total)}")
            if (withAmounts) {
                add("${formatQuantity(position.quantity)} participaciones")
                add("valor ${formatMoney(value)}")
                add("coste ${formatMoney(position.cost)}")
            }
            val gain = position.gain
            add(if (gain != null) "rentabilidad sobre coste ${percent(gain, position.cost, signed = true)}" else "sin precio de mercado")
        }
        val isin = position.isin?.let { " ($it)" }.orEmpty()
        appendLine("- ${position.name}$isin: ${parts.joinToString(" · ")}")
    }
    appendLine()
    appendLine("## Efectivo")
    for (account in state.accounts) {
        val amount = if (withAmounts) " · ${formatMoney(account.cash)}" else ""
        appendLine("- ${account.name}: peso ${percent(account.cash, total)}$amount")
    }
}.trimEnd()
