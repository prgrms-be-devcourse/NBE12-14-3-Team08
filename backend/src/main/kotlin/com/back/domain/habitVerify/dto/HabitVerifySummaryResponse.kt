package com.back.domain.habitVerify.dto

import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import java.time.LocalDate

data class HabitVerifySummaryResponse(
    val id: Long?,
    val verifyDate: LocalDate,
    val habitTitle: String,
    val memberNickname: String,
    val description: String?,
    val imageUrl: String?,
    val status: HabitVerifyStatus,
) {
    constructor(habitVerify: HabitVerify) : this(
        id = habitVerify.id,
        verifyDate = habitVerify.verifyDate,
        habitTitle = habitVerify.habit.title,
        memberNickname = habitVerify.habit.groupMember.member.nickname,
        description = habitVerify.description,
        imageUrl = habitVerify.imageUrl,
        status = habitVerify.status,
    )
}
