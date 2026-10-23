package com.back.domain.habit.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// 습관 제목·설명의 길이 제한(DB 컬럼 길이 255자와 같음)을 확인한다.
class CreateHabitRequestLengthTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private fun messages(request: CreateHabitRequest): List<String> =
        validator.validate(request).map { it.message }

    @ParameterizedTest(name = "제목 {0}자")
    @ValueSource(ints = [1, 254, 255])
    @DisplayName("제목이 255자 이하면 통과한다 (경계값 255 포함)")
    fun 제목_255자_이하는_통과한다(length: Int) {
        assertThat(messages(CreateHabitRequest("가".repeat(length), "설명", 3))).isEmpty()
    }

    @ParameterizedTest(name = "제목 {0}자")
    @ValueSource(ints = [256, 300, 5000])
    @DisplayName("제목이 255자를 넘으면 길이 메시지로 거절한다 (경계값 256 포함)")
    fun 제목_255자를_넘으면_거절한다(length: Int) {
        assertThat(messages(CreateHabitRequest("가".repeat(length), "설명", 3)))
            .containsExactly("습관 제목은 255자 이하여야 합니다.")
    }

    @ParameterizedTest(name = "설명 {0}자")
    @ValueSource(ints = [0, 1, 254, 255])
    @DisplayName("설명이 255자 이하면 통과한다 (빈 설명, 경계값 255 포함)")
    fun 설명_255자_이하는_통과한다(length: Int) {
        assertThat(messages(CreateHabitRequest("독서", "나".repeat(length), 3))).isEmpty()
    }

    @ParameterizedTest(name = "설명 {0}자")
    @ValueSource(ints = [256, 300, 5000])
    @DisplayName("설명이 255자를 넘으면 길이 메시지로 거절한다 (경계값 256 포함)")
    fun 설명_255자를_넘으면_거절한다(length: Int) {
        assertThat(messages(CreateHabitRequest("독서", "나".repeat(length), 3)))
            .containsExactly("습관 설명은 255자 이하여야 합니다.")
    }

    @Test
    @DisplayName("제목과 설명이 모두 길면 위반이 각각 보고된다")
    fun 제목과_설명이_모두_길면_모두_보고한다() {
        assertThat(messages(CreateHabitRequest("가".repeat(256), "나".repeat(256), 3)))
            .containsExactlyInAnyOrder("습관 제목은 255자 이하여야 합니다.", "습관 설명은 255자 이하여야 합니다.")
    }

    @Test
    @DisplayName("공백뿐인 제목은 길이와 상관없이 필수 메시지로 거절한다 (기존 검증 유지)")
    fun 공백_제목은_필수_메시지로_거절한다() {
        assertThat(messages(CreateHabitRequest(" ".repeat(10), "설명", 3)))
            .containsExactly("습관 제목은 필수입니다.")
    }

    @Test
    @DisplayName("이모지처럼 UTF-16으로 두 글자인 문자는 DB 한도보다 먼저 막힌다 (한도를 넘겨 저장되는 일은 없다)")
    fun 이모지는_두_글자로_센다() {
        // 😀 한 개 = 두 글자. 128개면 256글자라 거절, 127개(254글자)는 통과
        assertThat(messages(CreateHabitRequest("😀".repeat(127), "설명", 3))).isEmpty()
        assertThat(messages(CreateHabitRequest("😀".repeat(128), "설명", 3)))
            .containsExactly("습관 제목은 255자 이하여야 합니다.")
    }
}
