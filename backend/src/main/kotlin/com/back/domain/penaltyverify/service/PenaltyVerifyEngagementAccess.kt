package com.back.domain.penaltyverify.service

import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.domain.verifyEngagement.service.EngagementRules
import com.back.global.entity.requiredId
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.requireAllowed
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

// 벌칙 인증의 반응·댓글을 읽고 쓸 수 있는지 확인한다. 규칙은 습관 인증과 같다. (HabitVerifyEngagementAccess)
// 확인 순서: 인증 없음(404) → 방 멤버 아님(403) → 활동 중 아님(403) → 방 종료(400) → 승인 아님(400)
@Component
class PenaltyVerifyEngagementAccess(
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val groupMemberRepository: GroupMemberRepository,
) {

    class Writable(val penaltyVerify: PenaltyVerify, val actor: GroupMember) {
        // 내 벌칙인지: 벌칙 주인이 곧 쓰는 사람이면 true
        val isOwnVerify: Boolean
            get() = penaltyVerify.groupMember.requiredId == actor.requiredId
    }

    fun forRead(penaltyVerifyId: Long, memberId: Long): PenaltyVerify {
        // findById의 @EntityGraph가 멤버·방까지 함께 읽는다
        val penaltyVerify = penaltyVerifyRepository.findById(penaltyVerifyId).getOrNull()
            ?: throw EntityNotFoundException("존재하지 않는 벌칙 인증입니다.")

        val groupId = penaltyVerify.groupMember.group.requiredId
        requireAllowed(groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId)) {
            "같은 그룹의 멤버만 조회할 수 있습니다."
        }

        return penaltyVerify
    }

    fun memberRow(groupId: Long, memberId: Long): GroupMember? =
        groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId).getOrNull()

    // 쓰기는 그 벌칙 행을 먼저 잠근 뒤 상태를 읽는다. 벌칙 초기화(하트·댓글 삭제)와 겹쳐도 초기화 뒤에 새 글이 남지 않는다.
    fun forWrite(penaltyVerifyId: Long, memberId: Long, requireApproved: Boolean = true): Writable {
        penaltyVerifyRepository.lockRow(penaltyVerifyId)
        val penaltyVerify = forRead(penaltyVerifyId, memberId)
        val group = penaltyVerify.groupMember.group

        val actor = memberRow(group.requiredId, memberId)
            ?: throw EntityNotFoundException("해당 그룹의 멤버가 아닙니다.")
        EngagementRules.requireWritable(actor, group, penaltyVerify.status == PenaltyVerifyStatus.APPROVED, requireApproved)

        return Writable(penaltyVerify, actor)
    }
}
