package com.carloslafuente.finance.ui.magia

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carloslafuente.finance.data.ai.ApiKeyStore
import com.carloslafuente.finance.data.ai.ExtractionError
import com.carloslafuente.finance.data.ai.ExtractionResult
import com.carloslafuente.finance.data.ai.OrderExtractor
import com.carloslafuente.finance.data.ai.saveOrders
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.domain.ExtractedOrder
import com.carloslafuente.finance.ui.financeContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MagiaUiState(
    /** Null until the key store has been read. */
    val hasKey: Boolean? = null,
    val text: String = "",
    val images: List<Uri> = emptyList(),
    val working: Boolean = false,
    val error: ExtractionError? = null,
    /** Orders read by the model, waiting for the user's confirmation; null when there is nothing to review. */
    val orders: List<ExtractedOrder>? = null,
    val notes: String = "",
    /** Indices of [orders] the user wants to save. */
    val selected: Set<Int> = emptySet(),
    /** Account for the orders that do not name one. */
    val defaultAccount: String = "",
    val accounts: List<String> = emptyList(),
    /** How many transactions the last confirmation saved. */
    val saved: Int? = null,
) {
    val canExtract: Boolean get() = !working && (text.isNotBlank() || images.isNotEmpty())
    val needsAccount: Boolean get() = orders.orEmpty().filterIndexed { i, _ -> i in selected }.any { it.account == null }
    val canSave: Boolean get() = selected.isNotEmpty() && (!needsAccount || defaultAccount.isNotBlank())
}

class MagiaViewModel(
    private val db: AppDatabase,
    private val keyStore: ApiKeyStore,
    private val extractor: OrderExtractor,
) : ViewModel() {
    private val state = MutableStateFlow(MagiaUiState())

    val uiState: StateFlow<MagiaUiState> = combine(state, db.accountDao().observeAll()) { state, accounts ->
        val names = accounts.map { it.name }
        state.copy(accounts = names, defaultAccount = state.defaultAccount.ifEmpty { names.firstOrNull().orEmpty() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MagiaUiState())

    init {
        viewModelScope.launch { state.update { it.copy(hasKey = keyStore.load() != null) } }
    }

    fun saveKey(apiKey: String) = viewModelScope.launch {
        keyStore.save(apiKey.trim())
        state.update { it.copy(hasKey = true, error = null) }
    }

    fun forgetKey() = viewModelScope.launch {
        keyStore.clear()
        state.update { it.copy(hasKey = false) }
    }

    fun setText(text: String) = state.update { it.copy(text = text) }

    fun addImages(uris: List<Uri>) = state.update { it.copy(images = it.images + uris) }

    fun clearImages() = state.update { it.copy(images = emptyList()) }

    fun setDefaultAccount(name: String) = state.update { it.copy(defaultAccount = name) }

    fun toggle(index: Int) = state.update {
        it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index)
    }

    fun extract() {
        val current = state.value
        if (!current.canExtract) return
        state.update { it.copy(working = true, error = null, saved = null) }
        viewModelScope.launch {
            val apiKey = keyStore.load()
            val result = if (apiKey == null) {
                ExtractionResult.Failure(ExtractionError.INVALID_KEY)
            } else {
                extractor.extract(apiKey, current.text, current.images, LocalDate.now())
            }
            state.update {
                when (result) {
                    is ExtractionResult.Failure -> it.copy(working = false, error = result.error)
                    is ExtractionResult.Success -> it.copy(
                        working = false,
                        orders = result.extraction.orders,
                        notes = result.extraction.notes,
                        selected = result.extraction.orders.indices.filter { i -> result.extraction.orders[i].problem == null }.toSet(),
                    )
                }
            }
        }
    }

    fun discard() = state.update { it.copy(orders = null, notes = "", selected = emptySet()) }

    fun confirm() {
        val current = uiState.value
        val chosen = current.orders.orEmpty().filterIndexed { i, _ -> i in current.selected }
        if (!current.canSave) return
        viewModelScope.launch {
            saveOrders(db, chosen, current.defaultAccount, LocalDate.now())
            state.update { MagiaUiState(hasKey = true, saved = chosen.size) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = financeContainer()
                MagiaViewModel(container.database, container.apiKeyStore, container.orderExtractor)
            }
        }
    }
}
