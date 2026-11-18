package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AcademyRepository
import com.example.data.StudentRepository
import com.example.model.AttendanceStatus
import com.example.model.CalendarMode
import com.example.model.EvaluationRecord
import com.example.model.GradeCatalog
import com.example.model.InitialPaymentChoice
import com.example.model.PaymentMethod
import com.example.model.Student
import com.example.model.StudentFeeStatusDisplay
import com.example.model.StudyGroup
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.components.StudentPaymentStatusBadge
import com.example.ui.theme.AmberContainerLight
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonContainerLight
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldContainerLight
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldRoyal
import com.example.util.CalendarUtils
import com.example.util.ExportAndPrintManager
import com.example.util.toArabicErrorMessage
import com.example.viewmodel.AcademyDashboardState
import kotlinx.coroutines.launch

@Composable
fun AddStudentFirestoreDialog(
    academyId: String,
    groups: List<StudyGroup>,
    calendarMode: CalendarMode = CalendarMode.GREGORIAN,
    currencySymbol: String = "ج.م",
    onDismiss: () -> Unit,
    onStudentAdded: (studentId: String) -> Unit
) {
    val context = LocalContext.current
    val studentRepository = remember(context) { StudentRepository(context) }
    val academyRepository = remember(context) { AcademyRepository(context) }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    StudentEditorDialog(
        initialStudent = null,
        groups = groups,
        calendarMode = calendarMode,
        currencySymbol = currencySymbol,
        isSaving = isSaving,
        errorMessage = submitError,
        onDismiss = { if (!isSaving) onDismiss() },
        onSave = { fullName, guardianName, phone, enrollmentDateMillis, gradeLevel, groupId, notes, prescribedFee, paidAmount, paymentMethod ->
            if (academyId.isBlank()) {
                submitError = "تعذر تحديد الأكاديمية الحالية. يرجى إعادة تسجيل الدخول."
                return@StudentEditorDialog
            }
            val selectedGroup = groups.firstOrNull { it.id == groupId }
            val newStudent = Student(
                fullName = fullName.trim(),
                guardianName = guardianName.trim(),
                phone = phone.trim(),
                enrollmentDateMillis = enrollmentDateMillis,
                gradeLevel = gradeLevel,
                groupId = groupId,
                teacherUids = selectedGroup?.teacherUids ?: emptyList(),
                prescribedFee = prescribedFee,
                notes = notes.trim(),
                archived = false
            )
            isSaving = true
            submitError = null
            scope.launch {
                studentRepository.createStudent(academyId, newStudent)
                    .onSuccess { createdId ->
                        val due = (prescribedFee ?: selectedGroup?.monthlyFee ?: 300.0).coerceAtLeast(paidAmount)
                        val paid = paidAmount.coerceIn(0.0, due)
                        if (paid > 0.0) {
                            academyRepository.savePayment(
                                academyId = academyId,
                                student = newStudent.copy(id = createdId),
                                monthKey = CalendarUtils.currentMonthKey(),
                                amountDue = due,
                                amountPaid = paid,
                                method = paymentMethod,
                                paidAtMillis = System.currentTimeMillis(),
                                notes = if (paid < due) "سداد جزئي عند التسجيل" else "سداد المصروفات عند التسجيل"
                            )
                        }
                        isSaving = false
                        onStudentAdded(createdId)
                    }
                    .onFailure { err ->
                        isSaving = false
                        submitError = err.toArabicErrorMessage()
                    }
            }
        }
    )
}

private fun digitsOnlyAscii(raw: String, maxLen: Int = 7): String {
    val sb = StringBuilder()
    for (ch in raw) {
        val mapped = when (ch) {
            in '0'..'9' -> ch
            in '٠'..'٩' -> ('0' + (ch - '٠'))
            in '۰'..'۹' -> ('0' + (ch - '۰'))
            else -> null
        }
        if (mapped != null) {
            sb.append(mapped)
            if (sb.length >= maxLen) break
        }
    }
    return sb.toString()
}

