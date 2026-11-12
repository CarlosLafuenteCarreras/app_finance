package com.carloslafuente.finance

import android.app.Application
import com.carloslafuente.finance.data.local.AppDatabase

class FinanceApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Manual dependency container (no DI framework — the app is small). */
class AppContainer(private val application: Application) {
    val database: AppDatabase by lazy { AppDatabase.build(application) }
}
