package com.back.domain.penaltyverify.dto

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
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class SubmitPenaltyVerifyRequestTest {

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
    @DisplayName("사진 URL이 없거나 비어 있으면 검증에 실패한다 (벌칙 인증 사진은 필수)")
    fun 사진_없음(imageUrl: String?) {
        val violations = validator.validate(SubmitPenaltyVerifyRequest("인증", imageUrl))

        assertThat(violations)
            .extracting({ it.propertyPath.toString() }, ConstraintViolation<SubmitPenaltyVerifyRequest>::getMessage)
            .containsExactly(tuple("imageUrl", "인증 사진은 필수입니다."))
    }

    @Test
    @DisplayName("사진 URL이 있으면 설명이 없어도 검증을 통과한다")
    fun 사진_있음() {
        val violations = validator.validate(SubmitPenaltyVerifyRequest(null, "https://storage/a.png"))

        assertThat(violations).isEmpty()
    }
}
