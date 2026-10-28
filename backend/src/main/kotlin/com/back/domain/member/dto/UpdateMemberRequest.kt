package com.back.domain.member.dto

import jakarta.validation.constraints.Size

// 닉네임 길이는 가입 요청과 같은 기준(DB 컬럼 길이 255자)이다. 비어 있으면 닉네임은 바꾸지 않는다.
data class UpdateMemberRequest(
    @field:Size(max = CreateMemberRequest.NICKNAME_MAX_LENGTH, message = "이름은 255자 이하여야 합니다.")
    val nickname: String? = null,
    val currentPassword: String? = null,
    val newPassword: String? = null,
)
