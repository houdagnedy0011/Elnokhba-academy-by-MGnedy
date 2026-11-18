package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.AcademyTopBar
import com.example.viewmodel.AcademyDashboardState
import com.example.viewmodel.AcademyViewModel
import kotlinx.coroutines.launch

private data class NavDestination(
    val index: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val adminOnly: Boolean = false
)

@Composable
fun AcademyShell(
    state: AcademyDashboardState,
    viewModel: AcademyViewModel,
    onSignOut: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var openAddStudentTrigger by remember { mutableStateOf(false) }
    var openGroupsTabTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (selectedTab != 0) {
        BackHandler { selectedTab = 0 }
    }

    val destinations = remember(state.isAdmin) {
        listOf(
            NavDestination(0, "الرئيسية", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
            NavDestination(1, "الطلاب", Icons.Filled.Groups, Icons.Outlined.Groups),
            NavDestination(2, "الحضور", Icons.Filled.FactCheck, Icons.Outlined.FactCheck),
            NavDestination(3, "المدفوعات", Icons.Filled.Payments, Icons.Outlined.Payments, adminOnly = true),
            NavDestination(4, "التقييم", Icons.Filled.Assessment, Icons.Outlined.Assessment),
            NavDestination(5, "التقارير", Icons.Filled.Print, Icons.Outlined.Print)
        ).filter { !it.adminOnly || state.isAdmin }
    }

    if (destinations.none { it.index == selectedTab }) {
        selectedTab = 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AcademyTopBar(
                    config = state.config,
                    role = state.role,
                    isDarkTheme = state.isDarkTheme,
                    onToggleDarkTheme = { viewModel.toggleDarkMode(!state.isDarkTheme) },
                    onLockClick = if (state.config.pinEnabled && !state.config.pinCode.isNullOrBlank()) {
                        { viewModel.lockApp() }
                    } else null,
                    onSignOut = onSignOut
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val isSelected = selectedTab == dest.index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = dest.index },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        dest.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_${dest.index}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        destinations.forEach { dest ->
                            val isSelected = selectedTab == dest.index
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { selectedTab = dest.index },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_rail_${dest.index}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (selectedTab) {
                        0 -> DashboardScreen(
                            state = state,
                            onNavigateTab = { idx -> selectedTab = idx },
                            onQuickAddStudent = {
                                openAddStudentTrigger = true
                                selectedTab = 1
                            },
                            onNavigateGroups = {
                                openGroupsTabTrigger = true
                                selectedTab = 1
                            }
                        )
                        1 -> StudentsAndGroupsScreen(
                            state = state,
                            openAddStudentTrigger = openAddStudentTrigger,
                            onConsumedAddStudentTrigger = { openAddStudentTrigger = false },
                            openGroupsTabTrigger = openGroupsTabTrigger,
                            onConsumedGroupsTabTrigger = { openGroupsTabTrigger = false },
                            onSaveStudent = { id, fullName, guardian, phone, enroll, grade, grpId, notes, fee, paid, method ->
                                viewModel.saveStudent(id, fullName, guardian, phone, enroll, grade, grpId, notes, fee, paid, method)
                            },
                            onTransferStudent = { stu, grp -> viewModel.transferStudent(stu, grp) },
                            onArchiveStudent = { id, archived -> viewModel.archiveStudent(id, archived) },
                            onDeleteStudentPermanently = { id -> viewModel.deleteStudentPermanently(id) },
                            onSaveGroup = { id, name, grade, subjectId, teacherId, schedule, maxCap, fee ->
                                viewModel.saveGroup(id, name, grade, subjectId, teacherId, schedule, maxCap, fee)
                            },
                            onDeleteGroup = { id -> viewModel.deleteGroup(id) },
                            onSaveSubject = { id, name, code, colorKey, desc, active ->
                                viewModel.saveSubject(id, name, code, colorKey, desc, active)
                            },
                            onDeleteSubject = { id -> viewModel.deleteSubject(id) }
                        )
                        2 -> AttendanceScreen(
                            state = state,
                            onRecordAttendance = { stu, grp, dateKey, status ->
                                viewModel.recordAttendance(stu, grp, dateKey, status)
                            },
                            onMarkAllPresent = { grp, students, dateKey ->
                                viewModel.markAllPresent(grp, students, dateKey)
                            }
                        )
                        3 -> PaymentsScreen(
                            state = state,
                            onRecordPayment = { stu, monthKey, due, paid, method, notes, onSaved ->
                                viewModel.recordPayment(stu, monthKey, due, paid, method, notes, onSaved)
                            }
                        )
                        4 -> EvaluationsScreen(
                            state = state,
                            onSaveEvaluation = { stu, subject, scores, maxPerCriterion, notes ->
                                viewModel.saveEvaluation(stu, subject, scores, maxPerCriterion, notes)
                            }
                        )
                        5 -> ReportsAndSettingsScreen(
                            state = state,
                            onSaveTeacher = { id, name, phone, subject, email, grpIds, newGrpName, newGrpGrade, allowChoice, active ->
                                viewModel.saveTeacher(
                                    existingId = id,
                                    name = name,
                                    phone = phone,
                                    subject = subject,
                                    email = email,
                                    assignedGroupIds = grpIds,
                                    newGroupName = newGrpName,
                                    newGroupGradeLevel = newGrpGrade,
                                    allowTeacherGroupChoice = allowChoice,
                                    active = active
                                )
                            },
                            onSaveConfig = { name, sub, curr, cal, threshold, criteria, dark, pinEn, pin ->
                                viewModel.saveConfig(name, sub, curr, cal, threshold, criteria, dark, pinEn, pin)
                            },
                            onImportStudentsCsv = { csv -> viewModel.importStudentsCsv(csv) },
                            onRestoreBackupJson = { json -> viewModel.restoreBackupJson(json) },
                            onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                    }
                }
            }
        }
    }
}
