package com.back.domain.habitVerify.dto

import jakarta.validation.constraints.NotBlank

// @JvmRecord: 기존 Java 테스트가 request.imageUrl()처럼 record 접근자로 읽으므로 Java record로 컴파일한다
// imageUrl이 nullable인 이유: null이어도 객체는 만들어지고 @NotBlank 검증 오류(400 "입력값 검증에 실패했습니다.")로 응답해야 한다
@JvmRecord
data class HabitVerifyRequest(
    val description: String?,

    @field:NotBlank(message = "인증 사진은 필수입니다.")
    val imageUrl: String?,
)
