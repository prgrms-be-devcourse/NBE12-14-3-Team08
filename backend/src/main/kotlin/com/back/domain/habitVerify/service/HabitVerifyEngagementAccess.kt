package com.back.domain.habitVerify.service

import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.verifyEngagement.service.EngagementRules
import com.back.global.entity.requiredId
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.requireAllowed
import org.springframework.stereotype.Component

// 습관 인증의 반응·댓글을 읽고 쓸 수 있는지 확인한다. (docs/adr/0002-verify-reaction-comment-tables.md)
// - 읽기: 그 인증이 속한 방의 멤버면 된다. 나간 멤버도 읽을 수 있다. (인증 조회와 같은 규칙)
// - 쓰기: 활동 중인 멤버가 진행 중인 방에서 승인된 인증에만 쓸 수 있다.
// 확인 순서: 인증 없음(404) → 방 멤버 아님(403) → 활동 중 아님(403) → 방 종료(400) → 승인 아님(400)
@Component
class HabitVerifyEngagementAccess(
    private val habitVerifyRepository: HabitVerifyRepository,
    private val groupMemberRepository: GroupMemberRepository,
) {

    // 쓰기 대상과, 쓰는 사람(이 방에서의 멤버)
    class Writable(val habitVerify: HabitVerify, val actor: GroupMember) {
        // 내 인증인지: 인증 주인이 곧 쓰는 사람이면 true
        val isOwnVerify: Boolean
            get() = habitVerify.habit.groupMember.requiredId == actor.requiredId
    }

    fun forRead(verificationId: Long, memberId: Long): HabitVerify {
        val habitVerify = habitVerifyRepository.findWithGroupById(verificationId)
            ?: throw EntityNotFoundException("존재하지 않는 인증입니다.")

        val groupId = habitVerify.habit.groupMember.group.requiredId
        requireAllowed(groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId)) {
            "같은 그룹의 멤버만 조회할 수 있습니다."
        }

        return habitVerify
    }

    // 이 방에서의 내 멤버 행. 방 소속을 확인한 뒤에 쓴다.
    fun memberRow(groupId: Long, memberId: Long): GroupMember? =
        groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId).orElse(null)

    // requireApproved = false: 이미 남긴 하트 취소, 내 댓글 수정·삭제처럼 인증 상태가 바뀐 뒤에도 해야 하는 일
    fun forWrite(verificationId: Long, memberId: Long, requireApproved: Boolean = true): Writable {
        val habitVerify = forRead(verificationId, memberId)
        val group = habitVerify.habit.groupMember.group

        val actor = groupMemberRepository.findByGroupIdAndMemberId(group.requiredId, memberId)
            .orElseThrow { EntityNotFoundException("해당 그룹의 멤버가 아닙니다.") }
        EngagementRules.requireWritable(actor, group, habitVerify.status == HabitVerifyStatus.APPROVED, requireApproved)

        return Writable(habitVerify, actor)
    }
}
