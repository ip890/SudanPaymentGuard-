package com.sudanguard.payment.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TransactionDao {
    @Insert suspend fun insert(item: Transaction): Long
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC") suspend fun all(): List<Transaction>
    @Query("SELECT * FROM transactions WHERE transactionNumber LIKE '%' || :q || '%' OR sender LIKE '%' || :q || '%' OR receiver LIKE '%' || :q || '%' OR amount LIKE '%' || :q || '%' OR dateTime LIKE '%' || :q || '%' OR senderAccount LIKE '%' || :q || '%' OR receiverAccount LIKE '%' || :q || '%'  ORDER BY createdAt DESC") suspend fun search(q: String): List<Transaction>
}
