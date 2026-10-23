package com.back.domain.habitVerify.dto

import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import java.time.LocalDate

// @JvmRecord: 기존 Java 테스트가 response.status()처럼 record 접근자로 읽으므로 Java record로 컴파일한다
@JvmRecord
data class HabitVerifyResponse(
    val id: Long?,
    val habitId: Long?,
    val verifyDate: LocalDate,
    val status: HabitVerifyStatus,
    val description: String?,
    val imageUrl: String?,
) {
    companion object {
        @JvmStatic
        fun from(habitVerify: HabitVerify): HabitVerifyResponse =
            HabitVerifyResponse(
                id = habitVerify.id,
                habitId = habitVerify.habit.id,
                verifyDate = habitVerify.verifyDate,
                status = habitVerify.status,
                description = habitVerify.description,
                imageUrl = habitVerify.imageUrl,
            )
    }
}
