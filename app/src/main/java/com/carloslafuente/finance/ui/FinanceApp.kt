package com.carloslafuente.finance.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carloslafuente.finance.R
import com.carloslafuente.finance.ui.gestion.AssetScreen
import com.carloslafuente.finance.ui.gestion.GestionScreen
import com.carloslafuente.finance.ui.gestion.GestionViewModel
import com.carloslafuente.finance.ui.gestion.ProjectionScreen
import com.carloslafuente.finance.ui.gestion.TaxReportScreen
import com.carloslafuente.finance.ui.gestion.TransactionFormScreen
import com.carloslafuente.finance.ui.magia.MagiaScreen

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    GESTION(R.string.tab_gestion, Icons.Outlined.AccountBalanceWallet),
    MAGIA(R.string.tab_magia, Icons.Outlined.AutoAwesome),
}

private const val HOME = "home"
const val TRANSACTION_ID_ARG = "transactionId"
const val ASSET_ID_ARG = "assetId"
private const val ASSET = "asset/{$ASSET_ID_ARG}"
private const val TAX_REPORT = "tax"
private const val PROJECTION_VALUE_ARG = "value"
private const val PROJECTION = "projection/{$PROJECTION_VALUE_ARG}"
private const val TRANSACTION = "transaction?$TRANSACTION_ID_ARG={$TRANSACTION_ID_ARG}"

@Composable
fun FinanceApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeTabs(
                onOpenTransaction = { id -> navController.navigate("transaction?$TRANSACTION_ID_ARG=$id") },
                onOpenAsset = { id -> navController.navigate("asset/$id") },
                onOpenTaxReport = { navController.navigate(TAX_REPORT) },
                onOpenProjection = { value -> navController.navigate("projection/${value.toFloat()}") },
            )
        }
        composable(TAX_REPORT) { entry ->
            // Shares the home screen's view model, so the report needs no loading of its own.
            val home = remember(entry) { navController.getBackStackEntry(HOME) }
            TaxReportScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel(home, factory = GestionViewModel.Factory),
            )
        }
        composable(
            route = PROJECTION,
            arguments = listOf(navArgument(PROJECTION_VALUE_ARG) { type = NavType.FloatType }),
        ) { entry ->
            ProjectionScreen(
                startValue = entry.arguments!!.getFloat(PROJECTION_VALUE_ARG).toDouble(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = ASSET,
            arguments = listOf(navArgument(ASSET_ID_ARG) { type = NavType.LongType }),
        ) {
            AssetScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate("transaction?$TRANSACTION_ID_ARG=$id") },
            )
        }
        composable(
            route = TRANSACTION,
            arguments = listOf(navArgument(TRANSACTION_ID_ARG) { type = NavType.LongType; defaultValue = 0L }),
        ) {
            TransactionFormScreen(onDone = { navController.popBackStack() })
        }
    }
}

/** [onOpenTransaction] receives 0 for a new transaction. */
@Composable
private fun HomeTabs(
    onOpenTransaction: (Long) -> Unit,
    onOpenAsset: (Long) -> Unit,
    onOpenTaxReport: () -> Unit,
    onOpenProjection: (Double) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(Tab.GESTION) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selected) {
            Tab.GESTION -> GestionScreen(onOpenTransaction, onOpenAsset, onOpenTaxReport, onOpenProjection, Modifier.padding(innerPadding))
            Tab.MAGIA -> MagiaScreen(Modifier.padding(innerPadding))
        }
    }
}
