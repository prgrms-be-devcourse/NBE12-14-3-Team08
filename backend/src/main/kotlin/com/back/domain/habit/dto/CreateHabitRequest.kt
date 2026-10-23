package com.back.domain.habit.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 요청 DTO는 누락·null 입력을 Jackson 파싱 오류가 아니라 Bean Validation 메시지로 응답하도록
// (기존 record와 동일한 동작) nullable + 기본값으로 선언하고 검증 애노테이션으로 거른다.
//
// 제목·설명 길이는 DB 컬럼 길이(Habit.title, Habit.description: 255자)와 맞춘다.
// 검증이 없으면 저장할 때 DB 오류가 그대로 올라와 400이 아니라 500이 된다.
// @Size는 UTF-16 글자 수 기준이라 이모지처럼 두 글자로 세는 문자는 DB보다 먼저 막힐 수 있다. (DB 한도를 넘는 일은 없다)
data class CreateHabitRequest(
    @field:NotBlank(message = "습관 제목은 필수입니다.")
    @field:Size(max = TITLE_MAX_LENGTH, message = "습관 제목은 255자 이하여야 합니다.")
    val title: String? = null,

    @field:Size(max = DESCRIPTION_MAX_LENGTH, message = "습관 설명은 255자 이하여야 합니다.")
    val description: String? = null,

    @field:Min(value = 1, message = "실천 일수는 최소 1일입니다.")
    @field:Max(value = 7, message = "실천 일수는 최대 7일입니다.")
    val days: Int = 0,
) {
    companion object {
        const val TITLE_MAX_LENGTH = 255
        const val DESCRIPTION_MAX_LENGTH = 255
    }
}