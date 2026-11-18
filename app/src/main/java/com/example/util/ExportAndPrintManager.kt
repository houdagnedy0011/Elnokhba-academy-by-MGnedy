package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.AcademyConfig
import com.example.model.AttendanceRecord
import com.example.model.AttendanceStatus
import com.example.model.CalendarMode
import com.example.model.EvaluationRecord
import com.example.model.GradeCatalog
import com.example.model.PaymentMethod
import com.example.model.PaymentRecord
import com.example.model.Student
import com.example.model.StudyGroup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object ExportAndPrintManager {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    private fun loadLogo(context: Context): Bitmap? = try {
        BitmapFactory.decodeResource(context.resources, R.drawable.academy_logo)
    } catch (_: Exception) {
        null
    }

    private fun drawHeader(
        canvas: Canvas,
        context: Context,
        config: AcademyConfig,
        title: String,
        subtitle: String
    ): Float {
        val navyPaint = Paint().apply { color = Color.parseColor("#15243D") }
        val goldPaint = Paint().apply {
            color = Color.parseColor("#C59B27")
            strokeWidth = 3f
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 110f, navyPaint)
        canvas.drawLine(0f, 110f, PAGE_WIDTH.toFloat(), 110f, goldPaint)

        loadLogo(context)?.let { bmp ->
            val dest = Rect(PAGE_WIDTH - 140, 14, PAGE_WIDTH - 24, 96)
            canvas.drawBitmap(bmp, null, dest, null)
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5C158")
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8DEE9")
            textSize = 10f
        }

        val mode = CalendarMode.fromKey(config.primaryCalendar)
        val printDate = CalendarUtils.formatDualDate(System.currentTimeMillis(), mode)

        drawRtlText(canvas, config.academyName, titlePaint, MARGIN, 20f, PAGE_WIDTH - 190f)
        drawRtlText(canvas, title, subPaint, MARGIN, 48f, PAGE_WIDTH - 190f)
        drawRtlText(canvas, "$subtitle  •  تاريخ الطباعة: $printDate", datePaint, MARGIN, 72f, PAGE_WIDTH - 190f)

        return 132f
    }

    private fun drawRtlText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(40))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.15f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height + 6f
    }

    private fun savePdfAndPrintOrShare(
        context: Context,
        document: PdfDocument,
        fileName: String,
        printMode: Boolean
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        if (printMode) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            printManager?.print(
                fileName,
                object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback,
                        extras: Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onLayoutCancelled()
                            return
                        }
                        val info = PrintDocumentInfo.Builder(fileName)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build()
                        callback.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>,
                        destination: ParcelFileDescriptor,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback
                    ) {
                        try {
                            file.inputStream().use { input ->
                                FileOutputStream(destination.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback.onWriteFailed(e.message)
                        }
                    }
                },
                null
            )
        } else {
            shareFile(context, file, "application/pdf", "مشاركة تقرير PDF")
        }
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun generatePaymentReceiptPdf(
        context: Context,
        config: AcademyConfig,
        payment: PaymentRecord,
        groupName: String,
        printDirectly: Boolean = true
    ): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        var y = drawHeader(canvas, context, config, "إيصال سداد مصروفات دراسية", "رقم الإيصال: ${payment.receiptNumber}")

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141B29"); textSize = 13f }
        val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#15243D"); textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        val boxPaint = Paint().apply { color = Color.parseColor("#FAF8F4"); style = Paint.Style.FILL }
        val borderPaint = Paint().apply { color = Color.parseColor("#C59B27"); style = Paint.Style.STROKE; strokeWidth = 2f }

        canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 290f, 12f, 12f, boxPaint)
        canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 290f, 12f, 12f, borderPaint)
        y += 18f

        val mode = CalendarMode.fromKey(config.primaryCalendar)
        val paidDate = CalendarUtils.formatDualDate(payment.paidAtMillis, mode)
        val monthLabel = CalendarUtils.formatMonthLabel(payment.monthKey, mode)
        val remaining = (payment.amountDue - payment.amountPaid).coerceAtLeast(0.0)
        val methodLabel = PaymentMethod.fromKey(payment.paymentMethod).arabicLabel

        val lines = listOf(
            "اسم الطالب: ${payment.studentName}",
            "المجموعة: $groupName",
            "عن شهر: $monthLabel",
            "المبلغ المطلوب: ${CalendarUtils.formatCurrency(payment.amountDue, config.currencySymbol)}",
            "المبلغ المدفوع: ${CalendarUtils.formatCurrency(payment.amountPaid, config.currencySymbol)}",
            "المبلغ المتبقي: ${CalendarUtils.formatCurrency(remaining, config.currencySymbol)}",
            "طريقة الدفع: $methodLabel",
            "تاريخ السداد: $paidDate",
            "ملاحظات: ${payment.notes.ifBlank { "لا يوجد" }}"
        )
        lines.forEachIndexed { idx, line ->
            val p = if (idx == 4) boldPaint else bodyPaint
            y = drawRtlText(canvas, line, p, MARGIN + 16f, y, PAGE_WIDTH - 2 * MARGIN - 32f) + 4f
        }

        y += 28f
        drawRtlText(canvas, "شكراً لالتزامكم — إدارة ${config.academyName}", boldPaint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
        doc.finishPage(page)
        return savePdfAndPrintOrShare(context, doc, "receipt_${payment.receiptNumber}.pdf", printDirectly)
    }

    fun generateGroupsReportPdf(
        context: Context,
        config: AcademyConfig,
        groups: List<StudyGroup>,
        students: List<Student>,
        printDirectly: Boolean = true
    ): File {
        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        var y = drawHeader(canvas, context, config, "تقرير المجموعات الدراسية", "إجمالي المجموعات: ${groups.size}")
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141B29"); textSize = 11.5f }
        val bold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#15243D"); textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD }

        groups.sortedBy { it.gradeLevel }.forEach { grp ->
            if (y > PAGE_HEIGHT - 90f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = drawHeader(canvas, context, config, "تقرير المجموعات الدراسية (تابع)", "صفحة $pageNum")
            }
            val count = students.count { !it.archived && it.groupId == grp.id }
            y = drawRtlText(canvas, "• ${grp.name} (${GradeCatalog.labelFor(grp.gradeLevel.toInt())})", bold, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
            y = drawRtlText(
                canvas,
                "  المعلم: ${grp.teacherName.ifBlank { "غير محدد" }}  |  الطلاب: $count / ${grp.maxCapacity}  |  الموعد: ${grp.scheduleText.ifBlank { "-" }}  |  الرسوم: ${CalendarUtils.formatCurrency(grp.monthlyFee, config.currencySymbol)}",
                paint,
                MARGIN,
                y,
                PAGE_WIDTH - 2 * MARGIN
            ) + 6f
        }
        doc.finishPage(page)
        return savePdfAndPrintOrShare(context, doc, "groups_report.pdf", printDirectly)
    }

    fun generateFinancialReportPdf(
        context: Context,
        config: AcademyConfig,
        monthKey: String,
        paidSummaries: List<Pair<Student, Double>>,
        unpaidSummaries: List<Pair<Student, Double>>,
        totalCollected: Double,
        totalPending: Double,
        printDirectly: Boolean = true
    ): File {
        val doc = PdfDocument()
        val mode = CalendarMode.fromKey(config.primaryCalendar)
        val monthLabel = CalendarUtils.formatMonthLabel(monthKey, mode)
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        var y = drawHeader(canvas, context, config, "التقرير المالي الشهري", monthLabel)

        val bold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#15243D"); textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141B29"); textSize = 11.5f }

        y = drawRtlText(
            canvas,
            "إجمالي المحصّل: ${CalendarUtils.formatCurrency(totalCollected, config.currencySymbol)}   |   إجمالي المتأخرات: ${CalendarUtils.formatCurrency(totalPending, config.currencySymbol)}",
            bold,
            MARGIN,
            y,
            PAGE_WIDTH - 2 * MARGIN
        ) + 8f

        y = drawRtlText(canvas, "أولاً: الطلاب الذين سددوا (${paidSummaries.size}):", bold, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
        paidSummaries.forEach { (stu, paid) ->
            if (y > PAGE_HEIGHT - 70f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = drawHeader(canvas, context, config, "التقرير المالي الشهري (تابع)", monthLabel)
            }
            y = drawRtlText(canvas, "✓ ${stu.fullName} — المدفوع: ${CalendarUtils.formatCurrency(paid, config.currencySymbol)}", paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
        }

        y += 10f
        y = drawRtlText(canvas, "ثانياً: الطلاب الذين لم يسددوا / عليهم متبقي (${unpaidSummaries.size}):", bold, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
        unpaidSummaries.forEach { (stu, rem) ->
            if (y > PAGE_HEIGHT - 70f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = drawHeader(canvas, context, config, "التقرير المالي الشهري (تابع)", monthLabel)
            }
            y = drawRtlText(canvas, "✗ ${stu.fullName} — المتبقي: ${CalendarUtils.formatCurrency(rem, config.currencySymbol)} (${stu.phone})", paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
        }

        doc.finishPage(page)
        return savePdfAndPrintOrShare(context, doc, "financial_report_$monthKey.pdf", printDirectly)
    }

    fun generateAttendanceReportPdf(
        context: Context,
        config: AcademyConfig,
        titleSuffix: String,
        rows: List<String>,
        printDirectly: Boolean = true
    ): File {
        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        var y = drawHeader(canvas, context, config, "تقرير الحضور والغياب", titleSuffix)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141B29"); textSize = 11.5f }

        rows.forEach { line ->
            if (y > PAGE_HEIGHT - 70f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = drawHeader(canvas, context, config, "تقرير الحضور والغياب (تابع)", titleSuffix)
            }
            y = drawRtlText(canvas, line, paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN) + 3f
        }
        doc.finishPage(page)
        return savePdfAndPrintOrShare(context, doc, "attendance_report.pdf", printDirectly)
    }

    fun generateStudentDossierPdf(
        context: Context,
        config: AcademyConfig,
        student: Student,
        groupName: String,
        attendance: List<AttendanceRecord>,
        payments: List<PaymentRecord>,
        evaluations: List<EvaluationRecord>,
        printDirectly: Boolean = true
    ): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        val mode = CalendarMode.fromKey(config.primaryCalendar)
        var y = drawHeader(canvas, context, config, "كشف الطالب الشامل", student.fullName)

        val bold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#15243D"); textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141B29"); textSize = 11f }

        y = drawRtlText(canvas, "البيانات الأساسية:", bold, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
        y = drawRtlText(
            canvas,
            "الصف: ${GradeCatalog.labelFor(student.gradeLevel.toInt())}  |  المجموعة: $groupName  |  ولي الأمر: ${student.guardianName}  |  الهاتف: ${student.phone}",
            paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN
        )
        y = drawRtlText(
            canvas,
            "تاريخ الالتحاق: ${CalendarUtils.formatDualDate(student.enrollmentDateMillis, mode)}",
            paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN
        ) + 6f

        val presentCount = attendance.count { it.status == AttendanceStatus.PRESENT.key || it.status == AttendanceStatus.LATE.key }
        val attRate = if (attendance.isEmpty()) 100 else (presentCount * 100 / attendance.size)
        y = drawRtlText(canvas, "ملخص الحضور: $presentCount من ${attendance.size} حصة (نسبة الالتزام $attRate%)", bold, MARGIN, y, PAGE_WIDTH - 2 * MARGIN) + 6f

        y = drawRtlText(canvas, "سجل المدفوعات (${payments.size}):", bold, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
        payments.take(6).forEach { p ->
            y = drawRtlText(
                canvas,
                "• ${CalendarUtils.formatMonthLabel(p.monthKey, mode)}: مدفوع ${CalendarUtils.formatCurrency(p.amountPaid, config.currencySymbol)} من ${CalendarUtils.formatCurrency(p.amountDue, config.currencySymbol)} (إيصال ${p.receiptNumber})",
                paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN
            )
        }
        if (payments.isEmpty()) y = drawRtlText(canvas, "لا توجد مدفوعات مسجلة", paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)

        y += 8f
        y = drawRtlText(canvas, "سجل التقييمات (${evaluations.size}):", bold, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)
        evaluations.sortedByDescending { it.evaluatedDateMillis }.take(8).forEach { ev ->
            y = drawRtlText(
                canvas,
                "• ${ev.subject}: ${ev.percentage.toInt()}% (${ev.gradeLabel}) — ${ev.notes.ifBlank { "بدون ملاحظات" }}",
                paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN
            )
        }
        if (evaluations.isEmpty()) drawRtlText(canvas, "لا توجد تقييمات مسجلة", paint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN)

        doc.finishPage(page)
        return savePdfAndPrintOrShare(context, doc, "student_dossier_${student.id}.pdf", printDirectly)
    }

    fun buildStudentsCsv(students: List<Student>, groupsById: Map<String, StudyGroup>): String {
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("الاسم الكامل,ولي الأمر,رقم الهاتف,الصف الدراسي,المجموعة,ملاحظات,الحالة")
        students.forEach { s ->
            val grpName = s.groupId?.let { groupsById[it]?.name } ?: "الوافدون الجدد"
            val grade = GradeCatalog.labelFor(s.gradeLevel.toInt())
            val state = if (s.archived) "مؤرشف" else "نشط"
            val row = listOf(s.fullName, s.guardianName, s.phone, grade, grpName, s.notes, state)
                .joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" }
            sb.appendLine(row)
        }
        return sb.toString()
    }

    fun buildFinancialCsv(
        config: AcademyConfig,
        monthKey: String,
        payments: List<PaymentRecord>
    ): String {
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("رقم الإيصال,اسم الطالب,الشهر,المطلوب,المدفوع,المتبقي,طريقة الدفع,ملاحظات")
        payments.filter { it.monthKey == monthKey }.forEach { p ->
            val rem = (p.amountDue - p.amountPaid).coerceAtLeast(0.0)
            val method = PaymentMethod.fromKey(p.paymentMethod).arabicLabel
            val row = listOf(
                p.receiptNumber, p.studentName, p.monthKey,
                p.amountDue.toString(), p.amountPaid.toString(), rem.toString(),
                method, p.notes
            ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
            sb.appendLine(row)
        }
        return sb.toString()
    }

    fun buildBackupJson(
        config: AcademyConfig,
        groups: List<StudyGroup>,
        students: List<Student>,
        attendance: List<AttendanceRecord>,
        payments: List<PaymentRecord>,
        evaluations: List<EvaluationRecord>
    ): String {
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("academyName", config.academyName)
        root.put("currencySymbol", config.currencySymbol)

        root.put("groups", JSONArray().apply {
            groups.forEach { g ->
                put(JSONObject().apply {
                    put("name", g.name)
                    put("gradeLevel", g.gradeLevel)
                    put("teacherName", g.teacherName)
                    put("scheduleText", g.scheduleText)
                    put("maxCapacity", g.maxCapacity)
                    put("monthlyFee", g.monthlyFee)
                })
            }
        })

        root.put("students", JSONArray().apply {
            students.forEach { s ->
                put(JSONObject().apply {
                    put("fullName", s.fullName)
                    put("guardianName", s.guardianName)
                    put("phone", s.phone)
                    put("gradeLevel", s.gradeLevel)
                    put("enrollmentDateMillis", s.enrollmentDateMillis)
                    put("notes", s.notes)
                    put("archived", s.archived)
                })
            }
        })
        root.put("attendanceCount", attendance.size)
        root.put("paymentsCount", payments.size)
        root.put("evaluationsCount", evaluations.size)
        return root.toString(2)
    }
}
