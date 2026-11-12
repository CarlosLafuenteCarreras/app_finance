package com.carloslafuente.finance.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.math.BigDecimal
import java.time.LocalDate

class Converters {
    // Decimals are stored as text so no precision is lost to floating point.
    @TypeConverter
    fun decimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
}

@Database(
    entities = [AccountEntity::class, AssetEntity::class, TransactionEntity::class, PriceEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun assetDao(): AssetDao
    abstract fun transactionDao(): TransactionDao
    abstract fun priceDao(): PriceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "finance.db").build()
    }
}
