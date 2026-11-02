package com.sudanguard.payment.ocr

import com.sudanguard.payment.data.Transaction

/** Parses OCR output only. It never treats a visually plausible receipt as proof of payment. */
object ReceiptParser {
    fun parse(text: String): Transaction {
        val compact = text.replace('\u00a0', ' ')
        fun capture(regex: Regex): String = regex.find(compact)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val lines = compact.lines().map { it.trim() }.filter { it.isNotBlank() }
        val tx = capture(Regex("(?i)(?:transaction\\s*(?:id|no|number)|reference\\s*(?:no|number))\\s*[:#-]?\\s*([A-Z0-9-]{6,})"))
            .ifBlank { afterArabicLabel(lines, listOf("رقم العملية", "رقم المعاملة")).filter { it.isDigit() }.takeIf { it.length in 8..18 }.orEmpty() }
        val amount = capture(Regex("(?i)(?:amount|SDG)\\s*[:：]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"))
            .ifBlank { Regex("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+\\.\\d{2})(?![0-9])").find(compact)?.groupValues?.get(1).orEmpty() }
        val date = Regex("(?i)(\\d{1,2}[-/ ](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/ ]\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)").find(compact)?.groupValues?.get(1).orEmpty()
        val accounts = Regex("\\b\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{4}\\b").findAll(compact).map { it.value }.toList()
        val sender = valueAfterLabel(lines, listOf("sender", "from")).ifBlank { afterArabicLabel(lines, listOf("اسم المرسل", "المرسل")) }
        val receiver = valueAfterLabel(lines, listOf("receiver", "beneficiary", "to")).ifBlank { afterArabicLabel(lines, listOf("اسم المرسل اليه", "اسم المستلم", "المرسل اليه")) }
        return Transaction(
            transactionNumber = tx,
            sender = sender,
            receiver = receiver,
            senderAccount = accounts.getOrNull(0).orEmpty(),
            receiverAccount = accounts.getOrNull(1).orEmpty(),
            amount = amount,
            dateTime = date,
            rawText = compact
        )
    }

    private fun afterArabicLabel(lines: List<String>, labels: List<String>): String {
        for (i in lines.indices) if (labels.any { lines[i].contains(it) }) {
            val sameLine = lines[i].replace(Regex("رقم العملية|رقم المعاملة|اسم المرسل اليه|اسم المستلم|اسم المرسل|المرسل اليه|المرسل"), "").trim().trim(':', '-', ' ')
            if (sameLine.isNotBlank()) return sameLine
            return lines.getOrNull(i + 1).orEmpty()
        }
        return ""
    }

    private fun valueAfterLabel(lines: List<String>, labels: List<String>): String {
        for (i in lines.indices) if (labels.any { lines[i].contains(it, ignoreCase = true) }) {
            val sameLine = lines[i].substringAfter(':', "").trim()
            if (sameLine.isNotBlank()) return sameLine
            return lines.getOrNull(i + 1).orEmpty()
        }
        return ""
    }
}
