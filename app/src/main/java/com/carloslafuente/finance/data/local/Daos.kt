package com.carloslafuente.finance.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AccountEntity>>

    @Insert
    suspend fun insert(account: AccountEntity): Long
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets")
    suspend fun getAll(): List<AssetEntity>

    @Insert
    suspend fun insert(asset: AssetEntity): Long
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date, id")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date, id")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices p WHERE date = (SELECT MAX(date) FROM prices WHERE assetId = p.assetId)")
    fun observeLatest(): Flow<List<PriceEntity>>

    @Upsert
    suspend fun upsert(price: PriceEntity)
}
