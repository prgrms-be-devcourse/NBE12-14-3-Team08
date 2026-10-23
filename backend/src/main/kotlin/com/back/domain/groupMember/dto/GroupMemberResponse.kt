
package com.back.domain.groupMember.dto

interface GroupMemberResponse {

    data class Simple(
        val groupMemberId: Long,
        val memberId: Long,
        val nickname: String,
        val username: String,
        val role: String,
        val habitId: Long?,
        val habitTitle: String?,
        val habitDescription: String?
    )

    data class Detail(
        val groupMemberId: Long,
        val memberId: Long,
        val nickname: String,
        val username: String,
        val role: String,
        val penaltyCount: Long
    )

    data class JoinSuccess(
        val groupId: Long
    )
}