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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AttendanceStatus
import com.example.model.CalendarMode
import com.example.model.Student
import com.example.model.StudyGroup
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.InfoBlue
import com.example.util.CalendarUtils
import com.example.viewmodel.AcademyDashboardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    state: AcademyDashboardState,
    onRecordAttendance: (Student, StudyGroup, String, AttendanceStatus) -> Unit,
    onMarkAllPresent: (StudyGroup, List<Student>, String) -> Unit
) {
    var selectedGroupId by remember(state.groups) { mutableStateOf(state.groups.firstOrNull()?.id) }
    var selectedDateKey by remember { mutableStateOf(CalendarUtils.todayDateKey()) }
    var groupMenuExpanded by remember { mutableStateOf(false) }

    val selectedGroup = state.groups.firstOrNull { it.id == selectedGroupId } ?: state.groups.firstOrNull()
    val groupStudents = remember(selectedGroup, state.activeStudents) {
        if (selectedGroup == null) emptyList() else state.studentsInGroup(selectedGroup.id)
    }
    val mode = CalendarMode.fromKey(state.config.primaryCalendar)
    val dateLabel = CalendarUtils.formatDualDateFromKey(selectedDateKey, mode)
    val monthPrefix = selectedDateKey.take(7)

    val dayRecordsByStudent = remember(state.attendance, selectedDateKey, selectedGroup) {
        state.attendance
            .filter { it.dateKey == selectedDateKey && it.groupId == selectedGroup?.id }
            .associateBy { it.studentId }
    }
    val absenceAlertMap = remember(state.attendance, state.activeStudents, state.config.absenceAlertThreshold) {
        state.consecutiveAbsenceAlerts().associateBy { it.student.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "تسجيل الحضور والغياب",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.groups.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Outlined.Groups,
                    title = "لا توجد مجموعات متاحة",
                    description = "أنشئ مجموعة وأضف طلاباً إليها أولاً للبدء في تسجيل الحضور اليومي."
                )
            }
        } else {
            item {
                ExposedDropdownMenuBox(
                    expanded = groupMenuExpanded,
                    onExpandedChange = { groupMenuExpanded = !groupMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedGroup?.name ?: "اختر المجموعة",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("المجموعة الدراسية") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("attendance_group_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = groupMenuExpanded,
                        onDismissRequest = { groupMenuExpanded = false }
                    ) {
                        state.groups.forEach { grp ->
                            DropdownMenuItem(
                                text = { Text("${grp.name} (${state.studentsInGroup(grp.id).size} طالب)") },
                                onClick = {
                                    selectedGroupId = grp.id
                                    groupMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedDateKey = CalendarUtils.shiftDateKey(selectedDateKey, -1) }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "اليوم السابق")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(dateLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { selectedDateKey = CalendarUtils.todayDateKey() }) {
                                Text("العودة لليوم")
                            }
                        }
                        IconButton(onClick = { selectedDateKey = CalendarUtils.shiftDateKey(selectedDateKey, 1) }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "اليوم التالي")
                        }
                    }
                }
            }

            if (selectedGroup != null && groupStudents.isNotEmpty()) {
                item {
                    val presentCount = dayRecordsByStudent.values.count { it.status == AttendanceStatus.PRESENT.key }
                    val absentCount = dayRecordsByStudent.values.count { it.status == AttendanceStatus.ABSENT.key }
                    val lateCount = dayRecordsByStudent.values.count { it.status == AttendanceStatus.LATE.key }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill("حاضر: $presentCount", PillTone.SUCCESS)
                            StatusPill("غائب: $absentCount", PillTone.DANGER)
                            StatusPill("متأخر: $lateCount", PillTone.WARNING)
                        }
                        Button(
                            onClick = { onMarkAllPresent(selectedGroup, groupStudents, selectedDateKey) },
                            modifier = Modifier.testTag("mark_all_present_button")
                        ) {
                            Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp))
                            Spacer(Modifier.padding(3.dp))
                            Text("تحديد الكل حاضر")
                        }
                    }
                }

                items(groupStudents, key = { it.id }) { stu ->
                    val currentRecord = dayRecordsByStudent[stu.id]
                    val currentStatus = currentRecord?.let { AttendanceStatus.fromKey(it.status) }
                    val monthRecords = state.attendance.filter {
                        it.studentId == stu.id && it.dateKey.startsWith(monthPrefix)
                    }
                    val monthPresent = monthRecords.count {
                        it.status == AttendanceStatus.PRESENT.key || it.status == AttendanceStatus.LATE.key
                    }
                    val monthlyRate = if (monthRecords.isEmpty()) 100 else (monthPresent * 100 / monthRecords.size)
                    val absenceAlert = absenceAlertMap[stu.id]

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (absenceAlert != null) CrimsonError.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant
                        )
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
                                    Text(stu.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "نسبة الحضور الشهري: $monthlyRate% (${monthPresent}/${monthRecords.size})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (absenceAlert != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Outlined.WarningAmber, null, tint = CrimsonError, modifier = Modifier.size(18.dp))
                                        StatusPill("${absenceAlert.consecutiveCount} غياب متتالي", PillTone.DANGER)
                                    }
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(AttendanceStatus.entries) { status ->
                                    val selected = currentStatus == status
                                    val color = when (status) {
                                        AttendanceStatus.PRESENT -> EmeraldSuccess
                                        AttendanceStatus.ABSENT -> CrimsonError
                                        AttendanceStatus.LATE -> AmberWarning
                                        AttendanceStatus.EXCUSED -> InfoBlue
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onRecordAttendance(stu, selectedGroup, selectedDateKey, status) },
                                        label = { Text(status.arabicLabel) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = color.copy(alpha = 0.18f),
                                            selectedLabelColor = color
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedGroup != null) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Outlined.EventAvailable,
                        title = "المجموعة فارغة",
                        description = "لا يوجد طلاب مسجلون في مجموعة «${selectedGroup.name}» بعد."
                    )
                }
            }
        }
    }
}
