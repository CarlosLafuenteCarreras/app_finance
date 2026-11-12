package com.carloslafuente.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.carloslafuente.finance.ui.FinanceApp
import com.carloslafuente.finance.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceTheme {
                FinanceApp()
            }
        }
    }
}
