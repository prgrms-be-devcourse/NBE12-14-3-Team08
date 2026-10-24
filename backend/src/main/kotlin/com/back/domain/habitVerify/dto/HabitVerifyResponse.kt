package com.back.domain.habitVerify.dto

import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import java.time.LocalDate

data class HabitVerifyResponse(
    val id: Long?,
    val habitId: Long?,
    val verifyDate: LocalDate,
    val status: HabitVerifyStatus,
    val description: String?,
    val imageUrl: String?,
) {
    constructor(habitVerify: HabitVerify) : this(
        id = habitVerify.id,
        habitId = habitVerify.habit.id,
        verifyDate = habitVerify.verifyDate,
        status = habitVerify.status,
        description = habitVerify.description,
        imageUrl = habitVerify.imageUrl,
    )
}
