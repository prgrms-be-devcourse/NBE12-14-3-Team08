package com.back.domain.penaltyverify.service

import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.penaltyverify.dto.BulkActionRequest
import com.back.domain.penaltyverify.dto.PenaltyVerifyDetailResponse
import com.back.domain.penaltyverify.dto.PenaltyVerifySummaryResponse
import com.back.domain.penaltyverify.dto.SubmitPenaltyVerifyRequest
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.ForbiddenException
import com.back.global.storage.StorageService
import com.back.global.storage.UploadUrlResponse
import com.back.global.storage.VerificationImageType
import com.back.global.entity.requiredId
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class PenaltyVerifyService(
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val storageService: StorageService,
) {

    @Transactional
    fun createPenaltyVerify(habit: Habit) {
        penaltyVerifyRepository.save(PenaltyVerify.create(habit.groupMember, habit))
    }

    // 벌칙 인증 사진 업로드 URL 발급: 내 벌칙 → 진행 중인 방 → 제출 가능한 상태 순으로 확인
    fun createUploadUrl(memberId: Long, id: Long, filename: String?): UploadUrlResponse {
        val penaltyVerify = findById(id)

        if (penaltyVerify.groupMember.member.id != memberId) {
            throw ForbiddenException("본인의 벌칙에만 사진을 올릴 수 있습니다.")
        }

        if (penaltyVerify.groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹에서는 벌칙 사진을 올릴 수 없습니다.")
        }

        check(penaltyVerify.isSubmittable()) {
            "제출 또는 재제출할 수 있는 벌칙이 아닙니다. 현재 상태 : ${penaltyVerify.status}"
        }

        return storageService.createUploadUrl(VerificationImageType.PENALTY, memberId, id, filename)
    }

    @Transactional
    fun submit(memberId: Long, habitId: Long, request: SubmitPenaltyVerifyRequest): PenaltyVerifyDetailResponse {
        val penaltyVerify = penaltyVerifyRepository.findByHabitId(habitId)
            .orElseThrow { NoSuchElementException("해당 습관에 대한 벌칙 정보가 존재하지 않습니다.") }

        if (penaltyVerify.groupMember.member.id != memberId) {
            throw ForbiddenException("본인의 습관에 대해서만 벌칙을 제출할 수 있습니다.")
        }

        if (penaltyVerify.groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹에서는 벌칙을 제출할 수 없습니다.")
        }

        penaltyVerify.validateSubmittable()
        validatePenaltyImage(memberId, penaltyVerify, request.imageUrl)

        penaltyVerify.submit(LocalDate.now(), request.description, request.imageUrl)

        return PenaltyVerifyDetailResponse.from(penaltyVerify)
    }

    @Transactional
    fun resubmit(memberId: Long, id: Long, request: SubmitPenaltyVerifyRequest): PenaltyVerifyDetailResponse {
        val penaltyVerify = findById(id)

        if (penaltyVerify.groupMember.member.id != memberId) {
            throw ForbiddenException("본인의 벌칙만 재제출할 수 있습니다.")
        }

        if (penaltyVerify.groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹에서는 벌칙을 재제출할 수 없습니다.")
        }

        penaltyVerify.validateResubmittable()
        validatePenaltyImage(memberId, penaltyVerify, request.imageUrl)

        penaltyVerify.resubmit(LocalDate.now(), request.description, request.imageUrl)

        return PenaltyVerifyDetailResponse.from(penaltyVerify)
    }

    @Transactional
    fun approve(memberId: Long, id: Long): PenaltyVerifyDetailResponse {
        val penaltyVerify = findById(id)
        validateOwner(memberId, penaltyVerify.groupMember.group.requiredId)
        penaltyVerify.approve()
        return PenaltyVerifyDetailResponse.from(penaltyVerify)
    }

    @Transactional
    fun reject(memberId: Long, id: Long): PenaltyVerifyDetailResponse {
        val penaltyVerify = findById(id)
        validateOwner(memberId, penaltyVerify.groupMember.group.requiredId)
        penaltyVerify.reject()
        return PenaltyVerifyDetailResponse.from(penaltyVerify)
    }

    @Transactional
    fun bulkApprove(memberId: Long, groupId: Long, request: BulkActionRequest): List<PenaltyVerifyDetailResponse> {
        validateOwner(memberId, groupId)
        val targets = penaltyVerifyRepository.findAllByIdInAndGroupId(request.ids, groupId)
        targets.forEach(PenaltyVerify::approve)
        return targets.map(PenaltyVerifyDetailResponse::from)
    }

    @Transactional
    fun bulkReject(memberId: Long, groupId: Long, request: BulkActionRequest): List<PenaltyVerifyDetailResponse> {
        validateOwner(memberId, groupId)
        val targets = penaltyVerifyRepository.findAllByIdInAndGroupId(request.ids, groupId)
        targets.forEach(PenaltyVerify::reject)
        return targets.map(PenaltyVerifyDetailResponse::from)
    }

    // 방장만 대기 목록 조회 가능
    fun getPendingByGroup(memberId: Long, groupId: Long): List<PenaltyVerifySummaryResponse> {
        val requester = groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow { AccessDeniedException("해당 방의 멤버가 아닙니다.") }

        if (requester.role != GroupMemberRole.OWNER) {
            throw AccessDeniedException("방장만 대기 목록을 조회할 수 있습니다.")
        }

        return penaltyVerifyRepository.findPendingByGroupId(groupId).map(PenaltyVerifySummaryResponse::from)
    }

    fun getDetail(memberId: Long, id: Long): PenaltyVerifyDetailResponse {
        val penaltyVerify = findById(id)

        val isMember = groupMemberRepository.existsByGroupIdAndMemberId(
            penaltyVerify.habit.groupMember.group.requiredId,
            memberId,
        )

        if (!isMember) {
            throw AccessDeniedException("같은 그룹의 멤버만 조회할 수 있습니다.")
        }

        return PenaltyVerifyDetailResponse.from(penaltyVerify)
    }

    fun getCount(groupId: Long, memberId: Long): Long =
        penaltyVerifyRepository.countByGroupMember_Group_IdAndGroupMember_Member_Id(groupId, memberId)

    fun getPenaltiesByGroupMember(memberId: Long, groupId: Long, groupMemberId: Long): List<PenaltyVerifySummaryResponse> {
        if (!groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId)) {
            throw AccessDeniedException("같은 그룹의 멤버만 조회할 수 있습니다.")
        }

        if (!groupMemberRepository.existsByIdAndGroupId(groupMemberId, groupId)) {
            throw IllegalArgumentException("해당 그룹에 속하지 않은 멤버입니다.")
        }

        return penaltyVerifyRepository.findByGroupMemberIdOrderByIdDesc(groupMemberId)
            .map(PenaltyVerifySummaryResponse::from)
    }

    // 결산 집계를 위해 벌칙 row는 지우지 않는다 (docs/adr/0001-penalty-verify-no-hard-delete.md)
    // 방장: 벌칙 초기화 (자기 벌칙 포함) / 작성자: PENDING 제출 취소 / 그 외: 403
    @Transactional
    fun cancelOrReset(memberId: Long, id: Long): PenaltyVerifyDetailResponse {
        val penaltyVerify = findById(id)

        val isAuthor = penaltyVerify.groupMember.member.id == memberId
        val isOwner = isGroupOwner(memberId, penaltyVerify.groupMember.group.requiredId)

        if (!isAuthor && !isOwner) {
            throw ForbiddenException("벌칙 취소/초기화 권한이 없습니다.")
        }

        if (penaltyVerify.groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹의 벌칙 기록은 변경할 수 없습니다.")
        }

        if (isOwner) {
            penaltyVerify.resetByOwner()
        } else {
            penaltyVerify.cancelSubmission()
        }

        return PenaltyVerifyDetailResponse.from(penaltyVerify)
    }

    // 사진 필수 여부는 SubmitPenaltyVerifyRequest의 @NotBlank가 막는다.
    // Supabase 호출이 있으므로 다른 검사가 모두 끝난 뒤 마지막에 확인한다.
    private fun validatePenaltyImage(memberId: Long, penaltyVerify: PenaltyVerify, imageUrl: String?) {
        storageService.validateImageUrl(
            VerificationImageType.PENALTY,
            memberId,
            penaltyVerify.requiredId,
            imageUrl,
            penaltyVerify.imageUrl,
        )
    }

    private fun findById(id: Long): PenaltyVerify =
        penaltyVerifyRepository.findById(id)
            .orElseThrow { NoSuchElementException("존재하지 않는 벌칙 인증입니다.") }

    private fun validateOwner(memberId: Long, groupId: Long) {
        val groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow { ForbiddenException("승인/거절 권한이 없습니다.") }

        if (groupMember.role != GroupMemberRole.OWNER) {
            throw ForbiddenException("승인/거절 권한이 없습니다.")
        }

        if (groupMember.group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹은 벌칙을 검토할 수 없습니다.")
        }
    }

    private fun isGroupOwner(memberId: Long, groupId: Long): Boolean =
        groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId)
            .map { it.role == GroupMemberRole.OWNER }
            .orElse(false)
}