private data class StudentFormErrors(
    val fullNameError: String? = null,
    val guardianError: String? = null,
    val phoneError: String? = null,
    val gradeError: String? = null,
    val prescribedFeeError: String? = null,
    val paymentChoiceError: String? = null,
    val paidAmountError: String? = null,
    val notesError: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(
            fullNameError,
            guardianError,
            phoneError,
            gradeError,
            prescribedFeeError,
            paymentChoiceError,
            paidAmountError,
            notesError
        ).any { it != null }
}

private object StudentFormValidator {
    fun validate(
        isNewStudent: Boolean,
        fullName: String,
        guardianName: String,
        phone: String,
        gradeLevel: Long,
        prescribedFeeStr: String,
        paymentChoice: InitialPaymentChoice?,
        paidAmountStr: String,
        notes: String
    ): StudentFormErrors {
        val cleanName = fullName.trim()
        val nameErr = when {
            cleanName.isBlank() -> "يرجى إدخال الاسم الكامل للطالب"
            cleanName.length < 2 -> "يجب أن يتكون اسم الطالب من حرفين على الأقل"
            cleanName.length > 100 -> "اسم الطالب يجب ألا يتجاوز 100 حرف"
            else -> null
        }

        val cleanGuardian = guardianName.trim()
        val guardianErr = when {
            cleanGuardian.length > 100 -> "اسم ولي الأمر يجب ألا يتجاوز 100 حرف"
            else -> null
        }

        val cleanPhone = phone.trim()
        val digitsOnly = cleanPhone.filter { it.isDigit() }
        val phoneErr = when {
            cleanPhone.isBlank() -> "رقم الهاتف للتواصل مطلوب"
            !cleanPhone.matches(Regex("^\\+?[0-9\\s\\-()]{8,20}$")) || digitsOnly.length !in 8..15 ->
                "رقم الهاتف غير صالح (أدخل 8 إلى 15 رقماً)"
            cleanPhone.length > 30 -> "رقم الهاتف طويل جداً"
            else -> null
        }

        val gradeErr = if (gradeLevel !in 1L..12L) {
            "يرجى اختيار الصف الدراسي (من 1 إلى 12)"
        } else null

        val parsedFee = prescribedFeeStr.toLongOrNull()
        val feeErr = when {
            isNewStudent && prescribedFeeStr.isBlank() -> "يرجى إدخال الرسوم المقررة بالأرقام فقط"
            prescribedFeeStr.isNotBlank() && (parsedFee == null || parsedFee <= 0L) ->
                "يجب أن تكون الرسوم المقررة رقماً صحيحاً أكبر من صفر"
            parsedFee != null && parsedFee > 1_000_000L ->
                "قيمة الرسوم المقررة كبيرة جداً"
            else -> null
        }

        val choiceErr = if (isNewStudent && paymentChoice == null) {
            "لا يمكن المتابعة: يرجى اختيار «تم سداد المصروفات» أو «لم يسدد المصروفات»"
        } else null

        val paidErr = if (isNewStudent && paymentChoice == InitialPaymentChoice.PAID) {
            val parsedPaid = paidAmountStr.toLongOrNull()
            when {
                paidAmountStr.isBlank() -> "يرجى إدخال المبلغ المدفوع بالأرقام فقط"
                parsedPaid == null || parsedPaid <= 0L ->
                    "يرجى إدخال مبلغ أكبر من صفر أو اختيار «لم يسدد المصروفات»"
                parsedFee != null && parsedPaid > parsedFee ->
                    "المبلغ المدفوع ($parsedPaid) لا يمكن أن يتجاوز الرسوم المقررة ($parsedFee)"
                else -> null
            }
        } else null

        val notesErr = if (notes.trim().length > 500) {
            "الملاحظات يجب ألا تتجاوز 500 حرف"
        } else null

        return StudentFormErrors(
            fullNameError = nameErr,
            guardianError = guardianErr,
            phoneError = phoneErr,
            gradeError = gradeErr,
            prescribedFeeError = feeErr,
            paymentChoiceError = choiceErr,
            paidAmountError = paidErr,
            notesError = notesErr
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentEditorDialog(
    initialStudent: Student?,
    groups: List<StudyGroup>,
    calendarMode: CalendarMode = CalendarMode.GREGORIAN,
    currencySymbol: String = "ج.م",
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    val isNewStudent = initialStudent == null
    var fullName by remember { mutableStateOf(initialStudent?.fullName ?: "") }
    var guardianName by remember { mutableStateOf(initialStudent?.guardianName ?: "") }
    var phone by remember { mutableStateOf(initialStudent?.phone ?: "") }
    var gradeLevel by remember { mutableLongStateOf(initialStudent?.gradeLevel ?: 1L) }
    var selectedGroupId by remember { mutableStateOf(initialStudent?.groupId) }
    var notes by remember { mutableStateOf(initialStudent?.notes ?: "") }
    val enrollmentMillis = remember(initialStudent) {
        initialStudent?.enrollmentDateMillis ?: System.currentTimeMillis()
    }

    val initialGroupFee = remember(initialStudent, groups) {
        val g = groups.firstOrNull { it.id == initialStudent?.groupId }
        (initialStudent?.prescribedFee ?: g?.monthlyFee)?.toLong()?.takeIf { it > 0L }?.toString() ?: ""
    }
    var prescribedFeeStr by remember { mutableStateOf(initialGroupFee) }
    var feeManuallyEdited by remember { mutableStateOf(false) }
    var paymentChoice by remember { mutableStateOf<InitialPaymentChoice?>(null) }
    var paidAmountStr by remember { mutableStateOf("") }
    var paidManuallyEdited by remember { mutableStateOf(false) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }

    var gradeExpanded by remember { mutableStateOf(false) }
    var groupExpanded by remember { mutableStateOf(false) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var nameTouched by remember { mutableStateOf(false) }
    var phoneTouched by remember { mutableStateOf(false) }
    var feeTouched by remember { mutableStateOf(false) }
    var paidTouched by remember { mutableStateOf(false) }

    val availableGroups = remember(gradeLevel, groups) {
        groups.filter { it.gradeLevel == gradeLevel }
    }
    val validation = remember(
        isNewStudent, fullName, guardianName, phone, gradeLevel,
        prescribedFeeStr, paymentChoice, paidAmountStr, notes
    ) {
        StudentFormValidator.validate(
            isNewStudent = isNewStudent,
            fullName = fullName,
            guardianName = guardianName,
            phone = phone,
            gradeLevel = gradeLevel,
            prescribedFeeStr = prescribedFeeStr,
            paymentChoice = paymentChoice,
            paidAmountStr = paidAmountStr,
            notes = notes
        )
    }
    val showNameError = (attemptedSubmit || nameTouched) && validation.fullNameError != null
    val showGuardianError = (attemptedSubmit || guardianName.isNotEmpty()) && validation.guardianError != null
    val showPhoneError = (attemptedSubmit || phoneTouched) && validation.phoneError != null
    val showFeeError = (attemptedSubmit || feeTouched) && validation.prescribedFeeError != null
    val showChoiceError = attemptedSubmit && validation.paymentChoiceError != null
    val showPaidError = (attemptedSubmit || paidTouched) && validation.paidAmountError != null
    val showNotesError = (attemptedSubmit || notes.isNotEmpty()) && validation.notesError != null

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
    ) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) onDismiss() },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isNewStudent) "تسجيل طالب جديد" else "تعديل بيانات الطالب",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تاريخ التسجيل: ${CalendarUtils.formatDualDate(enrollmentMillis, calendarMode)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_form_error_summary")
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it.take(100)
                            nameTouched = true
                        },
                        label = { Text("الاسم الكامل للطالب *") },
                        placeholder = { Text("مثال: يوسف محمود حسن") },
                        isError = showNameError,
                        supportingText = if (showNameError) {
                            { Text(validation.fullNameError!!) }
                        } else null,
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_name_input")
                    )

                    OutlinedTextField(
                        value = guardianName,
                        onValueChange = { guardianName = it.take(100) },
                        label = { Text("اسم ولي الأمر") },
                        placeholder = { Text("مثال: محمود حسن") },
                        isError = showGuardianError,
                        supportingText = if (showGuardianError) {
                            { Text(validation.guardianError!!) }
                        } else null,
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_guardian_input")
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it.take(30)
                            phoneTouched = true
                        },
                        label = { Text("رقم الهاتف للتواصل *") },
                        placeholder = { Text("مثال: 01012345678") },
                        isError = showPhoneError,
                        supportingText = if (showPhoneError) {
                            { Text(validation.phoneError!!) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_phone_input")
                    )

                    ExposedDropdownMenuBox(
                        expanded = gradeExpanded && !isSaving,
                        onExpandedChange = { if (!isSaving) gradeExpanded = !gradeExpanded }
                    ) {
                        OutlinedTextField(
                            value = GradeCatalog.labelFor(gradeLevel.toInt()),
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isSaving,
                            isError = attemptedSubmit && validation.gradeError != null,
                            supportingText = if (attemptedSubmit && validation.gradeError != null) {
                                { Text(validation.gradeError!!) }
                            } else null,
                            label = { Text("الصف الدراسي (1 - 12) *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("student_grade_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = gradeExpanded,
                            onDismissRequest = { gradeExpanded = false }
                        ) {
                            GradeCatalog.grades.forEach { (level, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        gradeLevel = level.toLong()
                                        if (groups.none { it.id == selectedGroupId && it.gradeLevel == gradeLevel }) {
                                            selectedGroupId = null
                                        }
                                        gradeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = groupExpanded && !isSaving,
                        onExpandedChange = { if (!isSaving) groupExpanded = !groupExpanded }
                    ) {
                        val selectedGroupName = availableGroups.firstOrNull { it.id == selectedGroupId }?.name
                            ?: "قسم الوافدين الجدد (بدون مجموعة حالياً)"
                        OutlinedTextField(
                            value = selectedGroupName,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isSaving,
                            label = { Text("المجموعة الدراسية") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("student_group_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = groupExpanded,
                            onDismissRequest = { groupExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("قسم الوافدين الجدد (تعيين لاحقاً)") },
                                onClick = {
                                    selectedGroupId = null
                                    groupExpanded = false
                                }
                            )
                            availableGroups.forEach { grp ->
                                DropdownMenuItem(
                                    text = { Text("${grp.name} (${grp.scheduleText})") },
                                    onClick = {
                                        selectedGroupId = grp.id
                                        if (!feeManuallyEdited && grp.monthlyFee > 0.0) {
                                            val grpFeeStr = grp.monthlyFee.toLong().toString()
                                            prescribedFeeStr = grpFeeStr
                                            if (paymentChoice == InitialPaymentChoice.PAID && !paidManuallyEdited) {
                                                paidAmountStr = grpFeeStr
                                            }
                                        }
                                        groupExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // خانة دفع الرسوم المقررة (أرقام فقط)
                    OutlinedTextField(
                        value = prescribedFeeStr,
                        onValueChange = { raw ->
                            val cleanDigits = digitsOnlyAscii(raw)
                            prescribedFeeStr = cleanDigits
                            feeManuallyEdited = true
                            feeTouched = true
                            if (paymentChoice == InitialPaymentChoice.PAID && !paidManuallyEdited) {
                                paidAmountStr = cleanDigits
                            }
                        },
                        label = { Text("الرسوم المقررة ($currencySymbol — أرقام فقط)" + if (isNewStudent) " *" else "") },
                        placeholder = { Text("مثال: 350") },
                        isError = showFeeError,
                        supportingText = if (showFeeError) {
                            { Text(validation.prescribedFeeError!!) }
                        } else {
                            { Text("أدخل قيمة الرسوم المقررة بالأرقام فقط") }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_prescribed_fee_input")
                    )

                    if (isNewStudent) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (showChoiceError) 1.5.dp else 1.dp,
                                color = if (showChoiceError) CrimsonError else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "حالة سداد المصروفات (إلزامي قبل الإضافة) *",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showChoiceError) CrimsonError else MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val paidSelected = paymentChoice == InitialPaymentChoice.PAID
                                    Surface(
                                        onClick = {
                                            if (!isSaving) {
                                                paymentChoice = InitialPaymentChoice.PAID
                                                if (!paidManuallyEdited && paidAmountStr.isBlank()) {
                                                    paidAmountStr = prescribedFeeStr
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (paidSelected) EmeraldContainerLight else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (paidSelected) 2.dp else 1.dp,
                                            color = if (paidSelected) EmeraldSuccess else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("payment_status_paid_option")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = "تم سداد المصروفات",
                                                tint = if (paidSelected) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "تم سداد المصروفات",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (paidSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (paidSelected) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    val unpaidSelected = paymentChoice == InitialPaymentChoice.UNPAID
                                    Surface(
                                        onClick = {
                                            if (!isSaving) {
                                                paymentChoice = InitialPaymentChoice.UNPAID
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (unpaidSelected) CrimsonContainerLight else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (unpaidSelected) 2.dp else 1.dp,
                                            color = if (unpaidSelected) CrimsonError else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("payment_status_unpaid_option")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Cancel,
                                                contentDescription = "لم يسدد المصروفات",
                                                tint = if (unpaidSelected) CrimsonError else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "لم يسدد المصروفات",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (unpaidSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (unpaidSelected) CrimsonError else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                if (showChoiceError) {
                                    Text(
                                        text = validation.paymentChoiceError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CrimsonError,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("payment_choice_error_text")
                                    )
                                }

                                AnimatedVisibility(visible = paymentChoice == InitialPaymentChoice.PAID) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = paidAmountStr,
                                            onValueChange = { raw ->
                                                paidAmountStr = digitsOnlyAscii(raw)
                                                paidManuallyEdited = true
                                                paidTouched = true
                                            },
                                            label = { Text("المبلغ المدفوع الآن ($currencySymbol — أرقام فقط) *") },
                                            placeholder = { Text(prescribedFeeStr.ifBlank { "مثال: 200" }) },
                                            isError = showPaidError,
                                            supportingText = if (showPaidError) {
                                                { Text(validation.paidAmountError!!) }
                                            } else {
                                                { Text("اكتب المبلغ كاملًا أو جزءاً منه ليُحسب الباقي تلقائياً") }
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            enabled = !isSaving,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("student_paid_amount_input")
                                        )

                                        val feeVal = prescribedFeeStr.toDoubleOrNull() ?: 0.0
                                        val paidVal = paidAmountStr.toDoubleOrNull() ?: 0.0
                                        if (feeVal > 0.0 && paidVal > 0.0 && paidVal <= feeVal) {
                                            val rem = (feeVal - paidVal).coerceAtLeast(0.0)
                                            val isPartial = rem > 0.0
                                            Surface(
                                                color = if (isPartial) AmberContainerLight else EmeraldContainerLight,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (isPartial) {
                                                        "سداد جزئي: تم سداد ${CalendarUtils.formatCurrency(paidVal, currencySymbol)} • المتبقي ${CalendarUtils.formatCurrency(rem, currencySymbol)}"
                                                    } else {
                                                        "سداد كامل: سيظهر الطالب بحالة «مسدد للمصروفات»"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isPartial) AmberWarning else EmeraldSuccess,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        Text("طريقة السداد", style = MaterialTheme.typography.labelMedium)
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(PaymentMethod.entries) { method ->
                                                FilterChip(
                                                    selected = paymentMethod == method,
                                                    onClick = { paymentMethod = method },
                                                    label = { Text(method.arabicLabel) }
                                                )
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = paymentChoice == InitialPaymentChoice.UNPAID) {
                                    val feeVal = prescribedFeeStr.toDoubleOrNull() ?: 0.0
                                    Surface(
                                        color = CrimsonContainerLight,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (feeVal > 0.0) {
                                                "سيظهر تحت اسم الطالب: «لم يسدد المصروفات» (المطلوب ${CalendarUtils.formatCurrency(feeVal, currencySymbol)})"
                                            } else {
                                                "سيظهر تحت اسم الطالب: «لم يسدد المصروفات»"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CrimsonError,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(500) },
                        label = { Text("ملاحظات إضافية") },
                        isError = showNotesError,
                        supportingText = {
                            if (showNotesError) {
                                Text(validation.notesError!!)
                            } else {
                                Text("${notes.length}/500")
                            }
                        },
                        maxLines = 3,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_notes_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        attemptedSubmit = true
                        if (!validation.hasErrors) {
                            val parsedFee = prescribedFeeStr.toDoubleOrNull()
                            val paidAmount = if (isNewStudent && paymentChoice == InitialPaymentChoice.PAID) {
                                paidAmountStr.toDoubleOrNull() ?: 0.0
                            } else {
                                0.0
                            }
                            onSave(
                                fullName.trim(),
                                guardianName.trim(),
                                phone.trim(),
                                enrollmentMillis,
                                gradeLevel,
                                selectedGroupId,
                                notes.trim(),
                                parsedFee,
                                paidAmount,
                                paymentMethod
                            )
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("save_student_button")
                ) {
                    if (isSaving) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("جاري الحفظ...")
                    } else {
                        Text(if (isNewStudent) "إضافة الطالب" else "حفظ التعديلات")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier.testTag("cancel_student_button")
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun TransferGroupDialog(
    student: Student,
    groups: List<StudyGroup>,
    studentCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onSelectGroup: (StudyGroup?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نقل الطالب: ${student.fullName}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "يتم الاحتفاظ بسجل الحضور والمدفوعات والتقييمات عند النقل بين المجموعات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { onSelectGroup(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إعادة إلى قسم الوافدين الجدد")
                }
                groups.sortedBy { it.gradeLevel }.forEach { grp ->
                    val count = studentCounts[grp.id] ?: 0
                    val full = count >= grp.maxCapacity
                    Card(
                        onClick = { onSelectGroup(grp) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = BorderStroke(
                            1.dp,
                            if (grp.id == student.groupId) GoldRoyal else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(grp.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(
                                        GradeCatalog.shortLabelFor(grp.gradeLevel.toInt()),
                                        grp.subjectName.takeIf { it.isNotBlank() },
                                        grp.scheduleText.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = "$count / ${grp.maxCapacity}",
                                tone = if (full) PillTone.WARNING else PillTone.INFO
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
fun StudentDossierDialog(
    student: Student,
    state: AcademyDashboardState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val groupName = state.groupById(student.groupId)?.name ?: "الوافدون الجدد"
    val studentAttendance = state.attendance.filter { it.studentId == student.id }
    val currentMonth = remember { CalendarUtils.currentMonthKey() }
    val monthSummary = remember(state.payments, state.activeStudents, state.groups, student.id, currentMonth) {
        if (state.isAdmin) state.monthlyPaymentSummaries(currentMonth).firstOrNull { it.student.id == student.id }
        else null
    }
    val studentPayments = state.payments
        .filter { it.studentId == student.id && it.amountPaid > 0.0 }
        .sortedByDescending { it.paidAtMillis }
    val studentEvals = state.evaluations.filter { it.studentId == student.id }.sortedBy { it.evaluatedDateMillis }
    val presentCount = studentAttendance.count {
        it.status == AttendanceStatus.PRESENT.key || it.status == AttendanceStatus.LATE.key
    }
    val attRate = if (studentAttendance.isEmpty()) 100 else (presentCount * 100 / studentAttendance.size)
    val mode = CalendarMode.fromKey(state.config.primaryCalendar)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(student.fullName, style = MaterialTheme.typography.titleLarge)
                if (monthSummary != null) {
                    StudentPaymentStatusBadge(
                        status = StudentFeeStatusDisplay(
                            amountDue = monthSummary.amountDue,
                            amountPaid = monthSummary.amountPaid,
                            remaining = monthSummary.remaining,
                            currencySymbol = state.config.currencySymbol
                        )
                    )
                }
                Text(
                    "${GradeCatalog.labelFor(student.gradeLevel.toInt())} • $groupName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "ولي الأمر: ${student.guardianName.ifBlank { "-" }}  |  الهاتف: ${student.phone.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "الالتحاق: ${CalendarUtils.formatDualDate(student.enrollmentDateMillis, mode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("الحضور: $attRate% ($presentCount/${studentAttendance.size})", PillTone.SUCCESS)
                    StatusPill("التقييمات: ${studentEvals.size}", PillTone.GOLD)
                }

                if (studentEvals.isNotEmpty()) {
                    HorizontalDivider()
                    Text("مخطط تطور المستوى", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    EvaluationProgressChart(
                        evaluations = studentEvals,
                        lineColor = GoldRoyal,
                        modifier = Modifier.fillMaxWidth().height(130.dp)
                    )
                }

                if (state.isAdmin) {
                    HorizontalDivider()
                    Text("المدفوعات (${studentPayments.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (studentPayments.isEmpty()) {
                        Text("لا توجد دفعات مسجلة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        studentPayments.take(4).forEach { pay ->
                            Text(
                                "• ${CalendarUtils.formatMonthLabel(pay.monthKey, mode)}: ${CalendarUtils.formatCurrency(pay.amountPaid, state.config.currencySymbol)} (إيصال ${pay.receiptNumber})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (student.notes.isNotBlank()) {
                    HorizontalDivider()
                    Text("ملاحظات: ${student.notes}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ExportAndPrintManager.generateStudentDossierPdf(
                        context = context,
                        config = state.config,
                        student = student,
                        groupName = groupName,
                        attendance = studentAttendance,
                        payments = studentPayments,
                        evaluations = studentEvals,
                        printDirectly = true
                    )
                }
            ) {
                Icon(Icons.Outlined.Print, null)
                Spacer(Modifier.padding(3.dp))
                Text("طباعة كشف الطالب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun EvaluationProgressChart(
    evaluations: List<EvaluationRecord>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val pointColor = EmeraldSuccess
    val lowColor = CrimsonError
    val sorted = remember(evaluations) { evaluations.sortedBy { it.evaluatedDateMillis } }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width
                val h = size.height
                listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                    val y = h * ratio
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.5f
                    )
                }
                if (sorted.size == 1) {
                    val y = h * (1f - (sorted.first().percentage.toFloat() / 100f).coerceIn(0f, 1f))
                    drawCircle(color = lineColor, radius = 9f, center = Offset(w / 2f, y))
                } else if (sorted.size > 1) {
                    val stepX = w / (sorted.size - 1).coerceAtLeast(1)
                    val path = Path()
                    val points = sorted.mapIndexed { idx, rec ->
                        val x = idx * stepX
                        val y = h * (1f - (rec.percentage.toFloat() / 100f).coerceIn(0f, 1f))
                        Offset(x, y)
                    }
                    points.forEachIndexed { idx, pt ->
                        if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                    points.forEachIndexed { idx, pt ->
                        val pct = sorted[idx].percentage
                        val dotColor = if (pct >= 60.0) pointColor else lowColor
                        drawCircle(color = dotColor, radius = 8f, center = pt)
                    }
                }
            }
        }
    }
}
