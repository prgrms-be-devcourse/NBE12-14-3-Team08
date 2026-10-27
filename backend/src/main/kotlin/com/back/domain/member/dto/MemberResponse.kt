package com.back.domain.member.dto

import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberProvider
import java.time.LocalDateTime

data class MemberResponse(
    val memberId: Long?,
    val nickname: String,
    val username: String,
    // 가입 방식. 소셜 회원은 username이 자동으로 만들어진 값이라 화면에서 다르게 보여 주는 데 쓴다.
    val provider: MemberProvider,
    val createDate: LocalDateTime?,
    val modifyDate: LocalDateTime?,
) {

    companion object {
        @JvmStatic
        fun from(member: Member): MemberResponse = MemberResponse(
            memberId = member.id,
            nickname = member.nickname,
            username = member.username,
            provider = member.provider,
            createDate = member.createDate,
            modifyDate = member.modifyDate,
        )
    }
}
