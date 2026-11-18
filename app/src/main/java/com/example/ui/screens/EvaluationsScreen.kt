package com.example.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddChart
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CalendarMode
import com.example.model.GradeCatalog
import com.example.model.Student
import com.example.model.Subject
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldRoyal
import com.example.ui.theme.NavyPrimary
import com.example.util.CalendarUtils
import com.example.viewmodel.AcademyDashboardState
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationsScreen(
    state: AcademyDashboardState,
    onSaveEvaluation: (
        student: Student,
        subject: String,
        scores: Map<String, Long>,
        maxPerCriterion: Double,
        notes: String
    ) -> Unit
) {
    var selectedGroupId by remember(state.groups) { mutableStateOf<String?>(null) }
    var selectedStudentId by remember(state.activeStudents) { mutableStateOf(state.activeStudents.firstOrNull()?.id) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var studentMenuExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val availableStudents = remember(state.activeStudents, selectedGroupId) {
        if (selectedGroupId == null) state.activeStudents
        else state.activeStudents.filter { it.groupId == selectedGroupId }
    }
    val selectedStudent = availableStudents.firstOrNull { it.id == selectedStudentId }
        ?: availableStudents.firstOrNull()
    val studentEvaluations = remember(state.evaluations, selectedStudent) {
        if (selectedStudent == null) emptyList()
        else state.evaluations.filter { it.studentId == selectedStudent.id }
            .sortedByDescending { it.evaluatedDateMillis }
    }
    val mode = CalendarMode.fromKey(state.config.primaryCalendar)

    Scaffold(
        floatingActionButton = {
            if (selectedStudent != null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = NavyPrimary,
                    contentColor = GoldBright,
                    modifier = Modifier.testTag("fab_add_evaluation")
                ) {
                    Icon(Icons.Outlined.AddChart, contentDescription = "إضافة تقييم")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "التقييم ومتابعة المستوى",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.activeStudents.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Outlined.Assessment,
                        title = "لا يوجد طلاب للتقييم",
                        description = "سجّل الطلاب في المجموعات أولاً لإدخال تقييماتهم ومتابعة تطور مستواهم."
                    )
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = groupMenuExpanded,
                            onExpandedChange = { groupMenuExpanded = !groupMenuExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val grpName = state.groupById(selectedGroupId)?.name ?: "كل المجموعات"
                            OutlinedTextField(
                                value = grpName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("المجموعة") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupMenuExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = groupMenuExpanded,
                                onDismissRequest = { groupMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("كل المجموعات") },
                                    onClick = {
                                        selectedGroupId = null
                                        groupMenuExpanded = false
                                    }
                                )
                                state.groups.forEach { grp ->
                                    DropdownMenuItem(
                                        text = { Text(grp.name) },
                                        onClick = {
                                            selectedGroupId = grp.id
                                            groupMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = studentMenuExpanded,
                            onExpandedChange = { studentMenuExpanded = !studentMenuExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedStudent?.fullName ?: "اختر الطالب",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("الطالب") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(studentMenuExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = studentMenuExpanded,
                                onDismissRequest = { studentMenuExpanded = false }
                            ) {
                                availableStudents.forEach { stu ->
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
                    }
                }

                if (studentEvaluations.isNotEmpty()) {
                    item {
                        val avgPct = studentEvaluations.map { it.percentage }.average()
                        val overallGrade = GradeCatalog.classifyPercentage(avgPct)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, GoldRoyal.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("منحنى تطور مستوى الطالب", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "المتوسط العام: ${avgPct.toInt()}% • عدد التقييمات: ${studentEvaluations.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(overallGrade, PillTone.GOLD)
                                }
                                EvaluationProgressChart(
                                    evaluations = studentEvaluations,
                                    lineColor = GoldRoyal,
                                    modifier = Modifier.fillMaxWidth().height(145.dp)
                                )
                            }
                        }
                    }

                    items(studentEvaluations, key = { it.id }) { ev ->
                        val tone = when {
                            ev.percentage >= 85 -> PillTone.SUCCESS
                            ev.percentage >= 70 -> PillTone.INFO
                            ev.percentage >= 60 -> PillTone.WARNING
                            else -> PillTone.DANGER
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ev.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            CalendarUtils.formatDualDate(ev.evaluatedDateMillis, mode),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill("${ev.percentage.toInt()}% • ${ev.gradeLabel}", tone)
                                }
                                Text(
                                    ev.scores.entries.joinToString("  |  ") { "${it.key}: ${it.value}/25" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (ev.notes.isNotBlank()) {
                                    Text(
                                        "ملاحظات المعلم: ${ev.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        EmptyStatePanel(
                            icon = Icons.Outlined.Assessment,
                            title = "لا توجد تقييمات لهذا الطالب",
                            description = "أضف أول تقييم لقياس الفهم والواجبات والمشاركة والاختبارات وبناء الرسم البياني.",
                            actionLabel = "إضافة تقييم الآن",
                            onAction = { showAddDialog = true }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(64.dp)) }
        }
    }

    if (showAddDialog && selectedStudent != null) {
        AddEvaluationDialog(
            student = selectedStudent,
            criteria = state.config.evaluationCriteria,
            subjects = state.subjects.filter { it.active },
            onDismiss = { showAddDialog = false },
            onSave = { subject, scores, notes ->
                onSaveEvaluation(selectedStudent, subject, scores, 25.0, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddEvaluationDialog(
    student: Student,
    criteria: List<String>,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (subject: String, scores: Map<String, Long>, notes: String) -> Unit
) {
    var subject by remember { mutableStateOf(subjects.firstOrNull()?.name ?: "") }
    var notes by remember { mutableStateOf("") }
    val scoreMap = remember(criteria) {
        mutableStateMapOf<String, Long>().apply {
            criteria.forEach { put(it, 20L) }
        }
    }

    val total = scoreMap.values.sum().toDouble()
    val maxTotal = (criteria.size.coerceAtLeast(1) * 25.0)
    val pct = ((total / maxTotal) * 100.0).coerceIn(0.0, 100.0)
    val label = GradeCatalog.classifyPercentage(pct)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تقييم: ${student.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (subjects.isNotEmpty()) {
                    Text("اختر من المواد المضافة:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subjects, key = { it.id }) { sub ->
                            val dotColor = Color(sub.subjectColor.hexColor)
                            FilterChip(
                                selected = subject.trim() == sub.name,
                                onClick = { subject = sub.name },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                },
                                label = { Text(sub.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("المادة الدراسية *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("eval_subject_input")
                )
                StatusPill("النتيجة: ${pct.toInt()}% — $label", PillTone.GOLD)
                criteria.forEach { criterion ->
                    val currentVal = scoreMap[criterion] ?: 20L
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(criterion, style = MaterialTheme.typography.labelLarge)
                            Text("$currentVal / 25", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = currentVal.toFloat(),
                            onValueChange = { scoreMap[criterion] = it.roundToLong().coerceIn(0L, 25L) },
                            valueRange = 0f..25f
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات المعلم") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(subject, scoreMap.toMap(), notes) },
                enabled = subject.isNotBlank(),
                modifier = Modifier.testTag("save_evaluation_button")
            ) {
                Text("حفظ التقييم")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
