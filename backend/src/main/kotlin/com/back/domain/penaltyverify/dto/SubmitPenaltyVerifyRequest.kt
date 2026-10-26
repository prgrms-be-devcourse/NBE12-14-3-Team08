package com.back.domain.penaltyverify.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 설명 길이는 DB 컬럼 길이(PenaltyVerify.description: 255자)와 맞춘다.
// 검증이 없으면 저장할 때 DB 오류가 그대로 올라와 400이 아니라 500이 된다.
// @Size는 UTF-16 글자 수 기준이라 이모지처럼 두 글자로 세는 문자는 DB보다 먼저 막힐 수 있다. (DB 한도를 넘는 일은 없다)
data class SubmitPenaltyVerifyRequest(
    @field:Size(max = DESCRIPTION_MAX_LENGTH, message = "벌칙 인증 설명은 255자 이하여야 합니다.")
    val description: String?,

    @field:NotBlank(message = "인증 사진은 필수입니다.")
    val imageUrl: String?,
) {
    companion object {
        const val DESCRIPTION_MAX_LENGTH = 255
    }
}
