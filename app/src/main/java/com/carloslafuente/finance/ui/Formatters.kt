package com.carloslafuente.finance.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.carloslafuente.finance.AppContainer
import com.carloslafuente.finance.FinanceApplication
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** Shorthand used by every screen's `viewModelFactory { initializer { ... } }`. */
internal fun CreationExtras.financeContainer(): AppContainer =
    (this[APPLICATION_KEY] as FinanceApplication).container

private val spanish: Locale = Locale.forLanguageTag("es-ES")
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun formatMoney(value: BigDecimal): String =
    NumberFormat.getCurrencyInstance(spanish).apply { currency = Currency.getInstance("EUR") }.format(value)

fun formatQuantity(value: BigDecimal): String =
    DecimalFormat("#,##0.######", DecimalFormatSymbols(spanish)).format(value)

fun formatDate(date: LocalDate): String = date.format(dateFormat)

/** Accepts both "1234,56" and "1234.56"; with a comma present, dots are thousands separators. */
fun parseDecimal(text: String): BigDecimal? {
    val trimmed = text.trim()
    val normalized = if (',' in trimmed) trimmed.replace(".", "").replace(',', '.') else trimmed
    return normalized.toBigDecimalOrNull()
}
