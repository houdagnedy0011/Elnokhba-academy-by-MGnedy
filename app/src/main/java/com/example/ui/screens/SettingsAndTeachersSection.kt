package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.CalendarMode
import com.example.model.GradeCatalog
import com.example.model.StudyGroup
import com.example.model.Subject
import com.example.model.Teacher
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.util.InviteLinkHelper
import com.example.viewmodel.AcademyDashboardState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachersManagementSection(
    state: AcademyDashboardState,
    onSaveTeacher: (
        existingId: String?,
        name: String,
        phone: String,
        subject: String,
        email: String?,
        assignedGroupIds: List<String>,
        newGroupName: String,
        newGroupGradeLevel: Long,
        allowTeacherGroupChoice: Boolean,
        active: Boolean
    ) -> Unit,
    onNotify: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var editingTeacher by remember { mutableStateOf<Teacher?>(null) }
    var showAddTeacher by remember { mutableStateOf(false) }

    val invitesByCode = remember(state.invites) {
        state.invites.associateBy { it.id }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "إدارة حسابات المعلمين والدعوات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "دعوة المعلمين برمز أو رابط مباشر مع تحديد المجموعة والمادة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showAddTeacher = true },
                modifier = Modifier.testTag("add_teacher_invite_button")
            ) {
                Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("دعوة معلم")
            }
        }

        if (state.teachers.isEmpty()) {
            Text(
                "لم تتم إضافة معلمين بعد. عند دعوة معلم يتم توليد كود ورابط دعوة مباشر مع إمكانية تحديد المجموعة والمادة مسبقاً أو ترك المعلم يختارها عند التسجيل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.teachers.forEach { teacher ->
                val invite = teacher.inviteCode?.let { invitesByCode[it] }
                val assignedNames = teacher.assignedGroupIds
                    .mapNotNull { state.groupById(it)?.name }
                    .ifEmpty {
                        listOfNotNull(invite?.groupName?.takeIf { it.isNotBlank() })
                    }
                val resolvedSubject = teacher.subject.ifBlank { invite?.subjectName.orEmpty() }
                val inviteCode = teacher.inviteCode
                val inviteLink = inviteCode?.let { InviteLinkHelper.buildInviteLink(it) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("teacher_card_${teacher.id}")
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
                                Text(teacher.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "${resolvedSubject.ifBlank { "المادة غير محددة" }} • ${teacher.phone.ifBlank { "بدون هاتف" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = if (!teacher.linkedUid.isNullOrBlank()) "مربوط" else "بانتظار الدخول",
                                tone = if (!teacher.linkedUid.isNullOrBlank()) PillTone.SUCCESS else PillTone.INFO
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (resolvedSubject.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Outlined.MenuBook, null, Modifier.size(14.dp))
                                        Text(resolvedSubject, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Outlined.Groups, null, Modifier.size(14.dp))
                                    Text(
                                        text = if (assignedNames.isEmpty()) "بدون مجموعة معينة"
                                        else assignedNames.joinToString("، "),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (!inviteCode.isNullOrBlank() && !inviteLink.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "كود الدعوة: $inviteCode",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = inviteLink,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!inviteCode.isNullOrBlank() && !inviteLink.isNullOrBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(inviteCode))
                                            onNotify("تم نسخ كود الدعوة: $inviteCode")
                                        },
                                        modifier = Modifier.testTag("copy_invite_code_${teacher.id}")
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("الكود")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(inviteLink))
                                            onNotify("تم نسخ رابط الدعوة المباشر")
                                        },
                                        modifier = Modifier.testTag("copy_invite_link_${teacher.id}")
                                    ) {
                                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("الرابط")
                                    }
                                    IconButton(
                                        onClick = {
                                            val shareText = InviteLinkHelper.buildShareMessage(
                                                academyName = state.config.academyName,
                                                teacherName = teacher.name,
                                                inviteCode = inviteCode,
                                                subjectName = resolvedSubject,
                                                groupName = assignedNames.joinToString("، ")
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "دعوة انضمام - ${state.config.academyName}")
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "مشاركة رابط دعوة المعلم"))
                                        },
                                        modifier = Modifier.testTag("share_invite_${teacher.id}")
                                    ) {
                                        Icon(Icons.Outlined.Share, contentDescription = "مشاركة الدعوة")
                                    }
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            IconButton(
                                onClick = { editingTeacher = teacher },
                                modifier = Modifier.testTag("edit_teacher_${teacher.id}")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "تعديل المعلم")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTeacher || editingTeacher != null) {
        val currentInvite = editingTeacher?.inviteCode?.let { invitesByCode[it] }
        TeacherEditorDialog(
            initialTeacher = editingTeacher,
            initialAllowChoice = currentInvite?.allowTeacherGroupChoice ?: true,
            groups = state.groups,
            subjects = state.subjects.filter { it.active },
            onDismiss = {
                showAddTeacher = false
                editingTeacher = null
            },
            onSave = { name, phone, subject, email, groupIds, newGroupName, newGroupGrade, allowChoice, active ->
                onSaveTeacher(
                    editingTeacher?.id,
                    name,
                    phone,
                    subject,
                    email,
                    groupIds,
                    newGroupName,
                    newGroupGrade,
                    allowChoice,
                    active
                )
                showAddTeacher = false
                editingTeacher = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherEditorDialog(
    initialTeacher: Teacher?,
    initialAllowChoice: Boolean,
    groups: List<StudyGroup>,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        phone: String,
        subject: String,
        email: String?,
        assignedGroupIds: List<String>,
        newGroupName: String,
        newGroupGradeLevel: Long,
        allowTeacherGroupChoice: Boolean,
        active: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialTeacher?.name ?: "") }
    var phone by remember { mutableStateOf(initialTeacher?.phone ?: "") }
    var subject by remember { mutableStateOf(initialTeacher?.subject ?: "") }
    var email by remember { mutableStateOf(initialTeacher?.email ?: "") }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupGradeLevel by remember { mutableLongStateOf(1L) }
    var gradeExpanded by remember { mutableStateOf(false) }
    var allowTeacherGroupChoice by remember { mutableStateOf(initialAllowChoice) }
    val selectedGroups = remember {
        mutableStateListOf<String>().apply {
            addAll(initialTeacher?.assignedGroupIds ?: emptyList())
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    if (initialTeacher == null) "دعوة معلم برمز ورابط مباشر" else "تعديل بيانات ودعوة المعلم",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المعلم *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_name_input")
                    )

                    if (subjects.isNotEmpty()) {
                        Text("المادة الدراسية (اختر أو اكتب):", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        onValueChange = { subject = it.take(80) },
                        label = { Text("التخصص / المادة الدراسية") },
                        placeholder = { Text("مثال: الرياضيات، الفيزياء، اللغة العربية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_subject_input")
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(30) },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(160) },
                        label = { Text("البريد الإلكتروني (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (groups.isNotEmpty()) {
                        Text("تعيين لمجموعات قائمة (اختياري):", style = MaterialTheme.typography.labelLarge)
                        groups.forEach { grp ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = grp.id in selectedGroups,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedGroups.add(grp.id) else selectedGroups.remove(grp.id)
                                    }
                                )
                                Column {
                                    Text(grp.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        listOfNotNull(
                                            GradeCatalog.shortLabelFor(grp.gradeLevel.toInt()),
                                            grp.subjectName.takeIf { it.isNotBlank() }
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "أو إنشاء وتعيين مجموعة جديدة للمعلم:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it.take(80) },
                        label = { Text("اسم المجموعة الجديدة (اختياري)") },
                        placeholder = { Text("مثال: مجموعة التفوق - الصف الثالث") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_new_group_input")
                    )

                    if (newGroupName.isNotBlank()) {
                        ExposedDropdownMenuBox(
                            expanded = gradeExpanded,
                            onExpandedChange = { gradeExpanded = !gradeExpanded }
                        ) {
                            OutlinedTextField(
                                value = GradeCatalog.labelFor(newGroupGradeLevel.toInt()),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("صف المجموعة الجديدة") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(gradeExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = gradeExpanded,
                                onDismissRequest = { gradeExpanded = false }
                            ) {
                                GradeCatalog.grades.forEach { (lvl, lbl) ->
                                    DropdownMenuItem(
                                        text = { Text(lbl) },
                                        onClick = {
                                            newGroupGradeLevel = lvl.toLong()
                                            gradeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("اختيار المجموعة والمادة عند التسجيل", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "السماح للمعلم باختيار أو إدخال اسم مجموعته ومادته عند قبول رابط/كود الدعوة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowTeacherGroupChoice,
                            onCheckedChange = { allowTeacherGroupChoice = it },
                            modifier = Modifier.testTag("allow_teacher_group_choice_switch")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSave(
                            name,
                            phone,
                            subject,
                            email.ifBlank { null },
                            selectedGroups.toList(),
                            newGroupName,
                            newGroupGradeLevel,
                            allowTeacherGroupChoice,
                            true
                        )
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("save_teacher_invite_button")
                ) { Text("حفظ وتوليد الدعوة") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
        )
    }
}

@Composable
fun AcademySettingsSection(
    state: AcademyDashboardState,
    onSaveConfig: (
        academyName: String,
        subtitle: String,
        currencySymbol: String,
        primaryCalendar: String,
        absenceThreshold: Long,
        evaluationCriteria: List<String>,
        darkModeDefault: Boolean,
        pinEnabled: Boolean,
        pinCode: String?
    ) -> Unit
) {
    val cfg = state.config
    var academyName by remember(cfg) { mutableStateOf(cfg.academyName) }
    var subtitle by remember(cfg) { mutableStateOf(cfg.subtitle) }
    var currency by remember(cfg) { mutableStateOf(cfg.currencySymbol) }
    var calendarMode by remember(cfg) { mutableStateOf(cfg.primaryCalendar) }
    var absenceThreshold by remember(cfg) { mutableStateOf(cfg.absenceAlertThreshold.toString()) }
    var criteriaText by remember(cfg) { mutableStateOf(cfg.evaluationCriteria.joinToString("، ")) }
    var pinEnabled by remember(cfg) { mutableStateOf(cfg.pinEnabled) }
    var newPin by remember(cfg) { mutableStateOf(cfg.pinCode ?: "") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("إعدادات الهوية والنظام (ملف واحد موحد)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = academyName,
                onValueChange = { academyName = it },
                label = { Text("اسم الأكاديمية (يظهر في التقارير والإيصالات)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text("الوصف المختصر") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it },
                    label = { Text("العملة (ج.م)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = absenceThreshold,
                    onValueChange = { absenceThreshold = it.filter(Char::isDigit) },
                    label = { Text("تنبيه الغياب المتكرر") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = criteriaText,
                onValueChange = { criteriaText = it },
                label = { Text("معايير التقييم (افصل بفاصلة ،)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("التقويم الأساسي", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CalendarMode.entries) { mode ->
                    FilterChip(
                        selected = calendarMode == mode.key,
                        onClick = { calendarMode = mode.key },
                        label = { Text(mode.arabicLabel) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("تفعيل رمز الأمان السريع (PIN)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "بديل عملي لحماية الجلسة وتغيير الرمز على الأجهزة المشتركة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = pinEnabled, onCheckedChange = { pinEnabled = it })
            }
            if (pinEnabled) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                    label = { Text("تعيين / تغيير رمز PIN (4-8 أرقام)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedButton(
                onClick = {
                    val criteria = criteriaText.split("،", ",").map { it.trim() }.filter { it.isNotBlank() }
                    onSaveConfig(
                        academyName,
                        subtitle,
                        currency,
                        calendarMode,
                        absenceThreshold.toLongOrNull()?.coerceIn(1L, 30L) ?: 3L,
                        criteria,
                        state.isDarkTheme,
                        pinEnabled,
                        newPin.takeIf { it.length >= 4 }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ إعدادات الأكاديمية")
            }
        }
    }
}
