package com.sudanguard.payment.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionNumber: String = "",
    val sender: String = "",
    val receiver: String = "",
    val senderAccount: String = "",
    val receiverAccount: String = "",
    val amount: String = "",
    val dateTime: String = "",
    val bank: String = "Bank of Khartoum",
    val rawText: String = "",
    val imagePath: String = "",
    val verdict: String = "REVIEW",
    val reasons: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
