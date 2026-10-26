package com.back.domain.penaltyverify.dto

import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import java.time.LocalDate

data class PenaltyVerifyDetailResponse(
    val id: Long?,
    val verifyDate: LocalDate?,
    val habitTitle: String,
    val penaltyText: String,
    val description: String?,
    val imageUrl: String?,
    val status: PenaltyVerifyStatus,
) {
    constructor(penaltyVerify: PenaltyVerify) : this(
        id = penaltyVerify.id,
        verifyDate = penaltyVerify.verifyDate,
        habitTitle = penaltyVerify.habitTitle,
        penaltyText = penaltyVerify.penaltyText,
        description = penaltyVerify.description,
        imageUrl = penaltyVerify.imageUrl,
        status = penaltyVerify.status,
    )
}
