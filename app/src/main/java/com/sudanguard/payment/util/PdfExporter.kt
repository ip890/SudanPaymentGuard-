package com.sudanguard.payment.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.sudanguard.payment.data.Transaction
import java.io.File
import java.io.FileOutputStream

object PdfExporter {
    fun export(context: Context, rows: List<Transaction>, english: Boolean): File {
        val doc = PdfDocument()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var y = 44f
        fun drawLine(value: String, p: TextPaint) {
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, p, 520)
                .setAlignment(if (english) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(if (english) TextDirectionHeuristics.LTR else TextDirectionHeuristics.RTL)
                .setIncludePad(true).build()
            if (y + layout.height > 800f) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); y = 44f
            }
            page.canvas.save(); page.canvas.translate(36f, y); layout.draw(page.canvas); page.canvas.restore(); y += layout.height + 7f
        }
        drawLine("Sudan Payment Guard - Transaction Report", title)
        rows.forEach { t ->
            val lines = if (english) listOf("Reference: ${t.transactionNumber}", "Sender: ${t.sender}", "Receiver: ${t.receiver}", "Sender account: ${t.senderAccount}", "Receiver account: ${t.receiverAccount}", "Amount: ${t.amount}", "Date/time: ${t.dateTime}", "Screening: ${t.verdict}", "Notes: ${t.reasons}")
            else listOf("رقم العملية: ${t.transactionNumber}", "المرسل: ${t.sender}", "المستلم: ${t.receiver}", "حساب المرسل: ${t.senderAccount}", "حساب المستلم: ${t.receiverAccount}", "المبلغ: ${t.amount}", "التاريخ والوقت: ${t.dateTime}", "نتيجة الفحص: ${t.verdict}", "ملاحظات: ${t.reasons}")
            lines.forEach { drawLine(it, paint) }; y += 12f
        }
        doc.finishPage(page)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, "payment_guard_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }; doc.close()
        return file
    }
}
