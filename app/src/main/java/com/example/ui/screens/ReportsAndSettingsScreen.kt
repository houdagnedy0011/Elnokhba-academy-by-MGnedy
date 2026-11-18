package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AttendanceStatus
import com.example.model.CalendarMode
import com.example.util.CalendarUtils
import com.example.util.ExportAndPrintManager
import com.example.viewmodel.AcademyDashboardState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsAndSettingsScreen(
    state: AcademyDashboardState,
    onSaveTeacher: (String?, String, String, String, String?, List<String>, String, Long, Boolean, Boolean) -> Unit,
    onSaveConfig: (String, String, String, String, Long, List<String>, Boolean, Boolean, String?) -> Unit,
    onImportStudentsCsv: (String) -> Unit,
    onRestoreBackupJson: (String) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val currentMonth = remember { CalendarUtils.currentMonthKey() }
    var attendanceRangeDays by remember { mutableIntStateOf(30) }
    var selectedStudentId by remember(state.activeStudents) { mutableStateOf(state.activeStudents.firstOrNull()?.id) }
    var studentMenuExpanded by remember { mutableStateOf(false) }

    val selectedStudent = state.activeStudents.firstOrNull { it.id == selectedStudentId }
        ?: state.activeStudents.firstOrNull()

    val exportStudentsCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csv = ExportAndPrintManager.buildStudentsCsv(
                    state.students,
                    state.groups.associateBy { it.id }
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                onNotify("تم تصدير قائمة الطلاب إلى ملف Excel (CSV)")
            } catch (e: Exception) {
                onNotify("فشل التصدير: ${e.localizedMessage}")
            }
        }
    }

    val exportFinancialCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csv = ExportAndPrintManager.buildFinancialCsv(state.config, currentMonth, state.payments)
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                onNotify("تم تصدير التقرير المالي إلى CSV")
            } catch (e: Exception) {
                onNotify("فشل التصدير: ${e.localizedMessage}")
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                onImportStudentsCsv(text)
            } catch (e: Exception) {
                onNotify("تعذر قراءة الملف: ${e.localizedMessage}")
            }
        }
    }

    val backupJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = ExportAndPrintManager.buildBackupJson(
                    state.config, state.groups, state.students, state.attendance, state.payments, state.evaluations
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                onNotify("تم حفظ النسخة الاحتياطية بنجاح")
            } catch (e: Exception) {
                onNotify("فشل إنشاء النسخة الاحتياطية: ${e.localizedMessage}")
            }
        }
    }

    val restoreJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                onRestoreBackupJson(text)
            } catch (e: Exception) {
                onNotify("تعذر استعادة النسخة: ${e.localizedMessage}")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "التقارير والطباعة والإعدادات",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. تقرير المجموعات
        item {
            ReportCard(
                icon = Icons.Outlined.Groups,
                title = "تقرير المجموعات الدراسية",
                description = "أسماء المجموعات الـ ${state.groups.size} مع عدد الطلاب والمعلم المسؤول والمواعيد.",
                onPrint = {
                    ExportAndPrintManager.generateGroupsReportPdf(context, state.config, state.groups, state.students, true)
                },
                onSharePdf = {
                    ExportAndPrintManager.generateGroupsReportPdf(context, state.config, state.groups, state.students, false)
                }
            )
        }

        // 2. التقرير المالي الشهري (للمدير)
        if (state.isAdmin) {
            item {
                val summaries = state.monthlyPaymentSummaries(currentMonth)
                val paidPairs = summaries.filter { it.remaining <= 0.0 && it.amountPaid > 0.0 }.map { it.student to it.amountPaid }
                val unpaidPairs = summaries.filter { it.remaining > 0.0 }.map { it.student to it.remaining }
                val collected = summaries.sumOf { it.amountPaid }
                val pending = summaries.sumOf { it.remaining }

                ReportCard(
                    icon = Icons.Outlined.Payments,
                    title = "التقرير المالي الشهري",
                    description = "قائمة من سددوا (${paidPairs.size}) ومن لم يسددوا (${unpaidPairs.size}) مع الإجماليات.",
                    extraActionLabel = "تصدير Excel (CSV)",
                    onExtraAction = { exportFinancialCsvLauncher.launch("elite_financial_$currentMonth.csv") },
                    onPrint = {
                        ExportAndPrintManager.generateFinancialReportPdf(
                            context, state.config, currentMonth, paidPairs, unpaidPairs, collected, pending, true
                        )
                    },
                    onSharePdf = {
                        ExportAndPrintManager.generateFinancialReportPdf(
                            context, state.config, currentMonth, paidPairs, unpaidPairs, collected, pending, false
                        )
                    }
                )
            }
        }

        // 3. تقرير الحضور لفترة محددة
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.EventAvailable, null, tint = MaterialTheme.colorScheme.secondary)
                        Text("تقرير الحضور والغياب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7 to "آخر 7 أيام", 30 to "آخر 30 يوم", 90 to "آخر 3 شهور").forEach { (days, lbl) ->
                            FilterChip(
                                selected = attendanceRangeDays == days,
                                onClick = { attendanceRangeDays = days },
                                label = { Text(lbl) }
                            )
                        }
                    }
                    val cutoffKey = CalendarUtils.shiftDateKey(CalendarUtils.todayDateKey(), -attendanceRangeDays)
                    val rows = state.activeStudents.map { stu ->
                        val recs = state.attendance.filter { it.studentId == stu.id && it.dateKey >= cutoffKey }
                        val pres = recs.count { it.status == AttendanceStatus.PRESENT.key || it.status == AttendanceStatus.LATE.key }
                        val rate = if (recs.isEmpty()) 100 else (pres * 100 / recs.size)
                        val grp = state.groupById(stu.groupId)?.name ?: "الوافدون الجدد"
                        "• ${stu.fullName} ($grp) — حضور $pres من ${recs.size} ($rate%)"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                ExportAndPrintManager.generateAttendanceReportPdf(
                                    context, state.config, "خلال آخر $attendanceRangeDays يوم", rows, true
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Print, null, Modifier.size(18.dp))
                            Spacer(Modifier.padding(3.dp))
                            Text("طباعة PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                ExportAndPrintManager.generateAttendanceReportPdf(
                                    context, state.config, "خلال آخر $attendanceRangeDays يوم", rows, false
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.padding(3.dp))
                            Text("مشاركة PDF")
                        }
                    }
                }
            }
        }

        // 4. كشف الطالب الشامل
        if (selectedStudent != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.PersonSearch, null, tint = MaterialTheme.colorScheme.secondary)
                            Text("كشف الطالب الشامل (صفحة واحدة)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        ExposedDropdownMenuBox(
                            expanded = studentMenuExpanded,
                            onExpandedChange = { studentMenuExpanded = !studentMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedStudent.fullName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("اختر الطالب") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(studentMenuExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = studentMenuExpanded,
                                onDismissRequest = { studentMenuExpanded = false }
                            ) {
                                state.activeStudents.forEach { stu ->
                                    DropdownMenuItem(
                                        text = { Text(stu.fullName) },
                                        onClick = {
                                            selectedStudentId = stu.id
                                            studentMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    ExportAndPrintManager.generateStudentDossierPdf(
                                        context,
                                        state.config,
                                        selectedStudent,
                                        state.groupById(selectedStudent.groupId)?.name ?: "الوافدون الجدد",
                                        state.attendance.filter { it.studentId == selectedStudent.id },
                                        state.payments.filter { it.studentId == selectedStudent.id },
                                        state.evaluations.filter { it.studentId == selectedStudent.id },
                                        true
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("print_student_dossier_button")
                            ) {
                                Icon(Icons.Outlined.Print, null, Modifier.size(18.dp))
                                Spacer(Modifier.padding(3.dp))
                                Text("طباعة الكشف")
                            }
                            OutlinedButton(
                                onClick = {
                                    ExportAndPrintManager.generateStudentDossierPdf(
                                        context,
                                        state.config,
                                        selectedStudent,
                                        state.groupById(selectedStudent.groupId)?.name ?: "الوافدون الجدد",
                                        state.attendance.filter { it.studentId == selectedStudent.id },
                                        state.payments.filter { it.studentId == selectedStudent.id },
                                        state.evaluations.filter { it.studentId == selectedStudent.id },
                                        false
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                                Spacer(Modifier.padding(3.dp))
                                Text("مشاركة")
                            }
                        }
                    }
                }
            }
        }

        // التصدير والاستيراد والنسخ الاحتياطي
        if (state.isAdmin) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("النسخ الاحتياطي وتبادل بيانات Excel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportStudentsCsvLauncher.launch("elite_students.csv") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.TableChart, null, Modifier.size(18.dp))
                                Spacer(Modifier.padding(3.dp))
                                Text("تصدير CSV")
                            }
                            OutlinedButton(
                                onClick = { importCsvLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                                Spacer(Modifier.padding(3.dp))
                                Text("استيراد طلاب")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { backupJsonLauncher.launch("elite_academy_backup.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Backup, null, Modifier.size(18.dp))
                                Spacer(Modifier.padding(3.dp))
                                Text("نسخ احتياطي")
                            }
                            OutlinedButton(
                                onClick = { restoreJsonLauncher.launch(arrayOf("application/json", "text/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp))
                                Spacer(Modifier.padding(3.dp))
                                Text("استعادة نسخة")
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                TeachersManagementSection(
                    state = state,
                    onSaveTeacher = onSaveTeacher,
                    onNotify = onNotify
                )
            }

            item {
                HorizontalDivider()
                AcademySettingsSection(state = state, onSaveConfig = onSaveConfig)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ReportCard(
    icon: ImageVector,
    title: String,
    description: String,
    extraActionLabel: String? = null,
    onExtraAction: (() -> Unit)? = null,
    onPrint: () -> Unit,
    onSharePdf: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrint, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Print, null, Modifier.size(18.dp))
                    Spacer(Modifier.padding(3.dp))
                    Text("طباعة PDF")
                }
                OutlinedButton(onClick = onSharePdf, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.padding(3.dp))
                    Text("مشاركة")
                }
            }
            if (extraActionLabel != null && onExtraAction != null) {
                OutlinedButton(onClick = onExtraAction, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                    Spacer(Modifier.padding(3.dp))
                    Text(extraActionLabel)
                }
            }
        }
    }
}
