package com.back.domain.habit.dto

import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import java.time.LocalDateTime

data class HabitResponse(
    val id: Long?,
    val createDate: LocalDateTime?,
    val modifyDate: LocalDateTime?,
    val groupMemberId: Long?,
    val title: String,
    val description: String?,
    val days: Int,
    val status: HabitStatus,
) {
    constructor(habit: Habit) : this(
        id = habit.id,
        createDate = habit.createDate,
        modifyDate = habit.modifyDate,
        groupMemberId = habit.groupMember.id,
        title = habit.title,
        description = habit.description,
        days = habit.days,
        status = habit.status,
    )
}