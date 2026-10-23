package com.back.domain.member.dto

import com.back.domain.member.entity.Member
import java.time.LocalDateTime

data class MemberResponse(
    val memberId: Long?,
    val nickname: String,
    val username: String,
    val createDate: LocalDateTime?,
    val modifyDate: LocalDateTime?,
) {

    companion object {
        @JvmStatic
        fun from(member: Member): MemberResponse = MemberResponse(
            memberId = member.id,
            nickname = member.nickname,
            username = member.username,
            createDate = member.createDate,
            modifyDate = member.modifyDate,
        )
    }
}
