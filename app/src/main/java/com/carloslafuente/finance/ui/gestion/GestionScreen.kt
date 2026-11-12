package com.carloslafuente.finance.ui.gestion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carloslafuente.finance.R
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.ui.EmptyState
import com.carloslafuente.finance.ui.formatDate
import com.carloslafuente.finance.ui.formatMoney
import com.carloslafuente.finance.ui.formatQuantity
import com.carloslafuente.finance.ui.parseDecimal
import java.time.LocalDate

/** A price older than this is shown with its date so it is not mistaken for a current one. */
private const val STALE_AFTER_DAYS = 4L

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

@Composable
fun GestionScreen(
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GestionViewModel = viewModel(factory = GestionViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pricedPosition by remember { mutableStateOf<PositionRow?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackup)
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
                onExport = { exportLauncher.launch("finance-backup-${LocalDate.now()}.json") },
                onImport = { confirmImport = true },
            )
            state.backupMessage?.let {
                Text(stringResource(it), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (!state.loading && state.transactions.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.gestion_empty_title),
                    message = stringResource(R.string.gestion_empty_message),
                )
            } else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                if (state.hasOversold) {
                    item { Warning(stringResource(R.string.gestion_oversold_warning)) }
                }
                if (state.refresh.failed.isNotEmpty()) {
                    item {
                        Warning(stringResource(R.string.gestion_prices_failed, state.refresh.failed.joinToString()))
                    }
                }
                item { SectionHeader(R.string.gestion_section_accounts) }
                items(state.accounts, key = { "account-${it.id}" }) { account ->
                    ListItem(
                        headlineContent = { Text(account.name) },
                        trailingContent = { Text(formatMoney(account.cash), style = MaterialTheme.typography.bodyLarge) },
                    )
                }
                if (state.positions.isNotEmpty()) {
                    item { SectionHeader(R.string.gestion_section_positions) }
                    items(state.positions, key = { "position-${it.assetId}" }) { position ->
                        PositionItem(position, onClick = { pricedPosition = position })
                    }
                }
                item { SectionHeader(R.string.gestion_section_transactions) }
                items(state.transactions, key = { "transaction-${it.id}" }) { tx ->
                    ListItem(
                        headlineContent = {
                            val type = stringResource(tx.type.labelRes())
                            Text(if (tx.assetName != null) "$type · ${tx.assetName}" else type)
                        },
                        supportingContent = { Text("${formatDate(tx.date)} · ${tx.accountName}") },
                        trailingContent = {
                            if (tx.type != TransactionType.SPLIT) {
                                Text(formatMoney(tx.cashEffect), style = MaterialTheme.typography.bodyLarge)
                            }
                        },
                        modifier = Modifier.clickable { onOpenTransaction(tx.id) },
                    )
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

    pricedPosition?.let { position ->
        ManualPriceDialog(
            assetName = position.name,
            onDismiss = { pricedPosition = null },
            onConfirm = { price ->
                viewModel.setManualPrice(position.assetId, price)
                pricedPosition = null
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
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.gestion_total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatMoney(state.total), style = MaterialTheme.typography.headlineMedium)
        }
        if (state.refresh.running) {
            CircularProgressIndicator(Modifier.padding(12.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.gestion_refresh_prices))
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.gestion_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
private fun PositionItem(position: PositionRow, onClick: () -> Unit) {
    val gain = position.gain
    ListItem(
        headlineContent = { Text(position.name) },
        supportingContent = {
            val quantity = stringResource(R.string.gestion_position_quantity, formatQuantity(position.quantity))
            val priceDate = position.priceDate
            Text(
                when {
                    priceDate == null -> "$quantity · ${stringResource(R.string.gestion_no_price)}"
                    priceDate < LocalDate.now().minusDays(STALE_AFTER_DAYS) ->
                        "$quantity · ${stringResource(R.string.gestion_price_from, formatDate(priceDate))}"
                    else -> quantity
                },
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(position.value ?: position.cost), style = MaterialTheme.typography.bodyLarge)
                if (gain != null) {
                    Text(
                        (if (gain.signum() >= 0) "+" else "") + formatMoney(gain),
                        color = if (gain.signum() >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ManualPriceDialog(assetName: String, onDismiss: () -> Unit, onConfirm: (java.math.BigDecimal) -> Unit) {
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
private fun SectionHeader(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}
