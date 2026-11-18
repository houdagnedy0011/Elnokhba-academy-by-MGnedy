package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UserRole(val key: String, val arabicLabel: String) {
    ADMIN("admin", "مدير الأكاديمية"),
    TEACHER("teacher", "معلم"),
    PENDING("pending", "قيد الإعداد");

    companion object {
        fun fromKey(raw: String?): UserRole =
            entries.firstOrNull { it.key == raw } ?: PENDING
    }
}

enum class AttendanceStatus(val key: String, val arabicLabel: String) {
    PRESENT("present", "حاضر"),
    ABSENT("absent", "غائب"),
    LATE("late", "متأخر"),
    EXCUSED("excused", "بعذر");

    companion object {
        fun fromKey(raw: String?): AttendanceStatus =
            entries.firstOrNull { it.key == raw } ?: PRESENT
    }
}

enum class PaymentMethod(val key: String, val arabicLabel: String) {
    CASH("cash", "نقدي"),
    TRANSFER("transfer", "تحويل بنكي"),
    WALLET("wallet", "محفظة إلكترونية"),
    INSTAPAY("instapay", "إنستاباي");

    companion object {
        fun fromKey(raw: String?): PaymentMethod =
            entries.firstOrNull { it.key == raw } ?: CASH
    }
}

enum class CalendarMode(val key: String, val arabicLabel: String) {
    GREGORIAN("gregorian", "الميلادي أولاً"),
    HIJRI("hijri", "الهجري أولاً");

    companion object {
        fun fromKey(raw: String?): CalendarMode =
            entries.firstOrNull { it.key == raw } ?: GREGORIAN
    }
}

enum class SubjectColor(
    val key: String,
    val arabicLabel: String,
    val hexColor: Long
) {
    NAVY("navy", "كحلي", 0xFF15243D),
    GOLD("gold", "ذهبي", 0xFFC59B27),
    EMERALD("emerald", "زمردي", 0xFF147D55),
    INFO("info", "سماوي", 0xFF1E64B7),
    AMBER("amber", "كهرماني", 0xFFC87D0A),
    CRIMSON("crimson", "ياقوتي", 0xFFBA1A1A),
    TEAL("teal", "فيروزي", 0xFF00796B),
    PLUM("plum", "أرجواني", 0xFF6A1B9A);

    companion object {
        fun fromKey(raw: String?): SubjectColor =
            entries.firstOrNull { it.key == raw } ?: NAVY
    }
}

fun normalizeSubjectName(raw: String): String =
    raw.trim()
        .replace(Regex("\\s+"), " ")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .lowercase()

object GradeCatalog {
    val grades: List<Pair<Int, String>> = listOf(
        1 to "الصف الأول الابتدائي",
        2 to "الصف الثاني الابتدائي",
        3 to "الصف الثالث الابتدائي",
        4 to "الصف الرابع الابتدائي",
        5 to "الصف الخامس الابتدائي",
        6 to "الصف السادس الابتدائي",
        7 to "الصف الأول الإعدادي",
        8 to "الصف الثاني الإعدادي",
        9 to "الصف الثالث الإعدادي",
        10 to "الصف الأول الثانوي",
        11 to "الصف الثاني الثانوي",
        12 to "الصف الثالث الثانوي"
    )

    fun labelFor(level: Int): String =
        grades.firstOrNull { it.first == level }?.second ?: "الصف $level"

    fun shortLabelFor(level: Int): String = when (level) {
        in 1..6 -> "${level} ابتدائي"
        in 7..9 -> "${level - 6} إعدادي"
        in 10..12 -> "${level - 9} ثانوي"
        else -> "الصف $level"
    }

