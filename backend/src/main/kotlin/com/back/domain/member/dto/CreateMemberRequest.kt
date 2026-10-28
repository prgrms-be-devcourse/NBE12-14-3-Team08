package com.back.domain.member.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 필드를 nullable로 두는 이유: 값이 빠진 요청이 JSON 파싱 오류가 아니라 기존처럼 @NotBlank 검증 메시지로 거절되게 하기 위해서다.
//
// 닉네임·아이디 길이는 DB 컬럼 길이(Member.nickname, Member.username: 255자)와 맞춘다.
// 검증이 없으면 저장할 때 DB 오류가 그대로 올라와 400이 아니라 500이 된다.
// @Size는 UTF-16 글자 수 기준이라 이모지처럼 두 글자로 세는 문자는 DB보다 먼저 막힐 수 있다. (DB 한도를 넘는 일은 없다)
data class CreateMemberRequest(
    @field:NotBlank(message = "이름은 필수입니다.")
    @field:Size(max = NICKNAME_MAX_LENGTH, message = "이름은 255자 이하여야 합니다.")
    val nickname: String? = null,

    @field:NotBlank(message = "아이디는 필수입니다.")
    @field:Size(max = USERNAME_MAX_LENGTH, message = "아이디는 255자 이하여야 합니다.")
    val username: String? = null,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String? = null,
) {
    companion object {
        const val NICKNAME_MAX_LENGTH = 255
        const val USERNAME_MAX_LENGTH = 255
    }
}
