package com.back.domain.habitVerify.dto

import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import java.time.LocalDate

// @JvmRecord: 기존 Java 테스트가 response.habitTitle()처럼 record 접근자로 읽으므로 Java record로 컴파일한다
@JvmRecord
data class HabitVerifySummaryResponse(
    val id: Long?,
    val verifyDate: LocalDate,
    val habitTitle: String,
    val memberNickname: String,
    val description: String?,
    val imageUrl: String?,
    val status: HabitVerifyStatus,
) {
    companion object {
        @JvmStatic
        fun from(habitVerify: HabitVerify): HabitVerifySummaryResponse =
            HabitVerifySummaryResponse(
                id = habitVerify.id,
                verifyDate = habitVerify.verifyDate,
                habitTitle = habitVerify.habit.title,
                memberNickname = habitVerify.habit.groupMember.member.nickname,
                description = habitVerify.description,
                imageUrl = habitVerify.imageUrl,
                status = habitVerify.status,
            )
    }
}
