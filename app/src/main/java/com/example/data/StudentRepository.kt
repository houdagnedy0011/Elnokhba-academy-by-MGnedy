package com.example.data

import android.content.Context
import com.example.R
import com.example.model.Student
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

/**
 * Dedicated Firestore repository handling full CRUD operations for academy [Student] records
 * stored under `academies/{academyId}/students/{studentId}`.
 */
class StudentRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً")

    private fun studentsCollection(academyId: String) =
        db.collection("academies").document(academyId).collection("students")

    /**
     * Observes academy students in real time. Admins receive all academy students;
     * teachers are scoped by `teacherUids array-contains uid` to match Firestore security rules.
     */
    fun observeStudents(academyId: String, uid: String, isAdmin: Boolean): Flow<List<Student>> = flow {
        require(academyId.isNotBlank()) { "معرّف الأكاديمية مطلوب" }
        require(uid.isNotBlank()) { "معرّف المستخدم مطلوب" }
        val path = "academies/$academyId/students"
        val query = if (isAdmin) {
            studentsCollection(academyId)
        } else {
            studentsCollection(academyId).whereArrayContains("teacherUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Student::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Fetches a single student document by ID.
     */
    suspend fun getStudentById(academyId: String, studentId: String): Result<Student?> {
        val path = "academies/$academyId/students/$studentId"
        return try {
            requireUserId()
            require(academyId.isNotBlank()) { "معرّف الأكاديمية مطلوب" }
            require(studentId.isNotBlank()) { "معرّف الطالب مطلوب" }
            val snapshot = studentsCollection(academyId).document(studentId).get().await()
            val student = if (snapshot.exists()) {
                snapshot.toObject(
                    Student::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(student)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    /**
     * Creates a new student document and returns the generated document ID.
     */
    suspend fun createStudent(academyId: String, student: Student): Result<String> {
        var docPath = "academies/$academyId/students"
        return try {
            requireUserId()
            require(academyId.isNotBlank()) { "معرّف الأكاديمية مطلوب" }
            val cleanName = student.fullName.trim().take(100)
            require(cleanName.isNotBlank()) { "يرجى إدخال الاسم الكامل للطالب" }

            val col = studentsCollection(academyId)
            val docRef = if (student.id.isNotBlank()) col.document(student.id) else col.document()
            docPath = docRef.path

            val cleanUids = student.teacherUids.filter { it.isNotBlank() }.distinct().take(10)
            val cleanFee = student.prescribedFee?.coerceIn(0.0, 1000000.0)
            val payload = mapOf(
                "fullName" to cleanName,
                "guardianName" to student.guardianName.trim().take(100),
                "phone" to student.phone.trim().take(30),
                "enrollmentDateMillis" to student.enrollmentDateMillis.coerceAtLeast(0L),
                "gradeLevel" to student.gradeLevel.coerceIn(1L, 12L),
                "groupId" to student.groupId?.takeIf { it.isNotBlank() },
                "teacherUids" to cleanUids,
                "prescribedFee" to cleanFee,
                "notes" to student.notes.trim().take(500),
                "archived" to student.archived,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Updates an existing student's fields while preserving `createdAt`.
     */
    suspend fun updateStudent(academyId: String, studentId: String, student: Student): Result<Unit> {
        val path = "academies/$academyId/students/$studentId"
        return try {
            requireUserId()
            require(academyId.isNotBlank()) { "معرّف الأكاديمية مطلوب" }
            require(studentId.isNotBlank()) { "معرّف الطالب مطلوب للتحديث" }
            val cleanName = student.fullName.trim().take(100)
            require(cleanName.isNotBlank()) { "يرجى إدخال الاسم الكامل للطالب" }

            val cleanUids = student.teacherUids.filter { it.isNotBlank() }.distinct().take(10)
            val cleanFee = student.prescribedFee?.coerceIn(0.0, 1000000.0)
            val updateMap = mutableMapOf<String, Any>(
                "fullName" to cleanName,
                "guardianName" to student.guardianName.trim().take(100),
                "phone" to student.phone.trim().take(30),
                "enrollmentDateMillis" to student.enrollmentDateMillis.coerceAtLeast(0L),
                "gradeLevel" to student.gradeLevel.coerceIn(1L, 12L),
                "teacherUids" to cleanUids,
                "notes" to student.notes.trim().take(500),
                "archived" to student.archived,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            updateMap["groupId"] = if (student.groupId.isNullOrBlank()) {
                FieldValue.delete()
            } else {
                student.groupId
            }
            updateMap["prescribedFee"] = cleanFee ?: FieldValue.delete()

            studentsCollection(academyId).document(studentId).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Soft-archives or restores a student record.
     */
    suspend fun setStudentArchived(academyId: String, studentId: String, archived: Boolean): Result<Unit> {
        val path = "academies/$academyId/students/$studentId"
        return try {
            requireUserId()
            require(academyId.isNotBlank() && studentId.isNotBlank()) { "بيانات الطالب غير مكتملة" }
            studentsCollection(academyId).document(studentId).update(
                mapOf(
                    "archived" to archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Transfers a student to another study group (or back to Newcomers if [newGroupId] is null)
     * while keeping historical attendance, payments, and evaluations intact.
     */
    suspend fun transferStudentToGroup(
        academyId: String,
        studentId: String,
        newGroupId: String?,
        newGradeLevel: Long,
        newTeacherUids: List<String>
    ): Result<Unit> {
        val path = "academies/$academyId/students/$studentId"
        return try {
            requireUserId()
            require(academyId.isNotBlank() && studentId.isNotBlank()) { "بيانات الطالب غير مكتملة" }
            val payload = mutableMapOf<String, Any>(
                "gradeLevel" to newGradeLevel.coerceIn(1L, 12L),
                "teacherUids" to newTeacherUids.filter { it.isNotBlank() }.distinct().take(10),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            payload["groupId"] = if (newGroupId.isNullOrBlank()) FieldValue.delete() else newGroupId
            studentsCollection(academyId).document(studentId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes a student document from Firestore (admin-only per security rules).
     */
    suspend fun deleteStudent(academyId: String, studentId: String): Result<Unit> {
        val path = "academies/$academyId/students/$studentId"
        return try {
            requireUserId()
            require(academyId.isNotBlank() && studentId.isNotBlank()) { "بيانات الطالب غير مكتملة" }
            studentsCollection(academyId).document(studentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
