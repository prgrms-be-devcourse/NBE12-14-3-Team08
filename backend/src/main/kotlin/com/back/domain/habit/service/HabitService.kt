package com.back.domain.habit.service

import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.dto.CreateHabitRequest
import com.back.domain.habit.dto.HabitResponse
import com.back.domain.habit.dto.HabitWeeklyCheckResponse
import com.back.domain.habit.dto.WeeklyCheckResult
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.penaltyverify.service.PenaltyVerifyService
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import jakarta.transaction.Transactional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class HabitService(
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyService: PenaltyVerifyService,
) {

    @Transactional
    fun createHabit(
        groupId: Long,
        memberId: Long,
        request: CreateHabitRequest,
    ): HabitResponse {
        // 1. GroupMember 조회 (행 잠금: 같은 멤버의 동시 생성 요청이 "활성 습관 확인 → 저장"을 순서대로 거치도록 직렬화)
        val groupMember = groupMemberRepository
            .findByGroupIdAndMemberIdForUpdate(groupId, memberId)
            .orElseThrow { IllegalArgumentException("해당 그룹의 멤버를 찾을 수 없습니다.") }

        if (groupMember.group.isFinished) {
            throw BusinessRuleException("이미 종료된 그룹에서는 습관을 추가할 수 없습니다.")
        }

        // 2. 활성 습관 존재 여부 확인
        if (habitRepository.existsByGroupMember_IdAndStatus(groupMember.id, HabitStatus.ACTIVE)) {
            throw IllegalStateException("이미 활성화된 습관이 존재합니다.")
        }

        // 3. 습관 생성 (요청 검증을 거치지 않은 직접 호출에서도 제목 누락을 같은 메시지로 거절한다)
        val habit = Habit.create(
            groupMember,
            requireNotNull(request.title) { "습관 제목은 필수입니다." },
            request.description,
            request.days,
        )

        // 4. 저장
        val savedHabit = habitRepository.save(habit)

        return HabitResponse(savedHabit)
    }

    @Transactional
    fun failHabit(memberId: Long, habitId: Long) {
        val habit = habitRepository
            .findByIdAndGroupMember_Member_Id(habitId, memberId)
            .orElseThrow { IllegalArgumentException("해당 회원의 습관을 찾을 수 없습니다.") }

        if (habit.groupMember.group.isFinished) {
            throw BusinessRuleException("이미 종료된 그룹의 습관은 포기할 수 없습니다.")
        }

        habit.fail()

        penaltyVerifyService.createPenaltyVerify(habit)
    }

    @Transactional
    fun getActiveHabit(memberId: Long, groupId: Long): HabitResponse {
        val habit = habitRepository
            .findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(memberId, groupId, HabitStatus.ACTIVE)
            ?: throw IllegalArgumentException("현재 진행 중인 습관이 없습니다.")

        return HabitResponse(habit)
    }

    @Transactional
    fun getFailedHabits(memberId: Long, groupId: Long): List<HabitResponse> =
        habitRepository
            .findAllByGroupMember_Member_IdAndGroupMember_Group_IdAndStatusOrderByCreateDateDesc(
                memberId,
                groupId,
                HabitStatus.FAILED,
            )
            .map { HabitResponse(it) }

    @Transactional
    fun getHabit(memberId: Long, habitId: Long): HabitResponse {
        val habit = habitRepository.findByIdOrNull(habitId)
            ?: throw EntityNotFoundException("존재하지 않는 습관입니다.")

        val groupId = habit.groupMember.group.id
        if (!groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId)) {
            throw ForbiddenException("해당 그룹의 습관만 조회할 수 있습니다.")
        }

        return HabitResponse(habit)
    }

    @Transactional
    fun checkWeeklyFailureForGroup(memberId: Long, groupId: Long): List<HabitWeeklyCheckResponse> {
        val requester = groupMemberRepository
            .findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow { IllegalArgumentException("해당 그룹의 멤버를 찾을 수 없습니다.") }

        if (requester.role != GroupMemberRole.OWNER) {
            throw ForbiddenException("주간 판정은 방장만 실행할 수 있습니다.")
        }

        if (requester.group.isFinished) {
            throw BusinessRuleException("이미 종료된 그룹은 주간 판정을 실행할 수 없습니다.")
        }

        val activeHabits = habitRepository
            .findAllByGroupMember_Group_IdAndStatus(groupId, HabitStatus.ACTIVE)

        return activeHabits.map { evaluateWeeklyResult(it) }
    }

    // 매일 00시 05분에 모든 활성 습관을 순회하며 판정을 적용한다.
    // 방장이 weekly-check를 직접 호출하지 않아도, 검토 유예시간이 지난 미검토 건은
    // 이 스케줄러에 의해 자동 승인되고 그 결과로 PASSED/FAILED까지 자동 확정된다.
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    fun autoCheckAllActiveHabits() {
        val activeHabits = habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE)

        for (habit in activeHabits) {
            evaluateWeeklyResult(habit)
        }
    }

    // 판정 로직 자체는 memberId(로그인 컨텍스트) 없이 Habit만으로 동작하도록 분리
    // - 추후 스케줄러가 전체 ACTIVE 습관을 순회하며 그대로 재사용할 수 있도록 하기 위함
    private fun evaluateWeeklyResult(habit: Habit): HabitWeeklyCheckResponse {
        val habitId = habit.id

        if (habit.status != HabitStatus.ACTIVE) {
            return HabitWeeklyCheckResponse(habitId, WeeklyCheckResult.ALREADY_FAILED, habit.status)
        }

        val groupMember = habit.groupMember

        // 이미 끝난 그룹은 판정하지 않는다. (마감일이 지났지만 아직 FINISH 처리 전인 그룹 포함)
        // 판정하면 제출할 수 없는 벌칙이 생기고, 끝난 내기의 결과가 바뀌게 된다.
        if (groupMember.group.isFinished) {
            return HabitWeeklyCheckResponse(habitId, WeeklyCheckResult.GROUP_FINISHED, habit.status)
        }

        val now = LocalDateTime.now()

        // 가입 1주 미만이면 판정 대상에서 제외
        if (now.isBefore(groupMember.createDate.plusWeeks(1))) {
            return HabitWeeklyCheckResponse(habitId, WeeklyCheckResult.TOO_EARLY, habit.status)
        }

        // 방 생성 요일을 기준으로 이번 주 마감 시각(해당 요일 00시) 계산
        val deadlineDayOfWeek = groupMember.group.createDate.dayOfWeek
        val daysSinceDeadlineDay = (now.dayOfWeek.value - deadlineDayOfWeek.value + 7) % 7
        val currentDeadline = now.toLocalDate()
            .minusDays(daysSinceDeadlineDay.toLong())
            .atStartOfDay()
        val weekStart = currentDeadline.minusWeeks(1)

        // 습관이 이번 판정 구간의 마감 시점 이후에 생성됐다면, 그 구간 동안 아예 존재하지 않았던
        // 습관이므로 아직 판정하지 않음 (currentDeadline은 "지금 이전 또는 지금"의 마감이라
        // now와 비교하는 것은 항상 거짓이 되어 의미가 없음 - 습관 생성 시점과 비교해야 함)
        if (!habit.createDate.isBefore(currentDeadline)) {
            return HabitWeeklyCheckResponse(habitId, WeeklyCheckResult.BEFORE_DEADLINE, habit.status)
        }

        // 이번 주 구간 내 미검토(PENDING) 건이 남아있으면 결과와 무관하게 판정을 보류
        // (인증 기록이 검토되지 않은 채로 남지 않도록, 방장이 전부 검토를 끝내야 확정)
        val pendingCount = habitVerifyRepository.countByHabit_IdAndCreateDateBetweenAndStatus(
            habitId, weekStart, currentDeadline, HabitVerifyStatus.PENDING,
        )

        if (pendingCount > 0) {
            // 방장 검토 유예시간(마감 00시 ~ 다음날 24시)이 지났다면 미검토 건을 자동 승인 처리하고
            // 판정을 이어서 진행한다. 유예시간이 아직 남았다면 지금은 판정을 보류한다.
            val reviewGraceDeadline = currentDeadline.plusDays(1)

            if (now.isBefore(reviewGraceDeadline)) {
                return HabitWeeklyCheckResponse(habitId, WeeklyCheckResult.PENDING_REVIEW, habit.status)
            }

            autoApprovePendingVerifies(habitId, weekStart, currentDeadline)
        }

        // days: 일주일에 인증해야 하는 횟수 - 검토가 모두 끝난 상태의 승인 횟수로 최종 판정
        val approvedCount = habitVerifyRepository.countByHabit_IdAndCreateDateBetweenAndStatus(
            habitId, weekStart, currentDeadline, HabitVerifyStatus.APPROVED,
        )

        if (approvedCount >= habit.days) {
            return HabitWeeklyCheckResponse(habitId, WeeklyCheckResult.PASSED, habit.status)
        }

        // 검토가 모두 끝났는데도 days 미달: 자동 실패 및 벌칙 확정
        habit.fail()
        penaltyVerifyService.createPenaltyVerify(habit)

        return HabitWeeklyCheckResponse(habitId, WeeklyCheckResult.FAILED_INSUFFICIENT_SUBMISSION, habit.status)
    }

    // 방장 검토 유예시간이 지나도록 검토되지 않은 이번 주 구간의 인증 건을 자동 승인 처리한다.
    private fun autoApprovePendingVerifies(
        habitId: Long?,
        weekStart: LocalDateTime,
        currentDeadline: LocalDateTime,
    ) {
        habitVerifyRepository
            .findAllByHabit_IdAndCreateDateBetweenAndStatus(
                habitId, weekStart, currentDeadline, HabitVerifyStatus.PENDING,
            )
            .forEach { it.approve() }
    }
}