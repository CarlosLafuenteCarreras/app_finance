package com.carloslafuente.finance.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.data.prices.PriceRepository
import com.carloslafuente.finance.domain.buildLedger
import com.carloslafuente.finance.domain.cashEffect
import com.carloslafuente.finance.ui.financeContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class AccountRow(val id: Long, val name: String, val cash: BigDecimal)

data class PositionRow(
    val assetId: Long,
    val name: String,
    val quantity: BigDecimal,
    val cost: BigDecimal,
    /** Market value at the latest known price; null when the asset has no price yet. */
    val value: BigDecimal?,
    val priceDate: LocalDate?,
) {
    val gain: BigDecimal? get() = value?.minus(cost)
}

data class TransactionRow(
    val id: Long,
    val date: LocalDate,
    val type: TransactionType,
    val assetName: String?,
    val accountName: String,
    val cashEffect: BigDecimal,
)

data class RefreshState(val running: Boolean = false, val failed: List<String> = emptyList())

data class GestionUiState(
    val loading: Boolean = true,
    /** Cash plus positions, valuing at cost the positions that have no price. */
    val total: BigDecimal = BigDecimal.ZERO,
    val accounts: List<AccountRow> = emptyList(),
    val positions: List<PositionRow> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    val hasOversold: Boolean = false,
    val refresh: RefreshState = RefreshState(),
)

class GestionViewModel(db: AppDatabase, private val priceRepository: PriceRepository) : ViewModel() {
    private val refresh = MutableStateFlow(RefreshState())

    val uiState: StateFlow<GestionUiState> = combine(
        db.accountDao().observeAll(),
        db.assetDao().observeAll(),
        db.transactionDao().observeAll(),
        db.priceDao().observeLatest(),
        refresh,
    ) { accounts, assets, transactions, prices, refresh ->
        val ledger = buildLedger(transactions)
        val accountNames = accounts.associate { it.id to it.name }
        val assetNames = assets.associate { it.id to it.name }
        val latestPrice = prices.associateBy { it.assetId }
        val positions = ledger.lots.map { (assetId, lots) ->
            val quantity = lots.sumOf { it.quantity }
            val price = latestPrice[assetId]
            PositionRow(
                assetId = assetId,
                name = assetNames.getValue(assetId),
                quantity = quantity,
                cost = lots.sumOf { it.cost },
                value = price?.let { quantity * it.price },
                priceDate = price?.date,
            )
        }.sortedByDescending { it.value ?: it.cost }
        GestionUiState(
            loading = false,
            total = ledger.cash.values.sumOf { it } + positions.sumOf { it.value ?: it.cost },
            accounts = accounts.map { AccountRow(it.id, it.name, ledger.cash[it.id] ?: BigDecimal.ZERO) },
            positions = positions,
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
            refresh = refresh,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GestionUiState())

    init {
        refreshPrices()
    }

    fun refreshPrices() {
        if (refresh.value.running) return
        refresh.update { it.copy(running = true) }
        viewModelScope.launch {
            val failed = priceRepository.refresh()
            refresh.value = RefreshState(running = false, failed = failed)
        }
    }

    fun setManualPrice(assetId: Long, price: BigDecimal) = viewModelScope.launch {
        priceRepository.setManualPrice(assetId, price)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GestionViewModel(financeContainer().database, financeContainer().priceRepository) }
        }
    }
}
