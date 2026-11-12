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

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<AccountEntity>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets")
    suspend fun getAll(): List<AssetEntity>

    @Insert
    suspend fun insert(asset: AssetEntity): Long

    @Insert
    suspend fun insertAll(assets: List<AssetEntity>)

    @Query("DELETE FROM assets")
    suspend fun deleteAll()
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

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices p WHERE date = (SELECT MAX(date) FROM prices WHERE assetId = p.assetId)")
    fun observeLatest(): Flow<List<PriceEntity>>

    @Upsert
    suspend fun upsert(price: PriceEntity)
}
