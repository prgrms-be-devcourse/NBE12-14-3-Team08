package com.back.domain.penaltyverify.dto

import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import java.time.LocalDate

// 벌칙 요약 목록 (관리자 검토 및 사용자 벌칙 기록 공용)
@JvmRecord
data class PenaltyVerifySummaryResponse(
    val id: Long?,
    val habitId: Long?,
    val verifyDate: LocalDate?,
    val habitTitle: String,
    val description: String?,
    val memberNickname: String?,
    val imageUrl: String?,
    val penaltyText: String,
    val status: PenaltyVerifyStatus,
) {
    companion object {
        @JvmStatic
        fun from(penaltyVerify: PenaltyVerify): PenaltyVerifySummaryResponse =
            PenaltyVerifySummaryResponse(
                id = penaltyVerify.id,
                habitId = penaltyVerify.habit.id,
                verifyDate = penaltyVerify.verifyDate,
                habitTitle = penaltyVerify.habitTitle,
                description = penaltyVerify.description,
                memberNickname = penaltyVerify.groupMember.member.nickname,
                imageUrl = penaltyVerify.imageUrl,
                penaltyText = penaltyVerify.penaltyText,
                status = penaltyVerify.status,
            )
    }
}
