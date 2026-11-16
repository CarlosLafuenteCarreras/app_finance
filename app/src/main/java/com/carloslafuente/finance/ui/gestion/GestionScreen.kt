package com.carloslafuente.finance.ui.gestion

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carloslafuente.finance.R
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.data.plan.PlanImportResult
import com.carloslafuente.finance.ui.ChartRange
import com.carloslafuente.finance.ui.ChartSection
import com.carloslafuente.finance.ui.EmptyState
import com.carloslafuente.finance.ui.Sparkline
import com.carloslafuente.finance.ui.formatDate
import com.carloslafuente.finance.ui.formatGain
import com.carloslafuente.finance.ui.displayMoney
import com.carloslafuente.finance.ui.formatPercent
import com.carloslafuente.finance.ui.formatQuantity
import com.carloslafuente.finance.ui.formatSignedMoney
import com.carloslafuente.finance.ui.gainColor
import com.carloslafuente.finance.ui.hideAmounts
import com.carloslafuente.finance.ui.valuePoints
import java.math.BigDecimal
import java.time.LocalDate

/** A price older than this is shown with its date so it is not mistaken for a current one. */
private const val STALE_AFTER_DAYS = 4L

/** Transactions listed before "show all". */
private const val RECENT_TRANSACTIONS = 5

