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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.carloslafuente.finance.R
import com.carloslafuente.finance.ui.gestion.GestionScreen
import com.carloslafuente.finance.ui.magia.MagiaScreen

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    GESTION(R.string.tab_gestion, Icons.Outlined.AccountBalanceWallet),
    MAGIA(R.string.tab_magia, Icons.Outlined.AutoAwesome),
}

@Composable
fun FinanceApp() {
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
            Tab.GESTION -> GestionScreen(Modifier.padding(innerPadding))
            Tab.MAGIA -> MagiaScreen(Modifier.padding(innerPadding))
        }
    }
}
