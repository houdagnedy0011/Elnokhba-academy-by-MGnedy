package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.GradeCatalog
import com.example.model.StudyGroup
import com.example.model.Subject
import com.example.model.SubjectColor
import com.example.model.Teacher
import com.example.model.normalizeSubjectName
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldRoyal
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.util.CalendarUtils

private enum class GradeStageFilter(val label: String, val range: IntRange) {
    ALL("كل المراحل", 1..12),
    PRIMARY("الابتدائي", 1..6),
    PREP("الإعدادي", 7..9),
    SECONDARY("الثانوي", 10..12)
}

@Composable
fun GroupManagementScreen(
    groups: List<StudyGroup>,
    subjects: List<Subject>,
    teachers: List<Teacher>,
    studentCounts: Map<String, Int>,
    currencySymbol: String,
    isAdmin: Boolean,
    gradeFilter: Int? = null,
    onGradeFilterChange: (Int?) -> Unit = {},
    openAddGroupTrigger: Boolean = false,
    onConsumedAddGroupTrigger: () -> Unit = {},
    onSaveGroup: (
        existingId: String?,
        name: String,
        gradeLevel: Long,
        subjectId: String?,
        teacherId: String?,
        scheduleText: String,
        maxCapacity: Long,
        monthlyFee: Double
    ) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onAddSubjectRequested: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var stageFilter by remember { mutableStateOf(GradeStageFilter.ALL) }
    var subjectFilterId by remember { mutableStateOf<String?>(null) }
    var collapsedGrades by remember { mutableStateOf<Set<Int>>(emptySet()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var createDefaultGrade by remember { mutableLongStateOf((gradeFilter ?: 1).toLong()) }
    var editingGroup by remember { mutableStateOf<StudyGroup?>(null) }
    var deleteConfirmGroup by remember { mutableStateOf<StudyGroup?>(null) }

    LaunchedEffect(openAddGroupTrigger) {
        if (openAddGroupTrigger && isAdmin) {
            createDefaultGrade = (gradeFilter ?: 1).toLong()
            showCreateDialog = true
            onConsumedAddGroupTrigger()
        }
    }

    val subjectsById = remember(subjects) { subjects.associateBy { it.id } }
    val teachersById = remember(teachers) { teachers.associateBy { it.id } }

    val filteredGroups = remember(
        groups,
        searchQuery,
        stageFilter,
        gradeFilter,
        subjectFilterId,
        subjectsById,
        teachersById
    ) {
        groups.filter { grp ->
            val gradeInt = grp.gradeLevel.toInt()
            val matchStage = gradeInt in stageFilter.range
            val matchGrade = gradeFilter == null || gradeInt == gradeFilter
            val resolvedSubjectName = subjectsById[grp.subjectId]?.name ?: grp.subjectName
            val resolvedTeacherName = teachersById[grp.teacherId]?.name ?: grp.teacherName
            val matchSubject = subjectFilterId == null || grp.subjectId == subjectFilterId
            val matchQuery = searchQuery.isBlank() ||
                grp.name.contains(searchQuery, ignoreCase = true) ||
                resolvedSubjectName.contains(searchQuery, ignoreCase = true) ||
                resolvedTeacherName.contains(searchQuery, ignoreCase = true) ||
                grp.scheduleText.contains(searchQuery, ignoreCase = true)
            matchStage && matchGrade && matchSubject && matchQuery
        }.sortedWith(compareBy<StudyGroup> { it.gradeLevel }.thenBy { it.name })
    }

    val groupedByGrade = remember(filteredGroups) {
        filteredGroups.groupBy { it.gradeLevel.toInt() }.toSortedMap()
    }

    val totalEnrolled = remember(groups, studentCounts) {
        groups.sumOf { studentCounts[it.id] ?: 0 }
    }
    val totalCapacity = remember(groups) {
        groups.sumOf { it.maxCapacity.toInt() }
    }
    val activeGradesCount = remember(groups) {
        groups.map { it.gradeLevel }.distinct().size
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("group_management_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupOverviewBanner(
                totalGroups = groups.size,
                activeGradesCount = activeGradesCount,
                totalEnrolled = totalEnrolled,
                totalCapacity = totalCapacity,
                isAdmin = isAdmin,
                onCreateGroup = {
                    createDefaultGrade = (gradeFilter ?: stageFilter.range.first).toLong()
                    showCreateDialog = true
                }
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث باسم المجموعة، المادة، المعلم، أو الموعد") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GradeStageFilter.entries) { stage ->
                    FilterChip(
                        selected = stageFilter == stage && gradeFilter == null,
                        onClick = {
                            stageFilter = stage
                            onGradeFilterChange(null)
                        },
                        label = { Text(stage.label) },
                        modifier = Modifier.testTag("stage_filter_${stage.name}")
                    )
                }
                val visibleGrades = GradeCatalog.grades.filter { it.first in stageFilter.range }
                items(visibleGrades) { (lvl, _) ->
                    val countInGrade = groups.count { it.gradeLevel.toInt() == lvl }
                    val labelText = if (countInGrade > 0) {
                        "${GradeCatalog.shortLabelFor(lvl)} ($countInGrade)"
                    } else {
                        GradeCatalog.shortLabelFor(lvl)
                    }
                    FilterChip(
                        selected = gradeFilter == lvl,
                        onClick = { onGradeFilterChange(if (gradeFilter == lvl) null else lvl) },
                        label = { Text(labelText) },
                        modifier = Modifier.testTag("grade_filter_$lvl")
                    )
                }
            }
        }

        if (subjects.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = subjectFilterId == null,
                            onClick = { subjectFilterId = null },
                            label = { Text("كل المواد") },
                            leadingIcon = {
                                Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    items(subjects, key = { it.id }) { sub ->
                        val subColor = Color(sub.subjectColor.hexColor)
                        FilterChip(
                            selected = subjectFilterId == sub.id,
                            onClick = {
                                subjectFilterId = if (subjectFilterId == sub.id) null else sub.id
                            },
                            label = { Text(sub.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(subColor)
                                )
                            },
                            modifier = Modifier.testTag("group_subject_filter_${sub.id}")
                        )
                    }
                }
            }
        }

        if (groupedByGrade.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Outlined.Groups,
                    title = if (groups.isEmpty()) "لا توجد مجموعات دراسية بعد" else "لا توجد مجموعات مطابقة للفلترة",
                    description = "أنشئ مجموعة دراسية واربطها بالصف الدراسي والمادة والمعلم المسؤول وموعد الحصص.",
                    actionLabel = if (isAdmin) "إنشاء مجموعة جديدة" else null,
                    onAction = if (isAdmin) {
                        {
                            createDefaultGrade = (gradeFilter ?: stageFilter.range.first).toLong()
                            showCreateDialog = true
                        }
                    } else null
                )
            }
        } else {
            groupedByGrade.forEach { (gradeLevel, gradeGroups) ->
                val isCollapsed = gradeLevel in collapsedGrades
                val gradeStudentCount = gradeGroups.sumOf { studentCounts[it.id] ?: 0 }

                item(key = "grade_header_$gradeLevel") {
                    GradeLevelSectionHeader(
                        gradeLevel = gradeLevel,
                        groupCount = gradeGroups.size,
                        studentCount = gradeStudentCount,
                        isCollapsed = isCollapsed,
                        isAdmin = isAdmin,
                        onToggleCollapse = {
                            collapsedGrades = if (isCollapsed) {
                                collapsedGrades - gradeLevel
                            } else {
                                collapsedGrades + gradeLevel
                            }
                        },
                        onAddGroupForGrade = {
                            createDefaultGrade = gradeLevel.toLong()
                            showCreateDialog = true
                        }
                    )
                }

                if (!isCollapsed) {
                    items(gradeGroups, key = { it.id }) { grp ->
                        val linkedSubject = subjectsById[grp.subjectId]
                        val linkedTeacher = teachersById[grp.teacherId]
                        GroupManagementCard(
                            group = grp,
                            subject = linkedSubject,
                            teacher = linkedTeacher,
                            enrolledCount = studentCounts[grp.id] ?: 0,
                            currencySymbol = currencySymbol,
                            isAdmin = isAdmin,
                            onEdit = { editingGroup = grp },
                            onDelete = { deleteConfirmGroup = grp }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showCreateDialog || editingGroup != null) {
        GroupEditorDialog(
            initialGroup = editingGroup,
            defaultGrade = createDefaultGrade,
            subjects = subjects,
            teachers = teachers,
            currencySymbol = currencySymbol,
            onDismiss = {
                showCreateDialog = false
                editingGroup = null
            },
            onAddSubjectRequested = onAddSubjectRequested,
            onSave = { name, grade, subjectId, teacherId, schedule, maxCap, fee ->
                onSaveGroup(editingGroup?.id, name, grade, subjectId, teacherId, schedule, maxCap, fee)
                showCreateDialog = false
                editingGroup = null
            }
        )
    }

    deleteConfirmGroup?.let { grp ->
        AlertDialog(
            onDismissRequest = { deleteConfirmGroup = null },
            title = { Text("حذف المجموعة") },
            text = { Text("هل أنت متأكد من حذف مجموعة «${grp.name}»؟") },
            confirmButton = {
                Button(onClick = {
                    onDeleteGroup(grp.id)
                    deleteConfirmGroup = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmGroup = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun GroupOverviewBanner(
    totalGroups: Int,
    activeGradesCount: Int,
    totalEnrolled: Int,
    totalCapacity: Int,
    isAdmin: Boolean,
    onCreateGroup: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(NavyDeep, NavyPrimary)
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "إدارة المجموعات الدراسية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "موزعة حسب الصفوف ومرتبطة بالمواد والمعلمين",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                    if (isAdmin) {
                        Button(
                            onClick = onCreateGroup,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("create_group_header_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("مجموعة جديدة")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BannerStatTile(
                        label = "المجموعات",
                        value = "$totalGroups",
                        modifier = Modifier.weight(1f)
                    )
                    BannerStatTile(
                        label = "الصفوف النشطة",
                        value = "$activeGradesCount / 12",
                        modifier = Modifier.weight(1f)
                    )
                    BannerStatTile(
                        label = "الإشغال الكلي",
                        value = "$totalEnrolled / ${totalCapacity.coerceAtLeast(0)}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = GoldBright
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GradeLevelSectionHeader(
    gradeLevel: Int,
    groupCount: Int,
    studentCount: Int,
    isCollapsed: Boolean,
    isAdmin: Boolean,
    onToggleCollapse: () -> Unit,
    onAddGroupForGrade: () -> Unit
) {
    val stageName = when (gradeLevel) {
        in 1..6 -> "المرحلة الابتدائية"
        in 7..9 -> "المرحلة الإعدادية"
        else -> "المرحلة الثانوية"
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCollapse)
            .testTag("grade_section_header_$gradeLevel")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = GoldRoyal.copy(alpha = 0.16f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            tint = GoldRoyal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = GradeCatalog.labelFor(gradeLevel),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$stageName • $groupCount مجموعة • $studentCount طالب",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAdmin) {
                    IconButton(
                        onClick = onAddGroupForGrade,
                        modifier = Modifier.testTag("add_group_for_grade_$gradeLevel")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircleOutline,
                            contentDescription = "إضافة مجموعة في ${GradeCatalog.labelFor(gradeLevel)}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(
                    imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = if (isCollapsed) "توسيع الصف" else "طي الصف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupManagementCard(
    group: StudyGroup,
    subject: Subject?,
    teacher: Teacher?,
    enrolledCount: Int,
    currencySymbol: String,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val resolvedSubjectName = subject?.name ?: group.subjectName
    val resolvedColor = Color((subject?.subjectColor ?: group.subjectColor).hexColor)
    val resolvedTeacherName = teacher?.name ?: group.teacherName
    val capacity = group.maxCapacity.coerceAtLeast(1L).toInt()
    val progress = (enrolledCount.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
    val isFull = enrolledCount >= capacity

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group_card_${group.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(resolvedColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(
                        label = "$enrolledCount / $capacity طالب",
                        tone = if (isFull) PillTone.WARNING else PillTone.INFO
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Linked Subject Pill
                    Surface(
                        color = resolvedColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, resolvedColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MenuBook,
                                contentDescription = null,
                                tint = resolvedColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = resolvedSubjectName.ifBlank { "مادة غير محددة" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = resolvedColor
                            )
                            if (subject?.code?.isNotBlank() == true) {
                                Text(
                                    text = "(${subject.code})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = resolvedColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Linked Teacher Pill
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (resolvedTeacherName.isBlank()) "بدون معلم"
                                else if (!teacher?.subject.isNullOrBlank()) "$resolvedTeacherName • ${teacher?.subject}"
                                else resolvedTeacherName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isFull) AmberWarning else resolvedColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = group.scheduleText.ifBlank { "الموعد غير محدد" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "الرسوم الشهرية: ${CalendarUtils.formatCurrency(group.monthlyFee, currencySymbol)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (isAdmin) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.testTag("edit_group_${group.id}")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "تعديل المجموعة")
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.testTag("delete_group_${group.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "حذف المجموعة",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorDialog(
    initialGroup: StudyGroup?,
    defaultGrade: Long,
    subjects: List<Subject>,
    teachers: List<Teacher>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onAddSubjectRequested: (() -> Unit)? = null,
    onSave: (
        name: String,
        gradeLevel: Long,
        subjectId: String?,
        teacherId: String?,
        scheduleText: String,
        maxCapacity: Long,
        monthlyFee: Double
    ) -> Unit
) {
    val activeSubjects = remember(subjects) { subjects.filter { it.active } }
    val selectableSubjects = remember(subjects, initialGroup) {
        val currentId = initialGroup?.subjectId
        if (currentId != null && subjects.any { it.id == currentId && !it.active }) {
            subjects
        } else {
            activeSubjects
        }
    }

    var name by remember { mutableStateOf(initialGroup?.name ?: "") }
    var gradeLevel by remember { mutableLongStateOf(initialGroup?.gradeLevel ?: defaultGrade) }
    var selectedSubjectId by remember {
        mutableStateOf(
            initialGroup?.subjectId?.takeIf { id -> subjects.any { it.id == id } }
                ?: selectableSubjects.firstOrNull()?.id
        )
    }
    var selectedTeacherId by remember { mutableStateOf(initialGroup?.teacherId) }
    var scheduleText by remember { mutableStateOf(initialGroup?.scheduleText ?: "") }
    var maxCapacity by remember { mutableStateOf((initialGroup?.maxCapacity ?: 20L).toString()) }
    var monthlyFee by remember { mutableStateOf((initialGroup?.monthlyFee?.toInt() ?: 300).toString()) }

    var gradeExpanded by remember { mutableStateOf(false) }
    var subjectExpanded by remember { mutableStateOf(false) }
    var teacherExpanded by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var nameTouched by remember { mutableStateOf(false) }

    val selectedSubject = selectableSubjects.firstOrNull { it.id == selectedSubjectId }
    val normalizedSelectedSubject = selectedSubject?.let { normalizeSubjectName(it.name) }

    // Order teachers so teachers specialized in the selected subject appear first
    val orderedTeachers = remember(teachers, normalizedSelectedSubject) {
        if (normalizedSelectedSubject.isNullOrBlank()) {
            teachers.sortedByDescending { it.active }
        } else {
            teachers.sortedWith(
                compareByDescending<Teacher> { t ->
                    t.subject.isNotBlank() &&
                        (normalizeSubjectName(t.subject) == normalizedSelectedSubject ||
                            normalizeSubjectName(t.subject).contains(normalizedSelectedSubject))
                }.thenByDescending { it.active }.thenBy { it.name }
            )
        }
    }

    val cleanName = name.trim()
    val nameError = when {
        cleanName.isBlank() -> "يرجى إدخال اسم المجموعة"
        cleanName.length < 2 -> "اسم المجموعة يجب أن يكون حرفين على الأقل"
        cleanName.length > 80 -> "اسم المجموعة يجب ألا يتجاوز 80 حرفاً"
        else -> null
    }
    val parsedCapacity = maxCapacity.toLongOrNull()
    val capacityError = if (parsedCapacity == null || parsedCapacity !in 1L..500L) {
        "أدخل سعة بين 1 و 500"
    } else null
    val parsedFee = monthlyFee.toDoubleOrNull()
    val feeError = if (parsedFee == null || parsedFee < 0.0) {
        "أدخل قيمة رسوم صالحة"
    } else null

    val showNameError = (attemptedSubmit || nameTouched) && nameError != null

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = if (initialGroup == null) "إنشاء مجموعة دراسية" else "تعديل المجموعة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(80)
                            nameTouched = true
                        },
                        label = { Text("اسم المجموعة *") },
                        placeholder = { Text("مثال: مجموعة التفوق - رياضيات") },
                        isError = showNameError,
                        supportingText = if (showNameError) {
                            { Text(nameError!!) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_name_input")
                    )

                    ExposedDropdownMenuBox(
                        expanded = gradeExpanded,
                        onExpandedChange = { gradeExpanded = !gradeExpanded }
                    ) {
                        OutlinedTextField(
                            value = GradeCatalog.labelFor(gradeLevel.toInt()),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الصف الدراسي *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(gradeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("group_grade_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = gradeExpanded,
                            onDismissRequest = { gradeExpanded = false }
                        ) {
                            GradeCatalog.grades.forEach { (lvl, lbl) ->
                                DropdownMenuItem(
                                    text = { Text(lbl) },
                                    onClick = {
                                        gradeLevel = lvl.toLong()
                                        gradeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Linked Subject Selector
                    if (selectableSubjects.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = subjectExpanded,
                            onExpandedChange = { subjectExpanded = !subjectExpanded }
                        ) {
                            val subjectDisplay = selectedSubject?.let {
                                if (it.code.isNotBlank()) "${it.name} (${it.code})" else it.name
                            } ?: "بدون ربط بمادة حالياً"

                            OutlinedTextField(
                                value = subjectDisplay,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("المادة الدراسية المرتبطة") },
                                leadingIcon = {
                                    val dotColor = selectedSubject?.let { Color(it.subjectColor.hexColor) }
                                        ?: MaterialTheme.colorScheme.onSurfaceVariant
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("group_subject_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = subjectExpanded,
                                onDismissRequest = { subjectExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("بدون تحديد مادة") },
                                    onClick = {
                                        selectedSubjectId = null
                                        subjectExpanded = false
                                    }
                                )
                                selectableSubjects.forEach { sub ->
                                    val subColor = Color(sub.subjectColor.hexColor)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(subColor)
                                                )
                                                Text(
                                                    if (sub.code.isNotBlank()) "${sub.name} (${sub.code})" else sub.name
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedSubjectId = sub.id
                                            subjectExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(selectableSubjects, key = { it.id }) { sub ->
                                val subColor = Color(sub.subjectColor.hexColor)
                                val isSelected = selectedSubjectId == sub.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedSubjectId = if (isSelected) null else sub.id
                                    },
                                    label = { Text(sub.name) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(subColor)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = subColor.copy(alpha = 0.16f)
                                    )
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "لم تُضف مواد دراسية بعد",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (onAddSubjectRequested != null) {
                                    OutlinedButton(onClick = {
                                        onDismiss()
                                        onAddSubjectRequested()
                                    }) {
                                        Text("إضافة مادة")
                                    }
                                }
                            }
                        }
                    }

                    // Linked Teacher Selector
                    ExposedDropdownMenuBox(
                        expanded = teacherExpanded,
                        onExpandedChange = { teacherExpanded = !teacherExpanded }
                    ) {
                        val selectedTeacher = teachers.firstOrNull { it.id == selectedTeacherId }
                        val teacherLabel = selectedTeacher?.let { t ->
                            if (t.subject.isNotBlank()) "${t.name} (${t.subject})" else t.name
                        } ?: "غير معين حالياً"
                        OutlinedTextField(
                            value = teacherLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("المعلم المسؤول") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(teacherExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("group_teacher_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = teacherExpanded,
                            onDismissRequest = { teacherExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("بدون معلم حالياً") },
                                onClick = {
                                    selectedTeacherId = null
                                    teacherExpanded = false
                                }
                            )
                            orderedTeachers.forEach { t ->
                                val matchesSubject = !normalizedSelectedSubject.isNullOrBlank() &&
                                    t.subject.isNotBlank() &&
                                    (normalizeSubjectName(t.subject) == normalizedSelectedSubject ||
                                        normalizeSubjectName(t.subject).contains(normalizedSelectedSubject))
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${t.name} (${t.subject.ifBlank { "عام" }})")
                                            if (matchesSubject) {
                                                Text(
                                                    text = "• مطابق",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedTeacherId = t.id
                                        teacherExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = scheduleText,
                        onValueChange = { scheduleText = it.take(120) },
                        label = { Text("موعد الحصص (مثال: السبت والثلاثاء 5م)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_schedule_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = maxCapacity,
                            onValueChange = { maxCapacity = it.filter(Char::isDigit).take(3) },
                            label = { Text("الحد الأقصى *") },
                            isError = attemptedSubmit && capacityError != null,
                            supportingText = if (attemptedSubmit && capacityError != null) {
                                { Text(capacityError) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_capacity_input")
                        )
                        OutlinedTextField(
                            value = monthlyFee,
                            onValueChange = { monthlyFee = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("الرسوم ($currencySymbol) *") },
                            isError = attemptedSubmit && feeError != null,
                            supportingText = if (attemptedSubmit && feeError != null) {
                                { Text(feeError) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_fee_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        attemptedSubmit = true
                        if (nameError == null && capacityError == null && feeError == null) {
                            onSave(
                                cleanName,
                                gradeLevel,
                                selectedSubjectId,
                                selectedTeacherId,
                                scheduleText.trim(),
                                (parsedCapacity ?: 20L).coerceIn(1L, 500L),
                                (parsedFee ?: 300.0).coerceIn(0.0, 1000000.0)
                            )
                        }
                    },
                    modifier = Modifier.testTag("save_group_button")
                ) {
                    Text(if (initialGroup == null) "إنشاء المجموعة" else "حفظ التعديلات")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        )
    }
}