@StringRes
fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.BUY -> R.string.type_buy
    TransactionType.SELL -> R.string.type_sell
    TransactionType.DIVIDEND -> R.string.type_dividend
    TransactionType.INTEREST -> R.string.type_interest
    TransactionType.DEPOSIT -> R.string.type_deposit
    TransactionType.WITHDRAWAL -> R.string.type_withdrawal
    TransactionType.FEE -> R.string.type_fee
    TransactionType.SPLIT -> R.string.type_split
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionScreen(
    onOpenTransaction: (Long) -> Unit,
    onOpenAsset: (Long) -> Unit,
    onOpenTaxReport: () -> Unit,
    onOpenProjection: (startValue: Double) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GestionViewModel = viewModel(factory = GestionViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(ChartRange.MAX) }
    var confirmImport by remember { mutableStateOf(false) }
    var allTransactions by rememberSaveable { mutableStateOf(false) }
    val points = remember(state.history, range) { valuePoints(state.history, range) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackup)
    }

    val planLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPlan)
    }

    val context = LocalContext.current
    fun copySummary(withAmounts: Boolean) {
        val summary = buildSummary(state, LocalDate.now(), withAmounts)
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Finance", summary))
    }

    Box(modifier.fillMaxSize()) {
        Column {
            TotalHeader(
                state = state,
                onRefresh = viewModel::refreshPrices,
                onCopySummary = ::copySummary,
                onExport = { exportLauncher.launch("finance-backup-" + LocalDate.now() + ".json") },
                onImport = { confirmImport = true },
                onOpenTaxReport = onOpenTaxReport,
                onOpenProjection = { onOpenProjection(state.positionsValue.toDouble()) },
                onImportPlan = { planLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
            )
            state.planImport?.let { PlanImportMessage(it) }
            state.backupMessage?.let {
                Text(stringResource(it), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (!state.loading && state.transactions.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.gestion_empty_title),
                    message = stringResource(R.string.gestion_empty_message),
                )
            } else PullToRefreshBox(isRefreshing = state.refresh.running, onRefresh = viewModel::refreshPrices) {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                if (state.positions.isNotEmpty()) {
                    item {
                        ChartSection(
                            points = points,
                            range = range,
                            onRange = { range = it },
                            fallbackValue = state.positionsValue,
                        )
                        if (points.size >= 2) ChartLegend()
                    }
                }
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val dayChange = state.dayChange
                        if (dayChange != null) {
                            StatTile(
                                R.string.gestion_last_day,
                                formatGain(dayChange, state.dayRate),
                                Modifier.weight(1f),
                                color = gainColor(dayChange.signum()),
                            )
                        }
                        StatTile(R.string.gestion_total_short, displayMoney(state.total), Modifier.weight(1f))
                    }
                }
                if (state.hasOversold) {
                    item { Warning(stringResource(R.string.gestion_oversold_warning)) }
                }
                if (state.refresh.failed.isNotEmpty()) {
                    item {
                        Warning(stringResource(R.string.gestion_prices_failed, state.refresh.failed.joinToString()))
                    }
                }
                state.gains?.let { gains ->
                    item { GainsCard(gains, state.performance) }
                }
                if (state.plan.isNotEmpty()) {
                    item { PlanSection(state.plan, onOpenAsset) }
                }
                if (state.positions.isNotEmpty()) {
                    item { SectionHeader(R.string.gestion_section_positions) }
                    items(state.positions, key = { "position-" + it.assetId }) { position ->
                        PositionItem(position, onClick = { onOpenAsset(position.assetId) })
                    }
                }
                item { SectionHeader(R.string.gestion_section_accounts) }
                items(state.accounts, key = { "account-" + it.id }) { account ->
                    ListItem(
                        headlineContent = { Text(account.name) },
                        trailingContent = { Text(displayMoney(account.cash), style = MaterialTheme.typography.bodyLarge) },
                    )
                }
                item { SectionHeader(R.string.gestion_section_transactions) }
                val shown = if (allTransactions) state.transactions else state.transactions.take(RECENT_TRANSACTIONS)
                items(shown, key = { "transaction-" + it.id }) { tx ->
                    TransactionItem(tx, showAsset = true, onClick = { onOpenTransaction(tx.id) })
                }
                if (state.transactions.size > RECENT_TRANSACTIONS) {
                    item {
                        TextButton(
                            onClick = { allTransactions = !allTransactions },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Text(
                                if (allTransactions) {
                                    stringResource(R.string.gestion_show_fewer_transactions)
                                } else {
                                    stringResource(R.string.gestion_show_all_transactions, state.transactions.size)
                                },
                            )
                        }
                    }
                }
                }
            }
        }
        FloatingActionButton(
            onClick = { onOpenTransaction(0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.gestion_add_transaction))
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }) { Text(stringResource(R.string.backup_import)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun TotalHeader(
    state: GestionUiState,
    onRefresh: () -> Unit,
    onCopySummary: (withAmounts: Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportPlan: () -> Unit,
    onOpenTaxReport: () -> Unit,
    onOpenProjection: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.gestion_portfolio),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { hideAmounts = !hideAmounts }) {
            Icon(
                if (hideAmounts) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = stringResource(
                    if (hideAmounts) R.string.gestion_show_amounts else R.string.gestion_hide_amounts,
                ),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.gestion_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.gestion_refresh_prices)) },
                    onClick = {
                        menuOpen = false
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tax_title)) },
                    onClick = {
                        menuOpen = false
                        onOpenTaxReport()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.projection_title)) },
                    onClick = {
                        menuOpen = false
                        onOpenProjection()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.summary_copy)) },
                    onClick = {
                        menuOpen = false
                        onCopySummary(true)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.summary_copy_without_amounts)) },
                    onClick = {
                        menuOpen = false
                        onCopySummary(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_import)) },
                    onClick = {
                        menuOpen = false
                        onImportPlan()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.backup_export)) },
                    onClick = {
                        menuOpen = false
                        onExport()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.backup_import)) },
                    onClick = {
                        menuOpen = false
                        onImport()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlanImportMessage(result: PlanImportResult) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (result.applied == 0 && result.unmatched.isEmpty()) {
            Text(stringResource(R.string.plan_import_failed), color = MaterialTheme.colorScheme.error)
        } else {
            Text(stringResource(R.string.plan_import_done, result.applied))
            if (result.unmatched.isNotEmpty()) {
                Text(stringResource(R.string.plan_import_unmatched, result.unmatched.joinToString()))
            }
        }
    }
}

