package com.back.domain.habitVerify.service

import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.dto.BulkActionRequest
import com.back.domain.habitVerify.dto.HabitVerifyRequest
import com.back.domain.habitVerify.dto.HabitVerifyResponse
import com.back.domain.habitVerify.dto.HabitVerifySummaryResponse
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import com.back.global.storage.StorageService
import com.back.global.storage.UploadUrlResponse
import com.back.global.storage.VerificationImageType
import com.back.global.entity.requiredCreateDate
import com.back.global.entity.requiredId
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

@Service
class HabitVerifyService(
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val storageService: StorageService,
    private val groupMemberRepository: GroupMemberRepository,
) {

    // 습관 인증 사진 업로드 URL 발급: 내 습관 → 진행 중인 방 → 활성 습관 순으로 확인
    // 반려 건 재제출에도 새 사진이 필요하므로 "오늘 이미 인증했는지"는 보지 않는다.
    @Transactional(readOnly = true)
    fun createUploadUrl(memberId: Long, habitId: Long, filename: String?): UploadUrlResponse {
        val habit = habitRepository.findByIdAndGroupMember_Member_Id(habitId, memberId)
            .getOrNull() ?: throw ForbiddenException("본인의 습관에만 사진을 올릴 수 있습니다.")

        if (habit.groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹에서는 습관 인증 사진을 올릴 수 없습니다.")
        }

        check(habit.status == HabitStatus.ACTIVE) {
            "진행 중인 습관에만 사진을 올릴 수 있습니다. 현재 상태: ${habit.status}"
        }

        return storageService.createUploadUrl(VerificationImageType.HABIT, memberId, habitId, filename)
    }

    @Transactional
    fun create(habitId: Long, request: HabitVerifyRequest, memberId: Long): HabitVerifyResponse {
        val habit = habitRepository.findByIdAndGroupMember_Member_Id(habitId, memberId)
            .getOrNull() ?: throw ForbiddenException("본인의 습관에만 인증을 등록할 수 있습니다.")

        if (habit.groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹에서는 습관 인증을 등록할 수 없습니다.")
        }

        check(habit.status == HabitStatus.ACTIVE) {
            "진행 중인 습관에만 인증을 등록할 수 있습니다. 현재 상태: ${habit.status}"
        }

        val verifyDate = LocalDate.now()

        require(!habitVerifyRepository.existsByHabitIdAndVerifyDate(habitId, verifyDate)) {
            "해당 날짜에 이미 인증 기록이 있습니다."
        }

        validateHabitImage(memberId, habitId, request.imageUrl, null)

        val habitVerify = HabitVerify.create(habit, verifyDate, request.description, request.imageUrl)

        return HabitVerifyResponse.from(habitVerifyRepository.save(habitVerify))
    }

    @Transactional(readOnly = true)
    fun findAll(habitId: Long, memberId: Long): List<HabitVerifyResponse> {
        val habit = habitRepository.findById(habitId)
            .getOrNull() ?: throw EntityNotFoundException("존재하지 않는 습관입니다.")

        if (!groupMemberRepository.existsByGroupIdAndMemberId(habit.groupMember.group.requiredId, memberId)) {
            throw ForbiddenException("같은 그룹의 멤버만 조회할 수 있습니다.")
        }

        return habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(habitId)
            .map(HabitVerifyResponse::from)
    }

    @Transactional
    fun resubmit(
        habitId: Long,
        verificationId: Long,
        request: HabitVerifyRequest,
        memberId: Long,
    ): HabitVerifyResponse {
        val habit = habitRepository.findByIdAndGroupMember_Member_Id(habitId, memberId)
            .getOrNull() ?: throw ForbiddenException("본인의 습관 인증만 재제출할 수 있습니다.")

        if (habit.groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹의 습관 인증은 수정할 수 없습니다.")
        }

        check(habit.status == HabitStatus.ACTIVE) { "이미 종료된 습관의 인증은 수정할 수 없습니다." }

        val habitVerify = habitVerifyRepository.findByIdAndHabitId(verificationId, habitId)
            ?: throw EntityNotFoundException("존재하지 않는 인증 기록입니다.")

        check(LocalDateTime.now().isBefore(calculateEntryDeadline(habit, habitVerify))) {
            "마감일이 지나 더 이상 수정할 수 없습니다."
        }

        habitVerify.validateResubmittable()
        validateHabitImage(memberId, habitId, request.imageUrl, habitVerify.imageUrl)

        habitVerify.resubmit(request.description, request.imageUrl)

        return HabitVerifyResponse.from(habitVerify)
    }

    // 사진 필수 여부는 HabitVerifyRequest의 @NotBlank가 막는다.
    // Supabase 호출이 있으므로 다른 검사가 모두 끝난 뒤 마지막에 확인한다.
    private fun validateHabitImage(memberId: Long, habitId: Long, imageUrl: String?, currentImageUrl: String?) {
        storageService.validateImageUrl(VerificationImageType.HABIT, memberId, habitId, imageUrl, currentImageUrl)
    }

    // 이 인증 건이 제출된 주간 구간의 마감 시각(다음 마감 요일 00시)을 계산한다.
    // 방 생성 요일 기준 주간 판정 로직(HabitService.evaluateWeeklyResult)과 동일한 규칙을 사용해,
    // 반려된 인증은 그 주간 판정이 시작되기 전까지만 수정할 수 있도록 한다.
    private fun calculateEntryDeadline(habit: Habit, habitVerify: HabitVerify): LocalDateTime {
        val deadlineDayOfWeek = habit.groupMember.group.requiredCreateDate.dayOfWeek
        val submittedAt = habitVerify.requiredCreateDate

        val daysSincePeriodStart = (submittedAt.dayOfWeek.value - deadlineDayOfWeek.value + 7) % 7
        val periodStart = submittedAt.toLocalDate()
            .minusDays(daysSincePeriodStart.toLong())
            .atStartOfDay()

        return periodStart.plusWeeks(1)
    }

    @Transactional(readOnly = true)
    fun getPendingByGroup(memberId: Long, groupId: Long): List<HabitVerifySummaryResponse> {
        val groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId)
            .getOrNull() ?: throw AccessDeniedException("해당 그룹의 멤버가 아닙니다.")

        if (groupMember.role != GroupMemberRole.OWNER) {
            throw AccessDeniedException("방장만 대기 목록을 조회할 수 있습니다.")
        }

        return habitVerifyRepository.findPendingByGroupId(groupId)
            .map(HabitVerifySummaryResponse::from)
    }

    @Transactional
    fun approve(memberId: Long, verificationId: Long): HabitVerifyResponse {
        val habitVerify = findById(verificationId)
        validateOwner(memberId, habitVerify.habit.groupMember.group.requiredId)
        habitVerify.approve()
        return HabitVerifyResponse.from(habitVerify)
    }

    @Transactional
    fun reject(memberId: Long, verificationId: Long): HabitVerifyResponse {
        val habitVerify = findById(verificationId)
        validateOwner(memberId, habitVerify.habit.groupMember.group.requiredId)
        habitVerify.reject()
        return HabitVerifyResponse.from(habitVerify)
    }

    @Transactional
    fun bulkApprove(memberId: Long, groupId: Long, request: BulkActionRequest): List<HabitVerifyResponse> {
        validateOwner(memberId, groupId)
        val targets = habitVerifyRepository.findAllByIdInAndGroupId(request.ids, groupId)
        targets.forEach(HabitVerify::approve)
        return targets.map(HabitVerifyResponse::from)
    }

    @Transactional
    fun bulkReject(memberId: Long, groupId: Long, request: BulkActionRequest): List<HabitVerifyResponse> {
        validateOwner(memberId, groupId)
        val targets = habitVerifyRepository.findAllByIdInAndGroupId(request.ids, groupId)
        targets.forEach(HabitVerify::reject)
        return targets.map(HabitVerifyResponse::from)
    }

    private fun findById(verificationId: Long): HabitVerify =
        habitVerifyRepository.findById(verificationId)
            .getOrNull() ?: throw EntityNotFoundException("존재하지 않는 인증 기록입니다.")

    private fun validateOwner(memberId: Long, groupId: Long) {
        val groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId)
            .getOrNull() ?: throw AccessDeniedException("승인/거절 권한이 없습니다.")

        if (groupMember.role != GroupMemberRole.OWNER) {
            throw AccessDeniedException("승인/거절 권한이 없습니다.")
        }

        if (groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹은 인증을 검토할 수 없습니다.")
        }
    }
}
