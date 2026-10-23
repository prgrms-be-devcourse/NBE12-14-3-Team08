package com.back.domain.member.dto

import jakarta.validation.constraints.NotBlank

// 필드를 nullable로 두는 이유: 값이 빠진 요청이 JSON 파싱 오류가 아니라 기존처럼 @NotBlank 검증 메시지로 거절되게 하기 위해서다.
data class CreateMemberRequest(
    @field:NotBlank(message = "이름은 필수입니다.")
    val nickname: String? = null,

    @field:NotBlank(message = "아이디는 필수입니다.")
    val username: String? = null,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String? = null,
)
