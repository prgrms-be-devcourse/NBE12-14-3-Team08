package com.back.domain.penaltyverify.service

import com.back.domain.penaltyverify.repository.PenaltyVerifyReactionRepository
import com.back.domain.verifyEngagement.dto.ReactionResponse
import com.back.domain.verifyEngagement.service.EngagementRules
import com.back.global.entity.requiredId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 벌칙 인증의 하트. 방식은 HabitVerifyReactionService와 같다.
@Service
@Transactional
class PenaltyVerifyReactionService(
    private val access: PenaltyVerifyEngagementAccess,
    private val reactionRepository: PenaltyVerifyReactionRepository,
) {

    @Transactional(readOnly = true)
    fun get(penaltyVerifyId: Long, memberId: Long): ReactionResponse {
        val penaltyVerify = access.forRead(penaltyVerifyId, memberId)
        val actorId = access.memberRow(penaltyVerify.groupMember.group.requiredId, memberId)?.requiredId

        return response(penaltyVerify.requiredId, actorId)
    }

    fun react(penaltyVerifyId: Long, memberId: Long): ReactionResponse {
        val target = access.forWrite(penaltyVerifyId, memberId)
        EngagementRules.requireNotOwnVerify(target.isOwnVerify)

        reactionRepository.insertIfAbsent(target.penaltyVerify.requiredId, target.actor.requiredId, LocalDateTime.now())

        return response(target.penaltyVerify.requiredId, target.actor.requiredId)
    }

    fun cancel(penaltyVerifyId: Long, memberId: Long): ReactionResponse {
        val target = access.forWrite(penaltyVerifyId, memberId, requireApproved = false)

        reactionRepository.deleteByPenaltyVerifyIdAndGroupMemberId(target.penaltyVerify.requiredId, target.actor.requiredId)

        return response(target.penaltyVerify.requiredId, target.actor.requiredId)
    }

    private fun response(penaltyVerifyId: Long, actorId: Long?): ReactionResponse =
        ReactionResponse(
            reacted = actorId != null && reactionRepository.existsByPenaltyVerifyIdAndGroupMemberId(penaltyVerifyId, actorId),
            reactionCount = reactionRepository.countByPenaltyVerifyId(penaltyVerifyId),
        )
}
