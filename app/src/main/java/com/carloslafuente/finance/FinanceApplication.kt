package com.carloslafuente.finance

import android.app.Application
import com.carloslafuente.finance.data.backup.BackupRepository
import com.carloslafuente.finance.data.local.AppDatabase
import com.carloslafuente.finance.data.prices.PriceRepository
import com.carloslafuente.finance.data.prices.PriceSources
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FinanceApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Manual dependency container (no DI framework — the app is small). */
class AppContainer(private val application: Application) {
    val database: AppDatabase by lazy { AppDatabase.build(application) }

    val backupRepository: BackupRepository by lazy { BackupRepository(database, application.contentResolver) }

    val priceRepository: PriceRepository by lazy {
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        PriceRepository(database, PriceSources(client))
    }
}
