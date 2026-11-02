package com.sudanguard.payment.security

import android.graphics.Bitmap
import com.sudanguard.payment.data.Transaction

/** Conservative screening: flags missing/inconsistent evidence; cannot authenticate a bank transfer. */
object ReceiptRiskCheck {
    data class Result(val status: String, val reasons: List<String>)
    fun inspect(bitmap: Bitmap, tx: Transaction): Result {
        val reasons = mutableListOf<String>()
        if (bitmap.width < 600 || bitmap.height < 900) reasons += "Low image resolution"
        if (tx.transactionNumber.isBlank()) reasons += "Transaction reference was not extracted"
        if (tx.amount.isBlank()) reasons += "Amount was not confidently extracted"
        if (tx.dateTime.isBlank()) reasons += "Date/time was not confidently extracted"
        if (tx.rawText.length < 25) reasons += "Insufficient readable text"
        // A missing issue is not evidence that the transfer is fake or genuine.
        val status = if (reasons.isEmpty()) "REVIEW_REQUIRED" else "INSUFFICIENT_DATA"
        return Result(status, reasons)
    }
}
