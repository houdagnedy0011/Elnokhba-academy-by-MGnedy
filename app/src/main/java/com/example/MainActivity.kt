package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AcademyRepository
import com.example.data.AuthHelper
import com.example.data.PreferencesManager
import com.example.data.StudentRepository
import com.example.model.UiState
import com.example.model.UserRole
import com.example.ui.screens.AcademyShell
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.RoleOnboardingScreen
import com.example.ui.theme.EliteAcademyTheme
import com.example.util.InviteLinkHelper
import com.example.viewmodel.AcademyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    private var deepLinkInviteCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkInviteCode = extractInviteFromIntent(intent)
        setContent {
            EliteAcademyRoot(
                pendingInviteCode = deepLinkInviteCode,
                onConsumePendingInvite = { deepLinkInviteCode = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val extracted = extractInviteFromIntent(intent)
        if (!extracted.isNullOrBlank()) {
            deepLinkInviteCode = extracted
        }
    }

    private fun extractInviteFromIntent(intent: Intent?): String? {
        val data = intent?.dataString ?: return null
        val code = InviteLinkHelper.extractInviteCode(data)
        return code.takeIf { it.isNotBlank() }
    }
}

@Composable
fun EliteAcademyRoot(
    pendingInviteCode: String? = null,
    onConsumePendingInvite: () -> Unit = {}
) {
    val auth = Firebase.auth
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        EliteAcademyTheme(darkTheme = false) {
            AuthScreen(
                pendingInviteCode = pendingInviteCode,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        }
    } else {
        key(user.uid) {
            AuthenticatedAcademyFlow(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "مستخدم النخبة",
                pendingInviteCode = pendingInviteCode,
                onConsumePendingInvite = onConsumePendingInvite
            )
        }
    }
}

@Composable
private fun AuthenticatedAcademyFlow(
    currentUserId: String,
    userDisplayName: String,
    pendingInviteCode: String? = null,
    onConsumePendingInvite: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: AcademyViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AcademyViewModel(
                    repository = AcademyRepository(db),
                    studentRepository = StudentRepository(db),
                    prefs = PreferencesManager(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val uiState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val previewInviteState by viewModel.previewInviteState.collectAsStateWithLifecycle()

    val isDark = (uiState as? UiState.Success)?.data?.isDarkTheme ?: false

    val performSignOut: () -> Unit = {
        AuthHelper.signOut(
            credentialManager = credentialManager,
            scope = scope
        )
    }

    EliteAcademyTheme(darkTheme = isDark) {
        when (val state = uiState) {
            is UiState.Loading -> {
                Scaffold { inner ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(inner),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("جاري تحميل بيانات أكاديمية النخبة...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            is UiState.Error -> {
                Scaffold { inner ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = performSignOut) {
                                Text("إعادة تسجيل الدخول")
                            }
                        }
                    }
                }
            }

            is UiState.Success -> {
                val data = state.data
                if (data.academyId.isNullOrBlank() || data.role == UserRole.PENDING) {
                    RoleOnboardingScreen(
                        userName = data.userProfile?.displayName ?: userDisplayName,
                        isBusy = isBusy,
                        initialInviteCode = pendingInviteCode.orEmpty(),
                        previewInviteState = previewInviteState,
                        onCreateAcademy = { name, sub, curr -> viewModel.createAcademy(name, sub, curr) },
                        onLookupInvite = { codeOrLink -> viewModel.lookupInvite(codeOrLink) },
                        onClearInvitePreview = { viewModel.clearInvitePreview() },
                        onJoinWithInvite = { codeOrLink, groupId, groupName, gradeLevel, subjectName ->
                            onConsumePendingInvite()
                            viewModel.joinWithInvite(
                                inviteCodeOrLink = codeOrLink,
                                chosenGroupId = groupId,
                                chosenGroupName = groupName,
                                chosenGradeLevel = gradeLevel,
                                chosenSubjectName = subjectName
                            )
                        },
                        onSignOut = performSignOut
                    )
                } else if (!data.isPinUnlocked) {
                    PinLockScreen(
                        academyName = data.config.academyName,
                        onUnlock = { pin -> viewModel.unlockWithPin(pin) },
                        onSignOut = performSignOut
                    )
                } else {
                    AcademyShell(
                        state = data,
                        viewModel = viewModel,
                        onSignOut = performSignOut
                    )
                }
            }
        }
    }
}
