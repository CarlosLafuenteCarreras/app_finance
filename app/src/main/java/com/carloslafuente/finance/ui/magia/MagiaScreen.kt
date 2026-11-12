package com.carloslafuente.finance.ui.magia

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.carloslafuente.finance.R
import com.carloslafuente.finance.ui.EmptyState

@Composable
fun MagiaScreen(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.magia_empty_title),
        message = stringResource(R.string.magia_empty_message),
        modifier = modifier,
        footnote = stringResource(R.string.magia_disclaimer),
    )
}
