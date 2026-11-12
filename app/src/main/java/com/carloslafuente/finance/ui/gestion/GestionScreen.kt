package com.carloslafuente.finance.ui.gestion

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carloslafuente.finance.R
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.ui.EmptyState
import com.carloslafuente.finance.ui.formatDate
import com.carloslafuente.finance.ui.formatMoney
import com.carloslafuente.finance.ui.formatQuantity

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

    Box(modifier.fillMaxSize()) {
        if (!state.loading && state.transactions.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.gestion_empty_title),
                message = stringResource(R.string.gestion_empty_message),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                if (state.hasOversold) {
                    item {
                        Text(
                            stringResource(R.string.gestion_oversold_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
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
                        ListItem(
                            headlineContent = { Text(position.name) },
                            supportingContent = {
                                Text(stringResource(R.string.gestion_position_quantity, formatQuantity(position.quantity)))
                            },
                            trailingContent = {
                                Text(formatMoney(position.cost), style = MaterialTheme.typography.bodyLarge)
                            },
                        )
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
