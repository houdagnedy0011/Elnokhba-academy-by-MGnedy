package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CalendarMode
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.KpiStatCard
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldRoyal
import com.example.ui.theme.InfoBlue
import com.example.util.CalendarUtils
import com.example.viewmodel.AcademyDashboardState

@Composable
fun DashboardScreen(
    state: AcademyDashboardState,
    onNavigateTab: (Int) -> Unit,
    onQuickAddStudent: () -> Unit,
    onNavigateGroups: () -> Unit = { onNavigateTab(1) }
) {
    val currentMonth = CalendarUtils.currentMonthKey()
    val mode = CalendarMode.fromKey(state.config.primaryCalendar)
    val monthLabel = CalendarUtils.formatMonthLabel(currentMonth, mode)
    val summaries = if (state.isAdmin) state.monthlyPaymentSummaries(currentMonth) else emptyList()
    val totalCollected = summaries.sumOf { it.amountPaid }
    val totalPending = summaries.sumOf { it.remaining }
    val unpaidCount = summaries.count { it.remaining > 0.0 }
    val absenceAlerts = state.consecutiveAbsenceAlerts()
    val topGroups = state.topGroups()
    val todayAttRate = state.todayAttendanceRate()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "لوحة التحكم الفورية",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.config.subtitle} • $monthLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiStatCard(
                            title = "الطلاب النشطون",
                            value = "${state.activeStudents.size}",
                            subtitle = "وافدون جدد: ${state.newcomers.size}",
                            icon = Icons.Outlined.School,
                            accentColor = InfoBlue,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateTab(1) }
                        )
                        KpiStatCard(
                            title = "الحضور اليومي",
                            value = "$todayAttRate%",
                            subtitle = "المجموعات: ${state.groups.size}",
                            icon = Icons.Outlined.FactCheck,
                            accentColor = EmeraldSuccess,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateTab(2) }
                        )
                        if (state.isAdmin) {
                            KpiStatCard(
                                title = "المحصّل هذا الشهر",
                                value = CalendarUtils.formatCurrency(totalCollected, state.config.currencySymbol),
                                subtitle = "متأخرات: ${CalendarUtils.formatCurrency(totalPending, state.config.currencySymbol)}",
                                icon = Icons.Outlined.Payments,
                                accentColor = GoldRoyal,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateTab(3) }
                            )
                        } else {
                            KpiStatCard(
                                title = "التقييمات المسجلة",
                                value = "${state.evaluations.size}",
                                subtitle = "مجموعاتي: ${state.groups.size}",
                                icon = Icons.Outlined.Assessment,
                                accentColor = GoldRoyal,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateTab(4) }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KpiStatCard(
                                title = "الطلاب النشطون",
                                value = "${state.activeStudents.size}",
                                subtitle = "وافدون جدد: ${state.newcomers.size}",
                                icon = Icons.Outlined.School,
                                accentColor = InfoBlue,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateTab(1) }
                            )
                            KpiStatCard(
                                title = "الحضور اليومي",
                                value = "$todayAttRate%",
                                subtitle = "المجموعات: ${state.groups.size}",
                                icon = Icons.Outlined.FactCheck,
                                accentColor = EmeraldSuccess,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateTab(2) }
                            )
                        }
                        if (state.isAdmin) {
                            KpiStatCard(
                                title = "إجمالي المحصّل للشهر",
                                value = CalendarUtils.formatCurrency(totalCollected, state.config.currencySymbol),
                                subtitle = "متأخرات $unpaidCount طالب: ${CalendarUtils.formatCurrency(totalPending, state.config.currencySymbol)}",
                                icon = Icons.Outlined.Payments,
                                accentColor = GoldRoyal,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onNavigateTab(3) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "إجراءات سريعة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isAdmin) {
                        item {
                            AssistChip(
                                onClick = onQuickAddStudent,
                                label = { Text("إضافة طالب") },
                                leadingIcon = { Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp)) },
                                modifier = Modifier.testTag("quick_add_student_chip")
                            )
                        }
                    }
                    item {
                        AssistChip(
                            onClick = onNavigateGroups,
                            label = { Text("إدارة المجموعات") },
                            leadingIcon = { Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("quick_groups_chip")
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onNavigateTab(2) },
                            label = { Text("رصد الحضور") },
                            leadingIcon = { Icon(Icons.Outlined.FactCheck, null, Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("quick_attendance_chip")
                        )
                    }
                    if (state.isAdmin) {
                        item {
                            AssistChip(
                                onClick = { onNavigateTab(3) },
                                label = { Text("تحصيل دفعة") },
                                leadingIcon = { Icon(Icons.Outlined.Payments, null, Modifier.size(18.dp)) },
                                modifier = Modifier.testTag("quick_payments_chip")
                            )
                        }
                    }
                    item {
                        AssistChip(
                            onClick = { onNavigateTab(4) },
                            label = { Text("إضافة تقييم") },
                            leadingIcon = { Icon(Icons.Outlined.Assessment, null, Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("quick_evaluations_chip")
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onNavigateTab(5) },
                            label = { Text("التقارير والطباعة") },
                            leadingIcon = { Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("quick_reports_chip")
                        )
                    }
                }
            }

            // قسم التنبيهات الذكية
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = AmberWarning)
                        Text(
                            text = "التنبيهات والمتابعة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    StatusPill(
                        label = "${absenceAlerts.size + if (state.isAdmin) unpaidCount else 0} تنبيه",
                        tone = if (absenceAlerts.isNotEmpty() || (state.isAdmin && unpaidCount > 0)) PillTone.WARNING else PillTone.SUCCESS
                    )
                }
            }

            if (absenceAlerts.isEmpty() && (!state.isAdmin || unpaidCount == 0)) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = EmeraldSuccess)
                            Text(
                                text = "ممتاز! لا توجد تنبيهات غياب متكرر أو متأخرات مالية حالياً.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                if (state.isAdmin && unpaidCount > 0) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Payments, null, tint = AmberWarning)
                                    Column {
                                        Text(
                                            "مدفوعات شهرية غير مكتملة",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "$unpaidCount طالب لم يُتموا السداد — متأخرات ${CalendarUtils.formatCurrency(totalPending, state.config.currencySymbol)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = { onNavigateTab(3) }) {
                                    Text("مراجعة")
                                }
                            }
                        }
                    }
                }

                items(absenceAlerts.take(5), key = { it.student.id }) { alert ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.WarningAmber, null, tint = CrimsonError)
                                Column {
                                    Text(
                                        alert.student.fullName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${alert.groupName} • هاتف ولي الأمر: ${alert.student.phone.ifBlank { "غير مسجل" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StatusPill(
                                label = "${alert.consecutiveCount} غيابات متتالية",
                                tone = PillTone.DANGER
                            )
                        }
                    }
                }
            }

            // أفضل المجموعات أداءً
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.EmojiEvents, null, tint = GoldRoyal)
                    Text(
                        text = "أفضل المجموعات أداءً",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (topGroups.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Outlined.Groups,
                        title = "لا توجد مجموعات بعد",
                        description = if (state.isAdmin) "ابدأ بإنشاء المجموعات الدراسية وإضافة الطلاب لمتابعة مؤشرات الأداء الفورية."
                        else "لم يتم تعيين مجموعات لحسابك بعد، تواصل مع مدير الأكاديمية.",
                        actionLabel = if (state.isAdmin) "إدارة المجموعات والطلاب" else null,
                        onAction = if (state.isAdmin) onNavigateGroups else null
                    )
                }
            } else {
                items(topGroups, key = { it.first.id }) { (grp, score) ->
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
                                Column {
                                    Text(grp.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "المعلم: ${grp.teacherName.ifBlank { "غير محدد" }} • الطلاب: ${state.studentsInGroup(grp.id).size}/${grp.maxCapacity}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(label = "$score%", tone = PillTone.GOLD)
                            }
                            LinearProgressIndicator(
                                progress = { (score / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = GoldRoyal
                            )
                        }
                    }
                }
            }
        }
    }
}
