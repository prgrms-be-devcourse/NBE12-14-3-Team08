package com.back.domain.habit.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

// 요청 DTO의 Bean Validation 경계를 확인한다. (컨트롤러 통합 테스트는 대표 실패 케이스만 확인하므로 경계는 여기서 본다)
class CreateHabitRequestValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private fun messages(request: CreateHabitRequest): List<String> =
        validator.validate(request).map { it.message }

    @ParameterizedTest(name = "days={0}")
    @ValueSource(ints = [1, 2, 6, 7])
    @DisplayName("실천 일수가 1~7이면 통과한다 (경계값 1, 7 포함)")
    fun 실천_일수_범위_안은_통과한다(days: Int) {
        assertThat(messages(CreateHabitRequest("운동", "설명", days))).isEmpty()
    }

    @ParameterizedTest(name = "days={0}")
    @ValueSource(ints = [0, -1, Int.MIN_VALUE])
    @DisplayName("실천 일수가 1보다 작으면 최소 일수 메시지로 거절한다")
    fun 실천_일수가_1보다_작으면_거절한다(days: Int) {
        assertThat(messages(CreateHabitRequest("운동", "설명", days)))
            .containsExactly("실천 일수는 최소 1일입니다.")
    }

    @ParameterizedTest(name = "days={0}")
    @ValueSource(ints = [8, 100, Int.MAX_VALUE])
    @DisplayName("실천 일수가 7보다 크면 최대 일수 메시지로 거절한다")
    fun 실천_일수가_7보다_크면_거절한다(days: Int) {
        assertThat(messages(CreateHabitRequest("운동", "설명", days)))
            .containsExactly("실천 일수는 최대 7일입니다.")
    }

    @Test
    @DisplayName("실천 일수를 보내지 않으면 기본값 0이라 최소 일수 메시지로 거절한다")
    fun 실천_일수를_보내지_않으면_거절한다() {
        assertThat(messages(CreateHabitRequest(title = "운동"))).containsExactly("실천 일수는 최소 1일입니다.")
    }

    @ParameterizedTest(name = "title=\"{0}\"")
    @NullSource
    @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
    @DisplayName("제목이 null, 빈 문자열, 공백뿐이면 필수 메시지로 거절한다")
    fun 제목이_비어_있으면_거절한다(title: String?) {
        assertThat(messages(CreateHabitRequest(title, "설명", 3)))
            .containsExactly("습관 제목은 필수입니다.")
    }

    @ParameterizedTest(name = "description=\"{0}\"")
    @NullSource
    @ValueSource(strings = ["", "설명"])
    @DisplayName("설명은 null이나 빈 문자열이어도 통과한다")
    fun 설명은_비어_있어도_통과한다(description: String?) {
        assertThat(messages(CreateHabitRequest("운동", description, 3))).isEmpty()
    }

    @Test
    @DisplayName("제목과 일수가 모두 잘못되면 위반이 각각 보고된다")
    fun 여러_필드가_잘못되면_모두_보고한다() {
        assertThat(messages(CreateHabitRequest("", "설명", 0)))
            .containsExactlyInAnyOrder("습관 제목은 필수입니다.", "실천 일수는 최소 1일입니다.")
    }
}
