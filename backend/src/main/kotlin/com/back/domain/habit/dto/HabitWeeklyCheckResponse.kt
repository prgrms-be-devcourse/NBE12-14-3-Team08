package com.back.domain.habit.dto

import com.back.domain.habit.entity.HabitStatus

data class HabitWeeklyCheckResponse(
    val habitId: Long?,
    val result: WeeklyCheckResult,
    val habitStatus: HabitStatus,
)