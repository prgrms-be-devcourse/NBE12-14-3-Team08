package com.back.domain.member.service

import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.dto.MemberSummaryResponse
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredCreateDate
import com.back.global.entity.requiredId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class MemberSummaryService(
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
) {

    fun getSummary(memberId: Long, today: LocalDate = LocalDate.now()): MemberSummaryResponse {
        val habits = habitRepository.findActiveHabitsInRunningGroups(memberId, today)

        // "이번 주"는 판정 규칙과 같이 방을 만든 요일부터 7일씩 끊는다. 습관마다 방이 달라 주의 시작일도 다르다.
        val weekStartByHabitId = habits.associate { habit ->
            habit.requiredId to weekStart(habit.groupMember.group.requiredCreateDate.toLocalDate(), today)
        }

        val approvedCount = if (weekStartByHabitId.isEmpty()) {
            0
        } else {
            habitVerifyRepository
                .findApprovedVerifyDates(weekStartByHabitId.keys, weekStartByHabitId.values.min())
                .count { verify ->
                    val start = weekStartByHabitId.getValue(verify.habitId)
                    !verify.verifyDate.isBefore(start) && !verify.verifyDate.isAfter(today)
                }
        }

        return MemberSummaryResponse(
            weeklyApprovedCount = approvedCount,
            weeklyGoalCount = habits.sumOf { it.days },
            penaltyCount = penaltyVerifyRepository.countByGroupMember_Member_Id(memberId),
        )
    }

    // 오늘이 속한 주의 시작일 = 오늘 이전(오늘 포함)에서 가장 가까운 "방을 만든 요일"
    private fun weekStart(groupCreatedOn: LocalDate, today: LocalDate): LocalDate {
        val back = (today.dayOfWeek.value - groupCreatedOn.dayOfWeek.value + 7) % 7
        return today.minusDays(back.toLong())
    }
}
