package net.marvinweber.simsli.domain.model

import java.time.Instant

data class HouseholdMember(
    val id: String,
    val householdId: String,
    val userId: String,
    val role: MemberRole,
    val email: String?,
    val name: String?,
    val joinedAt: Instant,
    val isCurrentUser: Boolean = false
)

enum class MemberRole {
    OWNER,
    MEMBER;

    companion object {
        fun fromString(value: String): MemberRole = when (value.lowercase()) {
            "owner" -> OWNER
            else -> MEMBER
        }
    }
}
