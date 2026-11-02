package com.sudanguard.payment

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.googlecode.tesseract.android.TessBaseAPI
import com.sudanguard.payment.data.AppDatabase
import com.sudanguard.payment.data.Transaction
import com.sudanguard.payment.ocr.ReceiptParser
import com.sudanguard.payment.security.ReceiptRiskCheck
import com.sudanguard.payment.util.PdfExporter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var english = false
    private var selectedBitmap: Bitmap? = null
    private var selectedUri: Uri? = null
    private var current = Transaction()
    private lateinit var root: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private val ocrExecutor = Executors.newSingleThreadExecutor()
    private lateinit var resultBox: TextView
    private lateinit var searchBox: EditText
    private val db by lazy { AppDatabase.get(this) }
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::processUri) }
    private var cameraFile: File? = null
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraFile?.let { processUri(Uri.fromFile(it)) } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    private fun render() {
        val labels = if (english) mapOf("title" to "Sudan Payment Guard", "sub" to "Bank of Khartoum receipt screening", "camera" to "Take photo", "gallery" to "Choose image", "search" to "Search transactions", "export" to "Export PDF", "history" to "Saved transactions", "lang" to "العربية", "empty" to "No transactions yet") else mapOf("title" to "حارس التحويلات السوداني", "sub" to "فحص إشعارات بنك الخرطوم", "camera" to "تصوير إشعار", "gallery" to "اختيار صورة", "search" to "ابحث في العمليات", "export" to "تصدير PDF", "history" to "العمليات المحفوظة", "lang" to "English", "empty" to "لا توجد عمليات حتى الآن")
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 24, 20, 20); layoutDirection = if (english) android.view.View.LAYOUT_DIRECTION_LTR else android.view.View.LAYOUT_DIRECTION_RTL; setBackgroundColor(0xFFF4F7F4.toInt()) }
        val scroll = ScrollView(this); contentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(contentContainer); root.addView(scroll, ViewGroup.LayoutParams(-1, -1)); setContentView(root)
        val content = contentContainer
        content.addView(text(labels["title"]!!, 26f, true)); content.addView(text(labels["sub"]!!, 15f, false))
        val lang = button(labels["lang"]!!) { english = !english; render() }; content.addView(lang)
        content.addView(button("📷  ${labels["camera"]}") { openCamera() })
        content.addView(button("🖼  ${labels["gallery"]}") { picker.launch("image/*") })
        resultBox = text(if (english) "Choose a receipt image to begin." else "اختر صورة الإشعار للبدء.", 15f, false); content.addView(resultBox)
        searchBox = EditText(this).apply { hint = labels["search"]; singleLine = true }; content.addView(searchBox)
        content.addView(button(if (english) "Search" else "بحث") { search(searchBox.text.toString()) })
        content.addView(button(labels["export"]!!) { exportPdf() })
        content.addView(text(labels["history"]!!, 20f, true)); content.addView(button(if (english) "Show all" else "عرض الكل") { search("") })
        showRows(emptyList())
    }

    private fun text(value: String, size: Float, bold: Boolean) = TextView(this).apply { text = value; textSize = size; setTextColor(0xFF173D2B.toInt()); if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(8, 12, 8, 12) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; isAllCaps = false; setOnClickListener { action() }; setPadding(8, 8, 8, 8) }

    private fun openCamera() {
        val dir = getExternalFilesDir("Pictures") ?: filesDir; dir.mkdirs()
        cameraFile = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", cameraFile!!)
        camera.launch(uri)
    }

    private fun processUri(uri: Uri) {
        selectedUri = uri
        try {
            selectedBitmap = if (uri.scheme == "file") BitmapFactory.decodeFile(uri.path) else contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            val bitmap = selectedBitmap ?: throw IllegalArgumentException("Cannot decode image")
            resultBox.text = if (english) "Reading image…" else "جارٍ قراءة الصورة…"
            ocrExecutor.execute {
                try {
                    val dataDir = File(filesDir, "tesseract").apply { mkdirs() }
                    val tessDir = File(dataDir, "tessdata").apply { mkdirs() }
                    listOf("ara", "eng").forEach { lang ->
                        val target = File(tessDir, "$lang.traineddata")
                        if (!target.exists()) assets.open("tessdata/$lang.traineddata").use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
                    }
                    val api = TessBaseAPI()
                    if (!api.init(dataDir.absolutePath, "ara+eng")) throw IllegalStateException("OCR initialization failed")
                    api.setImage(bitmap)
                    val recognized = api.getUTF8Text().orEmpty()
                    api.end()
                    runOnUiThread {
                        current = ReceiptParser.parse(recognized).copy(imagePath = uri.toString())
                        val check = ReceiptRiskCheck.inspect(bitmap, current)
                        current = current.copy(verdict = check.status, reasons = check.reasons.joinToString("; "))
                        displayResult(current, check.reasons)
                        lifecycleScope.launch { db.transactionDao().insert(current) }
                    }
                } catch (e: Exception) {
                    runOnUiThread { resultBox.text = (if (english) "OCR error: " else "خطأ في قراءة النص: ") + (e.localizedMessage ?: "unknown") }
                }
            }
        } catch (e: Exception) { resultBox.text = e.localizedMessage ?: "Image error" }
    }

    private fun displayResult(t: Transaction, reasons: List<String>) {
        val rows = if (english) listOf("Reference" to t.transactionNumber, "Sender" to t.sender, "Receiver" to t.receiver, "Sender account" to t.senderAccount, "Receiver account" to t.receiverAccount, "Amount" to t.amount, "Date / time" to t.dateTime, "Screening" to t.verdict, "Findings" to reasons.joinToString(", "))
        else listOf("رقم العملية" to t.transactionNumber, "المرسل" to t.sender, "المستلم" to t.receiver, "حساب المرسل" to t.senderAccount, "حساب المستلم" to t.receiverAccount, "المبلغ" to t.amount, "التاريخ والوقت" to t.dateTime, "نتيجة الفحص" to "يتطلب مراجعة", "الملاحظات" to reasons.joinToString("، "))
        resultBox.text = rows.joinToString("\n") { "${it.first}: ${it.second.ifBlank { "—" }}" } + "\n\n" + if (english) "Image screening is NOT bank confirmation. Verify funds in the recipient account." else "فحص الصورة لا يؤكد وصول الأموال. تحقق من رصيد حساب المستلم أو من البنك."
    }

    private fun search(q: String) { lifecycleScope.launch { val rows = if (q.isBlank()) db.transactionDao().all() else db.transactionDao().search(q); showRows(rows) } }
    private fun showRows(rows: List<Transaction>) {
        val old = contentContainer.findViewWithTag<LinearLayout>("rows")
        if (old != null) contentContainer.removeView(old)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "rows" }
        if (rows.isEmpty()) list.addView(text(if (english) "No matching transactions" else "لا توجد عمليات مطابقة", 14f, false))
        rows.forEach { t -> list.addView(text("${t.transactionNumber.ifBlank { "—" }}   |   ${t.amount.ifBlank { "—" }}\n${t.receiver.ifBlank { "—" }}   •   ${t.dateTime.ifBlank { "—" }}\n${t.verdict}", 14f, false)) }
        contentContainer.addView(list)
    }
    private fun exportPdf() { lifecycleScope.launch { val rows = db.transactionDao().all(); if (rows.isEmpty()) { Toast.makeText(this@MainActivity, if (english) "No data to export" else "لا توجد بيانات للتصدير", Toast.LENGTH_SHORT).show(); return@launch }; val f = PdfExporter.export(this@MainActivity, rows, english); val share = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@MainActivity, "$packageName.provider", f)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; startActivity(Intent.createChooser(share, if (english) "Share report" else "مشاركة التقرير")) } }
}
