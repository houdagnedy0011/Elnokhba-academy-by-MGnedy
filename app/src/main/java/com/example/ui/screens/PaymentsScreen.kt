package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CalendarMode
import com.example.model.MonthlyStudentPaymentSummary
import com.example.model.PaymentMethod
import com.example.model.PaymentRecord
import com.example.model.Student
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.KpiStatCard
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldRoyal
import com.example.util.CalendarUtils
import com.example.util.ExportAndPrintManager
import com.example.viewmodel.AcademyDashboardState

@Composable
fun PaymentsScreen(
    state: AcademyDashboardState,
    onRecordPayment: (
        student: Student,
        monthKey: String,
        amountDue: Double,
        amountPaid: Double,
        method: PaymentMethod,
        notes: String,
        onSaved: (PaymentRecord) -> Unit
    ) -> Unit
) {
    if (!state.isAdmin) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            EmptyStatePanel(
                icon = Icons.Outlined.Lock,
                title = "قسم مخصص لإدارة الأكاديمية",
                description = "إدارة المدفوعات والمصروفات الشهرية وإصدار الإيصالات متاحة لحساب المدير فقط."
            )
        }
        return
    }

    val context = LocalContext.current
    var selectedMonthKey by remember { mutableStateOf(CalendarUtils.currentMonthKey()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = لم يسددوا، 1 = سددوا
    var payingSummary by remember { mutableStateOf<MonthlyStudentPaymentSummary?>(null) }

    val mode = CalendarMode.fromKey(state.config.primaryCalendar)
    val monthLabel = CalendarUtils.formatMonthLabel(selectedMonthKey, mode)
    val summaries = remember(state.activeStudents, state.payments, state.groups, selectedMonthKey) {
        state.monthlyPaymentSummaries(selectedMonthKey)
    }
    val paidList = remember(summaries) { summaries.filter { it.remaining <= 0.0 && it.amountPaid > 0.0 } }
    val unpaidList = remember(summaries) { summaries.filter { it.remaining > 0.0 } }

    val totalCollected = summaries.sumOf { it.amountPaid }
    val totalPending = summaries.sumOf { it.remaining }
    val totalDue = summaries.sumOf { it.amountDue }
    val collectionPct = if (totalDue <= 0.0) 100 else ((totalCollected / totalDue) * 100).toInt().coerceIn(0, 100)

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "إدارة المدفوعات والإيصالات",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedMonthKey = CalendarUtils.shiftMonthKey(selectedMonthKey, -1) }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "الشهر السابق")
                        }
                        Text(monthLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { selectedMonthKey = CalendarUtils.shiftMonthKey(selectedMonthKey, 1) }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "الشهر التالي")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiStatCard(
                        title = "إجمالي المحصّل",
                        value = CalendarUtils.formatCurrency(totalCollected, state.config.currencySymbol),
                        subtitle = "نسبة السداد $collectionPct%",
                        icon = Icons.Outlined.Payments,
                        accentColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        title = "إجمالي المتأخرات",
                        value = CalendarUtils.formatCurrency(totalPending, state.config.currencySymbol),
                        subtitle = "${unpaidList.size} طالب متبقي",
                        icon = Icons.Outlined.ReceiptLong,
                        accentColor = AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("لم يسددوا (${unpaidList.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("الذين سددوا (${paidList.size})") }
                    )
                }
            }

            val displayed = if (selectedTab == 0) unpaidList else paidList
            if (displayed.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Outlined.Payments,
                        title = if (selectedTab == 0) "لا توجد متأخرات هذا الشهر" else "لا توجد مدفوعات مسجلة لهذا الشهر",
                        description = if (selectedTab == 0) "أتمّ جميع الطلاب النشطون سداد مصروفات هذا الشهر." else "سجّل دفعة جديدة من قائمة «لم يسددوا»."
                    )
                }
            } else {
                items(displayed, key = { it.student.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.student.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${item.groupName} • هاتف: ${item.student.phone.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    label = if (item.remaining <= 0.0) "مسدد بالكامل"
                                    else if (item.amountPaid > 0.0) "سداد جزئي"
                                    else "غير مسدد",
                                    tone = if (item.remaining <= 0.0) PillTone.SUCCESS
                                    else if (item.amountPaid > 0.0) PillTone.WARNING
                                    else PillTone.DANGER
                                )
                            }

                            Text(
                                "المستحق: ${CalendarUtils.formatCurrency(item.amountDue, state.config.currencySymbol)}  •  " +
                                    "المدفوع: ${CalendarUtils.formatCurrency(item.amountPaid, state.config.currencySymbol)}  •  " +
                                    "المتبقي: ${CalendarUtils.formatCurrency(item.remaining, state.config.currencySymbol)}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                item.latestPayment?.let { latest ->
                                    OutlinedButton(
                                        onClick = {
                                            ExportAndPrintManager.generatePaymentReceiptPdf(
                                                context = context,
                                                config = state.config,
                                                payment = latest,
                                                groupName = item.groupName,
                                                printDirectly = true
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Print, null, Modifier.size(18.dp))
                                        Spacer(Modifier.padding(3.dp))
                                        Text("طباعة الإيصال")
                                    }
                                }
                                if (item.remaining > 0.0) {
                                    Button(
                                        onClick = { payingSummary = item },
                                        modifier = Modifier.testTag("record_payment_${item.student.id}")
                                    ) {
                                        Text(if (item.amountPaid > 0.0) "استكمال السداد" else "تسجيل دفعة")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    payingSummary?.let { summary ->
        RecordPaymentDialog(
            summary = summary,
            currencySymbol = state.config.currencySymbol,
            onDismiss = { payingSummary = null },
            onConfirm = { due, paid, method, notes, printAfter ->
                onRecordPayment(summary.student, selectedMonthKey, due, paid, method, notes) { saved ->
                    if (printAfter) {
                        ExportAndPrintManager.generatePaymentReceiptPdf(
                            context = context,
                            config = state.config,
                            payment = saved,
                            groupName = summary.groupName,
                            printDirectly = true
                        )
                    }
                }
                payingSummary = null
            }
        )
    }
}

@Composable
private fun RecordPaymentDialog(
    summary: MonthlyStudentPaymentSummary,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (due: Double, paid: Double, method: PaymentMethod, notes: String, printReceipt: Boolean) -> Unit
) {
    var amountDueStr by remember { mutableStateOf(summary.amountDue.toInt().toString()) }
    var amountPaidStr by remember { mutableStateOf(summary.remaining.toInt().toString()) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل دفعة: ${summary.student.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "المجموعة: ${summary.groupName} • المتبقي حالياً: ${CalendarUtils.formatCurrency(summary.remaining, currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountDueStr,
                        onValueChange = { amountDueStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("المطلوب ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = amountPaidStr,
                        onValueChange = { amountPaidStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("الدفعة الآن ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("payment_amount_input")
                    )
                }
                Text("طريقة الدفع", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PaymentMethod.entries) { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method.arabicLabel) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات الإيصال") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val due = amountDueStr.toDoubleOrNull() ?: summary.amountDue
                        val paid = amountPaidStr.toDoubleOrNull() ?: 0.0
                        onConfirm(due, paid, selectedMethod, notes, false)
                    }
                ) { Text("حفظ") }
                Button(
                    onClick = {
                        val due = amountDueStr.toDoubleOrNull() ?: summary.amountDue
                        val paid = amountPaidStr.toDoubleOrNull() ?: 0.0
                        onConfirm(due, paid, selectedMethod, notes, true)
                    },
                    modifier = Modifier.testTag("save_and_print_payment_button")
                ) {
                    Text("حفظ وطباعة")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
