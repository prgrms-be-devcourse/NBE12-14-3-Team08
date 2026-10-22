package com.back.domain.habit.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

// 요청 DTO는 누락·null 입력을 Jackson 파싱 오류가 아니라 Bean Validation 메시지로 응답하도록
// (기존 record와 동일한 동작) nullable + 기본값으로 선언하고 검증 애노테이션으로 거른다.
data class CreateHabitRequest(
    @field:NotBlank(message = "습관 제목은 필수입니다.")
    val title: String? = null,

    val description: String? = null,

    @field:Min(value = 1, message = "실천 일수는 최소 1일입니다.")
    @field:Max(value = 7, message = "실천 일수는 최대 7일입니다.")
    val days: Int = 0,
)