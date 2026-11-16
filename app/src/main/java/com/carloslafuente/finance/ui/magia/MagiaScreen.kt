package com.carloslafuente.finance.ui.magia

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carloslafuente.finance.R
import com.carloslafuente.finance.data.ai.ExtractionError
import com.carloslafuente.finance.domain.ExtractedOrder
import com.carloslafuente.finance.domain.OrderProblem
import com.carloslafuente.finance.ui.formatDate
import com.carloslafuente.finance.ui.formatMoney
import com.carloslafuente.finance.ui.formatQuantity
import com.carloslafuente.finance.ui.gestion.labelRes

@StringRes
private fun ExtractionError.messageRes(): Int = when (this) {
    ExtractionError.INVALID_KEY -> R.string.magia_error_key
    ExtractionError.RATE_LIMITED -> R.string.magia_error_rate
    ExtractionError.NETWORK -> R.string.magia_error_network
    ExtractionError.DECLINED -> R.string.magia_error_declined
    ExtractionError.UNREADABLE -> R.string.magia_error_unreadable
}

@StringRes
private fun OrderProblem.messageRes(): Int = when (this) {
    OrderProblem.MISSING_ASSET -> R.string.magia_problem_asset
    OrderProblem.MISSING_QUANTITY -> R.string.magia_problem_quantity
    OrderProblem.MISSING_PRICE -> R.string.magia_problem_price
    OrderProblem.MISSING_AMOUNT -> R.string.magia_problem_amount
}

@Composable
fun MagiaScreen(modifier: Modifier = Modifier, viewModel: MagiaViewModel = viewModel(factory = MagiaViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.tab_magia), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        when {
            state.hasKey == false -> KeyCard(onSave = viewModel::saveKey)
            state.orders != null -> ReviewCard(state, viewModel)
            state.hasKey == true -> ImportCard(state, viewModel)
        }
        if (state.hasKey == true && state.orders == null) {
            TextButton(onClick = viewModel::forgetKey) { Text(stringResource(R.string.magia_key_forget)) }
        }
        Text(
            stringResource(R.string.magia_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun KeyCard(onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Card {
        Text(stringResource(R.string.magia_key_title), style = MaterialTheme.typography.titleMedium)
        Muted(stringResource(R.string.magia_key_message))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.magia_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun ImportCard(state: MagiaUiState, viewModel: MagiaViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }
    Card {
        Text(stringResource(R.string.magia_import_title), style = MaterialTheme.typography.titleMedium)
        Muted(stringResource(R.string.magia_import_message))
        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::setText,
            label = { Text(stringResource(R.string.magia_import_text)) },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) { Text(stringResource(R.string.magia_import_add_images)) }
            if (state.images.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.magia_import_images, state.images.size, state.images.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = viewModel::clearImages) { Text(stringResource(R.string.magia_import_clear_images)) }
            }
        }
        if (state.working) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Muted(stringResource(R.string.magia_import_working))
        }
        state.error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        state.saved?.let { Text(pluralStringResource(R.plurals.magia_saved, it, it), color = MaterialTheme.colorScheme.tertiary) }
        Button(onClick = viewModel::extract, enabled = state.canExtract) { Text(stringResource(R.string.magia_import_extract)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(state: MagiaUiState, viewModel: MagiaViewModel) {
    val orders = state.orders.orEmpty()
    Card {
        Text(stringResource(R.string.magia_review_title), style = MaterialTheme.typography.titleMedium)
        if (orders.isEmpty()) Muted(stringResource(R.string.magia_review_empty))
        if (state.notes.isNotBlank()) Muted(state.notes)
        orders.forEachIndexed { index, order ->
            OrderRow(order, checked = index in state.selected, onToggle = { viewModel.toggle(index) })
        }
        if (state.needsAccount) {
            OutlinedTextField(
                value = state.defaultAccount,
                onValueChange = viewModel::setDefaultAccount,
                label = { Text(stringResource(R.string.magia_review_account)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.forEach { name ->
                    FilterChip(
                        selected = name == state.defaultAccount,
                        onClick = { viewModel.setDefaultAccount(name) },
                        label = { Text(name) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::confirm, enabled = state.canSave) {
                Text(pluralStringResource(R.plurals.magia_review_save, state.selected.size, state.selected.size))
            }
            OutlinedButton(onClick = viewModel::discard) { Text(stringResource(R.string.magia_review_discard)) }
        }
    }
}

@Composable
private fun OrderRow(order: ExtractedOrder, checked: Boolean, onToggle: () -> Unit) {
    val problem = order.problem
    Row(
        Modifier.fillMaxWidth().clickable(enabled = problem == null, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = problem == null)
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(stringResource(order.type.labelRes()), order.assetName).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
            val quantity = order.quantity
            val price = order.price
            val amount = order.amount
            Muted(
                listOfNotNull(
                    order.date?.let(::formatDate) ?: stringResource(R.string.magia_review_no_date),
                    if (order.type.usesQuantity && quantity != null && price != null) {
                        formatQuantity(quantity) + " × " + formatMoney(price)
                    } else {
                        null
                    },
                    amount?.takeIf { order.type.usesAmount }?.let(::formatMoney),
                    order.isin,
                    order.account,
                ).joinToString(" · "),
            )
            if (problem != null) {
                Text(
                    stringResource(problem.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
