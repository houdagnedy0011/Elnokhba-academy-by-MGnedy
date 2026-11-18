package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AcademyRepository
import com.example.data.PreferencesManager
import com.example.data.StudentRepository
import com.example.model.AcademyConfig
import com.example.model.AttendanceRecord
import com.example.model.AttendanceStatus
import com.example.model.ConsecutiveAbsenceAlert
import com.example.model.EvaluationRecord
import com.example.model.GradeCatalog
import com.example.model.MonthlyStudentPaymentSummary
import com.example.model.PaymentMethod
import com.example.model.PaymentRecord
import com.example.model.Student
import com.example.model.StudyGroup
import com.example.model.Subject
import com.example.model.SubjectColor
import com.example.model.Teacher
import com.example.model.TeacherInvite
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.normalizeSubjectName
import com.example.util.CalendarUtils
import com.example.util.InviteLinkHelper
import com.example.util.toArabicErrorMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AcademyDashboardState(
    val userProfile: UserProfile? = null,
    val config: AcademyConfig = AcademyConfig(),
    val teachers: List<Teacher> = emptyList(),
    val groups: List<StudyGroup> = emptyList(),
    val students: List<Student> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val evaluations: List<EvaluationRecord> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val invites: List<TeacherInvite> = emptyList(),
    val isPinUnlocked: Boolean = true,
    val isDarkTheme: Boolean = false
) {
    val role: UserRole get() = UserRole.fromKey(userProfile?.role)
    val isAdmin: Boolean get() = role == UserRole.ADMIN
    val academyId: String? get() = userProfile?.academyId
    val activeStudents: List<Student> get() = students.filter { !it.archived }
    val archivedStudents: List<Student> get() = students.filter { it.archived }
    val newcomers: List<Student> get() = activeStudents.filter { it.groupId.isNullOrBlank() }

    fun studentsInGroup(groupId: String): List<Student> =
        activeStudents.filter { it.groupId == groupId }

    fun groupById(groupId: String?): StudyGroup? =
        if (groupId.isNullOrBlank()) null else groups.firstOrNull { it.id == groupId }

    fun consecutiveAbsenceAlerts(): List<ConsecutiveAbsenceAlert> {
        val threshold = config.absenceAlertThreshold.toInt().coerceAtLeast(1)
        val byStudent = attendance.groupBy { it.studentId }
        return activeStudents.mapNotNull { student ->
            val records = byStudent[student.id]
                ?.sortedByDescending { it.dateKey }
                ?: return@mapNotNull null
            var streak = 0
            for (rec in records) {
                if (rec.status == AttendanceStatus.ABSENT.key) streak++
                else break
            }
            if (streak >= threshold) {
                val grpName = groupById(student.groupId)?.name ?: "الوافدون الجدد"
                ConsecutiveAbsenceAlert(student, grpName, streak)
            } else null
        }.sortedByDescending { it.consecutiveCount }
    }

    fun monthlyPaymentSummaries(monthKey: String): List<MonthlyStudentPaymentSummary> {
        val monthPayments = payments.filter { it.monthKey == monthKey }
        return activeStudents.map { student ->
            val group = groupById(student.groupId)
            val studentPays = monthPayments.filter { it.studentId == student.id }
            val defaultDue = student.prescribedFee ?: group?.monthlyFee ?: 300.0
            val due = studentPays.maxOfOrNull { it.amountDue } ?: defaultDue
            val paid = studentPays.sumOf { it.amountPaid }
            val remaining = (due - paid).coerceAtLeast(0.0)
            MonthlyStudentPaymentSummary(
                student = student,
                groupName = group?.name ?: "الوافدون الجدد",
                amountDue = due,
                amountPaid = paid,
                remaining = remaining,
                latestPayment = studentPays.maxByOrNull { it.paidAtMillis }
            )
        }
    }

    fun todayAttendanceRate(): Int {
        val today = CalendarUtils.todayDateKey()
        val todayRecs = attendance.filter { it.dateKey == today }
        if (todayRecs.isEmpty()) return 0
        val present = todayRecs.count {
            it.status == AttendanceStatus.PRESENT.key || it.status == AttendanceStatus.LATE.key
        }
        return (present * 100) / todayRecs.size
    }

    fun topGroups(): List<Pair<StudyGroup, Int>> {
        val evalsByGroup = evaluations.groupBy { it.groupId }
        return groups.map { grp ->
            val avg = evalsByGroup[grp.id]?.takeIf { it.isNotEmpty() }
                ?.map { it.percentage }?.average()?.toInt()
                ?: run {
                    val recs = attendance.filter { it.groupId == grp.id }
                    if (recs.isEmpty()) 0
                    else (recs.count { it.status == AttendanceStatus.PRESENT.key } * 100) / recs.size
                }
            grp to avg
        }.sortedByDescending { it.second }.take(3)
    }
}

