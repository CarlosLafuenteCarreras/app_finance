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
import com.carloslafuente.finance.data.local.PlanRole
import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.data.prices.PriceRepository
import com.carloslafuente.finance.data.prices.Quote
import com.carloslafuente.finance.domain.ValuePoint
import com.carloslafuente.finance.domain.buildLedger
import com.carloslafuente.finance.domain.cashEffect
import com.carloslafuente.finance.domain.portfolioHistory
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
    val planRole: PlanRole? = null,
    val planWeight: BigDecimal? = null,
    /** Price history, oldest first. */
    val quotes: List<Quote> = emptyList(),
    /** Null when nothing of this asset is held. */
    val position: PositionRow? = null,
    /** Share of all positions' value, 0..1. */
    val weight: Double? = null,
    /** Units per account name, when held. */
    val holdings: List<Pair<String, BigDecimal>> = emptyList(),
    /** Daily value of this position against the money put into it. */
    val positionHistory: List<ValuePoint> = emptyList(),
    val tradeDates: List<LocalDate> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    /** Raw ledger, for the sale simulator. */
    val ledgerTransactions: List<TransactionEntity> = emptyList(),
)

class AssetViewModel(
    private val db: AppDatabase,
    private val priceRepository: PriceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val assetId: Long = checkNotNull(savedStateHandle[ASSET_ID_ARG])

    val uiState: StateFlow<AssetUiState> = combine(
        db.accountDao().observeAll(),
        db.assetDao().observeAll(),
        db.transactionDao().observeAll(),
        db.priceDao().observeAll(),
    ) { accounts, assets, transactions, allPrices ->
        val asset = assets.firstOrNull { it.id == assetId } ?: return@combine AssetUiState(loading = false)
        val accountNames = accounts.associate { it.id to it.name }
        val own = transactions.filter { it.assetId == assetId }
        val ledger = buildLedger(transactions)
        val lots = ledger.lots[assetId]
        val latestByAsset = allPrices.groupBy { it.assetId }.mapValues { it.value.last().price }
        val prices = allPrices.filter { it.assetId == assetId }
        val latest = prices.lastOrNull()
        val quotes = prices.map { Quote(it.date, it.price) }
        // Positions without a price count at cost, as on the home screen.
        fun valueOf(id: Long) = ledger.lots[id].orEmpty().let { open ->
            latestByAsset[id]?.let { price -> open.sumOf { it.quantity } * price } ?: open.sumOf { it.cost }
        }
        val whole = ledger.lots.keys.sumOf(::valueOf)
        AssetUiState(
            loading = false,
            name = asset.name,
            type = asset.type,
            isin = asset.isin,
            planRole = asset.planRole,
            planWeight = asset.planWeight,
            quotes = quotes,
            weight = if (lots != null && whole.signum() > 0) valueOf(assetId).toDouble() / whole.toDouble() else null,
            holdings = ledger.holdings.filterKeys { it.assetId == assetId }
                .map { (key, quantity) -> accountNames.getValue(key.accountId) to quantity },
            positionHistory = portfolioHistory(own, mapOf(assetId to quotes)),
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

    /** A null [role] takes the asset out of the plan. */
    fun setPlan(role: PlanRole?, weight: BigDecimal?) = viewModelScope.launch {
        val asset = db.assetDao().getAll().first { it.id == assetId }
        db.assetDao().update(asset.copy(planRole = role, planWeight = weight.takeIf { role != null }))
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
