package com.carloslafuente.finance.ui.gestion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.carloslafuente.finance.R
import com.carloslafuente.finance.ui.EmptyState

@Composable
fun GestionScreen(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.gestion_empty_title),
        message = stringResource(R.string.gestion_empty_message),
        modifier = modifier,
    )
}
