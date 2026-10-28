package com.back.domain.habitVerify.service

import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habitVerify.dto.HabitVerifyPhotoPage
import com.back.domain.habitVerify.repository.HabitVerifyCommentRepository
import com.back.domain.habitVerify.repository.HabitVerifyReactionRepository
import com.back.global.entity.requiredId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 갤러리 사진 목록에 하트·댓글 개수와 "내가 눌렀는지"를 더한다.
// 개수는 저장하지 않고, 한 페이지의 인증 id를 모아 그룹 집계 쿼리 두 번으로 센다. (docs/adr/0002-verify-reaction-comment-tables.md)
@Service
@Transactional(readOnly = true)
class HabitVerifyEngagementService(
    private val reactionRepository: HabitVerifyReactionRepository,
    private val commentRepository: HabitVerifyCommentRepository,
    private val groupMemberRepository: GroupMemberRepository,
) {

    fun withCounts(memberId: Long, groupId: Long, page: HabitVerifyPhotoPage): HabitVerifyPhotoPage {
        if (page.items.isEmpty()) return page

        val ids = page.items.map { it.id }
        val reactionCounts = reactionRepository.countByHabitVerifyIds(ids).associate { it.targetId to it.total }
        val commentCounts = commentRepository.countByHabitVerifyIds(ids).associate { it.targetId to it.total }

        val actorId = groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId).map { it.requiredId }.orElse(null)
        val reacted = if (actorId == null) emptySet() else reactionRepository.findReactedHabitVerifyIds(ids, actorId).toSet()

        return page.copy(
            items = page.items.map {
                it.copy(
                    reactionCount = reactionCounts[it.id] ?: 0,
                    commentCount = commentCounts[it.id] ?: 0,
                    reactedByMe = it.id in reacted,
                )
            },
        )
    }
}
