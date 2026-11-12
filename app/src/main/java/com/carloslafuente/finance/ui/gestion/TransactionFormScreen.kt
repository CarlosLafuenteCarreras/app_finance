package com.carloslafuente.finance.ui.gestion

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carloslafuente.finance.R
import com.carloslafuente.finance.data.local.AccountKind
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.ui.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(
    onDone: () -> Unit,
    viewModel: TransactionFormViewModel = viewModel(factory = TransactionFormViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    val type = form.type

    LaunchedEffect(form.done) { if (form.done) onDone() }

    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showAssetDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.form_title_new else R.string.form_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { option ->
                    FilterChip(
                        selected = option == type,
                        onClick = { viewModel.edit { it.copy(type = option) } },
                        label = { Text(stringResource(option.labelRes())) },
                    )
                }
            }

            Picker(
                label = R.string.form_account,
                selected = state.accounts.firstOrNull { it.id == form.accountId }?.name,
                options = state.accounts.map { it.id to it.name },
                onSelect = { id -> viewModel.edit { it.copy(accountId = id) } },
                addLabel = R.string.form_new_account,
                onAdd = { showAccountDialog = true },
            )

            if (type.needsAsset) {
                Picker(
                    label = R.string.form_asset,
                    selected = state.assets.firstOrNull { it.id == form.assetId }?.name,
                    options = state.assets.map { it.id to it.name },
                    onSelect = { id -> viewModel.edit { it.copy(assetId = id) } },
                    addLabel = R.string.form_new_asset,
                    onAdd = { showAssetDialog = true },
                )
            }

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.form_date, formatDate(form.date)))
            }

            if (type.usesQuantity) {
                DecimalField(
                    label = if (type == TransactionType.SPLIT) R.string.form_split_ratio else R.string.form_quantity,
                    value = form.quantity,
                    onChange = { text -> viewModel.edit { it.copy(quantity = text) } },
                )
            }
            if (type.usesPrice) {
                DecimalField(R.string.form_price, form.price) { text -> viewModel.edit { it.copy(price = text) } }
            }
            if (type.usesAmount) {
                DecimalField(R.string.form_amount, form.amount) { text -> viewModel.edit { it.copy(amount = text) } }
            }
            if (type.usesFee) {
                DecimalField(R.string.form_fee, form.fee) { text -> viewModel.edit { it.copy(fee = text) } }
            }
            if (type.usesTax) {
                DecimalField(R.string.form_tax, form.tax) { text -> viewModel.edit { it.copy(tax = text) } }
            }
            OutlinedTextField(
                value = form.note,
                onValueChange = { text -> viewModel.edit { it.copy(note = text) } },
                label = { Text(stringResource(R.string.form_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            form.error?.let { error ->
                Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error)
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.edit { it.copy(date = date) }
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }

    if (showAccountDialog) {
        NewItemDialog(
            title = R.string.form_new_account,
            kinds = AccountKind.entries,
            kindLabel = { it.labelRes() },
            onDismiss = { showAccountDialog = false },
            onConfirm = { name, kind, _ ->
                viewModel.addAccount(name, kind)
                showAccountDialog = false
            },
        )
    }
    if (showAssetDialog) {
        NewItemDialog(
            title = R.string.form_new_asset,
            kinds = AssetType.entries,
            kindLabel = { it.labelRes() },
            withIsin = true,
            onDismiss = { showAssetDialog = false },
            onConfirm = { name, kind, isin ->
                viewModel.addAsset(name, kind, isin)
                showAssetDialog = false
            },
        )
    }
}

@StringRes
private fun FormError.messageRes(): Int = when (this) {
    FormError.ACCOUNT -> R.string.error_account
    FormError.ASSET -> R.string.error_asset
    FormError.QUANTITY -> R.string.error_quantity
    FormError.PRICE -> R.string.error_price
    FormError.AMOUNT -> R.string.error_amount
    FormError.FEE -> R.string.error_fee
    FormError.TAX -> R.string.error_tax
    FormError.OVERSOLD -> R.string.error_oversold
}

@StringRes
private fun AccountKind.labelRes(): Int = when (this) {
    AccountKind.BROKER -> R.string.account_broker
    AccountKind.BANK -> R.string.account_bank
}

@StringRes
private fun AssetType.labelRes(): Int = when (this) {
    AssetType.STOCK -> R.string.asset_stock
    AssetType.ETF -> R.string.asset_etf
    AssetType.FUND -> R.string.asset_fund
    AssetType.BOND -> R.string.asset_bond
}

@Composable
private fun DecimalField(@StringRes label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Picker(
    @StringRes label: Int,
    selected: String?,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    @StringRes addLabel: Int,
    onAdd: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${stringResource(label)}: ${selected ?: stringResource(R.string.form_none_selected)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(addLabel)) },
                onClick = {
                    onAdd()
                    expanded = false
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <K> NewItemDialog(
    @StringRes title: Int,
    kinds: List<K>,
    kindLabel: (K) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, kind: K, isin: String) -> Unit,
    withIsin: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    var isin by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(kinds.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.form_name)) },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { option ->
                        FilterChip(
                            selected = option == kind,
                            onClick = { kind = option },
                            label = { Text(stringResource(kindLabel(option))) },
                        )
                    }
                }
                if (withIsin) {
                    OutlinedTextField(
                        value = isin,
                        onValueChange = { isin = it },
                        label = { Text(stringResource(R.string.form_isin)) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, kind, isin) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
