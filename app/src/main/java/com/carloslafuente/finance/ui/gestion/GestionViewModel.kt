package com.carloslafuente.finance.ui.gestion

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carloslafuente.finance.R
import com.carloslafuente.finance.data.backup.BackupRepository
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import com.carloslafuente.finance.data.prices.PriceRepository
import com.carloslafuente.finance.data.prices.Quote
import com.carloslafuente.finance.domain.ValuePoint
import com.carloslafuente.finance.domain.annualizedReturn
import com.carloslafuente.finance.domain.benchmarkValue
import com.carloslafuente.finance.domain.buildLedger
import com.carloslafuente.finance.domain.cashEffect
import com.carloslafuente.finance.domain.investmentFlows
import com.carloslafuente.finance.domain.portfolioHistory
import com.carloslafuente.finance.ui.financeContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class AccountRow(val id: Long, val name: String, val cash: BigDecimal)

data class PositionRow(
    val assetId: Long,
    val name: String,
    val isin: String?,
    val quantity: BigDecimal,
    val cost: BigDecimal,
    /** Market value at the latest known price; null when the asset has no price yet. */
    val value: BigDecimal?,
    val priceDate: LocalDate?,
    val price: BigDecimal? = null,
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

/** How the invested money has done, next to the same money put into the benchmark on the same dates. */
data class Performance(
    val positionsValue: BigDecimal,
    /** Null until the benchmark history is available. */
    val benchmarkValue: BigDecimal?,
    /** Annualized rates; null with less than a year of history, where annualizing misleads. */
    val annualized: Double?,
    val benchmarkAnnualized: Double?,
)

/** Money made or lost so far, by where it comes from. */
data class Gains(
    /** Open positions at today's price against what they cost. */
    val unrealized: BigDecimal,
    /** Closed sales, net of fees. */
    val realized: BigDecimal,
    /** Dividends and interest received, net of withholding. */
    val income: BigDecimal,
)

data class RefreshState(val running: Boolean = false, val failed: List<String> = emptyList())

data class GestionUiState(
    val loading: Boolean = true,
    /** Cash plus positions, valuing at cost the positions that have no price. */
    val total: BigDecimal = BigDecimal.ZERO,
    val accounts: List<AccountRow> = emptyList(),
    val positions: List<PositionRow> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    /** Raw ledger, for the sale simulator. */
    val ledgerTransactions: List<TransactionEntity> = emptyList(),
    val performance: Performance? = null,
    val positionsValue: BigDecimal = BigDecimal.ZERO,
    val gains: Gains? = null,
    /** Daily value of the positions since the first trade. */
    val history: List<ValuePoint> = emptyList(),
    val hasOversold: Boolean = false,
    val refresh: RefreshState = RefreshState(),
    /** Outcome of the last backup export or import. */
    @StringRes val backupMessage: Int? = null,
)

class GestionViewModel(
    db: AppDatabase,
    private val priceRepository: PriceRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {
    private val refresh = MutableStateFlow(RefreshState())
    private val backupMessage = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<GestionUiState> = combine(
        db.accountDao().observeAll(),
        db.assetDao().observeAll(),
        db.transactionDao().observeAll(),
        db.priceDao().observeAll(),
        combine(refresh, backupMessage, priceRepository.benchmark, ::Triple),
    ) { accounts, assets, transactions, prices, (refresh, backupMessage, benchmark) ->
        val ledger = buildLedger(transactions)
        val accountNames = accounts.associate { it.id to it.name }
        val assetsById = assets.associateBy { it.id }
        val quotes = prices.groupBy({ it.assetId }, { Quote(it.date, it.price) })
        val latestPrice = quotes.mapValues { it.value.last() }
        val positions = ledger.lots.map { (assetId, lots) ->
            val quantity = lots.sumOf { it.quantity }
            val price = latestPrice[assetId]
            PositionRow(
                assetId = assetId,
                name = assetsById.getValue(assetId).name,
                isin = assetsById.getValue(assetId).isin,
                quantity = quantity,
                cost = lots.sumOf { it.cost },
                value = price?.let { quantity * it.price },
                priceDate = price?.date,
                price = price?.price,
            )
        }.sortedByDescending { it.value ?: it.cost }
        val today = LocalDate.now()
        val flows = investmentFlows(transactions)
        val positionsValue = positions.sumOf { it.value ?: it.cost }
        val benchmarkNow = benchmarkValue(flows, benchmark)
        val overAYear = flows.isNotEmpty() && flows.first().date <= today.minusYears(1)
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
                    assetName = it.assetId?.let { id -> assetsById.getValue(id).name },
                    accountName = accountNames.getValue(it.accountId),
                    cashEffect = it.cashEffect(),
                )
            },
            ledgerTransactions = transactions,
            positionsValue = positionsValue,
            gains = if (flows.isEmpty()) null else Gains(
                unrealized = positions.sumOf { it.gain ?: BigDecimal.ZERO },
                realized = ledger.realized.sumOf { it.gain },
                income = transactions
                    .filter { it.type == TransactionType.DIVIDEND || it.type == TransactionType.INTEREST }
                    .sumOf { it.cashEffect() },
            ),
            history = portfolioHistory(transactions, quotes),
            performance = if (flows.isEmpty()) null else Performance(
                positionsValue = positionsValue,
                benchmarkValue = benchmarkNow,
                annualized = if (overAYear) annualizedReturn(flows, positionsValue, today) else null,
                benchmarkAnnualized = if (overAYear && benchmarkNow != null) annualizedReturn(flows, benchmarkNow, today) else null,
            ),
            hasOversold = ledger.oversold.isNotEmpty(),
            refresh = refresh,
            backupMessage = backupMessage,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GestionUiState())

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

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        backupMessage.value = if (backupRepository.export(uri)) R.string.backup_exported else R.string.backup_export_failed
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        val imported = backupRepository.import(uri)
        backupMessage.value = if (imported) R.string.backup_imported else R.string.backup_import_failed
        if (imported) refreshPrices()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = financeContainer()
                GestionViewModel(container.database, container.priceRepository, container.backupRepository)
            }
        }
    }
}
