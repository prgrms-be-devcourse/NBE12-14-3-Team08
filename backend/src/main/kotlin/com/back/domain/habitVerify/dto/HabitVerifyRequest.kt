package com.back.domain.habitVerify.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// @JvmRecord: 기존 Java 테스트가 request.imageUrl()처럼 record 접근자로 읽으므로 Java record로 컴파일한다
// imageUrl이 nullable인 이유: null이어도 객체는 만들어지고 @NotBlank 검증 오류(400 "입력값 검증에 실패했습니다.")로 응답해야 한다
// 설명 길이는 DB 컬럼 길이(HabitVerify.description: 255자)와 맞춘다.
// 검증이 없으면 저장할 때 DB 오류가 그대로 올라와 400이 아니라 500이 된다.
// @Size는 UTF-16 글자 수 기준이라 이모지처럼 두 글자로 세는 문자는 DB보다 먼저 막힐 수 있다. (DB 한도를 넘는 일은 없다)
@JvmRecord
data class HabitVerifyRequest(
    @field:Size(max = DESCRIPTION_MAX_LENGTH, message = "습관 인증 설명은 255자 이하여야 합니다.")
    val description: String?,

    @field:NotBlank(message = "인증 사진은 필수입니다.")
    val imageUrl: String?,
) {
    companion object {
        const val DESCRIPTION_MAX_LENGTH = 255
    }
}
