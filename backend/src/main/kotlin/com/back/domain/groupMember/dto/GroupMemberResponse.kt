
package com.back.domain.groupMember.dto

import com.back.domain.member.entity.MemberProvider

interface GroupMemberResponse {

    data class Simple(
        val groupMemberId: Long,
        val memberId: Long,
        val nickname: String,
        val username: String,
        val role: String,
        val habitId: Long?,
        val habitTitle: String?,
        val habitDescription: String?,
        val provider: MemberProvider = MemberProvider.LOCAL
    )

    data class Detail(
        val groupMemberId: Long,
        val memberId: Long,
        val nickname: String,
        val username: String,
        val role: String,
        val penaltyCount: Long,
        val provider: MemberProvider = MemberProvider.LOCAL
    )

    data class JoinSuccess(
        val groupId: Long
    )
}