package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.Subject
import com.example.model.SubjectColor
import com.example.model.normalizeSubjectName
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill

@Composable
fun SubjectsTabContent(
    subjects: List<Subject>,
    isAdmin: Boolean,
    onAddSubject: () -> Unit,
    onEditSubject: (Subject) -> Unit,
    onDeleteSubject: (Subject) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSubjects = remember(subjects, searchQuery) {
        if (searchQuery.isBlank()) subjects
        else subjects.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("subjects_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث في المواد الدراسية أو الرمز") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subject_search_input")
            )
        }

        if (filteredSubjects.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Outlined.MenuBook,
                    title = if (subjects.isEmpty()) "لا توجد مواد دراسية مضافة" else "لا توجد مواد مطابقة للبحث",
                    description = "أضف المواد الدراسية الخاصة بالأكاديمية (مثل: الرياضيات، اللغة العربية، العلوم) مع لون ورمز مميز لكل مادة.",
                    actionLabel = if (isAdmin && subjects.isEmpty()) "إضافة مادة دراسية" else null,
                    onAction = if (isAdmin && subjects.isEmpty()) onAddSubject else null
                )
            }
        } else {
            items(filteredSubjects, key = { it.id }) { subject ->
                SubjectCard(
                    subject = subject,
                    isAdmin = isAdmin,
                    onEdit = { onEditSubject(subject) },
                    onDelete = { onDeleteSubject(subject) }
                )
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
private fun SubjectCard(
    subject: Subject,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = Color(subject.subjectColor.hexColor)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subject_card_${subject.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.5.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = subject.name,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (subject.code.isNotBlank()) {
                        Surface(
                            color = accent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = subject.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (subject.description.isNotBlank()) {
                    Text(
                        text = subject.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(
                        label = if (subject.active) "مفعّلة" else "متوقفة",
                        tone = if (subject.active) PillTone.SUCCESS else PillTone.WARNING
                    )
                    Text(
                        text = "اللون: ${subject.subjectColor.arabicLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAdmin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_subject_${subject.id}")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "تعديل المادة")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_subject_${subject.id}")
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "حذف المادة",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectEditorDialog(
    initialSubject: Subject?,
    existingSubjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        code: String,
        colorKey: String,
        description: String,
        active: Boolean
    ) -> Unit
) {
    // Automatically pick a distinct unused color when adding a new subject
    val defaultColor = remember(initialSubject, existingSubjects) {
        if (initialSubject != null) {
            initialSubject.colorKey
        } else {
            val used = existingSubjects.map { it.colorKey }.toSet()
            SubjectColor.entries.firstOrNull { it.key !in used }?.key
                ?: SubjectColor.entries[existingSubjects.size % SubjectColor.entries.size].key
        }
    }

    var name by remember { mutableStateOf(initialSubject?.name ?: "") }
    var code by remember { mutableStateOf(initialSubject?.code ?: "") }
    var colorKey by remember { mutableStateOf(defaultColor) }
    var description by remember { mutableStateOf(initialSubject?.description ?: "") }
    var active by remember { mutableStateOf(initialSubject?.active ?: true) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var nameTouched by remember { mutableStateOf(false) }

    val cleanName = name.trim().replace(Regex("\\s+"), " ")
    val normalized = normalizeSubjectName(cleanName)
    val isDuplicateName = cleanName.isNotBlank() && existingSubjects.any {
        it.id != initialSubject?.id && it.normalizedName == normalized
    }
    val nameError = when {
        cleanName.isBlank() -> "يرجى إدخال اسم المادة الدراسية"
        cleanName.length < 2 -> "اسم المادة يجب أن يتكون من حرفين على الأقل"
        cleanName.length > 80 -> "اسم المادة يجب ألا يتجاوز 80 حرفاً"
        isDuplicateName -> "هذه المادة مضافة بالفعل، يجب أن تكون كل مادة مختلفة"
        else -> null
    }

    val cleanCode = code.trim().uppercase()
    val isDuplicateCode = cleanCode.isNotBlank() && existingSubjects.any {
        it.id != initialSubject?.id && it.code.equals(cleanCode, ignoreCase = true)
    }
    val codeError = when {
        cleanCode.length > 20 -> "رمز المادة يجب ألا يتجاوز 20 حرفاً"
        isDuplicateCode -> "هذا الرمز مستخدم لمادة أخرى"
        else -> null
    }

    val showNameError = (attemptedSubmit || nameTouched) && nameError != null
    val showCodeError = (attemptedSubmit || cleanCode.isNotEmpty()) && codeError != null

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = if (initialSubject == null) "إضافة مادة دراسية جديدة" else "تعديل المادة الدراسية",
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
                        label = { Text("اسم المادة الدراسية *") },
                        placeholder = { Text("مثال: الرياضيات، الكيمياء، اللغة الإنجليزية") },
                        isError = showNameError,
                        supportingText = if (showNameError) {
                            { Text(nameError!!) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subject_name_input")
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(20) },
                        label = { Text("رمز المادة المميز (اختياري)") },
                        placeholder = { Text("مثال: MATH أو ARB-1") },
                        isError = showCodeError,
                        supportingText = if (showCodeError) {
                            { Text(codeError!!) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subject_code_input")
                    )

                    Text("لون تمييز المادة", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(SubjectColor.entries) { swatch ->
                            val selected = colorKey == swatch.key
                            val swatchColor = Color(swatch.hexColor)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { colorKey = swatch.key }
                                        .testTag("subject_color_${swatch.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = swatch.arabicLabel,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = swatch.arabicLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(200) },
                        label = { Text("وصف مختصر أو نطاق المنهج (اختياري)") },
                        supportingText = { Text("${description.length}/200") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subject_description_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("المادة مفعّلة", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "تظهر المادة النشطة في التقييمات وتعيين المعلمين",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        attemptedSubmit = true
                        if (nameError == null && codeError == null) {
                            onSave(cleanName, cleanCode, colorKey, description.trim(), active)
                        }
                    },
                    modifier = Modifier.testTag("save_subject_button")
                ) {
                    Text(if (initialSubject == null) "إضافة المادة" else "حفظ التعديلات")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("إلغاء")
                }
            }
        )
    }
}
