package com.carloslafuente.finance.ui.gestion

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carloslafuente.finance.R
import com.carloslafuente.finance.domain.RealizedGain
import com.carloslafuente.finance.domain.TaxReport
import com.carloslafuente.finance.domain.project
import com.carloslafuente.finance.domain.taxReport
import com.carloslafuente.finance.ui.LineChart
import com.carloslafuente.finance.ui.displayMoney
import com.carloslafuente.finance.ui.formatDate
import com.carloslafuente.finance.ui.formatMoney
import com.carloslafuente.finance.ui.formatQuantity
import com.carloslafuente.finance.ui.formatSignedMoney
import com.carloslafuente.finance.ui.gainColor
import com.carloslafuente.finance.ui.parseDecimal
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding -> content(Modifier.padding(innerPadding)) }
}

/** Plain text of the report, for pasting into the tax return notes or an email to an adviser. */
private fun TaxReport.asText(assetNames: Map<Long, String>): String = buildString {
    appendLine("Informe fiscal $year")
    appendLine()
    appendLine("Ganancias y pérdidas patrimoniales")
    for (sale in sales) {
        appendLine(
            "- ${assetNames[sale.assetId]}: ${formatQuantity(sale.quantity)} uds., compra ${formatDate(sale.acquiredOn)}, " +
                "venta ${formatDate(sale.soldOn)}, adquisición ${formatMoney(sale.cost)}, transmisión ${formatMoney(sale.proceeds)}, " +
                "resultado ${formatMoney(sale.gain)}",
        )
    }
    appendLine("Total transmisión: ${formatMoney(proceeds)} · adquisición: ${formatMoney(cost)} · resultado: ${formatMoney(gain)}")
    appendLine()
    appendLine("Rendimientos del capital mobiliario")
    appendLine("Dividendos: ${formatMoney(dividends)} · intereses: ${formatMoney(interest)} · retenciones: ${formatMoney(withheld)}")
}.trimEnd()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaxReportScreen(onBack: () -> Unit, viewModel: GestionViewModel = viewModel(factory = GestionViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transactions = state.ledgerTransactions
    val years = remember(transactions) {
        (transactions.map { it.date.year } + LocalDate.now().year).distinct().sortedDescending()
    }
    var year by rememberSaveable { mutableStateOf(LocalDate.now().year) }
    val report = remember(transactions, year) { taxReport(transactions, year) }
    val assetNames = remember(state.transactions) {
        val nameByTransaction = state.transactions.associate { it.id to it.assetName.orEmpty() }
        transactions.filter { it.assetId != null }.associate { it.assetId!! to nameByTransaction.getValue(it.id) }
    }
    val context = LocalContext.current

    ReportScaffold(stringResource(R.string.tax_title), onBack) { modifier ->
        LazyColumn(modifier) {
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.forEach { option ->
                        FilterChip(selected = option == year, onClick = { year = option }, label = { Text(option.toString()) })
                    }
                }
                SectionHeader(R.string.tax_section_summary)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine(R.string.tax_proceeds, displayMoney(report.proceeds))
                    DetailLine(R.string.tax_cost, displayMoney(report.cost))
                    DetailLine(R.string.tax_gain, formatSignedMoney(report.gain), color = gainColor(report.gain.signum()))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailLine(R.string.tax_dividends, displayMoney(report.dividends))
                    DetailLine(R.string.tax_interest, displayMoney(report.interest))
                    DetailLine(R.string.tax_withheld, displayMoney(report.withheld))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailLine(R.string.tax_estimated, displayMoney(report.estimatedTax))
                    DetailLine(
                        R.string.tax_to_pay,
                        displayMoney((report.estimatedTax - report.withheld).max(BigDecimal.ZERO)),
                        emphasized = true,
                    )
                    Text(
                        stringResource(R.string.tax_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Finance", report.asText(assetNames)))
                        },
                    ) { Text(stringResource(R.string.tax_copy)) }
                }
                SectionHeader(R.string.tax_section_sales)
                if (report.sales.isEmpty()) {
                    Text(
                        stringResource(R.string.tax_no_sales),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(report.sales) { sale -> SaleLot(sale, assetNames[sale.assetId].orEmpty()) }
        }
    }
}

@Composable
private fun SaleLot(sale: RealizedGain, assetName: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(assetName, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.tax_sale_detail,
                    formatQuantity(sale.quantity), formatDate(sale.acquiredOn), formatDate(sale.soldOn),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatSignedMoney(sale.gain), style = MaterialTheme.typography.bodyLarge, color = gainColor(sale.gain.signum()))
    }
}

private val SCENARIOS = listOf(0.03, 0.06, 0.09)
private val HORIZONS = listOf(5, 10, 20, 30)

/** "What if I keep contributing": three fixed return scenarios over [HORIZONS] years. */
@Composable
fun ProjectionScreen(startValue: Double, onBack: () -> Unit) {
    var monthlyText by rememberSaveable { mutableStateOf("1000") }
    var scenario by rememberSaveable { mutableStateOf(1) }
    val monthly = parseDecimal(monthlyText)?.takeIf { it.signum() >= 0 }?.toDouble() ?: 0.0
    val points = remember(startValue, monthly, scenario) { project(startValue, monthly, SCENARIOS[scenario], HORIZONS.last()) }
    var scrub by remember(points) { mutableStateOf<Int?>(null) }
    val shown = points[scrub ?: points.lastIndex]

    ReportScaffold(stringResource(R.string.projection_title), onBack) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = monthlyText,
                    onValueChange = { monthlyText = it },
                    label = { Text(stringResource(R.string.projection_monthly)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SCENARIOS.forEachIndexed { index, rate ->
                        FilterChip(
                            selected = index == scenario,
                            onClick = { scenario = index },
                            label = { Text(stringResource(R.string.projection_rate, (rate * 100).toInt())) },
                        )
                    }
                }
                Text(displayMoney(BigDecimal(shown.value).setScale(0, java.math.RoundingMode.HALF_UP)), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(
                        R.string.projection_detail,
                        shown.year,
                        displayMoney(BigDecimal(shown.contributed).setScale(0, java.math.RoundingMode.HALF_UP)),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LineChart(
                values = points.map { it.value.toFloat() },
                lineColor = MaterialTheme.colorScheme.tertiary,
                scrub = scrub,
                onScrub = { scrub = it },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                reference = points.map { it.contributed.toFloat() },
            )
            ChartLegend()
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HORIZONS.forEach { years ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.projection_years, years),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            displayMoney(BigDecimal(points[years].value).setScale(0, java.math.RoundingMode.HALF_UP)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Text(
                    stringResource(R.string.projection_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
