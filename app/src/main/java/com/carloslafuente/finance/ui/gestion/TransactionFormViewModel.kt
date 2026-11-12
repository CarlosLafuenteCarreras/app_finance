package com.carloslafuente.finance.ui.gestion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carloslafuente.finance.data.local.AccountEntity
import com.carloslafuente.finance.data.local.AccountKind
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.AssetEntity
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.domain.buildLedger
import com.carloslafuente.finance.ui.TRANSACTION_ID_ARG
import com.carloslafuente.finance.ui.financeContainer
import com.carloslafuente.finance.ui.parseDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

enum class FormError { ACCOUNT, ASSET, QUANTITY, PRICE, AMOUNT, FEE, TAX, OVERSOLD }

data class TransactionForm(
    val type: TransactionType = TransactionType.BUY,
    val accountId: Long? = null,
    val assetId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val quantity: String = "",
    val price: String = "",
    val amount: String = "",
    val fee: String = "",
    val tax: String = "",
    val note: String = "",
    val error: FormError? = null,
    val done: Boolean = false,
)

data class TransactionFormUiState(
    val isNew: Boolean,
    val form: TransactionForm = TransactionForm(),
    val accounts: List<AccountEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
)

class TransactionFormViewModel(private val db: AppDatabase, savedStateHandle: SavedStateHandle) : ViewModel() {
    private val transactionId: Long = savedStateHandle[TRANSACTION_ID_ARG] ?: 0L
    private val form = MutableStateFlow(TransactionForm())

    val uiState: StateFlow<TransactionFormUiState> = combine(
        form,
        db.accountDao().observeAll(),
        db.assetDao().observeAll(),
    ) { form, accounts, assets ->
        TransactionFormUiState(isNew = transactionId == 0L, form = form, accounts = accounts, assets = assets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionFormUiState(isNew = transactionId == 0L))

    init {
        if (transactionId != 0L) {
            viewModelScope.launch {
                val tx = db.transactionDao().getById(transactionId) ?: return@launch
                form.value = TransactionForm(
                    type = tx.type,
                    accountId = tx.accountId,
                    assetId = tx.assetId,
                    date = tx.date,
                    quantity = tx.quantity?.toPlainString().orEmpty(),
                    price = tx.price?.toPlainString().orEmpty(),
                    amount = tx.amount?.toPlainString().orEmpty(),
                    fee = tx.fee.takeIf { it.signum() != 0 }?.toPlainString().orEmpty(),
                    tax = tx.tax.takeIf { it.signum() != 0 }?.toPlainString().orEmpty(),
                    note = tx.note,
                )
            }
        }
    }

    fun edit(change: (TransactionForm) -> TransactionForm) = form.update { change(it).copy(error = null) }

    fun addAccount(name: String, kind: AccountKind) = viewModelScope.launch {
        val id = db.accountDao().insert(AccountEntity(name = name.trim(), kind = kind))
        edit { it.copy(accountId = id) }
    }

    fun addAsset(name: String, type: AssetType, isin: String) = viewModelScope.launch {
        val id = db.assetDao().insert(
            AssetEntity(name = name.trim(), type = type, isin = isin.trim().uppercase().ifEmpty { null }),
        )
        edit { it.copy(assetId = id) }
    }

    fun save() = viewModelScope.launch {
        val current = form.value
        val type = current.type
        val accountId = current.accountId ?: return@launch fail(FormError.ACCOUNT)
        val assetId = if (type.needsAsset) current.assetId ?: return@launch fail(FormError.ASSET) else null
        val quantity = if (type.usesQuantity) positive(current.quantity) ?: return@launch fail(FormError.QUANTITY) else null
        val price = if (type.usesPrice) positive(current.price) ?: return@launch fail(FormError.PRICE) else null
        val amount = if (type.usesAmount) positive(current.amount) ?: return@launch fail(FormError.AMOUNT) else null
        val fee = if (type.usesFee) optional(current.fee) ?: return@launch fail(FormError.FEE) else BigDecimal.ZERO
        val tax = if (type.usesTax) optional(current.tax) ?: return@launch fail(FormError.TAX) else BigDecimal.ZERO

        val tx = TransactionEntity(
            id = transactionId,
            accountId = accountId,
            assetId = assetId,
            type = type,
            date = current.date,
            quantity = quantity,
            price = price,
            amount = amount,
            fee = fee,
            tax = tax,
            note = current.note.trim(),
        )

        val others = db.transactionDao().getAll().filter { it.id != transactionId }
        val oversoldBefore = buildLedger(others).oversold.toSet()
        if (buildLedger(others + tx).oversold.any { it !in oversoldBefore }) return@launch fail(FormError.OVERSOLD)

        if (transactionId == 0L) db.transactionDao().insert(tx) else db.transactionDao().update(tx)
        form.update { it.copy(done = true) }
    }

    fun delete() = viewModelScope.launch {
        db.transactionDao().getById(transactionId)?.let { db.transactionDao().delete(it) }
        form.update { it.copy(done = true) }
    }

    private fun fail(error: FormError) = form.update { it.copy(error = error) }

    private fun positive(text: String): BigDecimal? = parseDecimal(text)?.takeIf { it.signum() > 0 }

    /** Blank means zero; null means the text is not a valid non-negative number. */
    private fun optional(text: String): BigDecimal? =
        if (text.isBlank()) BigDecimal.ZERO else parseDecimal(text)?.takeIf { it.signum() >= 0 }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TransactionFormViewModel(financeContainer().database, createSavedStateHandle()) }
        }
    }
}
