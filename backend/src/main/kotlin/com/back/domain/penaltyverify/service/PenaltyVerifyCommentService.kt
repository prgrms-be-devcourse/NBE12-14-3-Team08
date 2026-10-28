package com.back.domain.penaltyverify.service

import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.penaltyverify.entity.PenaltyVerifyComment
import com.back.domain.penaltyverify.repository.PenaltyVerifyCommentRepository
import com.back.domain.verifyEngagement.dto.CommentPage
import com.back.domain.verifyEngagement.dto.CommentRequest
import com.back.domain.verifyEngagement.dto.CommentResponse
import com.back.domain.verifyEngagement.service.EngagementRules
import com.back.global.entity.requiredCreateDate
import com.back.global.entity.requiredId
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.requireAllowed
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 벌칙 인증의 댓글. 규칙은 HabitVerifyCommentService와 같다.
@Service
@Transactional
class PenaltyVerifyCommentService(
    private val access: PenaltyVerifyEngagementAccess,
    private val commentRepository: PenaltyVerifyCommentRepository,
) {

    @Transactional(readOnly = true)
    fun list(penaltyVerifyId: Long, memberId: Long, cursor: Long?, size: Int): CommentPage {
        EngagementRules.requireValidCommentPage(size, cursor)

        val penaltyVerify = access.forRead(penaltyVerifyId, memberId)
        val viewerIsOwner = EngagementRules.isOwner(access.memberRow(penaltyVerify.groupMember.group.requiredId, memberId))

        val found = commentRepository.findPage(penaltyVerify.requiredId, cursor ?: 0L, PageRequest.of(0, size + 1))
        val page = found.take(size)

        return CommentPage(
            items = page.map { toResponse(it, memberId, viewerIsOwner) },
            nextCursor = if (found.size > size) page.last().requiredId else null,
            totalCount = commentRepository.countByPenaltyVerifyId(penaltyVerify.requiredId),
        )
    }

    fun create(penaltyVerifyId: Long, memberId: Long, request: CommentRequest): CommentResponse {
        val target = access.forWrite(penaltyVerifyId, memberId)
        val content = requireNotNull(request.content) { "댓글 내용을 입력해주세요." }.trim()

        val saved = commentRepository.save(PenaltyVerifyComment.create(target.penaltyVerify, target.actor, content))

        return toResponse(saved, memberId, target.actor.role == GroupMemberRole.OWNER)
    }

    fun edit(penaltyVerifyId: Long, commentId: Long, memberId: Long, request: CommentRequest): CommentResponse {
        val target = access.forWrite(penaltyVerifyId, memberId, requireApproved = false)
        val comment = findComment(commentId, penaltyVerifyId)
        requireAllowed(comment.groupMember.requiredId == target.actor.requiredId) { "본인이 쓴 댓글만 수정할 수 있습니다." }

        comment.edit(requireNotNull(request.content) { "댓글 내용을 입력해주세요." }.trim())
        // 수정일을 응답에 바로 반영해 "수정됨" 표시가 맞도록 저장 시점을 앞당긴다
        commentRepository.flush()

        return toResponse(comment, memberId, target.actor.role == GroupMemberRole.OWNER)
    }

    fun delete(penaltyVerifyId: Long, commentId: Long, memberId: Long) {
        val target = access.forWrite(penaltyVerifyId, memberId, requireApproved = false)
        val comment = findComment(commentId, penaltyVerifyId)
        val isAuthor = comment.groupMember.requiredId == target.actor.requiredId
        requireAllowed(isAuthor || target.actor.role == GroupMemberRole.OWNER) { "본인이 쓴 댓글만 삭제할 수 있습니다." }

        commentRepository.delete(comment)
    }

    private fun findComment(commentId: Long, penaltyVerifyId: Long): PenaltyVerifyComment =
        commentRepository.findByIdAndPenaltyVerifyId(commentId, penaltyVerifyId)
            ?: throw EntityNotFoundException("존재하지 않는 댓글입니다.")

    private fun toResponse(comment: PenaltyVerifyComment, viewerMemberId: Long, viewerIsOwner: Boolean): CommentResponse =
        EngagementRules.commentResponse(
            id = comment.requiredId,
            author = comment.groupMember,
            content = comment.content,
            createDate = comment.requiredCreateDate,
            modifyDate = comment.modifyDate,
            viewerMemberId = viewerMemberId,
            viewerIsOwner = viewerIsOwner,
        )
}
