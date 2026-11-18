package com.example.util

import com.example.model.InviteGroupOption

object InviteLinkHelper {
    const val INVITE_WEB_HOST = "eliteacademy.app"
    const val INVITE_CUSTOM_SCHEME = "eliteacademy"

    fun buildInviteLink(inviteCode: String): String {
        val clean = inviteCode.trim().uppercase()
        return "https://$INVITE_WEB_HOST/invite?code=$clean"
    }

    fun buildCustomSchemeLink(inviteCode: String): String {
        val clean = inviteCode.trim().uppercase()
        return "$INVITE_CUSTOM_SCHEME://invite?code=$clean"
    }

    fun extractInviteCode(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        if (
            trimmed.contains("://") ||
            trimmed.contains("invite", ignoreCase = true) ||
            trimmed.contains("code=", ignoreCase = true)
        ) {
            val queryMatch = Regex("[?&]code=([a-zA-Z0-9_\\-]+)", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
            if (!queryMatch.isNullOrBlank()) return queryMatch.uppercase()

            val pathMatch = Regex("/invite/([a-zA-Z0-9_\\-]+)", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
            if (!pathMatch.isNullOrBlank()) return pathMatch.uppercase()
        }
        return trimmed.uppercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    fun encodeGroupOption(id: String, name: String, gradeLevel: Long, subjectName: String): String {
        val safeName = name.replace("|", " ").trim()
        val safeSubject = subjectName.replace("|", " ").trim()
        return "$id|$safeName|$gradeLevel|$safeSubject"
    }

    fun decodeGroupOption(raw: String): InviteGroupOption? {
        val parts = raw.split("|")
        if (parts.size < 2 || parts[0].isBlank()) return null
        return InviteGroupOption(
            id = parts[0],
            name = parts[1],
            gradeLevel = parts.getOrNull(2)?.toLongOrNull()?.coerceIn(1L, 12L) ?: 1L,
            subjectName = parts.getOrNull(3).orEmpty()
        )
    }

    fun buildShareMessage(
        academyName: String,
        teacherName: String,
        inviteCode: String,
        subjectName: String,
        groupName: String
    ): String {
        val link = buildInviteLink(inviteCode)
        return buildString {
            appendLine("دعوة للانضمام كمعلم في $academyName")
            if (teacherName.isNotBlank()) appendLine("المعلم: $teacherName")
            if (subjectName.isNotBlank()) appendLine("المادة الدراسية: $subjectName")
            if (groupName.isNotBlank()) appendLine("المجموعة: $groupName")
            appendLine("كود الدعوة: $inviteCode")
            append("رابط الدعوة المباشر: $link")
        }.trim()
    }
}
