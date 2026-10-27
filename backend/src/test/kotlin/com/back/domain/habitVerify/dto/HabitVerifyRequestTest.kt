package com.back.domain.habitVerify.dto

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class HabitVerifyRequestTest {

    companion object {
        private lateinit var factory: ValidatorFactory
        private lateinit var validator: Validator

        @JvmStatic
        @BeforeAll
        fun setUp() {
            factory = Validation.buildDefaultValidatorFactory()
            validator = factory.validator
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            factory.close()
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   "])
    @DisplayName("사진 URL이 없거나 비어 있으면 검증에 실패한다 (습관 인증 사진은 필수)")
    fun 사진_없음(imageUrl: String?) {
        val violations = validator.validate(HabitVerifyRequest("인증", imageUrl))

        assertThat(violations)
            .extracting({ it.propertyPath.toString() }, ConstraintViolation<HabitVerifyRequest>::getMessage)
            .containsExactly(tuple("imageUrl", "인증 사진은 필수입니다."))
    }

    @Test
    @DisplayName("사진 URL이 있으면 설명이 없어도 검증을 통과한다")
    fun 사진_있음() {
        val violations = validator.validate(HabitVerifyRequest(null, "https://storage/a.png"))

        assertThat(violations).isEmpty()
    }

    @Test
    @DisplayName("사진 크기를 보내지 않아도 검증을 통과한다 (크기는 선택)")
    fun 크기_없음() {
        assertThat(validator.validate(HabitVerifyRequest("인증", "https://storage/a.png"))).isEmpty()
    }

    @ParameterizedTest(name = "가로={0}, 세로={1}")
    @CsvSource("1,1", "4032,3024", "3024,4032", "20000,20000")
    @DisplayName("가로와 세로를 함께 보내면 1~20000 범위에서 통과한다 (경계 포함)")
    fun 크기_통과(width: Int, height: Int) {
        assertThat(validator.validate(HabitVerifyRequest("인증", "https://storage/a.png", width, height))).isEmpty()
    }

    @ParameterizedTest(name = "가로={0}, 세로={1} → {2}")
    @CsvSource(
        "0,100,imageWidth,사진 가로 크기는 1 이상이어야 합니다.",
        "-1,100,imageWidth,사진 가로 크기는 1 이상이어야 합니다.",
        "20001,100,imageWidth,사진 가로 크기는 20000 이하여야 합니다.",
        "100,0,imageHeight,사진 세로 크기는 1 이상이어야 합니다.",
        "100,-1,imageHeight,사진 세로 크기는 1 이상이어야 합니다.",
        "100,20001,imageHeight,사진 세로 크기는 20000 이하여야 합니다.",
    )
    @DisplayName("범위를 벗어난 크기는 어느 쪽이 문제인지 알려 주며 검증에 실패한다")
    fun 크기_범위_밖(width: Int, height: Int, path: String, message: String) {
        val violations = validator.validate(HabitVerifyRequest("인증", "https://storage/a.png", width, height))

        assertThat(violations)
            .extracting({ it.propertyPath.toString() }, ConstraintViolation<HabitVerifyRequest>::getMessage)
            .containsExactly(tuple(path, message))
    }

    @ParameterizedTest(name = "가로={0}, 세로={1}")
    @CsvSource("100,", ",100")
    @DisplayName("가로와 세로 중 하나만 보내면 검증에 실패한다 (함께 보내야 함)")
    fun 한쪽만_보냄(width: Int?, height: Int?) {
        val violations = validator.validate(HabitVerifyRequest("인증", "https://storage/a.png", width, height))

        assertThat(violations)
            .extracting({ it.propertyPath.toString() }, ConstraintViolation<HabitVerifyRequest>::getMessage)
            .containsExactly(tuple("imageSizeComplete", "사진의 가로와 세로 크기는 함께 보내야 합니다."))
    }
}
