package com.back.domain.penaltyverify.dto

import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import java.time.LocalDate

// @JvmRecord: 기존 Java 테스트가 response.status()처럼 record 접근자로 읽으므로 Java record로 컴파일한다
@JvmRecord
data class PenaltyVerifyDetailResponse(
    val id: Long?,
    val verifyDate: LocalDate?,
    val habitTitle: String,
    val penaltyText: String,
    val description: String?,
    val imageUrl: String?,
    val status: PenaltyVerifyStatus,
) {
    companion object {
        @JvmStatic
        fun from(penaltyVerify: PenaltyVerify): PenaltyVerifyDetailResponse =
            PenaltyVerifyDetailResponse(
                id = penaltyVerify.id,
                verifyDate = penaltyVerify.verifyDate,
                habitTitle = penaltyVerify.habitTitle,
                penaltyText = penaltyVerify.penaltyText,
                description = penaltyVerify.description,
                imageUrl = penaltyVerify.imageUrl,
                status = penaltyVerify.status,
            )
    }
}
