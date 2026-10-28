package com.back.domain.habitVerify.service

import com.back.domain.habitVerify.repository.HabitVerifyReactionRepository
import com.back.domain.verifyEngagement.dto.ReactionResponse
import com.back.domain.verifyEngagement.service.EngagementRules
import com.back.global.entity.requiredId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 습관 인증의 하트. 눌러 둔 상태(PUT)와 취소한 상태(DELETE)를 정하는 방식이라 같은 요청을 몇 번 보내도, 동시에 보내도 결과가 같다.
@Service
@Transactional
class HabitVerifyReactionService(
    private val access: HabitVerifyEngagementAccess,
    private val reactionRepository: HabitVerifyReactionRepository,
) {

    @Transactional(readOnly = true)
    fun get(verificationId: Long, memberId: Long): ReactionResponse {
        val habitVerify = access.forRead(verificationId, memberId)
        val actorId = actorId(habitVerify.habit.groupMember.group.requiredId, memberId)

        return response(habitVerify.requiredId, actorId)
    }

    fun react(verificationId: Long, memberId: Long): ReactionResponse {
        val target = access.forWrite(verificationId, memberId)
        EngagementRules.requireNotOwnVerify(target.isOwnVerify)

        reactionRepository.insertIfAbsent(target.habitVerify.requiredId, target.actor.requiredId, LocalDateTime.now())

        return response(target.habitVerify.requiredId, target.actor.requiredId)
    }

    // 승인이 취소된 뒤에도 내가 눌러 둔 하트는 거둘 수 있다
    fun cancel(verificationId: Long, memberId: Long): ReactionResponse {
        val target = access.forWrite(verificationId, memberId, requireApproved = false)

        reactionRepository.deleteByHabitVerifyIdAndGroupMemberId(target.habitVerify.requiredId, target.actor.requiredId)

        return response(target.habitVerify.requiredId, target.actor.requiredId)
    }

    private fun response(habitVerifyId: Long, actorId: Long?): ReactionResponse =
        ReactionResponse(
            reacted = actorId != null && reactionRepository.existsByHabitVerifyIdAndGroupMemberId(habitVerifyId, actorId),
            reactionCount = reactionRepository.countByHabitVerifyId(habitVerifyId),
        )

    // 조회하는 사람이 이 방의 멤버 행을 가지고 있으면 그 id. (forRead가 방 소속을 이미 확인했다)
    private fun actorId(groupId: Long, memberId: Long): Long? = access.memberRow(groupId, memberId)?.requiredId
}
