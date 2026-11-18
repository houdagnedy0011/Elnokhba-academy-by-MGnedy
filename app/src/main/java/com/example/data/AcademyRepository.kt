package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AcademyConfig
import com.example.model.AcademyMember
import com.example.model.AttendanceRecord
import com.example.model.AttendanceStatus
import com.example.model.CalendarMode
import com.example.model.EvaluationRecord
import com.example.model.PaymentMethod
import com.example.model.PaymentRecord
import com.example.model.Student
import com.example.model.StudyGroup
import com.example.model.Subject
import com.example.model.SubjectColor
import com.example.model.Teacher
import com.example.model.TeacherInvite
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.normalizeSubjectName
import com.example.util.InviteLinkHelper
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AcademyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val studentRepository: StudentRepository = StudentRepository(db, auth)
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً")

    private fun <T> Flow<T>.wrapFirestoreErrors(op: OperationType, path: String, uid: String): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, op, path)
            }
            throw error
        }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .wrapFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    suspend fun ensureInitialUserProfile(): Result<UserProfile> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = uid)
                Result.success(existing)
            } else {
                val user = auth.currentUser!!
                val displayName = user.displayName?.takeIf { it.isNotBlank() }?.take(80) ?: "مستخدم النخبة"
                val email = user.email?.takeIf { it.isNotBlank() }?.take(160) ?: "$uid@elite.academy"
                val payload = mapOf(
                    "userId" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "role" to UserRole.PENDING.key,
                    "assignedGroupIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = uid, email = email, displayName = displayName)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun setupAcademyAsAdmin(academyName: String, subtitle: String, currency: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val path = "academies/$uid"
        return try {
            val user = auth.currentUser!!
            val displayName = user.displayName?.takeIf { it.isNotBlank() }?.take(80) ?: "مدير الأكاديمية"
            val email = user.email?.takeIf { it.isNotBlank() }?.take(160) ?: "$uid@elite.academy"

            val academyRef = db.collection("academies").document(uid)
            val existingAcademy = academyRef.get().await()
            if (!existingAcademy.exists()) {
                val academyPayload = mapOf(
                    "ownerUid" to uid,
                    "academyName" to academyName.trim().ifBlank { "أكاديمية النخبة" }.take(100),
                    "subtitle" to subtitle.trim().ifBlank { "التميز والريادة التعليمية" }.take(140),
                    "currencySymbol" to currency.trim().ifBlank { "ج.م" }.take(16),
                    "primaryCalendar" to CalendarMode.GREGORIAN.key,
                    "absenceAlertThreshold" to 3L,
                    "evaluationCriteria" to listOf("الفهم", "الواجبات", "المشاركة", "الاختبارات"),
                    "darkModeDefault" to false,
                    "pinEnabled" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                academyRef.set(academyPayload).await()
            }

            val memberRef = academyRef.collection("members").document(uid)
            if (!memberRef.get().await().exists()) {
                memberRef.set(
                    mapOf(
                        "uid" to uid,
                        "displayName" to displayName,
                        "email" to email,
                        "role" to UserRole.ADMIN.key,
                        "assignedGroupIds" to emptyList<String>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            db.collection("users").document(uid).update(
                mapOf(
                    "academyId" to uid,
                    "role" to UserRole.ADMIN.key,
                    "assignedGroupIds" to emptyList<String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun lookupInvite(inviteCodeOrLink: String): Result<TeacherInvite> {
        val code = InviteLinkHelper.extractInviteCode(inviteCodeOrLink)
        if (code.isBlank() || !code.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            return Result.failure(IllegalArgumentException("صيغة كود أو رابط الدعوة غير صالحة"))
        }
        val path = "invites/$code"
        return try {
            requireUserId()
            val snap = db.collection("invites").document(code).get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("كود الدعوة غير موجود"))
            }
            val invite = snap.toObject(TeacherInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("تعذر قراءة بيانات الدعوة"))
            if (invite.status != "pending") {
                return Result.failure(IllegalStateException("تم استخدام كود الدعوة مسبقاً"))
            }
            Result.success(invite)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun joinAcademyWithInvite(
        inviteCodeOrLink: String,
        chosenGroupId: String? = null,
        chosenGroupName: String = "",
        chosenGradeLevel: Long = 1L,
        chosenSubjectName: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("غير مسجل الدخول"))
        val code = InviteLinkHelper.extractInviteCode(inviteCodeOrLink)
        if (code.isBlank() || !code.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            return Result.failure(IllegalArgumentException("صيغة كود أو رابط الدعوة غير صالحة"))
        }
        val path = "invites/$code"
        return try {
            val inviteRef = db.collection("invites").document(code)
            val inviteSnap = inviteRef.get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("كود الدعوة غير موجود"))
            }
            val invite = inviteSnap.toObject(TeacherInvite::class.java)
                ?: return Result.failure(IllegalStateException("تعذر قراءة بيانات الدعوة"))
            if (invite.status != "pending") {
                return Result.failure(IllegalStateException("تم استخدام كود الدعوة مسبقاً"))
            }

            val user = auth.currentUser!!
            val displayName = user.displayName?.takeIf { it.isNotBlank() }?.take(80) ?: invite.teacherName
            val email = user.email?.takeIf { it.isNotBlank() }?.take(160) ?: "$uid@elite.academy"
            val academyRef = db.collection("academies").document(invite.academyId)

            val finalSubject = chosenSubjectName.trim().ifBlank { invite.subjectName.trim() }.take(80)
            val cleanChosenGroupId = chosenGroupId?.takeIf { it.isNotBlank() }
            val cleanChosenGroupName = chosenGroupName.trim().take(80)
            val finalGradeLevel = chosenGradeLevel.coerceIn(1L, 12L)

            val canChooseGroup = invite.allowTeacherGroupChoice
            var newGroupRefId: String? = null
            var finalGroupName = invite.groupName
            val finalAssignedGroupIds = when {
                !canChooseGroup -> invite.assignedGroupIds
                cleanChosenGroupId != null -> {
                    val matchedOption = invite.availableGroupOptions
                        .mapNotNull { InviteLinkHelper.decodeGroupOption(it) }
                        .firstOrNull { it.id == cleanChosenGroupId }
                    finalGroupName = cleanChosenGroupName.ifBlank { matchedOption?.name ?: invite.groupName }
                    (invite.assignedGroupIds + cleanChosenGroupId).distinct().take(40)
                }
                cleanChosenGroupName.isNotBlank() -> {
                    val newRef = academyRef.collection("groups").document()
                    newGroupRefId = newRef.id
                    finalGroupName = cleanChosenGroupName
                    (invite.assignedGroupIds + newRef.id).distinct().take(40)
                }
                invite.groupName.isNotBlank() && invite.assignedGroupIds.isEmpty() -> {
                    val newRef = academyRef.collection("groups").document()
                    newGroupRefId = newRef.id
                    finalGroupName = invite.groupName.trim().take(80)
                    listOf(newRef.id)
                }
                else -> invite.assignedGroupIds
            }

            // 1. تسجيل عضوية المعلم داخل الأكاديمية
            academyRef.collection("members").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "role" to UserRole.TEACHER.key,
                    "assignedGroupIds" to finalAssignedGroupIds,
                    "inviteCode" to code,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. إنشاء المجموعة الجديدة إذا اختار المعلم تأسيس مجموعة باسم محدد عند قبول الدعوة
            if (newGroupRefId != null && finalGroupName.isNotBlank()) {
                val newGroupMap = mapOf(
                    "name" to finalGroupName,
                    "gradeLevel" to finalGradeLevel,
                    "subjectId" to invite.subjectId?.takeIf { it.isNotBlank() },
                    "subjectName" to finalSubject,
                    "subjectColorKey" to SubjectColor.NAVY.key,
                    "teacherId" to invite.teacherDocId?.takeIf { it.isNotBlank() },
                    "teacherName" to displayName,
                    "teacherUids" to listOf(uid),
                    "scheduleText" to "",
                    "maxCapacity" to 25L,
                    "monthlyFee" to 300.0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                academyRef.collection("groups").document(newGroupRefId).set(newGroupMap).await()
            }

            // 3. تحديث المجموعات القائمة لإضافة المعلم وربط المادة إذا كانت غير محددة
            for (groupId in finalAssignedGroupIds) {
                if (groupId == newGroupRefId) continue
                try {
                    val grpRef = academyRef.collection("groups").document(groupId)
                    val grpSnap = grpRef.get().await()
                    if (grpSnap.exists()) {
                        val existingUids = (grpSnap.get("teacherUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val updatePayload = mutableMapOf<String, Any>(
                            "teacherUids" to (existingUids + uid).distinct(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        val existingTeacherName = grpSnap.getString("teacherName").orEmpty()
                        if (existingTeacherName.isBlank()) {
                            updatePayload["teacherName"] = displayName
                            invite.teacherDocId?.takeIf { it.isNotBlank() }?.let { updatePayload["teacherId"] = it }
                        }
                        val existingSubjectName = grpSnap.getString("subjectName").orEmpty()
                        if (existingSubjectName.isBlank() && finalSubject.isNotBlank()) {
                            updatePayload["subjectName"] = finalSubject
                            invite.subjectId?.takeIf { it.isNotBlank() }?.let { updatePayload["subjectId"] = it }
                        }
                        grpRef.update(updatePayload).await()
                    }
                } catch (_: Exception) {
                }
            }

            // 4. ربط سجل المعلم بالـ UID وتحديث المادة والمجموعات المؤكدة قبل إغلاق حالة الدعوة
            invite.teacherDocId?.takeIf { it.isNotBlank() }?.let { teacherDocId ->
                try {
                    academyRef.collection("teachers").document(teacherDocId).update(
                        mapOf(
                            "linkedUid" to uid,
                            "subject" to finalSubject,
                            "assignedGroupIds" to finalAssignedGroupIds,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (_: Exception) {
                }
            }

            // 5. تحديث الدعوة لحالة مستخدمة مع توثيق المجموعة والمادة المؤكدة
            inviteRef.update(
                mapOf(
                    "status" to "claimed",
                    "claimedByUid" to uid,
                    "subjectName" to finalSubject,
                    "groupName" to finalGroupName,
                    "gradeLevel" to finalGradeLevel,
                    "assignedGroupIds" to finalAssignedGroupIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 6. تحديث ملف المستخدم الشخصي
            db.collection("users").document(uid).update(
                mapOf(
                    "academyId" to invite.academyId,
                    "role" to UserRole.TEACHER.key,
                    "assignedGroupIds" to finalAssignedGroupIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeAcademyConfig(academyId: String, uid: String): Flow<AcademyConfig?> = flow {
        val path = "academies/$academyId"
        emitAll(
            db.collection("academies").document(academyId)
                .snapshots()
                .map { it.toObject(AcademyConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observeTeachers(academyId: String, uid: String, isAdmin: Boolean): Flow<List<Teacher>> = flow {
        val path = "academies/$academyId/teachers"
        val query = if (isAdmin) {
            db.collection("academies").document(academyId).collection("teachers")
        } else {
            db.collection("academies").document(academyId).collection("teachers")
                .whereEqualTo("linkedUid", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap -> snap.toObjects(Teacher::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeGroups(academyId: String, uid: String, isAdmin: Boolean): Flow<List<StudyGroup>> = flow {
        val path = "academies/$academyId/groups"
        val query = if (isAdmin) {
            db.collection("academies").document(academyId).collection("groups")
        } else {
            db.collection("academies").document(academyId).collection("groups")
                .whereArrayContains("teacherUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap -> snap.toObjects(StudyGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeStudents(academyId: String, uid: String, isAdmin: Boolean): Flow<List<Student>> =
        studentRepository.observeStudents(academyId, uid, isAdmin)

    fun observeAttendance(academyId: String, uid: String, isAdmin: Boolean): Flow<List<AttendanceRecord>> = flow {
        val path = "academies/$academyId/attendance"
        val query = if (isAdmin) {
            db.collection("academies").document(academyId).collection("attendance")
        } else {
            db.collection("academies").document(academyId).collection("attendance")
                .whereArrayContains("teacherUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap -> snap.toObjects(AttendanceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observePayments(academyId: String, uid: String): Flow<List<PaymentRecord>> = flow {
        val path = "academies/$academyId/payments"
        emitAll(
            db.collection("academies").document(academyId).collection("payments")
                .snapshots()
                .map { snap -> snap.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeEvaluations(academyId: String, uid: String, isAdmin: Boolean): Flow<List<EvaluationRecord>> = flow {
        val path = "academies/$academyId/evaluations"
        val query = if (isAdmin) {
            db.collection("academies").document(academyId).collection("evaluations")
        } else {
            db.collection("academies").document(academyId).collection("evaluations")
                .whereArrayContains("teacherUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap -> snap.toObjects(EvaluationRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeSubjects(academyId: String, uid: String): Flow<List<Subject>> = flow {
        val path = "academies/$academyId/subjects"
        emitAll(
            db.collection("academies").document(academyId).collection("subjects")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Subject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name }
                }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeInvites(academyId: String, uid: String): Flow<List<TeacherInvite>> = flow {
        val path = "invites"
        emitAll(
            db.collection("invites")
                .whereEqualTo("academyId", academyId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TeacherInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .wrapFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun updateAcademyConfig(
        academyId: String,
        academyName: String,
        subtitle: String,
        currencySymbol: String,
        primaryCalendar: String,
        absenceAlertThreshold: Long,
        evaluationCriteria: List<String>,
        darkModeDefault: Boolean,
        pinEnabled: Boolean,
        pinCode: String?
    ): Result<Unit> {
        val path = "academies/$academyId"
        return try {
            requireUserId()
            val payload = mutableMapOf<String, Any>(
                "academyName" to academyName.trim().ifBlank { "أكاديمية النخبة" }.take(100),
                "subtitle" to subtitle.trim().take(140),
                "currencySymbol" to currencySymbol.trim().ifBlank { "ج.م" }.take(16),
                "primaryCalendar" to if (primaryCalendar == CalendarMode.HIJRI.key) CalendarMode.HIJRI.key else CalendarMode.GREGORIAN.key,
                "absenceAlertThreshold" to absenceAlertThreshold.coerceIn(1L, 30L),
                "evaluationCriteria" to evaluationCriteria.map { it.trim() }.filter { it.isNotBlank() }.take(8).ifEmpty { listOf("الفهم", "الواجبات", "المشاركة", "الاختبارات") },
                "darkModeDefault" to darkModeDefault,
                "pinEnabled" to pinEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!pinCode.isNullOrBlank()) {
                payload["pinCode"] = pinCode.trim().take(12)
            }
            db.document(path).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveTeacherAndInvite(
        academyId: String,
        academyName: String = "أكاديمية النخبة",
        existingId: String?,
        name: String,
        phone: String,
        subject: String,
        subjectId: String? = null,
        email: String?,
        assignedGroupIds: List<String>,
        groupName: String = "",
        gradeLevel: Long = 1L,
        allowTeacherGroupChoice: Boolean = true,
        availableGroupOptions: List<String> = emptyList(),
        availableSubjectNames: List<String> = emptyList(),
        active: Boolean
    ): Result<String> {
        val path = "academies/$academyId/teachers"
        return try {
            val uid = requireUserId()
            val col = db.collection("academies").document(academyId).collection("teachers")
            val docRef = if (existingId.isNullOrBlank()) col.document() else col.document(existingId)
            val inviteCode = if (existingId.isNullOrBlank()) {
                "ELITE" + (1000..9999).random()
            } else {
                docRef.get().await().getString("inviteCode") ?: ("ELITE" + (1000..9999).random())
            }
            val cleanGroupIds = assignedGroupIds.distinct().take(40)
            val existingLinkedUid = if (!existingId.isNullOrBlank()) docRef.get().await().getString("linkedUid") else null
            val cleanSubject = subject.trim().take(80)
            val cleanGroupName = groupName.trim().take(80)
            val cleanAcademyName = academyName.trim().ifBlank { "أكاديمية النخبة" }.take(100)

            if (existingId.isNullOrBlank()) {
                val createMap = mapOf(
                    "name" to name.trim().take(80),
                    "phone" to phone.trim().take(30),
                    "subject" to cleanSubject,
                    "email" to email?.trim()?.takeIf { it.isNotBlank() }?.take(160),
                    "inviteCode" to inviteCode,
                    "linkedUid" to existingLinkedUid,
                    "assignedGroupIds" to cleanGroupIds,
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf(
                    "name" to name.trim().take(80),
                    "phone" to phone.trim().take(30),
                    "subject" to cleanSubject,
                    "email" to email?.trim()?.takeIf { it.isNotBlank() }?.take(160),
                    "inviteCode" to inviteCode,
                    "assignedGroupIds" to cleanGroupIds,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updateMap).await()
            }

            // إنشاء أو تحديث كود ورابط الدعوة للمعلم مع بيانات المادة والمجموعة
            val inviteRef = db.collection("invites").document(inviteCode)
            val inviteSnap = inviteRef.get().await()
            if (!inviteSnap.exists()) {
                val inviteMap = mapOf(
                    "academyId" to academyId,
                    "academyName" to cleanAcademyName,
                    "teacherName" to name.trim().take(80),
                    "teacherEmail" to email?.trim()?.takeIf { it.isNotBlank() }?.take(160),
                    "teacherDocId" to docRef.id,
                    "subjectId" to subjectId?.takeIf { it.isNotBlank() },
                    "subjectName" to cleanSubject,
                    "groupName" to cleanGroupName,
                    "gradeLevel" to gradeLevel.coerceIn(1L, 12L),
                    "allowTeacherGroupChoice" to allowTeacherGroupChoice,
                    "availableGroupOptions" to availableGroupOptions.take(40),
                    "availableSubjectNames" to availableSubjectNames.take(40),
                    "assignedGroupIds" to cleanGroupIds,
                    "status" to "pending",
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                inviteRef.set(inviteMap).await()
            } else if (inviteSnap.getString("status") == "pending") {
                val updateInviteMap = mutableMapOf<String, Any>(
                    "academyName" to cleanAcademyName,
                    "teacherName" to name.trim().take(80),
                    "teacherDocId" to docRef.id,
                    "subjectId" to (subjectId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
                    "subjectName" to cleanSubject,
                    "groupName" to cleanGroupName,
                    "gradeLevel" to gradeLevel.coerceIn(1L, 12L),
                    "allowTeacherGroupChoice" to allowTeacherGroupChoice,
                    "availableGroupOptions" to availableGroupOptions.take(40),
                    "availableSubjectNames" to availableSubjectNames.take(40),
                    "assignedGroupIds" to cleanGroupIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                inviteRef.update(updateInviteMap).await()
            }

            // تحديث صلاحية العضوية إن كان المعلم قد انضم بالفعل
            if (!existingLinkedUid.isNullOrBlank()) {
                try {
                    db.collection("academies").document(academyId)
                        .collection("members").document(existingLinkedUid)
                        .update(
                            mapOf(
                                "displayName" to name.trim().take(80),
                                "assignedGroupIds" to cleanGroupIds,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                } catch (_: Exception) {
                }
            }

            Result.success(inviteCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveGroup(
        academyId: String,
        existingId: String?,
        name: String,
        gradeLevel: Long,
        subjectId: String? = null,
        subjectName: String = "",
        subjectColorKey: String = SubjectColor.NAVY.key,
        teacherId: String?,
        teacherName: String,
        teacherUids: List<String>,
        scheduleText: String,
        maxCapacity: Long,
        monthlyFee: Double
    ): Result<String> {
        val path = "academies/$academyId/groups"
        return try {
            requireUserId()
            val col = db.collection("academies").document(academyId).collection("groups")
            val docRef = if (existingId.isNullOrBlank()) col.document() else col.document(existingId)
            val cleanUids = teacherUids.filter { it.isNotBlank() }.distinct().take(10)
            val cleanSubjectId = subjectId?.takeIf { it.isNotBlank() }
            val cleanSubjectName = subjectName.trim().take(80)
            val cleanSubjectColor = SubjectColor.fromKey(subjectColorKey).key

            if (existingId.isNullOrBlank()) {
                val createMap = mapOf(
                    "name" to name.trim().take(80),
                    "gradeLevel" to gradeLevel.coerceIn(1L, 12L),
                    "subjectId" to cleanSubjectId,
                    "subjectName" to cleanSubjectName,
                    "subjectColorKey" to cleanSubjectColor,
                    "teacherId" to teacherId?.takeIf { it.isNotBlank() },
                    "teacherName" to teacherName.trim().take(80),
                    "teacherUids" to cleanUids,
                    "scheduleText" to scheduleText.trim().take(120),
                    "maxCapacity" to maxCapacity.coerceIn(1L, 500L),
                    "monthlyFee" to monthlyFee.coerceIn(0.0, 1000000.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(createMap).await()
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "name" to name.trim().take(80),
                    "gradeLevel" to gradeLevel.coerceIn(1L, 12L),
                    "subjectId" to (cleanSubjectId ?: FieldValue.delete()),
                    "subjectName" to cleanSubjectName,
                    "subjectColorKey" to cleanSubjectColor,
                    "teacherId" to (teacherId?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
                    "teacherName" to teacherName.trim().take(80),
                    "teacherUids" to cleanUids,
                    "scheduleText" to scheduleText.trim().take(120),
                    "maxCapacity" to maxCapacity.coerceIn(1L, 500L),
                    "monthlyFee" to monthlyFee.coerceIn(0.0, 1000000.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(academyId: String, groupId: String): Result<Unit> {
        val path = "academies/$academyId/groups/$groupId"
        return try {
            requireUserId()
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun getStudentById(academyId: String, studentId: String): Result<Student?> =
        studentRepository.getStudentById(academyId, studentId)

    suspend fun saveStudent(
        academyId: String,
        existingId: String?,
        fullName: String,
        guardianName: String,
        phone: String,
        enrollmentDateMillis: Long,
        gradeLevel: Long,
        groupId: String?,
        teacherUids: List<String>,
        notes: String,
        prescribedFee: Double? = null,
        archived: Boolean = false
    ): Result<String> {
        val student = Student(
            id = existingId.orEmpty(),
            fullName = fullName,
            guardianName = guardianName,
            phone = phone,
            enrollmentDateMillis = enrollmentDateMillis,
            gradeLevel = gradeLevel,
            groupId = groupId,
            teacherUids = teacherUids,
            prescribedFee = prescribedFee,
            notes = notes,
            archived = archived
        )
        return if (existingId.isNullOrBlank()) {
            studentRepository.createStudent(academyId, student)
        } else {
            studentRepository.updateStudent(academyId, existingId, student).map { existingId }
        }
    }

    suspend fun setStudentArchived(academyId: String, studentId: String, archived: Boolean): Result<Unit> =
        studentRepository.setStudentArchived(academyId, studentId, archived)

    suspend fun transferStudentToGroup(
        academyId: String,
        studentId: String,
        newGroupId: String?,
        newGradeLevel: Long,
        newTeacherUids: List<String>
    ): Result<Unit> =
        studentRepository.transferStudentToGroup(
            academyId = academyId,
            studentId = studentId,
            newGroupId = newGroupId,
            newGradeLevel = newGradeLevel,
            newTeacherUids = newTeacherUids
        )

    suspend fun deleteStudent(academyId: String, studentId: String): Result<Unit> =
        studentRepository.deleteStudent(academyId, studentId)

    suspend fun recordAttendance(
        academyId: String,
        student: Student,
        groupId: String,
        teacherUids: List<String>,
        dateKey: String,
        status: AttendanceStatus,
        notes: String = ""
    ): Result<Unit> {
        val docId = "${dateKey}_${student.id}"
        val path = "academies/$academyId/attendance/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.document(path)
            val existing = docRef.get().await()
            val cleanUids = (teacherUids + student.teacherUids).filter { it.isNotBlank() }.distinct().take(10)
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "status" to status.key,
                        "recordedByUid" to uid,
                        "notes" to notes.trim().take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "studentId" to student.id,
                        "studentName" to student.fullName.take(100),
                        "groupId" to groupId,
                        "teacherUids" to cleanUids,
                        "dateKey" to dateKey,
                        "status" to status.key,
                        "recordedByUid" to uid,
                        "notes" to notes.trim().take(300),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllPresent(
        academyId: String,
        students: List<Student>,
        groupId: String,
        teacherUids: List<String>,
        dateKey: String
    ): Result<Unit> {
        val path = "academies/$academyId/attendance"
        return try {
            val uid = requireUserId()
            for (student in students) {
                val docId = "${dateKey}_${student.id}"
                val docRef = db.collection("academies").document(academyId).collection("attendance").document(docId)
                val existing = docRef.get().await()
                val cleanUids = (teacherUids + student.teacherUids).filter { it.isNotBlank() }.distinct().take(10)
                if (existing.exists()) {
                    docRef.update(
                        mapOf(
                            "status" to AttendanceStatus.PRESENT.key,
                            "recordedByUid" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "studentId" to student.id,
                            "studentName" to student.fullName.take(100),
                            "groupId" to groupId,
                            "teacherUids" to cleanUids,
                            "dateKey" to dateKey,
                            "status" to AttendanceStatus.PRESENT.key,
                            "recordedByUid" to uid,
                            "notes" to "",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun savePayment(
        academyId: String,
        student: Student,
        monthKey: String,
        amountDue: Double,
        amountPaid: Double,
        method: PaymentMethod,
        paidAtMillis: Long,
        notes: String
    ): Result<PaymentRecord> {
        val path = "academies/$academyId/payments"
        return try {
            val uid = requireUserId()
            val col = db.collection("academies").document(academyId).collection("payments")
            val docRef = col.document()
            val receiptNumber = "EL-${monthKey.replace("-", "")}-${(1000..9999).random()}"
            val payload = mapOf(
                "studentId" to student.id,
                "studentName" to student.fullName.take(100),
                "groupId" to student.groupId?.takeIf { it.isNotBlank() },
                "monthKey" to monthKey,
                "amountDue" to amountDue.coerceAtLeast(0.0),
                "amountPaid" to amountPaid.coerceAtLeast(0.0),
                "paymentMethod" to method.key,
                "receiptNumber" to receiptNumber,
                "paidAtMillis" to paidAtMillis.coerceAtLeast(0L),
                "recordedByUid" to uid,
                "notes" to notes.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(
                PaymentRecord(
                    id = docRef.id,
                    studentId = student.id,
                    studentName = student.fullName,
                    groupId = student.groupId,
                    monthKey = monthKey,
                    amountDue = amountDue,
                    amountPaid = amountPaid,
                    paymentMethod = method.key,
                    receiptNumber = receiptNumber,
                    paidAtMillis = paidAtMillis,
                    recordedByUid = uid,
                    notes = notes
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveEvaluation(
        academyId: String,
        student: Student,
        subject: String,
        scores: Map<String, Long>,
        maxPerCriterion: Double,
        gradeLabel: String,
        evaluatedDateMillis: Long,
        notes: String
    ): Result<Unit> {
        val path = "academies/$academyId/evaluations"
        return try {
            val uid = requireUserId()
            val totalScore = scores.values.sum().toDouble()
            val maxTotal = (scores.size.coerceAtLeast(1) * maxPerCriterion).coerceAtLeast(1.0)
            val pct = ((totalScore / maxTotal) * 100.0).coerceIn(0.0, 100.0)
            val docRef = db.collection("academies").document(academyId).collection("evaluations").document()
            val payload = mapOf(
                "studentId" to student.id,
                "studentName" to student.fullName.take(100),
                "groupId" to student.groupId?.takeIf { it.isNotBlank() },
                "teacherUids" to student.teacherUids.filter { it.isNotBlank() }.distinct().take(10),
                "subject" to subject.trim().take(80),
                "scores" to scores,
                "totalScore" to totalScore,
                "maxTotalScore" to maxTotal,
                "percentage" to pct,
                "gradeLabel" to gradeLabel,
                "evaluatedDateMillis" to evaluatedDateMillis.coerceAtLeast(0L),
                "notes" to notes.trim().take(500),
                "recordedByUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveSubject(
        academyId: String,
        existingId: String?,
        name: String,
        code: String,
        colorKey: String,
        description: String,
        active: Boolean
    ): Result<String> {
        var docPath = "academies/$academyId/subjects"
        return try {
            requireUserId()
            val cleanName = name.trim().replace(Regex("\\s+"), " ").take(80)
            require(cleanName.isNotBlank()) { "يرجى إدخال اسم المادة الدراسية" }
            val normalized = normalizeSubjectName(cleanName).take(80)
            val cleanCode = code.trim().uppercase().take(20)
            val validColor = SubjectColor.fromKey(colorKey).key
            val cleanDesc = description.trim().take(200)

            val col = db.collection("academies").document(academyId).collection("subjects")
            val duplicateName = col.whereEqualTo("normalizedName", normalized).get().await()
                .documents.any { it.id != existingId }
            if (duplicateName) {
                return Result.failure(
                    IllegalArgumentException("المادة «$cleanName» مضافة بالفعل، يرجى إدخال اسم مادة مختلفة")
                )
            }
            if (cleanCode.isNotBlank()) {
                val duplicateCode = col.whereEqualTo("code", cleanCode).get().await()
                    .documents.any { it.id != existingId }
                if (duplicateCode) {
                    return Result.failure(
                        IllegalArgumentException("رمز المادة «$cleanCode» مستخدم لمادة أخرى، يرجى اختيار رمز مختلف")
                    )
                }
            }

            val docRef = if (existingId.isNullOrBlank()) col.document() else col.document(existingId)
            docPath = docRef.path
            if (existingId.isNullOrBlank()) {
                docRef.set(
                    mapOf(
                        "name" to cleanName,
                        "normalizedName" to normalized,
                        "code" to cleanCode,
                        "colorKey" to validColor,
                        "description" to cleanDesc,
                        "active" to active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "normalizedName" to normalized,
                        "code" to cleanCode,
                        "colorKey" to validColor,
                        "description" to cleanDesc,
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSubject(academyId: String, subjectId: String): Result<Unit> {
        val path = "academies/$academyId/subjects/$subjectId"
        return try {
            requireUserId()
            require(subjectId.isNotBlank()) { "معرّف المادة مطلوب" }
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
