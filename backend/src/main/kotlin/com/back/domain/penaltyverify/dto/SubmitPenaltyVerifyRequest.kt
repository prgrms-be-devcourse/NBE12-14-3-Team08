package com.back.domain.penaltyverify.dto

import jakarta.validation.constraints.NotBlank

@JvmRecord
data class SubmitPenaltyVerifyRequest(
    val description: String?,

    @field:NotBlank(message = "인증 사진은 필수입니다.")
    val imageUrl: String?,
)