class AcademyViewModel(
    private val repository: AcademyRepository,
    private val studentRepository: StudentRepository,
    private val prefs: PreferencesManager,
    private val currentUserId: String
) : ViewModel() {

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _previewInviteState = MutableStateFlow<UiState<TeacherInvite>?>(null)
    val previewInviteState: StateFlow<UiState<TeacherInvite>?> = _previewInviteState.asStateFlow()

    private val _pinUnlockedOverride = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repository.ensureInitialUserProfile().onFailure { e ->
                _toastEvents.tryEmit(e.toArabicErrorMessage())
            }
        }
    }

    private fun <T> Flow<T>.toSafeResultFlow(defaultValue: T): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(defaultValue)
        }

    private val userProfileFlow: Flow<UserProfile?> = repository.observeUserProfile(currentUserId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardState: StateFlow<UiState<AcademyDashboardState>> = userProfileFlow
        .flatMapLatest { profile ->
            val academyId = profile?.academyId
            val role = UserRole.fromKey(profile?.role)
            if (profile == null || academyId.isNullOrBlank() || role == UserRole.PENDING) {
                combine(prefs.darkModeFlow, _pinUnlockedOverride) { darkPref, _ ->
                    UiState.Success(
                        AcademyDashboardState(
                            userProfile = profile,
                            isDarkTheme = darkPref ?: false,
                            isPinUnlocked = true
                        )
                    ) as UiState<AcademyDashboardState>
                }
            } else {
                val isAdmin = role == UserRole.ADMIN
                val coreFlow = combine(
                    repository.observeAcademyConfig(academyId, currentUserId).toSafeResultFlow(null),
                    repository.observeTeachers(academyId, currentUserId, isAdmin).toSafeResultFlow(emptyList()),
                    repository.observeGroups(academyId, currentUserId, isAdmin).toSafeResultFlow(emptyList()),
                    studentRepository.observeStudents(academyId, currentUserId, isAdmin).toSafeResultFlow(emptyList()),
                    repository.observeSubjects(academyId, currentUserId).toSafeResultFlow(emptyList())
                ) { cfg, teachers, groups, students, subjects ->
                    Quint(cfg ?: AcademyConfig(id = academyId, ownerUid = academyId), teachers, groups, students, subjects)
                }
                val recordsFlow = combine(
                    repository.observeAttendance(academyId, currentUserId, isAdmin).toSafeResultFlow(emptyList()),
                    if (isAdmin) repository.observePayments(academyId, currentUserId).toSafeResultFlow(emptyList())
                    else flowOf(emptyList()),
                    repository.observeEvaluations(academyId, currentUserId, isAdmin).toSafeResultFlow(emptyList()),
                    if (isAdmin) repository.observeInvites(academyId, currentUserId).toSafeResultFlow(emptyList())
                    else flowOf(emptyList()),
                    combine(prefs.darkModeFlow, _pinUnlockedOverride) { d, p -> d to p }
                ) { att, pay, evals, invites, darkAndPin ->
                    Quint(att, pay, evals, invites, darkAndPin)
                }

                combine(coreFlow, recordsFlow) { core, rec ->
                    val cfg = core.first
                    val darkPref = rec.fifth.first
                    val pinOverride = rec.fifth.second
                    val unlocked = !cfg.pinEnabled || cfg.pinCode.isNullOrBlank() || pinOverride
                    UiState.Success(
                        AcademyDashboardState(
                            userProfile = profile,
                            config = cfg,
                            teachers = core.second,
                            groups = core.third,
                            students = core.fourth,
                            attendance = rec.first,
                            payments = rec.second,
                            evaluations = rec.third,
                            subjects = core.fifth,
                            invites = rec.fourth,
                            isPinUnlocked = unlocked,
                            isDarkTheme = darkPref ?: cfg.darkModeDefault
                        )
                    ) as UiState<AcademyDashboardState>
                }
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).toArabicErrorMessage()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private fun currentState(): AcademyDashboardState? =
        (dashboardState.value as? UiState.Success)?.data

    fun unlockWithPin(pin: String): Boolean {
        val cfg = currentState()?.config ?: return false
        return if (cfg.pinCode == pin.trim()) {
            _pinUnlockedOverride.value = true
            true
        } else {
            _toastEvents.tryEmit("رمز الأمان غير صحيح")
            false
        }
    }

    fun lockApp() {
        _pinUnlockedOverride.value = false
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDarkMode(enabled)
        }
    }

    fun createAcademy(academyName: String, subtitle: String, currency: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.setupAcademyAsAdmin(academyName, subtitle, currency)
                .onSuccess { _toastEvents.tryEmit("تم إنشاء مساحة الأكاديمية بنجاح") }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun lookupInvite(codeOrLink: String) {
        viewModelScope.launch {
            _previewInviteState.value = UiState.Loading
            repository.lookupInvite(codeOrLink)
                .onSuccess { invite ->
                    _previewInviteState.value = UiState.Success(invite)
                }
                .onFailure { e ->
                    _previewInviteState.value = UiState.Error(e.toArabicErrorMessage())
                }
        }
    }

    fun clearInvitePreview() {
        _previewInviteState.value = null
    }

    fun joinWithInvite(
        inviteCodeOrLink: String,
        chosenGroupId: String? = null,
        chosenGroupName: String = "",
        chosenGradeLevel: Long = 1L,
        chosenSubjectName: String = ""
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.joinAcademyWithInvite(
                inviteCodeOrLink = inviteCodeOrLink,
                chosenGroupId = chosenGroupId,
                chosenGroupName = chosenGroupName,
                chosenGradeLevel = chosenGradeLevel,
                chosenSubjectName = chosenSubjectName
            )
                .onSuccess {
                    _previewInviteState.value = null
                    _toastEvents.tryEmit("تم الانضمام للأكاديمية بصلاحية معلم")
                }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun saveConfig(
        academyName: String,
        subtitle: String,
        currencySymbol: String,
        primaryCalendar: String,
        absenceThreshold: Long,
        evaluationCriteria: List<String>,
        darkModeDefault: Boolean,
        pinEnabled: Boolean,
        pinCode: String?
    ) {
        val academyId = currentState()?.academyId ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateAcademyConfig(
                academyId, academyName, subtitle, currencySymbol, primaryCalendar,
                absenceThreshold, evaluationCriteria, darkModeDefault, pinEnabled, pinCode
            ).onSuccess {
                _pinUnlockedOverride.value = true
                _toastEvents.tryEmit("تم حفظ إعدادات الأكاديمية")
            }.onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun saveTeacher(
        existingId: String?,
        name: String,
        phone: String,
        subject: String,
        email: String?,
        assignedGroupIds: List<String>,
        newGroupName: String = "",
        newGroupGradeLevel: Long = 1L,
        allowTeacherGroupChoice: Boolean = true,
        active: Boolean,
        onCodeReady: (String) -> Unit = {}
    ) {
        val state = currentState() ?: return
        val academyId = state.academyId ?: return
        if (name.isBlank()) {
            _toastEvents.tryEmit("يرجى إدخال اسم المعلم")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val cleanSubject = subject.trim()
            val matchedSubject = state.subjects.firstOrNull {
                normalizeSubjectName(it.name) == normalizeSubjectName(cleanSubject)
            }
            val cleanNewGroupName = newGroupName.trim()
            val finalGroupIds = assignedGroupIds.toMutableList()
            val extraGroupOptions = mutableListOf<String>()

            if (cleanNewGroupName.isNotBlank()) {
                val existingMatch = state.groups.firstOrNull {
                    it.name.equals(cleanNewGroupName, ignoreCase = true) &&
                        it.gradeLevel == newGroupGradeLevel.coerceIn(1L, 12L)
                }
                if (existingMatch != null) {
                    if (existingMatch.id !in finalGroupIds) finalGroupIds.add(existingMatch.id)
                } else {
                    val createdGroup = repository.saveGroup(
                        academyId = academyId,
                        existingId = null,
                        name = cleanNewGroupName,
                        gradeLevel = newGroupGradeLevel.coerceIn(1L, 12L),
                        subjectId = matchedSubject?.id,
                        subjectName = matchedSubject?.name ?: cleanSubject,
                        subjectColorKey = matchedSubject?.colorKey ?: SubjectColor.NAVY.key,
                        teacherId = existingId,
                        teacherName = name.trim(),
                        teacherUids = emptyList(),
                        scheduleText = "",
                        maxCapacity = 25L,
                        monthlyFee = 300.0
                    ).getOrNull()
                    if (createdGroup != null) {
                        finalGroupIds.add(createdGroup)
                        extraGroupOptions.add(
                            InviteLinkHelper.encodeGroupOption(
                                createdGroup,
                                cleanNewGroupName,
                                newGroupGradeLevel.coerceIn(1L, 12L),
                                matchedSubject?.name ?: cleanSubject
                            )
                        )
                    }
                }
            }

            val groupSummaryName = cleanNewGroupName.ifBlank {
                state.groups.filter { it.id in finalGroupIds }.joinToString("، ") { it.name }.take(80)
            }
            val groupOptions = (state.groups.map {
                InviteLinkHelper.encodeGroupOption(it.id, it.name, it.gradeLevel, it.subjectName)
            } + extraGroupOptions).distinct().take(40)
            val subjectOptions = state.subjects.filter { it.active }.map { it.name }.distinct().take(40)

            repository.saveTeacherAndInvite(
                academyId = academyId,
                academyName = state.config.academyName,
                existingId = existingId,
                name = name,
                phone = phone,
                subject = cleanSubject,
                subjectId = matchedSubject?.id,
                email = email,
                assignedGroupIds = finalGroupIds,
                groupName = groupSummaryName,
                gradeLevel = newGroupGradeLevel.coerceIn(1L, 12L),
                allowTeacherGroupChoice = allowTeacherGroupChoice,
                availableGroupOptions = groupOptions,
                availableSubjectNames = subjectOptions,
                active = active
            )
                .onSuccess { code ->
                    onCodeReady(code)
                    _toastEvents.tryEmit("تم حفظ المعلم — كود الدعوة: $code")
                }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun saveGroup(
        existingId: String?,
        name: String,
        gradeLevel: Long,
        subjectId: String?,
        teacherId: String?,
        scheduleText: String,
        maxCapacity: Long,
        monthlyFee: Double
    ) {
        val state = currentState() ?: return
        val academyId = state.academyId ?: return
        if (name.isBlank()) {
            _toastEvents.tryEmit("يرجى إدخال اسم المجموعة")
            return
        }
        val subject = state.subjects.firstOrNull { it.id == subjectId }
        val teacher = state.teachers.firstOrNull { it.id == teacherId }
        val teacherUids = listOfNotNull(teacher?.linkedUid)
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveGroup(
                academyId = academyId,
                existingId = existingId,
                name = name,
                gradeLevel = gradeLevel,
                subjectId = subject?.id,
                subjectName = subject?.name ?: "",
                subjectColorKey = subject?.colorKey ?: com.example.model.SubjectColor.NAVY.key,
                teacherId = teacher?.id,
                teacherName = teacher?.name ?: "",
                teacherUids = teacherUids,
                scheduleText = scheduleText,
                maxCapacity = maxCapacity,
                monthlyFee = monthlyFee
            ).onSuccess {
                _toastEvents.tryEmit("تم حفظ المجموعة بنجاح")
            }.onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun deleteGroup(groupId: String) {
        val academyId = currentState()?.academyId ?: return
        viewModelScope.launch {
            repository.deleteGroup(academyId, groupId)
                .onSuccess { _toastEvents.tryEmit("تم حذف المجموعة") }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
        }
    }

    fun saveSubject(
        existingId: String?,
        name: String,
        code: String,
        colorKey: String,
        description: String,
        active: Boolean
    ) {
        val state = currentState() ?: return
        val academyId = state.academyId ?: return
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.length < 2) {
            _toastEvents.tryEmit("يرجى إدخال اسم المادة الدراسية (حرفان على الأقل)")
            return
        }
        val normalized = normalizeSubjectName(cleanName)
        if (state.subjects.any { it.id != existingId && it.normalizedName == normalized }) {
            _toastEvents.tryEmit("المادة «$cleanName» مضافة بالفعل، يجب أن تكون كل مادة مختلفة")
            return
        }
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isNotBlank() && state.subjects.any { it.id != existingId && it.code.equals(cleanCode, ignoreCase = true) }) {
            _toastEvents.tryEmit("رمز المادة «$cleanCode» مستخدم لمادة أخرى")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveSubject(academyId, existingId, cleanName, cleanCode, colorKey, description, active)
                .onSuccess { _toastEvents.tryEmit("تم حفظ المادة الدراسية بنجاح") }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun deleteSubject(subjectId: String) {
        val academyId = currentState()?.academyId ?: return
        viewModelScope.launch {
            repository.deleteSubject(academyId, subjectId)
                .onSuccess { _toastEvents.tryEmit("تم حذف المادة الدراسية") }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
        }
    }

    fun saveStudent(
        existingId: String?,
        fullName: String,
        guardianName: String,
        phone: String,
        enrollmentDateMillis: Long,
        gradeLevel: Long,
        groupId: String?,
        notes: String,
        prescribedFee: Double? = null,
        initialPaidAmount: Double = 0.0,
        paymentMethod: PaymentMethod = PaymentMethod.CASH
    ) {
        val state = currentState() ?: return
        val academyId = state.academyId ?: return
        if (fullName.isBlank()) {
            _toastEvents.tryEmit("يرجى إدخال الاسم الكامل للطالب")
            return
        }
        val grp = state.groupById(groupId)
        val teacherUids = grp?.teacherUids ?: emptyList()
        val effectiveFee = (prescribedFee ?: grp?.monthlyFee)?.coerceIn(0.0, 1000000.0)
        viewModelScope.launch {
            _isBusy.value = true
            val student = Student(
                id = existingId.orEmpty(),
                fullName = fullName,
                guardianName = guardianName,
                phone = phone,
                enrollmentDateMillis = enrollmentDateMillis,
                gradeLevel = gradeLevel,
                groupId = groupId,
                teacherUids = teacherUids,
                prescribedFee = effectiveFee,
                notes = notes
            )
            if (existingId.isNullOrBlank()) {
                studentRepository.createStudent(academyId, student)
                    .onSuccess { createdId ->
                        val due = (effectiveFee ?: 300.0).coerceAtLeast(initialPaidAmount)
                        val paid = initialPaidAmount.coerceIn(0.0, due)
                        if (paid > 0.0) {
                            val monthKey = CalendarUtils.currentMonthKey()
                            val remaining = (due - paid).coerceAtLeast(0.0)
                            val payNote = if (remaining > 0.0) {
                                "دفعة عند التسجيل (المتبقي ${CalendarUtils.formatCurrency(remaining, state.config.currencySymbol)})"
                            } else {
                                "سداد المصروفات عند التسجيل"
                            }
                            repository.savePayment(
                                academyId = academyId,
                                student = student.copy(id = createdId),
                                monthKey = monthKey,
                                amountDue = due,
                                amountPaid = paid,
                                method = paymentMethod,
                                paidAtMillis = System.currentTimeMillis(),
                                notes = payNote
                            ).onSuccess { record ->
                                if (remaining > 0.0) {
                                    _toastEvents.tryEmit(
                                        "تم حفظ الطالب • المدفوع ${CalendarUtils.formatCurrency(paid, state.config.currencySymbol)} والمتبقي ${CalendarUtils.formatCurrency(remaining, state.config.currencySymbol)}"
                                    )
                                } else {
                                    _toastEvents.tryEmit("تم حفظ الطالب وتسجيل سداد المصروفات (${record.receiptNumber})")
                                }
                            }.onFailure { err ->
                                _toastEvents.tryEmit("تم حفظ الطالب لكن تعذر تسجيل الدفعة: ${err.toArabicErrorMessage()}")
                            }
                        } else {
                            _toastEvents.tryEmit("تم حفظ الطالب (الحالة: لم يسدد المصروفات)")
                        }
                    }
                    .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            } else {
                studentRepository.updateStudent(academyId, existingId, student)
                    .onSuccess { _toastEvents.tryEmit("تم تحديث بيانات الطالب") }
                    .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            }
            _isBusy.value = false
        }
    }

    fun transferStudent(student: Student, targetGroup: StudyGroup?) {
        val academyId = currentState()?.academyId ?: return
        viewModelScope.launch {
            studentRepository.transferStudentToGroup(
                academyId = academyId,
                studentId = student.id,
                newGroupId = targetGroup?.id,
                newGradeLevel = targetGroup?.gradeLevel ?: student.gradeLevel,
                newTeacherUids = targetGroup?.teacherUids ?: emptyList()
            ).onSuccess {
                val msg = if (targetGroup == null) "تم نقل الطالب إلى قسم الوافدين الجدد"
                else "تم نقل ${student.fullName} إلى ${targetGroup.name} مع حفظ كامل السجلات"
                _toastEvents.tryEmit(msg)
            }.onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
        }
    }

    fun archiveStudent(studentId: String, archived: Boolean) {
        val academyId = currentState()?.academyId ?: return
        viewModelScope.launch {
            studentRepository.setStudentArchived(academyId, studentId, archived)
                .onSuccess {
                    _toastEvents.tryEmit(if (archived) "تم نقل الطالب إلى الأرشيف" else "تمت استعادة الطالب بنجاح")
                }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
        }
    }

    fun deleteStudentPermanently(studentId: String) {
        val academyId = currentState()?.academyId ?: return
        viewModelScope.launch {
            _isBusy.value = true
            studentRepository.deleteStudent(academyId, studentId)
                .onSuccess { _toastEvents.tryEmit("تم حذف سجل الطالب نهائياً") }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun recordAttendance(student: Student, group: StudyGroup, dateKey: String, status: AttendanceStatus, notes: String = "") {
        val academyId = currentState()?.academyId ?: return
        viewModelScope.launch {
            repository.recordAttendance(academyId, student, group.id, group.teacherUids, dateKey, status, notes)
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
        }
    }

    fun markAllPresent(group: StudyGroup, students: List<Student>, dateKey: String) {
        val academyId = currentState()?.academyId ?: return
        if (students.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            repository.markAllPresent(academyId, students, group.id, group.teacherUids, dateKey)
                .onSuccess { _toastEvents.tryEmit("تم تحديد جميع طلاب المجموعة حاضرين") }
                .onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun recordPayment(
        student: Student,
        monthKey: String,
        amountDue: Double,
        amountPaid: Double,
        method: PaymentMethod,
        notes: String,
        onSaved: (PaymentRecord) -> Unit = {}
    ) {
        val academyId = currentState()?.academyId ?: return
        if (amountPaid <= 0.0) {
            _toastEvents.tryEmit("يرجى إدخال مبلغ صحيح أكبر من صفر")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.savePayment(
                academyId, student, monthKey, amountDue, amountPaid,
                method, System.currentTimeMillis(), notes
            ).onSuccess { record ->
                _toastEvents.tryEmit("تم تسجيل الدفعة وإصدار الإيصال ${record.receiptNumber}")
                onSaved(record)
            }.onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun saveEvaluation(
        student: Student,
        subject: String,
        scores: Map<String, Long>,
        maxPerCriterion: Double,
        notes: String
    ) {
        val academyId = currentState()?.academyId ?: return
        if (subject.isBlank()) {
            _toastEvents.tryEmit("يرجى إدخال اسم المادة")
            return
        }
        val total = scores.values.sum().toDouble()
        val maxTotal = (scores.size.coerceAtLeast(1) * maxPerCriterion).coerceAtLeast(1.0)
        val pct = ((total / maxTotal) * 100.0).coerceIn(0.0, 100.0)
        val label = GradeCatalog.classifyPercentage(pct)
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveEvaluation(
                academyId, student, subject, scores, maxPerCriterion,
                label, System.currentTimeMillis(), notes
            ).onSuccess {
                _toastEvents.tryEmit("تم حفظ التقييم ($label)")
            }.onFailure { _toastEvents.tryEmit(it.toArabicErrorMessage()) }
            _isBusy.value = false
        }
    }

    fun importStudentsCsv(csvText: String) {
        val academyId = currentState()?.academyId ?: return
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            _toastEvents.tryEmit("الملف فارغ")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            var imported = 0
            val startIdx = if (lines.first().contains("الاسم") || lines.first().contains("name", ignoreCase = true)) 1 else 0
            for (i in startIdx until lines.size) {
                val cols = lines[i].split(",").map { it.trim().trim('"') }
                val name = cols.getOrNull(0).orEmpty()
                if (name.isBlank()) continue
                val guardian = cols.getOrNull(1).orEmpty()
                val phone = cols.getOrNull(2).orEmpty()
                val grade = cols.getOrNull(3)?.filter { it.isDigit() }?.toLongOrNull()?.coerceIn(1L, 12L) ?: 1L
                val notes = cols.getOrNull(5).orEmpty()
                val res = studentRepository.createStudent(
                    academyId,
                    Student(
                        fullName = name,
                        guardianName = guardian,
                        phone = phone,
                        enrollmentDateMillis = System.currentTimeMillis(),
                        gradeLevel = grade,
                        groupId = null,
                        teacherUids = emptyList(),
                        notes = notes
                    )
                )
                if (res.isSuccess) imported++
            }
            _toastEvents.tryEmit("تم استيراد $imported طالب إلى قسم الوافدين الجدد")
            _isBusy.value = false
        }
    }

    fun restoreBackupJson(jsonText: String) {
        val academyId = currentState()?.academyId ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val root = JSONObject(jsonText)
                val groupsArr = root.optJSONArray("groups")
                if (groupsArr != null) {
                    for (i in 0 until groupsArr.length()) {
                        val g = groupsArr.getJSONObject(i)
                        repository.saveGroup(
                            academyId = academyId,
                            existingId = null,
                            name = g.optString("name", "مجموعة مستعادة"),
                            gradeLevel = g.optLong("gradeLevel", 1L),
                            teacherId = null,
                            teacherName = g.optString("teacherName", ""),
                            teacherUids = emptyList(),
                            scheduleText = g.optString("scheduleText", ""),
                            maxCapacity = g.optLong("maxCapacity", 20L),
                            monthlyFee = g.optDouble("monthlyFee", 300.0)
                        )
                    }
                }
                val stuArr = root.optJSONArray("students")
                var count = 0
                if (stuArr != null) {
                    for (i in 0 until stuArr.length()) {
                        val s = stuArr.getJSONObject(i)
                        val res = studentRepository.createStudent(
                            academyId,
                            Student(
                                fullName = s.optString("fullName", "طالب"),
                                guardianName = s.optString("guardianName", ""),
                                phone = s.optString("phone", ""),
                                enrollmentDateMillis = s.optLong("enrollmentDateMillis", System.currentTimeMillis()),
                                gradeLevel = s.optLong("gradeLevel", 1L),
                                groupId = null,
                                teacherUids = emptyList(),
                                notes = s.optString("notes", ""),
                                archived = s.optBoolean("archived", false)
                            )
                        )
                        if (res.isSuccess) count++
                    }
                }
                _toastEvents.tryEmit("تمت استعادة $count طالب بنجاح")
            } catch (e: Exception) {
                _toastEvents.tryEmit("ملف النسخة الاحتياطية غير صالح: ${e.localizedMessage}")
            }
            _isBusy.value = false
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