    fun classifyPercentage(percentage: Double): String = when {
        percentage >= 90.0 -> "ممتاز"
        percentage >= 80.0 -> "جيد جداً"
        percentage >= 70.0 -> "جيد"
        percentage >= 60.0 -> "مقبول"
        else -> "يحتاج متابعة"
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val academyId: String? = null,
    val role: String = UserRole.PENDING.key,
    val assignedGroupIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AcademyConfig(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val academyName: String = "أكاديمية النخبة",
    val subtitle: String = "التميز والريادة التعليمية",
    val currencySymbol: String = "ج.م",
    val primaryCalendar: String = CalendarMode.GREGORIAN.key,
    val absenceAlertThreshold: Long = 3L,
    val evaluationCriteria: List<String> = listOf("الفهم", "الواجبات", "المشاركة", "الاختبارات"),
    val darkModeDefault: Boolean = false,
    val pinEnabled: Boolean = false,
    val pinCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AcademyMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = UserRole.TEACHER.key,
    val assignedGroupIds: List<String> = emptyList(),
    val inviteCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class InviteGroupOption(
    val id: String,
    val name: String,
    val gradeLevel: Long,
    val subjectName: String
)

@Keep
@IgnoreExtraProperties
data class TeacherInvite(
    @DocumentId val id: String = "",
    val academyId: String = "",
    val academyName: String = "",
    val teacherName: String = "",
    val teacherEmail: String? = null,
    val teacherDocId: String? = null,
    val subjectId: String? = null,
    val subjectName: String = "",
    val groupName: String = "",
    val gradeLevel: Long = 1L,
    val allowTeacherGroupChoice: Boolean = true,
    val availableGroupOptions: List<String> = emptyList(),
    val availableSubjectNames: List<String> = emptyList(),
    val assignedGroupIds: List<String> = emptyList(),
    val status: String = "pending",
    val claimedByUid: String? = null,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Teacher(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val subject: String = "",
    val email: String? = null,
    val inviteCode: String? = null,
    val linkedUid: String? = null,
    val assignedGroupIds: List<String> = emptyList(),
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudyGroup(
    @DocumentId val id: String = "",
    val name: String = "",
    val gradeLevel: Long = 1L,
    val subjectId: String? = null,
    val subjectName: String = "",
    val subjectColorKey: String = SubjectColor.NAVY.key,
    val teacherId: String? = null,
    val teacherName: String = "",
    val teacherUids: List<String> = emptyList(),
    val scheduleText: String = "",
    val maxCapacity: Long = 20L,
    val monthlyFee: Double = 300.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:com.google.firebase.firestore.Exclude
    val subjectColor: SubjectColor
        get() = SubjectColor.fromKey(subjectColorKey)
}

/**
 * Represents an academy student stored in Firestore under `/academies/{academyId}/students/{studentId}`.
 * Includes name ([fullName]), grade ([gradeLevel]), contact info ([guardianName], [phone]),
 * registration date ([enrollmentDateMillis]), group assignment, and archive status.
 */
@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val fullName: String = "",
    val guardianName: String = "",
    val phone: String = "",
    val enrollmentDateMillis: Long = System.currentTimeMillis(),
    val gradeLevel: Long = 1L,
    val groupId: String? = null,
    val teacherUids: List<String> = emptyList(),
    val prescribedFee: Double? = null,
    val notes: String = "",
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:com.google.firebase.firestore.Exclude
    val name: String
        get() = fullName

    @get:com.google.firebase.firestore.Exclude
    val grade: Int
        get() = gradeLevel.toInt()

    @get:com.google.firebase.firestore.Exclude
    val contactInfo: String
        get() = listOfNotNull(
            guardianName.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }
        ).joinToString(" • ")

    @get:com.google.firebase.firestore.Exclude
    val registrationDateMillis: Long
        get() = enrollmentDateMillis
}

enum class InitialPaymentChoice {
    PAID,
    UNPAID
}

data class StudentFeeStatusDisplay(
    val amountDue: Double,
    val amountPaid: Double,
    val remaining: Double,
    val currencySymbol: String = "ج.م"
) {
    val isPaidInFull: Boolean get() = remaining <= 0.0
    val isPartiallyPaid: Boolean get() = amountPaid > 0.0 && remaining > 0.0
    val isUnpaid: Boolean get() = amountPaid <= 0.0 && remaining > 0.0
}

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val groupId: String = "",
    val teacherUids: List<String> = emptyList(),
    val dateKey: String = "",
    val status: String = AttendanceStatus.PRESENT.key,
    val recordedByUid: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val groupId: String? = null,
    val monthKey: String = "",
    val amountDue: Double = 0.0,
    val amountPaid: Double = 0.0,
    val paymentMethod: String = PaymentMethod.CASH.key,
    val receiptNumber: String = "",
    val paidAtMillis: Long = System.currentTimeMillis(),
    val recordedByUid: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val name: String = "",
    val normalizedName: String = "",
    val code: String = "",
    val colorKey: String = SubjectColor.NAVY.key,
    val description: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:com.google.firebase.firestore.Exclude
    val subjectColor: SubjectColor
        get() = SubjectColor.fromKey(colorKey)
}

@Keep
@IgnoreExtraProperties
data class EvaluationRecord(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val groupId: String? = null,
    val teacherUids: List<String> = emptyList(),
    val subject: String = "",
    val scores: Map<String, Long> = emptyMap(),
    val totalScore: Double = 0.0,
    val maxTotalScore: Double = 100.0,
    val percentage: Double = 0.0,
    val gradeLabel: String = "ممتاز",
    val evaluatedDateMillis: Long = System.currentTimeMillis(),
    val notes: String = "",
    val recordedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ConsecutiveAbsenceAlert(
    val student: Student,
    val groupName: String,
    val consecutiveCount: Int
)

data class MonthlyStudentPaymentSummary(
    val student: Student,
    val groupName: String,
    val amountDue: Double,
    val amountPaid: Double,
    val remaining: Double,
    val latestPayment: PaymentRecord?
)
