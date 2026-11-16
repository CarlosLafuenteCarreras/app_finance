package com.carloslafuente.finance.ui.gestion

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carloslafuente.finance.R
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.PlanRole
import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.prices.Quote
import com.carloslafuente.finance.domain.simulateSale
import com.carloslafuente.finance.ui.ChartPoint
import com.carloslafuente.finance.ui.ChartRange
import com.carloslafuente.finance.ui.ChartSection
import com.carloslafuente.finance.ui.formatGain
import com.carloslafuente.finance.ui.formatMoney
import com.carloslafuente.finance.ui.formatQuantity
import com.carloslafuente.finance.ui.gainColor
import com.carloslafuente.finance.ui.parseDecimal
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

@StringRes
private fun AssetType.labelRes(): Int = when (this) {
    AssetType.STOCK -> R.string.asset_stock
    AssetType.ETF -> R.string.asset_etf
    AssetType.FUND -> R.string.asset_fund
    AssetType.BOND -> R.string.asset_bond
}

/** Price points of [range]; gain and rate are the change since the first visible day. */
private fun pricePoints(quotes: List<Quote>, range: ChartRange): List<ChartPoint> {
    val start = range.start(LocalDate.now())
    val visible = if (start == null) quotes else quotes.filter { it.date >= start }
    val first = visible.firstOrNull()?.price ?: return emptyList()
    return visible.map {
        val gain = it.price - first
        ChartPoint(it.date, it.price, gain, gain.toDouble() / first.toDouble())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: AssetViewModel = viewModel(factory = AssetViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(ChartRange.ONE_YEAR) }
    var simulating by remember { mutableStateOf(false) }
    var pricing by remember { mutableStateOf(false) }
    var planning by remember { mutableStateOf(false) }
    val points = remember(state.quotes, range) { pricePoints(state.quotes, range) }
    val position = state.position

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            item {
                Text(
                    listOfNotNull(state.type?.let { stringResource(it.labelRes()) }, state.isin).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                ChartSection(
                    points = points,
                    range = range,
                    onRange = { range = it },
                    fallbackValue = position?.price,
                    showBaseline = true,
                    markers = state.tradeDates.filter { points.isNotEmpty() && it >= points.first().date },
                )
                if (state.tradeDates.isNotEmpty() && points.size >= 2) {
                    Text(
                        stringResource(R.string.asset_chart_markers),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item {
                SectionHeader(R.string.asset_section_position)
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (position == null) {
                        Text(
                            stringResource(R.string.asset_no_position),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        DetailLine(R.string.asset_quantity, formatQuantity(position.quantity))
                        DetailLine(
                            R.string.asset_average_price,
                            formatMoney(position.cost.divide(position.quantity, MathContext.DECIMAL64)),
                        )
                        DetailLine(R.string.asset_cost, formatMoney(position.cost))
                        position.value?.let { DetailLine(R.string.asset_value, formatMoney(it)) }
                        position.gain?.let { gain ->
                            DetailLine(
                                R.string.asset_gain,
                                formatGain(gain, gain.toDouble() / position.cost.toDouble()),
                                color = gainColor(gain.signum()),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (position != null) {
                            Button(onClick = { simulating = true }) { Text(stringResource(R.string.asset_simulate_sale)) }
                        }
                        OutlinedButton(onClick = { pricing = true }) { Text(stringResource(R.string.sale_set_price)) }
                    }
                }
            }
            item {
                SectionHeader(R.string.plan_section)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine(R.string.plan_role, stringResource(state.planRole.labelRes()))
                    state.planWeight?.let {
                        DetailLine(
                            if (state.planRole == PlanRole.OUTSIDE) R.string.plan_ceiling_label else R.string.plan_target_label,
                            formatWeight(it),
                        )
                    }
                    OutlinedButton(onClick = { planning = true }) { Text(stringResource(R.string.plan_edit)) }
                }
            }
            item { SectionHeader(R.string.gestion_section_transactions) }
            items(state.transactions, key = { it.id }) { tx ->
                TransactionItem(tx, showAsset = false, onClick = { onOpenTransaction(tx.id) })
            }
        }
    }

    if (simulating && position != null) {
        SaleSimulatorDialog(position, state.ledgerTransactions, onDismiss = { simulating = false })
    }
    if (planning) {
        PlanDialog(
            assetName = state.name,
            role = state.planRole,
            weight = state.planWeight,
            onDismiss = { planning = false },
            onConfirm = { role, weight ->
                viewModel.setPlan(role, weight)
                planning = false
            },
        )
    }
    if (pricing) {
        ManualPriceDialog(
            assetName = state.name,
            onDismiss = { pricing = false },
            onConfirm = { price ->
                viewModel.setManualPrice(price)
                pricing = false
            },
        )
    }
}

@Composable
internal fun DetailLine(@StringRes label: Int, value: String, color: Color = Color.Unspecified, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth()) {
        Text(
            stringResource(label),
            style = style,
            color = if (emphasized) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = style, color = color)
    }
}

@Composable
private fun SaleSimulatorDialog(position: PositionRow, transactions: List<TransactionEntity>, onDismiss: () -> Unit) {
    var quantityText by remember { mutableStateOf(position.quantity.stripTrailingZeros().toPlainString()) }
    var priceText by remember { mutableStateOf(position.price?.toPlainString().orEmpty()) }
    val quantity = parseDecimal(quantityText)
    val price = parseDecimal(priceText)?.takeIf { it.signum() > 0 }
    val simulation = if (quantity != null && price != null) {
        simulateSale(transactions, position.assetId, quantity, price, BigDecimal.ZERO, LocalDate.now())
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sale_title, position.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(stringResource(R.string.form_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.form_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (simulation == null) {
                    Text(stringResource(R.string.sale_invalid), color = MaterialTheme.colorScheme.error)
                } else {
                    DetailLine(R.string.sale_proceeds, formatMoney(simulation.proceeds))
                    DetailLine(R.string.sale_cost, formatMoney(simulation.cost))
                    DetailLine(R.string.sale_gain, formatMoney(simulation.gain))
                    DetailLine(R.string.sale_tax, formatMoney(simulation.tax))
                    DetailLine(R.string.sale_after_tax, formatMoney(simulation.afterTax), emphasized = true)
                    if (simulation.lossDeferred) {
                        Text(stringResource(R.string.sale_loss_deferred), color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    stringResource(R.string.sale_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun ManualPriceDialog(assetName: String, onDismiss: () -> Unit, onConfirm: (BigDecimal) -> Unit) {
    var text by remember { mutableStateOf("") }
    val price = parseDecimal(text)?.takeIf { it.signum() > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(assetName) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.gestion_manual_price)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { price?.let(onConfirm) }, enabled = price != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
