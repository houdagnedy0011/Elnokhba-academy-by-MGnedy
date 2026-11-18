package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.AuthHelper
import com.example.model.GradeCatalog
import com.example.model.TeacherInvite
import com.example.model.UiState
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldRoyal
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.util.InviteLinkHelper

@Composable
fun AuthScreen(
    pendingInviteCode: String? = null,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        AuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NavyDeep, NavyPrimary, MaterialTheme.colorScheme.background)
                )
            )
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, GoldRoyal.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.academy_logo),
                    contentDescription = "شعار أكاديمية النخبة",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(2.dp, GoldBright, RoundedCornerShape(18.dp))
                )

                Text(
                    text = "أكاديمية النخبة",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "منظومة متكاملة لإدارة الطلاب والمجموعات، رصد الحضور والغياب، متابعة المدفوعات، التقييم الأكاديمي، وإصدار التقارير المطبوعة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (!pendingInviteCode.isNullOrBlank()) {
                    Surface(
                        color = GoldBright.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldRoyal.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null, tint = GoldRoyal)
                            Text(
                                text = "تم فتح رابط دعوة معلم ($pendingInviteCode). سجل الدخول بحساب Google لإكمال قبول الدعوة وتحديد مجموعتك ومادتك.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeatureRow(Icons.Outlined.AdminPanelSettings, "صلاحيات كاملة للمدير وإدارة حسابات المعلمين")
                        FeatureRow(Icons.Outlined.School, "توزيع الصفوف الـ 12 وقسم الوافدين الجدد")
                        FeatureRow(Icons.Outlined.VerifiedUser, "مزامنة سحابية آمنة وتقارير PDF و Excel")
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        AuthHelper.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = { isLoading = false }
                        )
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyPrimary,
                        contentColor = GoldBright
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = GoldBright,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "تسجيل الدخول بحساب Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoleOnboardingScreen(
    userName: String,
    isBusy: Boolean,
    initialInviteCode: String = "",
    previewInviteState: UiState<TeacherInvite>? = null,
    onCreateAcademy: (name: String, subtitle: String, currency: String) -> Unit,
    onLookupInvite: (codeOrLink: String) -> Unit = {},
    onClearInvitePreview: () -> Unit = {},
    onJoinWithInvite: (
        codeOrLink: String,
        chosenGroupId: String?,
        chosenGroupName: String,
        chosenGradeLevel: Long,
        chosenSubjectName: String
    ) -> Unit,
    onSignOut: () -> Unit
) {
    var academyName by remember { mutableStateOf("أكاديمية النخبة") }
    var subtitle by remember { mutableStateOf("التميز والريادة التعليمية") }
    var currency by remember { mutableStateOf("ج.م") }
    var inviteInput by remember { mutableStateOf(initialInviteCode) }

    LaunchedEffect(initialInviteCode) {
        if (initialInviteCode.isNotBlank()) {
            inviteInput = initialInviteCode
            onLookupInvite(initialInviteCode)
        }
    }

    val loadedInvite = (previewInviteState as? UiState.Success)?.data
    val groupOptions = remember(loadedInvite) {
        loadedInvite?.availableGroupOptions
            ?.mapNotNull { InviteLinkHelper.decodeGroupOption(it) }
            .orEmpty()
    }
    val subjectSuggestions = remember(loadedInvite) {
        if (loadedInvite == null) emptyList()
        else (loadedInvite.availableSubjectNames + listOfNotNull(loadedInvite.subjectName.takeIf { it.isNotBlank() }))
            .distinct()
    }

    var chosenSubjectName by remember(loadedInvite) {
        mutableStateOf(loadedInvite?.subjectName.orEmpty())
    }
    var useExistingGroup by remember(loadedInvite, groupOptions) {
        mutableStateOf(
            loadedInvite?.assignedGroupIds?.isNotEmpty() == true &&
                groupOptions.any { it.id in loadedInvite.assignedGroupIds }
        )
    }
    var selectedExistingGroupId by remember(loadedInvite) {
        mutableStateOf(loadedInvite?.assignedGroupIds?.firstOrNull())
    }
    var customGroupName by remember(loadedInvite) {
        mutableStateOf(loadedInvite?.groupName.orEmpty())
    }
    var customGradeLevel by remember(loadedInvite) {
        mutableLongStateOf(loadedInvite?.gradeLevel ?: 1L)
    }
    var gradeExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مرحباً، $userName",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "اختر دورك لبدء العمل داخل أكاديمية النخبة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onSignOut) {
                    Text("خروج")
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, GoldRoyal.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = GoldRoyal)
                        Text("الإعداد كمدير للأكاديمية", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "أنشئ مساحة الأكاديمية بصلاحيات كاملة لإدارة الصفوف والمجموعات والمدفوعات والمعلمين.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = academyName,
                        onValueChange = { academyName = it },
                        label = { Text("اسم الأكاديمية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_academy_name")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = subtitle,
                            onValueChange = { subtitle = it },
                            label = { Text("الشعار الفرعي") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = currency,
                            onValueChange = { currency = it },
                            label = { Text("العملة") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { onCreateAcademy(academyName, subtitle, currency) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_academy_button")
                    ) {
                        Text("إنشاء وبدء الإدارة الآن")
                    }
                }
            }

            HorizontalDivider()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("الانضمام كمعلم عبر رمز أو رابط الدعوة", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "أدخل كود الدعوة أو الصق رابط الدعوة الذي أرسله مدير الأكاديمية، ثم حدد أو أكد المجموعة والمادة الدراسية الخاصة بك.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inviteInput,
                        onValueChange = {
                            inviteInput = it
                            if (previewInviteState != null) onClearInvitePreview()
                        },
                        label = { Text("كود الدعوة أو رابط الدعوة") },
                        placeholder = { Text("مثال: ELITE4512 أو https://eliteacademy.app/invite?code=ELITE4512") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_invite_code_input")
                    )

                    if (previewInviteState is UiState.Loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("جاري التحقق من الدعوة...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (previewInviteState is UiState.Error) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = previewInviteState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (loadedInvite == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onLookupInvite(inviteInput) },
                                enabled = !isBusy && inviteInput.isNotBlank() && previewInviteState !is UiState.Loading,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("lookup_invite_button")
                            ) {
                                Text("معاينة وتحديد المجموعة")
                            }
                            OutlinedButton(
                                onClick = { onJoinWithInvite(inviteInput, null, "", 1L, "") },
                                enabled = !isBusy && inviteInput.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("quick_join_teacher_button")
                            ) {
                                Text("تفعيل سريع")
                            }
                        }
                    } else {
                        // Invite Details & Group/Subject Selection on Registration
                        Surface(
                            color = EmeraldSuccess.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "دعوة صالحة من ${loadedInvite.academyName.ifBlank { "أكاديمية النخبة" }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "المعلم: ${loadedInvite.teacherName} • كود: ${loadedInvite.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Subject Selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.MenuBook, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("المادة الدراسية الخاصة بك:", style = MaterialTheme.typography.labelLarge)
                        }
                        if (subjectSuggestions.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(subjectSuggestions) { subName ->
                                    FilterChip(
                                        selected = chosenSubjectName.trim() == subName,
                                        onClick = { chosenSubjectName = subName },
                                        label = { Text(subName) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = chosenSubjectName,
                            onValueChange = { chosenSubjectName = it.take(80) },
                            label = { Text("اسم المادة الدراسية *") },
                            placeholder = { Text("مثال: الرياضيات، اللغة العربية") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("invite_subject_input")
                        )

                        // Group Selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Groups, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("المجموعة التي ستكون مسؤولاً عنها:", style = MaterialTheme.typography.labelLarge)
                        }

                        if (loadedInvite.allowTeacherGroupChoice) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (groupOptions.isNotEmpty()) {
                                    FilterChip(
                                        selected = useExistingGroup,
                                        onClick = { useExistingGroup = true },
                                        label = { Text("اختيار مجموعة قائمة (${groupOptions.size})") }
                                    )
                                }
                                FilterChip(
                                    selected = !useExistingGroup,
                                    onClick = {
                                        useExistingGroup = false
                                        selectedExistingGroupId = null
                                    },
                                    label = { Text("تسمية / إنشاء مجموعة جديدة") }
                                )
                            }

                            if (useExistingGroup && groupOptions.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    groupOptions.forEach { opt ->
                                        val isSelected = selectedExistingGroupId == opt.id
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedExistingGroupId = opt.id
                                                    customGroupName = opt.name
                                                    customGradeLevel = opt.gradeLevel
                                                    if (opt.subjectName.isNotBlank() && chosenSubjectName.isBlank()) {
                                                        chosenSubjectName = opt.subjectName
                                                    }
                                                }
                                                .testTag("invite_existing_group_${opt.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(opt.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        listOfNotNull(
                                                            GradeCatalog.labelFor(opt.gradeLevel.toInt()),
                                                            opt.subjectName.takeIf { it.isNotBlank() }
                                                        ).joinToString(" • "),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = customGroupName,
                                    onValueChange = { customGroupName = it.take(80) },
                                    label = { Text("اسم المجموعة المسؤول عنها *") },
                                    placeholder = { Text("مثال: مجموعة النخبة - رياضيات") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("invite_group_name_input")
                                )

                                ExposedDropdownMenuBox(
                                    expanded = gradeExpanded,
                                    onExpandedChange = { gradeExpanded = !gradeExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = GradeCatalog.labelFor(customGradeLevel.toInt()),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("الصف الدراسي للمجموعة") },
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
                                                    customGradeLevel = lvl.toLong()
                                                    gradeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "المجموعة المعينة من المدير: ${loadedInvite.groupName.ifBlank { "محددة مسبقاً" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        val canAccept = if (loadedInvite.allowTeacherGroupChoice) {
                            if (useExistingGroup) selectedExistingGroupId != null
                            else customGroupName.isNotBlank() || loadedInvite.assignedGroupIds.isNotEmpty()
                        } else true

                        Button(
                            onClick = {
                                onJoinWithInvite(
                                    inviteInput,
                                    if (useExistingGroup) selectedExistingGroupId else null,
                                    if (useExistingGroup) "" else customGroupName,
                                    customGradeLevel,
                                    chosenSubjectName
                                )
                            },
                            enabled = !isBusy && canAccept && chosenSubjectName.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("join_teacher_button")
                        ) {
                            Text("قبول الدعوة والانضمام كمعلم")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinLockScreen(
    academyName: String,
    onUnlock: (String) -> Boolean,
    onSignOut: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(color = GoldBright.copy(alpha = 0.2f), shape = CircleShape) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = GoldRoyal,
                        modifier = Modifier.padding(14.dp).size(32.dp)
                    )
                }
                Text(academyName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "أدخل رمز الأمان السريع لفتح جلسة العمل",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        invalid = false
                    },
                    label = { Text("رمز الأمان (PIN)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = invalid,
                    supportingText = if (invalid) { { Text("الرمز غير صحيح") } } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pin_lock_input")
                )
                Button(
                    onClick = { invalid = !onUnlock(pin) },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pin_unlock_button")
                ) {
                    Text("فتح التطبيق")
                }
                TextButton(onClick = onSignOut) {
                    Text("تبديل الحساب")
                }
            }
        }
    }
}