@Composable
private fun StatTile(@StringRes label: Int, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

/** Names the two lines of a value chart; the dashed one is the money put in. */
@Composable
internal fun ChartLegend() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(width = 16.dp, height = 2.dp)) {
            drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
        }
        Text(stringResource(R.string.gestion_legend_value), style = MaterialTheme.typography.labelMedium, color = muted)
        Canvas(Modifier.padding(start = 10.dp).size(width = 16.dp, height = 2.dp)) {
            drawLine(
                muted, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
            )
        }
        Text(stringResource(R.string.gestion_legend_invested), style = MaterialTheme.typography.labelMedium, color = muted)
    }
}

@Composable
private fun GainsCard(gains: Gains, performance: Performance?) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.gestion_section_gains), style = MaterialTheme.typography.titleMedium)
        GainLine(R.string.gains_unrealized, gains.unrealized)
        GainLine(R.string.gains_realized, gains.realized)
        GainLine(R.string.gains_income, gains.income)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GainLine(R.string.gains_total, gains.unrealized + gains.realized + gains.income, emphasized = true)
        if (performance != null) PerformanceSummary(performance)
    }
}

@Composable
private fun GainLine(@StringRes label: Int, amount: BigDecimal, emphasized: Boolean = false) {
    DetailLine(
        label,
        formatSignedMoney(amount),
        color = if (amount.signum() == 0) Color.Unspecified else gainColor(amount.signum()),
        emphasized = emphasized,
    )
}

@Composable
private fun PerformanceSummary(performance: Performance) {
    Column {
        val benchmark = performance.benchmarkValue
        if (benchmark != null) {
            val difference = performance.positionsValue - benchmark
            Text(
                stringResource(R.string.performance_benchmark, displayMoney(benchmark)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    if (difference.signum() >= 0) R.string.performance_ahead else R.string.performance_behind,
                    displayMoney(difference.abs()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = gainColor(difference.signum()),
            )
        }
        performance.annualized?.let { rate ->
            val benchmarkRate = performance.benchmarkAnnualized
            Text(
                if (benchmarkRate != null) {
                    stringResource(R.string.performance_annualized_both, formatPercent(rate), formatPercent(benchmarkRate))
                } else {
                    stringResource(R.string.performance_annualized, formatPercent(rate))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Name and weight on the left, the last weeks' shape in the middle, value and total gain on the right. */
@Composable
private fun PositionItem(position: PositionRow, onClick: () -> Unit) {
    val gain = position.gain
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(position.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = stringResource(
                R.string.gestion_position_detail,
                formatQuantity(position.quantity),
                formatWeight(BigDecimal(position.weight * 100)),
            )
            val priceDate = position.priceDate
            Text(
                when {
                    priceDate == null -> detail + " · " + stringResource(R.string.gestion_no_price)
                    priceDate < LocalDate.now().minusDays(STALE_AFTER_DAYS) ->
                        detail + " · " + stringResource(R.string.gestion_price_from, formatDate(priceDate))
                    else -> detail
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Sparkline(position.spark, Modifier.size(width = 56.dp, height = 28.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(displayMoney(position.value ?: position.cost), style = MaterialTheme.typography.bodyLarge)
            if (gain != null) {
                Text(
                    formatGain(gain, gain.toDouble() / position.cost.toDouble()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = gainColor(gain.signum()),
                )
            }
        }
    }
}

@Composable
internal fun TransactionItem(tx: TransactionRow, showAsset: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            val type = stringResource(tx.type.labelRes())
            Text(if (showAsset && tx.assetName != null) type + " · " + tx.assetName else type)
        },
        supportingContent = { Text(formatDate(tx.date) + " · " + tx.accountName) },
        trailingContent = {
            if (tx.type != TransactionType.SPLIT) {
                Text(displayMoney(tx.cashEffect), style = MaterialTheme.typography.bodyLarge)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Warning(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SectionHeader(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
    )
}
