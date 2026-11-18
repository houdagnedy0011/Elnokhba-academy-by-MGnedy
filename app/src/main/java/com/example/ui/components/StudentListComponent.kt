package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.StudentRepository
import com.example.model.CalendarMode
import com.example.model.GradeCatalog
import com.example.model.Student
import com.example.model.StudentFeeStatusDisplay
import com.example.model.UiState
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldRoyal
import com.example.ui.theme.NavyPrimary
import com.example.util.CalendarUtils
import com.example.util.toArabicErrorMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Self-contained RTL Arabic student list component that fetches live [Student] records
 * from Firestore via [StudentRepository] and displays them in a scrollable card layout.
 */
@Composable
fun FirestoreStudentList(
    academyId: String,
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
    includeArchived: Boolean = false,
    calendarMode: CalendarMode = CalendarMode.GREGORIAN,
    groupNameProvider: (String?) -> String = { if (it.isNullOrBlank()) "الوافدون الجدد" else it },
    paymentStatusProvider: ((Student) -> StudentFeeStatusDisplay?)? = null,
    onAddStudent: (() -> Unit)? = null,
    onViewDossier: (Student) -> Unit = {},
    onEdit: (Student) -> Unit = {},
    onTransfer: (Student) -> Unit = {},
    onArchive: (Student) -> Unit = {},
    onDeletePermanently: ((Student) -> Unit)? = null
) {
    val context = LocalContext.current
    val studentRepository = remember(context) { StudentRepository(context) }
    val currentUid = Firebase.auth.currentUser?.uid
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<UiState<List<Student>>>(UiState.Loading) }
    var retryKey by remember { mutableIntStateOf(0) }
    var confirmDeleteStudent by remember { mutableStateOf<Student?>(null) }

    LaunchedEffect(academyId, currentUid, isAdmin, includeArchived, retryKey) {
        if (currentUid.isNullOrBlank() || academyId.isBlank()) {
            uiState = UiState.Error("يرجى تسجيل الدخول بحساب Google لعرض قائمة الطلاب")
            return@LaunchedEffect
        }
        uiState = UiState.Loading
        studentRepository.observeStudents(academyId = academyId, uid = currentUid, isAdmin = isAdmin)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                uiState = UiState.Error((firestoreError ?: error).toArabicErrorMessage())
            }
            .collect { fetchedStudents ->
                val visible = if (includeArchived) {
                    fetchedStudents
                } else {
                    fetchedStudents.filter { !it.archived }
                }.sortedBy { it.fullName }
                uiState = UiState.Success(visible)
            }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (val state = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .testTag("student_list_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "جاري تحميل قائمة الطلاب...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("student_list_error"),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "خطأ في التحميل",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { retryKey++ },
                                modifier = Modifier.testTag("student_list_retry_button")
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("إعادة المحاولة")
                            }
                        }
                    }
                }
            }

            is UiState.Success -> {
                StudentListCardLayout(
                    students = state.data,
                    isAdmin = isAdmin,
                    calendarMode = calendarMode,
                    groupNameProvider = groupNameProvider,
                    paymentStatusProvider = paymentStatusProvider,
                    onAddStudent = onAddStudent,
                    onViewDossier = onViewDossier,
                    onEdit = onEdit,
                    onTransfer = onTransfer,
                    onArchive = onArchive,
                    onDeletePermanently = { stu ->
                        if (onDeletePermanently != null) {
                            onDeletePermanently(stu)
                        } else {
                            confirmDeleteStudent = stu
                        }
                    },
                    modifier = modifier
                )
            }
        }

        confirmDeleteStudent?.let { stu ->
            AlertDialog(
                onDismissRequest = { confirmDeleteStudent = null },
                title = { Text("تأكيد الحذف النهائي") },
                text = {
                    Text("هل أنت متأكد من حذف الطالب «${stu.fullName}» نهائياً من قاعدة البيانات؟ لا يمكن التراجع عن هذا الإجراء.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetId = stu.id
                            confirmDeleteStudent = null
                            scope.launch {
                                studentRepository.deleteStudent(academyId, targetId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonError)
                    ) {
                        Text("حذف نهائي")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteStudent = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

/**
 * Scrollable Arabic RTL card list for displaying [Student] records.
 * Supports optional [headerContent] (e.g., search & filter chips), [footerContent], and swipe-to-reveal edit/delete actions.
 */
@Composable
fun StudentListCardLayout(
    students: List<Student>,
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
    calendarMode: CalendarMode = CalendarMode.GREGORIAN,
    groupNameProvider: (String?) -> String = { if (it.isNullOrBlank()) "الوافدون الجدد" else it },
    paymentStatusProvider: ((Student) -> StudentFeeStatusDisplay?)? = null,
    emptyTitle: String = "لا يوجد طلاب مسجلون",
    emptyDescription: String = "أضف طلاباً جدداً لعرض بياناتهم ومتابعة الحضور والتقييم والمدفوعات.",
    onAddStudent: (() -> Unit)? = null,
    onViewDossier: (Student) -> Unit = {},
    onEdit: (Student) -> Unit = {},
    onTransfer: (Student) -> Unit = {},
    onArchive: (Student) -> Unit = {},
    onDeletePermanently: (Student) -> Unit = {},
    headerContent: (LazyListScope.() -> Unit)? = null,
    footerContent: (LazyListScope.() -> Unit)? = null
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("student_list_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            headerContent?.invoke(this)

            if (isAdmin && students.isNotEmpty()) {
                item(key = "swipe_hint_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Swipe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "اسحب بطاقة الطالب يميناً للتعديل أو يساراً للحذف النهائي",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (students.isEmpty()) {
                item(key = "empty_students_panel") {
                    EmptyStatePanel(
                        icon = Icons.Outlined.PersonAdd,
                        title = emptyTitle,
                        description = emptyDescription,
                        actionLabel = if (isAdmin && onAddStudent != null) "إضافة طالب" else null,
                        onAction = if (isAdmin) onAddStudent else null
                    )
                }
            } else {
                items(students, key = { it.id.ifBlank { it.fullName + it.enrollmentDateMillis } }) { student ->
                    val groupName = groupNameProvider(student.groupId)
                    val paymentStatus = if (isAdmin) paymentStatusProvider?.invoke(student) else null
                    SwipeableStudentCard(
                        student = student,
                        groupName = groupName,
                        calendarMode = calendarMode,
                        paymentStatus = paymentStatus,
                        isAdmin = isAdmin,
                        onViewDossier = { onViewDossier(student) },
                        onEdit = { onEdit(student) },
                        onTransfer = { onTransfer(student) },
                        onArchive = { onArchive(student) },
                        onDeletePermanently = { onDeletePermanently(student) }
                    )
                }
            }

            if (footerContent != null) {
                footerContent.invoke(this)
            } else {
                item(key = "student_list_bottom_spacer") {
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
    }
}

/**
 * Wraps [StudentListCard] with horizontal swipe-to-reveal actions for admins:
 * dragging right reveals the Edit option and dragging left reveals the Permanent Delete option.
 */
@Composable
private fun SwipeableStudentCard(
    student: Student,
    groupName: String,
    calendarMode: CalendarMode,
    paymentStatus: StudentFeeStatusDisplay?,
    isAdmin: Boolean,
    onViewDossier: () -> Unit,
    onEdit: () -> Unit,
    onTransfer: () -> Unit,
    onArchive: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    if (!isAdmin) {
        StudentListCard(
            student = student,
            groupName = groupName,
            calendarMode = calendarMode,
            paymentStatus = paymentStatus,
            isAdmin = false,
            onViewDossier = onViewDossier,
            onEdit = onEdit,
            onTransfer = onTransfer,
            onArchive = onArchive
        )
        return
    }

    val actionPanelWidth = 104.dp
    val actionWidthPx = with(LocalDensity.current) { actionPanelWidth.toPx() }
    val offsetX = remember(student.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val draggableState = rememberDraggableState { delta ->
        scope.launch {
            val next = (offsetX.value + delta).coerceIn(-actionWidthPx, actionWidthPx)
            offsetX.snapTo(next)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
    ) {
        // Use physical LTR coordinates for the revealed background panels so positive offsetX
        // (finger swipe right) cleanly uncovers the left Edit panel and negative offsetX
        // (finger swipe left) cleanly uncovers the right Delete panel.
        if (abs(offsetX.value) > 1f) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (offsetX.value > 1f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(actionPanelWidth)
                                .fillMaxHeight()
                                .background(NavyPrimary)
                                .clickable {
                                    scope.launch { offsetX.animateTo(0f) }
                                    onEdit()
                                }
                                .testTag("swipe_edit_student_${student.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "تعديل بيانات الطالب",
                                        tint = GoldBright,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Text(
                                        text = "تعديل",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldBright
                                    )
                                }
                            }
                        }
                    }

                    if (offsetX.value < -1f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(actionPanelWidth)
                                .fillMaxHeight()
                                .background(CrimsonError)
                                .clickable {
                                    scope.launch { offsetX.animateTo(0f) }
                                    onDeletePermanently()
                                }
                                .testTag("swipe_delete_student_${student.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteForever,
                                        contentDescription = "حذف الطالب نهائياً",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Text(
                                        text = "حذف نهائي",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        StudentListCard(
            student = student,
            groupName = groupName,
            calendarMode = calendarMode,
            paymentStatus = paymentStatus,
            isAdmin = true,
            onViewDossier = {
                if (abs(offsetX.value) > 4f) {
                    scope.launch {
                        offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                } else {
                    onViewDossier()
                }
            },
            onEdit = {
                scope.launch { offsetX.animateTo(0f) }
                onEdit()
            },
            onTransfer = {
                scope.launch { offsetX.animateTo(0f) }
                onTransfer()
            },
            onArchive = {
                scope.launch { offsetX.animateTo(0f) }
                onArchive()
            },
            modifier = Modifier
                .absoluteOffset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        scope.launch {
                            val current = offsetX.value
                            val target = when {
                                velocity > 550f -> actionWidthPx
                                velocity < -550f -> -actionWidthPx
                                current > actionWidthPx * 0.4f -> actionWidthPx
                                current < -actionWidthPx * 0.4f -> -actionWidthPx
                                else -> 0f
                            }
                            offsetX.animateTo(
                                targetValue = target,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    }
                )
        )
    }
}

/**
 * Individual RTL student card styled with the Navy & Gold academy palette,
 * displaying student identity, grade/group, guardian contact info, registration date, and quick actions.
 */
@Composable
fun StudentListCard(
    student: Student,
    groupName: String,
    calendarMode: CalendarMode,
    paymentStatus: StudentFeeStatusDisplay?,
    isAdmin: Boolean,
    onViewDossier: () -> Unit,
    onEdit: () -> Unit,
    onTransfer: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialChar = student.fullName.trim().firstOrNull()?.toString() ?: "ط"
    val registrationDateLabel = remember(student.enrollmentDateMillis, calendarMode) {
        CalendarUtils.formatDualDate(student.enrollmentDateMillis, calendarMode)
    }

    Card(
        onClick = onViewDossier,
        modifier = modifier
            .fillMaxWidth()
            .testTag("student_card_${student.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle Navy/Gold top header accent for visual depth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                NavyPrimary.copy(alpha = 0.09f),
                                GoldBright.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NavyPrimary)
                            .border(1.5.dp, GoldBright, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initialChar,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldBright
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = student.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (paymentStatus != null) {
                            StudentPaymentStatusBadge(
                                status = paymentStatus,
                                modifier = Modifier.testTag("student_payment_status_${student.id}")
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.School,
                                contentDescription = null,
                                tint = GoldRoyal,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${GradeCatalog.shortLabelFor(student.grade)} • $groupName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudentInfoLine(
                    icon = Icons.Outlined.Person,
                    label = "ولي الأمر: ${student.guardianName.ifBlank { "غير مسجل" }}"
                )
                StudentInfoLine(
                    icon = Icons.Outlined.Phone,
                    label = "الهاتف: ${student.phone.ifBlank { "غير مسجل" }}"
                )
                StudentInfoLine(
                    icon = Icons.Outlined.CalendarMonth,
                    label = "تاريخ الالتحاق: $registrationDateLabel"
                )
                if (student.notes.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Notes,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = student.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onViewDossier,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("view_student_dossier_${student.id}")
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = "كشف الطالب الشامل")
                }
                if (isAdmin) {
                    IconButton(
                        onClick = onTransfer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transfer_student_${student.id}")
                    ) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = "نقل المجموعة")
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_student_${student.id}")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "تعديل بيانات الطالب")
                    }
                    IconButton(
                        onClick = onArchive,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("archive_student_${student.id}")
                    ) {
                        Icon(Icons.Outlined.Archive, contentDescription = "نقل للأرشيف")
                    }
                }
            }
        }
    }
}

@Composable
fun StudentPaymentStatusBadge(
    status: StudentFeeStatusDisplay,
    modifier: Modifier = Modifier
) {
    val (label, tone) = when {
        status.isPaidInFull -> "مسدد للمصروفات" to PillTone.SUCCESS
        status.isPartiallyPaid -> {
            val paidText = CalendarUtils.formatCurrency(status.amountPaid, status.currencySymbol)
            val remainingText = CalendarUtils.formatCurrency(status.remaining, status.currencySymbol)
            "تم سداد $paidText • المتبقي $remainingText" to PillTone.WARNING
        }
        else -> {
            val dueText = CalendarUtils.formatCurrency(status.amountDue, status.currencySymbol)
            "لم يسدد المصروفات • المطلوب: $dueText" to PillTone.DANGER
        }
    }
    StatusPill(
        label = label,
        tone = tone,
        modifier = modifier
    )
}

@Composable
private fun StudentInfoLine(
    icon: ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
