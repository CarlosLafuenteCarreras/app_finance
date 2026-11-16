package com.carloslafuente.finance.ui.gestion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.data.prices.PriceRepository
import com.carloslafuente.finance.data.prices.Quote
import com.carloslafuente.finance.domain.buildLedger
import com.carloslafuente.finance.domain.cashEffect
import com.carloslafuente.finance.ui.ASSET_ID_ARG
import com.carloslafuente.finance.ui.financeContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class AssetUiState(
    val loading: Boolean = true,
    val name: String = "",
    val type: AssetType? = null,
    val isin: String? = null,
    /** Price history, oldest first. */
    val quotes: List<Quote> = emptyList(),
    /** Null when nothing of this asset is held. */
    val position: PositionRow? = null,
    val tradeDates: List<LocalDate> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    /** Raw ledger, for the sale simulator. */
    val ledgerTransactions: List<TransactionEntity> = emptyList(),
)

class AssetViewModel(
    db: AppDatabase,
    private val priceRepository: PriceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val assetId: Long = checkNotNull(savedStateHandle[ASSET_ID_ARG])

    val uiState: StateFlow<AssetUiState> = combine(
        db.accountDao().observeAll(),
        db.assetDao().observeAll(),
        db.transactionDao().observeAll(),
        db.priceDao().observeForAsset(assetId),
    ) { accounts, assets, transactions, prices ->
        val asset = assets.firstOrNull { it.id == assetId } ?: return@combine AssetUiState(loading = false)
        val accountNames = accounts.associate { it.id to it.name }
        val own = transactions.filter { it.assetId == assetId }
        val lots = buildLedger(transactions).lots[assetId]
        val latest = prices.lastOrNull()
        AssetUiState(
            loading = false,
            name = asset.name,
            type = asset.type,
            isin = asset.isin,
            quotes = prices.map { Quote(it.date, it.price) },
            position = lots?.let {
                val quantity = it.sumOf { lot -> lot.quantity }
                PositionRow(
                    assetId = assetId,
                    name = asset.name,
                    isin = asset.isin,
                    quantity = quantity,
                    cost = it.sumOf { lot -> lot.cost },
                    value = latest?.let { price -> quantity * price.price },
                    priceDate = latest?.date,
                    price = latest?.price,
                )
            },
            tradeDates = own.filter { it.type == TransactionType.BUY || it.type == TransactionType.SELL }.map { it.date },
            transactions = own.asReversed().map {
                TransactionRow(it.id, it.date, it.type, asset.name, accountNames.getValue(it.accountId), it.cashEffect())
            },
            ledgerTransactions = transactions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssetUiState())

    fun setManualPrice(price: BigDecimal) = viewModelScope.launch {
        priceRepository.setManualPrice(assetId, price)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = financeContainer()
                AssetViewModel(container.database, container.priceRepository, createSavedStateHandle())
            }
        }
    }
}
