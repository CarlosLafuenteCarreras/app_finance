package com.carloslafuente.finance.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.domain.buildLedger
import com.carloslafuente.finance.domain.cashEffect
import com.carloslafuente.finance.ui.financeContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate

data class AccountRow(val id: Long, val name: String, val cash: BigDecimal)

data class PositionRow(val assetId: Long, val name: String, val quantity: BigDecimal, val cost: BigDecimal)

data class TransactionRow(
    val id: Long,
    val date: LocalDate,
    val type: TransactionType,
    val assetName: String?,
    val accountName: String,
    val cashEffect: BigDecimal,
)

data class GestionUiState(
    val loading: Boolean = true,
    val accounts: List<AccountRow> = emptyList(),
    val positions: List<PositionRow> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    val hasOversold: Boolean = false,
)

class GestionViewModel(db: AppDatabase) : ViewModel() {

    val uiState: StateFlow<GestionUiState> = combine(
        db.accountDao().observeAll(),
        db.assetDao().observeAll(),
        db.transactionDao().observeAll(),
    ) { accounts, assets, transactions ->
        val ledger = buildLedger(transactions)
        val accountNames = accounts.associate { it.id to it.name }
        val assetNames = assets.associate { it.id to it.name }
        GestionUiState(
            loading = false,
            accounts = accounts.map { AccountRow(it.id, it.name, ledger.cash[it.id] ?: BigDecimal.ZERO) },
            positions = ledger.lots.map { (assetId, lots) ->
                PositionRow(
                    assetId = assetId,
                    name = assetNames.getValue(assetId),
                    quantity = lots.sumOf { it.quantity },
                    cost = lots.sumOf { it.cost },
                )
            }.sortedByDescending { it.cost },
            transactions = transactions.asReversed().map {
                TransactionRow(
                    id = it.id,
                    date = it.date,
                    type = it.type,
                    assetName = it.assetId?.let(assetNames::getValue),
                    accountName = accountNames.getValue(it.accountId),
                    cashEffect = it.cashEffect(),
                )
            },
            hasOversold = ledger.oversold.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GestionUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GestionViewModel(financeContainer().database) }
        }
    }
}
