package com.back.domain.habitVerify.service

import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.habitVerify.entity.HabitVerifyComment
import com.back.domain.habitVerify.repository.HabitVerifyCommentRepository
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

// 습관 인증의 댓글. 읽기·쓰기 조건은 HabitVerifyEngagementAccess를 따른다.
@Service
@Transactional
class HabitVerifyCommentService(
    private val access: HabitVerifyEngagementAccess,
    private val commentRepository: HabitVerifyCommentRepository,
) {

    @Transactional(readOnly = true)
    fun list(verificationId: Long, memberId: Long, cursor: Long?, size: Int): CommentPage {
        EngagementRules.requireValidCommentPage(size, cursor)

        val habitVerify = access.forRead(verificationId, memberId)
        val viewerIsOwner = EngagementRules.isOwner(access.memberRow(habitVerify.habit.groupMember.group.requiredId, memberId))

        // 한 건 더 읽어서 다음 페이지가 있는지 알아낸다
        val found = commentRepository.findPage(habitVerify.requiredId, cursor ?: 0L, PageRequest.of(0, size + 1))
        val page = found.take(size)

        return CommentPage(
            items = page.map { toResponse(it, memberId, viewerIsOwner) },
            nextCursor = if (found.size > size) page.last().requiredId else null,
            totalCount = commentRepository.countByHabitVerifyId(habitVerify.requiredId),
        )
    }

    fun create(verificationId: Long, memberId: Long, request: CommentRequest): CommentResponse {
        val target = access.forWrite(verificationId, memberId)
        val content = requireNotNull(request.content) { "댓글 내용을 입력해주세요." }.trim()

        val saved = commentRepository.save(HabitVerifyComment.create(target.habitVerify, target.actor, content))

        return toResponse(saved, memberId, target.actor.role == GroupMemberRole.OWNER)
    }

    // 내용은 작성자만 고칠 수 있다. 승인이 취소된 인증의 댓글도 고칠 수 있다.
    fun edit(verificationId: Long, commentId: Long, memberId: Long, request: CommentRequest): CommentResponse {
        val target = access.forWrite(verificationId, memberId, requireApproved = false)
        val comment = findComment(commentId, verificationId)
        requireAllowed(comment.groupMember.requiredId == target.actor.requiredId) { "본인이 쓴 댓글만 수정할 수 있습니다." }

        comment.edit(requireNotNull(request.content) { "댓글 내용을 입력해주세요." }.trim())
        // 수정일을 응답에 바로 반영해 "수정됨" 표시가 맞도록 저장 시점을 앞당긴다
        commentRepository.flush()

        return toResponse(comment, memberId, target.actor.role == GroupMemberRole.OWNER)
    }

    // 작성자와 방장이 지울 수 있다. 방장이 남의 댓글을 지울 때도 방이 진행 중이어야 한다.
    fun delete(verificationId: Long, commentId: Long, memberId: Long) {
        val target = access.forWrite(verificationId, memberId, requireApproved = false)
        val comment = findComment(commentId, verificationId)
        val isAuthor = comment.groupMember.requiredId == target.actor.requiredId
        requireAllowed(isAuthor || target.actor.role == GroupMemberRole.OWNER) { "본인이 쓴 댓글만 삭제할 수 있습니다." }

        commentRepository.delete(comment)
    }

    private fun findComment(commentId: Long, verificationId: Long): HabitVerifyComment =
        commentRepository.findByIdAndHabitVerifyId(commentId, verificationId)
            ?: throw EntityNotFoundException("존재하지 않는 댓글입니다.")

    private fun toResponse(comment: HabitVerifyComment, viewerMemberId: Long, viewerIsOwner: Boolean): CommentResponse =
        EngagementRules.commentResponse(
            id = comment.requiredId,
            author = comment.groupMember,
            content = comment.content,
            createDate = comment.requiredCreateDate,
            modifyDate = comment.modifyDate,
            viewerMemberId = viewerMemberId,
            viewerIsOwner = viewerIsOwner,
        )

    companion object {
        const val DEFAULT_SIZE = EngagementRules.COMMENT_DEFAULT_SIZE
    }
}
