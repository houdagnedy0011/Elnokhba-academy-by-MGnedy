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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CalendarMode
import com.example.model.GradeCatalog
import com.example.model.PaymentMethod
import com.example.model.Student
import com.example.model.StudentFeeStatusDisplay
import com.example.model.StudyGroup
import com.example.model.Subject
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.components.StudentListCardLayout
import com.example.ui.components.StudentPaymentStatusBadge
import com.example.ui.theme.GoldBright
import com.example.ui.theme.NavyPrimary
import com.example.util.CalendarUtils
import com.example.viewmodel.AcademyDashboardState

@Composable
fun StudentsAndGroupsScreen(
    state: AcademyDashboardState,
    openAddStudentTrigger: Boolean,
    onConsumedAddStudentTrigger: () -> Unit,
    openGroupsTabTrigger: Boolean = false,
    onConsumedGroupsTabTrigger: () -> Unit = {},
    onSaveStudent: (
        existingId: String?,
        fullName: String,
        guardianName: String,
        phone: String,
        enrollmentDateMillis: Long,
        gradeLevel: Long,
        groupId: String?,
        notes: String,
        prescribedFee: Double?,
        initialPaidAmount: Double,
        paymentMethod: PaymentMethod
    ) -> Unit,
    onTransferStudent: (Student, StudyGroup?) -> Unit,
    onArchiveStudent: (String, Boolean) -> Unit,
    onDeleteStudentPermanently: (String) -> Unit,
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
    onSaveSubject: (
        existingId: String?,
        name: String,
        code: String,
        colorKey: String,
        description: String,
        active: Boolean
    ) -> Unit,
    onDeleteSubject: (String) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var gradeFilter by remember { mutableStateOf<Int?>(null) }
    var groupFilterId by remember { mutableStateOf<String?>(null) }
    var paymentFilter by remember { mutableIntStateOf(0) } // 0=الكل، 1=سدد، 2=لم يسدد

    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var showAddStudent by remember { mutableStateOf(false) }
    var transferringStudent by remember { mutableStateOf<Student?>(null) }
    var archiveConfirmStudent by remember { mutableStateOf<Student?>(null) }
    var permanentDeleteStudent by remember { mutableStateOf<Student?>(null) }
    var dossierStudent by remember { mutableStateOf<Student?>(null) }

    var openAddGroupTrigger by remember { mutableStateOf(false) }

    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    var showAddSubject by remember { mutableStateOf(false) }
    var deleteConfirmSubject by remember { mutableStateOf<Subject?>(null) }

    if (openAddStudentTrigger && state.isAdmin) {
        showAddStudent = true
        onConsumedAddStudentTrigger()
    }
    if (openGroupsTabTrigger) {
        selectedSubTab = 2
        onConsumedGroupsTabTrigger()
    }

    val currentMonth = remember { CalendarUtils.currentMonthKey() }
    val paymentSummaries = remember(state.payments, state.activeStudents, state.groups, currentMonth) {
        if (state.isAdmin) state.monthlyPaymentSummaries(currentMonth).associateBy { it.student.id }
        else emptyMap()
    }
    val groupCounts = remember(state.activeStudents) {
        state.activeStudents.mapNotNull { it.groupId }.groupingBy { it }.eachCount()
    }

    val filteredStudents = remember(
        state.activeStudents, searchQuery, gradeFilter, groupFilterId, paymentFilter, paymentSummaries
    ) {
        state.activeStudents.filter { stu ->
            val matchQuery = searchQuery.isBlank() ||
                stu.fullName.contains(searchQuery, ignoreCase = true) ||
                stu.phone.contains(searchQuery, ignoreCase = true) ||
                stu.guardianName.contains(searchQuery, ignoreCase = true)
            val matchGrade = gradeFilter == null || stu.gradeLevel.toInt() == gradeFilter
            val matchGroup = groupFilterId == null || stu.groupId == groupFilterId
            val matchPay = when (paymentFilter) {
                1 -> (paymentSummaries[stu.id]?.remaining ?: 0.0) <= 0.0
                2 -> (paymentSummaries[stu.id]?.remaining ?: 0.0) > 0.0
                else -> true
            }
            matchQuery && matchGrade && matchGroup && matchPay
        }
    }

    Scaffold(
        floatingActionButton = {
            if (state.isAdmin) {
                when (selectedSubTab) {
                    0, 1 -> FloatingActionButton(
                        onClick = { showAddStudent = true },
                        containerColor = NavyPrimary,
                        contentColor = GoldBright,
                        modifier = Modifier.testTag("fab_add_student")
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = "إضافة طالب")
                    }
                    2 -> FloatingActionButton(
                        onClick = { openAddGroupTrigger = true },
                        containerColor = NavyPrimary,
                        contentColor = GoldBright,
                        modifier = Modifier.testTag("fab_add_group")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "إضافة مجموعة")
                    }
                    3 -> FloatingActionButton(
                        onClick = { showAddSubject = true },
                        containerColor = NavyPrimary,
                        contentColor = GoldBright,
                        modifier = Modifier.testTag("fab_add_subject")
                    ) {
                        Icon(Icons.Outlined.MenuBook, contentDescription = "إضافة مادة دراسية")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val subTabs = listOf(
                "الطلاب (${state.activeStudents.size})",
                "الوافدون الجدد (${state.newcomers.size})",
                "المجموعات (${state.groups.size})",
                "المواد (${state.subjects.size})",
                "الأرشيف (${state.archivedStudents.size})"
            )
            ScrollableTabRow(selectedTabIndex = selectedSubTab, edgePadding = 8.dp) {
                subTabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedSubTab == idx,
                        onClick = { selectedSubTab = idx },
                        text = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag("students_subtab_$idx")
                    )
                }
            }

            when (selectedSubTab) {
                0 -> {
                    StudentListCardLayout(
                        students = filteredStudents,
                        isAdmin = state.isAdmin,
                        calendarMode = CalendarMode.fromKey(state.config.primaryCalendar),
                        groupNameProvider = { grpId -> state.groupById(grpId)?.name ?: "الوافدون الجدد" },
                        paymentStatusProvider = { stu ->
                            if (state.isAdmin) {
                                paymentSummaries[stu.id]?.let { summary ->
                                    StudentFeeStatusDisplay(
                                        amountDue = summary.amountDue,
                                        amountPaid = summary.amountPaid,
                                        remaining = summary.remaining,
                                        currencySymbol = state.config.currencySymbol
                                    )
                                }
                            } else null
                        },
                        emptyTitle = "لا يوجد طلاب مطابقون",
                        emptyDescription = "أضف طلاباً جدداً أو غيّر معايير البحث والفلترة.",
                        onAddStudent = if (state.isAdmin) { { showAddStudent = true } } else null,
                        onViewDossier = { stu -> dossierStudent = stu },
                        onEdit = { stu -> editingStudent = stu },
                        onTransfer = { stu -> transferringStudent = stu },
                        onArchive = { stu -> archiveConfirmStudent = stu },
                        onDeletePermanently = { stu -> permanentDeleteStudent = stu },
                        headerContent = {
                            item {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("بحث سريع بالاسم أو الهاتف أو ولي الأمر") },
                                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("student_search_input")
                                )
                            }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        FilterChip(
                                            selected = gradeFilter == null,
                                            onClick = { gradeFilter = null },
                                            label = { Text("كل الصفوف") },
                                            leadingIcon = { Icon(Icons.Outlined.FilterList, null, Modifier.size(16.dp)) }
                                        )
                                    }
                                    items(GradeCatalog.grades) { (lvl, _) ->
                                        FilterChip(
                                            selected = gradeFilter == lvl,
                                            onClick = { gradeFilter = if (gradeFilter == lvl) null else lvl },
                                            label = { Text(GradeCatalog.shortLabelFor(lvl)) }
                                        )
                                    }
                                }
                            }
                            if (state.isAdmin) {
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0 to "كل حالات الدفع", 1 to "سددوا الشهر", 2 to "لم يسددوا").forEach { (id, label) ->
                                            item {
                                                FilterChip(
                                                    selected = paymentFilter == id,
                                                    onClick = { paymentFilter = id },
                                                    label = { Text(label) }
                                                )
                                            }
                                        }
                                        items(state.groups) { grp ->
                                            FilterChip(
                                                selected = groupFilterId == grp.id,
                                                onClick = { groupFilterId = if (groupFilterId == grp.id) null else grp.id },
                                                label = { Text(grp.name) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "الطلاب المسجلون حديثاً بانتظار التوزيع على مجموعاتهم الدائمة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.newcomers.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Outlined.PersonAdd,
                                    title = "قسم الوافدين الجدد فارغ",
                                    description = "أي طالب يُسجل دون تحديد مجموعة يظهر هنا تلقائياً لتوزيعه لاحقاً."
                                )
                            }
                        } else {
                            items(state.newcomers, key = { it.id }) { stu ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(stu.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            if (state.isAdmin) {
                                                paymentSummaries[stu.id]?.let { summary ->
                                                    StudentPaymentStatusBadge(
                                                        status = StudentFeeStatusDisplay(
                                                            amountDue = summary.amountDue,
                                                            amountPaid = summary.amountPaid,
                                                            remaining = summary.remaining,
                                                            currencySymbol = state.config.currencySymbol
                                                        )
                                                    )
                                                }
                                            }
                                            Text(
                                                "${GradeCatalog.labelFor(stu.gradeLevel.toInt())} • ${stu.phone.ifBlank { "بدون هاتف" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (state.isAdmin) {
                                            Button(onClick = { transferringStudent = stu }) {
                                                Text("تعيين لمجموعة")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    GroupManagementScreen(
                        groups = state.groups,
                        subjects = state.subjects,
                        teachers = state.teachers,
                        studentCounts = groupCounts,
                        currencySymbol = state.config.currencySymbol,
                        isAdmin = state.isAdmin,
                        gradeFilter = gradeFilter,
                        onGradeFilterChange = { gradeFilter = it },
                        openAddGroupTrigger = openAddGroupTrigger,
                        onConsumedAddGroupTrigger = { openAddGroupTrigger = false },
                        onSaveGroup = onSaveGroup,
                        onDeleteGroup = onDeleteGroup,
                        onAddSubjectRequested = if (state.isAdmin) {
                            {
                                selectedSubTab = 3
                                showAddSubject = true
                            }
                        } else null
                    )
                }

                3 -> {
                    SubjectsTabContent(
                        subjects = state.subjects,
                        isAdmin = state.isAdmin,
                        onAddSubject = { showAddSubject = true },
                        onEditSubject = { sub -> editingSubject = sub },
                        onDeleteSubject = { sub -> deleteConfirmSubject = sub }
                    )
                }

                4 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.archivedStudents.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Outlined.Archive,
                                    title = "الأرشيف فارغ",
                                    description = "الطلاب المحذوفون يتم نقلهم هنا بأمان بدلاً من الحذف النهائي مع إمكانية استعادتهم."
                                )
                            }
                        } else {
                            items(state.archivedStudents, key = { it.id }) { stu ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(stu.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(GradeCatalog.labelFor(stu.gradeLevel.toInt()), style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (state.isAdmin) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedButton(onClick = { onArchiveStudent(stu.id, false) }) {
                                                    Icon(Icons.Outlined.RestoreFromTrash, null, Modifier.size(18.dp))
                                                    Spacer(Modifier.padding(3.dp))
                                                    Text("استعادة")
                                                }
                                                IconButton(
                                                    onClick = { permanentDeleteStudent = stu },
                                                    modifier = Modifier.testTag("delete_student_permanently_${stu.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.DeleteForever,
                                                        contentDescription = "حذف نهائي",
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
                }
            }
        }
    }

    if (showAddStudent || editingStudent != null) {
        StudentEditorDialog(
            initialStudent = editingStudent,
            groups = state.groups,
            calendarMode = CalendarMode.fromKey(state.config.primaryCalendar),
            currencySymbol = state.config.currencySymbol,
            onDismiss = {
                showAddStudent = false
                editingStudent = null
            },
            onSave = { fullName, guardian, phone, enrollMillis, grade, groupId, notes, prescribedFee, paidAmount, payMethod ->
                onSaveStudent(
                    editingStudent?.id,
                    fullName,
                    guardian,
                    phone,
                    enrollMillis,
                    grade,
                    groupId,
                    notes,
                    prescribedFee,
                    paidAmount,
                    payMethod
                )
                showAddStudent = false
                editingStudent = null
            }
        )
    }

    transferringStudent?.let { stu ->
        TransferGroupDialog(
            student = stu,
            groups = state.groups,
            studentCounts = groupCounts,
            onDismiss = { transferringStudent = null },
            onSelectGroup = { grp ->
                onTransferStudent(stu, grp)
                transferringStudent = null
            }
        )
    }

    archiveConfirmStudent?.let { stu ->
        AlertDialog(
            onDismissRequest = { archiveConfirmStudent = null },
            title = { Text("تأكيد نقل الطالب للأرشيف") },
            text = { Text("سيتم نقل الطالب «${stu.fullName}» إلى الأرشيف مع الاحتفاظ بجميع سجلاته، ويمكنك استعادته لاحقاً. هل تريد المتابعة؟") },
            confirmButton = {
                Button(onClick = {
                    onArchiveStudent(stu.id, true)
                    archiveConfirmStudent = null
                }) { Text("نقل للأرشيف") }
            },
            dismissButton = {
                TextButton(onClick = { archiveConfirmStudent = null }) { Text("إلغاء") }
            }
        )
    }

    permanentDeleteStudent?.let { stu ->
        AlertDialog(
            onDismissRequest = { permanentDeleteStudent = null },
            title = { Text("تأكيد الحذف النهائي") },
            text = { Text("هل أنت متأكد من حذف سجل الطالب «${stu.fullName}» نهائياً من قاعدة البيانات؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteStudentPermanently(stu.id)
                    permanentDeleteStudent = null
                }) { Text("حذف نهائي") }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteStudent = null }) { Text("إلغاء") }
            }
        )
    }

    dossierStudent?.let { stu ->
        StudentDossierDialog(
            student = stu,
            state = state,
            onDismiss = { dossierStudent = null }
        )
    }

    if (showAddSubject || editingSubject != null) {
        SubjectEditorDialog(
            initialSubject = editingSubject,
            existingSubjects = state.subjects,
            onDismiss = {
                showAddSubject = false
                editingSubject = null
            },
            onSave = { name, code, colorKey, description, active ->
                onSaveSubject(editingSubject?.id, name, code, colorKey, description, active)
                showAddSubject = false
                editingSubject = null
            }
        )
    }

    deleteConfirmSubject?.let { sub ->
        AlertDialog(
            onDismissRequest = { deleteConfirmSubject = null },
            title = { Text("حذف المادة الدراسية") },
            text = { Text("هل أنت متأكد من حذف مادة «${sub.name}» من قائمة مواد الأكاديمية؟") },
            confirmButton = {
                Button(onClick = {
                    onDeleteSubject(sub.id)
                    deleteConfirmSubject = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmSubject = null }) { Text("إلغاء") }
            }
        )
    }
}
