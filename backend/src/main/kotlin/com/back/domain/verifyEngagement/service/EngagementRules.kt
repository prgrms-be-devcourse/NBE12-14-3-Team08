package com.back.domain.verifyEngagement.service

import com.back.domain.group.entity.Group
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.verifyEngagement.dto.CommentResponse
import com.back.global.entity.requiredId
import com.back.global.exception.requireAllowed
import com.back.global.exception.requireBusinessRule
import java.time.LocalDateTime

// 습관 인증과 벌칙 인증의 반응·댓글이 함께 쓰는 규칙. (docs/adr/0002-verify-reaction-comment-tables.md)
// 저장소와 엔티티는 대상별로 따로 두고, 누가 무엇을 할 수 있는지와 응답을 만드는 규칙은 한곳에서 정한다.
object EngagementRules {

    const val COMMENT_MAX_LENGTH = 500
    const val COMMENT_DEFAULT_SIZE = 30
    const val COMMENT_MAX_SIZE = 100
    const val GONE_AUTHOR_NAME = "탈퇴한 멤버"

    // 쓰기 조건: 활동 중인 멤버가 진행 중인 방에서 승인된 인증에만 쓸 수 있다.
    // 확인 순서: 활동 중 아님(403) → 방 종료(400) → 승인 아님(400)
    // requireApproved = false: 이미 남긴 하트 취소, 내 댓글 수정·삭제처럼 인증 상태가 바뀐 뒤에도 해야 하는 일
    fun requireWritable(actor: GroupMember, group: Group, approved: Boolean, requireApproved: Boolean) {
        requireAllowed(actor.status == GroupMemberStatus.ACTIVE) { "방을 나간 멤버는 남길 수 없습니다." }
        requireBusinessRule(!group.isFinished()) { "이미 종료된 그룹에서는 남길 수 없습니다." }
        requireBusinessRule(!requireApproved || approved) { "승인된 인증에만 남길 수 있습니다." }
    }

    fun requireNotOwnVerify(isOwnVerify: Boolean) {
        requireBusinessRule(!isOwnVerify) { "본인 인증에는 하트를 누를 수 없습니다." }
    }

    // 목록 요청 값 확인 (잘못되면 400)
    fun requireValidCommentPage(size: Int, cursor: Long?) {
        require(size in 1..COMMENT_MAX_SIZE) { "size는 1 이상 ${COMMENT_MAX_SIZE} 이하여야 합니다." }
        require(cursor == null || cursor >= 0) { "잘못된 커서입니다." }
    }

    fun isOwner(row: GroupMember?): Boolean = row?.role == GroupMemberRole.OWNER

    fun commentResponse(
        id: Long,
        author: GroupMember,
        content: String,
        createDate: LocalDateTime,
        modifyDate: LocalDateTime?,
        viewerMemberId: Long,
        viewerIsOwner: Boolean,
    ): CommentResponse {
        // 방을 나간 멤버는 닉네임을 노출하지 않는다
        val gone = author.status == GroupMemberStatus.LEFT
        val mine = !gone && author.member.requiredId == viewerMemberId

        return CommentResponse(
            id = id,
            authorMemberId = if (gone) null else author.member.requiredId,
            authorNickname = if (gone) GONE_AUTHOR_NAME else author.member.nickname,
            content = content,
            createDate = createDate,
            edited = modifyDate?.isAfter(createDate) == true,
            mine = mine,
            canEdit = mine,
            canDelete = mine || viewerIsOwner,
        )
    }
}
